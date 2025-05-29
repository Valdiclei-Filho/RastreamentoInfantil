package com.example.rastreamentoinfantil.viewmodel

import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import android.app.Application
import android.location.Location
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

class MainViewModel(
    application: Application,
    private val firebaseRepository: FirebaseRepository,
    private val locationService: LocationService,
    private val geocodingService: GeocodingService,
    private val geofenceHelperClass: GeofenceHelper // Injete o GeofenceHelper
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FirebaseRepository" // Definição do TAG aqui
    }

    private val _directionsPolyline = MutableStateFlow<List<LatLng>>(emptyList())
    val directionsPolyline: StateFlow<List<LatLng>> = _directionsPolyline.asStateFlow()

    private val _showExitNotificationEvent = MutableSharedFlow<String>()
    val showExitNotificationEvent = _showExitNotificationEvent.asSharedFlow()

    private var lastKnownGeofenceStatus: Boolean? = null

    private val _currentLocation = MutableStateFlow<android.location.Location?>(null)
    val currentLocation: StateFlow<android.location.Location?> = _currentLocation.asStateFlow()

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
        data class Success(val message: String) : RouteOperationStatus
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

    init {
        // A geofence será carregada via loadUserIdAndGeofence()
        // Não precisamos mais do loadSavedGeofence() daqui (que era para SharedPreferences)
        // nem do if (_geofenceArea.value == null) para definir uma padrão aqui,
        // pois o carregamento do Firebase cuidará disso.

        viewModelScope.launch {
            locationService.getLocationUpdates().collect { location ->
                _currentLocation.value = location
            }
        }

        viewModelScope.launch {
            currentLocation.combine(geofenceArea) { location, geofence -> // geofenceArea é o StateFlow
                if (location != null && geofence != null) {
                    geofenceHelperClass.isLocationInGeofence(location, geofence)
                } else {
                    null
                }
            }.collect { currentStatus ->
                _isUserInsideGeofence.value = currentStatus
                _isLocationOutOfGeofence.postValue(currentStatus == false) // Atualiza o LiveData

                if (lastKnownGeofenceStatus == true && currentStatus == false) {
                    val geofenceId = geofenceArea.value?.id ?: "Área Segura"
                    _showExitNotificationEvent.emit(geofenceId)
                }
                lastKnownGeofenceStatus = currentStatus
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
        locationService.startLocationUpdates { location -> // Esta lambda de startLocationUpdates parece redundante
            // se você já coleta de locationService.getLocationUpdates()
            // Considere ter apenas um mecanismo de coleta.
            handleLocationUpdate(location)
        }
        // Se locationService.getLocationUpdates() já emite para _currentLocation.value,
        // e _currentLocation.value já está sendo combinado com _geofenceArea.value,
        // então handleLocationUpdate pode não ser necessário da forma como está,
        // ou sua lógica precisa ser integrada ao fluxo de `combine`.
    }

    fun stopLocationMonitoring() {
        locationService.stopLocationUpdates()
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

    fun clearRouteOperationStatus() {
        _routeOperationStatus.value = RouteOperationStatus.Idle
    }
}