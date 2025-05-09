// GeofenceHelper.kt
package com.example.rastreamentoinfantil.helper

import android.location.Location
import androidx.compose.ui.geometry.isEmpty
import com.example.rastreamentoinfantil.model.Geofence // Importando sua classe Geofenceimport kotlin.math.acos
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

class GeofenceHelper {

    fun isLocationInGeofence(currentLocation: Location, geofence: Geofence): Boolean {
        geofence.radius?.let { radius ->
            // Você precisa garantir que 'coordinates' existe e é acessível aqui.
            // Abordaremos 'coordinates' em seguida.
            if (geofence.coordinates.isEmpty()){
                return true
            }
            return geofence.coordinates.any {
                val distance = calculateDistance(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    it.latitude!!,
                    it.longitude!!
                )
                distance < radius
            }
        }
        return true // Retorna true se o raio for nulo
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * acos(kotlin.math.sqrt(a.toDouble()))

        return R * c
    }
}