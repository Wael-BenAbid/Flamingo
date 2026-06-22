package com.example.flamingoandroid.presentation.screens.dailyorders

import android.content.Context
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.models.TableOrder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens ──────────────────────────────────────────────────────────────
private val Ocean    = Color(0xFF0B1628)
private val Surface  = Color(0xFF13203A)
private val Raised   = Color(0xFF1C2E4A)
private val Outline  = Color(0xFF2E4560)
private val Teal     = Color(0xFF2EC4B6)
private val Pearl    = Color(0xFFF0EEE8)
private val Mist     = Color(0xFF9DB4C0)
private val Flamingo = Color(0xFFFF7A85)
private val Jade     = Color(0xFF2DC653)
private val Amber    = Color(0xFFF59B35)

private fun fmtDt(d: Double) = String.format(Locale.FRANCE, "%.2f DT", d)

// ── ViewModel ──────────────────────────────────────────────────────────────────

class DailyOrdersViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _orders    = MutableStateFlow<List<TableOrder>>(emptyList())
    val orders: StateFlow<List<TableOrder>> = _orders

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadOrdersForDate(date: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val start = Calendar.getInstance().apply {
                    time = date
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                val end = Calendar.getInstance().apply {
                    time = date
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }.time

                val snap = db.collection("table_orders")
                    .whereGreaterThanOrEqualTo("created_at", Timestamp(start))
                    .whereLessThanOrEqualTo("created_at", Timestamp(end))
                    .orderBy("created_at", Query.Direction.ASCENDING)
                    .get().await()

                _orders.value = snap.documents
                    .mapNotNull { it.toObject(TableOrder::class.java)?.copy(id = it.id) }
                    .filter { it.status != "cancelled" }
            } catch (_: Exception) {
                _orders.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// ── Main screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyOrdersScreen(onBack: () -> Unit = {}) {
    val context   = LocalContext.current
    val viewModel = remember { DailyOrdersViewModel() }
    val orders    by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedDate by remember { mutableStateOf(Date()) }
    val dateFmt    = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFmt = remember { SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRANCE) }

    LaunchedEffect(selectedDate) { viewModel.loadOrdersForDate(selectedDate) }

    val grandTotal  = orders.sumOf { it.finalTotal }
    val totalItems  = orders.sumOf { o -> o.items.sumOf { it.quantity } }
    val totalPeople = orders.sumOf { it.adults + it.children }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Pearl)
                    }
                },
                title = {
                    Column {
                        Text("Commandes du Jour",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = Pearl)
                        Text("Flamingo Coucou Beach",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist, letterSpacing = 2.sp)
                    }
                },
                actions = {
                    if (orders.isNotEmpty()) {
                        IconButton(onClick = {
                            val html = buildDailyOrdersHtml(orders, selectedDate)
                            printReport(context, html, "Commandes-${dateFmt.format(selectedDate)}")
                        }) {
                            Icon(Icons.Default.Print, contentDescription = "Imprimer", tint = Flamingo)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = Ocean,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Date navigator ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = {
                    selectedDate = Calendar.getInstance().apply {
                        time = selectedDate; add(Calendar.DAY_OF_MONTH, -1)
                    }.time
                }) {
                    Icon(Icons.Default.KeyboardArrowLeft, null, tint = Pearl)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayFmt.format(selectedDate).replaceFirstChar { it.uppercase() },
                        color = Pearl, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (orders.isEmpty()) "Aucune commande"
                               else "${orders.size} table${if (orders.size > 1) "s" else ""}  ·  ${fmtDt(grandTotal)}",
                        color = if (orders.isEmpty()) Mist else Flamingo,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                IconButton(onClick = {
                    selectedDate = Calendar.getInstance().apply {
                        time = selectedDate; add(Calendar.DAY_OF_MONTH, 1)
                    }.time
                }) {
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = Pearl)
                }
            }

            // ── Content ────────────────────────────────────────────────────
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Flamingo)
                }
                orders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Aucune commande", color = Mist,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("pour ce jour", color = Mist.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SummaryCard(
                            tableCount = orders.size,
                            totalPeople = totalPeople,
                            totalItems = totalItems,
                            grandTotal = grandTotal,
                        )
                    }

                    items(orders, key = { it.id }) { order ->
                        OrderCard(order = order)
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val html = buildDailyOrdersHtml(orders, selectedDate)
                                printReport(context, html, "Commandes-${dateFmt.format(selectedDate)}")
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Flamingo),
                        ) {
                            Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Imprimer le rapport du jour", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ── Summary card ───────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(tableCount: Int, totalPeople: Int, totalItems: Int, grandTotal: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("RÉSUMÉ DU JOUR", color = Mist,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem("Tables",   tableCount.toString())
                StatItem("Clients",  totalPeople.toString())
                StatItem("Articles", totalItems.toString())
                StatItem("Total",    fmtDt(grandTotal), highlight = true)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = if (highlight) Flamingo else Pearl,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, color = Mist, style = MaterialTheme.typography.labelSmall)
    }
}

