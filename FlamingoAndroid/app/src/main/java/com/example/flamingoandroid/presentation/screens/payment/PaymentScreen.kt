package com.example.flamingoandroid.presentation.screens.payment

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flamingoandroid.data.models.MenuCategory
import com.example.flamingoandroid.data.models.MenuItem
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.example.flamingoandroid.data.models.TableOrder
import com.example.flamingoandroid.data.models.TableOrderItem
import com.example.flamingoandroid.presentation.viewmodels.PaymentAction
import com.example.flamingoandroid.presentation.viewmodels.PaymentUiState
import com.example.flamingoandroid.presentation.viewmodels.PaymentViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Design tokens ─────────────────────────────────────────────────────────────
private val Ocean   = Color(0xFF0B1628)
private val Surface = Color(0xFF13203A)
private val Raised  = Color(0xFF1C2E4A)
private val Outline = Color(0xFF2E4560)
private val Amber   = Color(0xFFF59B35)
private val Teal    = Color(0xFF2EC4B6)
private val Pearl   = Color(0xFFF0EEE8)
private val Mist    = Color(0xFF9DB4C0)
private val Jade    = Color(0xFF2DC653)
private val Crimson = Color(0xFFE8603A)

private val DISCOUNT_OPTIONS = listOf(5, 10, 20, 25)
private fun fmtDt(d: Double) = String.format(Locale.FRANCE, "%.2f DT", d)

// ── Main screen ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val action by viewModel.action.collectAsState()
    var invoiceTable  by remember { mutableStateOf<String?>(null) }
    var showWalkIn    by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle pay result
    LaunchedEffect(action) {
        when (val a = action) {
            is PaymentAction.Success -> {
                snackbarHostState.showSnackbar("✓ Paiement enregistré pour ${a.tableLabel}")
                invoiceTable = null
                viewModel.clearAction()
            }
            is PaymentAction.Error -> {
                snackbarHostState.showSnackbar("Erreur : ${a.message}")
                viewModel.clearAction()
            }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.loadMenuForWalkIn()
                    showWalkIn = true
                },
                icon = { Icon(Icons.Default.Person, contentDescription = null, tint = Ocean) },
                text = { Text("Sans réservation", color = Ocean, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium) },
                containerColor = Amber,
            )
        },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Pearl)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Paiement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Pearl,
                        )
                        Text(
                            text = "Factures par position",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist,
                            letterSpacing = 2.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = Ocean,
    ) { innerPadding ->

        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center), color = Amber,
                )
                state.positions.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Aucune zone configurée", color = Pearl, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Configurez les tables dans\nMenus & Tables", color = Mist,
                        textAlign = TextAlign.Center)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Tariff bar
                    item {
                        TariffBar(positions = state.positions)
                    }
                    items(state.positions, key = { it.id.ifBlank { it.type } }) { position ->
                        PaymentPositionCard(
                            position = position,
                            state = state,
                            onTileClick = { tableLabel -> invoiceTable = tableLabel },
                        )
                    }
                }
            }
        }
    }

    invoiceTable?.let { tableLabel ->
        val reservation = state.reservationByTable[tableLabel]
        val order       = state.orderByTable[tableLabel]
        val position    = state.positions.firstOrNull {
            tableLabel.startsWith(it.type.trim(), ignoreCase = true)
        }
        InvoiceDialog(
            tableLabel  = tableLabel,
            reservation = reservation,
            order       = order,
            position    = position,
            isPaid      = tableLabel in state.paidTables || order?.status == "paid",
            onDismiss   = { invoiceTable = null },
            onPay       = { discountPct, discountAmt, finalTotal, remarque ->
                viewModel.payTable(
                    tableLabel      = tableLabel,
                    reservation     = reservation,
                    order           = order,
                    discountPercent = discountPct,
                    discountAmount  = discountAmt,
                    finalTotal      = finalTotal,
                    remarque        = remarque,
                )
            },
        )
    }

    if (showWalkIn) {
        val categories   by viewModel.walkInCategories.collectAsState()
        val menuItems    by viewModel.walkInItems.collectAsState()
        val menuLoading  by viewModel.walkInMenuLoading.collectAsState()
        WalkInDialog(
            positions    = state.positions,
            categories   = categories,
            menuItems    = menuItems,
            menuLoading  = menuLoading,
            onDismiss    = { showWalkIn = false },
            onPay        = { tableLabel, clientName, adults, children,
                             adultPrice, childPrice, cartItems,
                             discountPct, discountAmt, finalTotal, remarque ->
                viewModel.payWalkIn(
                    tableLabel      = tableLabel,
                    clientName      = clientName,
                    adults          = adults,
                    children        = children,
                    adultUnitPrice  = adultPrice,
                    childUnitPrice  = childPrice,
                    cartItems       = cartItems,
                    discountPercent = discountPct,
                    discountAmount  = discountAmt,
                    finalTotal      = finalTotal,
                    remarque        = remarque,
                )
            },
        )
    }
}

