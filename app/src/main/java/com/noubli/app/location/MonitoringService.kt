package com.noubli.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import com.noubli.app.AppContainer
import com.noubli.app.NoubliApp
import com.noubli.app.domain.ProximityEvaluator
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.model.ZoneExit
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
 * Service au premier plan qui écoute le GPS tant que la surveillance est active.
 *
 * Déroulé : démarrage -> notification permanente -> abonnement aux zones actives de
 * l'utilisateur connecté -> à chaque position, [ProximityEvaluator] décide s'il faut
 * alerter -> l'alerte est enregistrée en base puis notifiée.
 * Le service s'arrête tout seul si l'utilisateur se déconnecte.
 */
class MonitoringService : Service() {

    // Les callbacks GPS arrivent sur le thread principal ; on y reste pour éviter les accès concurrents.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val evaluator = ProximityEvaluator()
    private var zones: List<Zone> = emptyList()
    private var tracking = false

    private lateinit var container: AppContainer
    private lateinit var locationManager: LocationManager

    /** Reçoit chaque nouvelle position du GPS ou du réseau. */
    private val locationListener = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) = handleLocation(location)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as NoubliApp).container
        locationManager = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sans notification au premier plan ni permission, Android tuerait le service : on abandonne proprement.
        if (!promoteToForeground() || !hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!tracking) {
            tracking = startLocationUpdates()
            if (tracking) observeZones() else stopSelf()
        }
        MonitoringStatus.set(tracking)
        return if (tracking) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        locationManager.removeUpdates(locationListener)
        scope.cancel()
        MonitoringStatus.set(false)
        super.onDestroy()
    }

    /** Affiche la notification permanente et passe le service au premier plan (type « location »). */
    private fun promoteToForeground(): Boolean = try {
        val notification = container.notifier.buildMonitoringNotification(zones.size)
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

    /** Abonne le service aux fournisseurs GPS et réseau actifs ; renvoie false si aucun n'est disponible. */
    @SuppressLint("MissingPermission") // permission vérifiée dans onStartCommand()
    private fun startLocationUpdates(): Boolean {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            Log.w(TAG, "Aucun fournisseur de localisation activé")
            return false
        }
        return try {
            providers.forEach {
                locationManager.requestLocationUpdates(
                    it, UPDATE_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper()
                )
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission de localisation refusée", e)
            false
        }
    }

    /**
     * Suit en continu les zones actives de l'utilisateur connecté : toute modification
     * (ajout, suppression, activation) est prise en compte sans redémarrer le service.
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
                        // Une zone sans objet n'a rien à rappeler : on l'ignore.
                        zones = list.filter { it.items.isNotEmpty() }
                        evaluator.retainOnly(zones.map { it.id })
                        container.notifier.updateMonitoringNotification(zones.size)
                    }
                }
        }
    }

    private fun zonesFlow(userId: Long?): Flow<List<Zone>?> =
        if (userId == null) flowOf(null) else container.zoneRepository.observeActiveZones(userId)

    /** Évalue une position et déclenche les alertes éventuelles. */
    private fun handleLocation(location: Location) {
        val exits = evaluator.onLocation(zones, location.toGeoFix())
        exits.forEach { exit -> scope.launch { raiseAlert(exit) } }
    }

    /** Enregistre l'alerte dans l'historique puis l'affiche. */
    private suspend fun raiseAlert(exit: ZoneExit) {
        val event = container.alertRepository.record(exit.zone, exit.distanceM)
        container.notifier.showForgetAlert(event, exit.zone)
    }

    companion object {
        const val ACTION_START = "com.noubli.app.ACTION_START_MONITORING"
        private const val TAG = "MonitoringService"

        /** Une mesure toutes les 5 s : bon compromis réactivité / batterie. */
        private const val UPDATE_INTERVAL_MS = 5_000L
    }
}
