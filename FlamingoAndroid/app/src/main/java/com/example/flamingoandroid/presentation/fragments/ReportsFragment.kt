package com.example.flamingoandroid.presentation.fragments

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.data.models.DashboardStats
import com.example.flamingoandroid.databinding.FragmentReportsBinding
import com.example.flamingoandroid.presentation.util.formatMoney
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val firebaseService = FirebaseService()
    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormatter = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)
    private var selectedDateIso: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val today = java.util.Date()
        selectedDateIso = isoFormatter.format(today)
        binding.tvReportsDate.text = displayFormatter.format(today)
        binding.btnReportsDate.text = displayFormatter.format(today)

        binding.btnReportsDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnReportExport.setOnClickListener {
            Snackbar.make(binding.root, "Export PDF à brancher", Snackbar.LENGTH_SHORT).show()
        }

        loadReport()
    }

    override fun onResume() {
        super.onResume()
        loadReport()
    }

    private fun loadReport() {
        viewLifecycleOwner.lifecycleScope.launch {
            val report = firebaseService.calculateDailyReport(selectedDateIso.ifBlank { isoFormatter.format(java.util.Date()) })
            binding.tvReportsSubtitle.text = "Réservations: ${report.totalReservations} • Arrivées: ${report.totalArrivals} • Clients: ${report.totalClients}"
            render(report)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        try {
            isoFormatter.parse(selectedDateIso)?.let { calendar.time = it }
        } catch (_: Exception) {
            calendar.time = java.util.Date()
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val pickedDate = calendar.time
                selectedDateIso = isoFormatter.format(pickedDate)
                val displayDate = displayFormatter.format(pickedDate)
                binding.tvReportsDate.text = displayDate
                binding.btnReportsDate.text = displayDate
                loadReport()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun render(report: com.example.flamingoandroid.data.models.DailyReport) {
        binding.tvReportRevenue.text = formatMoney(report.totalRevenue)
        binding.tvReportReservations.text = report.totalReservations.toString()
        binding.tvReportStaff.text = report.staffPresent.toString()
        binding.tvReportStock.text = report.totalProductUnitsSold.toString()
        binding.tvReportNet.text = formatMoney(report.netProfit)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
