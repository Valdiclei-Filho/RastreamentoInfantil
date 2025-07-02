package com.example.rastreamentoinfantil

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
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
            Log.d(TAG, "Adicionando callback de autenticação")
            authStateCallbacks.add(callback)
            // Executar callback imediatamente se já temos o estado
            if (_isAuthChecked) {
                Log.d(TAG, "Executando callback imediatamente - isLoggedIn=$_isUserLoggedIn")
                callback.invoke()
            }
        }
        
        fun removeAuthStateCallback(callback: () -> Unit) {
            authStateCallbacks.remove(callback)
        }
        
        private fun notifyAuthStateChanged() {
            Log.d(TAG, "Notificando mudança de estado - callbacks: ${authStateCallbacks.size}")
            authStateCallbacks.forEach { 
                try {
                    it.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao executar callback", e)
                }
            }
        }
        
        // Método para atualizar o estado de autenticação (chamado pelo LoginViewModel)
        fun updateAuthState(isLoggedIn: Boolean, userId: String? = null, userEmail: String? = null) {
            Log.d(TAG, "updateAuthState chamado: isLoggedIn=$isLoggedIn, userId=$userId")
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
        Log.d(TAG, "=== APLICAÇÃO INICIANDO ===")
        
        // Inicializar Firebase explicitamente
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase inicializado com sucesso")
            
            // Verificar se o FCM está disponível
            Firebase.messaging.isAutoInitEnabled.let { isEnabled ->
                Log.d(TAG, "FCM Auto Init Enabled: $isEnabled")
            }
            
            // Tentar obter token atual para debug
            Firebase.messaging.token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "Token FCM atual obtido na inicialização: $token")
                } else {
                    Log.e(TAG, "Erro ao obter token FCM na inicialização", task.exception)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar Firebase", e)
        }
        
        // Verificação síncrona inicial
        performInitialAuthCheck()
    }
    
    private fun performInitialAuthCheck() {
        try {
            Log.d(TAG, "=== REALIZANDO VERIFICAÇÃO INICIAL ===")
            
            // Verificação síncrona - Firebase Auth já tem o estado em cache
            val currentUser = FirebaseAuth.getInstance().currentUser
            Log.d(TAG, "FirebaseAuth.currentUser: $currentUser")
            
            _isUserLoggedIn = currentUser != null
            _currentUserId = currentUser?.uid
            _currentUserEmail = currentUser?.email
            _isAuthChecked = true
            
            Log.d(TAG, "=== VERIFICAÇÃO INICIAL CONCLUÍDA ===")
            Log.d(TAG, "isLoggedIn: $_isUserLoggedIn")
            Log.d(TAG, "userId: $_currentUserId")
            Log.d(TAG, "userEmail: $_currentUserEmail")
            Log.d(TAG, "isAuthChecked: $_isAuthChecked")
            
            // Notificar imediatamente
            notifyAuthStateChanged()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante verificação inicial de autenticação", e)
            _isAuthChecked = true
            _isUserLoggedIn = false
            notifyAuthStateChanged()
        }
    }
} 