package com.example.rastreamentoinfantil.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.example.rastreamentoinfantil.helper.GeofenceHelper
import com.example.rastreamentoinfantil.helper.RouteHelper
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.example.rastreamentoinfantil.service.GeocodingService
import com.example.rastreamentoinfantil.service.LocationService

class MainViewModelFactory(
    private val application: Application,
    private val firebaseRepository: FirebaseRepository,
    private val locationService: LocationService,
    private val geocodingService: GeocodingService,
    private val geofenceHelper: GeofenceHelper,
    private val routeHelper: RouteHelper
) : ViewModelProvider.Factory {

    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                application,
                firebaseRepository,
                locationService,
                geocodingService,
                geofenceHelper,
                routeHelper
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
