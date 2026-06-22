package com.example.flamingoandroid.presentation.fragments

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
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
        val options = arrayOf("📊  Bilan Journalier (PDF)", "📦  Bilan Stock (PDF)")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quel bilan imprimer ?")
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
                val html = withContext(Dispatchers.IO) { buildStockReportHtml() }
                printHtml(requireContext(), html, "Bilan-Stock-$selectedDateIso")
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur export : ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }


    private fun exportDetailedReport() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val html = withContext(Dispatchers.IO) { buildBilanHtml() }
                printHtml(requireContext(), html, "Bilan-Journalier-$selectedDateIso")
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur export : ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun printHtml(context: Context, html: String, jobName: String) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val pm = context.getSystemService(android.print.PrintManager::class.java)
                pm.print(
                    jobName,
                    view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(PrintAttributes.Resolution("300dpi", "300dpi", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
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

    // ── HTML builders ─────────────────────────────────────────────────────────

    private suspend fun buildBilanHtml(): String {
        val db      = FirebaseFirestore.getInstance()
        val dateIso = selectedDateIso.ifBlank { isoFormatter.format(java.util.Date()) }
        val dispDate = displayFormatter.format(isoFormatter.parse(dateIso) ?: java.util.Date())
            .replaceFirstChar { it.uppercase() }
        val genStr  = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(java.util.Date())
        fun dt(d: Double) = String.format(Locale.FRANCE, "%.2f&nbsp;DT", d)
        val THIN = "─".repeat(42)

        val cal = Calendar.getInstance()
        isoFormatter.parse(dateIso)?.let { cal.time = it }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val startTs = Timestamp(cal.time)
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val endTs = Timestamp(cal.time)

        val summary       = firebaseService.calculateDailyReport(dateIso)
        val reservations  = db.collection("reservations").whereEqualTo("date", dateIso).get().await().documents
        val orders        = db.collection("table_orders")
            .whereGreaterThanOrEqualTo("created_at", startTs)
            .whereLessThanOrEqualTo("created_at", endTs)
            .get().await().documents.filter { it.getString("status") != "cancelled" }
        val sales         = db.collection("sales").whereEqualTo("date", dateIso).get().await().documents
        val advances      = db.collection("advances").whereEqualTo("date", dateIso).get().await().documents
        val payments      = db.collection("payments").whereEqualTo("date", dateIso).get().await().documents
        val workers       = db.collection("workers").get().await().documents
        val workerNames   = workers.associate { it.id to (it.getString("fullName") ?: it.id) }

        fun row(l: String, r: String, bold: Boolean = false) = buildString {
            val style = if (bold) "font-weight:bold;" else ""
            append("""<div class="row" style="$style"><span>$l</span><span>$r</span></div>""")
        }

        // Réservations HTML
        val resHtml = if (reservations.isEmpty()) {
            """<div class="empty">Aucune réservation pour ce jour.</div>"""
        } else reservations.joinToString("") { doc ->
            val name = "${doc.getString("firstName") ?: ""} ${doc.getString("lastName") ?: ""}".trim().ifBlank { "—" }
            val pos  = "${doc.getString("positionType") ?: ""}${doc.getString("positionNumber")?.let { " N°$it" } ?: ""}"
            val adults = doc.getLong("adults") ?: 0
            val children = doc.getLong("children") ?: 0
            val status = when (doc.getString("status")) {
                "confirmed" -> "<span class='badge-ok'>CONFIRMÉ</span>"
                "absent"    -> "<span class='badge-warn'>ABSENT</span>"
                "cancelled" -> "<span class='badge-err'>ANNULÉ</span>"
                else        -> "<span class='badge-muted'>${doc.getString("status") ?: ""}</span>"
            }
            val amt  = doc.getDouble("totalPrice")?.let { dt(it) } ?: "—"
            val phone = doc.getString("phone")?.let { "<br><small style='color:#888'>Tél: $it</small>" } ?: ""
            """<div class="res-row">
              <div class="res-name">$name$phone</div>
              <div class="res-pos">$pos · ${adults}A ${children}ENF</div>
              <div class="res-right">$status <strong>$amt</strong></div>
            </div>"""
        }

        // Commandes HTML
        val ordersHtml = if (orders.isEmpty()) {
            """<div class="empty">Aucune commande enregistrée.</div>"""
        } else orders.joinToString("") { doc ->
            val table   = doc.getString("table_number") ?: "?"
            val server  = doc.getString("server_name") ?: ""
            val client  = doc.getString("clientName") ?: ""
            val facture = doc.getDouble("grandTotal") ?: doc.getDouble("total_price") ?: 0.0
            val discount = doc.getLong("discountPercent") ?: 0L
            @Suppress("UNCHECKED_CAST")
            val items = doc.get("items") as? List<Map<String,Any?>> ?: emptyList()
            val itemsHtml = items.joinToString("") { item ->
                val n = item["name"] as? String ?: ""; val q = (item["quantity"] as? Number)?.toInt() ?: 0
                val p = (item["unit_price"] as? Number)?.toDouble() ?: 0.0
                """<div class="item-row"><span>&times;$q  $n</span><span>${dt(p * q)}</span></div>"""
            }
            val discountLine = if (discount > 0) """<div class="discount-row">Remise $discount%</div>""" else ""
            """<div class="order-block">
              <div class="table-header"><span>TABLE $table</span><span class="total-val">${dt(facture)}</span></div>
              ${if (server.isNotBlank()) """<div class="order-meta">${row("Serveur :", "<strong>$server</strong>")}</div>""" else ""}
              ${if (client.isNotBlank()) """<div class="order-meta">${row("Client :", client)}</div>""" else ""}
              <div class="sep thin">$THIN</div>
              $itemsHtml
              $discountLine
              <div class="total-box">${row("TOTAL NET", dt(facture), true)}</div>
            </div>"""
        }

        // Finances HTML
        val finHtml = buildString {
            advances.forEach { doc ->
                val name = workerNames[doc.getString("workerId")] ?: "?"
                val amt  = doc.getDouble("amount") ?: 0.0
                val reason = doc.getString("reason") ?: ""
                append(row("Avance — $name${if (reason.isNotBlank()) " ($reason)" else ""}", dt(amt)))
            }
            payments.forEach { doc ->
                val name = workerNames[doc.getString("workerId")] ?: "?"
                val amt  = doc.getDouble("amount") ?: 0.0
                val method = doc.getString("method") ?: ""
                append(row("Paiement — $name${if (method.isNotBlank()) " · $method" else ""}", dt(amt)))
            }
        }

        // Ventes HTML
        val salesHtml = sales.joinToString("") { doc ->
            val name = doc.getString("productName") ?: "?"
            val qty  = doc.getLong("quantity") ?: 0L
            val tot  = doc.getDouble("totalPrice") ?: 0.0
            row("$name  ×$qty", dt(tot))
        }

        // Poissons
        val fishMap = mutableMapOf<String, Int>()
        orders.forEach { doc ->
            @Suppress("UNCHECKED_CAST")
            val items = doc.get("items") as? List<Map<String,Any?>> ?: emptyList()
            items.filter { (it["name"] as? String)?.lowercase()?.contains("poisson") == true }
                .forEach { item ->
                    val n = item["name"] as? String ?: ""; val q = (item["quantity"] as? Number)?.toInt() ?: 0
                    fishMap[n] = (fishMap[n] ?: 0) + q
                }
        }
        val totalFish = fishMap.values.sum()
        val fishHtml = if (fishMap.isEmpty()) "" else buildString {
            append("""<div class="section-header fish-header">POISSONS SORTIS — $totalFish PORTIONS</div>""")
            fishMap.entries.sortedByDescending { it.value }.forEach { (n, q) ->
                append(row("$n", "$q portion${if (q > 1) "s" else ""}"))
            }
        }

        return """<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8"/>
<title>Bilan Journalier — $dispDate</title>
<style>
  @page { margin: 8mm; size: A4; }
  * { margin:0; padding:0; box-sizing:border-box; }
  body { font-family: Arial, sans-serif; font-size: 11px; color: #000; line-height: 1.5; }
  .doc-header { background: #FF7A85; color: #fff; padding: 6mm 5mm; }
  .doc-header h1 { font-size: 15px; font-weight: bold; letter-spacing: 1px; }
  .doc-header p { font-size: 9px; opacity: 0.85; margin-top: 1mm; }
  .kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); border: 2px solid #1A365D; margin: 4mm 0; }
  .kpi-cell { text-align: center; padding: 4mm 2mm; border-right: 1px solid #1A365D; }
  .kpi-cell:last-child { border-right: none; }
  .kpi-val { font-size: 14px; font-weight: bold; }
  .kpi-lbl { font-size: 8px; color: #777; text-transform: uppercase; letter-spacing: 1px; }
  .section-header { background: #1A365D; color: #fff; padding: 2mm 4mm; font-weight: bold; font-size: 10px; letter-spacing: 1px; margin: 4mm 0 0; }
  .fish-header { background: #1e6ab0; }
  .row { display: flex; justify-content: space-between; padding: 1.5mm 4mm; border-bottom: 1px solid #f0f0f0; background: #fafafa; }
  .row strong { font-weight: bold; }
  .res-row { display: flex; align-items: center; justify-content: space-between; padding: 2mm 4mm; border-bottom: 1px solid #eee; }
  .res-name { flex: 2; font-weight: bold; }
  .res-pos { flex: 2; color: #555; font-size: 10px; }
  .res-right { flex: 1; text-align: right; }
  .badge-ok   { background:#d1fae5; color:#065f46; padding:1px 4px; border-radius:3px; font-size:8px; font-weight:bold; }
  .badge-warn { background:#fef3c7; color:#92400e; padding:1px 4px; border-radius:3px; font-size:8px; font-weight:bold; }
  .badge-err  { background:#fee2e2; color:#991b1b; padding:1px 4px; border-radius:3px; font-size:8px; font-weight:bold; }
  .badge-muted{ background:#f3f4f6; color:#6b7280; padding:1px 4px; border-radius:3px; font-size:8px; }
  .order-block { border: 1px solid #ccc; margin: 3mm 0; page-break-inside: avoid; }
  .table-header { background: #1A365D; color: #fff; padding: 2mm 4mm; display: flex; justify-content: space-between; font-weight: bold; font-size: 12px; }
  .total-val { color: #FF7A85; }
  .order-meta { padding: 1mm 4mm; background: #f9f9f9; border-bottom: 1px solid #eee; }
  .item-row { display: flex; justify-content: space-between; padding: 1mm 4mm; }
  .discount-row { padding: 1mm 4mm; color: #c00; font-size: 9px; }
  .total-box { border-top: 2px solid #000; padding: 2mm 4mm; background: #f9f9f9; }
  .sep { padding: 0 4mm; color: #bbb; font-size: 9px; }
  .empty { padding: 3mm 4mm; color: #999; font-style: italic; }
  .profit-box { display:flex; justify-content:space-between; padding: 3mm 4mm; font-size: 13px; font-weight: bold; margin: 2mm 0; }
</style>
</head>
<body>

<div class="doc-header">
  <h1>FLAMINGO COUCOU BEACH — BILAN JOURNALIER</h1>
  <p>$dispDate &nbsp;·&nbsp; Généré le $genStr</p>
</div>

<div class="kpi-grid">
  <div class="kpi-cell"><div class="kpi-val" style="color:#FF7A85">${dt(summary.totalRevenue)}</div><div class="kpi-lbl">Revenus</div></div>
  <div class="kpi-cell"><div class="kpi-val" style="color:#e63946">${dt(summary.totalExpenses)}</div><div class="kpi-lbl">Dépenses</div></div>
  <div class="kpi-cell"><div class="kpi-val" style="color:${if (summary.netProfit >= 0) "#10b981" else "#e63946"}">${dt(summary.netProfit)}</div><div class="kpi-lbl">Bénéfice Net</div></div>
  <div class="kpi-cell"><div class="kpi-val" style="color:#1A365D">${summary.totalClients}</div><div class="kpi-lbl">Clients</div></div>
</div>

<div class="section-header">RÉSUMÉ FINANCIER</div>
${row("Revenus totaux", dt(summary.totalRevenue))}
${row("Ventes produits", dt(summary.totalProductSales))}
${row("Coût produits", dt(summary.totalProductCost))}
${row("Avances travailleurs", dt(summary.totalWorkerAdvances))}
${row("Paiements travailleurs", dt(summary.totalWorkerPayments))}
<div class="profit-box" style="background:${if (summary.netProfit >= 0) "#d1fae5" else "#fee2e2"};color:${if (summary.netProfit >= 0) "#065f46" else "#991b1b"}">
  <span>BÉNÉFICE NET</span><span>${dt(summary.netProfit)}</span>
</div>

$fishHtml

<div class="section-header">RÉSERVATIONS (${reservations.size})</div>
$resHtml

<div class="section-header">COMMANDES &amp; FACTURES (${orders.size} tables)</div>
$ordersHtml

${if (sales.isNotEmpty()) """<div class="section-header">VENTES PRODUITS (${sales.size} articles)</div>$salesHtml""" else ""}
${if (finHtml.isNotBlank()) """<div class="section-header">MOUVEMENTS FINANCIERS TRAVAILLEURS</div>$finHtml""" else ""}

</body>
</html>"""
    }

    private suspend fun buildStockReportHtml(): String {
        val db      = FirebaseFirestore.getInstance()
        val dateIso = selectedDateIso.ifBlank { isoFormatter.format(java.util.Date()) }
        val dispDate = displayFormatter.format(isoFormatter.parse(dateIso) ?: java.util.Date())
            .replaceFirstChar { it.uppercase() }
        val genStr  = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(java.util.Date())
        fun dt(d: Double) = String.format(Locale.FRANCE, "%.2f&nbsp;DT", d)

        val inventory = db.collection("inventory").get().await().documents
        val sales     = db.collection("sales").whereEqualTo("date", dateIso).get().await().documents
        val getQty = { doc: com.google.firebase.firestore.DocumentSnapshot ->
            (doc.getLong("stockQuantity") ?: doc.getLong("quantity") ?: 0L).toInt()
        }
        val getMin = { doc: com.google.firebase.firestore.DocumentSnapshot ->
            (doc.getLong("minStock") ?: doc.getLong("minimumStock") ?: 0L).toInt()
        }
        val totalValue    = inventory.sumOf { (it.getDouble("buyPrice") ?: 0.0) * getQty(it) }
        val criticalCount = inventory.count { val q = getQty(it); q > 0 && q <= getMin(it) }
        val outOfStock    = inventory.count { getQty(it) == 0 }

        val salesHtml = if (sales.isEmpty()) """<div class="empty">Aucune vente enregistrée.</div>"""
        else sales.joinToString("") { doc ->
            val name = doc.getString("productName") ?: "?"
            val qty  = doc.getLong("quantity") ?: 0L
            val tot  = doc.getDouble("totalPrice") ?: 0.0
            """<div class="row"><span>$name &times;$qty</span><span>${dt(tot)}</span></div>"""
        }

        val byCategory = inventory.groupBy { it.getString("category")?.trim()?.ifBlank { "Autre" } ?: "Autre" }
        val detailHtml = byCategory.entries.sortedBy { it.key }.joinToString("") { (cat, items) ->
            buildString {
                append("""<div class="cat-header">$cat</div>""")
                items.forEach { doc ->
                    val name   = doc.getString("name") ?: "?"
                    val qty    = getQty(doc); val min = getMin(doc)
                    val val_   = (doc.getDouble("buyPrice") ?: 0.0) * qty
                    val unit   = doc.getString("unit")?.let { " $it" } ?: ""
                    val status = when { qty == 0 -> "RUPTURE"; qty <= min -> "CRITIQUE"; else -> "OK" }
                    val stColor = when (status) { "RUPTURE" -> "#c00"; "CRITIQUE" -> "#e67e00"; else -> "#065f46" }
                    append("""<div class="row">
                      <span>$name</span>
                      <span>$qty$unit / min $min — ${dt(val_)} — <strong style="color:$stColor">$status</strong></span>
                    </div>""")
                }
            }
        }

        return """<!DOCTYPE html>
<html lang="fr"><head><meta charset="UTF-8"/><title>Bilan Stock — $dispDate</title>
<style>
  @page { margin: 8mm; size: A4; }
  * { margin:0; padding:0; box-sizing:border-box; }
  body { font-family: Arial, sans-serif; font-size: 11px; color: #000; line-height: 1.5; }
  .doc-header { background: #1A365D; color: #fff; padding: 6mm 5mm; }
  .doc-header h1 { font-size: 15px; font-weight: bold; }
  .doc-header p { font-size: 9px; opacity: 0.8; margin-top: 1mm; }
  .kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); border: 2px solid #1A365D; margin: 4mm 0; }
  .kpi-cell { text-align: center; padding: 4mm 2mm; border-right: 1px solid #1A365D; }
  .kpi-cell:last-child { border-right: none; }
  .kpi-val { font-size: 14px; font-weight: bold; }
  .kpi-lbl { font-size: 8px; color: #777; text-transform: uppercase; }
  .section-header { background: #1A365D; color: #fff; padding: 2mm 4mm; font-weight: bold; font-size: 10px; margin: 4mm 0 0; }
  .cat-header { background: #e8f0fb; color: #1A365D; padding: 1.5mm 4mm; font-weight: bold; border-bottom: 1px solid #c5d8f5; }
  .row { display: flex; justify-content: space-between; padding: 1.5mm 4mm; border-bottom: 1px solid #f0f0f0; background: #fafafa; }
  .empty { padding: 3mm 4mm; color: #999; font-style: italic; }
</style>
</head><body>
<div class="doc-header">
  <h1>FLAMINGO COUCOU BEACH — BILAN STOCK</h1>
  <p>$dispDate &nbsp;·&nbsp; Généré le $genStr</p>
</div>
<div class="kpi-grid">
  <div class="kpi-cell"><div class="kpi-val" style="color:#1A365D">${inventory.size}</div><div class="kpi-lbl">Articles</div></div>
  <div class="kpi-cell"><div class="kpi-val" style="color:#e67e00">$criticalCount</div><div class="kpi-lbl">Critiques</div></div>
  <div class="kpi-cell"><div class="kpi-val" style="color:#c00">$outOfStock</div><div class="kpi-lbl">Rupture</div></div>
  <div class="kpi-cell"><div class="kpi-val" style="color:#FF7A85">${dt(totalValue)}</div><div class="kpi-lbl">Valeur stock</div></div>
</div>
<div class="section-header">CONSOMMÉS AUJOURD'HUI (${sales.size} articles)</div>
$salesHtml
<div class="section-header">ÉTAT DU STOCK — TOUS LES ARTICLES</div>
$detailHtml
</body></html>"""
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
