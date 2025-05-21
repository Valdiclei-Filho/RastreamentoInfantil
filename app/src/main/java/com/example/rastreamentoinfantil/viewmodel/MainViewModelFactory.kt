package com.example.rastreamentoinfantil.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.rastreamentoinfantil.helper.GeofenceHelper
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.service.GeocodingService
import com.example.rastreamentoinfantil.service.LocationService
import android.app.Application


class MainViewModelFactory(
    private val application: Application, // Adicione
    private val firebaseRepository: FirebaseRepository,
    private val locationService: LocationService,
    private val geocodingService: GeocodingService,
    private val geofenceHelper: GeofenceHelper // Adicione
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, firebaseRepository, locationService, geocodingService, geofenceHelper) as T // Passe
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
