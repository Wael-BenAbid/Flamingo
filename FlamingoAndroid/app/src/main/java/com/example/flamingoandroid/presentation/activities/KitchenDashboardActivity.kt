package com.example.flamingoandroid.presentation.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.flamingoandroid.presentation.screens.kitchen.KitchenDashboardScreen
import com.example.flamingoandroid.presentation.theme.FlamingoTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class KitchenDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlamingoTheme {
                KitchenDashboardScreen()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Quitter ?")
            .setMessage("Voulez-vous vraiment quitter Flamingo ?")
            .setPositiveButton("Quitter") { _, _ -> finish() }
            .setNegativeButton("Rester", null)
            .show()
    }
}
