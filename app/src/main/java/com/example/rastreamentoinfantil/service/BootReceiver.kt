package com.example.rastreamentoinfantil.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "onReceive: Boot completado, verificando se deve iniciar serviço")
                
                // Verificar se há usuário logado
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    Log.d(TAG, "onReceive: Usuário logado encontrado, iniciando serviço de localização")
                    startBackgroundLocationService(context)
                } else {
                    Log.d(TAG, "onReceive: Nenhum usuário logado, não iniciando serviço")
                }
            }
        }
    }
    
    private fun startBackgroundLocationService(context: Context) {
        try {
            val serviceIntent = Intent(context, BackgroundLocationService::class.java).apply {
                action = BackgroundLocationService.ACTION_START
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "startBackgroundLocationService: Serviço iniciado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "startBackgroundLocationService: Erro ao iniciar serviço", e)
        }
    }
} 