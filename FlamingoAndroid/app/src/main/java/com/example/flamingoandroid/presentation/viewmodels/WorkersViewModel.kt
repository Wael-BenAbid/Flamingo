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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        loadWorkers()
    }

    fun loadWorkers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _workers.value = workerRepo.getWorkers()
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Erreur lors du chargement"
            } finally {
                _isLoading.value = false
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
