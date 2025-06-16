package com.example.rastreamentoinfantil.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.rastreamentoinfantil.model.Family
import com.example.rastreamentoinfantil.model.FamilyInvite
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import java.util.UUID

class FamilyViewModel(
    private val repository: FirebaseRepository,
    val currentUserId: String,
    private val currentUserEmail: String
) : ViewModel() {

    private val _family = MutableLiveData<Family?>()
    val family: LiveData<Family?> = _family

    private val _members = MutableLiveData<List<User>>()
    val members: LiveData<List<User>> = _members

    private val _invites = MutableLiveData<List<FamilyInvite>>()
    val invites: LiveData<List<FamilyInvite>> = _invites

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    // Variável para evitar sobrescrever dados com callbacks atrasados
    private var currentLoadUserId = ""

    init {
        if (currentUserId.isEmpty()) {
            Log.e("FamilyViewModel", "currentUserId está vazio! Não pode carregar dados.")
            _message.postValue("Erro: usuário não autenticado")
            _loading.postValue(false)
        } else {
            loadFamilyData()
        }
    }


    private fun clearData() {
        _family.postValue(null)
        _members.postValue(emptyList())
        _invites.postValue(emptyList())
        _message.postValue(null)
    }

    fun loadFamilyData() {
        currentLoadUserId = currentUserId
        clearData()
        _loading.postValue(true)

        repository.getUserById(currentUserId) { user, error ->
            // Só atualiza se o callback for do usuário atual carregado
            if (currentLoadUserId != currentUserId) {
                // Callback desatualizado, ignorar
                return@getUserById
            }

            if (error != null) {
                _message.postValue("Erro ao buscar usuário: ${error.message}")
                _loading.postValue(false)
                return@getUserById
            }

            if (user == null || user.familyId.isNullOrEmpty()) {
                // Usuário sem família: carregar convites
                repository.getFamilyInvitesForUser(currentUserEmail) { invites, err ->
                    if (currentLoadUserId != currentUserId) return@getFamilyInvitesForUser

                    _loading.postValue(false)
                    if (err != null) {
                        _message.postValue("Erro ao buscar convites: ${err.message}")
                    } else {
                        _invites.postValue(invites ?: emptyList())
                    }
                }
            } else {
                // Usuário com família: carregar família e membros
                val familyId = user.familyId
                repository.getFamilyDetails(familyId) { family, members, err ->
                    if (currentLoadUserId != currentUserId) return@getFamilyDetails

                    _loading.postValue(false)
                    if (err != null) {
                        _message.postValue("Erro ao carregar família: ${err.message}")
                    } else {
                        _family.postValue(family)
                        _members.postValue(members ?: emptyList())
                    }
                }
            }
        }
    }

    fun createFamily(name: String) {
        _loading.postValue(true)

        val newFamily = Family(
            id = UUID.randomUUID().toString(),
            name = name,
            responsibleId = currentUserId
        )

        repository.createFamily(newFamily) { success, errorMsg ->
            if (success) {
                // Atualiza o user como responsável e define o familyId
                repository.updateUserTypeAndFamily(currentUserId, "responsavel", newFamily.id) { userUpdated, userError ->
                    _loading.postValue(false)
                    if (userUpdated) {
                        _message.postValue("Família criada e perfil atualizado como responsável")
                        loadFamilyData() // Recarrega dados atuais
                    } else {
                        _message.postValue("Família criada, mas falha ao atualizar usuário: $userError")
                    }
                }
            } else {
                _loading.postValue(false)
                _message.postValue(errorMsg)
            }
        }
    }


    fun sendInvite(recipientEmail: String) {
        val invite = FamilyInvite(
            id = "", // será gerado no repo
            familyName = _family.value?.name ?: "",
            familyId = _family.value?.id ?: "",
            recipientEmail = recipientEmail
        )
        _loading.postValue(true)
        repository.sendFamilyInvite(invite, recipientEmail) { success, errorMsg ->
            _loading.postValue(false)
            if (success) {
                _message.postValue("Convite enviado")
            } else {
                _message.postValue(errorMsg)
            }
        }
    }

    fun acceptInvite(invite: FamilyInvite) {
        _loading.postValue(true)
        repository.acceptFamilyInvite(currentUserId, invite.familyId, invite.id) { success, errorMsg ->
            _loading.postValue(false)
            if (success) {
                _message.postValue("Convite aceito")
                loadFamilyData()
            } else {
                _message.postValue(errorMsg)
            }
        }
    }

    fun clearMessage() {
        _message.postValue(null)
    }
}
