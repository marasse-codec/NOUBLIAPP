package com.noubli.app.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État observable de la surveillance (« en marche » ou non).
 * Alimenté par [MonitoringService] ; lu par l'écran d'accueil pour l'interrupteur.
 * Stocké en mémoire : si le processus meurt, le service est mort aussi, donc l'état reste cohérent.
 */
object MonitoringStatus {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    internal fun set(value: Boolean) {
        _running.value = value
    }
}

/** Démarre et arrête le service de surveillance (l'UI n'a pas à connaître les Intents). */
object MonitoringController {

    /** À appeler depuis un écran visible : Android 12+ interdit de lancer ce service en arrière-plan. */
    fun start(context: Context) {
        val intent = Intent(context, MonitoringService::class.java)
            .setAction(MonitoringService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, MonitoringService::class.java))
    }
}