// ── Tariff summary bar ────────────────────────────────────────────────────────
@Composable
private fun TariffBar(positions: List<Position>) {
    if (positions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Raised)
            .border(1.dp, Outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        positions.forEach { pos ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pos.type,
                    color = Pearl,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${fmtDt(pos.price)}/A",
                    color = Teal,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "${fmtDt(pos.childPrice)}/ENF",
                    color = Amber,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Position card ──────────────────────────────────────────────────────────────
@Composable
private fun PaymentPositionCard(
    position: Position,
    state: PaymentUiState,
    onTileClick: (String) -> Unit,
) {
    val count    = position.count.coerceAtLeast(0)
    val posType  = position.type.trim()
    val labels   = List(count) { i -> "$posType ${i + 1}" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(posType, color = Pearl, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Amber.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$count place${if (count != 1) "s" else ""}",
                        color = Amber,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (labels.isEmpty()) {
                Text("Aucune place définie.", color = Mist)
            } else {
                labels.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { label ->
                            PaymentTile(
                                tableLabel  = label,
                                reservation = state.reservationByTable[label],
                                order       = state.orderByTable[label],
                                isPaid      = label in state.paidTables
                                           || state.orderByTable[label]?.status == "paid",
                                position    = position,
                                modifier    = Modifier.weight(1f),
                                onClick     = { onTileClick(label) },
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ── Payment tile ──────────────────────────────────────────────────────────────
@Composable
private fun PaymentTile(
    tableLabel: String,
    reservation: Reservation?,
    order: TableOrder?,
    isPaid: Boolean,
    position: Position,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val adultPrice = position.price
    val childPrice = position.childPrice.takeIf { it > 0 } ?: (adultPrice * 0.5)
    val resTotal = if (reservation != null && reservation.totalPrice > 0)
        reservation.totalPrice
    else if (reservation != null)
        reservation.adults * adultPrice + reservation.children * childPrice
    else 0.0
    val ordTotal = order?.total_price ?: 0.0

    val containerColor = when {
        isPaid                             -> Mist.copy(alpha = 0.12f)
        reservation != null && order != null -> Amber.copy(alpha = 0.18f)
        reservation != null                -> Teal.copy(alpha = 0.14f)
        order != null                      -> Teal.copy(alpha = 0.08f)
        else                               -> Teal.copy(alpha = 0.04f)
    }
    val borderColor = when {
        isPaid                             -> Mist.copy(alpha = 0.4f)
        reservation != null && order != null -> Amber
        reservation != null                -> Teal
        order != null                      -> Teal.copy(alpha = 0.5f)
        else                               -> Outline
    }

    Box(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tableLabel.substringAfterLast(" "),
                color = if (isPaid) Mist else Pearl,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            val displayAdults   = reservation?.adults   ?: order?.adults?.takeIf { it > 0 }
            val displayChildren = reservation?.children ?: order?.children?.takeIf { it > 0 }

            if (isPaid) {
                Spacer(Modifier.height(2.dp))
                if (displayAdults != null) {
                    Text(
                        text = "${displayAdults}A · ${displayChildren ?: 0}ENF",
                        color = Mist,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                    )
                }
                Icon(Icons.Default.Check, contentDescription = null,
                    tint = Jade, modifier = Modifier.size(16.dp))
                Text("Payé", color = Jade, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
            } else if (reservation != null || displayAdults != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${displayAdults ?: 0}A · ${displayChildren ?: 0}ENF",
                    color = Teal,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                )
                val display = resTotal + ordTotal
                if (display > 0) {
                    Text(
                        text = String.format(Locale.FRANCE, "%.0f DT", display),
                        color = Amber,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                    )
                }
            } else {
                Spacer(Modifier.height(2.dp))
                Text("Libre", color = Mist, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Invoice dialog ────────────────────────────────────────────────────────────
@Composable
private fun InvoiceDialog(
    tableLabel: String,
    reservation: Reservation?,
    order: TableOrder?,
    position: Position?,
    isPaid: Boolean,
    onDismiss: () -> Unit,
    onPay: (discountPct: Int, discountAmt: Double, finalTotal: Double, remarque: String) -> Unit,
) {
    val context = LocalContext.current

    var discountPct by remember { mutableStateOf(0) }
    var remarque    by remember { mutableStateOf("") }
    var isPaying    by remember { mutableStateOf(false) }

    val adultUnitPrice = position?.price ?: 0.0
    val childUnitPrice = position?.childPrice?.takeIf { it > 0 } ?: (adultUnitPrice * 0.5)

    val isWalkIn = reservation == null && order?.source == "walkin"

    val adults   = reservation?.adults   ?: (if (isWalkIn) (order?.adults   ?: 0) else 0)
    val children = reservation?.children ?: (if (isWalkIn) (order?.children ?: 0) else 0)

    val reservationTotal = when {
        reservation != null && reservation.totalPrice > 0 -> reservation.totalPrice
        reservation != null -> adults * adultUnitPrice + children * childUnitPrice
        isWalkIn -> adults * adultUnitPrice + children * childUnitPrice
        else -> 0.0
    }
    val orderItems   = order?.items ?: emptyList()
    val orderTotal   = orderItems.sumOf { it.unit_price * it.quantity }
    val subtotal     = reservationTotal + orderTotal
    val discountAmt  = Math.round(subtotal * discountPct) / 100.0
    val finalTotal   = subtotal - discountAmt

    val clientName = when {
        reservation != null -> "${reservation.firstName} ${reservation.lastName}".trim().ifBlank { "—" }
        isWalkIn            -> order?.clientName?.ifBlank { "—" } ?: "—"
        else                -> "—"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)),
        ) {
            Column(Modifier.fillMaxSize()) {

                // ── Header ───────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Raised)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("FACTURE", color = Mist, style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 3.sp)
                        Text(tableLabel, color = Pearl, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall)
                    }
                    if (isPaid) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Jade.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("✓ PAYÉ", color = Jade, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
                        }
                    }
                }

                // ── Scrollable body ──────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {

                    // Client
                    if (reservation != null || isWalkIn) {
                        SectionTitle("CLIENT")
                        InvoiceRow("Nom", clientName)
                        if (isWalkIn)
                            InvoiceRow("Type", "Sans réservation")
                        else if (reservation != null && reservation.phone.isNotBlank())
                            InvoiceRow("Téléphone", reservation.phone)
                        if (adultUnitPrice > 0) {
                            if (adults > 0)
                                InvoiceRow(
                                    "$adults Adulte${if (adults > 1) "s" else ""} × ${fmtDt(adultUnitPrice)}",
                                    fmtDt(adults * adultUnitPrice),
                                    valueColor = Teal,
                                )
                            if (children > 0)
                                InvoiceRow(
                                    "$children Enfant${if (children > 1) "s" else ""} × ${fmtDt(childUnitPrice)}",
                                    fmtDt(children * childUnitPrice),
                                    valueColor = Amber,
                                )
                        } else {
                            InvoiceRow("Personnes", "${adults}A + ${children}ENF")
                        }
                    } else {
                        Text("Aucun client assigné.", color = Mist,
                            style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider(color = Outline)

                    // Commande
                    if (orderItems.isNotEmpty()) {
                        SectionTitle("CONSOMMATION")
                        orderItems.forEach { item ->
                            OrderItemRow(item)
                        }
                        InvoiceRow(
                            "Sous-total consommation",
                            fmtDt(orderTotal),
                            valueColor = Pearl,
                        )
                    } else {
                        Text("Aucune commande.", color = Mist,
                            style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider(color = Outline)

                    // Remise
                    SectionTitle("REMISE")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DISCOUNT_OPTIONS.forEach { pct ->
                            val selected = discountPct == pct
                            OutlinedButton(
                                onClick = { discountPct = if (selected) 0 else pct },
                                modifier = Modifier.weight(1f).height(40.dp),
                                enabled = !isPaid,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) Crimson.copy(alpha = 0.15f)
                                                     else Color.Transparent,
                                    contentColor   = if (selected) Crimson else Mist,
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selected) Crimson else Outline,
                                ),
                            ) {
                                Text("$pct%", fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (discountPct > 0) {
                        InvoiceRow(
                            "Remise $discountPct%",
                            "− ${fmtDt(discountAmt)}",
                            valueColor = Crimson,
                        )
                    }

                    HorizontalDivider(color = Outline)

                    // Remarque
                    SectionTitle("REMARQUE")
                    OutlinedTextField(
                        value = remarque,
                        onValueChange = { remarque = it },
                        placeholder = {
                            Text("Note interne (VIP, offert, problème…)",
                                color = Mist.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall)
                        },
                        enabled = !isPaid,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor    = Teal,
                            unfocusedBorderColor  = Outline,
                            focusedTextColor      = Pearl,
                            unfocusedTextColor    = Pearl,
                            cursorColor           = Teal,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )

                    HorizontalDivider(color = Outline)

                    // Total récapitulatif
                    SectionTitle("RÉCAPITULATIF")
                    if (reservationTotal > 0)
                        InvoiceRow("Réservation", fmtDt(reservationTotal))
                    if (orderTotal > 0)
                        InvoiceRow("Consommation", fmtDt(orderTotal))
                    if (discountPct > 0) {
                        InvoiceRow("Sous-total", fmtDt(subtotal), valueColor = Mist)
                        InvoiceRow("Remise $discountPct%", "− ${fmtDt(discountAmt)}",
                            valueColor = Crimson)
                    }
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Raised)
                            .border(1.dp, Outline, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("TOTAL NET", color = Pearl, fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
                            Text(fmtDt(finalTotal), color = Amber, fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // ── Footer buttons ────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Raised)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Print button
                    OutlinedButton(
                        onClick = {
                            val html = buildReceiptHtml(
                                tableLabel      = tableLabel,
                                clientName      = clientName,
                                adults          = adults,
                                children        = children,
                                adultUnitPrice  = adultUnitPrice,
                                childUnitPrice  = childUnitPrice,
                                reservationTotal= reservationTotal,
                                orderItems      = orderItems,
                                orderTotal      = orderTotal,
                                subtotal        = subtotal,
                                discountPercent = discountPct,
                                discountAmount  = discountAmt,
                                finalTotal      = finalTotal,
                                remarque        = remarque,
                            )
                            printHtml(context, html, "Ticket — $tableLabel")
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Pearl),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
                    ) {
                        Text("🖨 Imprimer", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge)
                    }

                    // Pay button
                    Button(
                        onClick = {
                            if (!isPaid && !isPaying) {
                                isPaying = true
                                onPay(discountPct, discountAmt, finalTotal, remarque)
                            }
                        },
                        enabled = !isPaid && !isPaying,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaid) Jade.copy(alpha = 0.3f) else Jade,
                            disabledContainerColor = Jade.copy(alpha = 0.3f),
                        ),
                    ) {
                        if (isPaying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Pearl,
                                strokeWidth = 2.dp,
                            )
                        } else if (isPaid) {
                            Icon(Icons.Default.Check, null, tint = Jade,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Payé", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge, color = Jade)
                        } else {
                            Text("💳 Payer", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge, color = Pearl)
                        }
                    }
                }
            }
        }
    }
}

// ── Print via Android PrintManager + WebView ─────────────────────────────────
private fun printHtml(context: Context, html: String, jobName: String) {
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(PrintManager::class.java)
            val adapter = view.createPrintDocumentAdapter(jobName)
            printManager.print(
                jobName,
                adapter,
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A8)
                    .setResolution(PrintAttributes.Resolution("203dpi", "203dpi", 203, 203))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
            )
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

// ── Receipt HTML builder ──────────────────────────────────────────────────────
private fun buildReceiptHtml(
    tableLabel: String,
    clientName: String,
    adults: Int,
    children: Int,
    adultUnitPrice: Double,
    childUnitPrice: Double,
    reservationTotal: Double,
    orderItems: List<TableOrderItem>,
    orderTotal: Double,
    subtotal: Double,
    discountPercent: Int,
    discountAmount: Double,
    finalTotal: Double,
    remarque: String,
): String {
    val now     = Date()
    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(now)
    val timeStr = SimpleDateFormat("HH:mm", Locale.FRANCE).format(now)
    val SEP  = "━".repeat(32)
    val THIN = "─".repeat(32)

    fun row(left: String, right: String) =
        """<div class="row"><span>$left</span><span class="bold">$right</span></div>"""
    fun dt(d: Double) = String.format(Locale.FRANCE, "%.2f&nbsp;DT", d)

    val itemsHtml = if (orderItems.isNotEmpty()) buildString {
        appendLine("""<div class="section-title">CONSOMMATION</div>""")
        orderItems.forEach { item ->
            appendLine("""<div class="row">
                <span>${item.quantity}&times; ${item.name}</span>
                <span class="bold">${dt(item.unit_price * item.quantity)}</span>
            </div>""")
            appendLine("""<div class="hint">@ ${dt(item.unit_price)} / unité</div>""")
        }
        appendLine("""<div class="sep thin">$THIN</div>""")
    } else ""

    val resHtml = if (adults > 0 || children > 0) buildString {
        appendLine("""<div class="section-title">RÉSERVATION</div>""")
        if (adults > 0)
            appendLine(row("$adults Adulte${if (adults > 1) "s" else ""} × ${dt(adultUnitPrice)}",
                dt(adults * adultUnitPrice)))
        if (children > 0)
            appendLine(row("$children Enfant${if (children > 1) "s" else ""} × ${dt(childUnitPrice)}",
                dt(children * childUnitPrice)))
        appendLine("""<div class="sep thin">$THIN</div>""")
    } else ""

    val discountHtml = if (discountPercent > 0)
        """<div class="row discount">
             <span>Remise $discountPercent%</span>
             <span>− ${dt(discountAmount)}</span>
           </div>"""
    else ""

    val remarkHtml = if (remarque.isNotBlank())
        """<div class="sep">$SEP</div>
           <div class="section-title">REMARQUE</div>
           <div style="font-size:10px;white-space:pre-wrap;">${remarque.trim()}</div>"""
    else ""

    val css = """
<style>
  @page { margin: 3mm; size: 80mm auto; }
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    font-family: 'Courier New', Courier, monospace;
    font-size: 11px; color: #000;
    width: 74mm; padding: 2mm; line-height: 1.55;
  }
  .ticket { margin-bottom: 4mm; }
  .center { text-align: center; }
  .bold   { font-weight: bold; }
  .xl     { font-size: 15px; letter-spacing: 1px; }
  .sub    { font-size: 9px; margin-bottom: 1mm; }
  .sep    { color: #444; margin: 1mm 0; }
  .thin   { color: #888; }
  .hint   { font-size: 9px; color: #666; padding-left: 4mm; }
  .row    { display: flex; justify-content: space-between; gap: 4px; }
  .discount { color: #c00; }
  .section-title {
    font-weight: bold; font-size: 10px; letter-spacing: 1px;
    border-bottom: 1px solid #000; padding-bottom: 1mm; margin: 2mm 0 1mm;
  }
  .copy-label {
    font-size: 9px; font-weight: bold; letter-spacing: 2px;
    background: #eee; padding: 1mm 0; margin: 1mm 0 2mm; text-align: center;
  }
  .copy-dark { background: #222; color: #fff; }
  .total-box { border: 2px solid #000; padding: 2mm; margin: 1mm 0; }
  .total-row { font-size: 14px; font-weight: bold; }
  .thanks { font-size: 9px; }
  @media print { body { width: 100%; } .ticket { page-break-inside: avoid; } }
</style>"""

    // CLIENT ticket — consommation only + discount + total
    val clientTicket = """
<div class="ticket">
  <div class="center bold xl">OCÉAN EL BOUNTA</div>
  <div class="center sub">Beach Club &amp; Restaurant</div>
  <div class="copy-label">── COPIE CLIENT ──</div>
  <div class="sep">$SEP</div>
  ${row("Date :", dateStr)}
  ${row("Heure :", timeStr)}
  ${row("Table :", "<strong>$tableLabel</strong>")}
  <div class="sep" style="margin:2mm 0">$SEP</div>
  $itemsHtml
  $discountHtml
  <div class="sep" style="margin:2mm 0">$SEP</div>
  <div class="total-box">
    <div class="row total-row">
      <span>TOTAL NET</span><span>${dt(finalTotal)}</span>
    </div>
  </div>
  <div class="sep" style="margin:3mm 0">$SEP</div>
  <div class="center thanks">Merci de votre visite !</div>
  <div class="center thanks">Océan Flamingo vous attend.</div>
  <div class="sep">$SEP</div>
</div>"""

    // RESTO ticket — full details
    val restoTicket = """
<div class="ticket" style="page-break-before:always;">
  <div class="center bold xl">OCÉAN EL BOUNTA</div>
  <div class="center sub">Beach Club &amp; Restaurant</div>
  <div class="copy-label copy-dark">── COPIE ÉTABLISSEMENT ──</div>
  <div class="sep">$SEP</div>
  ${row("Date :", dateStr)}
  ${row("Heure :", timeStr)}
  ${row("Table :", "<strong>$tableLabel</strong>")}
  ${if (clientName != "—") row("Client :", clientName) else ""}
  <div class="sep" style="margin:2mm 0">$SEP</div>
  $resHtml
  $itemsHtml
  <div class="sep" style="margin:2mm 0">$SEP</div>
  <div class="total-box">
    ${if (reservationTotal > 0) row("Réservation", dt(reservationTotal)) else ""}
    ${if (orderTotal > 0) row("Consommation", dt(orderTotal)) else ""}
    ${if (discountPercent > 0) """
      <div class="sep thin" style="margin:1mm 0">$THIN</div>
      ${row("Sous-total", dt(subtotal))}
      $discountHtml
    """ else ""}
    <div class="sep thin" style="margin:1mm 0">$THIN</div>
    <div class="row total-row">
      <span>TOTAL NET</span><span>${dt(finalTotal)}</span>
    </div>
  </div>
  $remarkHtml
  <div class="sep" style="margin:3mm 0">$SEP</div>
  <div class="center thanks">Document interne — Établissement</div>
  <div class="sep">$SEP</div>
</div>"""

    return """<!DOCTYPE html>
<html lang="fr">
<head><meta charset="UTF-8"/><title>Tickets — $tableLabel</title>$css</head>
<body>
$clientTicket
$restoTicket
</body>
</html>"""
}

// ── Walk-in dialog ────────────────────────────────────────────────────────────
@Suppress("NAME_SHADOWING")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalkInDialog(
    positions:   List<Position>,
    categories:  List<MenuCategory>,
    menuItems:   List<MenuItem>,
    menuLoading: Boolean,
    onDismiss:   () -> Unit,
    onPay: (
        tableLabel: String, clientName: String,
        adults: Int, children: Int,
        adultPrice: Double, childPrice: Double,
        cartItems: List<TableOrderItem>,
        discountPct: Int, discountAmt: Double,
        finalTotal: Double, remarque: String,
    ) -> Unit,
) {
    val context = LocalContext.current

    // ── Step state ──────────────────────────────────────────────────────────
    var step by remember { mutableStateOf(1) }

    // ── Step 1 inputs ───────────────────────────────────────────────────────
    val availPos = remember(positions) { positions.filter { it.count > 0 } }
    var clientName  by remember { mutableStateOf("") }
    var selPosIdx   by remember { mutableStateOf(0) }
    var selPosNum   by remember { mutableStateOf(1) }
    var adults      by remember { mutableStateOf(1) }
    var children    by remember { mutableStateOf(0) }
    var discountPct by remember { mutableStateOf(0) }
    var remarque    by remember { mutableStateOf("") }

    // Position type dropdown
    var posTypeExpanded by remember { mutableStateOf(false) }
    var posNumExpanded  by remember { mutableStateOf(false) }

    val selPos        = availPos.getOrNull(selPosIdx)
    val adultPrice    = selPos?.price ?: 0.0
    val childPrice    = selPos?.childPrice?.takeIf { it > 0 } ?: (adultPrice * 0.5)
    val posNumRange   = (1..(selPos?.count?.coerceAtLeast(1) ?: 1)).toList()
    val tableLabel    = selPos?.let { "${it.type.trim()} $selPosNum" } ?: "Walk-in"

    // ── Step 2 cart ─────────────────────────────────────────────────────────
    var cart by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isPaying by remember { mutableStateOf(false) }
    var paid     by remember { mutableStateOf(false) }

    val cartItems = remember(cart, menuItems) {
        cart.entries
            .filter { it.value > 0 }
            .mapNotNull { (itemId, qty) ->
                menuItems.find { it.id == itemId }?.let { m ->
                    TableOrderItem(item_id = itemId, name = m.name, quantity = qty,
                        notes = "", unit_price = m.price)
                }
            }
    }

    val reservationTotal = adultPrice * adults + childPrice * children
    val orderTotal       = cartItems.sumOf { it.unit_price * it.quantity }
    val subtotal         = reservationTotal + orderTotal
    val discountAmt      = Math.round(subtotal * discountPct) / 100.0
    val finalTotal       = subtotal - discountAmt

    Dialog(
        onDismissRequest = { if (!isPaying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)),
        ) {
            Column(Modifier.fillMaxSize()) {

                // ── Dialog header ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Raised)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Sans réservation", color = Pearl, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Étape $step/2 — ${if (step == 1) "Informations" else "Consommation"}",
                            color = Mist, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                    // Step dots
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        repeat(2) { i ->
                            Box(Modifier
                                .size(if (step == i + 1) 28.dp else 20.dp, 5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (step >= i + 1) Amber else Outline))
                        }
                    }
                }

                // ── STEP 1 ───────────────────────────────────────────────────
                if (step == 1) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Client name
                        SectionTitle("NOM DU CLIENT")
                        OutlinedTextField(
                            value = clientName,
                            onValueChange = { clientName = it },
                            placeholder = { Text("Nom (optionnel)", color = Mist.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal, unfocusedBorderColor = Outline,
                                focusedTextColor = Pearl, unfocusedTextColor = Pearl, cursorColor = Teal,
                            ),
                        )

                        // Zone dropdown
                        SectionTitle("ZONE / POSITION")
                        ExposedDropdownMenuBox(
                            expanded = posTypeExpanded,
                            onExpandedChange = { posTypeExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selPos?.type ?: "Aucune zone",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = Mist) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber, unfocusedBorderColor = Outline,
                                    focusedTextColor = Pearl, unfocusedTextColor = Pearl,
                                    focusedContainerColor = Raised, unfocusedContainerColor = Raised,
                                ),
                            )
                            ExposedDropdownMenu(expanded = posTypeExpanded,
                                onDismissRequest = { posTypeExpanded = false },
                                modifier = Modifier.background(Raised)) {
                                availPos.forEachIndexed { idx, pos ->
                                    DropdownMenuItem(
                                        text = { Text("${pos.type}  (${pos.count} places)", color = Pearl) },
                                        onClick = { selPosIdx = idx; selPosNum = 1; posTypeExpanded = false },
                                    )
                                }
                            }
                        }

                        // N° dropdown
                        SectionTitle("N° DE POSITION")
                        ExposedDropdownMenuBox(
                            expanded = posNumExpanded,
                            onExpandedChange = { posNumExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = "N° $selPosNum",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = Mist) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber, unfocusedBorderColor = Outline,
                                    focusedTextColor = Pearl, unfocusedTextColor = Pearl,
                                    focusedContainerColor = Raised, unfocusedContainerColor = Raised,
                                ),
                            )
                            ExposedDropdownMenu(expanded = posNumExpanded,
                                onDismissRequest = { posNumExpanded = false },
                                modifier = Modifier.background(Raised)) {
                                posNumRange.forEach { n ->
                                    DropdownMenuItem(
                                        text = { Text("N° $n", color = Pearl) },
                                        onClick = { selPosNum = n; posNumExpanded = false },
                                    )
                                }
                            }
                        }

                        // Adults / Children
                        SectionTitle("PERSONNES")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                Triple("Adultes", adults, { v: Int -> adults = v }),
                                Triple("Enfants", children, { v: Int -> children = v }),
                            ).forEach { (label, value, setter) ->
                                Column(modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(label, color = Mist, style = MaterialTheme.typography.labelSmall)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Raised)
                                            .border(1.dp, Outline, RoundedCornerShape(10.dp)),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        TextButton(onClick = { setter(maxOf(0, value - 1)) }) {
                                            Text("−", color = Mist, style = MaterialTheme.typography.titleLarge)
                                        }
                                        Text("$value", color = Pearl, fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium)
                                        TextButton(onClick = { setter(value + 1) }) {
                                            Text("+", color = Amber, style = MaterialTheme.typography.titleLarge)
                                        }
                                    }
                                }
                            }
                        }

                        // Entry price preview
                        if (reservationTotal > 0) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Teal.copy(alpha = 0.08f))
                                    .border(1.dp, Teal.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (adults > 0 && adultPrice > 0)
                                        InvoiceRow("$adults Adulte${if (adults > 1) "s" else ""} × ${fmtDt(adultPrice)}",
                                            fmtDt(adultPrice * adults), valueColor = Teal)
                                    if (children > 0 && childPrice > 0)
                                        InvoiceRow("$children Enfant${if (children > 1) "s" else ""} × ${fmtDt(childPrice)}",
                                            fmtDt(childPrice * children), valueColor = Amber)
                                    HorizontalDivider(color = Teal.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                                    InvoiceRow("Total entrée", fmtDt(reservationTotal), valueColor = Teal)
                                }
                            }
                        }

                        // Discount
                        SectionTitle("REMISE")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DISCOUNT_OPTIONS.forEach { pct ->
                                val sel = discountPct == pct
                                OutlinedButton(
                                    onClick = { discountPct = if (sel) 0 else pct },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (sel) Crimson.copy(alpha = 0.15f) else Color.Transparent,
                                        contentColor = if (sel) Crimson else Mist,
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) Crimson else Outline),
                                ) {
                                    Text("$pct%", fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // Remarque
                        SectionTitle("REMARQUE")
                        OutlinedTextField(
                            value = remarque,
                            onValueChange = { remarque = it },
                            placeholder = { Text("Note interne (optionnel)…", color = Mist.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2, maxLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal, unfocusedBorderColor = Outline,
                                focusedTextColor = Pearl, unfocusedTextColor = Pearl, cursorColor = Teal,
                            ),
                        )
                    }

                    // Footer step 1
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Raised).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
                        ) { Text("Annuler") }
                        Button(
                            onClick = { step = 2 },
                            enabled = availPos.isNotEmpty(),
                            modifier = Modifier.weight(2f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber),
                        ) {
                            Text("Consommation →", color = Ocean, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // ── STEP 2 ───────────────────────────────────────────────────
                if (step == 2) {
                    // Running total bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Raised)
                            .border(width = 0.dp, color = Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        InvoiceRow("Entrée (${adults}A+${children}ENF)", fmtDt(reservationTotal))
                        InvoiceRow("Consommation", fmtDt(orderTotal))
                        if (discountPct > 0)
                            InvoiceRow("Remise $discountPct%", "− ${fmtDt(discountAmt)}", valueColor = Crimson)
                        HorizontalDivider(color = Outline, modifier = Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL NET", color = Pearl, fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleSmall, letterSpacing = 2.sp)
                            Text(fmtDt(finalTotal), color = Amber, fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Menu items
                    if (menuLoading) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Amber)
                        }
                    } else if (categories.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center) {
                            Text("Aucun article de menu.\nVous pouvez passer au paiement.",
                                color = Mist, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            categories.forEach { cat ->
                                val catItems = menuItems.filter { it.category_id == cat.id }
                                if (catItems.isEmpty()) return@forEach
                                item {
                                    Text(cat.name.uppercase(), color = Mist,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                }
                                items(catItems, key = { it.id }) { menuItem ->
                                    val qty = cart[menuItem.id] ?: 0
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Raised)
                                            .border(1.dp, Outline, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(menuItem.name, color = Pearl, fontWeight = FontWeight.SemiBold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(fmtDt(menuItem.price), color = Amber,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Ocean)
                                                .border(1.dp, Outline, RoundedCornerShape(8.dp)),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    cart = if (qty <= 1) cart - menuItem.id
                                                           else cart + (menuItem.id to qty - 1)
                                                },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "−",
                                                    tint = if (qty > 0) Crimson else Mist,
                                                    modifier = Modifier.size(14.dp))
                                            }
                                            Text("$qty", color = Pearl, fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(horizontal = 8.dp))
                                            IconButton(
                                                onClick = { cart = cart + (menuItem.id to qty + 1) },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "+",
                                                    tint = Teal, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Footer step 2
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Raised).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { step = 1 },
                            enabled = !paid,
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
                        ) { Text("← Retour") }

                        OutlinedButton(
                            onClick = {
                                val html = buildReceiptHtml(
                                    tableLabel       = tableLabel,
                                    clientName       = clientName.ifBlank { "—" },
                                    adults           = adults,
                                    children         = children,
                                    adultUnitPrice   = adultPrice,
                                    childUnitPrice   = childPrice,
                                    reservationTotal = reservationTotal,
                                    orderItems       = cartItems,
                                    orderTotal       = orderTotal,
                                    subtotal         = subtotal,
                                    discountPercent  = discountPct,
                                    discountAmount   = discountAmt,
                                    finalTotal       = finalTotal,
                                    remarque         = remarque,
                                )
                                printHtml(context, html, "Ticket Walk-in — $tableLabel")
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Pearl),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
                        ) { Text("🖨 Imprimer", fontWeight = FontWeight.Bold) }

                        Button(
                            onClick = {
                                if (!paid && !isPaying) {
                                    isPaying = true
                                    onPay(
                                        tableLabel, clientName.ifBlank { "—" },
                                        adults, children, adultPrice, childPrice,
                                        cartItems, discountPct, discountAmt, finalTotal, remarque,
                                    )
                                    paid = true; isPaying = false
                                }
                            },
                            enabled = !paid && !isPaying,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (paid) Jade.copy(alpha = 0.3f) else Jade,
                                disabledContainerColor = Jade.copy(alpha = 0.3f),
                            ),
                        ) {
                            if (paid) {
                                Icon(Icons.Default.Check, null, tint = Jade, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Payé", color = Jade, fontWeight = FontWeight.Bold)
                            } else {
                                Text("💳 Payer", color = Pearl, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Small composable helpers ──────────────────────────────────────────────────
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Mist,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.5.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun InvoiceRow(
    label: String,
    value: String,
    valueColor: Color = Mist,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Mist, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).paddingEnd(8.dp))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun OrderItemRow(item: TableOrderItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = Pearl, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "@ ${String.format(Locale.FRANCE, "%.2f DT", item.unit_price)} / unité",
                color = Mist,
                style = MaterialTheme.typography.labelSmall,
            )
            if (item.notes.isNotBlank())
                Text(item.notes, color = Mist, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text("×${item.quantity}", color = Mist, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format(Locale.FRANCE, "%.2f DT", item.unit_price * item.quantity),
            color = Amber,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// Padding extension shorthand
private fun Modifier.paddingEnd(dp: androidx.compose.ui.unit.Dp) = this.padding(end = dp)
