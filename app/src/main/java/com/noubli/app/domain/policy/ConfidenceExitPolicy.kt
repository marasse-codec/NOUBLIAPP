package com.noubli.app.domain.policy

import com.noubli.app.domain.GeoMath
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.sensing.PositionEstimate
import kotlin.math.max

/**
 * Règle de sortie fondée sur la confiance : on alerte quand la distance dépasse le
 * rayon d'une marge proportionnelle à l'incertitude de la position (k × σ).
 *
 * Pour chaque zone, un petit automate à trois phases (comme en v1) évite les fausses alertes :
 *  - UNKNOWN : rien de fiable reçu (au démarrage, jamais d'alerte) ;
 *  - ARMED   : l'utilisateur est plausiblement dans la zone (distance ≤ rayon + σ) ;
 *  - OUTSIDE : alerte émise (ou démarrage déjà dehors) ; réarmée au retour dans la zone.
 *
 * L'alerte exige [confirmations] mesures consécutives au-delà du seuil rayon + k × σ.
 * Plus le GPS est mauvais (σ grand), plus l'alerte est tardive mais plus elle est fiable :
 * c'est le choix de prudence retenu pour la v2.
 */
class ConfidenceExitPolicy(
    private val k: Double = 3.0,
    private val confirmations: Int = 3,
    private val minSigmaM: Double = 1.0
) : ExitPolicy {

    private enum class Phase { UNKNOWN, ARMED, OUTSIDE }

    private class Tracker {
        var phase = Phase.UNKNOWN
        var outsideCount = 0
    }

    private val trackers = mutableMapOf<Long, Tracker>()

    override fun evaluate(zones: List<Zone>, estimate: PositionEstimate): List<ZoneVerdict> {
        val sigma = max(estimate.sigmaM, minSigmaM)
        return zones.map { zone ->
            val distance = GeoMath.distanceMeters(
                estimate.latitude, estimate.longitude, zone.latitude, zone.longitude
            )
            val trigger = zone.radiusM + k * sigma
            val exited = advance(trackers.getOrPut(zone.id) { Tracker() }, distance, zone.radiusM, sigma, trigger)
            ZoneVerdict(
                zoneId = zone.id,
                radiusM = zone.radiusM,
                state = when {
                    distance <= zone.radiusM -> ZoneState.INSIDE
                    distance > trigger -> ZoneState.OUTSIDE
                    else -> ZoneState.AMBIGUOUS
                },
                distanceM = distance,
                marginM = distance - zone.radiusM,
                triggerDistanceM = trigger,
                sigmaM = sigma,
                exited = exited
            )
        }
    }

    /** Fait avancer l'automate d'une zone ; renvoie true si la sortie vient d'être confirmée. */
    private fun advance(t: Tracker, distance: Double, radius: Int, sigma: Double, trigger: Double): Boolean {
        val plausiblyInside = distance <= radius + sigma
        val clearlyOutside = distance > trigger
        when (t.phase) {
            Phase.UNKNOWN -> {
                if (plausiblyInside) t.phase = Phase.ARMED
                else if (clearlyOutside) t.phase = Phase.OUTSIDE // déjà dehors au démarrage : pas d'alerte
            }
            Phase.ARMED -> {
                if (clearlyOutside) {
                    t.outsideCount++
                    if (t.outsideCount >= confirmations) {
                        t.phase = Phase.OUTSIDE
                        t.outsideCount = 0
                        return true
                    }
                } else {
                    t.outsideCount = 0 // les confirmations doivent être consécutives
                }
            }
            Phase.OUTSIDE -> if (plausiblyInside) {
                t.phase = Phase.ARMED // retour dans la zone : l'alerte est de nouveau armée
                t.outsideCount = 0
            }
        }
        return false
    }

    override fun retainOnly(zoneIds: Collection<Long>) {
        trackers.keys.retainAll(zoneIds.toSet())
    }

    override fun reset() = trackers.clear()
}
