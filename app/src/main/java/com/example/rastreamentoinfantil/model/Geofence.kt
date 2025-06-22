package com.example.rastreamentoinfantil.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Geofence(
    var id: String? = null,
    var name: String = "",
    var radius: Float = 100f,
    var coordinates: Coordinate = Coordinate(0.0, 0.0),
    var isActive: Boolean = false,
    var targetUserId: String? = null, // ID do usuário da família para quem a geofence é destinada
    var createdByUserId: String = "", // ID do usuário que criou a geofence (responsável)
    var color: String? = "#3F51B5", // Cor da geofence no mapa (Hex)
    @ServerTimestamp
    var createdAt: Date? = null,
    @ServerTimestamp
    var updatedAt: Date? = null
) {
    // Construtor vazio necessário para desserialização do Firestore
    constructor() : this(id = null)
}