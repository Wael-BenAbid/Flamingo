package com.example.flamingoandroid.presentation.fragments

import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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

        binding.tvReservationsStats.text =
            "${filtered.size} réservations • ${filtered.count { it.status == "confirmed" }} confirmées • ${filtered.count { it.status == "pending" }} en attente"

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

    private fun showReservationDialog(existingReservation: Reservation? = null) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
        }

        val firstNameInput = EditText(context).apply {
            hint = "Prénom *"
            setText(existingReservation?.firstName.orEmpty())
        }
        val lastNameInput = EditText(context).apply {
            hint = "Nom *"
            setText(existingReservation?.lastName.orEmpty())
        }
        val phoneInput = EditText(context).apply {
            hint = "Téléphone *"
            setText(existingReservation?.phone.orEmpty())
        }
        val adultsInput = EditText(context).apply {
            hint = "Adultes"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingReservation?.adults?.toString().orEmpty())
        }
        val childrenInput = EditText(context).apply {
            hint = "Enfants"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingReservation?.children?.toString().orEmpty())
        }
        val dateButton = MaterialButton(context).apply {
            text = "Date: ${existingReservation?.date?.takeIf { it.isNotBlank() } ?: selectedDateLabel}"
            isAllCaps = false
        }
        val timeButton = MaterialButton(context).apply {
            text = "Heure: ${existingReservation?.time?.takeIf { it.isNotBlank() } ?: "12:00"}"
            isAllCaps = false
        }
        val positionTypeButton = MaterialButton(context).apply {
            text = "Position: ${existingReservation?.positionType?.takeIf { it.isNotBlank() } ?: "choisir"}"
            isAllCaps = false
        }
        val positionNumberButton = MaterialButton(context).apply {
            text = "N° de position: ${existingReservation?.positionNumber?.takeIf { it.isNotBlank() } ?: "choisir"}"
            isAllCaps = false
            isEnabled = existingReservation?.positionType?.isNotBlank() == true
        }
        val notesInput = EditText(context).apply {
            hint = "Notes spéciales"
            minLines = 3
            maxLines = 5
            setText(existingReservation?.notes.orEmpty())
        }

        container.addView(firstNameInput)
        container.addView(lastNameInput)
        container.addView(phoneInput)
        container.addView(adultsInput)
        container.addView(childrenInput)
        container.addView(dateButton)
        container.addView(timeButton)
        container.addView(positionTypeButton)
        container.addView(positionNumberButton)
        container.addView(notesInput)

        var selectedDateIsoValue = existingReservation?.date?.takeIf { it.isNotBlank() } ?: selectedDateIso.ifBlank { isoFormatter.format(java.util.Date()) }
        var selectedTimeValue = existingReservation?.time?.takeIf { it.isNotBlank() } ?: "12:00"
        var selectedPositionType = existingReservation?.positionType?.takeIf { it.isNotBlank() } ?: ""
        var selectedPositionNumber = existingReservation?.positionNumber?.takeIf { it.isNotBlank() } ?: ""

        fun refreshPositionNumberButton() {
            val available = availablePositionNumbers(selectedDateIsoValue, selectedPositionType, existingReservation?.id)
            positionNumberButton.isEnabled = selectedPositionType.isNotBlank() && (available.isNotEmpty() || selectedPositionNumber.isNotBlank())
            positionNumberButton.text = when {
                selectedPositionNumber.isNotBlank() -> "N° de position: $selectedPositionNumber"
                selectedPositionType.isBlank() -> "N° de position: choisir d'abord la position"
                available.isEmpty() -> "Liste d'attente (aucune position)"
                else -> "N° de position: choisir"
            }
        }

        fun getPositionTypes(): List<String> {
            val types = availablePositions.map { it.type }.filter { it.isNotBlank() }
            return if (types.isNotEmpty()) types else defaultPositionTypes
        }

        fun showPositionNumberPicker() {
            val count = availablePositions.firstOrNull { it.type == selectedPositionType }?.count ?: 0
            val occupied = occupiedPositionNumbers(selectedDateIsoValue, selectedPositionType, existingReservation?.id)
            if (count <= 0) {
                Snackbar.make(binding.root, "Aucune position disponible pour ce type", Snackbar.LENGTH_SHORT).show()
                return
            }

            var pickerDialog: AlertDialog? = null
            val scrollView = ScrollView(context)
            val list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 8, 24, 8)
            }

            val summary = TextView(context).apply {
                text = if (occupied.isEmpty()) {
                    "Toutes les positions sont disponibles."
                } else {
                    "Positions occupées: ${occupied.sorted().joinToString(", ") { "N° $it" }}"
                }
                setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                textSize = 12f
            }
            list.addView(summary)

            (1..count).forEach { number ->
                val numberString = number.toString()
                val isOccupied = occupied.contains(numberString)
                val button = MaterialButton(context).apply {
                    text = if (isOccupied) "N° $number - occupé" else "N° $number"
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 12
                    }
                    if (isOccupied) {
                        isEnabled = false
                        setBackgroundColor(android.graphics.Color.parseColor("#263238"))
                        setTextColor(android.graphics.Color.WHITE)
                    } else {
                        setBackgroundColor(ContextCompat.getColor(context, R.color.ocean_light))
                        setTextColor(ContextCompat.getColor(context, R.color.ocean_dark))
                        setOnClickListener {
                            selectedPositionNumber = numberString
                            refreshPositionNumberButton()
                            pickerDialog?.dismiss()
                        }
                    }
                }
                list.addView(button)
            }

            scrollView.addView(list)
            pickerDialog = MaterialAlertDialogBuilder(context)
                .setTitle("Choisir le numéro")
                .setView(scrollView)
                .setNegativeButton("Fermer", null)
                .create()
            pickerDialog.show()
        }

        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            try {
                isoFormatter.parse(selectedDateIsoValue)?.let { calendar.time = it }
            } catch (_: ParseException) {
                calendar.time = java.util.Date()
            }
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    selectedDateIsoValue = isoFormatter.format(calendar.time)
                    selectedDateLabel = displayFormatter.format(calendar.time)
                    dateButton.text = "Date: $selectedDateLabel"
                    refreshPositionNumberButton()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
                show()
            }
        }

        timeButton.setOnClickListener {
            val now = Calendar.getInstance()
            val initialHour = selectedTimeValue.substringBefore(":").toIntOrNull() ?: now.get(Calendar.HOUR_OF_DAY)
            val initialMinute = selectedTimeValue.substringAfter(":").toIntOrNull() ?: now.get(Calendar.MINUTE)
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    selectedTimeValue = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                    timeButton.text = "Heure: $selectedTimeValue"
                },
                initialHour,
                initialMinute,
                true
            ).show()
        }

        positionTypeButton.setOnClickListener {
            val types = getPositionTypes()
            MaterialAlertDialogBuilder(context)
                .setTitle("Choisir la position")
                .setItems(types.toTypedArray()) { _, which ->
                    selectedPositionType = types[which]
                    selectedPositionNumber = ""
                    positionTypeButton.text = "Position: $selectedPositionType"
                    refreshPositionNumberButton()
                }
                .show()
        }

        positionNumberButton.setOnClickListener {
            showPositionNumberPicker()
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existingReservation == null) "Nouvelle Réservation" else "Modifier la Réservation")
            .setView(container)
            .setPositiveButton(if (existingReservation == null) "Confirmer" else "Enregistrer", null)
            .setNegativeButton("Annuler", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val firstName = firstNameInput.text.toString().trim()
                val lastName = lastNameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val adults = adultsInput.text.toString().toIntOrNull()
                val children = childrenInput.text.toString().toIntOrNull() ?: 0
                val notes = notesInput.text.toString().trim().ifBlank { null }
                val availableNumbers = availablePositionNumbers(selectedDateIsoValue, selectedPositionType, existingReservation?.id)

                val errors = mutableListOf<String>()
                if (firstName.isBlank()) errors.add("prénom")
                if (lastName.isBlank()) errors.add("nom")
                if (phone.isBlank()) errors.add("téléphone")
                if (adults == null || adults <= 0) errors.add("nombre d'adultes")
                if (selectedPositionType.isBlank()) errors.add("position")
                if (isPastDate(selectedDateIsoValue)) errors.add("date passée")
                if (phone.isNotBlank() && !isValidPhoneNumber(phone)) errors.add("numéro de téléphone valide")
                if (availableNumbers.isNotEmpty() && selectedPositionNumber.isBlank()) errors.add("numéro de position")
                if (selectedPositionNumber.isNotBlank() && !availableNumbers.contains(selectedPositionNumber)) errors.add("position déjà occupée")

                if (errors.isNotEmpty()) {
                    Snackbar.make(binding.root, "Merci de vérifier: ${errors.joinToString(", ")}", Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val reservation = Reservation(
                    firstName = firstName,
                    lastName = lastName,
                    phone = phone,
                    adults = adults ?: 0,
                    children = children,
                    date = selectedDateIsoValue,
                    time = selectedTimeValue,
                    positionType = selectedPositionType,
                    positionNumber = selectedPositionNumber.ifBlank { null },
                    status = existingReservation?.status ?: "pending",
                    notes = notes,
                    createdAt = existingReservation?.createdAt,
                    updatedAt = existingReservation?.updatedAt
                )

                if (existingReservation == null) {
                    viewModel.addReservation(reservation) { result ->
                        result.onSuccess {
                            Snackbar.make(binding.root, "Réservation ajoutée", Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }.onFailure { error ->
                            Snackbar.make(binding.root, error.message ?: "Erreur lors de l'enregistrement", Snackbar.LENGTH_LONG).show()
                        }
                    }
                } else {
                    viewModel.updateReservation(existingReservation.id, reservation) { result ->
                        result.onSuccess {
                            Snackbar.make(binding.root, "Réservation modifiée", Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }.onFailure { error ->
                            Snackbar.make(binding.root, error.message ?: "Erreur lors de l'enregistrement", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        refreshPositionNumberButton()
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
