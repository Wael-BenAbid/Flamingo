package com.example.flamingoandroid.presentation.screens.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.flamingoandroid.presentation.viewmodels.DessertEntry
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flamingoandroid.data.models.TableOrder
import com.example.flamingoandroid.presentation.viewmodels.KitchenDashboardViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Flamingo design tokens ───────────────────────────────────────────────
private val Ocean   = Color(0xFF0B1628)
private val Surface = Color(0xFF13203A)
private val Raised  = Color(0xFF1C2E4A)
private val Outline = Color(0xFF2E4560)
private val Amber   = Color(0xFFF59B35)
private val Coral   = Color(0xFFE8603A)
private val Teal    = Color(0xFF2EC4B6)
private val Pearl   = Color(0xFFF0EEE8)
private val Mist    = Color(0xFF9DB4C0)
private val Crimson = Color(0xFFE63946)
private val Jade    = Color(0xFF2DC653)

private data class TabDef(val status: String, val label: String, val color: Color)
private val tabs = listOf(
    TabDef("pending",   "En attente",     Color(0xFFF59B35)), // Amber
    TabDef("preparing", "En préparation", Color(0xFFE8603A)), // Coral
    TabDef("ready",     "Prêtes",         Color(0xFF2EC4B6)), // Teal
)

