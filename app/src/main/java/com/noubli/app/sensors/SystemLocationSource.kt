package com.noubli.app.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.sensing.DetectionConfig
import com.noubli.app.domain.sensing.PositionSource
import com.noubli.app.location.toGeoFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Adaptateur du GPS Android (LocationManager) vers le port [PositionSource].
 *
 * Il écoute les fournisseurs GPS et réseau activés. La fusion se charge ensuite d'écarter
 * les mesures réseau grossières quand une position GPS précise est disponible.
 * Le Fused Location de Google Play Services sera un autre adaptateur du même port
 * (voir la conception v2) : ce fichier restera le repli quand Play Services est absent.
 */
class SystemLocationSource(context: Context) : PositionSource {

    private val locationManager: LocationManager =
        context.applicationContext.getSystemService(LocationManager::class.java)

    private fun enabledProviders(): List<String> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }

    override val isAvailable: Boolean get() = enabledProviders().isNotEmpty()

    @SuppressLint("MissingPermission") // la permission est vérifiée par le service avant de collecter
    override fun fixes(config: DetectionConfig): Flow<GeoFix> = callbackFlow {
        val listener = object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                trySend(location.toGeoFix())
            }
        }
        val providers = enabledProviders()
        if (providers.isEmpty()) {
            close(IllegalStateException("Aucun fournisseur de localisation activé"))
        } else {
            try {
                providers.forEach {
                    locationManager.requestLocationUpdates(
                        it, config.sampleIntervalMs, 0f, listener, Looper.getMainLooper()
                    )
                }
            } catch (e: SecurityException) {
                close(e) // permission retirée entre-temps
            }
        }
        // Appelé à l'annulation de la collecte : on se désabonne du GPS.
        awaitClose { locationManager.removeUpdates(listener) }
    }
}
