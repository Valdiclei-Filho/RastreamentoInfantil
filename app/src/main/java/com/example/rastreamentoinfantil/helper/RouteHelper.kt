package com.example.rastreamentoinfantil.helper

import android.location.Location
import android.util.Log
import com.example.rastreamentoinfantil.model.Route
import com.example.rastreamentoinfantil.model.RoutePoint
import com.google.maps.android.PolyUtil
import kotlin.math.sqrt

class RouteHelper {
    companion object {
        private const val TAG = "RouteHelper"
        private const val MAX_DISTANCE_FROM_ROUTE = 50.0 // metros
        private const val MIN_DISTANCE_FOR_DEVIATION = 30.0 // metros
        private const val DEVIATION_CHECK_INTERVAL = 5000L // 5 segundos
        private const val MIN_POINTS_FOR_ROUTE = 2
    }

    private var lastDeviationCheckTime = 0L
    private var consecutiveDeviations = 0
    internal var lastKnownRoutePoint: RoutePoint? = null

    private fun distanceToSegment(
        px: Double, py: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double
    ): Double {
        val A = px - x1
        val B = py - y1
        val C = x2 - x1
        val D = y2 - y1

        val dot = A * C + B * D
        val lenSq = C * C + D * D
        var param = -1.0

        if (lenSq != 0.0) {
            param = dot / lenSq
        }

        var xx: Double
        var yy: Double

        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * C
            yy = y1 + param * D
        }

        val dx = px - xx
        val dy = py - yy
        return sqrt(dx * dx + dy * dy) * 111000 // Aproximação: 1 grau = 111km
    }

    fun isLocationOnRoute(location: Location, route: Route): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Verifica se é hora de fazer uma nova verificação
        if (currentTime - lastDeviationCheckTime < DEVIATION_CHECK_INTERVAL) {
            return true // Mantém o status anterior
        }
        
        lastDeviationCheckTime = currentTime

        Log.d(TAG, "Verificando se localização está na rota: ${route.name}")
        Log.d(TAG, "Localização atual: (${location.latitude}, ${location.longitude})")

        if (route.encodedPolyline == null) {
            Log.w(TAG, "Rota não possui polyline codificada")
            return false
        }

        // Decodifica a polyline para obter todos os pontos da rota
        val routePoints = try {
            PolyUtil.decode(route.encodedPolyline)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar polyline", e)
            return false
        }

        if (routePoints.size < MIN_POINTS_FOR_ROUTE) {
            Log.w(TAG, "Rota não possui pontos suficientes para verificação")
            return false
        }

        // Verifica a distância até cada segmento da rota
        var minDistance: Float = Float.MAX_VALUE
        var closestPoint: RoutePoint? = null

        for (i in 0 until routePoints.size - 1) {
            val startPoint = routePoints[i]
            val endPoint = routePoints[i + 1]
            
            val distance = distanceToSegment(
                location.latitude, location.longitude,
                startPoint.latitude, startPoint.longitude,
                endPoint.latitude, endPoint.longitude
            )
            
            if (distance < minDistance) {
                minDistance = distance.toFloat()
                closestPoint = RoutePoint(
                    latitude = (startPoint.latitude + endPoint.latitude) / 2,
                    longitude = (startPoint.longitude + endPoint.longitude) / 2
                )
            }
        }

        // Lógica de detecção de desvio
        val isCurrentlyOnRoute = minDistance <= MAX_DISTANCE_FROM_ROUTE
        
        if (isCurrentlyOnRoute) {
            consecutiveDeviations = 0
            lastKnownRoutePoint = closestPoint
            Log.d(TAG, "Localização está dentro da rota (distância: $minDistance metros)")
            return true
        } else {
            // Verifica se o desvio é significativo e persistente
            if (minDistance >= MIN_DISTANCE_FOR_DEVIATION) {
                consecutiveDeviations++
                Log.d(TAG, "Desvio detectado (distância: $minDistance metros, consecutivos: $consecutiveDeviations)")
                
                // Se houver desvios consecutivos, considera como desvio real
                if (consecutiveDeviations >= 2) {
                    return false
                }
            }
            
            // Se o desvio for pequeno ou não persistente, mantém na rota
            return true
        }
    }

    fun getLastKnownRoutePoint(): RoutePoint? = lastKnownRoutePoint

    fun calculateDeviationDistance(location: Location, route: Route): Float {
        val decodedPath = route.encodedPolyline?.let { PolyUtil.decode(it) }
        if (decodedPath == null || decodedPath.size < 2) {
            Log.w(TAG, "Rota não tem pontos suficientes para calcular desvio")
            return Float.MAX_VALUE
        }

        var minDistance: Float = Float.MAX_VALUE
        for (i in 0 until decodedPath.size - 1) {
            val segmentStart = decodedPath[i]
            val segmentEnd = decodedPath[i + 1]
            
            val distance = distanceToSegment(
                location.latitude,
                location.longitude,
                segmentStart.latitude,
                segmentStart.longitude,
                segmentEnd.latitude,
                segmentEnd.longitude
            )
            
            if (distance < minDistance) {
                minDistance = distance.toFloat()
                lastKnownRoutePoint = segmentStart as RoutePoint?
            }
        }

        return minDistance
    }
} 