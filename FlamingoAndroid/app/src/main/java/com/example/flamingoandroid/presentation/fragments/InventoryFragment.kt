package com.example.flamingoandroid.presentation.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.flamingoandroid.R
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.data.models.InventoryItem
import com.example.flamingoandroid.databinding.FragmentInventoryBinding
import com.example.flamingoandroid.presentation.util.bindInventoryLevel
import com.example.flamingoandroid.presentation.util.formatMoney
import com.example.flamingoandroid.presentation.util.initials
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class InventoryFragment : Fragment() {

    private val categories = listOf("Boissons", "Nourriture", "Glaces", "Produits nettoyage", "Autres")

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel = com.example.flamingoandroid.presentation.viewmodels.InventoryViewModel()
    private val firebaseService = FirebaseService()
    private var searchQuery: String = ""
    private var currentItems: List<InventoryItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddInventory.setOnClickListener {
            showAddItemDialog()
        }

        binding.etInventorySearch.doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString().orEmpty()
            renderItems()
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.isLoading.collectLatest { loading ->
                binding.pbInventoryLoading.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.errorMessage.collectLatest { error ->
                error?.let { binding.tvInventoryStats.text = it }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.inventoryItems.collectLatest { items ->
                currentItems = items
                renderItems()
            }
        }
    }

    private fun renderItems() {
        val filtered = currentItems.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }

        val lowStock = filtered.count { (it.stockQuantity.takeIf { q -> q > 0 } ?: it.quantity) <= (it.minStock.takeIf { m -> m > 0 } ?: it.minimumStock) }
        val totalValue = filtered.sumOf {
            val quantity = it.stockQuantity.takeIf { q -> q > 0 } ?: it.quantity
            val buyPrice = it.buyPrice.takeIf { p -> p > 0 } ?: it.unitPrice
            quantity * buyPrice
        }
        binding.tvInventoryStats.text = "${filtered.size} produits • $lowStock stock critique • ${formatMoney(totalValue)}"

        binding.tvInventoryEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.inventoryContainer.removeAllViews()

        filtered.forEach { item ->
            val card = layoutInflater.inflate(R.layout.item_inventory_admin, binding.inventoryContainer, false)
            bindItem(card, item)
            binding.inventoryContainer.addView(card)
        }
    }

    private fun bindItem(root: View, item: InventoryItem) {
        val avatar = root.findViewById<TextView>(R.id.tvInventoryAvatar)
        val nameView = root.findViewById<TextView>(R.id.tvInventoryName)
        val categoryView = root.findViewById<TextView>(R.id.tvInventoryCategory)
        val statusView = root.findViewById<TextView>(R.id.tvInventoryStatus)
        val pricesView = root.findViewById<TextView>(R.id.tvInventoryPrices)
        val plusButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnInventoryPlus)
        val minusButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnInventoryMinus)
        val sellButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnInventorySell)
        val deleteButton = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnInventoryDelete)

        val quantity = item.stockQuantity.takeIf { it > 0 } ?: item.quantity
        val minStock = item.minStock.takeIf { it > 0 } ?: item.minimumStock
        val buyPrice = item.buyPrice.takeIf { it > 0 } ?: item.unitPrice
        val sellPrice = item.sellPrice.takeIf { it > 0 } ?: item.unitPrice

        avatar.text = initials(item.name, "I")
        nameView.text = item.name.ifBlank { "Produit" }
        categoryView.text = item.category.ifBlank { "Catégorie" }
        statusView.bindInventoryLevel(requireContext(), quantity, minStock)
        pricesView.text = "Achat ${formatMoney(buyPrice)} • Vente ${formatMoney(sellPrice)}"

        plusButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                firebaseService.updateInventoryQuantity(item.id, quantity + 1)
                viewModel.loadInventory()
            }
        }

        minusButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                firebaseService.updateInventoryQuantity(item.id, (quantity - 1).coerceAtLeast(0))
                viewModel.loadInventory()
            }
        }

        sellButton.setOnClickListener {
            showSellDialog(item, quantity)
        }

        deleteButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Supprimer ce produit ?")
                .setMessage(item.name)
                .setPositiveButton("Supprimer") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        firebaseService.deleteInventoryItem(item.id)
                        viewModel.loadInventory()
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun showAddItemDialog() {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
        }
        val nameInput = EditText(context).apply { hint = "Nom du produit" }
        val categoryButton = MaterialButton(context).apply {
            text = "Catégorie: ${categories.first()}"
            isAllCaps = false
        }
        val quantityInput = EditText(context).apply {
            hint = "Quantité"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val minStockInput = EditText(context).apply {
            hint = "Stock minimum"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val buyPriceInput = EditText(context).apply {
            hint = "Prix achat"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val sellPriceInput = EditText(context).apply {
            hint = "Prix vente"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        listOf(nameInput, categoryButton, quantityInput, minStockInput, buyPriceInput, sellPriceInput)
            .forEach { container.addView(it) }

        var selectedCategory = categories.first()

        categoryButton.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("Choisir la catégorie")
                .setItems(categories.toTypedArray()) { _, which ->
                    selectedCategory = categories[which]
                    categoryButton.text = "Catégorie: $selectedCategory"
                }
                .show()
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Nouveau produit")
            .setView(container)
            .setPositiveButton("Enregistrer") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseService.addInventoryItem(
                        InventoryItem(
                            name = nameInput.text.toString().trim(),
                            category = selectedCategory,
                            stockQuantity = quantityInput.text.toString().toIntOrNull() ?: 0,
                            minStock = minStockInput.text.toString().toIntOrNull() ?: 0,
                            buyPrice = buyPriceInput.text.toString().toDoubleOrNull() ?: 0.0,
                            sellPrice = sellPriceInput.text.toString().toDoubleOrNull() ?: 0.0
                        )
                    )
                    viewModel.loadInventory()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showSellDialog(item: InventoryItem, availableQuantity: Int) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
        }
        val quantityInput = EditText(context).apply {
            hint = "Quantité à vendre"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        container.addView(quantityInput)

        MaterialAlertDialogBuilder(context)
            .setTitle("Vendre ${item.name}")
            .setMessage("Stock disponible: $availableQuantity")
            .setView(container)
            .setPositiveButton("Valider") { _, _ ->
                val quantity = quantityInput.text.toString().toIntOrNull() ?: 0
                if (quantity <= 0) {
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    firebaseService.addSale(item, quantity.coerceAtMost(availableQuantity))
                        .onSuccess {
                            viewModel.loadInventory()
                            Snackbar.make(binding.root, "Vente enregistrée", Snackbar.LENGTH_SHORT).show()
                        }
                        .onFailure { error ->
                            Snackbar.make(binding.root, error.message ?: "Erreur vente", Snackbar.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
