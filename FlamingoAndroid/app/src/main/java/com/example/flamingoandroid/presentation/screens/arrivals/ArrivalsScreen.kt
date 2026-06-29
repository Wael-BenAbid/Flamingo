package com.example.flamingoandroid.presentation.screens.arrivals

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.example.flamingoandroid.presentation.viewmodels.ArrivalsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Flamingo design tokens ────────────────────────────────────────────────
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalsScreen(viewModel: ArrivalsViewModel) {
    val pending   by viewModel.pendingArrivals.collectAsState()
    val confirmed by viewModel.confirmedArrivals.collectAsState()
    val cancelled by viewModel.cancelledArrivals.collectAsState()
    val absent    by viewModel.absentArrivals.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg  by viewModel.errorMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Confirmation dialog state
    var confirmingReservation by remember { mutableStateOf<Reservation?>(null) }

    // Positions occupées par les réservations déjà confirmées : type → set de numéros
    val occupiedPerType = remember(confirmed) {
        confirmed
            .filter { !it.positionNumber.isNullOrBlank() }
            .groupBy { it.positionType.trim() }
            .mapValues { (_, list) -> list.mapNotNull { it.positionNumber?.trim() }.toSet() }
    }

    val today = remember {
        SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date())
            .replaceFirstChar { it.uppercase() }
    }

    val filteredPending = remember(pending, searchQuery) {
        if (searchQuery.isBlank()) pending
        else pending.filter {
            "${it.firstName} ${it.lastName}".contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredConfirmed = remember(confirmed, searchQuery) {
        if (searchQuery.isBlank()) confirmed
        else confirmed.filter {
            "${it.firstName} ${it.lastName}".contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ocean),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Amber,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Header ─────────────────────────────────────────────
                item {
                    val totalAdults   = (pending + confirmed).sumOf { it.adults }
                    val totalChildren = (pending + confirmed).sumOf { it.children }
                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                        Text(
                            text = today,
                            style = MaterialTheme.typography.titleMedium,
                            color = Pearl,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${pending.size} en attente  •  ${confirmed.size} confirmées",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist,
                            letterSpacing = 1.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Teal.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "$totalAdults Adultes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Teal,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Amber.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "$totalChildren Enfants",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Amber,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // ── Search ──────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher client ou téléphone…", color = Mist) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Mist) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber,
                            unfocusedBorderColor = Outline,
                            focusedTextColor = Pearl,
                            unfocusedTextColor = Pearl,
                            cursorColor = Amber,
                            focusedContainerColor = Raised,
                            unfocusedContainerColor = Raised,
                        ),
                        singleLine = true,
                    )
                }

                // ── Section EN ATTENTE ──────────────────────────────────
                item { SectionHeader(label = "EN ATTENTE", count = filteredPending.size, color = Amber) }

                if (filteredPending.isEmpty()) {
                    item {
                        EmptyState(
                            text = if (searchQuery.isBlank()) "Aucune arrivée en attente pour aujourd'hui"
                                   else "Aucun résultat pour « $searchQuery »"
                        )
                    }
                } else {
                    items(filteredPending, key = { it.id }) { reservation ->
                        val posType = reservation.positionType.trim()
                        val totalCount = positions.firstOrNull { it.type.equals(posType, ignoreCase = true) }?.count ?: 0
                        val occupiedCount = occupiedPerType[posType]?.size ?: 0
                        val isPositionFull = posType.isNotBlank() && totalCount > 0 && occupiedCount >= totalCount
                        PendingArrivalCard(
                            reservation    = reservation,
                            isPositionFull = isPositionFull,
                            onConfirm      = { confirmingReservation = reservation },
                            onAbsent       = { viewModel.markAbsent(reservation.id) },
                            onCancel       = { viewModel.cancelArrival(reservation.id) },
                        )
                    }
                }

                // ── Section ARRIVÉES CONFIRMÉES ─────────────────────────
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(label = "ARRIVÉES CONFIRMÉES", count = filteredConfirmed.size, color = Jade)
                }

                if (filteredConfirmed.isEmpty()) {
                    item {
                        EmptyState(text = "Aucune arrivée confirmée pour le moment")
                    }
                } else {
                    items(filteredConfirmed, key = { "confirmed-${it.id}" }) { reservation ->
                        ConfirmedArrivalCard(
                            reservation = reservation,
                            onRevert    = { viewModel.revertToPending(reservation.id) },
                        )
                    }
                }

                // ── Section ANNULÉS ─────────────────────────────────────
                if (cancelled.isNotEmpty() || absent.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(label = "ANNULÉS / ABSENTS", count = cancelled.size + absent.size, color = Crimson)
                    }
                    items(cancelled, key = { "cancelled-${it.id}" }) { reservation ->
                        CancelledArrivalCard(
                            reservation = reservation,
                            statusLabel = "Annulé",
                            onRevert    = { viewModel.revertToPending(reservation.id) },
                        )
                    }
                    items(absent, key = { "absent-${it.id}" }) { reservation ->
                        CancelledArrivalCard(
                            reservation = reservation,
                            statusLabel = "Absent",
                            onRevert    = { viewModel.revertToPending(reservation.id) },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        // ── Error snackbar ──────────────────────────────────────────────
        errorMsg?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = viewModel::dismissError) {
                        Text("OK", color = Amber)
                    }
                },
                containerColor = Crimson,
            ) { Text(msg, color = Pearl) }
        }
    }

    // ── Confirmation dialog ─────────────────────────────────────────────
    confirmingReservation?.let { res ->
        ConfirmArrivalDialog(
            reservation     = res,
            positions       = positions,
            occupiedPerType = occupiedPerType,
            onConfirm       = { posType, posNum, adults, children ->
                viewModel.confirmArrival(res.id, posType, posNum, adults, children)
                confirmingReservation = null
            },
            onDismiss = { confirmingReservation = null },
        )
    }
}

