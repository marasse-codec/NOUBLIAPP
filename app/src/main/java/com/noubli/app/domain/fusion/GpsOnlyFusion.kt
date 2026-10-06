package com.noubli.app.domain.fusion

import com.noubli.app.domain.GeoMath
import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.sensing.PositionEstimate
import com.noubli.app.domain.sensing.SensorKind
import kotlin.math.max

/**
 * Port de fusion : transforme les mesures brutes des capteurs en une position
 * estimée avec son incertitude. La v2 fournit [GpsOnlyFusion] ; la fusion
 * GPS + pas + cap viendra comme une autre implémentation de cette interface.
 */
interface PositionFusion {

    /** Dernière estimation, ou null tant qu'aucune mesure exploitable n'a été reçue. */
    val estimate: PositionEstimate?

    /** Intègre une mesure GPS ; renvoie true si l'estimation a changé. */
    fun onFix(fix: GeoFix): Boolean

    /** Oublie tout (nouveau départ). */
    fun reset()
}

/**
 * Fusion minimale : l'estimation est la meilleure mesure GPS récente.
 *
 * Deux protections contre les fournisseurs mélangés (GPS précis + réseau grossier) :
 *  - une mesure bien moins précise que l'estimation courante est ignorée, tant que
 *    l'estimation est récente (sinon la position « sauterait » de 30 m) ;
 *  - une mesure plus ancienne que l'estimation courante est ignorée.
 *
 * Le déplacement cumulé ([PositionEstimate.pathM]) ne compte un segment que s'il
 * dépasse le bruit du GPS, sinon un utilisateur immobile « marcherait » sur place.
 */
class GpsOnlyFusion(
    private val maxFixAccuracyM: Float = 80f
) : PositionFusion {

    override var estimate: PositionEstimate? = null
        private set

    /** Dernier point à partir duquel on compte le déplacement cumulé. */
    private var anchor: GeoFix? = null
    private var pathM = 0.0

    override fun onFix(fix: GeoFix): Boolean {
        if (fix.accuracyM > maxFixAccuracyM) return false
        val current = estimate
        if (current != null) {
            if (fix.timeMs < current.timeMs) return false
            val fresh = fix.timeMs - current.timeMs < STALE_MS
            if (fresh && fix.accuracyM > current.sigmaM * WORSE_FACTOR) return false
        }
        accumulatePath(fix)
        estimate = PositionEstimate(
            latitude = fix.latitude,
            longitude = fix.longitude,
            sigmaM = fix.accuracyM.toDouble(),
            timeMs = fix.timeMs,
            sources = setOf(SensorKind.GPS),
            pathM = pathM
        )
        return true
    }

    private fun accumulatePath(fix: GeoFix) {
        val from = anchor
        if (from == null) {
            anchor = fix
            return
        }
        val d = GeoMath.distanceMeters(from.latitude, from.longitude, fix.latitude, fix.longitude)
        if (d > max(MIN_SEGMENT_M, NOISE_FACTOR * fix.accuracyM)) {
            pathM += d
            anchor = fix
        }
    }

    override fun reset() {
        estimate = null
        anchor = null
        pathM = 0.0
    }

    private companion object {
        /** Une mesure pire que 2 × l'incertitude courante est jugée trop grossière. */
        const val WORSE_FACTOR = 2.0
        /** Au-delà de 10 s sans mesure, on accepte à nouveau n'importe quelle mesure valide. */
        const val STALE_MS = 10_000L
        const val MIN_SEGMENT_M = 2.0
        const val NOISE_FACTOR = 0.5
    }
}
