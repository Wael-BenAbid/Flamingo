package com.example.flamingoandroid.data.repository

import com.example.flamingoandroid.data.models.CreatedStaffAccount
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * AuthRepository — single source of truth for Firebase Authentication.
 *
 *  • Sign in / sign out
 *  • Role resolution (admin whitelist → Custom Claims → Firestore fallback)
 *  • Staff account creation via Identity Toolkit REST API
 */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    companion object {
        // Mirrors ADMIN_EMAILS in shared/constants.ts and OceanConfig.swift.
        val ADMIN_EMAILS: Set<String> = setOf(
            "waelbenabid1@gmail.com",
            "abidos.games@gmail.com"
        )
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isAdminEmail(email: String?): Boolean =
        email?.let { e -> ADMIN_EMAILS.any { it.equals(e.trim(), ignoreCase = true) } } == true

    // ── SIGN IN / OUT ──────────────────────────────────────────────────

    suspend fun signInWithEmailPassword(email: String, password: String) =
        runCatching { auth.signInWithEmailAndPassword(email.trim(), password).await() }

    suspend fun signInWithGoogle(idToken: String) = runCatching {
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    fun signOut() = auth.signOut()

    // ── ROLE RESOLUTION ────────────────────────────────────────────────
    // Priority: admin email whitelist → Firebase Custom Claims → Firestore fallback.

    suspend fun hasAdminAccess(user: FirebaseUser?): Boolean {
        if (user == null) return false
        if (isAdminEmail(user.email)) return true
        return try {
            db.collection("admins").document(user.uid).get().await().exists() ||
            db.collection("workers").whereEqualTo("uid", user.uid)
                .limit(1).get().await().documents.any { normalizeRole(it.getString("role")) == "admin" }
        } catch (_: Exception) { false }
    }

    suspend fun getCurrentUserRole(user: FirebaseUser?, forceRefresh: Boolean = false): String? {
        if (user == null) return null
        if (isAdminEmail(user.email)) return "admin"
        val claimRole = getRoleFromClaims(user, forceRefresh)
        if (!claimRole.isNullOrBlank()) return claimRole
        return getRoleFromFirestore(user)
    }

    private suspend fun getRoleFromClaims(user: FirebaseUser, forceRefresh: Boolean): String? = try {
        val token = user.getIdToken(forceRefresh).await()
        normalizeRole(token.claims["role"] as? String)
    } catch (_: Exception) { null }

    private suspend fun getRoleFromFirestore(user: FirebaseUser): String? = try {
        val byUid = db.collection("workers").whereEqualTo("uid", user.uid)
            .limit(1).get().await().documents.firstOrNull()
        val doc = byUid ?: user.email?.let { email ->
            db.collection("workers").whereEqualTo("email", email.trim())
                .limit(1).get().await().documents.firstOrNull()
        }
        doc?.let { normalizeRole(it.getString("role") ?: it.getString("category")) }
    } catch (_: Exception) { null }

    // ── STAFF ACCOUNT CREATION ─────────────────────────────────────────

    suspend fun createStaffAuthAccount(
        email: String,
        password: String,
        category: String?
    ): Result<CreatedStaffAccount> = withContext(Dispatchers.IO) {
        runCatching {
            val trimEmail    = email.trim()
            val trimPassword = password.trim()
            require(trimEmail.isNotBlank())          { "L'adresse e-mail est obligatoire." }
            require(trimPassword.length >= 6)        { "Le mot de passe doit contenir au moins 6 caractères." }

            val apiKey = FirebaseApp.getInstance().options.apiKey?.trim().orEmpty()
            require(apiKey.isNotBlank())             { "Clé API Firebase introuvable." }

            val conn = (URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doInput  = true; doOutput = true
                connectTimeout = 15_000; readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            try {
                conn.outputStream.use { out ->
                    out.write(
                        JSONObject()
                            .put("email", trimEmail)
                            .put("password", trimPassword)
                            .put("returnSecureToken", true)
                            .toString()
                            .toByteArray(Charsets.UTF_8)
                    )
                }

                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()

                check(code in 200..299) {
                    val msg = try {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    } catch (_: Exception) { null }
                    mapAuthError(msg) ?: "Impossible de créer le compte Auth."
                }

                val uid = JSONObject(body).optString("localId").trim()
                check(uid.isNotBlank()) { "UID manquant dans la réponse Firebase." }

                CreatedStaffAccount(
                    uid      = uid,
                    email    = trimEmail,
                    password = trimPassword,
                    role     = resolveRoleFromCategory(category)
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────

    private fun normalizeRole(value: String?): String? {
        val s = value?.trim()?.lowercase(Locale.getDefault())
            ?.replace(Regex("[éèê]"), "e")
            ?.replace('à', 'a')
            ?.replace('ç', 'c')
            ?.replace(Regex("[\\s\\-]+"), "_")
            ?: return null
        if (s.isBlank()) return null
        return when (s) {
            "admin"                                                     -> "admin"
            "responsable", "manager"                                    -> "responsable"
            "cuisinier", "cuisine", "cook", "kitchen",
            "chef_cuisinier", "chef_cuisine", "chef_de_cuisine"         -> "cuisinier"
            "barman", "bar", "barmaid", "bartender"                     -> "barman"
            "serveur", "server", "waiter",
            "chef_serveur", "securite", "nettoyage", "employee"         -> "serveur"
            else                                                        -> s
        }
    }

    private fun resolveRoleFromCategory(category: String?): String =
        normalizeRole(category) ?: "serveur"

    private fun mapAuthError(msg: String?): String? = when (msg?.trim()) {
        "EMAIL_EXISTS"             -> "Cette adresse e-mail est déjà utilisée."
        "INVALID_EMAIL"            -> "Adresse e-mail invalide."
        "WEAK_PASSWORD"            -> "Le mot de passe est trop faible (6 caractères minimum)."
        "OPERATION_NOT_ALLOWED"    -> "Connexion e-mail/mot de passe désactivée dans Firebase."
        "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Trop de tentatives, réessayez plus tard."
        else                       -> msg?.takeIf { it.isNotBlank() }
    }
}
