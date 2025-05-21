package com.example.rastreamentoinfantil.helper

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.rastreamentoinfantil.MainActivity // Sua atividade principal
import com.example.rastreamentoinfantil.R

// Constante para o ID do canal (o mesmo da MainActivity)
const val GEOFENCE_CHANNEL_ID = "geofence_channel_id"
private const val GEOFENCE_NOTIFICATION_ID = 1 // ID único para esta notificação

object NotificationHelper {

    fun showGeofenceExitNotification(context: Context, geofenceId: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent para abrir o app quando a notificação for clicada
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationTitle = "Alerta de Segurança!"
        val notificationText = "Você saiu da área segura: $geofenceId."

        val builder = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background) // SUBSTITUA por um ícone seu
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Prioridade alta
            .setContentIntent(pendingIntent) // Intent ao clicar
            .setAutoCancel(true) // Remove a notificação ao clicar
        // Opcional: .setVibrate(longArrayOf(0, 1000, 500, 1000)) // Padrão de vibração
        // Opcional: .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        // Verifique se a permissão foi concedida antes de tentar notificar
        // (Embora o sistema Android 13+ não mostre se não tiver, é uma boa prática para robustez)
        // if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        notificationManager.notify(GEOFENCE_NOTIFICATION_ID, builder.build())
        println("Notificação de saída da geofence enviada para: $geofenceId")
        // } else {
        //     println("Não foi possível enviar notificação: permissão POST_NOTIFICATIONS não concedida.")
        // }
    }
}