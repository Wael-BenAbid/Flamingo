package com.example.flamingoandroid.presentation.fragments

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.R
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.data.models.Worker
import com.example.flamingoandroid.databinding.FragmentWorkersBinding
import com.example.flamingoandroid.presentation.util.bindPresenceStatus
import com.example.flamingoandroid.presentation.util.formatMoney
import com.example.flamingoandroid.presentation.util.initials
import com.example.flamingoandroid.presentation.access.StaffAccess
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WorkersFragment : Fragment() {

    private val workerCategories = listOf(
        "Chef serveur",
        "Serveur",
        "Cuisine",
        "Sécurité",
        "Nettoyage",
        "Responsable"
    )

    private var _binding: FragmentWorkersBinding? = null
    private val binding get() = _binding!!
    private val viewModel = com.example.flamingoandroid.presentation.viewmodels.WorkersViewModel()
    private val firebaseService = FirebaseService()
    private var searchQuery: String = ""
    private var currentWorkers: List<Worker> = emptyList()
    private var currentRole: String = StaffAccess.ROLE_NONE
    private var roleResolved: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddWorker.visibility = View.GONE
        binding.btnAddWorker.setOnClickListener {
            showAddWorkerDialog()
        }

        binding.etWorkersSearch.doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString().orEmpty()
            renderWorkers()
        }

        resolveCurrentRole()
        observeState()
    }

    private fun resolveCurrentRole() {
        roleResolved = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resolvedRole = firebaseService.getCurrentUserRole(firebaseService.getCurrentUser())
                currentRole = StaffAccess.normalize(resolvedRole)
            } catch (_: Exception) {
                currentRole = StaffAccess.ROLE_NONE
            } finally {
                roleResolved = true
                binding.btnAddWorker.visibility = if (canManageWorkers()) View.VISIBLE else View.GONE
                renderWorkers()
            }
        }
    }

    private fun canManageWorkers(): Boolean {
        return currentRole == StaffAccess.ROLE_ADMIN || currentRole == StaffAccess.ROLE_RESPONSABLE
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.isLoading.collectLatest { loading ->
                binding.pbWorkersLoading.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.errorMessage.collectLatest { error ->
                error?.let { binding.tvWorkersStats.text = it }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.workers.collectLatest { workers ->
                currentWorkers = workers
                renderWorkers()
            }
        }
    }

    private fun renderWorkers() {
        if (!roleResolved) {
            binding.tvWorkersStats.text = "Chargement du profil..."
            binding.tvWorkersEmpty.visibility = View.VISIBLE
            binding.tvWorkersEmpty.text = "Chargement de vos données..."
            binding.workersContainer.removeAllViews()
            binding.btnAddWorker.visibility = View.GONE
            return
        }

        val visibleWorkers = filterVisibleWorkersByRole(currentWorkers)

        val filtered = visibleWorkers.filter {
            it.fullName.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }

        val totalPaid = filtered.sumOf { it.totalPaid }
        binding.tvWorkersStats.text = "${filtered.size} travailleurs • ${formatMoney(totalPaid)} déjà payé"
        binding.btnAddWorker.visibility = if (canManageWorkers()) View.VISIBLE else View.GONE

        val emptyMessage = if (!canManageWorkers() && filtered.isEmpty()) {
            "Aucun dossier travailleur n'est lié à ce compte."
        } else {
            "Aucun travailleur"
        }
        binding.tvWorkersEmpty.text = emptyMessage
        binding.tvWorkersEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.workersContainer.removeAllViews()

        filtered.forEach { worker ->
            val card = layoutInflater.inflate(R.layout.item_worker_admin, binding.workersContainer, false)
            bindWorkerCard(card, worker)
            binding.workersContainer.addView(card)
        }
    }

    private fun filterVisibleWorkersByRole(workers: List<Worker>): List<Worker> {
        if (canManageWorkers()) {
            return workers
        }

        val currentUser = firebaseService.getCurrentUser() ?: return emptyList()
        val currentUid = currentUser.uid
        val currentEmail = currentUser.email?.trim()?.lowercase(Locale.getDefault())
        val currentDisplayName = currentUser.displayName?.trim()?.lowercase(Locale.getDefault())

        val byUid = workers.filter { worker ->
            worker.uid == currentUid || worker.id == currentUid
        }
        if (byUid.isNotEmpty()) {
            return byUid
        }

        val byEmail = workers.filter { worker ->
            currentEmail != null && worker.email.trim().lowercase(Locale.getDefault()) == currentEmail
        }
        if (byEmail.isNotEmpty()) {
            return byEmail
        }

        val byName = workers.filter { worker ->
            currentDisplayName != null && worker.fullName.trim().lowercase(Locale.getDefault()) == currentDisplayName
        }
        if (byName.isNotEmpty()) {
            return byName
        }

        val fallbackUser = currentUser
        return if (fallbackUser != null) {
            listOf(
                Worker(
                    id = fallbackUser.uid,
                    uid = fallbackUser.uid,
                    fullName = fallbackUser.displayName?.takeIf { it.isNotBlank() }
                        ?: fallbackUser.email?.substringBefore("@")
                        ?: "Travailleur",
                    category = StaffAccess.roleLabel(currentRole),
                    role = currentRole,
                    email = fallbackUser.email.orEmpty(),
                    isActive = true,
                )
            )
        } else {
            emptyList()
        }
    }

    private fun bindWorkerCard(root: View, worker: Worker) {
        val canEditWorkers = canManageWorkers()
        val avatar = root.findViewById<TextView>(R.id.tvWorkerAvatar)
        val nameView = root.findViewById<TextView>(R.id.tvWorkerName)
        val categoryView = root.findViewById<TextView>(R.id.tvWorkerCategory)
        val presenceView = root.findViewById<TextView>(R.id.tvWorkerPresence)
        val statsView = root.findViewById<TextView>(R.id.tvWorkerStats)
        val advanceButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWorkerAdvance)
        val penaltyButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWorkerPenalty)
        val paymentButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWorkerPayment)
        val deleteButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWorkerDelete)

        avatar.text = initials(worker.fullName, "W")
        nameView.text = worker.fullName.ifBlank { "Travailleur" }
        categoryView.text = worker.category.ifBlank { "Catégorie" }
        presenceView.bindPresenceStatus(requireContext(), getEffectivePresence(worker))
        statsView.text = "${worker.dailyWage.toInt()} DT/jour • ${worker.attendanceCount.toInt()} jours • ${formatMoney(worker.totalAdvances)} avances • ${formatMoney(worker.totalPenalties)} pénalités"

        root.isClickable = true
        root.isFocusable = true
        root.setOnClickListener {
            showWorkerDetailsDialog(worker)
        }

        presenceView.isClickable = canEditWorkers
        presenceView.isFocusable = canEditWorkers
        if (canEditWorkers) {
            presenceView.setOnClickListener {
                showPresenceDialog(worker)
            }
        } else {
            presenceView.setOnClickListener(null)
        }

        advanceButton.visibility = if (canEditWorkers) View.VISIBLE else View.GONE
        penaltyButton.visibility = if (canEditWorkers) View.VISIBLE else View.GONE
        paymentButton.visibility = if (canEditWorkers) View.VISIBLE else View.GONE
        deleteButton.visibility = if (canEditWorkers) View.VISIBLE else View.GONE

        advanceButton.setOnClickListener {
            if (!canEditWorkers) return@setOnClickListener
            showMoneyDialog("Ajouter une avance", "Motif de l'avance", "Montant (DT)") { amount, reason ->
                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseService.addAdvance(worker.id, amount, reason)
                    viewModel.loadWorkers()
                }
            }
        }

        penaltyButton.setOnClickListener {
            if (!canEditWorkers) return@setOnClickListener
            showMoneyDialog("Ajouter une pénalité", "Motif de la pénalité", "Montant (DT)") { amount, reason ->
                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseService.addPenalty(worker.id, amount, reason)
                    viewModel.loadWorkers()
                }
            }
        }

        paymentButton.setOnClickListener {
            if (!canEditWorkers) return@setOnClickListener
            showMoneyDialog("Enregistrer un paiement", "Méthode (cash/transfer/check)", "Montant (DT)") { amount, method ->
                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseService.addPayment(worker.id, amount, method)
                    viewModel.loadWorkers()
                }
            }
        }

        deleteButton.setOnClickListener {
            if (!canEditWorkers) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Supprimer ce travailleur ?")
                .setMessage(worker.fullName)
                .setPositiveButton("Supprimer") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        firebaseService.deleteWorker(worker.id)
                        viewModel.loadWorkers()
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun showPresenceDialog(worker: Worker) {
        if (!canManageWorkers()) {
            Snackbar.make(binding.root, "Action réservée aux administrateurs et responsables", Snackbar.LENGTH_SHORT).show()
            return
        }

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val labels = arrayOf("Présent", "Demi-journée", "Absent", "Off")
        val values = arrayOf("present", "half", "absent", "off")

        // Pré-sélectionner l'index courant si possible
        val currentIndex = values.indexOf(worker.currentPresence).coerceAtLeast(0)
        var selectedIndex = currentIndex

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choisir l'état du jour — ${worker.fullName}")
            .setSingleChoiceItems(labels, currentIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Valider") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val isAdmin = firebaseService.hasAdminAccess(firebaseService.getCurrentUser())
                    if (isAdmin) {
                        updateWorkerPresence(worker, today, values[selectedIndex])
                    } else {
                        Snackbar.make(binding.root, "Action réservée aux administrateurs", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showWorkerDetailsDialog(worker: Worker) {
        viewLifecycleOwner.lifecycleScope.launch {
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            val contentView = buildWorkerDetailsView(worker, monthKey, emptyMap())
            val builder = MaterialAlertDialogBuilder(requireContext())
                .setTitle(worker.fullName.ifBlank { "Travailleur" })
                .setView(contentView)
                .setNeutralButton("Fermer", null)

            if (canManageWorkers()) {
                builder.setPositiveButton("État du jour") { _, _ ->
                    showPresenceDialog(worker)
                }
            }

            val dialog = builder.show()

            combine(
                firebaseService.observeWorkerMonthlyAttendance(worker.id, monthKey),
                firebaseService.observeWorkerAttendanceRecords(worker.id)
            ) { monthAttendance, records ->
                val fallbackDays = records
                    .asSequence()
                    .filter { it.date.startsWith(monthKey) }
                    .associate { it.date.takeLast(2) to it.status }
                monthAttendance.days + fallbackDays
            }.collectLatest { days ->
                renderWorkerDetails(contentView, worker, monthKey, days)
                dialog.setTitle(worker.fullName.ifBlank { "Travailleur" })
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
    }

    private fun renderWorkerDetails(container: LinearLayout, worker: Worker, monthKey: String, days: Map<String, String>) {
        container.removeAllViews()

        val effectivePresence = getEffectivePresence(worker)
        val presenceLabel = when (effectivePresence) {
            "present" -> "Présent"
            "half" -> "Demi-journée"
            "absent" -> "Absent"
            else -> "Off"
        }

        fun addLine(label: String, value: String) {
            container.addView(TextView(requireContext()).apply {
                text = "$label: $value"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                textSize = 14f
                setPadding(0, 4, 0, 4)
            })
        }

        addLine("Mois", monthKey)
        addLine("Catégorie", worker.category.ifBlank { "-" })
        addLine("Salaire journalier", "${worker.dailyWage.toInt()} DT")
        addLine("Présence actuelle", presenceLabel)
        addLine("Jours comptés", worker.attendanceCount.toInt().toString())
        addLine("Avances", formatMoney(worker.totalAdvances))
        addLine("Pénalités", formatMoney(worker.totalPenalties))
        addLine("Total payé", formatMoney(worker.totalPaid))

        val statusChip = TextView(requireContext()).apply {
            text = presenceLabel.uppercase(Locale.getDefault())
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            textSize = 11f
            setPadding(20, 12, 20, 12)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_rounded)
            val chipColor = when (effectivePresence) {
                "present" -> R.color.success
                "half" -> R.color.warning
                "absent" -> R.color.error
                else -> R.color.text_secondary
            }
            ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), chipColor)))
        }
        container.addView(statusChip)

        val stripTitle = TextView(requireContext()).apply {
            text = "Calendrier mensuel"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            textSize = 15f
            setPadding(0, 18, 0, 8)
        }
        container.addView(stripTitle)

        val legend = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10)
        }

        fun legendItem(label: String, colorRes: Int) {
            legend.addView(TextView(requireContext()).apply {
                text = label
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 10f
                setPadding(14, 8, 14, 8)
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_rounded)
                ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes)))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = 8
                layoutParams = lp
            })
        }

        legendItem("Présent", R.color.success)
        legendItem("Demi", R.color.warning)
        legendItem("Absent", R.color.error)
        legendItem("Off", R.color.text_secondary)
        container.addView(legend)

        val scroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val calendar = Calendar.getInstance()
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val startDate = worker.startDate.orEmpty()
        val monthPrefix = "$monthKey-"
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        fun resolveDayStatus(dayKey: String, dateStr: String): String? {
            return days[dayKey] ?: if (dateStr <= todayStr) "off" else null
        }

        for (day in 1..totalDays) {
            val dayKey = String.format(Locale.getDefault(), "%02d", day)
            val dateStr = "$monthPrefix$dayKey"
            val effectiveStatus = resolveDayStatus(dayKey, dateStr)
            val beforeStart = startDate.isNotBlank() && dateStr < startDate
            val cellColor = when {
                effectiveStatus == "present" -> R.color.success
                effectiveStatus == "half" -> R.color.warning
                effectiveStatus == "absent" -> R.color.error
                effectiveStatus == "off" -> R.color.text_secondary
                beforeStart -> R.color.error
                else -> R.color.ocean_light
            }

            row.addView(TextView(requireContext()).apply {
                text = day.toString()
                setTextColor(ContextCompat.getColor(requireContext(), if (effectiveStatus == null && !beforeStart) R.color.ocean_dark else R.color.white))
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                setPadding(22, 18, 22, 18)
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_rounded)
                ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), cellColor)))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = 8
                layoutParams = lp
            })
        }

        scroll.addView(row)
        container.addView(scroll)
    }

    private fun buildWorkerDetailsView(worker: Worker, monthKey: String, days: Map<String, String>): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
            renderWorkerDetails(this, worker, monthKey, days)
        }
    }

    private fun getEffectivePresence(worker: Worker): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return if (worker.lastPresenceDate == today) worker.currentPresence else "off"
    }

    private fun updateWorkerPresence(worker: Worker, date: String, status: String) {
        if (date.isBlank()) {
            Snackbar.make(binding.root, "Date invalide", Snackbar.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = firebaseService.upsertWorkerAttendance(worker.id, date, status)
            result.onSuccess {
                viewModel.loadWorkers()
                val message = when (status) {
                    "present" -> "✅ Présence enregistrée"
                    "half" -> "🟠 Demi-journée enregistrée"
                    "absent" -> "❌ Absence enregistrée"
                    else -> "🌴 Jour de repos enregistré"
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }.onFailure { error ->
                Snackbar.make(binding.root, error.message ?: "Erreur de mise à jour", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddWorkerDialog() {
        if (!canManageWorkers()) {
            Snackbar.make(binding.root, "Action réservée aux administrateurs et responsables", Snackbar.LENGTH_SHORT).show()
            return
        }

        val context = requireContext()
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
        }
        val nameInput = EditText(context).apply { hint = "Nom complet" }
        val emailInput = EditText(context).apply {
            hint = "Adresse e-mail"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val passwordInput = EditText(context).apply {
            hint = "Mot de passe"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val categoryButton = MaterialButton(context).apply {
            text = "Catégorie: Serveur"
            isAllCaps = false
        }
        val dateButton = MaterialButton(context).apply {
            text = "Date d'entrée: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())}"
            isAllCaps = false
        }
        val wageInput = EditText(context).apply {
            hint = "Salaire journalier"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        container.addView(nameInput)
        container.addView(emailInput)
        container.addView(passwordInput)
        container.addView(categoryButton)
        container.addView(dateButton)
        container.addView(wageInput)

        var selectedCategory = workerCategories[1]
        var selectedStartDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())

        categoryButton.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("Choisir la catégorie")
                .setItems(workerCategories.toTypedArray()) { _, which ->
                    selectedCategory = workerCategories[which]
                    categoryButton.text = "Catégorie: $selectedCategory"
                }
                .show()
        }

        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                formatter.parse(selectedStartDate)?.let { calendar.time = it }
            } catch (_: Exception) {
                calendar.time = java.util.Date()
            }

            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedStartDate = formatter.format(calendar.time)
                    dateButton.text = "Date d'entrée: $selectedStartDate"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Nouveau travailleur")
            .setView(container)
            .setPositiveButton("Enregistrer") { _, _ ->
                val fullName = nameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()
                val wage = wageInput.text.toString().toDoubleOrNull() ?: 0.0

                if (fullName.isBlank()) {
                    Snackbar.make(binding.root, "Le nom complet est obligatoire", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (email.isBlank()) {
                    Snackbar.make(binding.root, "L'adresse e-mail est obligatoire", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (password.length < 6) {
                    Snackbar.make(binding.root, "Le mot de passe doit contenir au moins 6 caractères", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseService.createStaffAuthAccount(email, password, selectedCategory)
                        .onSuccess { account ->
                            firebaseService.addWorker(
                                Worker(
                                    id = account.uid,
                                    uid = account.uid,
                                    fullName = fullName,
                                    category = selectedCategory,
                                    role = account.role,
                                    dailyWage = wage,
                                    startDate = selectedStartDate,
                                    email = account.email
                                )
                            ).onSuccess {
                                viewModel.loadWorkers()
                                Snackbar.make(binding.root, "Compte créé pour $fullName", Snackbar.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Snackbar.make(binding.root, error.message ?: "Erreur lors de l'enregistrement", Snackbar.LENGTH_LONG).show()
                            }
                        }
                        .onFailure { error ->
                            Snackbar.make(binding.root, error.message ?: "Impossible de créer le compte", Snackbar.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showMoneyDialog(
        title: String,
        reasonHint: String,
        amountHint: String,
        onSubmit: (amount: Double, secondValue: String) -> Unit
    ) {
        val context = requireContext()
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
        }
        val amountInput = EditText(context).apply {
            hint = amountHint
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val reasonInput = EditText(context).apply { hint = reasonHint }
        container.addView(amountInput)
        container.addView(reasonInput)

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Valider") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                val secondValue = reasonInput.text.toString().trim()
                onSubmit(amount, secondValue)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
