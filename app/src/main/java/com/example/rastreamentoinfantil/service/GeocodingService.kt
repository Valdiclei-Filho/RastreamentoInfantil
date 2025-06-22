package com.example.rastreamentoinfantil.service

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import java.util.Locale

class GeocodingService(private val context: Context) {
    private val geocoder = Geocoder(context, Locale.getDefault())
    
    companion object {
        private const val TAG = "GeocodingService"
    }

    fun getAddressFromLocation(location: Location, callback: (String?) -> Unit) {
        Log.d(TAG, "getAddressFromLocation: Obtendo endereço para (${location.latitude}, ${location.longitude})")
        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0].getAddressLine(0)
                Log.d(TAG, "getAddressFromLocation: Endereço encontrado: $address")
                callback(address)
            } else {
                Log.w(TAG, "getAddressFromLocation: Nenhum endereço encontrado")
                callback("Endereço não encontrado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAddressFromLocation: Erro ao obter endereço", e)
            e.printStackTrace()
            callback("Erro ao obter endereço")
        }
    }
}