package com.example.rastreamentoinfantil

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Lógica para tratar a mensagem recebida
        if (remoteMessage.notification != null) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(remoteMessage.notification!!.title)
                .setContentText(remoteMessage.notification!!.body)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Substitua pelo seu ícone
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = NotificationManagerCompat.from(this)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notificationManager.notify(1, notification)
        }
    }

    override fun onNewToken(token: String) {
        // Lógica para tratar um novo token FCM
        // Aqui você pode enviar o token para o seu servidor para armazená-lo e usá-lo para enviar mensagens para este dispositivo.
    }

    companion object {
        const val CHANNEL_ID = "alerts_channel"
    }
}