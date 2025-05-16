package com.example.rastreamentoinfantil.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.rastreamentoinfantil.model.User
import com.example.rastreamentoinfantil.repository.FirebaseRepository

class LoginViewModel(private val firebaseRepository: FirebaseRepository) : ViewModel() {
    private val _isLoggedIn = MutableLiveData<Boolean>()
    val isLoggedIn: LiveData<Boolean> get() = _isLoggedIn

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _user = MutableLiveData<User?>()
    val user: MutableLiveData<User?> = _user

    // Nova LiveData para sucesso no cadastro
    private val _registerSuccess = MutableLiveData<Boolean>(false)
    val registerSuccess: LiveData<Boolean> get() = _registerSuccess

    fun registerUser(user: User, password: String) {
        _isLoading.value = true
        firebaseRepository.createUser(user, password) { success, message ->
            _isLoading.value = false
            if (success) {
                _registerSuccess.value = true  // Indica sucesso no cadastro
                _error.value = null
                _isLoggedIn.value = true // Você pode manter ou não essa linha dependendo do fluxo
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
                firebaseRepository.fetchUserData { userResult ->
                    _user.value = userResult
                    _isLoggedIn.value = true
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
    }
}