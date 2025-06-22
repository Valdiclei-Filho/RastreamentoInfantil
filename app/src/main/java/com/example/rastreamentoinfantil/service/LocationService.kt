package com.example.rastreamentoinfantil.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow

class LocationService(internal val context: Context) {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    init {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    companion object {
        private const val TAG = "LocationService"
    }

    // Reduzir frequência de atualizações para melhorar performance
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY, // Usar precisão balanceada para economizar bateria
        30000L // Intervalo: a cada 30 segundos (reduzido de 1 segundo)
    )
        .setWaitForAccurateLocation(false)
        .build()

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.locations.lastOrNull()?.let { location ->
                    trySend(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper() // Usar main looper para evitar problemas de thread
        ).addOnFailureListener { e ->
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(onLocation: (Location) -> Unit) {
        Log.d(TAG, "startLocationUpdates: Iniciando atualizações de localização")
        
        // Verificar permissões
        val hasFineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "startLocationUpdates: Permissões - FINE: $hasFineLocation, COARSE: $hasCoarseLocation")
        
        if (!hasFineLocation && !hasCoarseLocation) {
            Log.e(TAG, "startLocationUpdates: Permissões de localização não concedidas!")
            return
        }
        
        // Usar configuração mais eficiente para atualizações contínuas
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 
            60000L // Atualiza a cada 1 minuto (reduzido de 2 minutos para melhor responsividade)
        )
        .setWaitForAccurateLocation(false)
        .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d(TAG, "LocationCallback: Nova localização recebida")
                locationResult.lastLocation?.let { location ->
                    Log.d(TAG, "LocationCallback: Localização válida: (${location.latitude}, ${location.longitude})")
                    onLocation(location)
                } ?: run {
                    Log.w(TAG, "LocationCallback: Localização recebida é null")
                }
            }
        }

        Log.d(TAG, "startLocationUpdates: Solicitando atualizações de localização")
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnSuccessListener {
            Log.d(TAG, "startLocationUpdates: Atualizações de localização iniciadas com sucesso")
        }.addOnFailureListener { e ->
            Log.e(TAG, "startLocationUpdates: Erro ao iniciar atualizações de localização", e)
        }
    }

    fun stopLocationUpdates() {
        Log.d(TAG, "stopLocationUpdates: Parando atualizações de localização")
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "stopLocationUpdates: Atualizações de localização paradas com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "stopLocationUpdates: Erro ao parar atualizações de localização", e)
            // Ignorar exceções ao parar atualizações
        }
    }
    
    @SuppressLint("MissingPermission")
    fun getLastLocation(onSuccess: (Location) -> Unit, onFailure: (Exception) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onFailure(SecurityException("Permissões de localização não concedidas."))
            return
        }
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onSuccess(location)
                } else {
                    onFailure(Exception("Localização não encontrada."))
                }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }
}
