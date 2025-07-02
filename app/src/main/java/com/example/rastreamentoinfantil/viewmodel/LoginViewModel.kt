package com.example.rastreamentoinfantil.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.rastreamentoinfantil.RastreamentoInfantilApp
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.repository.FirebaseRepository
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

class LoginViewModel(private val firebaseRepository: FirebaseRepository) : ViewModel() {
    private val _isLoggedIn = MutableLiveData<Boolean>()
    val isLoggedIn: LiveData<Boolean> get() = _isLoggedIn

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    // Estado para verificação inicial de autenticação
    private val _isCheckingAuth = MutableLiveData<Boolean>(true)
    val isCheckingAuth: LiveData<Boolean> get() = _isCheckingAuth

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _user = MutableLiveData<User?>()
    val user: MutableLiveData<User?> = _user

    // Nova LiveData para sucesso no cadastro
    private val _registerSuccess = MutableLiveData<Boolean>(false)
    val registerSuccess: LiveData<Boolean> get() = _registerSuccess

    init {
        // Sincronizar com o estado global da aplicação
        syncWithAppAuthState()
        
        // Adicionar callback para mudanças no estado de autenticação
        RastreamentoInfantilApp.addAuthStateCallback {
            syncWithAppAuthState()
        }
    }

    private fun syncWithAppAuthState() {
        _isCheckingAuth.value = !RastreamentoInfantilApp.isAuthChecked
        _isLoggedIn.value = RastreamentoInfantilApp.isUserLoggedIn
        
        if (RastreamentoInfantilApp.isUserLoggedIn) {
            // Buscar dados do usuário se estiver logado
            firebaseRepository.fetchUserData { userResult, exception ->
                _user.value = userResult
            }
        } else {
            _user.value = null
        }
    }

    fun registerUser(user: User, password: String) {
        _isLoading.value = true
        firebaseRepository.createUser(user, password) { success, message ->
            _isLoading.value = false
            if (success) {
                _registerSuccess.value = true
                _error.value = null
                _isLoggedIn.value = true
                
                // Atualizar estado global
                RastreamentoInfantilApp.updateAuthState(
                    isLoggedIn = true,
                    userId = user.id,
                    userEmail = user.email
                )
            } else {
                _error.value = message
                _isLoggedIn.value = false
                _registerSuccess.value = false
            }
        }
    }

    fun login(email: String, password: String) {
        _isLoading.value = true
        firebaseRepository.signIn(email, password) { success, message ->
            _isLoading.value = false
            if (success) {
                firebaseRepository.fetchUserData { userResult, exception ->
                    _user.value = userResult
                    _isLoggedIn.value = true
                    
                    // Atualizar estado global
                    RastreamentoInfantilApp.updateAuthState(
                        isLoggedIn = true,
                        userId = userResult?.id,
                        userEmail = userResult?.email
                    )

                    // Salvar token FCM após login bem-sucedido
                    if (userResult?.id != null) {
                        Firebase.messaging.token.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val token = task.result
                                firebaseRepository.saveUserFcmToken(userResult.id!!, token) { success, exception ->
                                    if (success) {
                                        Log.d("LoginViewModel", "Token FCM salvo com sucesso após login")
                                    } else {
                                        Log.e("LoginViewModel", "Erro ao salvar token FCM após login: ", exception)
                                    }
                                }
                            } else {
                                Log.e("LoginViewModel", "Erro ao obter token FCM após login", task.exception)
                            }
                        }
                    }
                }
                _error.value = null
            } else {
                _error.value = message
                _isLoggedIn.value = false
            }
        }
    }

    fun signOut() {
        firebaseRepository.signOut()
        _isLoggedIn.value = false
        _user.value = null
        _error.value = null
        _registerSuccess.value = false
        
        // Atualizar estado global
        RastreamentoInfantilApp.updateAuthState(isLoggedIn = false)
        
        Log.d("LoginViewModel", "Usuário deslogado e dados limpos")
    }

    fun checkAuthenticationState() {
        // Agora apenas sincroniza com o estado global
        syncWithAppAuthState()
    }

    // Função para limpar o erro
    fun clearError() {
        _error.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        // Remover callback para evitar memory leaks
        RastreamentoInfantilApp.removeAuthStateCallback { syncWithAppAuthState() }
    }
}