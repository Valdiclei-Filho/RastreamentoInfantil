package com.example.rastreamentoinfantil.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.repository.FirebaseRepository // Supondo que FirebaseRepository lida com o salvamento da Geofence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GeofenceViewModel(
    private val firebaseRepository: FirebaseRepository // Injete seu repositório
) : ViewModel() {

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun saveGeofence(geofence: Geofence) {
        _isLoading.value = true
        _error.value = null // Limpar erros anteriores

        viewModelScope.launch {
            // Supondo que FirebaseRepository tenha uma função para salvar Geofence
            val userId = firebaseRepository.getCurrentUser()?.uid
            if (userId != null) {
                firebaseRepository.saveGeofence(geofence, userId) { success ->
                    _isLoading.value = false
                    if (success) {
                        _saveSuccess.value = true
                    } else {
                        _error.value = "Falha ao salvar geofence."
                    }
                }
            } else {
                _isLoading.value = false
                _error.value = "Usuário não logado."
            }
        }
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun setError(errorMessage: String) {
        _error.value = errorMessage
    }
}