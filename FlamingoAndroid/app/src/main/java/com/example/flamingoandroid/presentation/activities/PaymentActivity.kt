package com.example.flamingoandroid.presentation.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.flamingoandroid.presentation.screens.payment.PaymentScreen
import com.example.flamingoandroid.presentation.viewmodels.PaymentViewModel

class PaymentActivity : ComponentActivity() {

    private val viewModel: PaymentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaymentScreen(
                viewModel = viewModel,
                onBack = { finish() },
            )
        }
    }
}
