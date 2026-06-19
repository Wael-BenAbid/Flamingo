package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.firebase.MenuOrderingFirebaseService
import com.example.flamingoandroid.data.models.AppConfig
import com.example.flamingoandroid.data.models.MenuCategory
import com.example.flamingoandroid.data.models.MenuItem
import com.example.flamingoandroid.data.models.OrderCartLine
import com.example.flamingoandroid.data.models.TableOrder
import com.example.flamingoandroid.data.models.TableOrderItem
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MenuOrderingViewModel(
    private val service: MenuOrderingFirebaseService = MenuOrderingFirebaseService(),
) : ViewModel() {
    private val _appConfig = MutableStateFlow(AppConfig())
    val appConfig: StateFlow<AppConfig> = _appConfig.asStateFlow()

    private val _categories = MutableStateFlow<List<MenuCategory>>(emptyList())
    val categories: StateFlow<List<MenuCategory>> = _categories.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _activeOrders = MutableStateFlow<List<TableOrder>>(emptyList())
    val activeOrders: StateFlow<List<TableOrder>> = _activeOrders.asStateFlow()

    private val _cart = MutableStateFlow<List<OrderCartLine>>(emptyList())
    val cart: StateFlow<List<OrderCartLine>> = _cart.asStateFlow()

    private val _selectedTable = MutableStateFlow<String?>(null)
    val selectedTable: StateFlow<String?> = _selectedTable.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeOrderId = MutableStateFlow<String?>(null)
    val activeOrderId: StateFlow<String?> = _activeOrderId.asStateFlow()

    init {
        service.observeAppConfig().onEach { _appConfig.value = it }.launchIn(viewModelScope)
        service.observeMenuCategories().onEach { _categories.value = it }.launchIn(viewModelScope)
        service.observeMenuItems().onEach { _menuItems.value = it }.launchIn(viewModelScope)
        service.observeActivePlanOrders()
            .onEach { _activeOrders.value = it }
            .catch { error ->
                _errorMessage.value = error.message ?: "Impossible de charger les commandes actives"
            }
            .launchIn(viewModelScope)
    }

    fun selectTable(tableLabel: String) {
        _selectedTable.value = tableLabel
        val existingOrder = activeOrderForTable(tableLabel)
        if (existingOrder != null) {
            _activeOrderId.value = existingOrder.id
            _cart.value = existingOrder.items.map { item ->
                OrderCartLine(
                    item_id = item.item_id,
                    name = item.name,
                    quantity = item.quantity,
                    notes = item.notes,
                    unit_price = item.unit_price,
                )
            }
        } else {
            _activeOrderId.value = null
            _cart.value = emptyList()
        }
    }

    fun clearSelection() {
        _selectedTable.value = null
        _cart.value = emptyList()
        _activeOrderId.value = null
    }

    fun addItem(menuItem: MenuItem) {
        if (!menuItem.is_available) return

        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.item_id == menuItem.id }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            current.add(
                OrderCartLine(
                    item_id = menuItem.id,
                    name = menuItem.name,
                    quantity = 1,
                    notes = "",
                    unit_price = menuItem.price,
                )
            )
        }
        _cart.value = current
    }

    fun updateQuantity(itemId: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(itemId)
            return
        }

        _cart.value = _cart.value.map { line ->
            if (line.item_id == itemId) line.copy(quantity = quantity) else line
        }
    }

    fun updateNotes(itemId: String, notes: String) {
        _cart.value = _cart.value.map { line ->
            if (line.item_id == itemId) line.copy(notes = notes) else line
        }
    }

    fun removeItem(itemId: String) {
        _cart.value = _cart.value.filterNot { it.item_id == itemId }
    }

    fun cartTotal(): Double = _cart.value.sumOf { it.unit_price * it.quantity }

    fun saveTotalTablesCount(totalTablesCount: Int) {
        viewModelScope.launch {
            service.updateAppConfig(totalTablesCount).onFailure {
                _errorMessage.value = it.message ?: "Erreur lors de la configuration des tables"
            }
        }
    }

    fun sendOrder(serverId: String, serverName: String) {
        val tableLabel = _selectedTable.value ?: run {
            _errorMessage.value = "Veuillez selectionner une table"
            return
        }

        val cartSnapshot = _cart.value
        if (cartSnapshot.isEmpty()) {
            _errorMessage.value = "Le panier est vide"
            return
        }

        val existingOrderId = _activeOrderId.value

        viewModelScope.launch {
            _isSubmitting.value = true
            val items = cartSnapshot.map {
                TableOrderItem(
                    item_id = it.item_id,
                    name = it.name,
                    quantity = it.quantity,
                    notes = it.notes,
                    unit_price = it.unit_price,
                )
            }

            if (existingOrderId != null) {
                service.updateTableOrder(
                    orderId = existingOrderId,
                    items = items,
                    totalPrice = cartTotal(),
                    serverId = serverId,
                    serverName = serverName,
                ).onSuccess {
                    _cart.value = emptyList()
                    _activeOrderId.value = null
                    _selectedTable.value = null
                    _errorMessage.value = null
                }.onFailure { error ->
                    _errorMessage.value = error.message ?: "Erreur lors de la mise à jour de la commande"
                }
            } else {
                service.createTableOrder(
                    tableNumber = tableLabel,
                    serverId = serverId,
                    serverName = serverName,
                    items = items,
                    totalPrice = cartTotal(),
                ).onSuccess {
                    _cart.value = emptyList()
                    _selectedTable.value = null
                    _errorMessage.value = null
                }.onFailure { error ->
                    _errorMessage.value = error.message ?: "Erreur lors de l'envoi de la commande"
                }
            }
            _isSubmitting.value = false
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun tableHasActiveOrder(tableLabel: String): Boolean {
        return _activeOrders.value.any { order ->
            order.table_number == tableLabel && order.status != "paid"
        }
    }

    fun activeOrderForTable(tableLabel: String): TableOrder? {
        return _activeOrders.value.firstOrNull { order ->
            order.table_number == tableLabel && order.status != "paid"
        }
    }
}
