package com.example.flamingoandroid.presentation.fragments

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.R
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.data.models.Worker
import com.example.flamingoandroid.databinding.FragmentWorkersBinding
import com.example.flamingoandroid.presentation.access.StaffAccess
import com.example.flamingoandroid.presentation.util.bindPresenceStatus
import com.example.flamingoandroid.presentation.util.formatMoney
import com.example.flamingoandroid.presentation.util.initials
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WorkersFragment : Fragment() {

    // Catégories canoniques — synchronisées avec WORKER_CATEGORIES de shared/constants.ts
    private val workerCategories = listOf(
        "Responsable",
        "Serveur",
        "Cuisinier",
        "Barman"
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
        statsView.text = "${worker.dailyWage.toInt()} DT/jour  •  ${worker.attendanceCount.toInt()} jours présents\n${formatMoney(worker.totalAdvances)} avances  •  ${formatMoney(worker.totalPenalties)} pénalités"

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

        val presenceDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choisir l'état du jour — ${worker.fullName}")
            .setSingleChoiceItems(labels, currentIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Valider") { _, _ ->
                if (canManageWorkers()) {
                    updateWorkerPresence(worker, today, values[selectedIndex])
                } else {
                    Snackbar.make(binding.root, "Action réservée aux administrateurs et responsables", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .create()
        presenceDialog.setCanceledOnTouchOutside(false)
        presenceDialog.show()
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

    // ── Helpers formulaires ───────────────────────────────────────────────────

    private fun Int.dp(context: Context) =
        (this * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun makeTil(
        context: Context,
        hint: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
        isPassword: Boolean = false,
        suffixText: String? = null,
    ): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(context).apply {
            this.hint = hint
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(12f, 12f, 12f, 12f)
            if (isPassword) {
                endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
            if (suffixText != null) this.suffixText = suffixText
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14.dp(context) }
        }
        val et = TextInputEditText(til.context).apply {
            this.inputType = if (isPassword)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else inputType
        }
        til.addView(et)
        return til to et
    }

    private fun sectionLabel(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            letterSpacing = 0.1f
            setPadding(2.dp(context), 4.dp(context), 0, 6.dp(context))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

    // ── Dialog : Nouveau travailleur ──────────────────────────────────────────

    private fun showAddWorkerDialog() {
        if (!canManageWorkers()) {
            Snackbar.make(binding.root, "Action réservée aux administrateurs et responsables", Snackbar.LENGTH_SHORT).show()
            return
        }

        val context = requireContext()
        val pad = 20.dp(context)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var selectedCategory = workerCategories[1]   // "Serveur" par défaut
        var selectedStartDate = formatter.format(Date())

        // ── Champs texte ─────────────────────────────────────────────────────
        val (tilName, etName) = makeTil(context, "Nom complet")
        val (tilEmail, etEmail) = makeTil(
            context, "Adresse e-mail",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val (tilPassword, etPassword) = makeTil(
            context, "Mot de passe (6 car. min.)",
            isPassword = true
        )
        val (tilWage, etWage) = makeTil(
            context, "Salaire / jour",
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            suffixText = "DT"
        )

        // ── Sélecteur de catégorie (grille 2 × 2) ───────────────────────────
        val categoryButtons = mutableMapOf<String, MaterialButton>()
        val categoryGrid = GridLayout(context).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14.dp(context) }
        }

        fun refreshCategoryButtons() {
            categoryButtons.forEach { (cat, btn) ->
                val selected = cat == selectedCategory
                btn.isChecked = selected
            }
        }

        workerCategories.forEach { cat ->
            val btn = MaterialButton(
                context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = cat
                isAllCaps = false
                isCheckable = true
                setOnClickListener {
                    selectedCategory = cat
                    refreshCategoryButtons()
                }
                val glp = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
                }
                layoutParams = glp
            }
            categoryButtons[cat] = btn
            categoryGrid.addView(btn)
        }
        refreshCategoryButtons()

        // ── Bouton date d'entrée ─────────────────────────────────────────────
        val dateBtn = MaterialButton(
            context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "📅  $selectedStartDate"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14.dp(context) }
            setOnClickListener {
                val cal = Calendar.getInstance()
                try { formatter.parse(selectedStartDate)?.let { cal.time = it } } catch (_: Exception) {}
                DatePickerDialog(context, { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedStartDate = formatter.format(cal.time)
                    text = "📅  $selectedStartDate"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        // ── Assemblage de la vue ─────────────────────────────────────────────
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        inner.addView(tilName)
        inner.addView(tilEmail)
        inner.addView(tilPassword)
        inner.addView(sectionLabel(context, "CATÉGORIE"))
        inner.addView(categoryGrid)
        inner.addView(sectionLabel(context, "DATE D'ENTRÉE"))
        inner.addView(dateBtn)
        inner.addView(sectionLabel(context, "RÉMUNÉRATION"))
        inner.addView(tilWage)

        val scrollView = ScrollView(context).apply { addView(inner) }

        // ── Dialog ───────────────────────────────────────────────────────────
        val addWorkerDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Nouveau travailleur")
            .setView(scrollView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val fullName = etName.text?.toString()?.trim().orEmpty()
                val email    = etEmail.text?.toString()?.trim().orEmpty()
                val password = etPassword.text?.toString()?.trim().orEmpty()
                val wage     = etWage.text?.toString()?.toDoubleOrNull() ?: 0.0

                when {
                    fullName.isBlank() -> {
                        tilName.error = "Nom obligatoire"
                        return@setPositiveButton
                    }
                    email.isBlank() -> {
                        tilEmail.error = "E-mail obligatoire"
                        return@setPositiveButton
                    }
                    password.length < 6 -> {
                        tilPassword.error = "6 caractères minimum"
                        return@setPositiveButton
                    }
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseService.createStaffAuthAccount(email, password, selectedCategory)
                        .onSuccess { account ->
                            firebaseService.addWorker(
                                Worker(
                                    id          = account.uid,
                                    uid         = account.uid,
                                    fullName    = fullName,
                                    category    = selectedCategory,
                                    role        = account.role,
                                    dailyWage   = wage,
                                    startDate   = selectedStartDate,
                                    email       = account.email
                                )
                            ).onSuccess {
                                viewModel.loadWorkers()
                                Snackbar.make(binding.root, "✅ Compte créé pour $fullName", Snackbar.LENGTH_SHORT).show()
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
            .create()
        addWorkerDialog.setCanceledOnTouchOutside(false)
        addWorkerDialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                val hasData = etName.text?.isNotBlank() == true ||
                        etEmail.text?.isNotBlank() == true ||
                        etPassword.text?.isNotBlank() == true
                if (hasData) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Quitter sans sauvegarder ?")
                        .setMessage("Les informations saisies seront perdues.")
                        .setPositiveButton("Quitter") { _, _ -> addWorkerDialog.dismiss() }
                        .setNegativeButton("Continuer", null)
                        .show()
                } else {
                    addWorkerDialog.dismiss()
                }
                true
            } else {
                false
            }
        }
        addWorkerDialog.show()
    }

    // ── Dialog : Avance / Pénalité / Paiement ────────────────────────────────

    private fun showMoneyDialog(
        title: String,
        reasonHint: String,
        amountHint: String,
        onSubmit: (amount: Double, secondValue: String) -> Unit
    ) {
        val context = requireContext()
        val pad = 20.dp(context)

        val (tilAmount, etAmount) = makeTil(
            context, amountHint,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            suffixText = "DT"
        )
        val (tilReason, etReason) = makeTil(context, reasonHint)

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(tilAmount)
            addView(tilReason)
        }

        val moneyDialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(inner)
            .setPositiveButton("Valider") { _, _ ->
                val amount = etAmount.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) {
                    Snackbar.make(binding.root, "Montant invalide", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val secondValue = etReason.text?.toString()?.trim().orEmpty()
                onSubmit(amount, secondValue)
            }
            .setNegativeButton("Annuler", null)
            .create()
        moneyDialog.setCanceledOnTouchOutside(false)
        moneyDialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                val hasData = etAmount.text?.isNotBlank() == true || etReason.text?.isNotBlank() == true
                if (hasData) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Quitter sans sauvegarder ?")
                        .setMessage("Les informations saisies seront perdues.")
                        .setPositiveButton("Quitter") { _, _ -> moneyDialog.dismiss() }
                        .setNegativeButton("Continuer", null)
                        .show()
                } else {
                    moneyDialog.dismiss()
                }
                true
            } else {
                false
            }
        }
        moneyDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
