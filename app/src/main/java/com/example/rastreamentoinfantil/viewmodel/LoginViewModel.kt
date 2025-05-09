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

    fun registerUser(user: User, password: String) {
        _isLoading.value = true
        firebaseRepository.createUser(user, password) { success, message ->
            _isLoading.value = false
            if (success) {
                _isLoggedIn.value = true // Sucesso no cadastro
                _error.value = null
            } else {
                _error.value = message // Erro no cadastro
                _isLoggedIn.value = false
            }
        }
    }

    fun login(email: String, password: String) {
        _isLoading.value = true
        firebaseRepository.signIn(email, password) { success, message ->
            _isLoading.value = false
            if (success) {
                _isLoggedIn.value = true // Sucesso no login
                _error.value = null
            } else {
                _error.value = message // Erro no login
                _isLoggedIn.value = false
            }
        }
    }

    fun signOut() {
        firebaseRepository.signOut()
        _isLoggedIn.value = false
    }
}