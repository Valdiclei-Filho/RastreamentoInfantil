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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            // Otimizar: carregar dados em background
            viewModelScope.launch(Dispatchers.IO) {
                loadFamilyData()
            }
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
        Log.d("FamilyViewModel", "Carregando dados da família para usuário: $currentUserId, email: $currentUserEmail")

        // Otimizar: buscar usuário em background
        viewModelScope.launch(Dispatchers.IO) {
            repository.getUserById(currentUserId) { user, error ->
                // Só atualiza se o callback for do usuário atual carregado
                if (currentLoadUserId != currentUserId) {
                    Log.d("FamilyViewModel", "Callback desatualizado, ignorando")
                    return@getUserById
                }

                if (error != null) {
                    Log.e("FamilyViewModel", "Erro ao buscar usuário", error)
                    _message.postValue("Erro ao buscar usuário: ${error.message}")
                    _loading.postValue(false)
                    return@getUserById
                }

                Log.d("FamilyViewModel", "Usuário encontrado: ${user?.name}, familyId: ${user?.familyId}")

                if (user == null || user.familyId.isNullOrEmpty()) {
                    Log.d("FamilyViewModel", "Usuário sem família, buscando convites para: $currentUserEmail")
                    repository.getFamilyInvitesForUser(currentUserEmail) { invites, err ->
                        if (currentLoadUserId != currentUserId) return@getFamilyInvitesForUser
                        _loading.postValue(false)
                        if (err != null) {
                            Log.e("FamilyViewModel", "Erro ao buscar convites", err)
                            _message.postValue("Erro ao buscar convites: ${err.message}")
                        } else {
                            Log.d("FamilyViewModel", "Convites carregados: ${invites?.size ?: 0}")
                            _invites.postValue(invites ?: emptyList())
                        }
                    }
                } else {
                    val familyId = user.familyId
                    Log.d("FamilyViewModel", "Usuário com família, carregando família: $familyId")
                    repository.getFamilyDetails(familyId) { family, members, err ->
                        if (currentLoadUserId != currentUserId) return@getFamilyDetails
                        _loading.postValue(false)
                        if (err != null) {
                            Log.e("FamilyViewModel", "Erro ao carregar família", err)
                            _message.postValue("Erro ao carregar família: ${err.message}")
                        } else {
                            Log.d("FamilyViewModel", "Família carregada: ${family?.name}, membros: ${members?.size ?: 0}")
                            _family.postValue(family)
                            _members.postValue(members ?: emptyList())
                        }
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
                val familyId = newFamily.id ?: ""
                repository.updateUserTypeAndFamily(currentUserId, "responsavel", familyId) { userUpdated, userError ->
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
        val family = _family.value
        if (family?.id.isNullOrEmpty()) {
            _message.postValue("Erro: família não encontrada")
            return
        }
        
        val invite = FamilyInvite(
            id = null, // será gerado no repo
            familyName = family?.name ?: "",
            familyId = family?.id ?: "",
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
        val familyId = invite.familyId
        val inviteId = invite.id
        
        if (familyId.isNullOrEmpty() || inviteId.isNullOrEmpty()) {
            _message.postValue("Erro: dados do convite inválidos")
            return
        }
        
        _loading.postValue(true)
        repository.acceptFamilyInvite(currentUserId, familyId, inviteId) { success, errorMsg ->
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
