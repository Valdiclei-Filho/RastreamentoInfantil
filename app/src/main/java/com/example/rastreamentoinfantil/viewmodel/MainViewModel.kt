package com.example.rastreamentoinfantil.viewmodel

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.rastreamentoinfantil.model.Geofence
import com.example.rastreamentoinfantil.model.LocationRecord
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.service.GeocodingService
import com.example.rastreamentoinfantil.helper.GeofenceHelper
import com.example.rastreamentoinfantil.service.LocationService


class MainViewModel(
    private val firebaseRepository: FirebaseRepository,
    private val locationService: LocationService,
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _locationRecords = MutableLiveData<List<LocationRecord>>()
    val locationRecords: LiveData<List<LocationRecord>> get() = _locationRecords

    private val _isLocationOutOfRoute = MutableLiveData<Boolean>()
    val isLocationOutOfRoute: LiveData<Boolean> get() = _isLocationOutOfRoute

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private var currentGeofence: Geofence? = Geofence(radius = 500.0)
    private var currentUserId: String? = null
    init {
        // Iniciar o monitoramento assim que o ViewModel for criado
        loadUserId()
    }

    private fun loadUserId() {
        _isLoading.value = true
        val currentUser = firebaseRepository.getCurrentUser()
        if (currentUser != null) {
            currentUserId = currentUser.uid
            _isLoading.value = false
            loadLocationRecords()
        }else {
            _isLoading.value = false
            _error.value = "Usuário não encontrado!"
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
            val isOutOfRoute = GeofenceHelper().isLocationInGeofence(location, currentGeofence ?: Geofence())

            _isLocationOutOfRoute.postValue(isOutOfRoute)

            val locationRecord = LocationRecord(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address,
                dateTime = java.text.SimpleDateFormat("dd/MM/yyyy - HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                isOutOfRoute = isOutOfRoute
            )
            currentUserId?.let{
                firebaseRepository.saveLocationRecord(locationRecord, it) { success ->
                    if (success) {
                        // Logica para atualizar a lista de LocationRecord
                        loadLocationRecords()
                    }else{
                        _error.postValue("Falha ao salvar localizacao!")
                    }
                    _isLoading.postValue(false)
                }
            }

        }
    }

}