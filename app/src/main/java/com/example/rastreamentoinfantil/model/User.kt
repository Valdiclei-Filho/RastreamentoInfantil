package com.example.rastreamentoinfantil.model

import java.util.UUID

data class User(
    var id: String = "",
    val name: String? = null,
    val email: String? = null,
    val type: String? = null,
    val familyId: String? = null
)

data class Family(
    val id: String = "",
    val name: String = "",
    val responsibleId: String = ""
)

data class FamilyInvite(
    val id: String,
    val familyName: String,
    val familyId: String,
    val recipientEmail: String
)
