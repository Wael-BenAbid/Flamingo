package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.firebase.MenuOrderingFirebaseService
import com.example.flamingoandroid.data.models.MenuCategory
import com.example.flamingoandroid.data.models.MenuItem
import com.example.flamingoandroid.data.models.TableOrder
import com.example.flamingoandroid.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class KitchenDashboardViewModel(
    private val service: MenuOrderingFirebaseService = MenuOrderingFirebaseService(),
    private val authRepo: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _orders             = MutableStateFlow<List<TableOrder>>(emptyList())
    private val _menuCategories     = MutableStateFlow<List<MenuCategory>>(emptyList())
    private val _menuItems          = MutableStateFlow<List<MenuItem>>(emptyList())
    private val _userRole           = MutableStateFlow<String?>(null)
    private val _currentMinuteOfDay = MutableStateFlow(minuteOfDay())

    private fun minuteOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private val _isLoading    = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val pageTitle: StateFlow<String> = _userRole
        .map { role ->
            when (role) {
                "cuisinier" -> "Commandes Cuisine"
                "barman"    -> "Commandes Bar"
                else        -> "Commandes Cuisine & Bar"
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Commandes Cuisine & Bar")

    /** Orders filtered by scheduled time + role, always sorted by original creation time. */
    val filteredOrders: StateFlow<List<TableOrder>> = combine(
        _orders, _userRole, _menuCategories, _menuItems, _currentMinuteOfDay,
    ) { orders: List<TableOrder>, role: String?, categories: List<MenuCategory>, items: List<MenuItem>, currentMinute: Int ->
        // 1. Filter out orders whose scheduled service time hasn't arrived yet
        val timeVisible = orders.filter { order ->
            val t = order.scheduled_time
            if (t.isNullOrBlank()) true
            else {
                val parts = t.split(":")
                if (parts.size != 2) true
                else {
                    val h = parts[0].toIntOrNull() ?: 0
                    val m = parts[1].toIntOrNull() ?: 0
                    h * 60 + m <= currentMinute
                }
            }
        }
        // 2. Role-based item filtering
        val categoryRoles = categories.associate { it.id to it.target_role }
        val lookup = items.associate { it.id to categoryRoles[it.category_id] }
        val roleFiltered = when (role) {
            "cuisinier", "barman" -> timeVisible
                .map { order ->
                    order.copy(
                        items = order.items.filter { item ->
                            val itemRole = lookup[item.item_id]
                            itemRole == null || itemRole == role
                        },
                    )
                }
                .filter { it.items.isNotEmpty() }
            else -> timeVisible
        }
        // 3. Always sort by original creation time so modifications never change position
        roleFiltered.sortedWith(
            compareBy<TableOrder> { it.created_at?.toDate()?.time ?: Long.MAX_VALUE }
                .thenBy { it.id }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Sorted list of (name, total quantity) across all active filtered orders. */
    val itemTotals: StateFlow<List<Pair<String, Int>>> = filteredOrders
        .map { orders ->
            val totals = linkedMapOf<String, Pair<String, Int>>()
            for (order in orders) {
                for (item in order.items) {
                    if (item.name.isBlank()) continue
                    val key = item.item_id.ifBlank { item.name }
                    val prev = totals[key]?.second ?: 0
                    totals[key] = item.name to (prev + item.quantity)
                }
            }
            totals.values.sortedByDescending { it.second }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Refresh current minute every 60 s so scheduled orders appear on time
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                _currentMinuteOfDay.value = minuteOfDay()
            }
        }

        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            _userRole.value = authRepo.getCurrentUserRole(user)
        }

        service.observeKitchenOrders()
            .onEach { _orders.value = it; _isLoading.value = false }
            .catch { error ->
                _isLoading.value = false
                _errorMessage.value = error.message ?: "Impossible de charger les commandes cuisine"
            }
            .launchIn(viewModelScope)

        service.observeMenuCategories()
            .onEach { _menuCategories.value = it }
            .catch { }
            .launchIn(viewModelScope)

        service.observeMenuItems()
            .onEach { _menuItems.value = it }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun startPreparation(orderId: String) { setStatus(orderId, "preparing") }

    fun markReady(orderId: String) { setStatus(orderId, "ready") }

    fun setStatus(orderId: String, status: String) {
        viewModelScope.launch {
            service.updateOrderStatus(orderId, status).onFailure {
                _errorMessage.value = it.message ?: "Erreur lors de la mise a jour du statut"
            }
        }
    }

    fun dismissError() { _errorMessage.value = null }
}
