package com.example.rastreamentoinfantil.model

import com.google.firebase.firestore.GeoPoint

data class LocationRecord(
    var id: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null, // Corrigido para 'address'
    val dateTime: String? = null, // Corrigido para 'dateTime'
    val isOutOfRoute: Boolean? = null, // Corrigido para 'isOutOfRoute'
)