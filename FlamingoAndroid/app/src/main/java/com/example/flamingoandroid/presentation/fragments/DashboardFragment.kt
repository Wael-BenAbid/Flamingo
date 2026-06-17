package com.example.flamingoandroid.presentation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.R
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.data.models.DashboardStats
import com.example.flamingoandroid.databinding.FragmentDashboardBinding
import com.example.flamingoandroid.presentation.activities.AdminSection
import com.example.flamingoandroid.presentation.activities.HomeActivity
import com.example.flamingoandroid.presentation.util.formatMoney
import kotlinx.coroutines.flow.collectLatest

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel = com.example.flamingoandroid.presentation.viewmodels.DashboardViewModel()
    private val firebaseService = FirebaseService()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        renderGreeting()

        binding.btnDashboardReservations.setOnClickListener {
            (activity as? HomeActivity)?.openSection(AdminSection.RESERVATIONS)
        }
        binding.btnDashboardWorkers.setOnClickListener {
            (activity as? HomeActivity)?.openSection(AdminSection.WORKERS)
        }
        binding.btnDashboardInventory.setOnClickListener {
            (activity as? HomeActivity)?.openSection(AdminSection.INVENTORY)
        }
        binding.btnDashboardReports.setOnClickListener {
            (activity as? HomeActivity)?.openSection(AdminSection.REPORTS)
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.isLoading.collectLatest { loading ->
                binding.pbDashboardLoading.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.errorMessage.collectLatest { error ->
                binding.tvDashboardStatus.text = error ?: getString(R.string.dashboard_status_ready)
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.dashboardStats.collectLatest { stats ->
                renderStats(stats)
            }
        }
    }

    private fun renderGreeting() {
        val user = firebaseService.getCurrentUser()
        val name = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Admin"
        binding.tvDashboardTitle.text = "Bonjour $name"
    }

    private fun renderStats(stats: DashboardStats) {
        binding.tvDashboardRevenue.text = formatMoney(stats.dailyRevenue)
        binding.tvDashboardReservations.text = stats.totalGuests.toString()
        binding.tvDashboardStaff.text = stats.totalStaff.toString()
        binding.tvDashboardStock.text = stats.lowStockItems.toString()
        binding.tvDashboardSnapshot.text = "Occupation active: ${stats.occupiedRooms} | Réservations en attente: ${stats.pendingArrivals}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
