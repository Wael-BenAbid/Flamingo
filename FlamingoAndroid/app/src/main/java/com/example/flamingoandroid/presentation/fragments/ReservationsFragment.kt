package com.example.flamingoandroid.presentation.fragments

import android.content.Context
import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.R
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.example.flamingoandroid.databinding.FragmentReservationsBinding
import com.example.flamingoandroid.presentation.util.bindReservationStatus
import com.example.flamingoandroid.presentation.util.initials
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReservationsFragment : Fragment() {

    private var _binding: FragmentReservationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel = com.example.flamingoandroid.presentation.viewmodels.ReservationsViewModel()

    private var searchQuery: String = ""
    private var allReservations: List<Reservation> = emptyList()
    private var currentReservations: List<Reservation> = emptyList()
    private var availablePositions: List<Position> = emptyList()
    private var selectedDateIso: String = ""
    private var selectedDateLabel: String = ""
    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormatter = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)
    private val defaultPositionTypes = listOf(
        "Terrasse",
        "Parasol",
        "Cabane",
        "Payotte",
        "Cabane avec piscine privée"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReservationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Afficher la date lisible et préparer la date ISO pour filtrage
        val today = java.util.Date()
        selectedDateIso = isoFormatter.format(today)
        selectedDateLabel = displayFormatter.format(today)
        binding.tvReservationsDate.text = selectedDateLabel
        binding.btnSelectReservationDate.text = selectedDateLabel

        binding.btnAddReservation.setOnClickListener {
            showReservationDialog()
        }

        binding.btnSelectReservationDate.setOnClickListener {
            showDatePicker()
        }

        binding.etReservationSearch.doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString().orEmpty()
            renderReservations()
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.isLoading.collectLatest { loading ->
                binding.pbReservationsLoading.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.errorMessage.collectLatest { error ->
                error?.let {
                    binding.tvReservationsStats.text = it
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.reservations.collectLatest { reservations ->
                allReservations = reservations
                applyDateFilter()
                renderReservations()
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.positions.collectLatest { positions ->
                availablePositions = positions
            }
        }
    }

    private fun applyDateFilter() {
        currentReservations = allReservations.filter { it.date == selectedDateIso }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        try {
            isoFormatter.parse(selectedDateIso)?.let { calendar.time = it }
        } catch (_: ParseException) {
            calendar.time = java.util.Date()
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val pickedDate = calendar.time
                selectedDateIso = isoFormatter.format(pickedDate)
                selectedDateLabel = displayFormatter.format(pickedDate)
                binding.tvReservationsDate.text = selectedDateLabel
                binding.btnSelectReservationDate.text = selectedDateLabel
                applyDateFilter()
                renderReservations()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun renderReservations() {
        val filtered = currentReservations.filter {
            val name = "${it.firstName} ${it.lastName}".lowercase(Locale.getDefault())
            name.contains(searchQuery.lowercase(Locale.getDefault())) ||
                it.phone.contains(searchQuery, ignoreCase = true)
        }

        val totalAdults   = filtered.sumOf { it.adults }
        val totalChildren = filtered.sumOf { it.children }
        binding.tvReservationsStats.text =
            "${filtered.size} réservations • ${filtered.count { it.status == "confirmed" }} confirmées • ${filtered.count { it.status == "pending" }} en attente • ${totalAdults}A ${totalChildren}ENF"

        binding.tvReservationsEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.reservationsContainer.removeAllViews()

        filtered.forEach { reservation ->
            val card = layoutInflater.inflate(R.layout.item_reservation_admin, binding.reservationsContainer, false)
            bindReservationCard(card, reservation)
            binding.reservationsContainer.addView(card)
        }
    }

    private fun bindReservationCard(root: View, reservation: Reservation) {
        val name = "${reservation.firstName} ${reservation.lastName}".trim()
        val avatar = root.findViewById<TextView>(R.id.tvReservationAvatar)
        val nameView = root.findViewById<TextView>(R.id.tvReservationName)
        val phoneView = root.findViewById<TextView>(R.id.tvReservationPhone)
        val detailsView = root.findViewById<TextView>(R.id.tvReservationDetails)
        val notesView = root.findViewById<TextView>(R.id.tvReservationNotes)
        val statusView = root.findViewById<TextView>(R.id.tvReservationStatus)
        val editButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReservationEdit)
        val statusButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReservationStatus)
        val deleteButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReservationDelete)

        avatar.text = initials(name, "R")
        nameView.text = name.ifBlank { "Réservation" }
        phoneView.text = reservation.phone.ifBlank { "Téléphone non disponible" }
        phoneView.isClickable = reservation.phone.isNotBlank()
        phoneView.setOnClickListener {
            openDialer(reservation.phone)
        }
        val positionDisplay = if (reservation.positionNumber?.isNotBlank() == true) {
            "${reservation.positionType} · N°${reservation.positionNumber}"
        } else {
            reservation.positionType.ifBlank { "Position" }
        }
        detailsView.text = "${reservation.adults} AD • ${reservation.children} ENF • $positionDisplay • ${reservation.time.ifBlank { "--:--" }}"
        notesView.text = reservation.notes?.takeIf { it.isNotBlank() } ?: "Aucune note spéciale"
        statusView.bindReservationStatus(requireContext(), reservation.status)

        statusButton.setOnClickListener {
            showStatusDialog(reservation)
        }

        editButton.setOnClickListener {
            showReservationDialog(reservation)
        }

        deleteButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Supprimer cette réservation ?")
                .setMessage(name)
                .setPositiveButton("Supprimer") { _, _ ->
                    viewModel.deleteReservation(reservation.id)
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun showStatusDialog(reservation: Reservation) {
        val statuses = arrayOf("confirmed", "pending", "cancelled", "absent")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Changer le statut")
            .setItems(statuses) { _, which ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.updateReservationStatus(reservation.id, statuses[which])
                }
            }
            .show()
    }

    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[\\s\\-().]"), "")
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        val normalized = normalizePhoneNumber(phone)
        val tunisian = Regex("^((\\+|00)216[2459]\\d{7}|[2459]\\d{7})$")
        val french = Regex("^((\\+|00)33[1-9]\\d{8}|0[1-9]\\d{8})$")
        val italian = Regex("^((\\+|00)39\\d{8,11})$")
        return tunisian.matches(normalized) || french.matches(normalized) || italian.matches(normalized)
    }

    private fun isPastDate(dateIso: String): Boolean {
        return try {
            val selected = isoFormatter.parse(dateIso) ?: return false
            val selectedCalendar = Calendar.getInstance().apply {
                time = selected
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            selectedCalendar.before(todayCalendar)
        } catch (_: Exception) {
            false
        }
    }

    private fun occupiedPositionNumbers(dateIso: String, positionType: String, excludedReservationId: String? = null): Set<String> {
        return allReservations
            .asSequence()
            .filter { it.date == dateIso }
            .filter { it.positionType == positionType }
            .filter { excludedReservationId == null || it.id != excludedReservationId }
            .filter { it.status.lowercase(Locale.getDefault()) !in setOf("cancelled", "absent") }
            .mapNotNull { it.positionNumber?.takeIf { number -> number.isNotBlank() } }
            .toSet()
    }

    private fun availablePositionNumbers(dateIso: String, positionType: String, excludedReservationId: String? = null): List<String> {
        val total = availablePositions.firstOrNull { it.type == positionType }?.count ?: 0
        val occupied = occupiedPositionNumbers(dateIso, positionType, excludedReservationId)
        return (1..total).map { it.toString() }.filterNot { occupied.contains(it) }
    }

    private fun openDialer(phone: String) {
        val normalized = normalizePhoneNumber(phone)
        if (normalized.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized"))
        startActivity(intent)
    }

    // ── Helpers formulaire ───────────────────────────────────────────────────

    private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private fun fmTil(
        ctx: Context,
        hint: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
        multiline: Boolean = false,
        phoneKb: Boolean = false,
    ): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(ctx).apply {
            this.hint = hint
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(12f, 12f, 12f, 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp(ctx) }
        }
        val et = TextInputEditText(til.context).apply {
            this.inputType = when {
                phoneKb   -> InputType.TYPE_CLASS_PHONE
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else      -> inputType
            }
            if (multiline) { minLines = 3; maxLines = 5 }
        }
        til.addView(et)
        return til to et
    }

    private fun fmSection(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 10f
        letterSpacing = 0.12f
        setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        setPadding(2.dp(ctx), 10.dp(ctx), 0, 6.dp(ctx))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fmStepper(
        ctx: Context,
        label: String,
        initial: Int,
        min: Int = 0,
        onChanged: (Int) -> Unit,
    ): Pair<LinearLayout, () -> Int> {
        var count = initial
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp(ctx) }
        }
        val lbl = TextView(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
        }
        val countView = TextView(ctx).apply {
            text = count.toString()
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(52.dp(ctx), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val btnMinus = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "−"; isAllCaps = false; setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(44.dp(ctx), 44.dp(ctx))
            setOnClickListener {
                if (count > min) { count--; countView.text = count.toString(); onChanged(count) }
            }
        }
        val btnPlus = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "+"; isAllCaps = false; setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(44.dp(ctx), 44.dp(ctx))
            setOnClickListener { count++; countView.text = count.toString(); onChanged(count) }
        }
        row.addView(lbl); row.addView(btnMinus); row.addView(countView); row.addView(btnPlus)
        return row to { count }
    }

    private fun fmPickerBtn(ctx: Context, text: String): MaterialButton =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp(ctx) }
        }

    // ── Dialog réservation ────────────────────────────────────────────────────

    private fun showReservationDialog(existingReservation: Reservation? = null) {
        val ctx = requireContext()
        val pad = 20.dp(ctx)

        // ── État mutable ─────────────────────────────────────────────────────
        var selectedDateIsoValue = existingReservation?.date?.takeIf { it.isNotBlank() }
            ?: selectedDateIso.ifBlank { isoFormatter.format(java.util.Date()) }
        var selectedTimeValue = existingReservation?.time?.takeIf { it.isNotBlank() } ?: "12:00"
        var selectedPositionType = existingReservation?.positionType?.takeIf { it.isNotBlank() } ?: ""
        var selectedPositionNumber = existingReservation?.positionNumber?.takeIf { it.isNotBlank() } ?: ""
        var adultsCount = existingReservation?.adults?.takeIf { it > 0 } ?: 1
        var childrenCount = existingReservation?.children ?: 0

        // ── Champs texte ──────────────────────────────────────────────────────
        val (tilFirstName, etFirstName) = fmTil(ctx, "Prénom *")
        etFirstName.setText(existingReservation?.firstName.orEmpty())

        val (tilLastName, etLastName) = fmTil(ctx, "Nom *")
        etLastName.setText(existingReservation?.lastName.orEmpty())

        val (tilPhone, etPhone) = fmTil(ctx, "Téléphone *", phoneKb = true)
        etPhone.setText(existingReservation?.phone.orEmpty())

        val (_, etNotes) = fmTil(ctx, "Notes / demandes spéciales", multiline = true)
        etNotes.setText(existingReservation?.notes.orEmpty())

        // ── Boutons date & heure ──────────────────────────────────────────────
        fun dateLabel() = try {
            "📅  ${displayFormatter.format(isoFormatter.parse(selectedDateIsoValue)!!)}"
        } catch (_: Exception) { "📅  Choisir la date" }

        val dateBtn = fmPickerBtn(ctx, dateLabel())
        val timeBtn = fmPickerBtn(ctx, "🕐  $selectedTimeValue")

        val dateTimeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp(ctx) }
        }
        dateBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginEnd = 8.dp(ctx) }
        timeBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        dateTimeRow.addView(dateBtn); dateTimeRow.addView(timeBtn)

        // ── Compteurs adultes / enfants ───────────────────────────────────────
        val (adultsRow, getAdults)   = fmStepper(ctx, "Adultes", adultsCount, min = 1) { adultsCount = it }
        val (childrenRow, getChildren) = fmStepper(ctx, "Enfants", childrenCount, min = 0) { childrenCount = it }

        // ── Sélecteur type de position (grille 2 col) ─────────────────────────
        val posTypeButtons = mutableMapOf<String, MaterialButton>()
        val posTypeGrid = GridLayout(ctx).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp(ctx) }
        }

        fun getPositionTypes(): List<String> {
            val types = availablePositions.map { it.type }.filter { it.isNotBlank() }
            return types.ifEmpty { defaultPositionTypes }
        }

        fun refreshPosTypeGrid() {
            posTypeButtons.forEach { (cat, btn) -> btn.isChecked = (cat == selectedPositionType) }
        }

        // Grille N° de position (inline, reconstruite à chaque changement de type)
        val posNumContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp(ctx) }
        }
        val posNumSectionLabel = fmSection(ctx, "N° DE POSITION")
        posNumSectionLabel.visibility = View.GONE
        posNumContainer.addView(posNumSectionLabel)

        fun rebuildPosNumGrid() {
            // Remove everything except the section label (index 0)
            while (posNumContainer.childCount > 1) posNumContainer.removeViewAt(1)
            if (selectedPositionType.isBlank()) { posNumSectionLabel.visibility = View.GONE; return }
            posNumSectionLabel.visibility = View.VISIBLE

            val count    = availablePositions.firstOrNull { it.type == selectedPositionType }?.count ?: 0
            val occupied = occupiedPositionNumbers(selectedDateIsoValue, selectedPositionType, existingReservation?.id)
            if (count == 0) return

            val grid = GridLayout(ctx).apply {
                columnCount = 5
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            (1..count).forEach { n ->
                val numStr   = n.toString()
                val occupied2 = occupied.contains(numStr)
                val isSelected = (numStr == selectedPositionNumber)

                val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = numStr; isAllCaps = false; isCheckable = true; isChecked = isSelected
                    if (occupied2) { isEnabled = false; alpha = 0.35f }
                    val glp = GridLayout.LayoutParams().apply {
                        width = 0
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(3.dp(ctx), 3.dp(ctx), 3.dp(ctx), 3.dp(ctx))
                    }
                    layoutParams = glp
                    if (!occupied2) {
                        setOnClickListener {
                            selectedPositionNumber = if (selectedPositionNumber == numStr) "" else numStr
                            rebuildPosNumGrid()
                        }
                    }
                }
                grid.addView(btn)
            }
            posNumContainer.addView(grid)

            if (occupied.isNotEmpty()) {
                posNumContainer.addView(TextView(ctx).apply {
                    text = "● Grisé = occupé ce jour"
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                    setPadding(4.dp(ctx), 4.dp(ctx), 0, 0)
                })
            }
        }

        getPositionTypes().forEach { type ->
            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = type; isAllCaps = false; isCheckable = true
                val glp = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4.dp(ctx), 4.dp(ctx), 4.dp(ctx), 4.dp(ctx))
                }
                layoutParams = glp
                setOnClickListener {
                    selectedPositionType = type
                    selectedPositionNumber = ""
                    refreshPosTypeGrid()
                    rebuildPosNumGrid()
                }
            }
            posTypeButtons[type] = btn
            posTypeGrid.addView(btn)
        }
        refreshPosTypeGrid()
        rebuildPosNumGrid()

        // ── Boutons date & heure (actions) ────────────────────────────────────
        dateBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            try { isoFormatter.parse(selectedDateIsoValue)?.let { cal.time = it } } catch (_: ParseException) {}
            DatePickerDialog(ctx, { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                selectedDateIsoValue = isoFormatter.format(cal.time)
                dateBtn.text = dateLabel()
                rebuildPosNumGrid()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .apply { datePicker.minDate = System.currentTimeMillis() - 1000; show() }
        }

        timeBtn.setOnClickListener {
            val h = selectedTimeValue.substringBefore(":").toIntOrNull() ?: 12
            val m = selectedTimeValue.substringAfter(":").toIntOrNull() ?: 0
            TimePickerDialog(ctx, { _, hr, mn ->
                selectedTimeValue = String.format(Locale.getDefault(), "%02d:%02d", hr, mn)
                timeBtn.text = "🕐  $selectedTimeValue"
            }, h, m, true).show()
        }

        // ── Assemblage ────────────────────────────────────────────────────────
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        inner.addView(fmSection(ctx, "INFORMATIONS CLIENT"))
        inner.addView(tilFirstName)
        inner.addView(tilLastName)
        inner.addView(tilPhone)
        inner.addView(fmSection(ctx, "DATE & HEURE"))
        inner.addView(dateTimeRow)
        inner.addView(fmSection(ctx, "INVITÉS"))
        inner.addView(adultsRow)
        inner.addView(childrenRow)
        inner.addView(fmSection(ctx, "TYPE DE POSITION *"))
        inner.addView(posTypeGrid)
        inner.addView(posNumContainer)
        inner.addView(fmSection(ctx, "NOTES (optionnel)"))
        val (tilNotesFull, etNotesFull) = fmTil(ctx, "Demandes spéciales…", multiline = true)
        etNotesFull.setText(existingReservation?.notes.orEmpty())
        inner.addView(tilNotesFull)

        val scrollView = ScrollView(ctx).apply { addView(inner) }

        // ── Dialog ────────────────────────────────────────────────────────────
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existingReservation == null) "Nouvelle réservation" else "Modifier la réservation")
            .setView(scrollView)
            .setPositiveButton(if (existingReservation == null) "Confirmer" else "Enregistrer", null)
            .setNegativeButton("Annuler", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val firstName    = etFirstName.text?.toString()?.trim().orEmpty()
                val lastName     = etLastName.text?.toString()?.trim().orEmpty()
                val phone        = etPhone.text?.toString()?.trim().orEmpty()
                val adults       = getAdults()
                val children     = getChildren()
                val notes        = etNotesFull.text?.toString()?.trim()?.ifBlank { null }
                val availableNums = availablePositionNumbers(selectedDateIsoValue, selectedPositionType, existingReservation?.id)

                // Inline errors on TIL
                tilFirstName.error = if (firstName.isBlank()) "Obligatoire" else null
                tilLastName.error  = if (lastName.isBlank())  "Obligatoire" else null
                tilPhone.error     = when {
                    phone.isBlank()                    -> "Obligatoire"
                    !isValidPhoneNumber(phone)         -> "Numéro invalide (TN / FR / IT)"
                    else                               -> null
                }

                val errors = mutableListOf<String>()
                if (firstName.isBlank())                               errors.add("prénom")
                if (lastName.isBlank())                                errors.add("nom")
                if (phone.isBlank() || !isValidPhoneNumber(phone))     errors.add("téléphone")
                if (adults <= 0)                                        errors.add("au moins 1 adulte")
                if (selectedPositionType.isBlank())                    errors.add("type de position")
                if (isPastDate(selectedDateIsoValue))                  errors.add("date passée")
                if (availableNums.isNotEmpty() && selectedPositionNumber.isBlank()) errors.add("N° de position")
                if (selectedPositionNumber.isNotBlank() && !availableNums.contains(selectedPositionNumber))
                    errors.add("position déjà occupée")

                if (errors.isNotEmpty()) {
                    Snackbar.make(binding.root, "Vérifier : ${errors.joinToString(" · ")}", Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val reservation = Reservation(
                    firstName      = firstName,
                    lastName       = lastName,
                    phone          = phone,
                    adults         = adults,
                    children       = children,
                    date           = selectedDateIsoValue,
                    time           = selectedTimeValue,
                    positionType   = selectedPositionType,
                    positionNumber = selectedPositionNumber.ifBlank { null },
                    status         = existingReservation?.status ?: "pending",
                    notes          = notes,
                    createdAt      = existingReservation?.createdAt,
                    updatedAt      = existingReservation?.updatedAt
                )

                if (existingReservation == null) {
                    viewModel.addReservation(reservation) { result ->
                        result.onSuccess {
                            Snackbar.make(binding.root, "✅ Réservation ajoutée", Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }.onFailure { err ->
                            Snackbar.make(binding.root, err.message ?: "Erreur", Snackbar.LENGTH_LONG).show()
                        }
                    }
                } else {
                    viewModel.updateReservation(existingReservation.id, reservation) { result ->
                        result.onSuccess {
                            Snackbar.make(binding.root, "✅ Réservation modifiée", Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }.onFailure { err ->
                            Snackbar.make(binding.root, err.message ?: "Erreur", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
