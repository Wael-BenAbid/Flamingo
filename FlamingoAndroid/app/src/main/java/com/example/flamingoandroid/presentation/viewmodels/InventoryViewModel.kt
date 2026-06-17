package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.models.InventoryItem
import com.example.flamingoandroid.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val inventoryRepo: InventoryRepository = InventoryRepository()
) : ViewModel() {

    private val _inventoryItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val inventoryItems: StateFlow<List<InventoryItem>> = _inventoryItems

    private val _lowStockItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val lowStockItems: StateFlow<List<InventoryItem>> = _lowStockItems

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        loadInventory()
    }

    fun loadInventory() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _inventoryItems.value = inventoryRepo.getInventoryItems()
                _lowStockItems.value  = inventoryRepo.getLowStockItems()
                _errorMessage.value   = null
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Erreur lors du chargement"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateItemQuantity(itemId: String, newQuantity: Int) {
        viewModelScope.launch {
            try {
                inventoryRepo.updateStock(itemId, newQuantity).onSuccess { loadInventory() }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Erreur lors de la mise à jour"
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
