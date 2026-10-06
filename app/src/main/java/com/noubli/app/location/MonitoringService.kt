package com.noubli.app.location

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.noubli.app.AppContainer
import com.noubli.app.NoubliApp
import com.noubli.app.domain.model.Zone
import com.noubli.app.engine.TrackingEngine
import com.noubli.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Service au premier plan qui garde la surveillance en vie (écran éteint compris).
 *
 * Depuis la v2 il ne contient plus de logique de détection : il gère uniquement le cycle de
 * vie Android (notification permanente, permission, arrêt à la déconnexion) et délègue tout le
 * reste au [TrackingEngine] (lecture du GPS, fusion, règle de sortie, alertes).
 */
class MonitoringService : Service() {

    // Le moteur et les callbacks restent sur le thread principal : pas d'accès concurrent.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var zonesCount = 0

    private lateinit var container: AppContainer
    private lateinit var engine: TrackingEngine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as NoubliApp).container
        engine = container.createEngine(onError = { e ->
            Log.e(TAG, "Le suivi s'est arrêté", e)
            stopSelf()
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sans notification au premier plan ni permission, Android tuerait le service : on abandonne proprement.
        if (!promoteToForeground() || !hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!engine.isRunning) {
            if (container.positionSource.isAvailable) {
                observeZones()
                engine.start(scope)
            } else {
                Log.w(TAG, "Aucun fournisseur de localisation activé")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        MonitoringStatus.set(true)
        return START_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        scope.cancel()
        MonitoringStatus.set(false)
        super.onDestroy()
    }

    /** Affiche la notification permanente et passe le service au premier plan (type « location »). */
    private fun promoteToForeground(): Boolean = try {
        val notification = container.notifier.buildMonitoringNotification(zonesCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.MONITORING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.MONITORING_NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        // Ex. lancement refusé en arrière-plan (Android 12+) ou permission manquante.
        Log.e(TAG, "Impossible de passer au premier plan", e)
        false
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Suit en continu les zones actives de l'utilisateur connecté : toute modification
     * (ajout, suppression, activation) est transmise au moteur sans redémarrer le service.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeZones() {
        scope.launch {
            container.session.userId
                .flatMapLatest { userId -> zonesFlow(userId) }
                .collect { list ->
                    if (list == null) {
                        stopSelf() // déconnexion
                    } else {
                        // Les zones sans objet restent visibles sur le radar ; le moteur n'alerte que si des objets existent.
                        engine.setZones(list)
                        zonesCount = list.count { it.items.isNotEmpty() }
                        container.notifier.updateMonitoringNotification(zonesCount)
                    }
                }
        }
    }

    private fun zonesFlow(userId: Long?): Flow<List<Zone>?> =
        if (userId == null) flowOf(null) else container.zoneRepository.observeActiveZones(userId)

    companion object {
        const val ACTION_START = "com.noubli.app.ACTION_START_MONITORING"
        private const val TAG = "MonitoringService"
    }
}
