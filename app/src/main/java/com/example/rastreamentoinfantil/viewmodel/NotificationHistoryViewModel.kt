package com.example.rastreamentoinfantil.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rastreamentoinfantil.model.NotificationHistoryEntry
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NotificationHistoryViewModel(
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _allNotifications = MutableStateFlow<List<NotificationHistoryEntry>>(emptyList())
    private val _filteredNotifications = MutableStateFlow<List<NotificationHistoryEntry>>(emptyList())
    val notifications: StateFlow<List<NotificationHistoryEntry>> = _filteredNotifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Filtros
    private val _selectedDependent = MutableStateFlow<String?>(null)
    val selectedDependent: StateFlow<String?> = _selectedDependent.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _selectedEventType = MutableStateFlow<String?>(null)
    val selectedEventType: StateFlow<String?> = _selectedEventType.asStateFlow()

    private val _isResponsible = MutableStateFlow(false)
    val isResponsible: StateFlow<Boolean> = _isResponsible.asStateFlow()

    // Lista de dependentes disponíveis (para responsáveis)
    private val _dependents = MutableStateFlow<List<String>>(emptyList())
    val dependents: StateFlow<List<String>> = _dependents.asStateFlow()

    // Lista de tipos de eventos disponíveis
    private val _eventTypes = MutableStateFlow<List<String>>(emptyList())
    val eventTypes: StateFlow<List<String>> = _eventTypes.asStateFlow()

    init {
        // Combina os filtros para aplicar automaticamente
        viewModelScope.launch {
            combine(
                _allNotifications,
                _selectedDependent,
                _selectedDate,
                _selectedEventType
            ) { notifications, dependent, date, eventType ->
                applyFilters(notifications, dependent, date, eventType)
            }.collect { filtered ->
                _filteredNotifications.value = filtered
            }
        }
    }

    fun loadNotificationHistory(userId: String, isResponsible: Boolean = false) {
        if (userId.isEmpty()) {
            _error.value = "ID do usuário não fornecido"
            return
        }

        _isResponsible.value = isResponsible

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            firebaseRepository.getNotificationHistory(userId) { notifications, exception ->
                _isLoading.value = false
                
                if (exception != null) {
                    _error.value = "Erro ao carregar histórico: ${exception.message}"
                } else {
                    val notificationList = notifications ?: emptyList()
                    _allNotifications.value = notificationList
                    
                    // Extrai dependentes únicos (para responsáveis)
                    if (isResponsible) {
                        val uniqueDependents = notificationList
                            .mapNotNull { it.childName }
                            .distinct()
                            .sorted()
                        _dependents.value = uniqueDependents
                    }
                    
                    // Extrai tipos de eventos únicos
                    val uniqueEventTypes = notificationList
                        .mapNotNull { it.tipoEvento }
                        .distinct()
                        .sorted()
                        .map { getEventTypeDisplayName(it) }
                    _eventTypes.value = uniqueEventTypes
                }
            }
        }
    }

    fun setDependentFilter(dependent: String?) {
        _selectedDependent.value = dependent
    }

    fun setDateFilter(date: String?) {
        _selectedDate.value = date
    }

    fun setEventTypeFilter(eventType: String?) {
        _selectedEventType.value = eventType
    }

    fun clearFilters() {
        _selectedDependent.value = null
        _selectedDate.value = null
        _selectedEventType.value = null
    }

    private fun applyFilters(
        notifications: List<NotificationHistoryEntry>,
        dependent: String?,
        date: String?,
        eventType: String?
    ): List<NotificationHistoryEntry> {
        return notifications.filter { notification ->
            var matches = true
            
            // Filtro por dependente
            if (dependent != null && dependent.isNotEmpty()) {
                matches = matches && notification.childName == dependent
            }
            
            // Filtro por data
            if (date != null && date.isNotEmpty()) {
                matches = matches && notification.horarioEvento?.startsWith(date) == true
            }
            
            // Filtro por tipo de evento
            if (eventType != null && eventType.isNotEmpty()) {
                val displayName = getEventTypeDisplayName(notification.tipoEvento)
                matches = matches && displayName == eventType
            }
            
            matches
        }.sortedByDescending { it.contagemTempo }
    }

    private fun getEventTypeDisplayName(eventType: String?): String {
        return when (eventType) {
            "saida_geofence" -> "Saída de Área Segura"
            "volta_geofence" -> "Retorno à Área Segura"
            "saida_rota" -> "Saída de Rota"
            "volta_rota" -> "Retorno à Rota"
            "desvio_rota" -> "Desvio de Rota"
            else -> eventType ?: "Desconhecido"
        }
    }

    fun markAsRead(notificationId: String, userId: String) {
        // TODO: Implementar marcação como lida
        // firebaseRepository.markNotificationAsRead(userId, notificationId)
    }

    fun clearError() {
        _error.value = null
    }

    fun refresh(userId: String) {
        loadNotificationHistory(userId, _isResponsible.value)
    }

    fun getNotificationStats(): Map<String, Int> {
        val notifications = _allNotifications.value
        return mapOf(
            "total" to notifications.size,
            "nao_lidas" to notifications.count { !it.lida },
            "saidas_geofence" to notifications.count { it.tipoEvento == "saida_geofence" },
            "voltas_geofence" to notifications.count { it.tipoEvento == "volta_geofence" },
            "saidas_rota" to notifications.count { it.tipoEvento == "saida_rota" },
            "voltas_rota" to notifications.count { it.tipoEvento == "volta_rota" }
        )
    }
} 