private fun computeTotals(orders: List<TableOrder>): List<Pair<String, Int>> {
    val map = linkedMapOf<String, Pair<String, Int>>()
    for (order in orders) {
        for (item in order.items) {
            if (item.name.isBlank()) continue
            val key = item.item_id.ifBlank { item.name }
            val prev = map[key]?.second ?: 0
            map[key] = item.name to (prev + item.quantity)
        }
    }
    return map.values.sortedByDescending { it.second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenDashboardScreen(
    viewModel: KitchenDashboardViewModel = viewModel(),
) {
    val filteredOrders      by viewModel.filteredOrders.collectAsState()
    val itemTotals          by viewModel.itemTotals.collectAsState()
    val dessertPerTable     by viewModel.dessertPerTable.collectAsState()
    val servedDessertTables by viewModel.servedDessertTables.collectAsState()
    val pageTitle           by viewModel.pageTitle.collectAsState()
    val isLoading           by viewModel.isLoading.collectAsState()
    val errorMessage        by viewModel.errorMessage.collectAsState()

    var activeTab by remember { mutableStateOf("pending") }

    val tabOrders by remember(filteredOrders) {
        derivedStateOf {
            mapOf(
                "pending"   to filteredOrders.filter { it.status == "pending" },
                "preparing" to filteredOrders.filter { it.status == "preparing" },
                "ready"     to filteredOrders.filter { it.status == "ready" },
            )
        }
    }
    val visibleOrders = tabOrders[activeTab] ?: emptyList()

    val pendingTotals   = remember(filteredOrders) { computeTotals(filteredOrders.filter { it.status == "pending" }) }
    val preparingTotals = remember(filteredOrders) { computeTotals(filteredOrders.filter { it.status == "preparing" }) }

    val totalDesserts       = remember(dessertPerTable) { dessertPerTable.sumOf { it.count } }
    val servedDessertsCount = remember(dessertPerTable, servedDessertTables) {
        dessertPerTable.filter { it.table in servedDessertTables }.sumOf { it.count }
    }
    val remainingDesserts   = totalDesserts - servedDessertsCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Pearl,
                        )
                        Text(
                            text = "${filteredOrders.size} commande${if (filteredOrders.size != 1) "s" else ""} active${if (filteredOrders.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                ),
            )
        },
        containerColor = Ocean,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Totals (global + en attente + en préparation) ────────────
            if (filteredOrders.isNotEmpty()) {
                if (itemTotals.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface)
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TotalsRow(label = "TOTAL EN COURS", color = Mist,  totals = itemTotals)
                        if (pendingTotals.isNotEmpty())
                            TotalsRow(label = "EN ATTENTE",     color = Amber, totals = pendingTotals)
                        if (preparingTotals.isNotEmpty())
                            TotalsRow(label = "EN PRÉPARATION", color = Coral, totals = preparingTotals)
                        if (dessertPerTable.isNotEmpty()) {
                            val dessertSummary = buildList {
                                add("total" to totalDesserts)
                                if (remainingDesserts > 0) add("restants" to remainingDesserts)
                                else add("servis ✓" to totalDesserts)
                            }
                            TotalsRow(
                                label  = "🍮  DESSERTS",
                                color  = if (remainingDesserts > 0) Amber else Jade,
                                totals = dessertSummary,
                            )
                        }
                    }
                    Divider(color = Outline, thickness = 1.dp)
                }

                // ── Status tabs ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Onglets commandes (En attente / En préparation / Prêtes)
                    tabs.forEach { tab ->
                        val count = tabOrders[tab.status]?.size ?: 0
                        val isActive = activeTab == tab.status
                        val darkOnActive = Color(0xFF1A0A00)
                        KitchenTabChip(
                            label    = tab.label,
                            count    = count,
                            color    = tab.color,
                            isActive = isActive,
                            darkText = darkOnActive,
                            onClick  = { activeTab = tab.status },
                        )
                    }

                    // Onglet Desserts — visible uniquement si des desserts existent
                    if (dessertPerTable.isNotEmpty()) {
                        val isActive = activeTab == "desserts"
                        KitchenTabChip(
                            label    = "🍮 Desserts",
                            count    = remainingDesserts,
                            color    = if (remainingDesserts > 0) Amber else Jade,
                            isActive = isActive,
                            darkText = Color(0xFF1A0A00),
                            onClick  = { activeTab = "desserts" },
                        )
                    }
                }
                Divider(color = Outline, thickness = 1.dp)
            }

            // ── Error ────────────────────────────────────────────────────
            val err = errorMessage
            if (!err.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Crimson.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Crimson, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = err, color = Crimson, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── Content ───────────────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (activeTab == "desserts") {
                        // ── Onglet Desserts ────────────────────────────────
                        item(key = "dessert-header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "🍮  DESSERTS PAR TABLE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (remainingDesserts > 0) Amber else Jade,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                )
                                Text(
                                    text = if (remainingDesserts > 0)
                                        "$remainingDesserts restant${if (remainingDesserts > 1) "s" else ""}"
                                    else "Tous servis ✓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (remainingDesserts > 0) Amber else Jade,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (dessertPerTable.isEmpty()) {
                            item(key = "desserts-empty") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("Aucun dessert à afficher", color = Mist,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Configurez le ratio dans Menus & Tables",
                                        color = Mist.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            items(dessertPerTable, key = { "dessert-${it.table}" }) { entry ->
                                DessertCard(
                                    entry    = entry,
                                    isServed = entry.table in servedDessertTables,
                                    onSortir = { viewModel.markDessertServed(entry.table) },
                                    onRevenir= { viewModel.unmarkDessertServed(entry.table) },
                                )
                            }
                        }
                    } else {
                        // ── Onglets commandes (En attente / En préparation / Prêtes) ──
                        if (visibleOrders.isEmpty()) {
                            item(key = "orders-empty") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                                        tint = Teal.copy(alpha = 0.5f), modifier = Modifier.size(52.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Aucune commande « ${tabs.find { it.status == activeTab }?.label ?: activeTab} »",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Pearl, fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Les nouvelles commandes apparaîtront ici",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Mist, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            items(visibleOrders, key = { it.id }) { order ->
                                KitchenTicketCard(
                                    order = order,
                                    onSetStatus = { status -> viewModel.setStatus(order.id, status) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KitchenTabChip(
    label: String,
    count: Int,
    color: Color,
    isActive: Boolean,
    darkText: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) color else Raised)
            .border(1.dp, if (isActive) color else color.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) darkText else color,
                fontWeight = FontWeight.SemiBold,
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isActive) darkText.copy(alpha = 0.18f) else color.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) darkText else color,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsRow(label: String, color: Color, totals: List<Pair<String, Int>>) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            totals.forEach { (name, qty) ->
                TotalChip(name = name, quantity = qty, accentColor = color)
            }
        }
    }
}

@Composable
private fun KitchenTicketCard(
    order: TableOrder,
    onSetStatus: (String) -> Unit,
) {
    val elapsedLabel = rememberElapsedTime(order)
    val elapsedMinutes = rememberElapsedMinutes(order)

    // Urgency color based on wait time
    val urgencyColor = when {
        elapsedMinutes >= 20 -> Crimson
        elapsedMinutes >= 10 -> Coral
        else                 -> Amber
    }

    val statusColor = when (order.status) {
        "preparing" -> Coral
        "ready"     -> Teal
        else        -> Amber
    }

    val statusLabel = when (order.status) {
        "pending"   -> "EN ATTENTE"
        "preparing" -> "EN PRÉPARATION"
        "ready"     -> "PRÊT"
        else        -> order.status.uppercase(Locale.getDefault())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(
            width = if (elapsedMinutes >= 15) 2.dp else 1.dp,
            color = if (elapsedMinutes >= 15) urgencyColor.copy(alpha = 0.6f) else Outline,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = order.table_number,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Pearl,
                    )
                    Text(
                        text = order.server_name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                    Text(
                        text = elapsedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = urgencyColor,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!order.scheduled_time.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Amber.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "⏱ ${order.scheduled_time}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Divider(color = Outline.copy(alpha = 0.5f), thickness = 1.dp)

            // ── Items ────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Raised)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Quantité — grande et lisible à gauche
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Amber),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${item.quantity}",
                                color = Color(0xFF1A0800),
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        // Nom + note
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                color = Pearl,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (item.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "📝 ${item.notes}",
                                    color = Amber.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // ── Action buttons ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Back button
                when (order.status) {
                    "preparing" -> OutlinedButton(
                        onClick = { onSetStatus("pending") },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                    ) {
                        Text("← En attente", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    }
                    "ready" -> OutlinedButton(
                        onClick = { onSetStatus("preparing") },
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Coral.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral),
                    ) {
                        Text("← En prép.", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Forward button
                when (order.status) {
                    "pending" -> Button(
                        onClick = { onSetStatus("preparing") },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A0A00)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Commencer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                    "preparing" -> Button(
                        onClick = { onSetStatus("ready") },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color(0xFF001E1B)),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prête", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                    "ready" -> Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Teal.copy(alpha = 0.12f))
                            .border(1.dp, Teal.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Commande prête", color = Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DessertCard(
    entry: DessertEntry,
    isServed: Boolean,
    onSortir: () -> Unit,
    onRevenir: () -> Unit,
) {
    val borderColor = if (isServed) Jade.copy(alpha = 0.45f) else Outline
    val bgColor     = if (isServed) Jade.copy(alpha = 0.07f) else Surface
    val accentColor = if (isServed) Jade else Amber

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Table + personnes
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = entry.table,
                    color = Pearl,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${entry.persons} personne${if (entry.persons > 1) "s" else ""}",
                    color = Mist,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Nombre de plats + bouton
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${entry.count} plt${if (entry.count > 1) "s" else ""}",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (isServed) {
                    OutlinedButton(
                        onClick = onRevenir,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Text("← Retour", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Button(
                        onClick = onSortir,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Jade),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = "Sorti ✓",
                            color = Color(0xFF001E0F),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalChip(name: String, quantity: Int, accentColor: Color = Amber) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Raised)
            .border(1.dp, accentColor.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = Pearl,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(accentColor)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$quantity",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF0B1628),
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun rememberElapsedMinutes(order: TableOrder): Long {
    var now by remember(order.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(order.id) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val createdAtMillis = order.created_at?.toDate()?.time ?: now
    return TimeUnit.MILLISECONDS.toMinutes((now - createdAtMillis).coerceAtLeast(0))
}

@Composable
private fun rememberElapsedTime(order: TableOrder): String {
    val elapsedMinutes = rememberElapsedMinutes(order)
    return when {
        elapsedMinutes < 1  -> "À l'instant"
        elapsedMinutes < 60 -> "$elapsedMinutes min"
        else -> {
            val hours = elapsedMinutes / 60
            val mins  = elapsedMinutes % 60
            String.format(Locale.FRANCE, "%02dh%02d", hours, mins)
        }
    }
}
