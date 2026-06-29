package com.example.flamingoandroid.presentation.screens.menu

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flamingoandroid.data.models.MenuCategory
import com.example.flamingoandroid.data.models.MenuItem
import com.example.flamingoandroid.data.models.OrderCartLine
import com.example.flamingoandroid.data.models.TableOrder
import androidx.activity.compose.BackHandler
import com.example.flamingoandroid.presentation.viewmodels.MenuOrderingViewModel
import com.example.flamingoandroid.presentation.viewmodels.TablesViewModel
import com.example.flamingoandroid.presentation.viewmodels.TablesUiState
import com.example.flamingoandroid.presentation.screens.tables.TablesGridScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

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

@Composable
fun TableOrderingActivityContent(
    serverId: String,
    serverName: String,
    onExit: () -> Unit = {},
    viewModel: MenuOrderingViewModel = viewModel(),
    tablesViewModel: TablesViewModel = viewModel(),
) {
    val selectedTable  by viewModel.selectedTable.collectAsState()
    val categories     by viewModel.categories.collectAsState()
    val menuItems      by viewModel.menuItems.collectAsState()
    val cart           by viewModel.cart.collectAsState()
    val activeOrders   by viewModel.activeOrders.collectAsState()
    val isSubmitting   by viewModel.isSubmitting.collectAsState()
    val errorMessage   by viewModel.errorMessage.collectAsState()
    val activeOrderId  by viewModel.activeOrderId.collectAsState()
    val scheduledTime  by viewModel.scheduledTime.collectAsState()
    val tablesState    by tablesViewModel.uiState.collectAsState()

    // Adultes/Enfants de la table sélectionnée (depuis les réservations confirmées)
    val clients: Pair<Int, Int>? = selectedTable?.let { label ->
        (tablesState as? TablesUiState.Success)?.clientsPerTable?.get(label)
    }

    var tabIndex by remember { mutableStateOf(0) }

    // Retour physique : si une table est sélectionnée → revenir à la grille ; sinon → onExit (dialogue quitter dans l'Activity)
    BackHandler(enabled = selectedTable != null) {
        viewModel.clearSelection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ocean),
    ) {
        if (selectedTable == null) {
            TablesGridScreen(
                viewModel = tablesViewModel,
                onTableClick = { tableLabel -> viewModel.selectTable(tableLabel) },
                onBack = onExit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            TableOrderScreen(
                tableLabel    = selectedTable!!,
                serverName    = serverName,
                clients       = clients,
                categories    = categories,
                menuItems     = menuItems,
                cart          = cart,
                activeOrders  = activeOrders,
                activeOrderId = activeOrderId,
                scheduledTime = scheduledTime,
                onScheduledTimeChange = viewModel::setScheduledTime,
                tabIndex      = tabIndex,
                onTabChanged  = { tabIndex = it },
                onAddItem     = { viewModel.addItem(it) },
                onQuantityChange = { itemId, qty -> viewModel.updateQuantity(itemId, qty) },
                onNotesChange = { itemId, note -> viewModel.updateNotes(itemId, note) },
                onRemoveItem  = { viewModel.removeItem(it) },
                onSendOrder   = { viewModel.sendOrder(serverId, serverName) },
                onBack        = { viewModel.clearSelection() },
                isSubmitting  = isSubmitting,
                errorMessage  = errorMessage,
                onDismissError = viewModel::dismissError,
                modifier      = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun isMainDishCategory(name: String): Boolean {
    val n = name.lowercase().trim()
        .replace('é', 'e').replace('è', 'e').replace('ê', 'e').replace('à', 'a')
    return n.contains("plat")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOrderScreen(
    tableLabel: String,
    serverName: String,
    clients: Pair<Int, Int>?,          // (adults, children) de la réservation
    categories: List<MenuCategory>,
    menuItems: List<MenuItem>,
    cart: List<OrderCartLine>,
    activeOrders: List<TableOrder>,
    activeOrderId: String?,
    scheduledTime: String?,
    onScheduledTimeChange: (String?) -> Unit,
    tabIndex: Int,
    onTabChanged: (Int) -> Unit,
    onAddItem: (MenuItem) -> Unit,
    onQuantityChange: (String, Int) -> Unit,
    onNotesChange: (String, String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSendOrder: () -> Unit,
    onBack: () -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedCategories = remember(categories) { categories.sortedBy { it.display_order } }
    val currentCategory  = sortedCategories.getOrNull(tabIndex)
    val itemsByCategory  = remember(menuItems) { menuItems.groupBy { it.category_id.ifBlank { "__uncategorized__" } } }
    val total = cart.sumOf { it.unit_price * it.quantity }

    // IDs des catégories "plat" (contient "plat" dans le nom)
    val mainDishCatIds = remember(sortedCategories) {
        sortedCategories.filter { isMainDishCategory(it.name) }.map { it.id }.toSet()
    }
    val maxPersons = clients?.let { it.first + it.second }

    // Dialogue dépassement plats
    var limitDialogMsg by remember { mutableStateOf<String?>(null) }

    // Wrapper qui vérifie la limite avant d'ajouter
    val safeAddItem: (MenuItem) -> Unit = { item ->
        if (item.category_id in mainDishCatIds && maxPersons != null) {
            val currentTotal = cart
                .filter { line -> menuItems.firstOrNull { it.id == line.item_id }?.category_id in mainDishCatIds }
                .sumOf { it.quantity }
            if (currentTotal >= maxPersons) {
                limitDialogMsg = "Maximum atteint : $maxPersons plat${if (maxPersons > 1) "s" else ""} pour ${clients!!.first}A + ${clients.second}ENF"
            } else {
                onAddItem(item)
            }
        } else {
            onAddItem(item)
        }
    }

    val safeQuantityChange: (String, Int) -> Unit = { itemId, newQty ->
        val item = menuItems.firstOrNull { it.id == itemId }
        if (item?.category_id in mainDishCatIds && maxPersons != null && newQty > (cart.firstOrNull { it.item_id == itemId }?.quantity ?: 0)) {
            val currentTotal = cart
                .filter { line -> menuItems.firstOrNull { it.id == line.item_id }?.category_id in mainDishCatIds }
                .sumOf { it.quantity }
            if (currentTotal >= maxPersons) {
                limitDialogMsg = "Maximum atteint : $maxPersons plat${if (maxPersons > 1) "s" else ""} pour ${clients!!.first}A + ${clients.second}ENF"
            } else {
                onQuantityChange(itemId, newQty)
            }
        } else {
            onQuantityChange(itemId, newQty)
        }
    }

    // ── Dialogue dépassement plats ───────────────────────────────────────
    limitDialogMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { limitDialogMsg = null },
            confirmButton = {
                TextButton(onClick = { limitDialogMsg = null }) {
                    Text("OK", color = Amber, fontWeight = FontWeight.Bold)
                }
            },
            title = { Text("Nombre dépassé", color = Pearl, fontWeight = FontWeight.Bold) },
            text = { Text(msg, color = Mist) },
            containerColor = Surface,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        )
    }

    Column(
        modifier = modifier.background(Ocean),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────
        TopAppBar(
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tableLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Pearl,
                        )
                        if (clients != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Teal.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "${clients.first}A · ${clients.second}ENF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Teal,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Serveur: $serverName",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mist,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Pearl)
                }
            },
            actions = {
                if (cart.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Amber.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "${cart.sumOf { it.quantity }} article${if (cart.sumOf { it.quantity } > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Surface,
                navigationIconContentColor = Pearl,
            ),
        )

        // ── Active order banner ──────────────────────────────────────────
        if (activeOrderId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Amber.copy(alpha = 0.15f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Amber),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Modification de la commande existante — les articles ont été chargés",
                    color = Amber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ── Error message ───────────────────────────────────────────────
        if (!errorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Crimson.copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = errorMessage,
                    color = Crimson,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissError) {
                    Text("Fermer", color = Mist)
                }
            }
        }

        // ── Category tabs ────────────────────────────────────────────────
        if (sortedCategories.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = tabIndex,
                containerColor = Surface,
                contentColor = Pearl,
                indicator = { tabPositions ->
                    if (tabIndex < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                            color = Amber,
                            height = 2.dp,
                        )
                    }
                },
                divider = { Divider(color = Outline, thickness = 1.dp) },
            ) {
                sortedCategories.forEachIndexed { index, category ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { onTabChanged(index) },
                        text = {
                            Text(
                                text = category.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.sp,
                                fontWeight = if (tabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        selectedContentColor = Amber,
                        unselectedContentColor = Mist,
                    )
                }
            }
        }

        // ── Menu items ────────────────────────────────────────────────────
        if (currentCategory != null) {
            val categoryItems = itemsByCategory[currentCategory.id].orEmpty()

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(categoryItems) { item ->
                    MenuItemCard(item = item, onAddItem = safeAddItem)
                }

                if (cart.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Divider(
                                modifier = Modifier.weight(1f),
                                color = Outline,
                            )
                            Text(
                                text = "  PANIER  ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mist,
                                letterSpacing = 2.sp,
                            )
                            Divider(
                                modifier = Modifier.weight(1f),
                                color = Outline,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(cart) { line ->
                        CartLineCard(
                            line = line,
                            onQuantityChange = safeQuantityChange,
                            onNotesChange = onNotesChange,
                            onRemoveItem = onRemoveItem,
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Sélectionnez une catégorie",
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // ── Footer — total + submit ───────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .border(width = 1.dp, color = Outline, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "TOTAL ESTIMÉ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mist,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = String.format(Locale.FRANCE, "%.2f DT", total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Pearl,
                    )
                }
                Text(
                    text = "${cart.sumOf { it.quantity }} art.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }

            ScheduledTimeRow(
                scheduledTime = scheduledTime,
                onScheduledTimeChange = onScheduledTimeChange,
            )

            Button(
                onClick = onSendOrder,
                enabled = cart.isNotEmpty() && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor   = Color(0xFF1A0A00),
                    disabledContainerColor = Outline,
                    disabledContentColor   = Mist,
                ),
            ) {
                Text(
                    text = when {
                        isSubmitting -> if (activeOrderId != null) "Mise à jour…" else "Envoi en cours…"
                        activeOrderId != null -> "Mettre à jour la commande"
                        else -> "Passer la commande"
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ScheduledTimeRow(
    scheduledTime: String?,
    onScheduledTimeChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(scheduledTime != null) }
    val cal = remember { java.util.Calendar.getInstance() }
    var pickerHour by remember(scheduledTime) {
        mutableStateOf(
            scheduledTime?.split(":")?.getOrNull(0)?.toIntOrNull()
                ?: (cal.get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24
        )
    }
    var pickerMinute by remember(scheduledTime) {
        mutableStateOf(
            scheduledTime?.split(":")?.getOrNull(1)?.toIntOrNull()?.let { ((it + 2) / 5) * 5 % 60 } ?: 0
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Raised)
                .border(
                    1.dp,
                    if (scheduledTime != null) Amber.copy(alpha = 0.5f) else Outline,
                    RoundedCornerShape(10.dp),
                )
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "⏱  Heure de service",
                color = Mist,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = scheduledTime ?: "Immédiat",
                color = if (scheduledTime != null) Amber else Teal,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // Inline picker
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ocean)
                    .border(1.dp, Outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Hour picker
                IconButton(
                    onClick = { pickerHour = (pickerHour - 1 + 24) % 24 },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("−", color = Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text(
                    text = String.format("%02d", pickerHour),
                    color = Pearl,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = { pickerHour = (pickerHour + 1) % 24 },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("+", color = Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Text(":", color = Pearl, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 2.dp))

                // Minute picker (steps of 5)
                IconButton(
                    onClick = { pickerMinute = (pickerMinute - 5 + 60) % 60 },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("−", color = Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text(
                    text = String.format("%02d", pickerMinute),
                    color = Pearl,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = { pickerMinute = (pickerMinute + 5) % 60 },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("+", color = Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Clear / Confirm
                if (scheduledTime != null) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onScheduledTimeChange(null)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Effacer", color = Mist, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = {
                        onScheduledTimeChange(String.format("%02d:%02d", pickerHour, pickerMinute))
                        expanded = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ocean),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MenuItemCard(
    item: MenuItem,
    onAddItem: (MenuItem) -> Unit,
) {
    val available = item.is_available
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = available) { onAddItem(item) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Raised),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (available) Outline else Crimson.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    color = if (available) Pearl else Pearl.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.FRANCE, "%.2f DT", item.price),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (available) Amber else Mist,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (available) Teal.copy(alpha = 0.18f)
                        else Crimson.copy(alpha = 0.12f)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (available) "Ajouter" else "Indispo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (available) Teal else Crimson,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun CartLineCard(
    line: OrderCartLine,
    onQuantityChange: (String, Int) -> Unit,
    onNotesChange: (String, String) -> Unit,
    onRemoveItem: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Raised),
        border = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.name,
                        fontWeight = FontWeight.SemiBold,
                        color = Pearl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = String.format(Locale.FRANCE, "%.2f DT", line.unit_price),
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                    )
                }
                IconButton(
                    onClick = { onRemoveItem(line.item_id) },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Crimson.copy(alpha = 0.12f)),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = Crimson,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Quantity control
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ocean)
                    .border(1.dp, Outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(
                    onClick = { onQuantityChange(line.item_id, line.quantity - 1) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "-1", tint = Mist, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = line.quantity.toString(),
                    fontWeight = FontWeight.Bold,
                    color = Pearl,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                IconButton(
                    onClick = { onQuantityChange(line.item_id, line.quantity + 1) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+1", tint = Amber, modifier = Modifier.size(16.dp))
                }
            }

            OutlinedTextField(
                value = line.notes,
                onValueChange = { onNotesChange(line.item_id, it) },
                label = { Text("Note pour la cuisine", color = Mist, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Amber,
                    unfocusedBorderColor = Outline,
                    focusedTextColor     = Pearl,
                    unfocusedTextColor   = Pearl,
                    cursorColor          = Amber,
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Pearl),
                minLines = 1,
                maxLines = 2,
            )
        }
    }
}
