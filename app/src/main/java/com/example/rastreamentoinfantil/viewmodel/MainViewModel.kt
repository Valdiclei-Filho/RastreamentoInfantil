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

    private var lastKnownRouteStatus: Boolean? = null
    private val _isLocationOnRoute = MutableStateFlow<Boolean?>(null)
    val isLocationOnRoute: StateFlow<Boolean?> = _isLocationOnRoute.asStateFlow()

    private val _showRouteExitNotificationEvent = MutableSharedFlow<String>()
    val showRouteExitNotificationEvent = _showRouteExitNotificationEvent.asSharedFlow()

    private val _routeDeviationEvent = MutableSharedFlow<RouteDeviation>()
    val routeDeviationEvent = _routeDeviationEvent.asSharedFlow()

    data class RouteDeviation(
        val routeId: String,
        val routeName: String,
        val currentLocation: Location,
        val lastKnownRoutePoint: RoutePoint?,
        val deviationDistance: Double
    )

    init {
        Log.d(TAG1, "Inicializando MainViewModel")
        startLocationMonitoring()
        
        // Monitora a localização e verifica automaticamente as rotas ativas
        viewModelScope.launch {
            currentLocation.collect { location ->
                if (location != null) {
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
            }
        }

        // Inicia o monitoramento de localização
        viewModelScope.launch {
            try {
                locationService.getLocationUpdates().collect { location ->
                    Log.d(TAG1, "Nova localização recebida: (${location.latitude}, ${location.longitude})")
                    _currentLocation.value = location
                }
            } catch (e: Exception) {
                Log.e(TAG1, "Erro ao obter atualizações de localização", e)
            }
        }

        // Monitoramento de rota
        viewModelScope.launch {
            currentLocation.combine(selectedRoute) { location, route ->
                Log.d(TAG1, "Verificando combinação - Localização: ${location != null}, Rota: ${route != null}")
                
                if (location != null && route != null) {
                    Log.d(TAG1, "Verificando localização na rota: ${route.name}")
                    Log.d(TAG1, "Localização atual: (${location.latitude}, ${location.longitude})")
                    Log.d(TAG1, "Rota selecionada: ${route.name}, ID: ${route.id}")
                    Log.d(TAG1, "Rota ativa: ${route.isActive}")
                    Log.d(TAG1, "Polyline da rota: ${route.encodedPolyline?.take(50)}...")
                    
                    if (!route.isActive) {
                        Log.d(TAG1, "Rota ${route.name} não está ativa, pulando verificação")
                        return@combine true
                    }
                    
                    val isOnRoute = routeHelper.isLocationOnRoute(location, route)
                    val lastKnownPoint = routeHelper.getLastKnownRoutePoint()
                    
                    Log.d(TAG1, "Status da verificação: ${if (isOnRoute) "Na rota" else "Fora da rota"}")
                    
                    if (!isOnRoute) {
                        // Calcula a distância do desvio
                        val deviationDistance = lastKnownPoint?.let { point ->
                            val results = FloatArray(1)
                            Location.distanceBetween(
                                location.latitude, location.longitude,
                                point.latitude, point.longitude,
                                results
                            )
                            results[0].toDouble()
                        } ?: 0.0

                        Log.d(TAG1, "Desvio detectado! Distância: $deviationDistance metros")
                        Log.d(TAG1, "Último ponto conhecido na rota: (${lastKnownPoint?.latitude}, ${lastKnownPoint?.longitude})")

                        // Emite evento de desvio
                        _routeDeviationEvent.emit(
                            RouteDeviation(
                                routeId = route.id ?: "",
                                routeName = route.name,
                                currentLocation = location,
                                lastKnownRoutePoint = lastKnownPoint,
                                deviationDistance = deviationDistance
                            )
                        )
                    }
                    
                    isOnRoute
                } else {
                    Log.d(TAG1, "Não é possível verificar rota: localização ou rota é nula")
                    Log.d(TAG1, "Localização: ${location != null}, Rota: ${route != null}")
                    null
                }
            }.collect { currentStatus ->
                Log.d(TAG1, "Status da rota atualizado: $currentStatus")
                _isLocationOnRoute.value = currentStatus

                if (lastKnownRouteStatus == true && currentStatus == false) {
                    val routeName = selectedRoute.value?.name ?: "Rota"
                    Log.d(TAG1, "Detectado desvio da rota: $routeName")
                    _showRouteExitNotificationEvent.emit(routeName)
                }
                lastKnownRouteStatus = currentStatus
            }
        }

        viewModelScope.launch {
            currentLocation
                .filterNotNull() // Só processa se a localização não for nula
                // .debounce(10000) // Opcional: processa apenas se a localização não mudar por X ms (ex: 10s)
                // .distinctUntilChanged { old, new -> old?.latitude == new?.latitude && old?.longitude == new?.longitude } // Opcional: só processa se a localização realmente mudou
                .collect { location ->
                    processAndSaveLocationRecord(location)
                }
        }

        loadUserIdAndGeofence() // Carregar usuário e geofence ao inicializar
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


    fun startLocationMonitoring() {
        Log.d(TAG1, "Iniciando monitoramento de localização")
        // Não precisamos mais chamar startLocationUpdates() pois já estamos usando getLocationUpdates()
    }

    fun stopLocationMonitoring() {
        Log.d(TAG1, "Parando monitoramento de localização")
        // Não precisamos mais chamar stopLocationUpdates() pois o Flow já é gerenciado pelo viewModelScope
    }

    private fun processAndSaveLocationRecord(location: Location) {
        // isLoading para esta operação específica, se necessário
        // _isLoading.value = true

        val activeGeofence = _geofenceArea.value
        val isCurrentlyInside = _isUserInsideGeofence.value
        val isDeviceOutsideGeofence =
            if (activeGeofence == null) false else (isCurrentlyInside == false)

        geocodingService.getAddressFromLocation(location) { address ->
            val locationRecord = LocationRecord(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address,
                dateTime = SimpleDateFormat(
                    "dd/MM/yyyy - HH:mm",
                    Locale.getDefault()
                ).format(Date()),
                isOutOfRoute = isDeviceOutsideGeofence // Renomeie o campo no LocationRecord se for sobre geofence
            )

            currentUserId?.let { userId ->
                firebaseRepository.saveLocationRecord(locationRecord, userId) { success, exception ->
                    if (success) {
                        loadLocationRecords() // Ou atualize a lista localmente
                    } else {
                        _error.postValue("Falha ao salvar localização!")
                    }
                    // _isLoading.postValue(false)
                }
            } ?: run {
                // _isLoading.postValue(false)
                _error.postValue("Não é possível salvar o registro de localização: ID do usuário ausente.")
            }
        }
    }

    // Refatorar handleLocationUpdate para usar _geofenceArea.value e o geofenceHelperClass injetado
    private fun handleLocationUpdate(location: Location) {
        _isLoading.value = true // Isso pode causar pisca-pisca na UI a cada atualização de localização
        // Considere usar isLoading para operações mais longas como salvar no Firebase.

        val activeGeofence = _geofenceArea.value // Usa a geofence ativa do StateFlow

        // O cálculo de estar dentro ou fora da geofence já é feito pelo flow `combine` que atualiza `_isUserInsideGeofence`
        // e `_isLocationOutOfGeofence`. Podemos usar esses valores.
        val isCurrentlyInside = _isUserInsideGeofence.value
        val isCurrentlyOutside = if (isCurrentlyInside == null) false else !isCurrentlyInside

        geocodingService.getAddressFromLocation(location) { address ->
            val locationRecord = LocationRecord(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address,
                dateTime = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault()).format(Date()),
                // Use o status calculado pelo flow `combine`
                // Se activeGeofence for null, isOutOfRoute pode ser considerado false ou true dependendo da sua lógica
                isOutOfRoute = if (activeGeofence == null) false else isCurrentlyOutside
            )

            currentUserId?.let { userId ->
                firebaseRepository.saveLocationRecord(locationRecord, userId) { success, exception ->
                    if (success) {
                        // Não precisa chamar loadLocationRecords() aqui a menos que você queira
                        // recarregar todos os registros a cada novo ponto salvo.
                        // Se você quiser apenas adicionar o novo registro à lista existente,
                        // pode atualizar _locationRecords localmente e depois sincronizar.
                        // Para simplificar por agora, manter o recarregamento está OK.
                        loadLocationRecords()
                    } else {
                        _error.postValue("Falha ao salvar localização!")
                    }
                    _isLoading.postValue(false) // Define isLoading como false após a tentativa de salvar
                }
            } ?: run {
                _isLoading.postValue(false) // Garante que isLoading seja definido como false se não houver ID de usuário
                _error.postValue("Não é possível salvar o registro de localização: ID do usuário ausente.")
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
        viewModelScope.launch {
            _isLoadingRoutes.value = true
            firebaseRepository.getUserRoutes(userId) { routesResult, exception ->
                _isLoadingRoutes.value = false
                if (exception != null) {
                    _routes.value = emptyList() // Mantenha como está
                    _error.value = "Falha ao carregar rotas: ${exception.message}"
                    Log.e(TAG, "Erro ao carregar rotas para o usuário $userId", exception)
                } else {
                    // Filtra rotas que possam ter ID nulo (embora não devesse acontecer)
                    _routes.value = routesResult?.filter { it.id != null } ?: emptyList()
                    Log.d(TAG, "Rotas carregadas para o usuário $userId: ${_routes.value.size} rotas válidas")
                }
            }
        }
    }
    fun addOrUpdateRoute(route: Route) {
        currentUserId?.let { userId ->
            viewModelScope.launch {
                _routeOperationStatus.value = RouteOperationStatus.Loading
                firebaseRepository.saveRoute(userId, route) { routeId, exception -> // routeId é String?, exception é Exception?
                    Log.d(TAG, "Usuario id desgracado: $userId")
                    if (exception != null) {
                        _routeOperationStatus.value = RouteOperationStatus.Error(exception.message ?: "Falha ao salvar rota.")
                        Log.e(TAG, "Erro ao salvar rota ${route.id} para o usuário $userId", exception)
                    } else if (routeId != null) { // Sucesso se routeId não for nulo
                        _routeOperationStatus.value = RouteOperationStatus.Success("Rota salva com sucesso!")
                        loadUserRoutes(userId) // Recarregar rotas após salvar
                    } else {
                        // Caso inesperado: exception é null mas routeId também é null (não deveria acontecer com a lógica atual do repo)
                        _routeOperationStatus.value = RouteOperationStatus.Error("Falha desconhecida ao salvar rota.")
                        Log.e(TAG, "Erro desconhecido ao salvar rota ${route.id} para o usuário $userId")
                    }
                }
            }
        } ?: run {
            _routeOperationStatus.value = RouteOperationStatus.Error("Usuário não logado. Não é possível salvar a rota.")
            Log.w(TAG, "Tentativa de salvar rota sem usuário logado.")
        }
    }
    fun deleteRoute(routeId: String) {
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
        Log.d(TAG1, "Carregando detalhes da rota: $routeId")
        viewModelScope.launch {
            try {
                val route = firebaseRepository.getRouteById(routeId)
                if (route != null) {
                    Log.d(TAG1, "Rota carregada com sucesso: ${route.name}")
                    Log.d(TAG1, "Rota ativa: ${route.isActive}")
                    Log.d(TAG1, "Polyline da rota: ${route.encodedPolyline?.take(50)}...")
                    _selectedRoute.value = route
                } else {
                    Log.e(TAG1, "Rota não encontrada: $routeId")
                }
            } catch (e: Exception) {
                Log.e(TAG1, "Erro ao carregar rota: $routeId", e)
            }
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
        isActive: Boolean = true
    ) {
        currentUserId?.let { userId ->
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
        _isLoadingRoutes.value = false
        _currentLocation.value = null
        _geofenceArea.value = null
        _isUserInsideGeofence.value = null
    }

}