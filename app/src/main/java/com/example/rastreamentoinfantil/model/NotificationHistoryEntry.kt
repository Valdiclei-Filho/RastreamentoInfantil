package com.example.rastreamentoinfantil.model

import java.util.UUID

// Novo modelo com campos completos para histórico de notificações

data class NotificationHistoryEntry(
    var id: String? = null,
    val titulo: String = "",
    val body: String = "",
    val childId: String? = null,
    val childName: String? = null, // Nome do dependente
    val tipoEvento: String? = null, // desvio, saida, volta
    val latitude: Double? = null, // Localização do dependente
    val longitude: Double? = null, // Localização do dependente
    val horarioEvento: String? = null, // Horário do evento (formato string para facilitar exibição)
    val contagemTempo: Long = 0L,
    var lida: Boolean = false
) {
    constructor() : this(null, "", "", null, null, null, null, null, null, 0L, false)
}