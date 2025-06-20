package com.example.rastreamentoinfantil.model

import java.util.UUID

data class User(
    var id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val type: String? = null,
    val familyId: String? = null,
    val acceptedTerms: Boolean = false
) {
    // Construtor sem argumentos para o Firestore
    constructor() : this(null, null, null, null, null, false)
}

data class Family(
    var id: String? = null,
    val name: String = "",
    val responsibleId: String = ""
) {
    // Construtor sem argumentos para o Firestore
    constructor() : this(null, "", "")
}

data class FamilyInvite(
    var id: String? = null,
    val familyName: String = "",
    val familyId: String = "",
    val recipientEmail: String = ""
) {
    // Construtor sem argumentos para o Firestore
    constructor() : this(null, "", "", "")
}
