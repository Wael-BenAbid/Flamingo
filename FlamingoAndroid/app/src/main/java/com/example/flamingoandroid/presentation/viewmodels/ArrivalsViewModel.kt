package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.example.flamingoandroid.data.repository.ReservationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ArrivalsViewModel(
    private val reservationRepo: ReservationRepository = ReservationRepository()
) : ViewModel() {

    private val _pendingArrivals = MutableStateFlow<List<Reservation>>(emptyList())
    val pendingArrivals: StateFlow<List<Reservation>> = _pendingArrivals

    private val _confirmedArrivals = MutableStateFlow<List<Reservation>>(emptyList())
    val confirmedArrivals: StateFlow<List<Reservation>> = _confirmedArrivals

    private val _cancelledArrivals = MutableStateFlow<List<Reservation>>(emptyList())
    val cancelledArrivals: StateFlow<List<Reservation>> = _cancelledArrivals

    private val _absentArrivals = MutableStateFlow<List<Reservation>>(emptyList())
    val absentArrivals: StateFlow<List<Reservation>> = _absentArrivals

    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    val positions: StateFlow<List<Position>> = _positions

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        observeArrivals()
        observePositions()
    }

    private fun observeArrivals() {
        viewModelScope.launch {
            reservationRepo.observeTodayAllArrivals().collect { all ->
                _isLoading.value = false
                _pendingArrivals.value = all
                    .filter { it.status.equals("pending", ignoreCase = true) }
                    .sortedBy { it.time }
                _confirmedArrivals.value = all
                    .filter { it.status.equals("confirmed", ignoreCase = true) }
                    .sortedWith(
                        compareBy<Reservation> { it.positionType }
                            .thenBy { it.positionNumber?.toIntOrNull() ?: Int.MAX_VALUE }
                            .thenBy { it.positionNumber ?: "" }
                    )
                _cancelledArrivals.value = all
                    .filter { it.status.equals("cancelled", ignoreCase = true) }
                    .sortedBy { it.time }
                _absentArrivals.value = all
                    .filter { it.status.equals("absent", ignoreCase = true) }
                    .sortedBy { it.time }
            }
        }
    }

    private fun observePositions() {
        viewModelScope.launch {
            reservationRepo.observePositions().collect { list ->
                _positions.value = list.sortedBy { it.type }
            }
        }
    }

    fun confirmArrival(
        reservationId: String,
        positionType: String,
        positionNumber: String,
        adults: Int,
        children: Int,
    ) {
        viewModelScope.launch {
            reservationRepo.confirmArrival(reservationId, positionType, positionNumber, adults, children)
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun markAbsent(reservationId: String) {
        viewModelScope.launch {
            reservationRepo.markArrivalAbsent(reservationId)
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun cancelArrival(reservationId: String) {
        viewModelScope.launch {
            reservationRepo.updateReservationStatus(reservationId, "cancelled")
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun revertToPending(reservationId: String) {
        viewModelScope.launch {
            reservationRepo.updateReservationStatus(reservationId, "pending")
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun dismissError() { _errorMessage.value = null }

    // Legacy compat — kept so existing call-sites compile during transition
    @Deprecated("Use confirmArrival(id, positionType, positionNumber)")
    fun loadArrivals() { /* no-op: now driven by real-time flow */ }
}
