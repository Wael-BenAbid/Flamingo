package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.models.Worker
import com.example.flamingoandroid.data.repository.WorkerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WorkersViewModel(
    private val workerRepo: WorkerRepository = WorkerRepository()
) : ViewModel() {

    private val _workers = MutableStateFlow<List<Worker>>(emptyList())
    val workers: StateFlow<List<Worker>> = _workers

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        // Listener temps-réel : la liste se met à jour automatiquement
        // dès qu'un travailleur est ajouté/modifié/supprimé (depuis n'importe quel appareil).
        viewModelScope.launch {
            try {
                workerRepo.observeWorkers().collect { list ->
                    _workers.value = list
                    _isLoading.value = false
                    _errorMessage.value = null
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Erreur lors du chargement"
            }
        }
    }

    // Conservé pour compatibilité avec les appels existants dans WorkersFragment.
    // Le listener temps-réel gère déjà les mises à jour, ce call force un refresh immédiat.
    fun loadWorkers() {
        viewModelScope.launch {
            try {
                val fresh = workerRepo.getWorkers()
                if (fresh.isNotEmpty()) {
                    _workers.value = fresh
                }
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Erreur lors du chargement"
            }
        }
    }

    fun addWorker(worker: Worker) {
        viewModelScope.launch {
            try {
                workerRepo.addWorker(worker)
                    .onSuccess { loadWorkers() }
                    .onFailure { _errorMessage.value = it.message ?: "Erreur lors de l'ajout" }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Erreur lors de l'ajout"
            }
        }
    }

    fun deleteWorker(workerId: String) {
        viewModelScope.launch {
            try {
                workerRepo.deleteWorker(workerId)
                    .onSuccess { loadWorkers() }
                    .onFailure { _errorMessage.value = it.message ?: "Erreur lors de la suppression" }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Erreur lors de la suppression"
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
