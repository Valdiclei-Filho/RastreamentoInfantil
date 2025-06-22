package com.example.rastreamentoinfantil.model

data class Coordinate(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0
) {
    // Construtor vazio necessário para desserialização do Firestore
    constructor() : this(latitude = 0.0, longitude = 0.0)
}