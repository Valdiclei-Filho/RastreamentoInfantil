package com.example.rastreamentoinfantil.service

import android.content.Context
import android.location.Geocoder
import android.location.Location
import java.util.Locale

class GeocodingService(private val context: Context) {
    private val geocoder = Geocoder(context, Locale.getDefault())

    fun getAddressFromLocation(location: Location, callback: (String?) -> Unit) {
        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0].getAddressLine(0)
                callback(address)
            } else {
                callback("Endereço não encontrado")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            callback("Erro ao obter endereço")
        }
    }
}