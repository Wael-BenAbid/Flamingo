package com.example.flamingoandroid.presentation.fragments

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.databinding.FragmentReportsBinding
import com.example.flamingoandroid.presentation.util.formatMoney
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

        binding.btnReportsDate.setOnClickListener { showDatePicker() }
        binding.btnReportExport.setOnClickListener { showExportDialog() }

        loadReport()
    }

    override fun onResume() {
        super.onResume()
        loadReport()
    }

    private fun loadReport() {
        viewLifecycleOwner.lifecycleScope.launch {
            val report = firebaseService.calculateDailyReport(
                selectedDateIso.ifBlank { isoFormatter.format(java.util.Date()) }
            )
            binding.tvReportsSubtitle.text =
                "Réservations: ${report.totalReservations} • Arrivées: ${report.totalArrivals} • Clients: ${report.totalClients}"
            render(report)
        }
    }

    private fun showExportDialog() {
        val options = arrayOf("📊  Bilan Journalier", "📦  Bilan Stock")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quel bilan exporter ?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportDetailedReport()
                    1 -> exportStockReport()
                }
            }
            .show()
    }

    private fun exportStockReport() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { buildStockReportText() }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Bilan Stock Flamingo — $selectedDateIso")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(intent, "Partager le bilan stock"))
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur export : ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun buildStockReportText(): String {
        val db = FirebaseFirestore.getInstance()
        val dateIso = selectedDateIso.ifBlank { isoFormatter.format(java.util.Date()) }

        val inventorySnap = db.collection("inventory").get().await()
        val salesSnap     = db.collection("sales").whereEqualTo("date", dateIso).get().await()

        val sb  = StringBuilder()
        val sep = "═".repeat(36)
        val thin = "─".repeat(36)

        sb.appendLine(sep)
        sb.appendLine("  FLAMINGO — BILAN STOCK")
        sb.appendLine("  Date : $dateIso")
        sb.appendLine(sep)

        // ── Résumé ────────────────────────────────────────────────────
        val items = inventorySnap.documents
        val getQty = { doc: com.google.firebase.firestore.DocumentSnapshot ->
            (doc.getLong("stockQuantity") ?: doc.getLong("quantity") ?: 0L).toInt()
        }
        val getMin = { doc: com.google.firebase.firestore.DocumentSnapshot ->
            (doc.getLong("minStock") ?: doc.getLong("minimumStock") ?: 0L).toInt()
        }
        val totalValue   = items.sumOf { (it.getDouble("buyPrice") ?: 0.0) * getQty(it) }
        val criticalCount = items.count { getQty(it) <= getMin(it) }
        val outOfStock   = items.count { getQty(it) == 0 }

        sb.appendLine()
        sb.appendLine("── RÉSUMÉ STOCK ──")
        sb.appendLine("Articles total       : ${items.size}")
        sb.appendLine("Articles critiques   : $criticalCount")
        sb.appendLine("Articles en rupture  : $outOfStock")
        sb.appendLine("Valeur totale stock  : ${String.format("%.2f", totalValue)} DT")
        sb.appendLine(thin)

        // ── Consommés aujourd'hui ─────────────────────────────────────
        val salesDocs = salesSnap.documents
        sb.appendLine()
        sb.appendLine("── CONSOMMÉS AUJOURD'HUI (${salesDocs.size} articles) ──")
        if (salesDocs.isEmpty()) {
            sb.appendLine("  Aucune vente ce jour.")
        } else {
            salesDocs.forEachIndexed { i, doc ->
                val name  = doc.getString("productName") ?: "?"
                val qty   = doc.getLong("quantity") ?: 0L
                val total = doc.getDouble("totalPrice") ?: 0.0
                sb.appendLine("${i + 1}. $name  ×$qty  =  ${String.format("%.2f", total)} DT")
            }
        }
        sb.appendLine(thin)

        // ── Détail par article ────────────────────────────────────────
        val byCategory = items.groupBy { it.getString("category")?.trim()?.ifBlank { "Autre" } ?: "Autre" }
        sb.appendLine()
        sb.appendLine("── ÉTAT DU STOCK ──")
        byCategory.entries.sortedBy { it.key }.forEach { (cat, catItems) ->
            sb.appendLine()
            sb.appendLine("  [ ${cat.uppercase()} ]")
            catItems.forEach { doc ->
                val name   = doc.getString("name") ?: "?"
                val qty    = getQty(doc)
                val min    = getMin(doc)
                val unit   = doc.getString("unit")?.let { " $it" } ?: ""
                val val_   = (doc.getDouble("buyPrice") ?: 0.0) * qty
                val status = when {
                    qty == 0       -> "⚠ RUPTURE"
                    qty <= min     -> "⚠ CRITIQUE"
                    else           -> "✓ OK"
                }
                sb.appendLine("  • $name  Stock: $qty$unit / Min: $min  Val: ${String.format("%.2f", val_)} DT  $status")
            }
        }

        sb.appendLine()
        sb.appendLine(sep)
        return sb.toString()
    }

    private fun exportDetailedReport() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { buildReportText() }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Bilan Flamingo — $selectedDateIso")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(intent, "Partager le bilan"))
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur export : ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun buildReportText(): String {
        val db = FirebaseFirestore.getInstance()
        val dateIso = selectedDateIso.ifBlank { isoFormatter.format(java.util.Date()) }

        // Réservations du jour
        val reservationsSnap = db.collection("reservations")
            .whereEqualTo("date", dateIso)
            .get().await()

        // Ventes du jour
        val salesSnap = db.collection("sales")
            .whereEqualTo("date", dateIso)
            .get().await()

        // Commandes du jour (table_orders entre 00:00 et 23:59:59)
        val cal = Calendar.getInstance()
        isoFormatter.parse(dateIso)?.let { cal.time = it }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val startTs = Timestamp(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endTs = Timestamp(cal.time)

        val ordersSnap = db.collection("table_orders")
            .whereGreaterThanOrEqualTo("created_at", startTs)
            .whereLessThan("created_at", endTs)
            .get().await()

        val sb = StringBuilder()
        val sep = "═".repeat(36)
        val thin = "─".repeat(36)

        sb.appendLine(sep)
        sb.appendLine("  FLAMINGO — BILAN JOURNALIER")
        sb.appendLine("  Date : $dateIso")
        sb.appendLine(sep)

        // ── Résumé financier (depuis calculateDailyReport) ───────────
        val summary = firebaseService.calculateDailyReport(dateIso)
        sb.appendLine()
        sb.appendLine("── RÉSUMÉ FINANCIER ──")
        sb.appendLine("Revenus totaux       : ${formatMoney(summary.totalRevenue)}")
        sb.appendLine("Dépenses totales     : ${formatMoney(summary.totalExpenses)}")
        sb.appendLine("Bénéfice net         : ${formatMoney(summary.netProfit)}")
        sb.appendLine(thin)

        // ── Réservations ─────────────────────────────────────────────
        val allReservations = reservationsSnap.documents
        val confirmed = allReservations.filter {
            it.getString("status") in listOf("confirmed", "checked-in")
        }
        sb.appendLine()
        sb.appendLine("── RÉSERVATIONS (${confirmed.size} confirmées / ${allReservations.size} total) ──")
        if (allReservations.isEmpty()) {
            sb.appendLine("  Aucune réservation.")
        } else {
            allReservations.forEachIndexed { i, doc ->
                val firstName = doc.getString("firstName") ?: ""
                val lastName  = doc.getString("lastName") ?: ""
                val name = "$firstName $lastName".trim().ifBlank { "—" }
                val adults    = doc.getLong("adults") ?: 0
                val children  = doc.getLong("children") ?: 0
                val zone      = doc.getString("positionType") ?: ""
                val posNum    = doc.getString("positionNumber")?.let { " N°$it" } ?: ""
                val status    = doc.getString("status") ?: ""
                val total     = doc.getDouble("totalPrice") ?: 0.0
                sb.appendLine("${i + 1}. $name — $zone$posNum — ${adults}A/${children}ENF — $status — ${String.format("%.2f", total)} DT")
            }
        }
        sb.appendLine(thin)

        // ── Commandes & Factures ─────────────────────────────────────
        val orders = ordersSnap.documents
        sb.appendLine()
        sb.appendLine("── COMMANDES & FACTURES (${orders.size} tables) ──")
        if (orders.isEmpty()) {
            sb.appendLine("  Aucune commande enregistrée.")
        } else {
            orders.forEachIndexed { i, doc ->
                val tableNum  = doc.getString("table_number") ?: "?"
                val client    = doc.getString("clientName")?.ifBlank { null }
                    ?: doc.getString("server_name") ?: "—"
                val status    = doc.getString("status") ?: ""
                val facture   = doc.getDouble("grandTotal") ?: doc.getDouble("total_price") ?: 0.0
                val discount  = doc.getLong("discountPercent") ?: 0L
                val remise    = if (discount > 0) " (remise ${discount}%)" else ""
                sb.appendLine("${i + 1}. Table $tableNum — $client$remise — $status — ${String.format("%.2f", facture)} DT")

                @Suppress("UNCHECKED_CAST")
                val items = doc.get("items") as? List<Map<String, Any?>> ?: emptyList()
                items.forEach { item ->
                    val itemName = item["name"] as? String ?: ""
                    val qty      = (item["quantity"] as? Long) ?: (item["quantity"] as? Number)?.toLong() ?: 0L
                    val price    = (item["unit_price"] as? Double) ?: (item["unit_price"] as? Number)?.toDouble() ?: 0.0
                    sb.appendLine("   • ${qty}× $itemName  @  ${String.format("%.2f", price)} DT  =  ${String.format("%.2f", qty * price)} DT")
                }
            }
        }
        sb.appendLine(thin)

        // ── Ventes détail ─────────────────────────────────────────────
        val salesDocs = salesSnap.documents
        if (salesDocs.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("── VENTES PRODUITS (${salesDocs.size} articles) ──")
            salesDocs.forEachIndexed { i, doc ->
                val name  = doc.getString("productName") ?: "?"
                val qty   = doc.getLong("quantity") ?: 0L
                val total = doc.getDouble("totalPrice") ?: 0.0
                sb.appendLine("${i + 1}. $name  ×$qty  =  ${String.format("%.2f", total)} DT")
            }
            sb.appendLine(thin)
        }

        sb.appendLine()
        sb.appendLine(sep)
        return sb.toString()
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
