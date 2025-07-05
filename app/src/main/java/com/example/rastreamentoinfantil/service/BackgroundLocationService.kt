package com.example.rastreamentoinfantil.service

import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rastreamentoinfantil.MainActivity
import com.example.rastreamentoinfantil.R
import com.example.rastreamentoinfantil.helper.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

class BackgroundLocationService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationService: LocationService
    private var isRunning = false
    
    companion object {
        private const val TAG = "BackgroundLocationService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "background_location_channel"
        private const val CHANNEL_NAME = "Localização em Segundo Plano"
        
        // Ações para controlar o serviço
        const val ACTION_START = "START_BACKGROUND_LOCATION"
        const val ACTION_STOP = "STOP_BACKGROUND_LOCATION"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Serviço criado")
        locationService = LocationService(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> startBackgroundLocation()
            ACTION_STOP -> stopBackgroundLocation()
        }
        
        return START_STICKY // Reinicia o serviço se for morto pelo sistema
    }
    
    private fun startBackgroundLocation() {
        if (isRunning) {
            Log.d(TAG, "startBackgroundLocation: Serviço já está rodando")
            return
        }
        
        Log.d(TAG, "startBackgroundLocation: Iniciando serviço de localização em segundo plano")
        isRunning = true
        
        // Iniciar como foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Iniciar monitoramento de localização
        serviceScope.launch {
            try {
                locationService.startLocationUpdates { location ->
                    Log.d(TAG, "Nova localização em segundo plano: (${location.latitude}, ${location.longitude})")
                    processLocationUpdate(location)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar monitoramento de localização", e)
            }
        }
    }
    
    private fun stopBackgroundLocation() {
        Log.d(TAG, "stopBackgroundLocation: Parando serviço de localização em segundo plano")
        isRunning = false
        
        serviceScope.launch {
            try {
                locationService.stopLocationUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao parar monitoramento de localização", e)
            }
        }
        
        stopForeground(true)
        stopSelf()
    }
    
    private fun processLocationUpdate(location: Location) {
        // Verificar se há usuário logado
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "processLocationUpdate: Nenhum usuário logado")
            return
        }
        
        // Aqui você pode adicionar a lógica para:
        // 1. Salvar localização no Firebase
        // 2. Verificar geofences
        // 3. Verificar rotas
        // 4. Enviar notificações se necessário
        
        Log.d(TAG, "processLocationUpdate: Processando localização para usuário ${currentUser.uid}")
        
        // TODO: Implementar lógica específica do seu app
        // Por exemplo, chamar o MainViewModel para processar a localização
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoramento de localização em segundo plano"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rastreamento Ativo")
            .setContentText("Monitorando localização em segundo plano")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Serviço destruído")
        isRunning = false
        serviceScope.cancel()
    }
} 