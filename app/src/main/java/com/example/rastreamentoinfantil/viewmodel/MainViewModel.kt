package com.example.rastreamentoinfantil.viewmodel

import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import android.app.Application
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.model.LocationRecord
import com.example.rastreamentoinfantil.model.Coordinate
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.service.GeocodingService
import com.example.rastreamentoinfantil.helper.GeofenceHelper // Certifique-se que o nome da classe é GeofenceHelper
import com.example.rastreamentoinfantil.service.LocationService
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import com.example.rastreamentoinfantil.model.Route // Importe o modelo Route
import com.example.rastreamentoinfantil.model.RoutePoint
import com.google.firebase.ktx.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import com.example.rastreamentoinfantil.helper.RouteHelper
import com.google.firebase.auth.FirebaseAuth
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.model.NotificationHistoryEntry
import com.example.rastreamentoinfantil.model.FamilyRelationship
import java.util.Calendar

class MainViewModel(
    application: Application,
    private val firebaseRepository: FirebaseRepository,
    private val locationService: LocationService,
    private val geocodingService: GeocodingService,
    private val geofenceHelperClass: GeofenceHelper,
    private val routeHelper: RouteHelper = RouteHelper()
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FirebaseRepository" // Definição do TAG aqui
        private const val TAG1 = "MainViewModel" // TAG para MainViewModel
    }

    private val _directionsPolyline = MutableStateFlow<List<LatLng>>(emptyList())
    val directionsPolyline: StateFlow<List<LatLng>> = _directionsPolyline.asStateFlow()

    private val _showExitNotificationEvent = MutableSharedFlow<String>()
    val showExitNotificationEvent = _showExitNotificationEvent.asSharedFlow()

    private var lastKnownGeofenceStatus: Boolean? = null

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    // Este será o StateFlow que a UI e a lógica de notificação observam
    // REMOVIDO: _geofenceArea e geofenceArea - agora o sistema usa múltiplas geofences
    // Use geofences.value para acessar todas as geofences do usuário

    private val _isUserInsideGeofence = MutableStateFlow<Boolean?>(null)
    val isUserInsideGeofence: StateFlow<Boolean?> = _isUserInsideGeofence.asStateFlow()

    private val _locationRecords = MutableLiveData<List<LocationRecord>>()
    val locationRecords: LiveData<List<LocationRecord>> get() = _locationRecords

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    private val _isLoadingRoutes = MutableStateFlow<Boolean>(false)
    val isLoadingRoutes: StateFlow<Boolean> = _isLoadingRoutes.asStateFlow()

    sealed interface RouteOperationStatus {
        data class Success(val message: String, val routeId: String? = null) : RouteOperationStatus
        data class Error(val errorMessage: String) : RouteOperationStatus
        object Loading : RouteOperationStatus
        object Idle : RouteOperationStatus // Estado inicial ou após conclusão
    }
    private val _routeOperationStatus = MutableStateFlow<RouteOperationStatus>(RouteOperationStatus.Idle)
    val routeOperationStatus: StateFlow<RouteOperationStatus> = _routeOperationStatus.asStateFlow()

    private val _selectedRoute = MutableStateFlow<Route?>(null)
    val selectedRoute: StateFlow<Route?> = _selectedRoute.asStateFlow()
    // Este LiveData pode ser derivado de _isUserInsideGeofence se a lógica for a mesma
    // ou pode ter sua própria lógica se "fora de rota" for diferente de "fora da geofence circular"
    private val _isLocationOutOfGeofence = MutableLiveData<Boolean>() // Renomeado para clareza
    val isLocationOutOfGeofence: LiveData<Boolean> get() = _isLocationOutOfGeofence

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    // Removido: private var currentGeofence: Geofence? = null
            // REMOVIDO: Comentário sobre _geofenceArea - agora o sistema usa múltiplas geofences
    private var _currentUserId: String? = null
    val currentUserId: String? get() = _currentUserId
    private var isLocationMonitoringActive: Boolean = false // Flag para controlar se o monitoramento está ativo

    private var lastKnownRouteStatus: Boolean? = null
    private val _isLocationOnRoute = MutableStateFlow<Boolean?>(null)
    val isLocationOnRoute: StateFlow<Boolean?> = _isLocationOnRoute.asStateFlow()
    
    // Controle para forçar recálculo de geofences quando o dia muda
    private var lastKnownDayOfWeek: String? = null
    private val _forceGeofenceRecalculation = MutableStateFlow<Boolean>(false)
    val forceGeofenceRecalculation: StateFlow<Boolean> = _forceGeofenceRecalculation.asStateFlow()

    private val _showRouteExitNotificationEvent = MutableSharedFlow<String>()
    val showRouteExitNotificationEvent = _showRouteExitNotificationEvent.asSharedFlow()

    private val _routeDeviationEvent = MutableSharedFlow<RouteDeviation>()
    val routeDeviationEvent = _routeDeviationEvent.asSharedFlow()

    // Novos campos para controle de família e responsável
    private val _familyMembers = MutableStateFlow<List<User>>(emptyList())
    val familyMembers: StateFlow<List<User>> = _familyMembers.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isResponsible = MutableStateFlow<Boolean>(false)
    val isResponsible: StateFlow<Boolean> = _isResponsible.asStateFlow()

    // Novos campos para geofences
    private val _geofences = MutableStateFlow<List<Geofence>>(emptyList())
    val geofences: StateFlow<List<Geofence>> = _geofences.asStateFlow()

    private val _isLoadingGeofences = MutableStateFlow<Boolean>(false)
    val isLoadingGeofences: StateFlow<Boolean> = _isLoadingGeofences.asStateFlow()

    sealed interface GeofenceOperationStatus {
        data class Success(val message: String, val geofenceId: String? = null) : GeofenceOperationStatus
        data class Error(val errorMessage: String) : GeofenceOperationStatus
        object Loading : GeofenceOperationStatus
        object Idle : GeofenceOperationStatus
    }
    private val _geofenceOperationStatus = MutableStateFlow<GeofenceOperationStatus>(GeofenceOperationStatus.Idle)
    val geofenceOperationStatus: StateFlow<GeofenceOperationStatus> = _geofenceOperationStatus.asStateFlow()

    private val _selectedGeofence = MutableStateFlow<Geofence?>(null)
    val selectedGeofence: StateFlow<Geofence?> = _selectedGeofence.asStateFlow()

    // Novos campos para controle de status de geofences e rotas
    private val _geofenceStatusMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val geofenceStatusMap: StateFlow<Map<String, Boolean>> = _geofenceStatusMap.asStateFlow()

    private val _routeStatusMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val routeStatusMap: StateFlow<Map<String, Boolean>> = _routeStatusMap.asStateFlow()

    // Cache para evitar notificações duplicadas
    private val lastNotificationTime = mutableMapOf<String, Long>()
    private val NOTIFICATION_COOLDOWN = 30000L // 30 segundos entre notificações do mesmo tipo
    private val PERIODIC_NOTIFICATION_INTERVAL = 40000L // 5 minutos para notificações periódicas

    data class RouteDeviation(
        val routeId: String,
        val routeName: String,
        val currentLocation: Location,
        val lastKnownRoutePoint: RoutePoint?,
        val deviationDistance: Double
    )

    // Estados para localizações e informações dos dependentes
    private val _dependentsLocations = MutableStateFlow<Map<String, LocationRecord>>(emptyMap())
    val dependentsLocations: StateFlow<Map<String, LocationRecord>> = _dependentsLocations.asStateFlow()

    private val _dependentsInfo = MutableStateFlow<Map<String, User>>(emptyMap())
    val dependentsInfo: StateFlow<Map<String, User>> = _dependentsInfo.asStateFlow()

    // ==================== VARIÁVEIS PARA MONITORAMENTO INTELIGENTE ====================
    
    // Rastreia se o usuário está presente em alguma rota (evita notificações desnecessárias)
    private var _isPresentInAnyRoute = MutableStateFlow<Boolean?>(null)
    val isPresentInAnyRoute: StateFlow<Boolean?> = _isPresentInAnyRoute.asStateFlow()
    
    // Rastreia se o usuário está presente em alguma geofence (evita notificações desnecessárias)
    private var _isPresentInAnyGeofence = MutableStateFlow<Boolean?>(null)
    val isPresentInAnyGeofence: StateFlow<Boolean?> = _isPresentInAnyGeofence.asStateFlow()
    
    // Rastreia a última rota onde o usuário estava presente
    private var _lastPresentRouteId = MutableStateFlow<String?>(null)
    val lastPresentRouteId: StateFlow<String?> = _lastPresentRouteId.asStateFlow()
    
    // Rastreia a última geofence onde o usuário estava presente
    private var _lastPresentGeofenceId = MutableStateFlow<String?>(null)
    val lastPresentGeofenceId: StateFlow<String?> = _lastPresentGeofenceId.asStateFlow()

    // Variáveis de contagem para saída global
    private var exitAllGeofencesCounter = 0
    private var exitAllRoutesCounter = 0
    private val EXIT_GLOBAL_THRESHOLD = 3

    init {
        Log.d(TAG1, "Inicializando MainViewModel")
        
        // Não inicia monitoramento automaticamente no init
        // Será iniciado explicitamente pela MainActivity quando necessário
        
        // Inicializar o dia da semana atual
        val diasSemana = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
        val cal = java.util.Calendar.getInstance()
        val idx = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        lastKnownDayOfWeek = diasSemana[idx]
        Log.d(TAG1, "[init] Dia da semana inicial: $lastKnownDayOfWeek")
    }

    /**
     * Inicializa o currentUserId quando o usuário faz login
     */
    fun initializeUser() {
        val currentUser = firebaseRepository.getCurrentUser()
        if (currentUser != null) {
            _currentUserId = currentUser.uid
            Log.d(TAG1, "Usuário inicializado: $_currentUserId")

            // Obter e salvar token FCM
            retrieveAndSaveFcmToken(currentUser.uid)
            
            // Forçar obtenção do token atual para debug
            forceGetCurrentToken()

            // Executar migração de relacionamentos familiares em background
            viewModelScope.launch(Dispatchers.IO) {
                firebaseRepository.migrateExistingFamilyRelationships { success, message ->
                    if (success) {
                        Log.d(TAG1, "Migração de relacionamentos concluída: $message")
                    } else {
                        Log.e(TAG1, "Erro na migração de relacionamentos: $message")
                    }
                }
            }
            
            loadUserIdAndGeofence()
            
            // Iniciar verificação periódica de mudança de dia da semana
            startDayOfWeekMonitoring()
        }
    }

    fun startLocationMonitoring() {
        if (isLocationMonitoringActive) {
            Log.d(TAG1, "startLocationMonitoring: Monitoramento já está ativo, ignorando chamada")
            return
        }
        Log.d(TAG1, "startLocationMonitoring: Iniciando monitoramento de localização")
        isLocationMonitoringActive = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                locationService.startLocationUpdates { location ->
                    _currentLocation.value = location
                    Log.d(TAG1, "Nova localização recebida: (${location.latitude}, ${location.longitude})")
                    
                    // Salvar localização no Firebase
                    currentUserId?.let { userId ->
                        saveLocationToFirebase(location, userId)
                    }
                    
                    // Verificar status de geofence e rota
                    checkAllGeofencesStatus(location)
                    checkAllRoutesStatus(location)
                    
                    // Verifica rotas ativas em uma coroutine separada com prioridade baixa
                    viewModelScope.launch(Dispatchers.Default) {
                        checkActiveRoutes(location)
                    }
                }
                Log.d(TAG1, "Serviço de localização iniciado")
            } catch (e: Exception) {
                Log.e(TAG1, "Erro ao iniciar serviço de localização", e)
                _error.value = "Erro ao iniciar serviço de localização: ${e.message}"
                isLocationMonitoringActive = false // Reset da flag em caso de erro
            }
        }
    }

    /**
     * Para o monitoramento de localização
     */
    fun stopLocationMonitoring() {
        if (!isLocationMonitoringActive) {
            Log.d(TAG1, "stopLocationMonitoring: Monitoramento não está ativo")
            return
        }
        
        Log.d(TAG1, "stopLocationMonitoring: Parando monitoramento de localização")
        locationService.stopLocationUpdates()
        isLocationMonitoringActive = false
    }

    /**
     * Salva a localização atual no Firebase
     */
    private fun saveLocationToFirebase(location: Location, userId: String) {
        Log.d(TAG1, "saveLocationToFirebase: Iniciando salvamento da localização para usuário: $userId")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG1, "saveLocationToFirebase: Obtendo endereço da localização")
                // Obter endereço da localização
                geocodingService.getAddressFromLocation(location) { address ->
                    Log.d(TAG1, "saveLocationToFirebase: Endereço obtido: $address")
                    val locationRecord = LocationRecord(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address,
                        dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                        isOutOfRoute = false // Será atualizado pela verificação de rotas
                    )
                    
                    Log.d(TAG1, "saveLocationToFirebase: Salvando LocationRecord no Firebase")
                    firebaseRepository.saveLocationRecord(locationRecord, userId) { success, exception ->
                        if (success) {
                            Log.d(TAG1, "saveLocationToFirebase: Localização salva no Firebase com sucesso: (${location.latitude}, ${location.longitude})")
                            // Recarregar registros de localização para atualizar a UI apenas se necessário
                            // Comentado para evitar múltiplas chamadas desnecessárias
                            // loadLocationRecords()
                        } else {
                            Log.e(TAG1, "saveLocationToFirebase: Erro ao salvar localização no Firebase", exception)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG1, "saveLocationToFirebase: Erro ao processar localização para salvar", e)
            }
        }
    }

    private suspend fun checkActiveRoutes(location: Location) {
        if (location == null) return

        Log.d(TAG1, "Nova localização recebida: (${location.latitude}, ${location.longitude})")
        
        // Primeiro, verifica se o usuário está em alguma rota ativa
        val activeRoutes = routes.value.filter { it.isActive }
        var isOnAnyRoute = false
        
        // Verifica todas as rotas ativas
        activeRoutes.forEach { route ->
            Log.d(TAG1, "Verificando rota ativa: ${route.name}")
            if (routeHelper.isLocationOnRoute(location, route, forceCheck = true)) {
                isOnAnyRoute = true
                Log.d(TAG1, "Usuário está na rota: ${route.name}")
            }
        }
        
        // Se não estiver em nenhuma rota, verifica desvios
        if (!isOnAnyRoute) {
            activeRoutes.forEach { route ->
                val deviationDistance = routeHelper.calculateDeviationDistance(location, route)
                Log.d(TAG1, "Desvio detectado na rota ${route.name}. Distância: $deviationDistance metros")
                
                // Emite evento de desvio
                _routeDeviationEvent.emit(
                    RouteDeviation(
                        routeId = route.id ?: "",
                        routeName = route.name,
                        currentLocation = location,
                        lastKnownRoutePoint = routeHelper.lastKnownRoutePoint,
                        deviationDistance = deviationDistance.toDouble()
                    )
                )
            }
        }
    }

    private fun loadUserIdAndGeofence() {
        _isLoading.value = true
        val currentUser = firebaseRepository.getCurrentUser()
        if (currentUser != null) {
            _currentUserId = currentUser.uid
            // Carregar geofences do usuário
            loadUserGeofences()
        } else {
            _isLoading.value = false
            _error.value = "Usuário não encontrado!"
            // REMOVIDO: _geofenceArea - agora o sistema usa múltiplas geofences
            println("Nenhum usuário logado, nenhuma geofence será carregada do Firebase.")
        }
    }

    // REMOVIDO: loadUserGeofence - agora o sistema usa múltiplas geofences
    // A função loadUserGeofences() já existe e carrega todas as geofences do usuário

    private fun loadLocationRecords() {
        currentUserId?.let { userId ->
            // Considere definir _isLoading.value = true aqui se esta é uma operação de carregamento independente
            firebaseRepository.getUserLocationRecords(userId) { records, exception ->
                // _isLoading.value = false; // Defina isLoading aqui após a operação completar
                if (exception != null) {
                    _error.value = "Erro ao carregar registros de localização: ${exception.message}"
                    // Mesmo em caso de erro, você pode querer limpar os registros existentes ou postar uma lista vazia
                    _locationRecords.postValue(emptyList())
                    return@getUserLocationRecords
                }

                // Se exception for null, 'records' pode ser a lista de dados ou null (se não houver registros)
                // Usamos o operador elvis (?:) para fornecer uma lista vazia se 'records' for nulo.
                _locationRecords.postValue(records ?: emptyList()) // CORRIGIDO AQUI
            }
        } ?: run {
            // Se não houver ID de usuário, poste uma lista vazia ou trate o erro conforme necessário
            _locationRecords.postValue(emptyList())
            _error.value = "ID do usuário não disponível para carregar registros de localização."
            // _isLoading.value = false; // Certifique-se que o loading é parado
            Log.w(TAG, "loadLocationRecords chamado sem currentUserId.")
        }
    }

    // REMOVIDO: updateUserGeofence - agora o sistema usa múltiplas geofences
    // As funções createGeofence(), updateGeofence() e deleteGeofence() já existem

    fun retrieveAndSaveFcmToken(userId: String) {
        Log.d(TAG1, "[retrieveAndSaveFcmToken] Solicitando token FCM para userId: $userId")
        
        // Verificar se o FCM está habilitado
        val isAutoInitEnabled = com.google.firebase.ktx.Firebase.messaging.isAutoInitEnabled
        Log.d(TAG1, "[retrieveAndSaveFcmToken] FCM Auto Init Enabled: $isAutoInitEnabled")
        
        com.google.firebase.ktx.Firebase.messaging.token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG1, "[retrieveAndSaveFcmToken] Falha ao obter token FCM", task.exception)
                return@addOnCompleteListener
            }

            // Obter novo token de registro FCM
            val token = task.result
            Log.d(TAG1, "[retrieveAndSaveFcmToken] Token FCM obtido: $token")

            // Salve este token no Firestore associado ao userId do pai
            firebaseRepository.saveUserFcmToken(userId, token) { success, exception ->
                if (success) {
                    Log.d(TAG1, "[retrieveAndSaveFcmToken] Token FCM salvo com sucesso no Firestore para userId: $userId")
                } else {
                    Log.e(TAG1, "[retrieveAndSaveFcmToken] Erro ao salvar token FCM no Firestore: ${exception?.message}")
                }
            }
        }
    }
    
    /**
     * Força a obtenção do token atual para debug
     */
    fun forceGetCurrentToken() {
        Log.d(TAG1, "[forceGetCurrentToken] Forçando obtenção do token atual")
        com.google.firebase.ktx.Firebase.messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG1, "[forceGetCurrentToken] Token atual: $token")
            } else {
                Log.e(TAG1, "[forceGetCurrentToken] Erro ao obter token", task.exception)
            }
        }
    }

    fun loadUserRoutes(userId: String) {
        Log.d(TAG1, "[loadUserRoutes] Iniciando carregamento de rotas para userId: $userId")
        _isLoadingRoutes.value = true
        firebaseRepository.getUserRoutes(userId) { routes, error ->
            _isLoadingRoutes.value = false
            if (error != null) {
                Log.e(TAG1, "[loadUserRoutes] Erro ao carregar rotas", error)
                _error.value = "Erro ao carregar rotas: ${error.message}"
            } else {
                val routesList = routes ?: emptyList()
                val previousRoutes = _routes.value
                _routes.value = routesList
                Log.d(TAG1, "[loadUserRoutes] Rotas carregadas: ${routesList.size}")
                
                // Inicializar o status das rotas se houver localização atual
                _currentLocation.value?.let { location ->
                    Log.d(TAG1, "[loadUserRoutes] Inicializando status das rotas com localização atual: (${location.latitude}, ${location.longitude})")
                    
                    // Preservar status anterior se existir
                    val currentStatusMap = _routeStatusMap.value.toMutableMap()
                    
                    routesList.forEach { route ->
                        val routeId = route.id ?: return@forEach
                        val isOnRoute = routeHelper.isLocationOnRoute(location, route, forceCheck = true)
                        
                        // Se já existe um status anterior para esta rota, verificar se houve mudança
                        val previousStatus = currentStatusMap[routeId]
                        if (previousStatus != null && previousStatus != isOnRoute) {
                            Log.d(TAG1, "[loadUserRoutes] 🔄 Mudança detectada na rota ${route.name}: $previousStatus -> $isOnRoute")
                            // Forçar verificação de notificação
                            val currentTime = System.currentTimeMillis()
                            val notificationKey = "route_${routeId}_${if (isOnRoute) "return" else "exit"}"
                            val cooldownTime = if (isOnRoute) 10000L else NOTIFICATION_COOLDOWN
                            
                            if (currentTime - (lastNotificationTime[notificationKey] ?: 0L) > cooldownTime) {
                                lastNotificationTime[notificationKey] = currentTime
                                
                                if (!isOnRoute) {
                                    Log.d(TAG1, "[loadUserRoutes] 🚪 Usuário saiu da rota: ${route.name}")
                                    _showRouteExitNotificationEvent.tryEmit(route.name)
                                    onRouteDeviation(route, location)
                                } else {
                                    Log.d(TAG1, "[loadUserRoutes] 🏠 Usuário voltou para a rota: ${route.name}")
                                    onRouteReturn(route, location)
                                }
                            }
                        }
                        
                        // Atualizar o status atual
                        currentStatusMap[routeId] = isOnRoute
                    }
                    
                    _routeStatusMap.value = currentStatusMap
                    Log.d(TAG1, "[loadUserRoutes] Status das rotas atualizado: $currentStatusMap")
                    
                    // Verificar status inicial das rotas (apenas para novas rotas)
                    Log.d(TAG1, "[loadUserRoutes] Chamando checkAllRoutesStatus...")
                    checkAllRoutesStatus(location)
                } ?: run {
                    Log.w(TAG1, "[loadUserRoutes] ⚠️ Nenhuma localização atual disponível para inicializar status")
                }
            }
        }
        
        // Também carregar geofences do usuário
        loadUserGeofences()
    }
    fun addOrUpdateRoute(route: Route) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        Log.d(TAG, "addOrUpdateRoute: FirebaseAuth.currentUser = ${firebaseUser?.uid}, email = ${firebaseUser?.email}")
        val userId = firebaseUser?.uid
        
        // Verificar se o usuário é responsável
        if (!canManageRoutes()) {
            _routeOperationStatus.value = RouteOperationStatus.Error("Apenas responsáveis podem editar rotas.")
            return
        }
        
        userId?.let {
            viewModelScope.launch {
                _routeOperationStatus.value = RouteOperationStatus.Loading
                firebaseRepository.saveRoute(it, route) { routeId, exception ->
                    Log.d(TAG, "Usuario id passado para o repo: $it")
                    if (exception != null) {
                        _routeOperationStatus.value = RouteOperationStatus.Error(exception.message ?: "Falha ao salvar rota.")
                        Log.e(TAG, "Erro ao salvar rota ${route.id} para o usuário $it", exception)
                    } else if (routeId != null) { // Sucesso se routeId não for nulo
                        _routeOperationStatus.value = RouteOperationStatus.Success("Rota salva com sucesso!")
                        loadUserRoutes(it) // Recarregar rotas após salvar
                        
                        // Forçar verificação de status após atualização
                        Log.d(TAG1, "[addOrUpdateRoute] Forçando verificação de status após atualização")
                        forceRouteStatusCheck()
                    } else {
                        _routeOperationStatus.value = RouteOperationStatus.Error("Falha desconhecida ao salvar rota.")
                        Log.e(TAG, "Erro desconhecido ao salvar rota ${route.id} para o usuário $it")
                    }
                }
            }
        } ?: run {
            _routeOperationStatus.value = RouteOperationStatus.Error("Usuário não logado. Não é possível salvar a rota.")
            Log.w(TAG, "Tentativa de salvar rota sem usuário logado. FirebaseAuth.currentUser = $firebaseUser")
        }
    }
    fun deleteRoute(routeId: String) {
        // Verificar se o usuário é responsável
        if (!canManageRoutes()) {
            _routeOperationStatus.value = RouteOperationStatus.Error("Apenas responsáveis podem excluir rotas.")
            return
        }
        
        currentUserId?.let { userId ->
            viewModelScope.launch {
                _routeOperationStatus.value = RouteOperationStatus.Loading
                firebaseRepository.deleteRoute(userId, routeId) { success, exception ->
                    if (success) {
                        _routeOperationStatus.value = RouteOperationStatus.Success("Rota removida com sucesso!")
                        loadUserRoutes(userId) // Recarregar rotas após deletar
                        if (_selectedRoute.value?.id == routeId) { // Limpar rota selecionada se for a deletada
                            _selectedRoute.value = null
                        }
                    } else {
                        _routeOperationStatus.value = RouteOperationStatus.Error(exception?.message ?: "Falha ao remover rota.")
                        Log.e(TAG, "Erro ao deletar rota $routeId para o usuário $userId", exception)
                    }
                }
            }
        } ?: run {
            _routeOperationStatus.value = RouteOperationStatus.Error("Usuário não logado. Não é possível remover a rota.")
            Log.w(TAG, "Tentativa de deletar rota $routeId sem usuário logado.")
        }
    }

    fun loadRouteDetails(routeId: String) {
        currentUserId?.let { userId ->
            _isLoading.value = true // Ou um isLoading específico para detalhes da rota
            firebaseRepository.getRouteById(userId, routeId) { route, exception ->
                _isLoading.value = false
                if (exception != null) {
                    _error.value = "Falha ao carregar detalhes da rota: ${exception.message}"
                    _selectedRoute.value = null
                    Log.e(TAG, "Erro ao carregar rota $routeId", exception)
                } else {
                    _selectedRoute.value = route // route pode ser nulo se não encontrado
                    if (route == null) {
                        _error.value = "Rota não encontrada."
                        Log.w(TAG, "Rota $routeId não encontrada para usuário $userId")
                    }
                }
            }
        } ?: run {
            _error.value = "Usuário não logado para carregar detalhes da rota."
            _selectedRoute.value = null
        }
    }

    private suspend fun getEncodedPolylineFromDirectionsApi(
        origin: RoutePoint,
        destination: RoutePoint,
        waypoints: List<RoutePoint>?,
        apiKey: String
    ): String? {
        return withContext(Dispatchers.IO) {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destinationStr = "${destination.latitude},${destination.longitude}"

            // Otimizar os waypoints para a API Directions
            // A API prefere "optimize:true|waypoint1|waypoint2"
            val waypointsStr = waypoints?.takeIf { it.isNotEmpty() }?.joinToString(separator = "|") {
                "${it.latitude},${it.longitude}"
            }

            var urlString = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=$originStr" +
                    "&destination=$destinationStr" +
                    "&key=$apiKey" +
                    "&mode=driving" // ou walking, bicycling

            if (!waypointsStr.isNullOrEmpty()) {
                // Para melhor roteamento com waypoints, a API pode reordená-los se você adicionar optimize:true
                // urlString += "&waypoints=optimize:true|$waypointsStr"
                // Se a ordem dos waypoints for estrita:
                urlString += "&waypoints=$waypointsStr"
            }

            Log.d(TAG1, "Requesting Encoded Polyline URL: $urlString")
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000 // 15 segundos
                connection.readTimeout = 15000  // 15 segundos

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val jsonResponse = JSONObject(response.toString())
                    Log.d(TAG1, "Directions API Response for Polyline: $jsonResponse")

                    if (jsonResponse.getString("status") == "OK") {
                        val routesArray = jsonResponse.getJSONArray("routes")
                        if (routesArray.length() > 0) {
                            return@withContext routesArray.getJSONObject(0)
                                .getJSONObject("overview_polyline")
                                .getString("points")
                        } else {
                            Log.w(TAG1, "Directions API: Nenhuma rota encontrada.")
                        }
                    } else {
                        val errorMessage = jsonResponse.optString("error_message", "Status não OK")
                        Log.e(TAG1, "Erro da Directions API: ${jsonResponse.getString("status")} - $errorMessage")
                    }
                } else {
                    Log.e(TAG1, "Directions API: HTTP Error Code: ${connection.responseCode} - ${connection.responseMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG1, "Exceção ao processar resposta da Directions API: ${e.message}", e)
            } finally {
                connection?.disconnect()
            }
            return@withContext null
        }
    }

    // --- NOVA FUNÇÃO PARA CRIAR ROTA COM DIRECTIONS ---
    fun createRouteWithDirections(
        name: String,
        originPoint: RoutePoint,
        destinationPoint: RoutePoint,
        waypointsList: List<RoutePoint>?,
        routeColor: String = "#FF0000", // Cor padrão
        isActive: Boolean = true,
        activeDays: List<String> = emptyList(),
        targetUserId: String? = null,
        id: String? = null, // NOVO: id opcional para edição
        createdByUserId: String? = null // NOVO: para manter o criador original
    ) {
        currentUserId?.let { userId ->
            // Verificar se o usuário é responsável
            if (!canManageRoutes()) {
                _routeOperationStatus.value = RouteOperationStatus.Error("Apenas responsáveis podem criar rotas.")
                return
            }
            viewModelScope.launch {
                _routeOperationStatus.value = RouteOperationStatus.Loading
                val apiKey = "AIzaSyB3KoJSAscYJb_YG70Mw3dqiBVXjMm7Z-k" // Obtenha sua API Key do BuildConfig

                if (apiKey.isEmpty() || apiKey != "AIzaSyB3KoJSAscYJb_YG70Mw3dqiBVXjMm7Z-k") {
                    Log.e(TAG1, "MAPS_API_KEY não configurada no BuildConfig.")
                    Log.e(TAG1, apiKey.isEmpty().toString())
                    _routeOperationStatus.value = RouteOperationStatus.Error("Chave de API do Google Maps não configurada.")
                    return@launch
                }

                val encodedPolylineString = getEncodedPolylineFromDirectionsApi(
                    originPoint,
                    destinationPoint,
                    waypointsList,
                    apiKey
                )

                if (encodedPolylineString == null) {
                    _routeOperationStatus.value = RouteOperationStatus.Error("Não foi possível calcular o caminho da rota. Verifique os pontos ou a conexão.")
                    Log.w(TAG1, "Falha ao obter encodedPolyline da API.")
                    return@launch
                }

                val newRoute = Route(
                    id = id ?: UUID.randomUUID().toString(), // Use o id passado para edição, ou gere novo para criação
                    name = name,
                    origin = originPoint,
                    destination = destinationPoint,
                    waypoints = waypointsList,
                    encodedPolyline = encodedPolylineString, // ARMAZENA A POLYLINE CODIFICADA
                    isActive = isActive,
                    routeColor = routeColor,
                    activeDays = activeDays,
                    targetUserId = targetUserId,
                    createdByUserId = createdByUserId ?: userId
                    // createdAt e updatedAt serão definidos pelo @ServerTimestamp no Firestore
                )

                firebaseRepository.saveRoute(userId, newRoute) { routeIdFromSave, exception ->
                    if (exception != null) {
                        _routeOperationStatus.value = RouteOperationStatus.Error(exception.message ?: "Falha ao salvar rota.")
                        Log.e(TAG1, "Erro ao salvar nova rota ${newRoute.id} para o usuário $userId", exception)
                    } else if (routeIdFromSave != null) { // Sucesso
                        _routeOperationStatus.value = RouteOperationStatus.Success("Rota '${newRoute.name}' criada e salva!", routeIdFromSave)
                        loadUserRoutes(userId) // Recarregar rotas após salvar
                        Log.d(TAG1, "[createRouteWithDirections] Forçando verificação de status após criação/edição")
                        forceRouteStatusCheck()
                    } else {
                        _routeOperationStatus.value = RouteOperationStatus.Error("Falha desconhecida ao salvar rota.")
                        Log.e(TAG1, "Erro desconhecido ao salvar nova rota ${newRoute.id} para o usuário $userId")
                    }
                }
            }
        } ?: run {
            _routeOperationStatus.value = RouteOperationStatus.Error("Usuário não logado. Não é possível criar a rota.")
            Log.w(TAG1, "Tentativa de criar rota sem usuário logado.")
        }
    }

    fun clearRouteOperationStatus() {
        _routeOperationStatus.value = RouteOperationStatus.Idle
    }

    fun clearAllData() {
        // Limpar dados de localização
        _currentLocation.value = null
        _isUserInsideGeofence.value = null
        _isLocationOutOfGeofence.value = false
        _isLocationOnRoute.value = null
        
        // Limpar dados de rotas
        _routes.value = emptyList()
        _selectedRoute.value = null
        _isLoadingRoutes.value = false
        _routeOperationStatus.value = RouteOperationStatus.Idle
        
        // Limpar dados de geofences (múltiplas geofences)
        _geofences.value = emptyList()
        _selectedGeofence.value = null
        _isLoadingGeofences.value = false
        _geofenceOperationStatus.value = GeofenceOperationStatus.Idle
        
        // Limpar variáveis de monitoramento inteligente
        _isPresentInAnyRoute.value = null
        _isPresentInAnyGeofence.value = null
        _lastPresentRouteId.value = null
        _lastPresentGeofenceId.value = null
        
        // Limpar registros de localização
        _locationRecords.value = emptyList()
        
        // Limpar dados de usuário e família
        _currentUserId = null
        _currentUser.value = null
        _familyMembers.value = emptyList()
        _isResponsible.value = false
        
        // Limpar estados de loading e erro
        _isLoading.value = false
        _error.value = null
        
        Log.d(TAG, "Todos os dados foram limpos")
    }

    fun resetAuthenticationState() {
        clearAllData()
        // Resetar o estado de autenticação
        _currentUserId = null
        Log.d(TAG, "Estado de autenticação resetado")
    }

    /**
     * Sincroniza o usuário logado do FirebaseAuth com o campo currentUserId do ViewModel.
     * Deve ser chamada ao abrir telas sensíveis (ex: edição de rota, mapa, etc).
     */
    fun syncCurrentUser() {
        Log.d(TAG1, "[syncCurrentUser] Iniciando sincronização do usuário")
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            _currentUserId = firebaseUser.uid
            Log.d(TAG1, "[syncCurrentUser] Usuário sincronizado: ${firebaseUser.uid}, email: ${firebaseUser.email}")
            // Carregar dados do usuário e família após sincronizar
            loadCurrentUserData()
            // Carregar rotas após determinar o tipo de usuário
            loadUserRoutes(firebaseUser.uid)
            // Iniciar monitoramento de localização
            startLocationMonitoring()
        } else {
            _currentUserId = null
            // Parar monitoramento se não há usuário logado
            stopLocationMonitoring()
            Log.w(TAG1, "[syncCurrentUser] Nenhum usuário logado!")
        }
    }

    /**
     * Carrega dados do usuário atual e verifica se é responsável
     */
    private fun loadCurrentUserData() {
        Log.d(TAG1, "[loadCurrentUserData] Iniciando carregamento de dados do usuário")
        currentUserId?.let { userId ->
            firebaseRepository.getUserById(userId) { user, error ->
                if (error != null) {
                    Log.e(TAG1, "[loadCurrentUserData] Erro ao carregar dados do usuário", error)
                    _error.value = "Erro ao carregar dados do usuário: ${error.message}"
                } else {
                    user?.let { currentUserData ->
                        _currentUser.value = currentUserData
                        val wasResponsible = _isResponsible.value
                        _isResponsible.value = currentUserData.type == "responsavel"
                        Log.d(TAG1, "[loadCurrentUserData] Usuário carregado: ${currentUserData.name}, tipo: ${currentUserData.type}, isResponsible: ${_isResponsible.value}")
                        
                        // Se o status de responsável mudou, recarregar rotas
                        if (wasResponsible != _isResponsible.value) {
                            Log.d(TAG1, "[loadCurrentUserData] Status de responsável mudou, recarregando rotas")
                            loadUserRoutes(userId)
                        }
                        
                        // Se o usuário tem família, carregar membros
                        if (!currentUserData.familyId.isNullOrEmpty()) {
                            Log.d(TAG1, "[loadCurrentUserData] Carregando membros da família: ${currentUserData.familyId}")
                            loadFamilyMembers(currentUserData.familyId!!)
                        } else {
                            Log.d(TAG1, "[loadCurrentUserData] Usuário não tem família definida")
                        }
                    } ?: run {
                        Log.w(TAG1, "[loadCurrentUserData] Usuário não encontrado no Firestore")
                    }
                }
            }
        } ?: run {
            Log.w(TAG1, "[loadCurrentUserData] currentUserId é nulo")
        }
    }

    /**
     * Carrega membros da família (excluindo o responsável)
     */
    private fun loadFamilyMembers(familyId: String) {
        firebaseRepository.getFamilyDetails(familyId) { family, members, error ->
            if (error != null) {
                Log.e(TAG1, "Erro ao carregar membros da família", error)
                _error.value = "Erro ao carregar membros da família: ${error.message}"
            } else {
                // Filtrar apenas membros (excluindo responsáveis)
                val filteredMembers = members?.filter { member ->
                    member.type != "responsavel"
                } ?: emptyList()
                
                _familyMembers.value = filteredMembers
                Log.d(TAG1, "Membros da família carregados (excluindo responsável): ${filteredMembers.size} de ${members?.size ?: 0} total")
            }
        }
    }

    /**
     * Verifica se o usuário atual pode criar/editar rotas (apenas responsáveis)
     */
    fun canManageRoutes(): Boolean {
        return _isResponsible.value
    }

    /**
     * Verifica se uma rota está ativa para o dia atual
     */
    fun isRouteActiveForToday(route: Route): Boolean {
        if (!route.isActive) return false
        
        val today = Calendar.getInstance()
        val dayOfWeek = today.get(Calendar.DAY_OF_WEEK)
        
        // Converter número do dia da semana para string (com acentos)
        val dayName = when (dayOfWeek) {
            Calendar.SUNDAY -> "Domingo"
            Calendar.MONDAY -> "Segunda"
            Calendar.TUESDAY -> "Terça"
            Calendar.WEDNESDAY -> "Quarta"
            Calendar.THURSDAY -> "Quinta"
            Calendar.FRIDAY -> "Sexta"
            Calendar.SATURDAY -> "Sábado"
            else -> ""
        }
        
        return route.activeDays.contains(dayName)
    }

    /**
     * Filtra rotas que estão ativas para hoje
     */
    fun getActiveRoutesForToday(): List<Route> {
        val diasSemana = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
        val cal = java.util.Calendar.getInstance()
        val idx = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        val diaAtual = diasSemana[idx]
        Log.d(TAG1, "[getActiveRoutesForToday] diaAtual=$diaAtual")
        Log.d(TAG1, "[getActiveRoutesForToday] isResponsible: ${_isResponsible.value}")
        
        val activeRoutes = routes.value.filter { route ->
            Log.d(TAG1, "[getActiveRoutesForToday] Route ${route.name}: activeDays=${route.activeDays}")
            
            val isActive = route.isActive
            
            // Para responsáveis: mostrar todas as rotas ativas (independente do dia)
            // Para membros: mostrar apenas rotas ativas para o dia atual
            val isDayMatch = if (_isResponsible.value) {
                Log.d(TAG1, "[getActiveRoutesForToday] Responsável: ignorando filtro de dia da semana")
                true // Responsáveis veem todas as rotas independente do dia
            } else {
                val dayMatch = route.activeDays.contains(diaAtual)
                Log.d(TAG1, "[getActiveRoutesForToday] Membro: verificando se $diaAtual está em ${route.activeDays} = $dayMatch")
                dayMatch // Membros veem apenas rotas do dia atual
            }
            
            val result = isActive && isDayMatch
            Log.d(TAG1, "[getActiveRoutesForToday] Route ${route.name}: isActive=$isActive, diaAtual=$diaAtual, activeDays=${route.activeDays}, result=$result")
            result
        }
        Log.d(TAG1, "[getActiveRoutesForToday] Rotas ativas para hoje: ${activeRoutes.size}")
        return activeRoutes
    }

    // ==================== FUNÇÕES DE GEOFENCE ====================

    /**
     * Carrega geofences do usuário atual
     */
    fun loadUserGeofences() {
        Log.d(TAG1, "[loadUserGeofences] === INÍCIO DO CARREGAMENTO ===")
        Log.d(TAG1, "[loadUserGeofences] currentUserId: $currentUserId")
        currentUserId?.let { userId ->
            _isLoadingGeofences.value = true
            Log.d(TAG1, "[loadUserGeofences] Chamando Firebase para carregar geofences do usuário: $userId")
            firebaseRepository.getUserGeofences(userId) { geofences, error ->
                _isLoadingGeofences.value = false
                if (error != null) {
                    Log.e(TAG1, "[loadUserGeofences] ❌ Erro ao carregar geofences", error)
                    _error.value = "Erro ao carregar áreas seguras: ${error.message}"
                } else {
                    val geofencesList = geofences ?: emptyList()
                    val previousGeofences = _geofences.value
                    _geofences.value = geofencesList
                    Log.d(TAG1, "[loadUserGeofences] ✅ Geofences carregadas do Firebase: ${geofencesList.size}")
                    
                    geofencesList.forEach { geofence ->
                        Log.d(TAG1, "[loadUserGeofences] 📍 Geofence: ${geofence.name} (ID: ${geofence.id})")
                        Log.d(TAG1, "[loadUserGeofences]   - isActive: ${geofence.isActive}")
                        Log.d(TAG1, "[loadUserGeofences]   - targetUserId: ${geofence.targetUserId}")
                        Log.d(TAG1, "[loadUserGeofences]   - createdByUserId: ${geofence.createdByUserId}")
                        Log.d(TAG1, "[loadUserGeofences]   - coordenadas: (${geofence.coordinates.latitude}, ${geofence.coordinates.longitude})")
                        Log.d(TAG1, "[loadUserGeofences]   - raio: ${geofence.radius}m")
                    }
                    
                    // Inicializar o status das geofences se houver localização atual
                    _currentLocation.value?.let { location ->
                        Log.d(TAG1, "[loadUserGeofences] Inicializando status das geofences com localização atual: (${location.latitude}, ${location.longitude})")
                        
                        // Preservar status anterior se existir
                        val currentStatusMap = _geofenceStatusMap.value.toMutableMap()
                        
                        geofencesList.forEach { geofence ->
                            val geofenceId = geofence.id ?: return@forEach
                            val isInside = geofenceHelperClass.isLocationInGeofence(location, geofence)
                            
                            // Se já existe um status anterior para esta geofence, verificar se houve mudança
                            val previousStatus = currentStatusMap[geofenceId]
                            if (previousStatus != null && previousStatus != isInside) {
                                Log.d(TAG1, "[loadUserGeofences] 🔄 Mudança detectada na geofence ${geofence.name}: $previousStatus -> $isInside")
                                // Forçar verificação de notificação
                                val currentTime = System.currentTimeMillis()
                                val notificationKey = "geofence_${geofenceId}_${if (isInside) "return" else "exit"}"
                                val cooldownTime = if (isInside) 10000L else NOTIFICATION_COOLDOWN
                                
                                if (currentTime - (lastNotificationTime[notificationKey] ?: 0L) > cooldownTime) {
                                    lastNotificationTime[notificationKey] = currentTime
                                    
                                    if (!isInside) {
                                        Log.d(TAG1, "[loadUserGeofences] 🚪 Usuário saiu da geofence: ${geofence.name}")
                                        _showExitNotificationEvent.tryEmit(geofence.name)
                                        onGeofenceExit(geofence, location)
                                    } else {
                                        Log.d(TAG1, "[loadUserGeofences] 🏠 Usuário voltou para a geofence: ${geofence.name}")
                                        onGeofenceReturn(geofence, location)
                                    }
                                }
                            }
                            
                            // Atualizar o status atual
                            currentStatusMap[geofenceId] = isInside
                        }
                        
                        _geofenceStatusMap.value = currentStatusMap
                        Log.d(TAG1, "[loadUserGeofences] Status das geofences atualizado: $currentStatusMap")
                        
                        // Verificar status inicial das geofences (apenas para novas geofences)
                        Log.d(TAG1, "[loadUserGeofences] Chamando checkAllGeofencesStatus...")
                        checkAllGeofencesStatus(location)
                    } ?: run {
                        Log.w(TAG1, "[loadUserGeofences] ⚠️ Nenhuma localização atual disponível para inicializar status")
                    }
                }
            }
        } ?: run {
            Log.w(TAG1, "[loadUserGeofences] ❌ currentUserId é nulo - não é possível carregar geofences")
        }
        Log.d(TAG1, "[loadUserGeofences] === FIM DO CARREGAMENTO ===")
    }

    /**
     * Carrega detalhes de uma geofence específica
     */
    fun loadGeofenceDetails(geofenceId: String) {
        firebaseRepository.getGeofenceById(geofenceId) { geofence, error ->
            if (error != null) {
                Log.e(TAG1, "Erro ao carregar detalhes da geofence", error)
                _error.value = "Erro ao carregar detalhes da área segura: ${error.message}"
            } else {
                _selectedGeofence.value = geofence
                Log.d(TAG1, "Detalhes da geofence carregados: ${geofence?.name}")
            }
        }
    }

    /**
     * Cria uma nova geofence
     */
    fun createGeofence(geofence: Geofence) {
        _geofenceOperationStatus.value = GeofenceOperationStatus.Loading
        currentUserId?.let { userId ->
            firebaseRepository.saveGeofence(userId, geofence) { geofenceId, exception ->
                if (exception != null) {
                    _geofenceOperationStatus.value = GeofenceOperationStatus.Error(exception.message ?: "Falha ao criar área segura.")
                    Log.e(TAG1, "Erro ao criar geofence ${geofence.id}", exception)
                } else if (geofenceId != null) {
                    _geofenceOperationStatus.value = GeofenceOperationStatus.Success("Área segura '${geofence.name}' criada!", geofenceId)
                    loadUserGeofences() // Recarregar geofences após criar
                } else {
                    _geofenceOperationStatus.value = GeofenceOperationStatus.Error("Falha desconhecida ao criar área segura.")
                    Log.e(TAG1, "Erro desconhecido ao criar geofence ${geofence.id}")
                }
            }
        } ?: run {
            _geofenceOperationStatus.value = GeofenceOperationStatus.Error("Usuário não logado. Não é possível criar a área segura.")
            Log.w(TAG1, "Tentativa de criar geofence sem usuário logado.")
        }
    }

    /**
     * Atualiza uma geofence existente
     */
    fun updateGeofence(geofence: Geofence) {
        _geofenceOperationStatus.value = GeofenceOperationStatus.Loading
        currentUserId?.let { userId ->
            firebaseRepository.saveGeofence(userId, geofence) { geofenceId, exception ->
                if (exception != null) {
                    _geofenceOperationStatus.value = GeofenceOperationStatus.Error(exception.message ?: "Falha ao atualizar área segura.")
                    Log.e(TAG1, "Erro ao atualizar geofence ${geofence.id}", exception)
                } else if (geofenceId != null) {
                    _geofenceOperationStatus.value = GeofenceOperationStatus.Success("Área segura '${geofence.name}' atualizada!", geofenceId)
                    loadUserGeofences() // Recarregar geofences após atualizar
                    
                    // Forçar verificação de status após atualização
                    Log.d(TAG1, "[updateGeofence] Forçando verificação de status após atualização")
                    forceGeofenceStatusCheck()
                } else {
                    _geofenceOperationStatus.value = GeofenceOperationStatus.Error("Falha desconhecida ao atualizar área segura.")
                    Log.e(TAG1, "Erro desconhecido ao atualizar geofence ${geofence.id}")
                }
            }
        } ?: run {
            _geofenceOperationStatus.value = GeofenceOperationStatus.Error("Usuário não logado. Não é possível atualizar a área segura.")
            Log.w(TAG1, "Tentativa de atualizar geofence sem usuário logado.")
        }
    }

    /**
     * Deleta uma geofence
     */
    fun deleteGeofence(geofenceId: String) {
        _geofenceOperationStatus.value = GeofenceOperationStatus.Loading
        currentUserId?.let { userId ->
            firebaseRepository.deleteGeofence(userId, geofenceId) { exception ->
                if (exception != null) {
                    _geofenceOperationStatus.value = GeofenceOperationStatus.Error(exception.message ?: "Falha ao deletar área segura.")
                    Log.e(TAG1, "Erro ao deletar geofence $geofenceId", exception)
                } else {
                    _geofenceOperationStatus.value = GeofenceOperationStatus.Success("Área segura deletada!")
                    loadUserGeofences() // Recarregar geofences após deletar
                }
            }
        } ?: run {
            _geofenceOperationStatus.value = GeofenceOperationStatus.Error("Usuário não logado. Não é possível deletar a área segura.")
            Log.w(TAG1, "Tentativa de deletar geofence sem usuário logado.")
        }
    }

    /**
     * Limpa o status de operação de geofence
     */
    fun clearGeofenceOperationStatus() {
        _geofenceOperationStatus.value = GeofenceOperationStatus.Idle
    }
    
    /**
     * Limpa o flag de recálculo forçado de geofences
     */
    fun clearForceGeofenceRecalculation() {
        _forceGeofenceRecalculation.value = false
    }

    /**
     * Verifica se o usuário atual pode gerenciar geofences (apenas responsáveis)
     */
    fun canManageGeofences(): Boolean {
        return _isResponsible.value
    }

    /**
     * Filtra geofences que estão ativas para o usuário atual
     */
    fun getActiveGeofencesForUser(): List<Geofence> {
        Log.d(TAG1, "[getActiveGeofencesForUser] === INÍCIO DO FILTRO ===")
        Log.d(TAG1, "[getActiveGeofencesForUser] Total de geofences carregadas: ${geofences.value.size}")
        Log.d(TAG1, "[getActiveGeofencesForUser] isResponsible: ${isResponsible.value}, currentUserId: $currentUserId")

        // Descobrir o dia da semana atual (em português)
        val diasSemana = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
        val cal = java.util.Calendar.getInstance()
        val idx = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 // Segunda=0, Terça=1, ..., Domingo=6
        val diaAtual = diasSemana[idx]
        Log.d(TAG1, "[getActiveGeofencesForUser] diaAtual=$diaAtual")
        
        // Verificar se o dia da semana mudou
        if (lastKnownDayOfWeek != null && lastKnownDayOfWeek != diaAtual) {
            Log.d(TAG1, "[getActiveGeofencesForUser] 🔄 Dia da semana mudou: $lastKnownDayOfWeek -> $diaAtual")
            _forceGeofenceRecalculation.value = true
        }
        lastKnownDayOfWeek = diaAtual
        val activeGeofences = geofences.value.filter { geofence ->
            Log.d(TAG1, "[getActiveGeofencesForUser] Geofence ${geofence.name}: activeDays=${geofence.activeDays}")
            
            val isActive = geofence.isActive
            val isResponsibleMatch = isResponsible.value && geofence.createdByUserId == currentUserId
            val isMemberMatch = !isResponsible.value && geofence.targetUserId == currentUserId
            
            // Para responsáveis: mostrar todas as geofences ativas que eles criaram (independente do dia)
            // Para membros: mostrar apenas geofences ativas para o dia atual
            val isDayMatch = if (isResponsible.value) {
                Log.d(TAG1, "[getActiveGeofencesForUser] Responsável: ignorando filtro de dia da semana")
                true // Responsáveis veem todas as geofences independente do dia
            } else {
                val dayMatch = geofence.activeDays.contains(diaAtual)
                Log.d(TAG1, "[getActiveGeofencesForUser] Membro: verificando se $diaAtual está em ${geofence.activeDays} = $dayMatch")
                dayMatch // Membros veem apenas geofences do dia atual
            }
            
            val result = isActive && (isResponsibleMatch || isMemberMatch) && isDayMatch
            
            Log.d(TAG1, "[getActiveGeofencesForUser] Geofence ${geofence.name}: isActive=$isActive, createdByUserId=${geofence.createdByUserId}, targetUserId=${geofence.targetUserId}, diaAtual=$diaAtual, activeDays=${geofence.activeDays}, result=$result")
            result
        }
        
        Log.d(TAG1, "[getActiveGeofencesForUser] Geofences ativas para o usuário: ${activeGeofences.size}")
        activeGeofences.forEach { geofence ->
            Log.d(TAG1, "[getActiveGeofencesForUser] ✅ Geofence ativa: ${geofence.name} (ID: ${geofence.id})")
        }
        
        // Log das geofences que foram filtradas
        val filteredOutGeofences = geofences.value.filter { geofence ->
            !activeGeofences.contains(geofence)
        }
        if (filteredOutGeofences.isNotEmpty()) {
            Log.d(TAG1, "[getActiveGeofencesForUser] ❌ Geofences filtradas (não ativas):")
            filteredOutGeofences.forEach { geofence ->
                val isActive = geofence.isActive
                val isResponsibleMatch = isResponsible.value && geofence.createdByUserId == currentUserId
                val isMemberMatch = !isResponsible.value && geofence.targetUserId == currentUserId
                val isDayMatch = geofence.activeDays.contains(diaAtual)
                
                Log.d(TAG1, "[getActiveGeofencesForUser]   - ${geofence.name}: isActive=$isActive, isResponsibleMatch=$isResponsibleMatch, isMemberMatch=$isMemberMatch, isDayMatch=$isDayMatch, activeDays=${geofence.activeDays}")
            }
        }
        
        Log.d(TAG1, "[getActiveGeofencesForUser] === FIM DO FILTRO ===")
        return activeGeofences
    }

    // Função utilitária para registrar notificação no Firestore
    private fun registrarNotificacao(
        tipoEvento: String, // "desvio", "saida", "volta"
        childId: String?,
        childName: String?,
        latitude: Double?,
        longitude: Double?,
        horario: String,
        titulo: String,
        body: String
    ) {
        val userId = currentUserId ?: return
        val notificacao = NotificationHistoryEntry(
            id = null,
            titulo = titulo,
            body = body,
            childId = childId,
            childName = childName,
            tipoEvento = tipoEvento,
            latitude = latitude,
            longitude = longitude,
            horarioEvento = horario,
            contagemTempo = System.currentTimeMillis(),
            lida = false
        )
        firebaseRepository.saveNotificationToHistory(userId, notificacao) { success, exception ->
            if (!success) {
                Log.e(TAG1, "Erro ao salvar notificação no Firestore: ${exception?.message}")
            } else {
                Log.d(TAG1, "Notificação salva no Firestore: $notificacao")
            }
        }
    }

    /**
     * Verifica o status de todas as geofences ativas para o usuário atual
     * LÓGICA: Sempre notifica saídas, independente se entra em outra geofence
     */
    private fun checkAllGeofencesStatus(location: Location) {
        Log.d(TAG1, "[checkAllGeofencesStatus] MÉTODO CHAMADO")
        // Não verificar geofences para responsáveis
        if (_isResponsible.value) {
            Log.d(TAG1, "checkAllGeofencesStatus: Usuário é responsável, não verifica geofences.")
            return
        }
        Log.d(TAG1, "[checkAllGeofencesStatus] === INÍCIO DA VERIFICAÇÃO ===")
        Log.d(TAG1, "[checkAllGeofencesStatus] Localização recebida: (${location.latitude}, ${location.longitude})")
        
        val activeGeofences = getActiveGeofencesForUser()
        Log.d(TAG1, "[checkAllGeofencesStatus] Geofences ativas encontradas: ${activeGeofences.size}")
        
        if (activeGeofences.isEmpty()) {
            Log.w(TAG1, "[checkAllGeofencesStatus] NENHUMA GEOFENCE ATIVA ENCONTRADA!")
            return
        }
        
        val currentStatusMap = _geofenceStatusMap.value.toMutableMap()
        val currentTime = System.currentTimeMillis()
        val previousPresentInAnyGeofence = _isPresentInAnyGeofence.value
        
        Log.d(TAG1, "[checkAllGeofencesStatus] Status atual das geofences: $currentStatusMap")
        Log.d(TAG1, "[checkAllGeofencesStatus] Presença anterior em alguma geofence: $previousPresentInAnyGeofence")
        
        // Verificar presença atual em todas as geofences
        var isCurrentlyInAnyGeofence = false
        var currentlyPresentGeofenceId: String? = null
        
        activeGeofences.forEach { geofence ->
            val geofenceId = geofence.id ?: return@forEach
            val isInside = geofenceHelperClass.isLocationInGeofence(location, geofence)
            val previousStatus = currentStatusMap[geofenceId]
            
            Log.d(TAG1, "[checkAllGeofencesStatus] === ANÁLISE GEOFENCE: ${geofence.name} ===")
            Log.d(TAG1, "[checkAllGeofencesStatus] Geofence ${geofence.name}: previousStatus=$previousStatus, isInside=$isInside")
            
            // Atualiza o status atual
                currentStatusMap[geofenceId] = isInside
            
            // Se está dentro desta geofence, marcar como presente
            if (isInside) {
                isCurrentlyInAnyGeofence = true
                currentlyPresentGeofenceId = geofenceId
                Log.d(TAG1, "[checkAllGeofencesStatus] ✅ Usuário está dentro da geofence: ${geofence.name}")
            }
                
            // Se é a primeira verificação, apenas inicializar sem notificação
            if (previousStatus == null) {
                Log.d(TAG1, "[checkAllGeofencesStatus] PRIMEIRA VERIFICAÇÃO para geofence ${geofence.name}: isInside=$isInside")
                return@forEach
            }
                
                // Verifica se houve mudança de status
                if (previousStatus != isInside) {
                    val notificationKey = "geofence_${geofenceId}_${if (isInside) "return" else "exit"}"
                val cooldownTime = if (isInside) 10000L else NOTIFICATION_COOLDOWN
                    
                    Log.d(TAG1, "[checkAllGeofencesStatus] 🔄 MUDANÇA DETECTADA: previousStatus=$previousStatus, isInside=$isInside")
                    
                    if (currentTime - (lastNotificationTime[notificationKey] ?: 0L) > cooldownTime) {
                        lastNotificationTime[notificationKey] = currentTime
                        
                    if (isInside) {
                        // Usuário entrou na geofence
                        Log.d(TAG1, "[checkAllGeofencesStatus] 🏠 Usuário entrou na geofence: ${geofence.name}")
                        onGeofenceReturn(geofence, location)
                    } else {
                        // Usuário saiu da geofence - SEMPRE NOTIFICAR
                        Log.d(TAG1, "[checkAllGeofencesStatus] 🚪 Usuário saiu da geofence: ${geofence.name} - NOTIFICANDO")
                            _showExitNotificationEvent.tryEmit(geofence.name)
                            onGeofenceExit(geofence, location)
                        }
                    } else {
                        Log.d(TAG1, "[checkAllGeofencesStatus] ⏰ Notificação ignorada devido ao cooldown para geofence: ${geofence.name}")
                    }
            }
        }
        
        // Atualizar status de presença global
        _isPresentInAnyGeofence.value = isCurrentlyInAnyGeofence
        _lastPresentGeofenceId.value = currentlyPresentGeofenceId
        
        // Verificar se saiu de todas as geofences (notificação global)
        if (!isCurrentlyInAnyGeofence) {
            exitAllGeofencesCounter++
            Log.d(TAG1, "[checkAllGeofencesStatus] Contador saída de todas as geofences: $exitAllGeofencesCounter")
            if (exitAllGeofencesCounter == EXIT_GLOBAL_THRESHOLD) {
                Log.d(TAG1, "[checkAllGeofencesStatus] 🚨 USUÁRIO SAIU DE TODAS AS GEOFENCES (após $EXIT_GLOBAL_THRESHOLD checagens consecutivas)!")
                onExitAllGeofences(location)
            }
        } else {
            if (exitAllGeofencesCounter != 0) Log.d(TAG1, "[checkAllGeofencesStatus] Usuário voltou para dentro de alguma geofence, contador zerado.")
            exitAllGeofencesCounter = 0
        }
        
        _geofenceStatusMap.value = currentStatusMap
        _isUserInsideGeofence.value = isCurrentlyInAnyGeofence
        
        Log.d(TAG1, "[checkAllGeofencesStatus] Presença atual em alguma geofence: $isCurrentlyInAnyGeofence")
        Log.d(TAG1, "[checkAllGeofencesStatus] Última geofence presente: $currentlyPresentGeofenceId")
        Log.d(TAG1, "[checkAllGeofencesStatus] === FIM DA VERIFICAÇÃO ===")
    }

    /**
     * Verifica o status de todas as rotas ativas para o usuário atual
     * LÓGICA: Sempre notifica saídas, independente se entra em outra rota
     */
    private fun checkAllRoutesStatus(location: Location) {
        Log.d(TAG1, "[checkAllRoutesStatus] MÉTODO CHAMADO")
        // Não verificar rotas para responsáveis
        if (_isResponsible.value) {
            Log.d(TAG1, "checkAllRoutesStatus: Usuário é responsável, não verifica rotas.")
            return
        }
        Log.d(TAG1, "[checkAllRoutesStatus] === INÍCIO DA VERIFICAÇÃO ===")
        
        val activeRoutes = getActiveRoutesForToday()
        Log.d(TAG1, "[checkAllRoutesStatus] Rotas ativas para hoje: ${activeRoutes.size}")
        
        if (activeRoutes.isEmpty()) {
            Log.w(TAG1, "[checkAllRoutesStatus] NENHUMA ROTA ATIVA ENCONTRADA!")
            return
        }
        
        val currentStatusMap = _routeStatusMap.value.toMutableMap()
        val currentTime = System.currentTimeMillis()
        val previousPresentInAnyRoute = _isPresentInAnyRoute.value
        
        Log.d(TAG1, "[checkAllRoutesStatus] Status atual das rotas: $currentStatusMap")
        Log.d(TAG1, "[checkAllRoutesStatus] Presença anterior em alguma rota: $previousPresentInAnyRoute")
        
        // Verificar presença atual em todas as rotas
        var isCurrentlyOnAnyRoute = false
        var currentlyPresentRouteId: String? = null
        
        activeRoutes.forEach { route ->
            val routeId = route.id ?: return@forEach
            val isOnRoute = routeHelper.isLocationOnRoute(location, route, forceCheck = true)
            val previousStatus = currentStatusMap[routeId]
            
            Log.d(TAG1, "[checkAllRoutesStatus] === ANÁLISE ROTA: ${route.name} ===")
            Log.d(TAG1, "[checkAllRoutesStatus] Rota ${route.name}: previousStatus=$previousStatus, isOnRoute=$isOnRoute")
            
                // Atualiza o status atual
                currentStatusMap[routeId] = isOnRoute
            
            // Se está na rota, marcar como presente
            if (isOnRoute) {
                isCurrentlyOnAnyRoute = true
                currentlyPresentRouteId = routeId
                Log.d(TAG1, "[checkAllRoutesStatus] ✅ Usuário está na rota: ${route.name}")
            }
            
            // Se é a primeira verificação, apenas inicializar sem notificação
            if (previousStatus == null) {
                Log.d(TAG1, "[checkAllRoutesStatus] PRIMEIRA VERIFICAÇÃO para rota ${route.name}: isOnRoute=$isOnRoute")
                return@forEach
            }
                
                // Verifica se houve mudança de status
                if (previousStatus != isOnRoute) {
                    val notificationKey = "route_${routeId}_${if (isOnRoute) "return" else "exit"}"
                val cooldownTime = if (isOnRoute) 10000L else NOTIFICATION_COOLDOWN
                
                Log.d(TAG1, "[checkAllRoutesStatus] 🔄 MUDANÇA DETECTADA: previousStatus=$previousStatus, isOnRoute=$isOnRoute")
                
                    if (currentTime - (lastNotificationTime[notificationKey] ?: 0L) > cooldownTime) {
                        lastNotificationTime[notificationKey] = currentTime
                    
                    if (isOnRoute) {
                        // Usuário entrou na rota
                        Log.d(TAG1, "[checkAllRoutesStatus] 🏠 Usuário entrou na rota: ${route.name}")
                        onRouteReturn(route, location)
                    } else {
                        // Usuário saiu da rota - SEMPRE NOTIFICAR
                        Log.d(TAG1, "[checkAllRoutesStatus] 🚪 Usuário saiu da rota: ${route.name} - NOTIFICANDO")
                            _showRouteExitNotificationEvent.tryEmit(route.name)
                            onRouteDeviation(route, location)
                        }
                    } else {
                    Log.d(TAG1, "[checkAllRoutesStatus] ⏰ Notificação ignorada devido ao cooldown para rota: ${route.name}")
                    }
            }
        }
        
        // Atualizar status de presença global
        _isPresentInAnyRoute.value = isCurrentlyOnAnyRoute
        _lastPresentRouteId.value = currentlyPresentRouteId
        
        // Verificar se saiu de todas as rotas (notificação global)
        if (!isCurrentlyOnAnyRoute) {
            exitAllRoutesCounter++
            Log.d(TAG1, "[checkAllRoutesStatus] Contador saída de todas as rotas: $exitAllRoutesCounter")
            if (exitAllRoutesCounter == EXIT_GLOBAL_THRESHOLD) {
                Log.d(TAG1, "[checkAllRoutesStatus] 🚨 USUÁRIO SAIU DE TODAS AS ROTAS (após $EXIT_GLOBAL_THRESHOLD checagens consecutivas)!")
                onExitAllRoutes(location)
            }
        } else {
            if (exitAllRoutesCounter != 0) Log.d(TAG1, "[checkAllRoutesStatus] Usuário voltou para dentro de alguma rota, contador zerado.")
            exitAllRoutesCounter = 0
        }
        
        _routeStatusMap.value = currentStatusMap
        _isLocationOnRoute.value = isCurrentlyOnAnyRoute
        
        Log.d(TAG1, "[checkAllRoutesStatus] Presença atual em alguma rota: $isCurrentlyOnAnyRoute")
        Log.d(TAG1, "[checkAllRoutesStatus] Última rota presente: $currentlyPresentRouteId")
        Log.d(TAG1, "[checkAllRoutesStatus] === FIM DA VERIFICAÇÃO ===")
    }

    /**
     * Obtém o nome do dependente atual
     */
    private fun getCurrentDependentName(): String? {
        val name = _currentUser.value?.name
        Log.d(TAG1, "[getCurrentDependentName] Nome do dependente: $name")
        return name
    }

    /**
     * Obtém o endereço da localização atual
     */
    private fun getLocationAddress(location: Location, callback: (String?) -> Unit) {
        geocodingService.getAddressFromLocation(location) { address ->
            callback(address)
        }
    }

    // 1. Saída de geofence
    private fun onGeofenceExit(geofence: Geofence, location: Location) {
        val childName = getCurrentDependentName()
        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        Log.d(TAG1, "[onGeofenceExit] Chamado para geofence: ${geofence.name}, userId: $currentUserId, childName: $childName, location: (${location.latitude}, ${location.longitude})")
        if (currentUserId == null) {
            Log.e(TAG1, "[onGeofenceExit] currentUserId está nulo! Não é possível salvar notificação.")
            return
        }
        getLocationAddress(location) { address ->
            val locationInfo = address ?: "Localização: ${location.latitude}, ${location.longitude}"
            val notificacao = NotificationHistoryEntry(
                id = null,
                titulo = "Saída de Área Segura",
                body = "${childName ?: "Dependente"} saiu da área segura '${geofence.name}' às $horario. $locationInfo",
                childId = currentUserId,
                childName = childName,
                tipoEvento = "saida_geofence",
                latitude = location.latitude,
                longitude = location.longitude,
                horarioEvento = horario,
                contagemTempo = System.currentTimeMillis(),
                lida = false
            )
            Log.d(TAG1, "[onGeofenceExit] Notificação criada: $notificacao")
            currentUserId?.let { userId ->
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "[onGeofenceExit] Erro ao salvar notificação de saída de geofence: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "[onGeofenceExit] Notificação de saída de geofence salva com sucesso para userId: $userId")
                        // Enviar push para responsável e dependente
                        firebaseRepository.getResponsibleForDependent(userId) { relationship, error ->
                            Log.d(TAG1, "[RELATIONSHIP] -> $relationship")
                            Log.d(TAG1, "[USER ID] -> $userId")
                            if (relationship != null) {
                                firebaseRepository.sendPushNotification(
                                    dependentId = userId,
                                    responsibleId = relationship.responsibleId,
                                    title = "Saída de Área Segura",
                                    body = "${childName ?: "Dependente"} saiu da área segura '${geofence.name}' às $horario. $locationInfo",
                                    data = mapOf(
                                        "eventType" to "geofence_exit",
                                        "geofenceName" to geofence.name,
                                        "latitude" to location.latitude.toString(),
                                        "longitude" to location.longitude.toString()
                                    )
                                ) { pushSuccess, pushError ->
                                    if (pushSuccess) {
                                        Log.d(TAG1, "[onGeofenceExit] Notificação push enviada com sucesso")
                                    } else {
                                        Log.e(TAG1, "[onGeofenceExit] Erro ao enviar notificação push: ${pushError?.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                Log.e(TAG1, "[onGeofenceExit] currentUserId nulo no momento de salvar notificação!")
            }
        }
    }
    
    // 2. Retorno à geofence
    private fun onGeofenceReturn(geofence: Geofence, location: Location) {
        val childName = getCurrentDependentName()
        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        Log.d(TAG1, "[onGeofenceReturn] Chamado para geofence: ${geofence.name}, userId: $currentUserId, childName: $childName, location: (${location.latitude}, ${location.longitude})")
        if (currentUserId == null) {
            Log.e(TAG1, "[onGeofenceReturn] currentUserId está nulo! Não é possível salvar notificação.")
            return
        }
        Log.d(TAG1, "[onGeofenceReturn] Iniciando obtenção de endereço...")
        getLocationAddress(location) { address ->
            val locationInfo = address ?: "Localização: ${location.latitude}, ${location.longitude}"
            Log.d(TAG1, "[onGeofenceReturn] Endereço obtido: $locationInfo")
            val notificacao = NotificationHistoryEntry(
                id = null,
                titulo = "Retorno à Área Segura",
                body = "${childName ?: "Dependente"} voltou para a área segura '${geofence.name}' às $horario. $locationInfo",
                childId = currentUserId,
                childName = childName,
                tipoEvento = "volta_geofence",
                latitude = location.latitude,
                longitude = location.longitude,
                horarioEvento = horario,
                contagemTempo = System.currentTimeMillis(),
                lida = false
            )
            Log.d(TAG1, "[onGeofenceReturn] Notificação criada: $notificacao")
            currentUserId?.let { userId ->
                Log.d(TAG1, "[onGeofenceReturn] Chamando saveNotificationToBothUsers para userId: $userId")
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "[onGeofenceReturn] Erro ao salvar notificação de retorno à geofence: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "[onGeofenceReturn] Notificação de retorno à geofence salva com sucesso para userId: $userId")
                        // Enviar push para responsável e dependente
                        firebaseRepository.getResponsibleForDependent(userId) { relationship, error ->
                            if (relationship != null) {
                                firebaseRepository.sendPushNotification(
                                    dependentId = userId,
                                    responsibleId = relationship.responsibleId,
                                    title = "Retorno à Área Segura",
                                    body = "${childName ?: "Dependente"} voltou para a área segura '${geofence.name}' às $horario. $locationInfo",
                                    data = mapOf(
                                        "eventType" to "geofence_return",
                                        "geofenceName" to geofence.name,
                                        "latitude" to location.latitude.toString(),
                                        "longitude" to location.longitude.toString()
                                    )
                                ) { pushSuccess, pushError ->
                                    if (pushSuccess) {
                                        Log.d(TAG1, "[onGeofenceReturn] Notificação push enviada com sucesso")
                                    } else {
                                        Log.e(TAG1, "[onGeofenceReturn] Erro ao enviar notificação push: ${pushError?.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                Log.e(TAG1, "[onGeofenceReturn] currentUserId nulo no momento de salvar notificação!")
            }
        }
    }
    
    // 3. Desvio de rota
    private fun onRouteDeviation(route: Route, location: Location) {
        val childName = getCurrentDependentName()
        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        Log.d(TAG1, "[onRouteDeviation] Chamado para rota: ${route.name}, userId: $currentUserId, childName: $childName, location: (${location.latitude}, ${location.longitude})")
        if (currentUserId == null) {
            Log.e(TAG1, "[onRouteDeviation] currentUserId está nulo! Não é possível salvar notificação.")
            return
        }
        getLocationAddress(location) { address ->
            val locationInfo = address ?: "Localização: ${location.latitude}, ${location.longitude}"
            val notificacao = NotificationHistoryEntry(
                id = null,
                titulo = "Desvio de Rota",
                body = "${childName ?: "Dependente"} saiu da rota '${route.name}' às $horario. $locationInfo",
                childId = currentUserId,
                childName = childName,
                tipoEvento = "saida_rota",
                latitude = location.latitude,
                longitude = location.longitude,
                horarioEvento = horario,
                contagemTempo = System.currentTimeMillis(),
                lida = false
            )
            Log.d(TAG1, "[onRouteDeviation] Notificação criada: $notificacao")
            currentUserId?.let { userId ->
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "[onRouteDeviation] Erro ao salvar notificação de desvio de rota: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "[onRouteDeviation] Notificação de desvio de rota salva com sucesso para userId: $userId")
                        // Enviar push para responsável e dependente
                        firebaseRepository.getResponsibleForDependent(userId) { relationship, error ->
                            if (relationship != null) {
                                firebaseRepository.sendPushNotification(
                                    dependentId = userId,
                                    responsibleId = relationship.responsibleId,
                                    title = "Desvio de Rota",
                                    body = "${childName ?: "Dependente"} saiu da rota '${route.name}' às $horario. $locationInfo",
                                    data = mapOf(
                                        "eventType" to "route_deviation",
                                        "routeName" to route.name,
                                        "latitude" to location.latitude.toString(),
                                        "longitude" to location.longitude.toString()
                                    )
                                ) { pushSuccess, pushError ->
                                    if (pushSuccess) {
                                        Log.d(TAG1, "[onRouteDeviation] Notificação push enviada com sucesso")
                                    } else {
                                        Log.e(TAG1, "[onRouteDeviation] Erro ao enviar notificação push: ${pushError?.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                Log.e(TAG1, "[onRouteDeviation] currentUserId nulo no momento de salvar notificação!")
            }
        }
    }

    // 5. Saída de todas as geofences (notificação global)
    private fun onExitAllGeofences(location: Location) {
        val childName = getCurrentDependentName()
        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        Log.d(TAG1, "[onExitAllGeofences] Chamado - userId: $currentUserId, childName: $childName, location: (${location.latitude}, ${location.longitude})")
        
        if (currentUserId == null) {
            Log.e(TAG1, "[onExitAllGeofences] currentUserId está nulo! Não é possível salvar notificação.")
            return
        }
        
        getLocationAddress(location) { address ->
            val locationInfo = address ?: "Localização: ${location.latitude}, ${location.longitude}"
            val notificacao = NotificationHistoryEntry(
                id = null,
                titulo = "Saída de Todas as Áreas Seguras",
                body = "${childName ?: "Dependente"} saiu de todas as áreas seguras às $horario. $locationInfo",
                childId = currentUserId,
                childName = childName,
                tipoEvento = "saida_todas_geofences",
                latitude = location.latitude,
                longitude = location.longitude,
                horarioEvento = horario,
                contagemTempo = System.currentTimeMillis(),
                lida = false
            )
            
            Log.d(TAG1, "[onExitAllGeofences] Notificação criada: $notificacao")
            currentUserId?.let { userId ->
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "[onExitAllGeofences] Erro ao salvar notificação: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "[onExitAllGeofences] Notificação salva com sucesso para userId: $userId")
                        // Enviar push para responsável e dependente
                        firebaseRepository.getResponsibleForDependent(userId) { relationship, error ->
                            if (relationship != null) {
                                firebaseRepository.sendPushNotification(
                                    dependentId = userId,
                                    responsibleId = relationship.responsibleId,
                                    title = "Saída de Todas as Áreas Seguras",
                                    body = "${childName ?: "Dependente"} saiu de todas as áreas seguras às $horario. $locationInfo",
                                    data = mapOf(
                                        "eventType" to "exit_all_geofences",
                                        "latitude" to location.latitude.toString(),
                                        "longitude" to location.longitude.toString()
                                    )
                                ) { pushSuccess, pushError ->
                                    if (pushSuccess) {
                                        Log.d(TAG1, "[onExitAllGeofences] Notificação push enviada com sucesso")
                                    } else {
                                        Log.e(TAG1, "[onExitAllGeofences] Erro ao enviar notificação push: ${pushError?.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                Log.e(TAG1, "[onExitAllGeofences] currentUserId nulo no momento de salvar notificação!")
            }
        }
    }
    
    // 6. Saída de todas as rotas (notificação global)
    private fun onExitAllRoutes(location: Location) {
        val childName = getCurrentDependentName()
        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        Log.d(TAG1, "[onExitAllRoutes] Chamado - userId: $currentUserId, childName: $childName, location: (${location.latitude}, ${location.longitude})")
        
        if (currentUserId == null) {
            Log.e(TAG1, "[onExitAllRoutes] currentUserId está nulo! Não é possível salvar notificação.")
            return
        }
        
        getLocationAddress(location) { address ->
            val locationInfo = address ?: "Localização: ${location.latitude}, ${location.longitude}"
            val notificacao = NotificationHistoryEntry(
                id = null,
                titulo = "Saída de Todas as Rotas",
                body = "${childName ?: "Dependente"} saiu de todas as rotas às $horario. $locationInfo",
                childId = currentUserId,
                childName = childName,
                tipoEvento = "saida_todas_rotas",
                latitude = location.latitude,
                longitude = location.longitude,
                horarioEvento = horario,
                contagemTempo = System.currentTimeMillis(),
                lida = false
            )
            
            Log.d(TAG1, "[onExitAllRoutes] Notificação criada: $notificacao")
            currentUserId?.let { userId ->
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "[onExitAllRoutes] Erro ao salvar notificação: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "[onExitAllRoutes] Notificação salva com sucesso para userId: $userId")
                        // Enviar push para responsável e dependente
                        firebaseRepository.getResponsibleForDependent(userId) { relationship, error ->
                            if (relationship != null) {
                                firebaseRepository.sendPushNotification(
                                    dependentId = userId,
                                    responsibleId = relationship.responsibleId,
                                    title = "Saída de Todas as Rotas",
                                    body = "${childName ?: "Dependente"} saiu de todas as rotas às $horario. $locationInfo",
                                    data = mapOf(
                                        "eventType" to "exit_all_routes",
                                        "latitude" to location.latitude.toString(),
                                        "longitude" to location.longitude.toString()
                                    )
                                ) { pushSuccess, pushError ->
                                    if (pushSuccess) {
                                        Log.d(TAG1, "[onExitAllRoutes] Notificação push enviada com sucesso")
                                    } else {
                                        Log.e(TAG1, "[onExitAllRoutes] Erro ao enviar notificação push: ${pushError?.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                Log.e(TAG1, "[onExitAllRoutes] currentUserId nulo no momento de salvar notificação!")
            }
        }
    }

    // 4. Retorno à rota
    private fun onRouteReturn(route: Route, location: Location) {
        val childName = getCurrentDependentName()
        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        Log.d(TAG1, "[onRouteReturn] Chamado para rota: ${route.name}, userId: $currentUserId, childName: $childName, location: (${location.latitude}, ${location.longitude})")
        if (currentUserId == null) {
            Log.e(TAG1, "[onRouteReturn] currentUserId está nulo! Não é possível salvar notificação.")
            return
        }
        Log.d(TAG1, "[onRouteReturn] Iniciando obtenção de endereço...")
        getLocationAddress(location) { address ->
            val locationInfo = address ?: "Localização: ${location.latitude}, ${location.longitude}"
            Log.d(TAG1, "[onRouteReturn] Endereço obtido: $locationInfo")
            val notificacao = NotificationHistoryEntry(
                id = null,
                titulo = "Retorno à Rota",
                body = "${childName ?: "Dependente"} voltou para a rota '${route.name}' às $horario. $locationInfo",
                childId = currentUserId,
                childName = childName,
                tipoEvento = "volta_rota",
                latitude = location.latitude,
                longitude = location.longitude,
                horarioEvento = horario,
                contagemTempo = System.currentTimeMillis(),
                lida = false
            )
            Log.d(TAG1, "[onRouteReturn] Notificação criada: $notificacao")
            currentUserId?.let { userId ->
                Log.d(TAG1, "[onRouteReturn] Chamando saveNotificationToBothUsers para userId: $userId")
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "[onRouteReturn] Erro ao salvar notificação de retorno à rota: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "[onRouteReturn] Notificação de retorno à rota salva com sucesso para userId: $userId")
                        // Enviar push para responsável e dependente
                        firebaseRepository.getResponsibleForDependent(userId) { relationship, error ->
                            if (relationship != null) {
                                firebaseRepository.sendPushNotification(
                                    dependentId = userId,
                                    responsibleId = relationship.responsibleId,
                                    title = "Retorno à Rota",
                                    body = "${childName ?: "Dependente"} voltou para a rota '${route.name}' às $horario. $locationInfo",
                                    data = mapOf(
                                        "eventType" to "route_return",
                                        "routeName" to route.name,
                                        "latitude" to location.latitude.toString(),
                                        "longitude" to location.longitude.toString()
                                    )
                                ) { pushSuccess, pushError ->
                                    if (pushSuccess) {
                                        Log.d(TAG1, "[onRouteReturn] Notificação push enviada com sucesso")
                                    } else {
                                        Log.e(TAG1, "[onRouteReturn] Erro ao enviar notificação push: ${pushError?.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: run {
                Log.e(TAG1, "[onRouteReturn] currentUserId nulo no momento de salvar notificação!")
            }
        }
    }

    /**
     * Função de teste para forçar criação de notificação de retorno à geofence
     */
    fun testGeofenceReturnNotification() {
        Log.d(TAG1, "[testGeofenceReturnNotification] Testando criação de notificação de retorno à geofence")
        currentUserId?.let { userId ->
            val testGeofence = Geofence(
                id = "test_geofence",
                name = "Geofence de Teste",
                coordinates = Coordinate(-23.550520, -46.633308),
                radius = 100f,
                isActive = true,
                targetUserId = userId,
                createdByUserId = userId
            )
            val testLocation = Location("test").apply {
                latitude = -23.550520
                longitude = -46.633308
            }
            onGeofenceReturn(testGeofence, testLocation)
        } ?: run {
            Log.e(TAG1, "[testGeofenceReturnNotification] currentUserId é nulo!")
        }
    }

    /**
     * Função de teste para forçar criação de notificação de retorno à rota
     */
    fun testRouteReturnNotification() {
        Log.d(TAG1, "[testRouteReturnNotification] Testando criação de notificação de retorno à rota")
        currentUserId?.let { userId ->
            val testRoute = Route(
                id = "test_route",
                name = "Rota de Teste",
                origin = RoutePoint(-23.550520, -46.633308),
                destination = RoutePoint(-23.550520, -46.633308),
                waypoints = emptyList(),
                encodedPolyline = "",
                routeColor = "#3F51B5",
                isActive = true,
                activeDays = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta"),
                targetUserId = userId,
                createdByUserId = userId
            )
            val testLocation = Location("test").apply {
                latitude = -23.550520
                longitude = -46.633308
            }
            onRouteReturn(testRoute, testLocation)
        } ?: run {
            Log.e(TAG1, "[testRouteReturnNotification] currentUserId é nulo!")
        }
    }

    /**
     * Função para limpar o cache de notificações e forçar nova verificação
     */
    fun clearNotificationCache() {
        Log.d(TAG1, "[clearNotificationCache] Limpando cache de notificações")
        lastNotificationTime.clear()
        _geofenceStatusMap.value = emptyMap()
        _routeStatusMap.value = emptyMap()
        Log.d(TAG1, "[clearNotificationCache] Cache limpo. Próxima verificação de localização irá redefinir todos os status")
        
        // Força uma nova verificação com a localização atual
        _currentLocation.value?.let { location ->
            Log.d(TAG1, "[clearNotificationCache] Forçando nova verificação com localização atual: (${location.latitude}, ${location.longitude})")
            checkAllGeofencesStatus(location)
            checkAllRoutesStatus(location)
            
            // Forçar verificação específica para geofences e rotas
            Log.d(TAG1, "[clearNotificationCache] Forçando verificação específica de geofences e rotas")
            forceGeofenceStatusCheck()
            forceRouteStatusCheck()
        } ?: run {
            Log.d(TAG1, "[clearNotificationCache] Nenhuma localização atual disponível")
        }
    }

    /**
     * Função para forçar verificação de status após atualização de geofence
     */
    private fun forceGeofenceStatusCheck() {
        Log.d(TAG1, "[forceGeofenceStatusCheck] Forçando verificação de status das geofences")
        _currentLocation.value?.let { location ->
            Log.d(TAG1, "[forceGeofenceStatusCheck] Verificando com localização: (${location.latitude}, ${location.longitude})")
            
            // Limpar cache de notificações para esta verificação específica
            val currentTime = System.currentTimeMillis()
            val activeGeofences = getActiveGeofencesForUser()
            val currentStatusMap = _geofenceStatusMap.value.toMutableMap()
            
            activeGeofences.forEach { geofence ->
                val geofenceId = geofence.id ?: return@forEach
                val isInside = geofenceHelperClass.isLocationInGeofence(location, geofence)
                val previousStatus = currentStatusMap[geofenceId]
                
                Log.d(TAG1, "[forceGeofenceStatusCheck] Geofence ${geofence.name}: previousStatus=$previousStatus, isInside=$isInside")
                
                // Se há mudança de status, gerar notificação imediatamente
                if (previousStatus != null && previousStatus != isInside) {
                    Log.d(TAG1, "[forceGeofenceStatusCheck] 🔄 Mudança detectada: $previousStatus -> $isInside")
                    
                    val notificationKey = "geofence_${geofenceId}_${if (isInside) "return" else "exit"}"
                    lastNotificationTime[notificationKey] = currentTime
                    
                    if (!isInside) {
                        Log.d(TAG1, "[forceGeofenceStatusCheck] 🚪 Usuário saiu da geofence: ${geofence.name}")
                        _showExitNotificationEvent.tryEmit(geofence.name)
                        onGeofenceExit(geofence, location)
                    } else {
                        Log.d(TAG1, "[forceGeofenceStatusCheck] 🏠 Usuário voltou para a geofence: ${geofence.name}")
                        onGeofenceReturn(geofence, location)
                    }
                }
                
                // Atualizar status
                currentStatusMap[geofenceId] = isInside
            }
            
            _geofenceStatusMap.value = currentStatusMap
            Log.d(TAG1, "[forceGeofenceStatusCheck] Status atualizado: $currentStatusMap")
        } ?: run {
            Log.w(TAG1, "[forceGeofenceStatusCheck] Nenhuma localização atual disponível")
        }
    }
    
    /**
     * Força recálculo das geofences ativas (útil para mudanças de dia da semana)
     */
    fun forceActiveGeofencesRecalculation() {
        Log.d(TAG1, "[forceActiveGeofencesRecalculation] Forçando recálculo das geofences ativas")
        _forceGeofenceRecalculation.value = true
    }
    
    /**
     * Inicia monitoramento de mudanças de dia da semana
     */
    private fun startDayOfWeekMonitoring() {
        viewModelScope.launch {
            while (currentUserId != null) {
                val diasSemana = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
                val cal = java.util.Calendar.getInstance()
                val idx = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                val currentDay = diasSemana[idx]
                
                if (lastKnownDayOfWeek != null && lastKnownDayOfWeek != currentDay) {
                    Log.d(TAG1, "[startDayOfWeekMonitoring] 🔄 Dia da semana mudou: $lastKnownDayOfWeek -> $currentDay")
                    _forceGeofenceRecalculation.value = true
                    lastKnownDayOfWeek = currentDay
                }
                
                // Verificar a cada hora
                kotlinx.coroutines.delay(3600000) // 1 hora em millisegundos
            }
        }
    }

    /**
     * Função para forçar verificação de status após atualização de rota
     */
    private fun forceRouteStatusCheck() {
        Log.d(TAG1, "[forceRouteStatusCheck] Forçando verificação de status das rotas")
        _currentLocation.value?.let { location ->
            Log.d(TAG1, "[forceRouteStatusCheck] Verificando com localização: (${location.latitude}, ${location.longitude})")
            
            // Limpar cache de notificações para esta verificação específica
            val currentTime = System.currentTimeMillis()
            val activeRoutes = getActiveRoutesForToday()
            val currentStatusMap = _routeStatusMap.value.toMutableMap()
            
            activeRoutes.forEach { route ->
                val routeId = route.id ?: return@forEach
                val isOnRoute = routeHelper.isLocationOnRoute(location, route, forceCheck = true)
                val previousStatus = currentStatusMap[routeId]
                
                Log.d(TAG1, "[forceRouteStatusCheck] Rota ${route.name}: previousStatus=$previousStatus, isOnRoute=$isOnRoute")
                
                // Se há mudança de status, gerar notificação imediatamente
                if (previousStatus != null && previousStatus != isOnRoute) {
                    Log.d(TAG1, "[forceRouteStatusCheck] 🔄 Mudança detectada: $previousStatus -> $isOnRoute")
                    
                    val notificationKey = "route_${routeId}_${if (isOnRoute) "return" else "exit"}"
                    lastNotificationTime[notificationKey] = currentTime
                    
                    if (!isOnRoute) {
                        Log.d(TAG1, "[forceRouteStatusCheck] 🚪 Usuário saiu da rota: ${route.name}")
                        _showRouteExitNotificationEvent.tryEmit(route.name)
                        onRouteDeviation(route, location)
                    } else {
                        Log.d(TAG1, "[forceRouteStatusCheck] 🏠 Usuário voltou para a rota: ${route.name}")
                        onRouteReturn(route, location)
                    }
                }
                
                // Atualizar status
                currentStatusMap[routeId] = isOnRoute
            }
            
            _routeStatusMap.value = currentStatusMap
            Log.d(TAG1, "[forceRouteStatusCheck] Status atualizado: $currentStatusMap")
        } ?: run {
            Log.w(TAG1, "[forceRouteStatusCheck] Nenhuma localização atual disponível")
        }
    }

    // Carrega localizações dos dependentes
    fun loadDependentsLocations(familyId: String) {
        if (!_isResponsible.value) return // Apenas responsáveis
        firebaseRepository.getDependentsLocations(familyId) { locations, exception ->
            if (exception != null) {
                Log.e(TAG1, "Erro ao carregar localizações dos dependentes", exception)
                return@getDependentsLocations
            }
            _dependentsLocations.value = locations ?: emptyMap()
            // Buscar informações dos dependentes
            loadDependentsInfo(familyId)
        }
    }

    // Carrega informações dos dependentes
    private fun loadDependentsInfo(familyId: String) {
        firebaseRepository.getFamilyDetails(familyId) { family, members, error ->
            if (error != null || members == null) {
                _dependentsInfo.value = emptyMap()
                return@getFamilyDetails
            }
            val dependentsMap = members.filter { it.type == "membro" && it.id != null }.associateBy { it.id!! }
            _dependentsInfo.value = dependentsMap
        }
    }

    // Inicia atualização periódica das localizações dos dependentes
    fun startDependentsLocationUpdates(familyId: String) {
        viewModelScope.launch {
            while (isLocationMonitoringActive && _isResponsible.value) {
                loadDependentsLocations(familyId)
                kotlinx.coroutines.delay(30000) // 30 segundos
            }
        }
    }
    
    /**
     * Função de teste para verificar a filtragem de geofences
     */
    fun testGeofenceFiltering() {
        Log.d(TAG1, "[testGeofenceFiltering] === TESTE DE FILTRAGEM DE GEOFENCES ===")
        Log.d(TAG1, "[testGeofenceFiltering] currentUserId: $currentUserId")
        Log.d(TAG1, "[testGeofenceFiltering] isResponsible: ${isResponsible.value}")
        
        val diasSemana = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
        val cal = java.util.Calendar.getInstance()
        val idx = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        val diaAtual = diasSemana[idx]
        Log.d(TAG1, "[testGeofenceFiltering] diaAtual: $diaAtual")
        
        Log.d(TAG1, "[testGeofenceFiltering] Total de geofences carregadas: ${geofences.value.size}")
        geofences.value.forEach { geofence ->
            val isActive = geofence.isActive
            val isResponsibleMatch = isResponsible.value && geofence.createdByUserId == currentUserId
            val isMemberMatch = !isResponsible.value && geofence.targetUserId == currentUserId
            val isDayMatch = geofence.activeDays.contains(diaAtual)
            val finalResult = isActive && (isResponsibleMatch || isMemberMatch) && isDayMatch
            
            Log.d(TAG1, "[testGeofenceFiltering] Geofence: ${geofence.name}")
            Log.d(TAG1, "[testGeofenceFiltering]   - isActive: $isActive")
            Log.d(TAG1, "[testGeofenceFiltering]   - createdByUserId: ${geofence.createdByUserId}")
            Log.d(TAG1, "[testGeofenceFiltering]   - targetUserId: ${geofence.targetUserId}")
            Log.d(TAG1, "[testGeofenceFiltering]   - activeDays: ${geofence.activeDays}")
            Log.d(TAG1, "[testGeofenceFiltering]   - isResponsibleMatch: $isResponsibleMatch")
            Log.d(TAG1, "[testGeofenceFiltering]   - isMemberMatch: $isMemberMatch")
            Log.d(TAG1, "[testGeofenceFiltering]   - isDayMatch: $isDayMatch")
            Log.d(TAG1, "[testGeofenceFiltering]   - RESULTADO FINAL: $finalResult")
        }
        
        val activeGeofences = getActiveGeofencesForUser()
        Log.d(TAG1, "[testGeofenceFiltering] Geofences ativas após filtro: ${activeGeofences.size}")
        Log.d(TAG1, "[testGeofenceFiltering] === FIM DO TESTE ===")
    }

    /**
     * Corrige geofences com problemas (ativa geofences inativas e adiciona dias ativos)
     */
    fun fixGeofences() {
        Log.d(TAG1, "[fixGeofences] === CORREÇÃO DE GEOFENCES ===")
        currentUserId?.let { userId ->
            val geofences = _geofences.value
            var fixedCount = 0
            var totalToFix = geofences.count { !it.isActive || it.activeDays.isEmpty() }
            
            if (totalToFix == 0) {
                Log.d(TAG1, "[fixGeofences] Nenhuma geofence precisa de correção")
                return
            }
            
            geofences.forEach { geofence ->
                var needsUpdate = false
                val updatedGeofence = geofence.copy()
                
                // Corrigir geofence inativa
                if (!geofence.isActive) {
                    Log.d(TAG1, "[fixGeofences] Ativando geofence inativa: ${geofence.name}")
                    updatedGeofence.isActive = true
                    needsUpdate = true
                }
                
                // Corrigir lista de dias ativos vazia
                if (geofence.activeDays.isEmpty()) {
                    Log.d(TAG1, "[fixGeofences] Adicionando dias ativos para geofence: ${geofence.name}")
                    updatedGeofence.activeDays = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
                    needsUpdate = true
                }
                
                // Salvar geofence corrigida
                if (needsUpdate) {
                    firebaseRepository.saveGeofence(userId, updatedGeofence) { geofenceId, exception ->
                        if (exception != null) {
                            Log.e(TAG1, "[fixGeofences] Erro ao corrigir geofence ${geofence.name}", exception)
                        } else {
                            Log.d(TAG1, "[fixGeofences] ✅ Geofence corrigida: ${geofence.name}")
                            fixedCount++
                            
                            // Recarregar geofences após todas as correções
                            if (fixedCount == totalToFix) {
                                Log.d(TAG1, "[fixGeofences] Recarregando geofences após correções")
                                loadUserGeofences()
                            }
                        }
                    }
                }
            }
        }
        Log.d(TAG1, "[fixGeofences] === FIM DA CORREÇÃO ===")
    }

}