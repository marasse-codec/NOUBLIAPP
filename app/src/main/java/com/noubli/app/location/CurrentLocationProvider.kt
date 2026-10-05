package com.noubli.app.location

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import com.noubli.app.domain.model.GeoFix
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Convertit une position Android en [GeoFix] (modèle indépendant d'Android). */
fun Location.toGeoFix() = GeoFix(
    latitude = latitude,
    longitude = longitude,
    accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
    timeMs = time
)

/**
 * Obtient la position actuelle une seule fois (bouton « Utiliser ma position »).
 * Utilise le LocationManager du système : aucune dépendance aux services Google.
 */
class CurrentLocationProvider(private val context: Context) {

    private val locationManager: LocationManager =
        context.getSystemService(LocationManager::class.java)

    /** Vrai si la localisation précise est autorisée pour l'application. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Vrai si le GPS / la localisation est activé dans les réglages de l'appareil. */
    fun isLocationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(locationManager)

    /**
     * Attend une première position suffisamment précise (20 s maximum), sinon
     * retombe sur la dernière position connue. Renvoie null si rien n'est disponible.
     */
    suspend fun getCurrent(timeoutMs: Long = 20_000L): GeoFix? {
        if (!hasPermission()) return null
        val providers = enabledProviders()
        if (providers.isEmpty()) return null
        return withTimeoutOrNull(timeoutMs) { awaitAccurateFix(providers) } ?: lastKnown(providers)
    }

    /** Fournisseurs actuellement activés (GPS et réseau). */
    private fun enabledProviders(): List<String> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false)
        }

    @SuppressLint("MissingPermission") // permission vérifiée dans getCurrent()
    private suspend fun awaitAccurateFix(providers: List<String>): GeoFix =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListenerCompat {
                override fun onLocationChanged(location: Location) {
                    val accurate = location.hasAccuracy() && location.accuracy <= GOOD_ACCURACY_M
                    if (accurate && continuation.isActive) {
                        locationManager.removeUpdates(this)
                        continuation.resume(location.toGeoFix())
                    }
                }
            }
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(
                    provider, 1_000L, 0f, listener, Looper.getMainLooper()
                )
            }
            // Annulation (timeout ou écran fermé) : on arrête d'écouter le GPS.
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }

    @SuppressLint("MissingPermission") // permission vérifiée dans getCurrent()
    private fun lastKnown(providers: List<String>): GeoFix? =
        providers
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.toGeoFix()

    private companion object {
        /** Précision (m) jugée suffisante pour enregistrer le centre d'une zone. */
        const val GOOD_ACCURACY_M = 30f
    }
}
