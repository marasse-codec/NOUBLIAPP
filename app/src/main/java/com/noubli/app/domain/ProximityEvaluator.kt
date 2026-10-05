package com.noubli.app.domain

import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.model.ZoneExit
import kotlin.math.roundToInt

/**
 * Cœur métier : décide à chaque position GPS si l'utilisateur vient de quitter une zone.
 *
 * Pour chaque zone, un petit automate à trois états est suivi :
 *  - UNKNOWN : aucune mesure fiable reçue pour l'instant ;
 *  - INSIDE  : l'utilisateur est dans le cercle (alerte « armée ») ;
 *  - OUTSIDE : l'utilisateur est dehors (alerte déjà émise ou non applicable).
 *
 * Une alerte n'est émise que lors du passage INSIDE -> OUTSIDE, après
 * [confirmationsNeeded] mesures consécutives clairement à l'extérieur. Cela évite
 * les fausses alertes dues aux sauts de précision du GPS près de la bordure.
 * Si l'app démarre alors que l'utilisateur est déjà dehors, aucune alerte n'est émise.
 *
 * La classe n'est pas thread-safe : elle est appelée depuis le thread principal du service.
 */
class ProximityEvaluator(
    /** Les mesures moins précises que ce seuil (en mètres) sont ignorées. */
    private val maxAccuracyM: Float = 80f,
    /** Nombre de mesures consécutives à l'extérieur avant d'alerter. */
    private val confirmationsNeeded: Int = 2,
    /** Part de l'imprécision GPS ajoutée au rayon pour être « clairement dehors ». */
    private val accuracyFactor: Double = 0.5
) {

    private enum class Phase { UNKNOWN, INSIDE, OUTSIDE }

    /** État mémorisé pour une zone. */
    private class Tracker {
        var phase = Phase.UNKNOWN
        var outsideCount = 0
    }

    private val trackers = mutableMapOf<Long, Tracker>()

    /** Traite une nouvelle position et renvoie les zones qui viennent d'être quittées. */
    fun onLocation(zones: List<Zone>, fix: GeoFix): List<ZoneExit> {
        if (fix.accuracyM > maxAccuracyM) return emptyList()

        val exits = mutableListOf<ZoneExit>()
        for (zone in zones) {
            val tracker = trackers.getOrPut(zone.id) { Tracker() }
            val distance = GeoMath.distanceMeters(
                fix.latitude, fix.longitude, zone.latitude, zone.longitude
            )
            if (update(tracker, zone, distance, fix.accuracyM)) {
                exits += ZoneExit(zone, distance.roundToInt())
            }
        }
        return exits
    }

    /** Met à jour l'automate d'une zone ; renvoie true si une alerte doit être émise. */
    private fun update(tracker: Tracker, zone: Zone, distance: Double, accuracyM: Float): Boolean {
        val inside = distance <= zone.radiusM
        val clearlyOutside = distance > zone.radiusM + accuracyM * accuracyFactor

        when (tracker.phase) {
            Phase.UNKNOWN -> {
                // Pas d'alerte au démarrage : on mémorise simplement la situation.
                if (inside) tracker.phase = Phase.INSIDE
                else if (clearlyOutside) tracker.phase = Phase.OUTSIDE
            }
            Phase.INSIDE -> {
                if (inside) {
                    tracker.outsideCount = 0
                } else if (clearlyOutside) {
                    tracker.outsideCount++
                    if (tracker.outsideCount >= confirmationsNeeded) {
                        tracker.phase = Phase.OUTSIDE
                        tracker.outsideCount = 0
                        return true
                    }
                }
                // Entre le rayon et le rayon + marge : zone ambiguë, on ne change rien.
            }
            Phase.OUTSIDE -> {
                // Retour dans la zone : l'alerte est de nouveau armée.
                if (inside) {
                    tracker.phase = Phase.INSIDE
                    tracker.outsideCount = 0
                }
            }
        }
        return false
    }

    /** Oublie les zones supprimées ou désactivées. */
    fun retainOnly(zoneIds: Collection<Long>) {
        trackers.keys.retainAll(zoneIds.toSet())
    }

    /** Remet tous les états à zéro (ex. redémarrage de la surveillance). */
    fun reset() = trackers.clear()
}
