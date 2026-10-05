package com.noubli.app.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.noubli.app.MainActivity
import com.noubli.app.R
import com.noubli.app.domain.model.AlertEvent
import com.noubli.app.domain.model.Zone

/**
 * Création et affichage des notifications :
 *  - une notification discrète et permanente tant que la surveillance est active
 *    (obligatoire pour un service au premier plan) ;
 *  - une alerte bruyante (son + vibration) quand l'utilisateur s'éloigne d'une zone.
 */
class NotificationHelper(private val context: Context) {

    /** À appeler au démarrage de l'app (minSdk 26 : les canaux existent toujours). */
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        val alerts = NotificationChannel(
            CHANNEL_ALERTS, "Alertes d'oubli", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rappel des objets à emporter quand tu quittes une zone"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
        }

        val monitoring = NotificationChannel(
            CHANNEL_MONITORING, "Surveillance active", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Indique que Noubli surveille tes zones" }

        manager.createNotificationChannels(listOf(alerts, monitoring))
    }

    /** Notification permanente du service de surveillance. */
    fun buildMonitoringNotification(activeZones: Int): Notification {
        val text = when (activeZones) {
            0 -> "Aucune zone avec des objets à surveiller"
            1 -> "1 zone surveillée"
            else -> "$activeZones zones surveillées"
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Noubli veille sur toi")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    /** Met à jour le texte de la notification permanente (nombre de zones surveillées). */
    @SuppressLint("MissingPermission") // notify() ignore silencieusement si la permission manque
    fun updateMonitoringNotification(activeZones: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            manager.notify(MONITORING_NOTIFICATION_ID, buildMonitoringNotification(activeZones))
        }
    }

    /** Affiche l'alerte « Tu t'éloignes de … » avec la liste des objets. */
    @SuppressLint("MissingPermission") // vérifié via areNotificationsEnabled()
    fun showForgetAlert(event: AlertEvent, zone: Zone) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val notificationId = alertNotificationId(event.id)
        val items = zone.items.joinToString(", ") { it.label }

        // Bouton « J'ai tout » : traité par AlertActionReceiver.
        val ackIntent = Intent(context, AlertActionReceiver::class.java)
            .setAction(ACTION_ACKNOWLEDGE)
            .putExtra(EXTRA_EVENT_ID, event.id)
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        val ackPending = PendingIntent.getBroadcast(
            context, notificationId, ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tu t'éloignes de « ${zone.name} » !")
            .setContentText("N'oublie pas : $items")
            .setStyle(NotificationCompat.BigTextStyle().bigText("N'oublie pas : $items"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "J'ai tout", ackPending)
            .build()

        manager.notify(notificationId, notification)
    }

    /** Ouvre l'application au toucher de la notification. */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ALERTS = "noubli_alerts"
        const val CHANNEL_MONITORING = "noubli_monitoring"
        const val MONITORING_NOTIFICATION_ID = 1001

        const val ACTION_ACKNOWLEDGE = "com.noubli.app.ACTION_ACKNOWLEDGE"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val ALERT_ID_BASE = 2000

        /** Chaque alerte a sa propre notification (elles ne s'écrasent pas entre elles). */
        fun alertNotificationId(eventId: Long): Int = ALERT_ID_BASE + (eventId % 100_000).toInt()
    }
}
