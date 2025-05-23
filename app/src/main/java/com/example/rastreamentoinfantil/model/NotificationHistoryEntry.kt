package com.example.rastreamentoinfantil.model

import java.util.UUID

data class NotificationHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val titulo: String,
    val body: String,
    val childId: String?,
    val tipoEvento: String?,
    val contagemTempo: Long,
    var lida: Boolean = false
) {
    constructor() : this("", "", "", null, null, 0L, false)
}