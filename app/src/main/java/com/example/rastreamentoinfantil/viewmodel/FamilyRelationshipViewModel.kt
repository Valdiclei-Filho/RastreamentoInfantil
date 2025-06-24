package com.example.rastreamentoinfantil.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rastreamentoinfantil.model.FamilyRelationship
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FamilyRelationshipViewModel(
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _dependents = MutableStateFlow<List<FamilyRelationship>>(emptyList())
    val dependents: StateFlow<List<FamilyRelationship>> = _dependents.asStateFlow()

    private val _responsible = MutableStateFlow<FamilyRelationship?>(null)
    val responsible: StateFlow<FamilyRelationship?> = _responsible.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadDependentsForResponsible(responsibleId: String) {
        if (responsibleId.isEmpty()) {
            _error.value = "ID do responsável não fornecido"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            firebaseRepository.getDependentsForResponsible(responsibleId) { dependents, exception ->
                _isLoading.value = false
                
                if (exception != null) {
                    _error.value = "Erro ao carregar dependentes: ${exception.message}"
                } else {
                    _dependents.value = dependents ?: emptyList()
                }
            }
        }
    }

    fun loadResponsibleForDependent(dependentId: String) {
        if (dependentId.isEmpty()) {
            _error.value = "ID do dependente não fornecido"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            firebaseRepository.getResponsibleForDependent(dependentId) { responsible, exception ->
                _isLoading.value = false
                
                if (exception != null) {
                    _error.value = "Erro ao carregar responsável: ${exception.message}"
                } else {
                    _responsible.value = responsible
                }
            }
        }
    }

    fun createFamilyRelationship(relationship: FamilyRelationship) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            firebaseRepository.saveFamilyRelationship(relationship) { success, exception ->
                _isLoading.value = false
                
                if (!success) {
                    _error.value = "Erro ao criar relacionamento: ${exception?.message}"
                } else {
                    // Recarrega a lista de dependentes se for um responsável
                    if (relationship.responsibleId.isNotEmpty()) {
                        loadDependentsForResponsible(relationship.responsibleId)
                    }
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun refreshDependents(responsibleId: String) {
        loadDependentsForResponsible(responsibleId)
    }
} 