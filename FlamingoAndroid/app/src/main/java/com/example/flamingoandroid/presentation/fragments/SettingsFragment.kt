package com.example.flamingoandroid.presentation.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.databinding.FragmentSettingsBinding
import com.example.flamingoandroid.presentation.activities.HomeActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val firebaseService = FirebaseService()
    private var positions = listOf<Position>()
    private val positionEdits = mutableMapOf<String, Triple<EditText, EditText, EditText>>() // id -> (countInput, priceInput, childPriceInput)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = firebaseService.getCurrentUser()
        binding.tvSettingsUser.text = user?.email ?: "Compte administrateur"
        binding.btnSettingsLogout.setOnClickListener {
            (activity as? HomeActivity)?.logout()
        }

        // Initialize notification switches
        binding.switchStockAlert.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationPreference("stock_alert", isChecked)
        }

        binding.switchArrivalAlert.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationPreference("arrival_alert", isChecked)
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationPreference("general_notifications", isChecked)
        }

        loadNotificationPreferences()
        loadPositions()
    }

    private fun loadPositions() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val loaded = firebaseService.getPositions()
                if (loaded.isNotEmpty()) {
                    positions = loaded
                } else {
                    // Initialize with defaults if empty
                    positions = listOf(
                        Position(type = "Terrasse", count = 4, price = 50.0, childPrice = 25.0),
                        Position(type = "Parasol", count = 5, price = 30.0, childPrice = 15.0),
                        Position(type = "Cabane", count = 12, price = 150.0, childPrice = 75.0),
                        Position(type = "Payotte", count = 12, price = 100.0, childPrice = 50.0),
                        Position(type = "Cabane avec piscine privée", count = 2, price = 350.0, childPrice = 175.0),
                    )
                }
                renderPositions()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderPositions() {
        binding.settingsPositionsContainer.removeAllViews()
        positionEdits.clear()

        positions.forEach { position ->
            val itemView = layoutInflater.inflate(com.example.flamingoandroid.R.layout.item_position_settings, binding.settingsPositionsContainer, false)

            val tvType = itemView.findViewById<TextView>(com.example.flamingoandroid.R.id.tvPositionType)
            val etCount = itemView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.flamingoandroid.R.id.etPositionCount)
            val etPrice = itemView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.flamingoandroid.R.id.etPositionPrice)
            val etChildPrice = itemView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.example.flamingoandroid.R.id.etPositionChildPrice)

            tvType.text = position.type
            etCount.setText(position.count.toString())
            etPrice.setText(String.format("%.2f", position.price))
            etChildPrice.setText(String.format("%.2f", position.childPrice))

            positionEdits[position.id] = Triple(etCount, etPrice, etChildPrice)
            binding.settingsPositionsContainer.addView(itemView)
        }

        // Add Save button
        val saveButton = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "Enregistrer les tarifs"
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.flamingoandroid.R.color.primary))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
                height = 48
            }
            setOnClickListener { savePositions() }
        }
        binding.settingsPositionsContainer.addView(saveButton)
    }

    private fun savePositions() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                positions.forEach { pos ->
                    val edits = positionEdits[pos.id]
                    if (edits != null) {
                        val newCount = edits.first.text.toString().toIntOrNull() ?: pos.count
                        val newPrice = edits.second.text.toString().toDoubleOrNull() ?: pos.price
                        val newChildPrice = edits.third.text.toString().toDoubleOrNull() ?: pos.childPrice

                        if (pos.id.isEmpty()) {
                            firebaseService.addPosition(pos.copy(count = newCount, price = newPrice, childPrice = newChildPrice))
                        } else {
                            firebaseService.updatePosition(pos.id, newCount, newPrice, newChildPrice)
                        }
                    }
                }
                Snackbar.make(binding.root, "Positions enregistrées!", Snackbar.LENGTH_SHORT).show()
                loadPositions()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveNotificationPreference(key: String, value: Boolean) {
        val sharedPref = requireContext().getSharedPreferences("ocean_prefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean(key, value).apply()
    }

    private fun loadNotificationPreferences() {
        val sharedPref = requireContext().getSharedPreferences("ocean_prefs", android.content.Context.MODE_PRIVATE)
        binding.switchStockAlert.isChecked = sharedPref.getBoolean("stock_alert", true)
        binding.switchArrivalAlert.isChecked = sharedPref.getBoolean("arrival_alert", true)
        binding.switchNotifications.isChecked = sharedPref.getBoolean("general_notifications", true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
