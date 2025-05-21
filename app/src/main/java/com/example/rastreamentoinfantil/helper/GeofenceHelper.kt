package com.example.rastreamentoinfantil.helper

import android.location.Location
import com.example.rastreamentoinfantil.model.Geofence
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

class GeofenceHelper {

    fun isLocationInGeofence(currentLocation: Location, geofence: Geofence): Boolean {
        if (geofence.radius <= 0) {
            println("GeofenceHelper: Raio inválido (${geofence.radius}), retornando false")
            return false
        }

        val distance = calculateDistance(
            currentLocation.latitude,
            currentLocation.longitude,
            geofence.coordinates.latitude,
            geofence.coordinates.longitude
        )

        println("GeofenceHelper: Localização Atual: (${currentLocation.latitude}, ${currentLocation.longitude})")
        println("GeofenceHelper: Centro Geofence: (${geofence.coordinates.latitude}, ${geofence.coordinates.longitude})")
        println("GeofenceHelper: Raio Geofence: ${geofence.radius}m")
        println("GeofenceHelper: Distância Calculada: ${distance}m")

        val isInside = distance < geofence.radius
        println("GeofenceHelper: Está dentro? $isInside (distance < radius)")
        return isInside
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Raio da Terra em metros
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)

        // val sqrtA = sqrt(a)
        // val validatedSqrtA = min(1.0, max(-1.0, sqrtA)) // Não precisamos mais disto para atan2

        // Forma mais estável numericamente, especialmente para distâncias pequenas:
        val c = 2 * atan2(sqrt(a), sqrt(1 - a)) // <<< MUDANÇA AQUI

        val distance = r * c
        println("calculateDistance: lat1=$lat1, lon1=$lon1, lat2=$lat2, lon2=$lon2, a_val=$a, c_val=$c, result=$distance")
        return distance
    }
}