// ── Section header ──────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Pending arrival card ────────────────────────────────────────────────────
@Composable
private fun PendingArrivalCard(
    reservation: Reservation,
    isPositionFull: Boolean = false,
    onConfirm: () -> Unit,
    onAbsent: () -> Unit,
    onCancel: () -> Unit,
) {
    val name = "${reservation.firstName} ${reservation.lastName}".trim().ifBlank { "Client" }
    val posLabel = buildString {
        reservation.positionType.takeIf { it.isNotBlank() }?.let { append(it) }
        reservation.positionNumber?.takeIf { it.isNotBlank() }?.let { append(" N°$it") }
        if (isEmpty()) append("Position non définie")
    }

    val missingPos = reservation.positionNumber.isNullOrBlank()
    val needsAttention = missingPos || isPositionFull
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(
            if (needsAttention) 2.dp else 1.dp,
            if (needsAttention) Crimson else Outline,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Name + time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    AvatarCircle(name = name, color = Amber)
                    Column {
                        Text(name, color = Pearl, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${reservation.adults}A + ${reservation.children}ENF  •  $posLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Amber.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = reservation.time.ifBlank { "--:--" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Phone (cliquable → app téléphone)
            if (reservation.phone.isNotBlank()) {
                val context = LocalContext.current
                val phone   = reservation.phone.replace(Regex("[\\s\\-().]"), "")
                Text(
                    text = reservation.phone,
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    },
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                ) {
                    Text("Arrivé", color = Ocean, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onAbsent,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber),
                ) {
                    Text("Absent", color = Amber, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Crimson),
                ) {
                    Text("Annuler", color = Crimson, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── Confirmed arrival card ──────────────────────────────────────────────────
@Composable
private fun ConfirmedArrivalCard(
    reservation: Reservation,
    onRevert: () -> Unit,
) {
    val name = "${reservation.firstName} ${reservation.lastName}".trim().ifBlank { "Client" }
    val posLabel = buildString {
        reservation.positionType.takeIf { it.isNotBlank() }?.let { append(it) }
        reservation.positionNumber?.takeIf { it.isNotBlank() }?.let { append(" N°$it") }
        if (isEmpty()) append("—")
    }

    val missingPosConf = reservation.positionNumber.isNullOrBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Jade.copy(alpha = 0.07f)),
        border = androidx.compose.foundation.BorderStroke(
            if (missingPosConf) 2.dp else 1.dp,
            if (missingPosConf) Crimson else Jade.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AvatarCircle(name = name, color = Jade)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Pearl, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${reservation.adults}A + ${reservation.children}ENF  •  $posLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Check, contentDescription = "Confirmé", tint = Jade,
                    modifier = Modifier.size(20.dp))
                TextButton(
                    onClick = onRevert,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("Modifier", color = Mist, style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp)
                }
            }
        }
    }
}

// ── Cancelled / absent card ─────────────────────────────────────────────────
@Composable
private fun CancelledArrivalCard(
    reservation: Reservation,
    statusLabel: String,
    onRevert: () -> Unit,
) {
    val name = "${reservation.firstName} ${reservation.lastName}".trim().ifBlank { "Client" }
    val posLabel = buildString {
        reservation.positionType.takeIf { it.isNotBlank() }?.let { append(it) }
        reservation.positionNumber?.takeIf { it.isNotBlank() }?.let { append(" N°$it") }
        if (isEmpty()) append("—")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Crimson.copy(alpha = 0.07f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Crimson.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AvatarCircle(name = name, color = Crimson)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Pearl, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${reservation.adults}A + ${reservation.children}ENF  •  $posLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Crimson.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(statusLabel, color = Crimson, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onRevert,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text("Modifier", color = Mist, style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp)
                }
            }
        }
    }
}

// ── Confirmation dialog ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmArrivalDialog(
    reservation: Reservation,
    positions: List<Position>,
    occupiedPerType: Map<String, Set<String>>,
    onConfirm: (posType: String, posNum: String, adults: Int, children: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = "${reservation.firstName} ${reservation.lastName}".trim().ifBlank { "Client" }

    fun firstFreeNumber(pos: Position?): String {
        if (pos == null) return "1"
        val occupied = occupiedPerType[pos.type.trim()] ?: emptySet()
        return (1..pos.count.coerceAtLeast(1)).map { it.toString() }
            .firstOrNull { it !in occupied } ?: "COMPLET"
    }

    val initialPos = positions.firstOrNull { it.type.equals(reservation.positionType, ignoreCase = true) }
        ?: positions.firstOrNull()

    var selectedPosition by remember { mutableStateOf(initialPos) }
    var selectedNumber   by remember { mutableStateOf(firstFreeNumber(initialPos)) }
    var adults           by remember { mutableStateOf(reservation.adults.coerceAtLeast(1)) }
    var children         by remember { mutableStateOf(reservation.children.coerceAtLeast(0)) }

    var typeDropdownExpanded by remember { mutableStateOf(false) }

    val allNumbers = remember(selectedPosition) {
        (1..(selectedPosition?.count?.coerceAtLeast(1) ?: 1)).map { it.toString() }
    }
    val occupiedNumbers = remember(selectedPosition, occupiedPerType) {
        occupiedPerType[selectedPosition?.type?.trim()] ?: emptySet()
    }
    val allOccupied = allNumbers.isNotEmpty() && allNumbers.all { it in occupiedNumbers }

    // Si le numéro sélectionné est occupé (ou la liste a changé), auto-sélectionner le premier libre
    androidx.compose.runtime.LaunchedEffect(occupiedNumbers) {
        if (selectedNumber in occupiedNumbers || selectedNumber == "COMPLET") {
            selectedNumber = allNumbers.firstOrNull { it !in occupiedNumbers } ?: "COMPLET"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Outline, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title
                Text(
                    text = "Confirmer l'arrivée",
                    style = MaterialTheme.typography.titleMedium,
                    color = Pearl,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )

                Divider(color = Outline)

                // Adultes & Enfants
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ADULTES & ENFANTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mist,
                        letterSpacing = 1.5.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Adultes counter
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Raised)
                                .border(1.dp, Outline, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = { if (adults > 1) adults-- },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text("−", color = if (adults > 1) Amber else Mist,
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$adults",
                                    color = Teal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )
                                Text(
                                    text = "Adultes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Mist,
                                    fontSize = 9.sp,
                                )
                            }
                            IconButton(
                                onClick = { adults++ },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text("+", color = Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                        // Enfants counter
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Raised)
                                .border(1.dp, Outline, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = { if (children > 0) children-- },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text("−", color = if (children > 0) Amber else Mist,
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$children",
                                    color = Amber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )
                                Text(
                                    text = "Enfants",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Mist,
                                    fontSize = 9.sp,
                                )
                            }
                            IconButton(
                                onClick = { children++ },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text("+", color = Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }

                Divider(color = Outline)

                // Position type dropdown
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TYPE DE POSITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mist,
                        letterSpacing = 1.5.sp,
                    )
                    if (positions.isEmpty()) {
                        Text(
                            text = "Aucune position configurée dans les paramètres",
                            style = MaterialTheme.typography.bodySmall,
                            color = Coral,
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = typeDropdownExpanded,
                            onExpandedChange = { typeDropdownExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedPosition?.type ?: "Sélectionner…",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Mist)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Amber,
                                    unfocusedBorderColor = Outline,
                                    focusedTextColor     = Pearl,
                                    unfocusedTextColor   = Pearl,
                                    focusedContainerColor   = Raised,
                                    unfocusedContainerColor = Raised,
                                ),
                            )
                            ExposedDropdownMenu(
                                expanded = typeDropdownExpanded,
                                onDismissRequest = { typeDropdownExpanded = false },
                                modifier = Modifier.background(Raised),
                            ) {
                                positions.forEach { pos ->
                                    val occ = occupiedPerType[pos.type.trim()] ?: emptySet()
                                    val full = occ.size >= pos.count
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                buildString {
                                                    append("${pos.type}  (${pos.count} places)")
                                                    if (full) append("  — COMPLET")
                                                },
                                                color = if (full) Crimson else Pearl,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        onClick = {
                                            selectedPosition = pos
                                            selectedNumber = firstFreeNumber(pos)
                                            typeDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Position number — grille visuelle
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "N° DE POSITION",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist,
                            letterSpacing = 1.5.sp,
                        )
                        if (occupiedNumbers.isNotEmpty()) {
                            Text(
                                text = "■ = occupé",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mist.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                            )
                        }
                    }

                    if (allOccupied) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Crimson.copy(alpha = 0.12f))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Toutes les places sont occupées — choisir un autre type",
                                color = Crimson,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        allNumbers.chunked(5).forEach { rowNums ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                rowNums.forEach { num ->
                                    val isOccupied = num in occupiedNumbers
                                    val isSelected = selectedNumber == num
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    isOccupied -> Color(0xFF111111)
                                                    isSelected -> Amber
                                                    else       -> Raised
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                when {
                                                    isOccupied -> Color(0xFF333333)
                                                    isSelected -> Amber
                                                    else       -> Outline
                                                },
                                                RoundedCornerShape(8.dp),
                                            )
                                            .clickable(enabled = !isOccupied) { selectedNumber = num },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = num,
                                            color = when {
                                                isOccupied -> Color(0xFF444444)
                                                isSelected -> Ocean
                                                else       -> Pearl
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                                // Remplir les cases vides si la ligne < 5
                                repeat(5 - rowNums.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }

                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
                    ) {
                        Text("Annuler", color = Mist)
                    }
                    Button(
                        onClick = {
                            val posType = selectedPosition?.type.orEmpty()
                            if (posType.isNotBlank()) {
                                onConfirm(posType, selectedNumber, adults, children)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = selectedPosition != null && !allOccupied
                            && selectedNumber !in occupiedNumbers
                            && selectedNumber != "COMPLET",
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    ) {
                        Text("Confirmer", color = Ocean, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Avatar circle ──────────────────────────────────────────────────────────
@Composable
private fun AvatarCircle(name: String, color: Color) {
    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = color, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium)
    }
}

// ── Empty state ─────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Mist,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
