package com.example.rastreamentoinfantil.viewmodel

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rastreamentoinfantil.model.Geofence // Sua classe Geofence personalizada
import com.example.rastreamentoinfantil.model.LocationRecord
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.service.GeocodingService
import com.example.rastreamentoinfantil.helper.GeofenceHelper
import com.example.rastreamentoinfantil.service.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.rastreamentoinfantil.model.Coordinate // Não se esqueça do import


class MainViewModel(
    private val firebaseRepository: FirebaseRepository,
    private val locationService: LocationService,
    private val geocodingService: GeocodingService,
) : ViewModel() {

    private val _currentLocation = MutableStateFlow<android.location.Location?>(null)
    val currentLocation: StateFlow<android.location.Location?> = _currentLocation

    private val _geofenceArea = MutableStateFlow<Geofence?>(null)
    val geofenceArea: StateFlow<Geofence?> = _geofenceArea

    private val _locationRecords = MutableLiveData<List<LocationRecord>>()
    val locationRecords: LiveData<List<LocationRecord>> get() = _locationRecords

    private val _isLocationOutOfRoute = MutableLiveData<Boolean>()
    val isLocationOutOfRoute: LiveData<Boolean> get() = _isLocationOutOfRoute

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private var currentGeofence: com.example.rastreamentoinfantil.model.Geofence? = null // Inicializar como null
    private var currentUserId: String? = null

    init {
        viewModelScope.launch {
            locationService.getLocationUpdates().collect { location ->
                _currentLocation.value = location
            }
        }


        // Inicializa a geofence com dados de exemplo
        // No futuro, você pode carregar isso de uma fonte de dados (Firebase, etc.)
        _geofenceArea.value = Geofence(
            id = "escola_principal",
            coordinates = Coordinate(latitude = 23.551234, longitude = 46.634567),
            radius = 200.00 // Exemplo: Raio de 200 metros
        )

        loadUserIdAndGeofence() // Carregar usuário e geofence ao inicializar
    }

    private fun loadUserIdAndGeofence() {
        _isLoading.value = true
        val currentUser = firebaseRepository.getCurrentUser()
        if (currentUser != null) {
            currentUserId = currentUser.uid
            loadUserGeofence(currentUserId!!) // Carregar geofence após obter o ID do usuário
        } else {
            _isLoading.value = false
            _error.value = "Usuário não encontrado!"
        }
    }

    private fun loadUserGeofence(userId: String) {
        firebaseRepository.getUserGeofence(userId) { geofence ->
            currentGeofence = geofence
            _isLoading.value = false
            loadLocationRecords() // Carregar registros de localização após carregar a geofence
        }
    }

    private fun loadLocationRecords() {
        currentUserId?.let { userId ->
            _isLoading.value = true
            firebaseRepository.getUserLocationRecords(userId) { records ->
                _locationRecords.postValue(records)
                _isLoading.value = false
            }
        }
    }

    fun startLocationMonitoring() {
        locationService.startLocationUpdates { location ->
            handleLocationUpdate(location)
        }
    }

    fun stopLocationMonitoring() {
        locationService.stopLocationUpdates()
    }

    private fun handleLocationUpdate(location: Location) {
        _isLoading.value = true
        geocodingService.getAddressFromLocation(location) { address ->
            // Usar a currentGeofence carregada
            val isOutOfRoute = GeofenceHelper().isLocationInGeofence(location, currentGeofence ?: com.example.rastreamentoinfantil.model.Geofence(radius = 200.00, coordinates = Coordinate(latitude = 23.551234, longitude = 46.634567))) // Fornecer um padrão se currentGeofence for nulo

            _isLocationOutOfRoute.postValue(isOutOfRoute)

            val locationRecord = LocationRecord(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address,
                dateTime = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault()).format(Date()),
                isOutOfRoute = isOutOfRoute
            )
            currentUserId?.let{
                firebaseRepository.saveLocationRecord(locationRecord, it) { success ->
                    if (success) {
                        loadLocationRecords() // Recarregar registros após salvar
                    } else {
                        _error.postValue("Falha ao salvar localizacao!")
                    }
                    _isLoading.postValue(false)
                }
            }
        }
    }
}