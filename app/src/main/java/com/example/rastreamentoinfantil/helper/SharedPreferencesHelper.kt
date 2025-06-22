package com.example.rastreamentoinfantil.helper // ou o pacote correto do seu helper

import android.content.Context
import android.content.SharedPreferences
import com.example.rastreamentoinfantil.model.Coordinate // Importe seu Coordinate
import com.example.rastreamentoinfantil.model.Geofence   // Importe seu Geofence
// Não precisamos mais do import com.google.android.gms.maps.model.LatLng aqui
// a menos que você o use para outra coisa neste arquivo.

object SharedPreferencesHelper {

    private const val PREFS_NAME = "geofence_prefs"
    private const val KEY_GEOFENCE_ID = "geofence_id"
    private const val KEY_GEOFENCE_NAME = "geofence_name" // Nova chave para o nome
    private const val KEY_GEOFENCE_LAT = "geofence_lat"
    private const val KEY_GEOFENCE_LNG = "geofence_lng"
    private const val KEY_GEOFENCE_RADIUS = "geofence_radius"
    private const val KEY_GEOFENCE_DEFINED = "geofence_defined"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveGeofence(context: Context, geofence: Geofence) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_GEOFENCE_ID, geofence.id)
        editor.putString(KEY_GEOFENCE_NAME, geofence.name) // Salva o nome
        // Acessa latitude/longitude do seu objeto Coordinate personalizado
        editor.putLong(KEY_GEOFENCE_LAT, java.lang.Double.doubleToRawLongBits(geofence.coordinates.latitude))
        editor.putLong(KEY_GEOFENCE_LNG, java.lang.Double.doubleToRawLongBits(geofence.coordinates.longitude))
        editor.putFloat(KEY_GEOFENCE_RADIUS, geofence.radius)
        editor.putBoolean(KEY_GEOFENCE_DEFINED, true)
        editor.apply()
        println("Geofence salva em SharedPreferences: $geofence")
    }

    fun loadGeofence(context: Context): Geofence? {
        val prefs = getPreferences(context)
        if (!prefs.getBoolean(KEY_GEOFENCE_DEFINED, false)) {
            println("Nenhuma geofence definida em SharedPreferences.")
            return null
        }

        val id = prefs.getString(KEY_GEOFENCE_ID, null) // Pode ser null se sua classe Geofence permite
        val name = prefs.getString(KEY_GEOFENCE_NAME, null) // Carrega o nome, pode ser null
        val latBits = prefs.getLong(KEY_GEOFENCE_LAT, 0L)
        val lngBits = prefs.getLong(KEY_GEOFENCE_LNG, 0L)
        val radius = prefs.getFloat(KEY_GEOFENCE_RADIUS, 0f)

        // Verificação básica de validade
        if (radius == 0f && latBits == 0L && lngBits == 0L) {
            println("Dados de geofence inválidos ou não totalmente definidos carregados de SharedPreferences.")
            return null
        }


        val latitude = java.lang.Double.longBitsToDouble(latBits)
        val longitude = java.lang.Double.longBitsToDouble(lngBits)

        // 1. Crie uma instância da sua classe Coordinate
        val loadedCoordinates = Coordinate(latitude = latitude, longitude = longitude)

        // 2. Crie a instância de Geofence usando sua classe Coordinate
        val geofence = Geofence(
            id = id,
            name = name ?: "", // Garantir que name seja uma String não-nula
            radius = radius,
            coordinates = loadedCoordinates // Passe sua instância de Coordinate aqui
        )
        println("Geofence carregada de SharedPreferences: $geofence")
        return geofence
    }

    fun clearGeofence(context: Context) {
        val editor = getPreferences(context).edit()
        editor.remove(KEY_GEOFENCE_ID)
        editor.remove(KEY_GEOFENCE_NAME) // Limpa o nome também
        editor.remove(KEY_GEOFENCE_LAT)
        editor.remove(KEY_GEOFENCE_LNG)
        editor.remove(KEY_GEOFENCE_RADIUS)
        editor.remove(KEY_GEOFENCE_DEFINED)
        editor.apply()
        println("Geofence removida de SharedPreferences.")
    }
}