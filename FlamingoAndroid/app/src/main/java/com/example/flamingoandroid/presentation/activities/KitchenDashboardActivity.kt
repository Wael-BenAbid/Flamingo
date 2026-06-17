package com.example.flamingoandroid.presentation.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.flamingoandroid.presentation.screens.kitchen.KitchenDashboardScreen
import com.example.flamingoandroid.presentation.theme.FlamingoTheme

class KitchenDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FlamingoTheme {
                KitchenDashboardScreen()
            }
        }
    }
}
