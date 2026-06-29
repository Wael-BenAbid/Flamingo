package com.example.flamingoandroid.presentation.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.R
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.databinding.ActivityAdminLoginBinding
import com.example.flamingoandroid.presentation.access.StaffAccess
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Role → destination mapping:
//   cuisine            → KitchenDashboardActivity (direct kitchen view)
//   all other roles    → HomeActivity (drawer nav, initial section set by StaffAccess.defaultSection)

class AdminLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminLoginBinding
    private val firebaseService = FirebaseService()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                setLoading(false)
                setStatus(getString(R.string.admin_login_cancelled), false)
                return@registerForActivityResult
            }

            handleSignInResult(result.data!!)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureGoogleSignInClient()
        configureGoogleButton()

        binding.btnEmailSignIn.setOnClickListener {
            startEmailPasswordSignIn()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }

        checkExistingAdminSession()
    }

    private fun configureGoogleSignInClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun configureGoogleButton() {
        binding.btnGoogleSignIn.setSize(SignInButton.SIZE_WIDE)
        binding.btnGoogleSignIn.setColorScheme(SignInButton.COLOR_LIGHT)
    }

    private fun clearFieldErrors() {
        binding.tilEmail.error = null
        binding.tilPassword.error = null
    }

    // Routing direct par rôle — évite le flash Dashboard et tout accès non autorisé :
    //   serveur    → TableOrderingActivity  (prendre commande)
    //   cuisinier  → KitchenDashboardActivity (commandes cuisine)
    //   barman     → KitchenDashboardActivity (commandes cuisine)
    //   autres     → HomeActivity (section définie par StaffAccess.defaultSection)
    private fun navigateToRoleHome(role: String) {
        val user = firebaseService.getCurrentUser()
        val normalizedRole = StaffAccess.normalize(role)
        val intent = when (normalizedRole) {
            StaffAccess.ROLE_SERVEUR -> Intent(this, TableOrderingActivity::class.java)
                .putExtra(TableOrderingActivity.EXTRA_SERVER_ID, user?.uid.orEmpty())
                .putExtra(TableOrderingActivity.EXTRA_SERVER_NAME,
                    user?.displayName?.takeIf { it.isNotBlank() }
                        ?: user?.email?.substringBefore("@") ?: "Serveur")
            StaffAccess.ROLE_CUISINIER -> Intent(this, KitchenDashboardActivity::class.java)
            StaffAccess.ROLE_BARMAN -> Intent(this, HomeActivity::class.java)
            else -> Intent(this, HomeActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun checkExistingAdminSession() {
        val currentUser = firebaseService.getCurrentUser() ?: run {
            setStatus(getString(R.string.admin_login_ready), false)
            return
        }

        // Si l'email est admin, naviguer directement sans aucune requête Firestore
        if (firebaseService.isAdminEmail(currentUser.email)) {
            navigateToRoleHome("admin")
            return
        }

        setLoading(true)
        setStatus(getString(R.string.admin_login_checking), false)

        lifecycleScope.launch {
            // Timeout 10s pour éviter un blocage infini si Firestore est injoignable
            val currentRole = withTimeoutOrNull(10_000L) {
                withContext(Dispatchers.IO) {
                    firebaseService.getCurrentUserRole(currentUser, forceRefresh = false)
                }
            }

            when {
                !currentRole.isNullOrBlank() && currentRole != StaffAccess.ROLE_NONE -> {
                    navigateToRoleHome(currentRole)
                }
                currentRole == null -> {
                    // Timeout ou erreur réseau — ne pas déconnecter, laisser réessayer
                    setLoading(false)
                    setStatus("Connexion lente — réessayez ou vérifiez le réseau", true)
                }
                else -> {
                    // Rôle vide ou none → compte non autorisé
                    firebaseService.signOut()
                    googleSignInClient.signOut()
                    setLoading(false)
                    setStatus(getString(R.string.admin_login_not_authorized), true)
                }
            }
        }
    }

    private fun startEmailPasswordSignIn() {
        clearFieldErrors()

        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        var hasValidationError = false

        if (email.isBlank()) {
            binding.tilEmail.error = getString(R.string.admin_login_email_required)
            hasValidationError = true
        }

        if (password.isBlank()) {
            binding.tilPassword.error = getString(R.string.admin_login_password_required)
            hasValidationError = true
        }

        if (hasValidationError) {
            setStatus(getString(R.string.admin_login_fill_credentials), true)
            return
        }

        setLoading(true)
        setStatus(getString(R.string.admin_login_email_prompt), false)

        lifecycleScope.launch {
            val authResult = withContext(Dispatchers.IO) {
                firebaseService.signInWithEmailPassword(email, password)
            }

            if (authResult.isSuccess) {
                val user = authResult.getOrNull()?.user
                // forceRefresh = true: the token just issued may carry a freshly-set Custom Claim.
                val currentRole = withContext(Dispatchers.IO) {
                    firebaseService.getCurrentUserRole(user, forceRefresh = true)
                }

                if (!currentRole.isNullOrBlank() && currentRole != StaffAccess.ROLE_NONE) {
                    setStatus(getString(R.string.admin_login_success), false)
                    navigateToRoleHome(currentRole)
                } else {
                    firebaseService.signOut()
                    googleSignInClient.signOut()
                    setLoading(false)
                    setStatus(getString(R.string.admin_login_not_authorized), true)
                }
            } else {
                setLoading(false)
                setStatus(
                    getFriendlyAuthError(authResult.exceptionOrNull()),
                    true
                )
            }
        }
    }

    private fun startGoogleSignIn() {
        clearFieldErrors()
        setLoading(true)
        setStatus(getString(R.string.admin_login_google_prompt), false)
        googleSignInClient.revokeAccess().addOnCompleteListener {
            googleSignInClient.signOut().addOnCompleteListener {
                signInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    private fun handleSignInResult(data: Intent) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken

            if (idToken.isNullOrBlank()) {
                setLoading(false)
                setStatus(getString(R.string.admin_login_missing_token), true)
                return
            }

            lifecycleScope.launch {
                val authResult = withContext(Dispatchers.IO) {
                    firebaseService.signInWithGoogle(idToken)
                }

                if (authResult.isSuccess) {
                    val user = authResult.getOrNull()?.user
                    val currentRole = withContext(Dispatchers.IO) {
                        firebaseService.getCurrentUserRole(user, forceRefresh = true)
                    }

                    if (!currentRole.isNullOrBlank() && currentRole != StaffAccess.ROLE_NONE) {
                        setStatus(getString(R.string.admin_login_success), false)
                        navigateToRoleHome(currentRole)
                    } else {
                        firebaseService.signOut()
                        googleSignInClient.signOut()
                        setLoading(false)
                        setStatus(getString(R.string.admin_login_not_authorized), true)
                    }
                } else {
                    setLoading(false)
                    setStatus(
                        getFriendlyAuthError(authResult.exceptionOrNull()),
                        true
                    )
                }
            }
        } catch (e: ApiException) {
            setLoading(false)
            setStatus(
                getString(R.string.admin_login_failed_with_code, e.statusCode),
                true
            )
        } catch (e: Exception) {
            setLoading(false)
            setStatus(e.localizedMessage ?: getString(R.string.admin_login_failed), true)
        }
    }

    private fun getFriendlyAuthError(exception: Throwable?): String {
        return when (exception) {
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException -> getString(R.string.admin_login_invalid_credentials)
            else -> exception?.localizedMessage ?: getString(R.string.admin_login_failed)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.pbLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
        binding.btnEmailSignIn.isEnabled = !isLoading
        binding.btnGoogleSignIn.isEnabled = !isLoading
    }

    private fun setStatus(message: String, isError: Boolean) {
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.error else R.color.success
            )
        )
    }
}
