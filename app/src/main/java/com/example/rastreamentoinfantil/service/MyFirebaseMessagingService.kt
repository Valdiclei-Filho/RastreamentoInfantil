package com.example.rastreamentoinfantil.service // ou seu pacote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rastreamentoinfantil.MainActivity // Sua atividade principal
import com.example.rastreamentoinfantil.R // Seu R.drawable etc.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        const val CHANNEL_ID = "geofence_event_channel"
        private const val CHANNEL_NAME = "Geofence Alerts"
    }

    /**
     * Chamado quando uma mensagem é recebida.
     * @param remoteMessage Objeto representando a mensagem recebida do FCM.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Se vier como notification (ex: do Firebase Console)
        remoteMessage.notification?.let {
            val title = it.title ?: "Alerta"
            val body = it.body ?: "Nova notificação"
            Log.d(TAG, "Recebido notification: title=$title, body=$body")
            sendNotification(title, body)
            return
        }

        // Se vier como data (ex: do backend)
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "Alerta de Geofence"
            val body = remoteMessage.data["body"] ?: "Evento de geofence detectado."
            Log.d(TAG, "Recebido data: title=$title, body=$body, data=${remoteMessage.data}")
            sendNotification(title, body)
        }
    }

    /**
     * Chamado se o token de registro do FCM for atualizado. Este pode ser chamado quando um novo token é
     * gerado ou o token existente é invalidado.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        
        // Salvar o token FCM no Firestore para o usuário logado
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            Log.d(TAG, "Salvando token para usuário: ${user.uid}")
            val repo = com.example.rastreamentoinfantil.repository.FirebaseRepository()
            repo.saveUserFcmToken(user.uid, token) { success, exception ->
                if (success) {
                    Log.d(TAG, "Token FCM salvo com sucesso no Firestore")
                } else {
                    Log.e(TAG, "Erro ao salvar token FCM: ${exception?.message}")
                }
            }
        } else {
            Log.w(TAG, "Usuário não logado, não é possível salvar token FCM")
        }
    }

    /**
     * Cria e exibe uma notificação local simples contendo a mensagem FCM recebida.
     * @param messageBody Corpo da mensagem FCM recebida.
     */
    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java) // Abre o app ao clicar
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Você pode adicionar extras ao intent se quiser navegar para uma tela específica
        // intent.putExtra("navigateTo", "notification_history")

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
            pendingIntentFlags)

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background) // SUBSTITUA por seu ícone de notificação
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Para heads-up notification

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Desde o Android Oreo (API 26), o canal de notificação é necessário.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH) // IMPORTANCE_HIGH para heads-up
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0 /* ID da notificação */, notificationBuilder.build())
    }
}