package com.example.rastreamentoinfantil

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RastreamentoInfantilApp : Application() {
    
    companion object {
        private const val TAG = "RastreamentoInfantilApp"
        
        // Estado global da autenticação
        private var _isAuthChecked = false
        private var _isUserLoggedIn = false
        private var _currentUserId: String? = null
        private var _currentUserEmail: String? = null
        
        // Getters thread-safe
        val isAuthChecked: Boolean get() = _isAuthChecked
        val isUserLoggedIn: Boolean get() = _isUserLoggedIn
        val currentUserId: String? get() = _currentUserId
        val currentUserEmail: String? get() = _currentUserEmail
        
        // Callbacks para notificar mudanças
        private val authStateCallbacks = mutableListOf<() -> Unit>()
        
        fun addAuthStateCallback(callback: () -> Unit) {
            authStateCallbacks.add(callback)
        }
        
        fun removeAuthStateCallback(callback: () -> Unit) {
            authStateCallbacks.remove(callback)
        }
        
        private fun notifyAuthStateChanged() {
            authStateCallbacks.forEach { it.invoke() }
        }
        
        // Método para atualizar o estado de autenticação (chamado pelo LoginViewModel)
        fun updateAuthState(isLoggedIn: Boolean, userId: String? = null, userEmail: String? = null) {
            _isUserLoggedIn = isLoggedIn
            _currentUserId = userId
            _currentUserEmail = userEmail
            _isAuthChecked = true
            notifyAuthStateChanged()
            
            Log.d(TAG, "Estado de autenticação atualizado: isLoggedIn=$isLoggedIn, userId=$userId")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Aplicação iniciando...")
        
        // Verificação síncrona inicial (muito rápida)
        performInitialAuthCheck()
        
        // Verificação assíncrona para validar token (em background)
        performAsyncAuthValidation()
    }
    
    private fun performInitialAuthCheck() {
        try {
            Log.d(TAG, "Realizando verificação inicial de autenticação...")
            
            // Verificação síncrona - Firebase Auth já tem o estado em cache
            val currentUser = FirebaseAuth.getInstance().currentUser
            
            _isUserLoggedIn = currentUser != null
            _currentUserId = currentUser?.uid
            _currentUserEmail = currentUser?.email
            _isAuthChecked = true
            
            Log.d(TAG, "Verificação inicial concluída: isLoggedIn=$_isUserLoggedIn, userId=$_currentUserId")
            
            // Notificar imediatamente
            notifyAuthStateChanged()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante verificação inicial de autenticação", e)
            _isAuthChecked = true
            _isUserLoggedIn = false
            notifyAuthStateChanged()
        }
    }
    
    private fun performAsyncAuthValidation() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Iniciando validação assíncrona do token...")
                
                val currentUser = FirebaseAuth.getInstance().currentUser
                
                if (currentUser != null) {
                    // Verificar se o token ainda é válido
                    try {
                        val tokenResult = currentUser.getIdToken(false).await()
                        Log.d(TAG, "Token validado com sucesso")
                        
                        // Atualizar estado se necessário
                        if (!_isUserLoggedIn || _currentUserId != currentUser.uid) {
                            _isUserLoggedIn = true
                            _currentUserId = currentUser.uid
                            _currentUserEmail = currentUser.email
                            notifyAuthStateChanged()
                            Log.d(TAG, "Estado atualizado após validação: userId=${currentUser.uid}")
                        }
                        
                    } catch (tokenException: Exception) {
                        Log.w(TAG, "Token inválido, usuário precisa fazer login novamente", tokenException)
                        
                        // Token inválido, limpar estado
                        _isUserLoggedIn = false
                        _currentUserId = null
                        _currentUserEmail = null
                        notifyAuthStateChanged()
                        
                        // Fazer logout do Firebase
                        FirebaseAuth.getInstance().signOut()
                    }
                } else {
                    Log.d(TAG, "Nenhum usuário logado encontrado")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro durante validação assíncrona", e)
                // Não alterar o estado em caso de erro na validação
            }
        }
    }
} 