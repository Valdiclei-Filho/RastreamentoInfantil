package com.example.rastreamentoinfantil.helper

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rastreamentoinfantil.MainActivity
import com.example.rastreamentoinfantil.R
import java.text.SimpleDateFormat
import java.util.*

// Constante para o ID do canal (o mesmo da MainActivity)
const val GEOFENCE_CHANNEL_ID = "geofence_channel_id"
const val ROUTE_CHANNEL_ID = "route_channel_id"
private const val GEOFENCE_NOTIFICATION_ID = 1
private const val ROUTE_NOTIFICATION_ID = 2

object NotificationHelper {
    private const val TAG = "NotificationHelper"

    fun showGeofenceExitNotification(
        context: Context, 
        geofenceId: String,
        childName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        Log.d(TAG, "Preparando notificação de saída da geofence: $geofenceId")
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        val childInfo = childName ?: "Dependente"
        val locationInfo = if (latitude != null && longitude != null) {
            "Localização: %.4f, %.4f".format(latitude, longitude)
        } else {
            ""
        }

        val notificationTitle = "Alerta de Segurança!"
        val notificationText = "$childInfo saiu da área segura '$geofenceId' às $horario. $locationInfo"

        val builder = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(GEOFENCE_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "Notificação de saída da geofence enviada para: $geofenceId")
    }

    fun showGeofenceReturnNotification(
        context: Context, 
        geofenceId: String,
        childName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        Log.d(TAG, "Preparando notificação de retorno à geofence: $geofenceId")
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        val childInfo = childName ?: "Dependente"
        val locationInfo = if (latitude != null && longitude != null) {
            "Localização: %.4f, %.4f".format(latitude, longitude)
        } else {
            ""
        }

        val notificationTitle = "Retorno à Área Segura"
        val notificationText = "$childInfo voltou para a área segura '$geofenceId' às $horario. $locationInfo"

        val builder = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(GEOFENCE_NOTIFICATION_ID + 1, builder.build())
        Log.d(TAG, "Notificação de retorno à geofence enviada para: $geofenceId")
    }

    fun showRouteExitNotification(
        context: Context, 
        routeName: String,
        childName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        Log.d(TAG, "Preparando notificação de saída da rota: $routeName")
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val horario = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        val childInfo = childName ?: "Dependente"
        val locationInfo = if (latitude != null && longitude != null) {
            "Localização: %.4f, %.4f".format(latitude, longitude)
        } else {
            ""
        }

        val notificationTitle = "Alerta de Desvio!"
        val notificationText = "$childInfo saiu da rota '$routeName' às $horario. $locationInfo"

        val builder = NotificationCompat.Builder(context, ROUTE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(ROUTE_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "Notificação de saída da rota enviada para: $routeName")
    }
}