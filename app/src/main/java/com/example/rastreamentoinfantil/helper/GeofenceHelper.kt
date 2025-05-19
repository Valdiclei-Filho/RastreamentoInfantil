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
        // Se o raio for nulo, consideramos que não há geofence para verificar,
        // ou você pode decidir retornar false dependendo da sua lógica de negócios.
        // Por enquanto, manterei sua lógica original de retornar true se o raio for nulo.
        geofence.radius?.let { radius ->
            // Não há lista de coordenadas para verificar se está vazia.
            // Uma geofence com um único ponto central sempre tem coordenadas.
            // A verificação de nulidade das coordenadas já foi tratada antes.

            val distance = calculateDistance(
                currentLocation.latitude,
                currentLocation.longitude,
                geofence.coordinates.latitude,  // Usar diretamente a coordenada da geofence
                geofence.coordinates.longitude // Usar diretamente a coordenada da geofence
            )
            return distance < radius
        }
        return true // Retorna true se o raio for nulo (ou se a geofence não tiver raio)
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