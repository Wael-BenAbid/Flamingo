package com.example.flamingoandroid.presentation.screens.arrivals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val positions by viewModel.positions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg  by viewModel.errorMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Confirmation dialog state
    var confirmingReservation by remember { mutableStateOf<Reservation?>(null) }

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
                        PendingArrivalCard(
                            reservation = reservation,
                            onConfirm   = { confirmingReservation = reservation },
                            onAbsent    = { viewModel.markAbsent(reservation.id) },
                            onCancel    = { viewModel.cancelArrival(reservation.id) },
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
                        ConfirmedArrivalCard(reservation = reservation)
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
            reservation = res,
            positions   = positions,
            onConfirm   = { posType, posNum ->
                viewModel.confirmArrival(res.id, posType, posNum)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
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

            // Phone
            if (reservation.phone.isNotBlank()) {
                Text(
                    text = reservation.phone,
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist,
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
private fun ConfirmedArrivalCard(reservation: Reservation) {
    val name = "${reservation.firstName} ${reservation.lastName}".trim().ifBlank { "Client" }
    val posLabel = buildString {
        reservation.positionType.takeIf { it.isNotBlank() }?.let { append(it) }
        reservation.positionNumber?.takeIf { it.isNotBlank() }?.let { append(" N°$it") }
        if (isEmpty()) append("—")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Jade.copy(alpha = 0.07f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Jade.copy(alpha = 0.35f)),
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
            Icon(Icons.Default.Check, contentDescription = "Confirmé", tint = Jade,
                modifier = Modifier.size(20.dp))
        }
    }
}

// ── Confirmation dialog ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmArrivalDialog(
    reservation: Reservation,
    positions: List<Position>,
    onConfirm: (posType: String, posNum: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = "${reservation.firstName} ${reservation.lastName}".trim().ifBlank { "Client" }

    // Pre-fill from existing reservation fields if set
    var selectedPosition by remember {
        mutableStateOf(
            positions.firstOrNull { it.type.equals(reservation.positionType, ignoreCase = true) }
                ?: positions.firstOrNull()
        )
    }
    var selectedNumber by remember {
        mutableStateOf(reservation.positionNumber?.takeIf { it.isNotBlank() } ?: "1")
    }

    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var numDropdownExpanded  by remember { mutableStateOf(false) }

    val availableNumbers = remember(selectedPosition) {
        (1..(selectedPosition?.count?.coerceAtLeast(1) ?: 1)).map { it.toString() }
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
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${pos.type}  (${pos.count} places)",
                                                color = Pearl,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        onClick = {
                                            selectedPosition = pos
                                            selectedNumber = "1"
                                            typeDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Position number dropdown
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "N° DE POSITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mist,
                        letterSpacing = 1.5.sp,
                    )
                    ExposedDropdownMenuBox(
                        expanded = numDropdownExpanded,
                        onExpandedChange = { numDropdownExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedNumber,
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
                            expanded = numDropdownExpanded,
                            onDismissRequest = { numDropdownExpanded = false },
                            modifier = Modifier.background(Raised),
                        ) {
                            availableNumbers.forEach { num ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "N° $num",
                                            color = Pearl,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    onClick = {
                                        selectedNumber = num
                                        numDropdownExpanded = false
                                    },
                                )
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
                                onConfirm(posType, selectedNumber)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = selectedPosition != null,
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
