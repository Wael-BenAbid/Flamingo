package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.example.flamingoandroid.data.models.TableOrder
import com.example.flamingoandroid.data.repository.ReservationRepository
import com.example.flamingoandroid.data.repository.TableRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface TablesUiState {
    data object Loading : TablesUiState

    data class Success(
        val positions: List<Position>,
        val activeOrders: Map<String, String>,      // tableLabel -> status
        val clientsPerTable: Map<String, Pair<Int, Int>>, // tableLabel -> (adults, children)
    ) : TablesUiState
}

class TablesViewModel(
    private val repository: TableRepository = TableRepository(),
    private val reservationRepo: ReservationRepository = ReservationRepository(),
) : ViewModel() {

    val uiState: StateFlow<TablesUiState> = combine(
        repository.listenToPositions(),
        repository.listenToActiveOrders(),
        reservationRepo.observeTodayAllArrivals(),
    ) { positions: List<Position>, orders: List<TableOrder>, arrivals: List<Reservation> ->
        val sortedPositions = positions.sortedWith(
            compareBy<Position> { it.type.trim().lowercase() }.thenBy { it.id }
        )
        val activeOrders = orders.associate { it.table_number to it.status }

        // Map each confirmed arrival to its table label (e.g. "Terrasse 1")
        val clientsPerTable = arrivals
            .filter {
                it.status.equals("confirmed", ignoreCase = true) &&
                it.positionType.isNotBlank() &&
                !it.positionNumber.isNullOrBlank()
            }
            .associate { r ->
                "${r.positionType.trim()} ${r.positionNumber!!.trim()}" to Pair(r.adults, r.children)
            }

        TablesUiState.Success(
            positions = sortedPositions,
            activeOrders = activeOrders,
            clientsPerTable = clientsPerTable,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TablesUiState.Loading,
    )
}
