package com.noubli.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.noubli.app.NoubliApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reçoit le clic sur « J'ai tout » : ferme la notification et marque l'alerte
 * comme acquittée dans l'historique.
 */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_ACKNOWLEDGE) return

        val eventId = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        NotificationManagerCompat.from(context).cancel(notificationId)
        if (eventId <= 0) return

        // goAsync() laisse le temps d'écrire en base avant que le récepteur ne soit détruit.
        val pending = goAsync()
        val repository = (context.applicationContext as NoubliApp).container.alertRepository
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.acknowledge(eventId)
            } finally {
                pending.finish()
            }
        }
    }
}
