package com.example.flamingoandroid.data.firebase

import com.example.flamingoandroid.data.models.*
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CreatedStaffAccount(
    val uid: String,
    val email: String,
    val password: String,
    val role: String
)

class FirebaseService {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private val ADMIN_EMAILS = setOf(
            "waelbenabid1@gmail.com",
            "abidos.games@gmail.com",
            "admin@gmail.com",
            "m.aminejaouani@gmail.com"
        )

        @Volatile
        private var INSTANCE: FirebaseService? = null

        fun getInstance(): FirebaseService = INSTANCE ?: synchronized(this) {
            INSTANCE ?: FirebaseService().also { INSTANCE = it }
        }
    }

    // ==================== AUDIT TRAIL ====================

    suspend fun logAudit(
        action: String,
        collectionName: String,
        documentId: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        try {
            val user = auth.currentUser ?: return
            val entry = hashMapOf<String, Any?>(
                "timestamp"    to Timestamp.now(),
                "userId"       to user.uid,
                "userName"     to (user.displayName?.takeIf { it.isNotBlank() } ?: user.email ?: user.uid),
                "userEmail"    to user.email,
                "action"       to action,
                "collection"   to collectionName,
                "documentId"   to documentId,
                "details"      to details.ifEmpty { null },
                "platform"     to "android",
            )
            db.collection("audit_logs").add(entry).await()
        } catch (_: Exception) {
            // Never fail the main operation for audit
        }
    }

    // ==================== AUTHENTICATION ====================

    fun getCurrentUser() = auth.currentUser

    fun isMainAdminEmail(email: String?): Boolean {
        return isAdminEmail(email)
    }

    fun isAdminEmail(email: String?): Boolean {
        return email?.let { candidate -> 
            ADMIN_EMAILS.any { adminEmail -> adminEmail.equals(candidate, ignoreCase = true) }
        } == true
    }

    private fun normalizeRoleValue(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        val s = raw.lowercase(Locale.getDefault())
            .replace('é', 'e').replace('è', 'e').replace('ê', 'e')
            .replace('à', 'a').replace('ç', 'c')
            .replace(Regex("[\\s\\-]+"), "_")
        return when {
            s == "admin"                                                -> "admin"
            s == "responsable" || s == "manager"                       -> "responsable"
            s == "cuisinier" || s == "cuisine" || s == "cook"
                || s == "kitchen" || s.startsWith("chef_cu")           -> "cuisinier"
            s == "barman" || s == "bar" || s == "barmaid"
                || s == "bartender"                                    -> "barman"
            s == "serveur" || s == "server" || s == "waiter"
                || s == "chef_serveur"                                 -> "serveur"
            else                                                       -> null
        }
    }

    private fun resolveRoleFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot): String? {
        val explicitRole = normalizeRoleValue(doc.getString("role"))
        if (explicitRole != null) {
            return explicitRole
        }

        val categoryRole = normalizeRoleValue(doc.getString("category"))
        if (categoryRole != null) {
            return categoryRole
        }

        val positionRole = normalizeRoleValue(doc.getString("position"))
        if (positionRole != null) {
            return positionRole
        }

        return if (doc.exists()) "employee" else null
    }

    suspend fun signInWithGoogle(idToken: String): Result<com.google.firebase.auth.AuthResult> = try {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        Result.success(result)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<com.google.firebase.auth.AuthResult> = try {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        Result.success(result)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun hasAdminAccess(user: FirebaseUser?): Boolean = try {
        if (user == null) {
            false
        } else if (isAdminEmail(user.email)) {
            true
        } else {
            val normalizedEmail = user.email?.trim().orEmpty()

            db.collection("admins").document(user.uid).get().await().exists() ||
                (normalizedEmail.isNotBlank() && db.collection("admins").document(normalizedEmail).get().await().exists()) ||
                db.collection("admins").whereEqualTo("uid", user.uid).limit(1).get().await().documents.isNotEmpty() ||
                (normalizedEmail.isNotBlank() && db.collection("admins").whereEqualTo("email", normalizedEmail).limit(1).get().await().documents.isNotEmpty()) ||
                db.collection("workers").whereEqualTo("uid", user.uid).limit(1).get().await().documents.any {
                    normalizeRoleValue(it.getString("role")) == "admin"
                } ||
                (normalizedEmail.isNotBlank() && db.collection("workers").whereEqualTo("email", normalizedEmail).limit(1).get().await().documents.any {
                    normalizeRoleValue(it.getString("role")) == "admin"
                })
        }
    } catch (e: Exception) {
        false
    }

    // Reads the `role` Custom Claim from the Firebase ID token — server-authoritative, no Firestore round-trip.
    // forceRefresh = true is needed right after login to pick up freshly-set Claims before the cached token expires.
    private suspend fun getRoleFromClaims(user: FirebaseUser, forceRefresh: Boolean): String? = try {
        val tokenResult = user.getIdToken(forceRefresh).await()
        normalizeRoleValue(tokenResult.claims["role"] as? String)
    } catch (e: Exception) {
        null
    }

    // Resolution priority: admin-email whitelist → Custom Claims → Firestore fallback.
    // Pass forceRefresh = true immediately after sign-in so the freshest Claims are used for routing.
    suspend fun getCurrentUserRole(user: FirebaseUser?, forceRefresh: Boolean = false): String? = try {
        when {
            user == null -> null
            isAdminEmail(user.email) -> "admin"
            else -> {
                val claimRole = getRoleFromClaims(user, forceRefresh)
                if (!claimRole.isNullOrBlank()) {
                    claimRole
                } else if (hasAdminAccess(user)) {
                    "admin"
                } else {
                    // GET par UID : la règle Firestore autorise resource.data.uid == request.auth.uid
                    // (contrairement à LIST qui exige isAdmin/isResponsable/hasStaffClaimRole)
                    val byUidDoc = db.collection("workers").document(user.uid).get().await()
                    if (byUidDoc.exists()) {
                        resolveRoleFromDoc(byUidDoc)
                    } else {
                        // Fallback email — fonctionne si l'admin lit (lui a les droits list)
                        user.email?.let { email ->
                            db.collection("workers").whereEqualTo("email", email.trim())
                                .limit(1).get().await().documents.firstOrNull()
                                ?.let(::resolveRoleFromDoc)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    // ==================== WORKERS ====================
    
    suspend fun getWorkers(): List<Worker> = try {
        db.collection("workers")
            .get()
            .await()
            .documents
            .map { doc ->
                val worker = doc.toObject(Worker::class.java) ?: Worker()
                worker.copy(
                    id = doc.id,
                    uid = worker.uid.ifBlank { doc.id },
                    role = worker.role.ifBlank { resolveRoleFromDoc(doc).orEmpty().ifBlank { resolveRoleFromCategory(worker.category) } }
                )
            }
    } catch (e: Exception) {
        emptyList()
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun currentTimeString(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    suspend fun getWorkerById(workerId: String): Worker? = try {
        val snapshot = db.collection("workers")
            .document(workerId)
            .get()
            .await()
        val worker = snapshot.toObject(Worker::class.java) ?: Worker()
        worker.copy(
            id = workerId,
            uid = worker.uid.ifBlank { snapshot.getString("uid").orEmpty().ifBlank { workerId } },
            role = worker.role.ifBlank {
                snapshot.getString("role").orEmpty().ifBlank { resolveRoleFromCategory(snapshot.getString("category")) }
            }
        )
    } catch (e: Exception) {
        null
    }

    suspend fun upsertWorkerAttendance(workerId: String, date: String, status: String): Result<Unit> = try {
        val docId = "${workerId}_$date"
        val monthKey = date.take(7)
        val dayKey = date.takeLast(2)
        val now = Date()
        val record = AttendanceRecord(
            id = docId,
            workerId = workerId,
            date = date,
            time = currentTimeString(),
            status = status,
            timestamp = now.time,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        db.collection("attendance")
            .document(docId)
            .set(record)
            .await()

        val monthRef = db.collection("workers")
            .document(workerId)
            .collection("attendance_months")
            .document(monthKey)

        monthRef.set(
            mapOf(
                "workerId" to workerId,
                "month" to monthKey,
                "days" to mapOf(dayKey to status),
                "updatedAt" to Timestamp.now()
            ),
            SetOptions.merge()
        ).await()

        val worker = getWorkerById(workerId) ?: Worker(id = workerId)
        val records = observeWorkerAttendanceRecords(workerId).first()

        val attendanceCount = records.sumOf { attendance ->
            when (attendance.status.lowercase(Locale.getDefault())) {
                "present" -> 1.0
                "half" -> 0.5
                else -> 0.0
            }
        }

        val totalEarned = records.sumOf { attendance ->
            when (attendance.status.lowercase(Locale.getDefault())) {
                "present" -> worker.dailyWage
                "half" -> worker.dailyWage * 0.5
                else -> 0.0
            }
        }

        db.collection("workers")
            .document(workerId)
            .update(
                mapOf(
                    "attendanceCount" to attendanceCount,
                    "totalEarned" to totalEarned,
                    "currentPresence" to status,
                    "lastPresenceDate" to date,
                    "updatedAt" to Timestamp.now()
                )
            )
            .await()

        logAudit("presence", "attendance", "${workerId}_$date", mapOf("workerId" to workerId, "date" to date, "status" to status))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun observeWorkerAttendanceRecords(workerId: String): Flow<List<AttendanceRecord>> = callbackFlow {
        val registration = db.collection("attendance")
            .whereEqualTo("workerId", workerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val records = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(AttendanceRecord::class.java)?.copy(id = doc.id)
                }
                trySend(records)
            }

        awaitClose { registration.remove() }
    }

    fun observeWorkerMonthlyAttendance(workerId: String, monthKey: String): Flow<AttendanceMonthRecord> = callbackFlow {
        val registration = db.collection("workers")
            .document(workerId)
            .collection("attendance_months")
            .document(monthKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(AttendanceMonthRecord(id = monthKey, workerId = workerId, month = monthKey))
                    return@addSnapshotListener
                }

                val month = snapshot?.toObject(AttendanceMonthRecord::class.java)
                    ?.copy(id = snapshot.id, workerId = workerId, month = monthKey)
                    ?: AttendanceMonthRecord(id = monthKey, workerId = workerId, month = monthKey)
                trySend(month)
            }

        awaitClose { registration.remove() }
    }

    private fun firebaseApiKey(): String {
        return FirebaseApp.getInstance().options.apiKey?.trim().orEmpty()
    }

    private fun resolveRoleFromCategory(category: String?): String {
        val raw = category?.trim().orEmpty().lowercase(Locale.getDefault())
            .replace('é', 'e').replace('è', 'e').replace('ê', 'e')
            .replace('à', 'a').replace('ç', 'c')
        return when {
            raw == "responsable" || raw == "manager"          -> "responsable"
            raw == "cuisinier" || raw == "cuisine"
                || raw.startsWith("chef_cu")                  -> "cuisinier"
            raw == "barman" || raw == "bar" || raw == "barmaid" -> "barman"
            raw == "serveur" || raw == "server" || raw == "waiter"
                || raw == "chef_serveur"                       -> "serveur"
            raw == "admin"                                     -> "admin"
            else                                               -> "serveur"
        }
    }

    private fun prepareWorkerForCreate(workerId: String, worker: Worker): Worker {
        val resolvedRole = worker.role.ifBlank { resolveRoleFromCategory(worker.category) }
        val now = Timestamp.now()

        return worker.copy(
            id = workerId,
            uid = worker.uid.ifBlank { workerId },
            role = resolvedRole,
            createdAt = worker.createdAt ?: now,
            updatedAt = now
        )
    }

    private fun prepareWorkerForUpdate(workerId: String, worker: Worker): Worker {
        val resolvedRole = worker.role.ifBlank { resolveRoleFromCategory(worker.category) }

        return worker.copy(
            id = workerId,
            uid = worker.uid.ifBlank { workerId },
            role = resolvedRole,
            updatedAt = Timestamp.now()
        )
    }

    private fun readResponseBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun parseAuthErrorMessage(responseBody: String): String? {
        return try {
            val errorObject = JSONObject(responseBody).optJSONObject("error") ?: return null
            val primaryMessage = errorObject.optString("message").trim()
            if (primaryMessage.isNotBlank()) {
                primaryMessage
            } else {
                val nestedMessage = errorObject.optJSONArray("errors")
                    ?.optJSONObject(0)
                    ?.optString("message")
                    .orEmpty()
                    .trim()
                if (nestedMessage.isBlank()) null else nestedMessage
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapAuthErrorMessage(message: String?, fallback: String): String {
        return when (message?.trim().orEmpty()) {
            "EMAIL_EXISTS" -> "Cette adresse e-mail est déjà utilisée par un autre compte."
            "INVALID_EMAIL" -> "Adresse e-mail invalide."
            "OPERATION_NOT_ALLOWED" -> "La création de compte e-mail/mot de passe n’est pas activée dans Firebase Auth."
            "WEAK_PASSWORD" -> "Le mot de passe est trop faible. Utilisez au moins 6 caractères."
            "MISSING_PASSWORD" -> "Le mot de passe est obligatoire."
            "MISSING_EMAIL" -> "L’adresse e-mail est obligatoire."
            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Trop de tentatives, réessayez plus tard."
            "INVALID_API_KEY", "API_KEY_SERVICE_BLOCKED" -> "La clé Firebase utilisée par Android est refusée par Google."
            else -> message?.trim().orEmpty().ifBlank { fallback }
        }
    }

    /**
     * Create a Firebase Auth user with the Identity Toolkit REST API.
     */
    suspend fun createStaffAuthAccount(
        email: String,
        password: String,
        category: String?
    ): Result<CreatedStaffAccount> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val trimmedPassword = password.trim()

            if (trimmedEmail.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("L’adresse e-mail est obligatoire."))
            }

            if (trimmedPassword.length < 6) {
                return@withContext Result.failure(IllegalArgumentException("Le mot de passe doit contenir au moins 6 caractères."))
            }

            val apiKey = firebaseApiKey()
            if (apiKey.isBlank()) {
                throw IllegalStateException("Clé API Firebase introuvable.")
            }

            val connection = (URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doInput = true
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            try {
                val payload = JSONObject()
                    .put("email", trimmedEmail)
                    .put("password", trimmedPassword)
                    .put("returnSecureToken", true)
                    .toString()

                connection.outputStream.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val responseBody = readResponseBody(connection, responseCode)

                if (responseCode !in 200..299) {
                    val errorMessage = parseAuthErrorMessage(responseBody)
                    throw IllegalStateException(mapAuthErrorMessage(errorMessage, "Impossible de créer le compte Auth."))
                }

                val responseJson = JSONObject(responseBody)
                val uid = responseJson.optString("localId").trim()
                val returnedEmail = responseJson.optString("email").trim().ifBlank { trimmedEmail }

                if (uid.isBlank()) {
                    throw IllegalStateException("Impossible de récupérer l'identifiant du compte créé.")
                }

                Result.success(
                    CreatedStaffAccount(
                        uid = uid,
                        email = returnedEmail,
                        password = trimmedPassword,
                        role = resolveRoleFromCategory(category)
                    )
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addWorker(worker: Worker): Result<String> = try {
        val workerId = worker.uid.trim().ifBlank { worker.id.trim() }
        val role = worker.role.ifBlank { resolveRoleFromCategory(worker.category) }
        val now = Timestamp.now()

        // Utiliser un HashMap pour s'assurer que tous les champs (y compris
        // createdAt/updatedAt annotés @Exclude sur le data class) sont bien écrits
        // en Firestore. Sans ça, les timestamps sont silencieusement omis.
        val data = hashMapOf<String, Any?>(
            "fullName"        to worker.fullName,
            "category"        to worker.category,
            "role"            to role,
            "email"           to worker.email,
            "uid"             to workerId.ifBlank { "" },
            "dailyWage"       to worker.dailyWage,
            "totalAdvances"   to worker.totalAdvances,
            "totalPenalties"  to worker.totalPenalties,
            "totalPaid"       to worker.totalPaid,
            "totalEarned"     to worker.totalEarned,
            "attendanceCount" to worker.attendanceCount,
            "currentPresence" to worker.currentPresence,
            "lastPresenceDate" to worker.lastPresenceDate,
            "startDate"       to worker.startDate,
            "isActive"        to worker.isActive,
            "createdAt"       to now,
            "updatedAt"       to now,
        )

        if (workerId.isBlank()) {
            val docRef = db.collection("workers").add(data).await()
            logAudit("create-worker", "workers", docRef.id, mapOf("name" to worker.fullName, "category" to worker.category))
            Result.success(docRef.id)
        } else {
            db.collection("workers").document(workerId).set(data).await()
            logAudit("create-worker", "workers", workerId, mapOf("name" to worker.fullName, "category" to worker.category))
            Result.success(workerId)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateWorker(workerId: String, worker: Worker): Result<Unit> = try {
        val role = worker.role.ifBlank { resolveRoleFromCategory(worker.category) }
        val data = hashMapOf<String, Any?>(
            "fullName"         to worker.fullName,
            "category"         to worker.category,
            "role"             to role,
            "email"            to worker.email,
            "uid"              to worker.uid.ifBlank { workerId },
            "dailyWage"        to worker.dailyWage,
            "totalAdvances"    to worker.totalAdvances,
            "totalPenalties"   to worker.totalPenalties,
            "totalPaid"        to worker.totalPaid,
            "totalEarned"      to worker.totalEarned,
            "attendanceCount"  to worker.attendanceCount,
            "currentPresence"  to worker.currentPresence,
            "lastPresenceDate" to worker.lastPresenceDate,
            "startDate"        to worker.startDate,
            "isActive"         to worker.isActive,
            "updatedAt"        to Timestamp.now(),
        )
        db.collection("workers").document(workerId).set(data).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteWorker(workerId: String): Result<Unit> = try {
        db.collection("workers").document(workerId).delete().await()
        logAudit("delete-worker", "workers", workerId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addAdvance(workerId: String, amount: Double, reason: String): Result<String> = try {
        val now = java.util.Date()
        val record = AdvanceRecord(
            workerId = workerId,
            amount = amount,
            date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(now),
            reason = reason,
            timestamp = now.time,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        val docRef = db.collection("advances").add(record).await()
        db.collection("workers").document(workerId)
            .update("totalAdvances", FieldValue.increment(amount))
            .await()
        logAudit("advance", "advances", docRef.id, mapOf("workerId" to workerId, "amount" to amount, "reason" to reason))
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addPenalty(workerId: String, amount: Double, reason: String): Result<String> = try {
        val now = java.util.Date()
        val record = PenaltyRecord(
            workerId = workerId,
            amount = amount,
            date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(now),
            reason = reason,
            timestamp = now.time,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        val docRef = db.collection("penalties").add(record).await()
        db.collection("workers").document(workerId)
            .update("totalPenalties", FieldValue.increment(amount))
            .await()
        logAudit("penalty", "penalties", docRef.id, mapOf("workerId" to workerId, "amount" to amount, "reason" to reason))
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addPayment(workerId: String, amount: Double, method: String): Result<String> = try {
        val now = java.util.Date()
        val record = PaymentRecord(
            workerId = workerId,
            amount = amount,
            date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(now),
            method = method,
            timestamp = now.time,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        val docRef = db.collection("payments").add(record).await()
        db.collection("workers").document(workerId)
            .update("totalPaid", FieldValue.increment(amount))
            .await()
        logAudit("payment", "payments", docRef.id, mapOf("workerId" to workerId, "amount" to amount, "method" to method))
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== RESERVATIONS ====================

    suspend fun getReservations(): List<Reservation> = try {
        db.collection("reservations")
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .map { doc -> doc.toObject(Reservation::class.java)?.copy(id = doc.id) ?: Reservation(id = doc.id) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun addReservation(reservation: Reservation): Result<String> = try {
        val now = Timestamp.now()
        val reservationWithTimestamps = reservation.copy(createdAt = now, updatedAt = now)
        val docRef = db.collection("reservations").add(reservationWithTimestamps).await()
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateReservation(reservationId: String, reservation: Reservation): Result<Unit> = try {
        db.collection("reservations").document(reservationId).set(reservation).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateReservationStatus(reservationId: String, status: String): Result<Unit> = try {
        db.collection("reservations").document(reservationId).update("status", status).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteReservation(reservationId: String): Result<Unit> = try {
        db.collection("reservations").document(reservationId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== POSITIONS ====================

    suspend fun getPositions(): List<Position> = try {
        db.collection("positions")
            .get()
            .await()
            .documents
            .map { doc -> doc.toObject(Position::class.java)?.copy(id = doc.id) ?: Position(id = doc.id) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun addPosition(position: Position): Result<String> = try {
        val docRef = db.collection("positions").add(position).await()
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updatePosition(positionId: String, count: Int, price: Double, childPrice: Double): Result<Unit> = try {
        db.collection("positions").document(positionId).update(
            mapOf(
                "count" to count,
                "price" to price,
                "childPrice" to childPrice
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deletePosition(positionId: String): Result<Unit> = try {
        db.collection("positions").document(positionId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== ARRIVALS ====================

    suspend fun getArrivals(): List<Arrival> = try {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        db.collection("reservations")
            .whereEqualTo("date", today)
            .get()
            .await()
            .documents
            .mapNotNull { doc -> doc.toObject(Arrival::class.java)?.copy(id = doc.id) ?: Arrival(id = doc.id) }
            .filter { it.status.equals("pending", ignoreCase = true) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun addArrival(arrival: Arrival): Result<String> = try {
        val docRef = db.collection("arrivals").add(arrival).await()
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun confirmArrival(arrivalId: String): Result<Unit> = try {
        db.collection("arrivals").document(arrivalId).update("status", "confirmed").await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== INVENTORY ====================

    suspend fun getInventoryItems(): List<InventoryItem> = try {
        db.collection("inventory")
            .get()
            .await()
            .documents
            .map { doc -> doc.toObject(InventoryItem::class.java)?.copy(id = doc.id) ?: InventoryItem(id = doc.id) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun getLowStockItems(): List<InventoryItem> = try {
        db.collection("inventory")
            .get()
            .await()
            .toObjects(InventoryItem::class.java)
            .filter { (if (it.stockQuantity > 0) it.stockQuantity else it.quantity) <= (if (it.minStock > 0) it.minStock else it.minimumStock) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun addInventoryItem(item: InventoryItem): Result<String> = try {
        val docRef = db.collection("inventory").add(item).await()
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateInventoryQuantity(itemId: String, newQuantity: Int): Result<Unit> = try {
        db.collection("inventory").document(itemId)
            .update(mapOf("stockQuantity" to newQuantity, "quantity" to newQuantity))
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteInventoryItem(itemId: String): Result<Unit> = try {
        db.collection("inventory").document(itemId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun addSale(product: InventoryItem, quantity: Int): Result<String> = try {
        val now = java.util.Date()
        val qty = quantity.coerceAtLeast(1)
        val unitBuyPrice = if (product.buyPrice > 0) product.buyPrice else product.unitPrice
        val unitSellPrice = if (product.sellPrice > 0) product.sellPrice else product.unitPrice
        val record = SaleRecord(
            productId = product.id,
            productName = product.name,
            quantity = qty,
            unitBuyPrice = unitBuyPrice,
            unitSellPrice = unitSellPrice,
            totalCost = unitBuyPrice * qty,
            totalPrice = unitSellPrice * qty,
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
            timestamp = now.time,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        val docRef = db.collection("sales").add(record).await()
        db.collection("inventory").document(product.id)
            .update("stockQuantity", (product.stockQuantity - qty).coerceAtLeast(0))
            .await()
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getSales(): List<SaleRecord> = try {
        db.collection("sales")
            .get()
            .await()
            .documents
            .map { doc -> doc.toObject(SaleRecord::class.java)?.copy(id = doc.id) ?: SaleRecord(id = doc.id) }
    } catch (e: Exception) {
        emptyList()
    }

    // ==================== DAILY REPORTS ====================

    suspend fun getTodayReport(): DailyReport? = try {
        calculateDailyReport(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    } catch (e: Exception) {
        null
    }

    suspend fun calculateDailyReport(date: String): DailyReport = try {
        val positions = getPositions()
        val workers = getWorkers()
        val reservations = getReservations().filter { it.date == date }
        val sales = getSales().filter { it.date == date }
        val advances = db.collection("advances")
            .whereEqualTo("date", date)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(AdvanceRecord::class.java) }
        val payments = db.collection("payments")
            .whereEqualTo("date", date)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(PaymentRecord::class.java) }

        val positionMap = positions.associateBy { it.type.trim().lowercase(Locale.getDefault()) }

        val confirmedReservations = reservations.filter {
            it.status.equals("confirmed", ignoreCase = true) || it.status.equals("checked-in", ignoreCase = true)
        }

        val reservationRevenue = confirmedReservations.sumOf { reservation ->
            if (reservation.totalPrice > 0) {
                reservation.totalPrice
            } else {
                val position = positionMap[reservation.positionType.trim().lowercase(Locale.getDefault())]
                val adultPrice = position?.price ?: 0.0
                val childPrice = position?.childPrice ?: (adultPrice * 0.5)
                (reservation.adults * adultPrice) + (reservation.children * childPrice)
            }
        }

        val totalProductSales = sales.sumOf { it.totalPrice }
        val totalProductCost = sales.sumOf { it.totalCost }
        val totalWorkerAdvances = advances.sumOf { it.amount }
        val totalWorkerPayments = payments.sumOf { it.amount }
        val totalRevenue = reservationRevenue + totalProductSales
        val totalExpenses = totalProductCost + totalWorkerAdvances + totalWorkerPayments
        val netProfit = totalRevenue - totalExpenses
        val totalClients = confirmedReservations.sumOf { it.adults + it.children }
        val totalProductUnitsSold = sales.sumOf { it.quantity }

        DailyReport(
            date = date,
            totalRevenue = totalRevenue,
            totalExpenses = totalExpenses,
            totalReservations = reservations.size,
            totalArrivals = confirmedReservations.size,
            totalClients = totalClients,
            totalProductSales = totalProductSales,
            totalWorkerAdvances = totalWorkerAdvances,
            totalWorkerPayments = totalWorkerPayments,
            totalProductCost = totalProductCost,
            activeWorkers = workers.count { it.isActive },
            totalDepartures = 0,
            totalOccupiedRooms = confirmedReservations.size,
            staffPresent = workers.count { it.currentPresence.equals("present", ignoreCase = true) },
            totalProductUnitsSold = totalProductUnitsSold,
            netProfit = netProfit
        )
    } catch (e: Exception) {
        DailyReport(date = date)
    }

    suspend fun addDailyReport(report: DailyReport): Result<String> = try {
        val docRef = db.collection("dailyReports").add(report).await()
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateDailyReport(reportId: String, report: DailyReport): Result<Unit> = try {
        db.collection("dailyReports").document(reportId).set(report).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== DASHBOARD STATS ====================

    suspend fun getDashboardStats(): DashboardStats = try {
        val workers = db.collection("workers").get().await().size()
        val reservationItems = getReservations()
        val reservations = reservationItems.size
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val todayReservations = reservationItems.filter { it.date == today }
        val activeReservations = todayReservations.count {
            it.status.equals("confirmed", ignoreCase = true) ||
                it.status.equals("checked-in", ignoreCase = true)
        }
        val dailyRevenue = todayReservations.sumOf { it.totalPrice }
        val inventory = getLowStockItems().size // Ici .size car c'est une List Kotlin (pas de parenthèses)

        DashboardStats(
            totalGuests = reservations,
            occupiedRooms = activeReservations,
            totalStaff = workers,
            dailyRevenue = dailyRevenue,
            lowStockItems = inventory,
            pendingArrivals = todayReservations.count { it.status.equals("pending", ignoreCase = true) } // .size car c'est une List Kotlin
        )
    } catch (e: Exception) {
        DashboardStats()
    }
}
