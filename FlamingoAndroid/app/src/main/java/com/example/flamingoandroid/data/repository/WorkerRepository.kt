package com.example.flamingoandroid.data.repository

import com.example.flamingoandroid.data.models.AdvanceRecord
import com.example.flamingoandroid.data.models.AttendanceMonthRecord
import com.example.flamingoandroid.data.models.AttendanceRecord
import com.example.flamingoandroid.data.models.PaymentRecord
import com.example.flamingoandroid.data.models.PenaltyRecord
import com.example.flamingoandroid.data.models.Worker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkerRepository — single source of truth for:
 *  • workers        collection (CRUD + real-time Flow)
 *  • attendance     collection (per-worker Flows + upsert)
 *  • advances, penalties, payments  sub-collections
 */
class WorkerRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun today()       = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private fun now()         = SimpleDateFormat("HH:mm:ss",   Locale.getDefault()).format(Date())
    private fun normalizeRole(raw: String?) = raw?.trim()?.lowercase(Locale.getDefault())?.let {
        when (it) {
            "chef_cuisinier", "cuisinier", "cook", "kitchen" -> "cuisine"
            "server", "waiter"                               -> "serveur"
            "manager"                                        -> "responsable"
            else                                             -> it
        }
    }

    // ── REAL-TIME FLOWS ───────────────────────────────────────────────

    /** Emits the full worker list whenever Firestore changes. */
    fun observeWorkers(): Flow<List<Worker>> = callbackFlow {
        val listener = db.collection("workers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { doc ->
                        doc.toObject(Worker::class.java)?.copy(
                            id  = doc.id,
                            uid = doc.getString("uid").orEmpty().ifBlank { doc.id },
                            role = doc.getString("role").orEmpty().ifBlank {
                                normalizeRole(doc.getString("category")).orEmpty()
                            }
                        )
                    }
                )
            }
        awaitClose { listener.remove() }
    }

    /** Emits attendance records for a single worker. */
    fun observeAttendanceRecords(workerId: String): Flow<List<AttendanceRecord>> = callbackFlow {
        val listener = db.collection("attendance")
            .whereEqualTo("workerId", workerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { doc -> doc.toObject(AttendanceRecord::class.java)?.copy(id = doc.id) }
                )
            }
        awaitClose { listener.remove() }
    }

    /** Emits the monthly attendance summary for a worker. */
    fun observeMonthlyAttendance(workerId: String, monthKey: String): Flow<AttendanceMonthRecord> = callbackFlow {
        val listener = db.collection("workers").document(workerId)
            .collection("attendance_months").document(monthKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(AttendanceMonthRecord(id = monthKey, workerId = workerId, month = monthKey))
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.toObject(AttendanceMonthRecord::class.java)
                        ?.copy(id = snapshot.id, workerId = workerId, month = monthKey)
                        ?: AttendanceMonthRecord(id = monthKey, workerId = workerId, month = monthKey)
                )
            }
        awaitClose { listener.remove() }
    }

    // ── ONE-SHOT READS ─────────────────────────────────────────────────

    suspend fun getWorkers(): List<Worker> = try {
        db.collection("workers").get().await().documents.mapNotNull { doc ->
            doc.toObject(Worker::class.java)?.copy(
                id  = doc.id,
                uid = doc.getString("uid").orEmpty().ifBlank { doc.id },
                role = doc.getString("role").orEmpty().ifBlank {
                    normalizeRole(doc.getString("category")).orEmpty()
                }
            )
        }
    } catch (e: Exception) { emptyList() }

    suspend fun getWorkerById(id: String): Worker? = try {
        val doc = db.collection("workers").document(id).get().await()
        doc.toObject(Worker::class.java)?.copy(
            id  = id,
            uid = doc.getString("uid").orEmpty().ifBlank { id },
            role = doc.getString("role").orEmpty().ifBlank {
                normalizeRole(doc.getString("category")).orEmpty()
            }
        )
    } catch (e: Exception) { null }

    // ── WORKER MUTATIONS ──────────────────────────────────────────────

    suspend fun addWorker(worker: Worker): Result<String> = try {
        val now  = Timestamp.now()
        val role = worker.role.ifBlank { normalizeRole(worker.category) ?: "employee" }
        val id   = worker.uid.trim().ifBlank { worker.id.trim() }

        if (id.isBlank()) {
            val ref = db.collection("workers")
                .add(worker.copy(role = role, createdAt = now, updatedAt = now)).await()
            Result.success(ref.id)
        } else {
            db.collection("workers").document(id)
                .set(worker.copy(id = id, uid = id, role = role, createdAt = now, updatedAt = now)).await()
            Result.success(id)
        }
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateWorker(id: String, worker: Worker): Result<Unit> = try {
        val role = worker.role.ifBlank { normalizeRole(worker.category) ?: "employee" }
        db.collection("workers").document(id)
            .set(worker.copy(id = id, uid = worker.uid.ifBlank { id }, role = role, updatedAt = Timestamp.now()))
            .await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteWorker(id: String): Result<Unit> = try {
        db.collection("workers").document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ── ATTENDANCE ────────────────────────────────────────────────────

    /**
     * Upserts the attendance record for [workerId] on [date] and
     * recalculates the worker's cumulative salary counters.
     */
    suspend fun upsertAttendance(workerId: String, date: String, status: String): Result<Unit> = try {
        val docId    = "${workerId}_$date"
        val monthKey = date.take(7)
        val dayKey   = date.takeLast(2)

        // 1. Write the daily record
        db.collection("attendance").document(docId).set(
            AttendanceRecord(
                id         = docId,
                workerId   = workerId,
                date       = date,
                time       = now(),
                status     = status,
                timestamp  = System.currentTimeMillis(),
                createdAt  = Timestamp.now(),
                updatedAt  = Timestamp.now()
            )
        ).await()

        // 2. Merge into the monthly sub-document
        db.collection("workers").document(workerId)
            .collection("attendance_months").document(monthKey)
            .set(
                mapOf(
                    "workerId"  to workerId,
                    "month"     to monthKey,
                    "days"      to mapOf(dayKey to status),
                    "updatedAt" to Timestamp.now()
                ),
                SetOptions.merge()
            ).await()

        // 3. Recalculate cumulative salary counters
        val worker  = getWorkerById(workerId) ?: Worker(id = workerId)
        val records = observeAttendanceRecords(workerId).first()

        val attendanceCount = records.sumOf { r ->
            when (r.status.lowercase(Locale.getDefault())) {
                "present" -> 1.0; "half" -> 0.5; else -> 0.0
            }
        }
        val totalEarned = records.sumOf { r ->
            when (r.status.lowercase(Locale.getDefault())) {
                "present" -> worker.dailyWage; "half" -> worker.dailyWage * 0.5; else -> 0.0
            }
        }

        db.collection("workers").document(workerId).update(
            mapOf(
                "attendanceCount"   to attendanceCount,
                "totalEarned"       to totalEarned,
                "currentPresence"   to status,
                "lastPresenceDate"  to date,
                "updatedAt"         to Timestamp.now()
            )
        ).await()

        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ── FINANCIAL RECORDS ──────────────────────────────────────────────

    suspend fun addAdvance(workerId: String, amount: Double, reason: String): Result<String> = try {
        val date = today()
        val now  = Timestamp.now()
        val ref  = db.collection("advances").add(
            AdvanceRecord(workerId = workerId, amount = amount, date = date, reason = reason,
                timestamp = System.currentTimeMillis(), createdAt = now, updatedAt = now)
        ).await()
        db.collection("workers").document(workerId)
            .update("totalAdvances", FieldValue.increment(amount)).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun addPenalty(workerId: String, amount: Double, reason: String): Result<String> = try {
        val date = today()
        val now  = Timestamp.now()
        val ref  = db.collection("penalties").add(
            PenaltyRecord(workerId = workerId, amount = amount, date = date, reason = reason,
                timestamp = System.currentTimeMillis(), createdAt = now, updatedAt = now)
        ).await()
        db.collection("workers").document(workerId)
            .update("totalPenalties", FieldValue.increment(amount)).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun addPayment(workerId: String, amount: Double, method: String): Result<String> = try {
        val date = today()
        val now  = Timestamp.now()
        val ref  = db.collection("payments").add(
            PaymentRecord(workerId = workerId, amount = amount, date = date, method = method,
                timestamp = System.currentTimeMillis(), createdAt = now, updatedAt = now)
        ).await()
        db.collection("workers").document(workerId)
            .update("totalPaid", FieldValue.increment(amount)).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }
}
