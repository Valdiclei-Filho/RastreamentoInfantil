package com.example.rastreamentoinfantil.model

data class Geofence(
    var id: String? = null,
    var name: String? = null,
    var radius: Double,
    var coordinates: Coordinate
)