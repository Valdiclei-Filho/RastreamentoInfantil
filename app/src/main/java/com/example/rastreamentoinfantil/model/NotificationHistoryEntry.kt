package com.example.rastreamentoinfantil.model

import java.util.UUID

data class NotificationHistoryEntry(
    var id: String? = null,
    val titulo: String = "",
    val body: String = "",
    val childId: String? = null,
    val tipoEvento: String? = null,
    val contagemTempo: Long = 0L,
    var lida: Boolean = false
) {
    constructor() : this(null, "", "", null, null, 0L, false)
}