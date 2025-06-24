package com.example.rastreamentoinfantil.model

data class FamilyRelationship(
    var id: String? = null,
    val dependentId: String = "", // ID do dependente
    val responsibleId: String = "", // ID do responsável
    val dependentName: String = "", // Nome do dependente
    val responsibleName: String = "", // Nome do responsável
    val relationshipType: String = "parent_child", // Tipo de relacionamento
    val isActive: Boolean = true, // Se o relacionamento está ativo
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this(null, "", "", "", "", "parent_child", true, System.currentTimeMillis(), System.currentTimeMillis())
} 