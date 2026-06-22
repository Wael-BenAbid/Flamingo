package com.example.flamingoandroid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.databinding.ActivityMainBinding
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.presentation.activities.AdminLoginActivity
import com.example.flamingoandroid.presentation.activities.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val firebaseService = FirebaseService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAdminLogin.setOnClickListener {
            val intent = Intent(this, AdminLoginActivity::class.java)
            startActivity(intent)
        }

        checkExistingAdminSession()
    }

    private fun checkExistingAdminSession() {
        val currentUser = firebaseService.getCurrentUser() ?: run {
            binding.tvMainStatus.text = getString(R.string.main_status_ready)
            return
        }

        // Email admin connu → naviguer directement sans requête Firestore
        if (firebaseService.isAdminEmail(currentUser.email)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        binding.tvMainStatus.text = getString(R.string.main_status_checking)
        binding.btnAdminLogin.isEnabled = false

        lifecycleScope.launch {
            // Timeout 10s — évite le blocage infini si Firestore est injoignable
            val hasAccess = withTimeoutOrNull(10_000L) {
                withContext(Dispatchers.IO) {
                    firebaseService.hasAdminAccess(currentUser)
                }
            }

            when {
                hasAccess == true -> {
                    startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                    finish()
                }
                hasAccess == null -> {
                    // Timeout réseau — ne pas déconnecter, laisser réessayer
                    binding.btnAdminLogin.isEnabled = true
                    binding.tvMainStatus.text = "Connexion lente — réessayez"
                }
                else -> {
                    firebaseService.signOut()
                    binding.btnAdminLogin.isEnabled = true
                    binding.tvMainStatus.text = getString(R.string.main_status_ready)
                }
            }
        }
    }
}
