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
    private val _geofenceArea = MutableStateFlow<Geofence?>(null)
    val geofenceArea: StateFlow<Geofence?> = _geofenceArea.asStateFlow()

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
    // Usaremos _geofenceArea.value como a fonte da verdade para a geofence ativa
    private var currentUserId: String? = null
    private var isLocationMonitoringActive: Boolean = false // Flag para controlar se o monitoramento está ativo

    private var lastKnownRouteStatus: Boolean? = null
    private val _isLocationOnRoute = MutableStateFlow<Boolean?>(null)
    val isLocationOnRoute: StateFlow<Boolean?> = _isLocationOnRoute.asStateFlow()

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

    data class RouteDeviation(
        val routeId: String,
        val routeName: String,
        val currentLocation: Location,
        val lastKnownRoutePoint: RoutePoint?,
        val deviationDistance: Double
    )

    init {
        Log.d(TAG1, "Inicializando MainViewModel")
        
        // Não inicia monitoramento automaticamente no init
        // Será iniciado explicitamente pela MainActivity quando necessário
    }

    /**
     * Inicializa o currentUserId quando o usuário faz login
     */
    fun initializeUser() {
        val currentUser = firebaseRepository.getCurrentUser()
        if (currentUser != null) {
            currentUserId = currentUser.uid
            Log.d(TAG1, "Usuário inicializado: $currentUserId")
            loadUserIdAndGeofence()
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
            if (routeHelper.isLocationOnRoute(location, route)) {
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
            currentUserId = currentUser.uid
            // Agora, loadUserGeofence irá popular _geofenceArea
            loadUserGeofence(currentUserId!!)
        } else {
            _isLoading.value = false
            _error.value = "Usuário não encontrado!"
            _geofenceArea.value = null // Garante que nenhuma geofence antiga persista se o usuário deslogar
            println("Nenhum usuário logado, nenhuma geofence será carregada do Firebase.")
        }
    }

    private fun loadUserGeofence(userId: String) {
        firebaseRepository.getUserActiveGeofence(userId) { geofenceFromFirebase, exception -> // DOIS parâmetros
            if (exception != null) {
                _error.value = "Erro ao carregar geofence: ${exception.message}"
                _isLoading.value = false // Certifique-se de parar o loading
                return@getUserActiveGeofence
            }

            // Se exception for null, geofenceFromFirebase pode ser a geofence ou null se não encontrada
            _geofenceArea.value = geofenceFromFirebase
            _isLoading.value = false
            if (geofenceFromFirebase != null) {
                println("Geofence carregada: $geofenceFromFirebase")
            } else {
                println("Nenhuma geofence encontrada.")
            }
            loadLocationRecords()
        }
    }

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

    // Função para permitir que a UI defina/atualize uma geofence
    // Esta geofence deve então ser salva no Firebase para o usuário atual
    fun updateUserGeofence(newGeofence: Geofence?) {
        _geofenceArea.value = newGeofence // Atualiza imediatamente o StateFlow
        currentUserId?.let { userId ->
            if (newGeofence != null) {
                _isLoading.value = true
                firebaseRepository.saveUserActiveGeofence(userId, newGeofence) { success, exception -> // DOIS parâmetros
                    _isLoading.value = false
                    if (success) {
                        println("Geofence salva no Firebase para o usuário $userId.")
                    } else {
                        _error.value = "Falha ao salvar geofence: ${exception?.message ?: "Erro desconhecido"}"
                    }
                }
            } else { // Se newGeofence for null, significa que estamos limpando a geofence
                _isLoading.value = true
                firebaseRepository.deleteUserActiveGeofence(userId) { success, exception ->
                    _isLoading.value = false
                    if (success) {
                        println("Geofence removida do Firebase para o usuário $userId.")
                    } else {
                        _error.value = "Falha ao remover geofence no Firebase."
                    }
                }
            }
        } ?: run {
            _error.value = "ID do usuário não disponível para salvar/remover geofence."
            if (newGeofence != null) {
                // Se não houver usuário, mas uma geofence foi definida (ex: modo offline ou antes do login)
                // você pode querer salvá-la localmente usando SharedPreferences como um fallback ou cache temporário.
                // SharedPreferencesHelper.saveGeofence(getApplication(), newGeofence)
                println("Nenhum usuário logado. A geofence definida não foi salva no Firebase.")
            }
        }
    }

    fun retrieveAndSaveFcmToken(userId: String) {
        Firebase.messaging.token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Obter novo token de registro FCM
            val token = task.result
            Log.d(TAG, "FCM Token: $token")

            // Salve este token no Firestore associado ao userId do pai
            // Exemplo (você precisará da sua implementação do FirebaseRepository):
            // firebaseRepository.saveUserFcmToken(userId, token) { success ->
            //     if (success) {
            //         Log.d(TAG, "FCM token saved to Firestore for user $userId")
            //     } else {
            //         Log.w(TAG, "Failed to save FCM token for user $userId")
            //     }
            // }
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
                _routes.value = routes ?: emptyList()
                Log.d(TAG1, "[loadUserRoutes] Rotas carregadas: ${routes?.size ?: 0}")
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
        targetUserId: String? = null
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
                    id = UUID.randomUUID().toString(), // Gere um ID único para a nova rota
                    name = name,
                    origin = originPoint,
                    destination = destinationPoint,
                    waypoints = waypointsList,
                    encodedPolyline = encodedPolylineString, // ARMAZENA A POLYLINE CODIFICADA
                    isActive = isActive,
                    routeColor = routeColor,
                    activeDays = activeDays,
                    targetUserId = targetUserId,
                    createdByUserId = userId
                    // createdAt e updatedAt serão definidos pelo @ServerTimestamp no Firestore
                )

                // Agora, chame a função existente para salvar (que antes era addOrUpdate)
                // Se sua função `saveRoute` no repositório lida com a atribuição de ID do Firestore,
                // você pode passar `id = null` aqui e deixar o repo preencher.
                // Mas para consistência, gerar um ID aqui e usá-lo é mais simples.
                firebaseRepository.saveRoute(userId, newRoute) { routeIdFromSave, exception ->
                    if (exception != null) {
                        _routeOperationStatus.value = RouteOperationStatus.Error(exception.message ?: "Falha ao salvar rota.")
                        Log.e(TAG1, "Erro ao salvar nova rota ${newRoute.id} para o usuário $userId", exception)
                    } else if (routeIdFromSave != null) { // Sucesso
                        _routeOperationStatus.value = RouteOperationStatus.Success("Rota '${newRoute.name}' criada e salva!", routeIdFromSave)
                        loadUserRoutes(userId) // Recarregar rotas após salvar
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
        _geofenceArea.value = null
        _isUserInsideGeofence.value = null
        _isLocationOutOfGeofence.value = false
        _isLocationOnRoute.value = null
        
        // Limpar dados de rotas
        _routes.value = emptyList()
        _selectedRoute.value = null
        _isLoadingRoutes.value = false
        _routeOperationStatus.value = RouteOperationStatus.Idle
        
        // Limpar dados de geofence (usando _geofenceArea que é a variável correta)
        _geofenceArea.value = null
        
        // Limpar registros de localização
        _locationRecords.value = emptyList()
        
        // Limpar dados de usuário e família
        currentUserId = null
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
        currentUserId = null
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
            currentUserId = firebaseUser.uid
            Log.d(TAG1, "[syncCurrentUser] Usuário sincronizado: ${firebaseUser.uid}, email: ${firebaseUser.email}")
            // Carregar dados do usuário e família após sincronizar
            loadCurrentUserData()
            // Carregar rotas após determinar o tipo de usuário
            loadUserRoutes(firebaseUser.uid)
            // Iniciar monitoramento de localização
            startLocationMonitoring()
        } else {
            currentUserId = null
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
        
        Log.d(TAG1, "[getActiveRoutesForToday] Dia atual: $dayName, Total de rotas: ${routes.value.size}")
        
        val activeRoutes = routes.value.filter { route ->
            route.isActive && route.activeDays.contains(dayName)
        }
        
        Log.d(TAG1, "[getActiveRoutesForToday] Rotas ativas para hoje: ${activeRoutes.size}")
        return activeRoutes
    }

    // ==================== FUNÇÕES DE GEOFENCE ====================

    /**
     * Carrega geofences do usuário atual
     */
    fun loadUserGeofences() {
        currentUserId?.let { userId ->
            _isLoadingGeofences.value = true
            firebaseRepository.getUserGeofences(userId) { geofences, error ->
                _isLoadingGeofences.value = false
                if (error != null) {
                    Log.e(TAG1, "Erro ao carregar geofences", error)
                    _error.value = "Erro ao carregar áreas seguras: ${error.message}"
                } else {
                    _geofences.value = geofences ?: emptyList()
                    Log.d(TAG1, "Geofences carregadas: ${geofences?.size ?: 0}")
                }
            }
        }
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
     * Verifica se o usuário atual pode gerenciar geofences (apenas responsáveis)
     */
    fun canManageGeofences(): Boolean {
        return _isResponsible.value
    }

    /**
     * Filtra geofences que estão ativas para o usuário atual
     */
    fun getActiveGeofencesForUser(): List<Geofence> {
        return geofences.value.filter { geofence ->
            geofence.isActive &&
            (
                // Responsáveis veem todas as geofences que criaram
                (isResponsible.value && geofence.createdByUserId == currentUserId) ||
                // Membros veem apenas geofences onde são o targetUserId
                (!isResponsible.value && geofence.targetUserId == currentUserId)
            )
        }
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
     */
    private fun checkAllGeofencesStatus(location: Location) {
        val activeGeofences = getActiveGeofencesForUser()
        val currentStatusMap = _geofenceStatusMap.value.toMutableMap()
        val currentTime = System.currentTimeMillis()
        
        activeGeofences.forEach { geofence ->
            val geofenceId = geofence.id ?: return@forEach
            val isInside = geofenceHelperClass.isLocationInGeofence(location, geofence)
            val previousStatus = currentStatusMap[geofenceId]
            
            // Atualiza o status atual
            currentStatusMap[geofenceId] = isInside
            
            // Verifica se houve mudança de status
            if (previousStatus != null && previousStatus != isInside) {
                val notificationKey = "geofence_${geofenceId}_${if (isInside) "return" else "exit"}"
                
                // Verifica se já foi notificado recentemente
                if (currentTime - (lastNotificationTime[notificationKey] ?: 0L) > NOTIFICATION_COOLDOWN) {
                    lastNotificationTime[notificationKey] = currentTime
                    
                    if (!isInside) {
                        // Usuário saiu da geofence
                        Log.d(TAG1, "Usuário saiu da geofence: ${geofence.name}")
                        _showExitNotificationEvent.tryEmit(geofence.name)
                        onGeofenceExit(geofence, location)
                    } else {
                        // Usuário voltou para a geofence
                        Log.d(TAG1, "Usuário voltou para a geofence: ${geofence.name}")
                        onGeofenceReturn(geofence, location)
                    }
                }
            }
        }
        
        _geofenceStatusMap.value = currentStatusMap
        
        // Atualiza o status geral (se está dentro de alguma geofence)
        val isInsideAnyGeofence = currentStatusMap.values.any { it }
        _isUserInsideGeofence.value = isInsideAnyGeofence
    }

    /**
     * Verifica o status de todas as rotas ativas para o usuário atual
     */
    private fun checkAllRoutesStatus(location: Location) {
        val activeRoutes = getActiveRoutesForToday()
        Log.d(TAG1, "[checkAllRoutesStatus] Rotas ativas para hoje: ${activeRoutes.size}")
        
        val currentStatusMap = _routeStatusMap.value.toMutableMap()
        val currentTime = System.currentTimeMillis()
        
        activeRoutes.forEach { route ->
            val routeId = route.id ?: return@forEach
            val isOnRoute = routeHelper.isLocationOnRoute(location, route)
            val previousStatus = currentStatusMap[routeId]
            
            // Atualiza o status atual
            currentStatusMap[routeId] = isOnRoute
            
            // Verifica se houve mudança de status
            if (previousStatus != null && previousStatus != isOnRoute) {
                val notificationKey = "route_${routeId}_${if (isOnRoute) "return" else "exit"}"
                
                // Verifica se já foi notificado recentemente
                if (currentTime - (lastNotificationTime[notificationKey] ?: 0L) > NOTIFICATION_COOLDOWN) {
                    lastNotificationTime[notificationKey] = currentTime
                    
                    if (!isOnRoute) {
                        // Usuário saiu da rota
                        Log.d(TAG1, "[checkAllRoutesStatus] Usuário saiu da rota: ${route.name}")
                        _showRouteExitNotificationEvent.tryEmit(route.name)
                        onRouteDeviation(route, location)
                    } else {
                        // Usuário voltou para a rota
                        Log.d(TAG1, "[checkAllRoutesStatus] Usuário voltou para a rota: ${route.name}")
                        onRouteReturn(route, location)
                    }
                } else {
                    Log.d(TAG1, "[checkAllRoutesStatus] Notificação ignorada devido ao cooldown para rota: ${route.name}")
                }
            }
        }
        
        _routeStatusMap.value = currentStatusMap
        
        // Atualiza o status geral (se está em alguma rota)
        val isOnAnyRoute = currentStatusMap.values.any { it }
        _isLocationOnRoute.value = isOnAnyRoute
    }

    /**
     * Obtém o nome do dependente atual
     */
    private fun getCurrentDependentName(): String? {
        return _currentUser.value?.name
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
            
            // Salva para dependente e responsável
            currentUserId?.let { userId ->
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "Erro ao salvar notificação de saída de geofence: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "Notificação de saída de geofence salva com sucesso")
                    }
                }
            }
        }
    }
    
    // 2. Retorno à geofence
    private fun onGeofenceReturn(geofence: Geofence, location: Location) {
        val childName = getCurrentDependentName()
        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        
        getLocationAddress(location) { address ->
            val locationInfo = address ?: "Localização: ${location.latitude}, ${location.longitude}"
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
            
            // Salva para dependente e responsável
            currentUserId?.let { userId ->
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "Erro ao salvar notificação de retorno à geofence: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "Notificação de retorno à geofence salva com sucesso")
                    }
                }
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
            // Salva para dependente e responsável
            currentUserId?.let { userId ->
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "[onRouteDeviation] Erro ao salvar notificação de desvio de rota: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "[onRouteDeviation] Notificação de desvio de rota salva com sucesso para userId: $userId")
                    }
                }
            } ?: run {
                Log.e(TAG1, "[onRouteDeviation] currentUserId nulo no momento de salvar notificação!")
            }
        }
    }

    // 4. Retorno à rota
    private fun onRouteReturn(route: Route, location: Location) {
        val childName = getCurrentDependentName()
        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
        
        getLocationAddress(location) { address ->
            val locationInfo = address ?: "Localização: ${location.latitude}, ${location.longitude}"
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
            
            // Salva para dependente e responsável
            currentUserId?.let { userId ->
                firebaseRepository.saveNotificationToBothUsers(userId, notificacao) { success, exception ->
                    if (!success) {
                        Log.e(TAG1, "Erro ao salvar notificação de retorno à rota: ${exception?.message}")
                    } else {
                        Log.d(TAG1, "Notificação de retorno à rota salva com sucesso")
                    }
                }
            }
        }
    }

}