// ── Order card ─────────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(order: TableOrder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.fillMaxWidth()) {

            // Header: table + serveur + total
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Raised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Table ${order.table_number}", color = Pearl,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    if (order.server_name.isNotBlank()) {
                        Text("Serveur : ${order.server_name}", color = Flamingo,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Column(horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(fmtDt(order.finalTotal), color = Jade,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    if (order.discountPercent > 0) {
                        Text("Remise ${order.discountPercent}%", color = Amber,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Client / personnes
            if (order.clientName.isNotBlank() || order.adults > 0 || order.children > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (order.clientName.isNotBlank())
                        Text("Client : ${order.clientName}", color = Mist,
                            style = MaterialTheme.typography.bodySmall)
                    if (order.adults > 0 || order.children > 0)
                        Text("${order.adults}A · ${order.children}ENF", color = Mist,
                            style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(color = Outline, thickness = 0.5.dp)
            }

            // Articles
            if (order.items.isEmpty()) {
                Text("(aucun article)", color = Mist.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp))
            } else {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Teal.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("×${item.quantity}", color = Teal,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(item.name, color = Pearl,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                        }
                        Text(fmtDt(item.unit_price * item.quantity), color = Mist,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(color = Outline, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("TOTAL NET", color = Pearl, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium)
                    Text(fmtDt(order.finalTotal), color = Jade, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

// ── HTML builder ───────────────────────────────────────────────────────────────

private fun buildDailyOrdersHtml(orders: List<TableOrder>, date: Date): String {
    val dateStr    = SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRANCE).format(date)
        .replaceFirstChar { it.uppercase() }
    val genStr     = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date())
    val grandTotal = orders.sumOf { it.finalTotal }
    val THIN       = "─".repeat(40)

    fun row(l: String, r: String) =
        """<div class="row"><span>$l</span><span class="bold">$r</span></div>"""
    fun dt(d: Double) = String.format(Locale.FRANCE, "%.2f&nbsp;DT", d)

    val ordersHtml = orders.joinToString("\n") { order ->
        val itemsHtml = if (order.items.isEmpty()) {
            """<div class="hint">(aucun article enregistré)</div>"""
        } else buildString {
            appendLine("""<div class="section-title">ARTICLES</div>""")
            order.items.forEach { item ->
                appendLine("""
                    <div class="row">
                      <span>${item.quantity}&times;&nbsp;${item.name}</span>
                      <span class="bold">${dt(item.unit_price * item.quantity)}</span>
                    </div>
                    <div class="hint">@ ${dt(item.unit_price)} / unité</div>
                """.trimIndent())
            }
            appendLine("""<div class="sep thin">$THIN</div>""")
            if (order.discountPercent > 0) {
                val orderSubtotal = order.items.sumOf { it.unit_price * it.quantity }
                appendLine(row("Sous-total :", dt(orderSubtotal)))
                appendLine("""<div class="row discount"><span>Remise ${order.discountPercent}%</span><span>&minus;&nbsp;${dt(orderSubtotal * order.discountPercent / 100.0)}</span></div>""")
            }
        }

        """
        <div class="order-block">
          <div class="table-header">
            <span>TABLE&nbsp;${order.table_number}</span>
            <span class="total-inline">${dt(order.finalTotal)}</span>
          </div>
          <div class="order-meta">
            ${if (order.server_name.isNotBlank()) row("Serveur&nbsp;:", "<strong>${order.server_name}</strong>") else ""}
            ${if (order.clientName.isNotBlank()) row("Client&nbsp;:", order.clientName) else ""}
            ${if (order.adults > 0 || order.children > 0) row("Personnes&nbsp;:", "${order.adults}A + ${order.children}ENF") else ""}
            ${if (order.source == "walkin") "<div class='source-badge'>Walk-in</div>" else ""}
          </div>
          $itemsHtml
          <div class="total-box">
            <div class="row total-row">
              <span>TOTAL NET</span><span>${dt(order.finalTotal)}</span>
            </div>
          </div>
        </div>
        """.trimIndent()
    }

    return """<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8"/>
<title>Commandes du Jour — $dateStr</title>
<style>
  @page { margin: 8mm; size: A4; }
  * { margin:0; padding:0; box-sizing:border-box; }
  body { font-family: 'Courier New', Courier, monospace; font-size: 11px; color: #000; line-height: 1.55; }
  .doc-header { background: #1A365D; color: #fff; padding: 6mm 5mm; margin-bottom: 5mm; }
  .doc-header h1 { font-size: 15px; font-weight: bold; letter-spacing: 1px; }
  .doc-header p { font-size: 9px; opacity: 0.75; margin-top: 1.5mm; }
  .summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); border: 2px solid #1A365D; margin-bottom: 5mm; }
  .summary-cell { text-align: center; padding: 4mm 2mm; border-right: 1px solid #1A365D; }
  .summary-cell:last-child { border-right: none; }
  .summary-cell .val { font-size: 16px; font-weight: bold; color: #FF7A85; }
  .summary-cell .lbl { font-size: 8px; color: #666; letter-spacing: 1px; text-transform: uppercase; }
  .order-block { border: 1px solid #bbb; margin-bottom: 5mm; page-break-inside: avoid; }
  .table-header { background: #1A365D; color: #fff; padding: 3mm 4mm; display: flex; justify-content: space-between; align-items: center; font-weight: bold; font-size: 13px; }
  .total-inline { color: #FF7A85; font-size: 14px; }
  .order-meta { padding: 2mm 4mm; border-bottom: 1px solid #eee; background: #f9f9f9; }
  .row { display: flex; justify-content: space-between; gap: 4px; padding: 1mm 4mm; }
  .bold { font-weight: bold; }
  .hint { font-size: 9px; color: #777; padding: 0 4mm 0 8mm; }
  .sep { padding: 0.5mm 4mm; color: #999; }
  .thin { color: #ccc; }
  .discount { color: #c00; padding: 0 4mm; }
  .source-badge { display: inline-block; background: #FCD34D; color: #333; font-size: 8px; font-weight: bold; padding: 0.5mm 2mm; border-radius: 2mm; margin: 1mm 4mm; }
  .section-title { font-weight: bold; font-size: 10px; letter-spacing: 1px; border-bottom: 1px solid #ddd; padding: 1.5mm 4mm; margin: 1mm 0; background: #f5f5f5; }
  .total-box { border-top: 2px solid #000; padding: 2mm 4mm; background: #f9f9f9; }
  .total-row { font-size: 13px; font-weight: bold; }
</style>
</head>
<body>

<div class="doc-header">
  <h1>FLAMINGO COUCOU BEACH — COMMANDES DU JOUR</h1>
  <p>$dateStr &nbsp;·&nbsp; Généré le $genStr</p>
</div>

<div class="summary-grid">
  <div class="summary-cell"><div class="val">${orders.size}</div><div class="lbl">Tables</div></div>
  <div class="summary-cell"><div class="val">${orders.sumOf { it.adults + it.children }}</div><div class="lbl">Clients</div></div>
  <div class="summary-cell"><div class="val">${orders.sumOf { o -> o.items.sumOf { it.quantity } }}</div><div class="lbl">Articles</div></div>
  <div class="summary-cell"><div class="val">${dt(grandTotal)}</div><div class="lbl">Total</div></div>
</div>

$ordersHtml

</body>
</html>"""
}

// ── Print helper ───────────────────────────────────────────────────────────────

private fun printReport(context: Context, html: String, jobName: String) {
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
