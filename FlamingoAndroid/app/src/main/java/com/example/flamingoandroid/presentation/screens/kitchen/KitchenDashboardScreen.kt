package com.example.flamingoandroid.presentation.screens.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
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
    val filteredOrders  by viewModel.filteredOrders.collectAsState()
    val itemTotals      by viewModel.itemTotals.collectAsState()
    val dessertPerTable by viewModel.dessertPerTable.collectAsState()
    val pageTitle       by viewModel.pageTitle.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val errorMessage    by viewModel.errorMessage.collectAsState()

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
                    }
                    Divider(color = Outline, thickness = 1.dp)
                }

                // ── Desserts par table ───────────────────────────────────
                if (dessertPerTable.isNotEmpty()) {
                    val totalDesserts = dessertPerTable.sumOf { it.count }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface)
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "DESSERTS PAR TABLE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                text = "$totalDesserts plat${if (totalDesserts > 1) "s" else ""} total",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(dessertPerTable, key = { it.table }) { entry ->
                                DessertChip(entry)
                            }
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
                    tabs.forEach { tab ->
                        val count = tabOrders[tab.status]?.size ?: 0
                        val isActive = activeTab == tab.status
                        val textOnActive = when (tab.color) {
                            Color(0xFF2EC4B6) -> Color(0xFF001E1B) // dark on teal
                            else              -> Color(0xFF1A0A00)  // dark on amber/coral
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isActive) tab.color else Raised)
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) tab.color else tab.color.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .clickable { activeTab = tab.status }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isActive) textOnActive else tab.color,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isActive) textOnActive.copy(alpha = 0.18f) else tab.color.copy(alpha = 0.18f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActive) textOnActive else tab.color,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
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
            } else if (visibleOrders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Teal.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Aucune commande « ${tabs.find { it.status == activeTab }?.label ?: activeTab} »",
                            style = MaterialTheme.typography.titleMedium,
                            color = Pearl,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Les nouvelles commandes apparaîtront ici",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mist,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
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
                        style = MaterialTheme.typography.labelSmall,
                        color = urgencyColor,
                        fontWeight = FontWeight.SemiBold,
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(Raised)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.SemiBold,
                                color = Pearl,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (item.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.notes,
                                    color = Amber.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Amber.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "×${item.quantity}",
                                color = Amber,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                            )
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
private fun DessertChip(entry: DessertEntry) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Raised)
            .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = entry.table,
            color = Pearl,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Text(
            text = "${entry.persons} pers.",
            color = Mist,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Amber)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${entry.count} plt",
                color = Color(0xFF1A0A00),
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
            )
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
