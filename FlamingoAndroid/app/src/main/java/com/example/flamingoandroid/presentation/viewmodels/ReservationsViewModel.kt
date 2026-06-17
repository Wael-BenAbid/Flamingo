package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.example.flamingoandroid.data.repository.ReservationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ReservationsViewModel — driven by ReservationRepository Snapshot Listeners.
 *
 * Key changes from the original FirebaseService-based version:
 *  • One-time loads replaced by real-time Flows → UI always in sync with Firestore
 *  • No manual refresh needed after mutations — the Snapshot Listener emits automatically
 *  • Computed helpers (occupiedNumbers / availableNumbers) exposed here so fragments
 *    don't duplicate business logic
 */
class ReservationsViewModel : ViewModel() {

    private val repository = ReservationRepository()

    // ── Published state ───────────────────────────────────────────────

    private val _reservations  = MutableStateFlow<List<Reservation>>(emptyList())
    val reservations: StateFlow<List<Reservation>> = _reservations.asStateFlow()

    private val _positions     = MutableStateFlow<List<Position>>(emptyList())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    private val _isLoading     = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage  = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Init — start real-time Snapshot Listeners ─────────────────────

    init {
        viewModelScope.launch {
            combine(
                repository.observeReservations(),
                repository.observePositions(),
            ) { res, pos -> Pair(res, pos) }
            .catch { e -> _errorMessage.value = e.localizedMessage }
            .collect { (res, pos) ->
                _reservations.value = res
                _positions.value    = pos
                _isLoading.value    = false
            }
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────

    fun addReservation(reservation: Reservation, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.addReservation(reservation)) }
    }

    fun updateReservation(id: String, reservation: Reservation, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.updateReservation(id, reservation)) }
    }

    fun updateReservationStatus(id: String, status: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.updateReservationStatus(id, status)) }
    }

    fun deleteReservation(id: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.deleteReservation(id)) }
    }

    // ── Computed helpers ───────────────────────────────────────────────

    /** Position numbers already booked on [date] for [positionType]. */
    fun occupiedNumbers(date: String, positionType: String, excludeId: String? = null): List<String> =
        _reservations.value
            .filter { r -> r.date == date && r.positionType == positionType }
            .filter { r -> excludeId == null || r.id != excludeId }
            .filter { r -> r.status !in listOf("cancelled", "absent") }
            .mapNotNull { r -> r.positionNumber?.trim()?.takeIf { it.isNotBlank() } }
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

    /** Position numbers still available for a given date and type. */
    fun availableNumbers(date: String, positionType: String, excludeId: String? = null): List<String> {
        val total    = _positions.value.find { it.type == positionType }?.count ?: 0
        val occupied = occupiedNumbers(date, positionType, excludeId).toSet()
        return (1..total).map(Int::toString).filter { it !in occupied }
    }

    fun dismissError() { _errorMessage.value = null }
}
