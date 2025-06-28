package com.example.rastreamentoinfantil.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rastreamentoinfantil.model.NotificationHistoryEntry
import com.example.rastreamentoinfantil.model.User
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

    // Filtros melhorados
    private val _selectedDependent = MutableStateFlow<String?>(null)
    val selectedDependent: StateFlow<String?> = _selectedDependent.asStateFlow()

    private val _selectedDateFilter = MutableStateFlow<String?>(null)
    val selectedDateFilter: StateFlow<String?> = _selectedDateFilter.asStateFlow()

    private val _selectedEventType = MutableStateFlow<String?>(null)
    val selectedEventType: StateFlow<String?> = _selectedEventType.asStateFlow()

    private val _selectedReadStatus = MutableStateFlow<String?>(null)
    val selectedReadStatus: StateFlow<String?> = _selectedReadStatus.asStateFlow()

    private val _isResponsible = MutableStateFlow(false)
    val isResponsible: StateFlow<Boolean> = _isResponsible.asStateFlow()

    // Lista de dependentes disponíveis (para responsáveis)
    private val _dependents = MutableStateFlow<List<String>>(emptyList())
    val dependents: StateFlow<List<String>> = _dependents.asStateFlow()

    // Lista de tipos de eventos disponíveis
    private val _eventTypes = MutableStateFlow<List<String>>(emptyList())
    val eventTypes: StateFlow<List<String>> = _eventTypes.asStateFlow()

    // Lista de opções de data disponíveis
    private val _dateFilterOptions = MutableStateFlow<List<String>>(emptyList())
    val dateFilterOptions: StateFlow<List<String>> = _dateFilterOptions.asStateFlow()

    // Lista de opções de status de leitura
    private val _readStatusOptions = MutableStateFlow<List<String>>(emptyList())
    val readStatusOptions: StateFlow<List<String>> = _readStatusOptions.asStateFlow()

    init {
        // Inicializar opções de filtro
        _dateFilterOptions.value = listOf(
            "hoje",
            "ontem", 
            "ultima_semana",
            "ultimo_mes",
            "ultima_hora",
            "ultimas_24h"
        )
        
        _readStatusOptions.value = listOf(
            "todas",
            "nao_lidas",
            "lidas"
        )
        
        // Inicializar tipos de eventos com todos os tipos possíveis
        _eventTypes.value = listOf(
            "Saída de Área Segura",
            "Retorno à Área Segura", 
            "Saída de Rota",
            "Retorno à Rota",
            "Desvio de Rota"
        )
        
        // Combina os filtros para aplicar automaticamente
        viewModelScope.launch {
            combine(
                _allNotifications,
                _selectedDependent,
                _selectedDateFilter,
                _selectedEventType,
                _selectedReadStatus
            ) { notifications, dependent, dateFilter, eventType, readStatus ->
                applyFilters(notifications, dependent, dateFilter, eventType, readStatus)
            }.collect { filtered ->
                _filteredNotifications.value = filtered
            }
        }
    }

    fun loadNotificationHistory(userId: String, isResponsible: Boolean = false, familyId: String? = null) {
        if (userId.isEmpty()) {
            _error.value = "ID do usuário não fornecido"
            return
        }

        Log.d("NotificationHistoryViewModel", "loadNotificationHistory: userId=$userId, isResponsible=$isResponsible, familyId=$familyId")
        _isResponsible.value = isResponsible

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            if (isResponsible) {
                // Para responsáveis: buscar notificações de todos os dependentes da família
                loadNotificationsForResponsible(userId, familyId)
            } else {
                // Para dependentes: buscar apenas suas próprias notificações
                loadNotificationsForDependent(userId)
            }
        }
    }

    private fun loadNotificationsForDependent(userId: String) {
        firebaseRepository.getNotificationHistory(userId) { notifications, exception ->
            _isLoading.value = false
            if (exception != null) {
                _error.value = "Erro ao carregar histórico: ${exception.message}"
                Log.e("NotificationHistoryViewModel", "Erro ao carregar histórico", exception)
            } else {
                val notificationList = notifications ?: emptyList()
                Log.d("NotificationHistoryViewModel", "Notificações carregadas para dependente: ${notificationList.size}")
                _allNotifications.value = notificationList
            }
        }
    }

    private fun loadNotificationsForResponsible(userId: String, familyId: String?) {
        Log.d("NotificationHistoryViewModel", "Carregando notificações para responsável: $userId")
        
        // Primeiro, buscar o usuário responsável para obter o familyId
        firebaseRepository.getUserById(userId) { user, error ->
            if (error != null) {
                _isLoading.value = false
                _error.value = "Erro ao buscar usuário: ${error.message}"
                Log.e("NotificationHistoryViewModel", "Erro ao buscar usuário responsável", error)
                return@getUserById
            }

            if (user == null || user.familyId.isNullOrEmpty()) {
                _isLoading.value = false
                _error.value = "Usuário não pertence a nenhuma família"
                Log.w("NotificationHistoryViewModel", "Responsável não tem família")
                return@getUserById
            }

            val currentFamilyId = familyId ?: user.familyId
            Log.d("NotificationHistoryViewModel", "Buscando membros da família: $currentFamilyId")

            // Buscar todos os membros da família (mesma lógica da tela de família)
            firebaseRepository.getFamilyDetails(currentFamilyId) { family, members, familyError ->
                if (familyError != null) {
                    _isLoading.value = false
                    _error.value = "Erro ao carregar família: ${familyError.message}"
                    Log.e("NotificationHistoryViewModel", "Erro ao carregar família", familyError)
                    return@getFamilyDetails
                }

                if (family == null || members == null) {
                    _isLoading.value = false
                    _error.value = "Família não encontrada"
                    Log.w("NotificationHistoryViewModel", "Família não encontrada")
                    return@getFamilyDetails
                }

                // Filtrar apenas dependentes (tipo "membro")
                val dependents = members.filter { it.type == "membro" }
                Log.d("NotificationHistoryViewModel", "Dependentes encontrados: ${dependents.size}")

                if (dependents.isEmpty()) {
                    _isLoading.value = false
                    _allNotifications.value = emptyList()
                    _dependents.value = emptyList()
                    Log.d("NotificationHistoryViewModel", "Nenhum dependente encontrado")
                    return@getFamilyDetails
                }

                // Buscar notificações de todos os dependentes
                loadNotificationsFromAllDependents(dependents)
            }
        }
    }

    private fun loadNotificationsFromAllDependents(dependents: List<User>) {
        Log.d("NotificationHistoryViewModel", "Buscando notificações de ${dependents.size} dependentes")
        
        val allNotifications = mutableListOf<NotificationHistoryEntry>()
        var completedRequests = 0
        val totalRequests = dependents.size

        // Lista de nomes dos dependentes para o filtro
        val dependentNames = dependents.mapNotNull { it.name }.distinct().sorted()
        _dependents.value = dependentNames

        for (dependent in dependents) {
            val dependentId = dependent.id
            if (dependentId.isNullOrEmpty()) {
                completedRequests++
                if (completedRequests == totalRequests) {
                    finishLoadingNotifications(allNotifications)
                }
                continue
            }

            firebaseRepository.getNotificationHistory(dependentId) { notifications, exception ->
                completedRequests++
                
                if (exception != null) {
                    Log.e("NotificationHistoryViewModel", "Erro ao buscar notificações do dependente ${dependent.name}", exception)
                } else {
                    val dependentNotifications = notifications ?: emptyList()
                    Log.d("NotificationHistoryViewModel", "Notificações do dependente ${dependent.name}: ${dependentNotifications.size}")
                    
                    // Adicionar informações do dependente às notificações
                    val notificationsWithDependentInfo = dependentNotifications.map { notification ->
                        notification.copy(
                            childId = dependentId,
                            childName = dependent.name ?: "Dependente"
                        )
                    }
                    
                    allNotifications.addAll(notificationsWithDependentInfo)
                }

                if (completedRequests == totalRequests) {
                    finishLoadingNotifications(allNotifications)
                }
            }
        }
    }

    private fun finishLoadingNotifications(allNotifications: List<NotificationHistoryEntry>) {
        _isLoading.value = false
        
        // Ordenar por data (mais recentes primeiro)
        val sortedNotifications = allNotifications.sortedByDescending { it.contagemTempo }
        
        Log.d("NotificationHistoryViewModel", "Total de notificações carregadas: ${sortedNotifications.size}")
        _allNotifications.value = sortedNotifications
    }

    fun setDependentFilter(dependent: String?) {
        _selectedDependent.value = dependent
    }

    fun setDateFilter(dateFilter: String?) {
        _selectedDateFilter.value = dateFilter
    }

    fun setEventTypeFilter(eventType: String?) {
        _selectedEventType.value = eventType
    }

    fun setReadStatusFilter(readStatus: String?) {
        _selectedReadStatus.value = readStatus
    }

    fun clearFilters() {
        _selectedDependent.value = null
        _selectedDateFilter.value = null
        _selectedEventType.value = null
        _selectedReadStatus.value = null
    }

    private fun applyFilters(
        notifications: List<NotificationHistoryEntry>,
        dependent: String?,
        dateFilter: String?,
        eventType: String?,
        readStatus: String?
    ): List<NotificationHistoryEntry> {
        return notifications.filter { notification ->
            var matches = true
            
            // Filtro por dependente
            if (dependent != null && dependent.isNotEmpty()) {
                matches = matches && notification.childName == dependent
            }
            
            // Filtro por data
            if (dateFilter != null && dateFilter.isNotEmpty()) {
                matches = matches && isNotificationInDateRange(notification, dateFilter)
            }
            
            // Filtro por tipo de evento
            if (eventType != null && eventType.isNotEmpty()) {
                val displayName = getEventTypeDisplayName(notification.tipoEvento)
                matches = matches && displayName == eventType
            }
            
            // Filtro por status de leitura
            if (readStatus != null && readStatus.isNotEmpty()) {
                matches = matches && when (readStatus) {
                    "nao_lidas" -> !notification.lida
                    "lidas" -> notification.lida
                    else -> true // "todas"
                }
            }
            
            matches
        }.sortedByDescending { it.contagemTempo }
    }

    private fun isNotificationInDateRange(notification: NotificationHistoryEntry, dateFilter: String): Boolean {
        val calendar = Calendar.getInstance()
        val now = Date()
        
        return when (dateFilter) {
            "hoje" -> {
                val today = Calendar.getInstance()
                val notificationDate = parseNotificationDate(notification.horarioEvento)
                notificationDate?.let { date ->
                    val notifCalendar = Calendar.getInstance().apply { time = date }
                    notifCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    notifCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                } ?: false
            }
            "ontem" -> {
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                val notificationDate = parseNotificationDate(notification.horarioEvento)
                notificationDate?.let { date ->
                    val notifCalendar = Calendar.getInstance().apply { time = date }
                    notifCalendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                    notifCalendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
                } ?: false
            }
            "ultima_semana" -> {
                val weekAgo = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -1) }
                val notificationDate = parseNotificationDate(notification.horarioEvento)
                notificationDate?.after(weekAgo.time) ?: false
            }
            "ultimo_mes" -> {
                val monthAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                val notificationDate = parseNotificationDate(notification.horarioEvento)
                notificationDate?.after(monthAgo.time) ?: false
            }
            "ultima_hora" -> {
                val hourAgo = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -1) }
                val notificationDate = parseNotificationDate(notification.horarioEvento)
                notificationDate?.after(hourAgo.time) ?: false
            }
            "ultimas_24h" -> {
                val dayAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                val notificationDate = parseNotificationDate(notification.horarioEvento)
                notificationDate?.after(dayAgo.time) ?: false
            }
            else -> true
        }
    }

    private fun parseNotificationDate(dateString: String?): Date? {
        if (dateString.isNullOrEmpty()) return null
        
        return try {
            // Tenta diferentes formatos de data
            val formats = listOf(
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "yyyy-MM-dd HH:mm:ss"
            )
            
            for (format in formats) {
                try {
                    val dateFormat = SimpleDateFormat(format, Locale("pt", "BR"))
                    return dateFormat.parse(dateString)
                } catch (e: Exception) {
                    // Continua para o próximo formato
                }
            }
            null
        } catch (e: Exception) {
            null
        }
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
        if (userId.isEmpty() || notificationId.isEmpty()) {
            _error.value = "ID do usuário ou da notificação não fornecido"
            return
        }

        viewModelScope.launch {
            firebaseRepository.markNotificationAsRead(userId, notificationId) { success, exception ->
                if (success) {
                    // Atualizar a lista local de notificações
                    val updatedNotifications = _allNotifications.value.map { notification ->
                        if (notification.id == notificationId) {
                            notification.copy(lida = true)
                        } else {
                            notification
                        }
                    }
                    _allNotifications.value = updatedNotifications
                    Log.d("NotificationHistoryViewModel", "Notificação $notificationId marcada como lida")
                } else {
                    _error.value = "Erro ao marcar notificação como lida: ${exception?.message}"
                    Log.e("NotificationHistoryViewModel", "Erro ao marcar notificação como lida", exception)
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun refresh(userId: String) {
        Log.d("NotificationHistoryViewModel", "refresh: userId=$userId, isResponsible=${_isResponsible.value}")
        // Buscar o familyId do usuário atual se for responsável
        if (_isResponsible.value) {
            // Para responsáveis, sempre buscar dependentes da família
            loadNotificationHistory(userId, true, null)
        } else {
            loadNotificationHistory(userId, false, null)
        }
    }

    fun getNotificationStats(): Map<String, Int> {
        val notifications = _allNotifications.value
        return mapOf(
            "total" to notifications.size,
            "nao_lidas" to notifications.count { !it.lida },
            "lidas" to notifications.count { it.lida },
            "saidas_geofence" to notifications.count { it.tipoEvento == "saida_geofence" },
            "voltas_geofence" to notifications.count { it.tipoEvento == "volta_geofence" },
            "saidas_rota" to notifications.count { it.tipoEvento == "saida_rota" },
            "voltas_rota" to notifications.count { it.tipoEvento == "volta_rota" },
            "desvios_rota" to notifications.count { it.tipoEvento == "desvio_rota" }
        )
    }

    fun getFilteredStats(): Map<String, Int> {
        val notifications = _filteredNotifications.value
        return mapOf(
            "total_filtrado" to notifications.size,
            "nao_lidas_filtrado" to notifications.count { !it.lida },
            "lidas_filtrado" to notifications.count { it.lida }
        )
    }
} 