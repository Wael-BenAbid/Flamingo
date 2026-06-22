package com.example.flamingoandroid.presentation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.flamingoandroid.presentation.screens.dailyorders.DailyOrdersScreen
import com.example.flamingoandroid.presentation.theme.FlamingoTheme

class DailyOrdersFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            FlamingoTheme {
                DailyOrdersScreen(
                    onBack = { requireActivity().onBackPressed() }
                )
            }
        }
    }
}
