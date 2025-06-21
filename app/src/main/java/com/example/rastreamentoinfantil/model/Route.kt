package com.example.rastreamentoinfantil.model

import com.google.firebase.firestore.GeoPoint // Para armazenar pontos geográficos de forma nativa no Firestore
import com.google.firebase.firestore.ServerTimestamp // Para timestamps
import java.util.Date

data class Route(
    var id: String? = null, // ID do documento Firestore, pode ser preenchido após a criação/leitura
    val name: String = "",
    val origin: RoutePoint? = null,
    val destination: RoutePoint? = null,
    val waypoints: List<RoutePoint>? = null, // Lista de pontos intermediários (opcional)
    val encodedPolyline: String? = null, // Para desenhar no mapa
    var isActive: Boolean = false, // Se a rota está atualmente sendo monitorada
    var lastKnownStatus: String? = null, // Ex: "ON_ROUTE", "DEVIATED", "ARRIVED"
    var routeColor: String? = "#FF0000", // Cor da rota no mapa (Hex)
    val activeDays: List<String> = emptyList(), // Dias da semana em que a rota está ativa
    val targetUserId: String? = null, // ID do usuário da família para quem a rota é destinada
    val createdByUserId: String = "", // ID do usuário que criou a rota (responsável)
    @ServerTimestamp // Define o timestamp no servidor ao criar/atualizar
    var createdAt: Date? = null,
    @ServerTimestamp
    var updatedAt: Date? = null
) {
    // Construtor vazio necessário para desserialização do Firestore
    constructor() : this(id = null)
}

data class RoutePoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String? = null, // Endereço textual (opcional)
    // Para usar GeoPoint diretamente, o que é bom para consultas geoespaciais no Firestore
    // val location: GeoPoint? = null
) {
    // Construtor vazio necessário para desserialização do Firestore
    constructor() : this(0.0, 0.0)

    // Se você quiser usar GeoPoint:
    // constructor(geoPoint: GeoPoint, address: String?) : this(geoPoint.latitude, geoPoint.longitude, address, geoPoint)
}