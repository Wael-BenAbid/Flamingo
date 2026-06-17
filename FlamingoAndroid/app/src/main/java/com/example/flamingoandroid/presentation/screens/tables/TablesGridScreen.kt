package com.example.flamingoandroid.presentation.screens.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.presentation.viewmodels.TablesUiState
import com.example.flamingoandroid.presentation.viewmodels.TablesViewModel

private val Amber   = Color(0xFFF59B35)
private val Coral   = Color(0xFFE8603A)
private val Teal    = Color(0xFF2EC4B6)
private val Ocean   = Color(0xFF0B1628)
private val Surface = Color(0xFF13203A)
private val Raised  = Color(0xFF1C2E4A)
private val Outline = Color(0xFF2E4560)
private val Pearl   = Color(0xFFF0EEE8)
private val Mist    = Color(0xFF9DB4C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablesGridScreen(
    viewModel: TablesViewModel,
    onTableClick: (String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Retour",
                            tint = Pearl,
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Prendre une commande",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Pearl,
                        )
                        Text(
                            text = "Océan Flamingo",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist,
                            letterSpacing = 2.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = Pearl,
                ),
            )
        },
        containerColor = Ocean,
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val currentState = state) {
                TablesUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Amber,
                    )
                }

                is TablesUiState.Success -> {
                    if (currentState.positions.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Aucune zone configurée",
                                style = MaterialTheme.typography.titleMedium,
                                color = Pearl,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Configurez les tables dans\nMenus & Tables sur le web",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Mist,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(
                                items = currentState.positions,
                                key = { position -> position.id.ifBlank { position.type } },
                            ) { position ->
                                PositionSectionCard(
                                    position = position,
                                    activeOrders = currentState.activeOrders,
                                    clientsPerTable = currentState.clientsPerTable,
                                    onTableClick = onTableClick,
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
private fun PositionSectionCard(
    position: Position,
    activeOrders: Map<String, String>,
    clientsPerTable: Map<String, Pair<Int, Int>>,
    onTableClick: (String) -> Unit,
) {
    val tableCount = position.count.coerceAtLeast(0)
    val positionType = position.type.trim()
    val tableLabels = List(tableCount) { index -> "$positionType ${index + 1}" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = positionType,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Pearl,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Amber.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "$tableCount place${if (tableCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Amber,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }
            }

            if (tableLabels.isEmpty()) {
                Text(
                    text = "Aucune table définie dans cette zone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            } else {
                tableLabels.chunked(3).forEach { rowLabels ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowLabels.forEach { tableLabel ->
                            TableTile(
                                tableLabel = tableLabel,
                                status = activeOrders[tableLabel],
                                clients = clientsPerTable[tableLabel],
                                modifier = Modifier.weight(1f),
                                onClick = { onTableClick(tableLabel) },
                            )
                        }
                        repeat(3 - rowLabels.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Status legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusLegendDot(color = Teal,  label = "Libre")
                StatusLegendDot(color = Amber, label = "En attente")
                StatusLegendDot(color = Coral, label = "En cuisine")
            }
        }
    }
}

@Composable
private fun StatusLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Mist)
    }
}

@Composable
private fun TableTile(
    tableLabel: String,
    status: String?,
    clients: Pair<Int, Int>?,   // (adults, children) from confirmed reservation
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (containerColor, borderColor, statusText, textColor) = when (status) {
        "pending"   -> TableTileStyle(Amber.copy(alpha = 0.18f),  Amber,  "En attente", Amber)
        "preparing" -> TableTileStyle(Coral.copy(alpha = 0.18f),  Coral,  "En cuisine", Coral)
        "ready"     -> TableTileStyle(Teal.copy(alpha = 0.18f),   Teal,   "Prête",      Teal)
        else        -> TableTileStyle(Teal.copy(alpha = 0.10f),   Outline,"Libre",      Teal)
    }

    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Position number
            Text(
                text = tableLabel.substringAfterLast(" "),
                color = Pearl,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusText,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.5.sp,
            )
            // nb A / ENF — shown only when a confirmed client is assigned
            if (clients != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${clients.first}A · ${clients.second}ENF",
                    color = Mist,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

private data class TableTileStyle(
    val containerColor: Color,
    val borderColor: Color,
    val statusText: String,
    val textColor: Color,
)
