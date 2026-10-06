package com.noubli.app.engine

import com.noubli.app.domain.GeoMath
import com.noubli.app.domain.fusion.GpsOnlyFusion
import com.noubli.app.domain.fusion.PositionFusion
import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.model.ZoneExit
import com.noubli.app.domain.policy.ConfidenceExitPolicy
import com.noubli.app.domain.policy.ExitPolicy
import com.noubli.app.domain.policy.LegacyThresholdPolicy
import com.noubli.app.domain.sensing.DetectionConfig
import com.noubli.app.domain.sensing.TrailPoint
import kotlin.math.roundToInt

/** Résultat du traitement d'une mesure : nouvel état à afficher et alertes à émettre. */
data class PipelineOutput(val snapshot: TrackingSnapshot, val exits: List<ZoneExit>)

/**
 * Chaîne de traitement synchrone et sans Android : mesure -> fusion -> règle de sortie.
 * Elle est séparée du moteur coroutine ([TrackingEngine]) pour être testée en JVM pur,
 * avec des trajets simulés, sans aucun délai réel.
 */
class TrackingPipeline(
    private val config: DetectionConfig,
    private val fusion: PositionFusion,
    private val policy: ExitPolicy
) {
    private val trail = ArrayDeque<TrailPoint>()
    private var lastExit: ExitMark? = null
    private var current = TrackingSnapshot.EMPTY

    /** Traite une position GPS pour les zones données. */
    fun onFix(fix: GeoFix, zones: List<Zone>): PipelineOutput {
        // Mesure écartée par la fusion (trop imprécise, trop ancienne…) : rien ne change.
        if (!fusion.onFix(fix)) return PipelineOutput(current, emptyList())
        val estimate = fusion.estimate ?: return PipelineOutput(current, emptyList())

        val verdicts = policy.evaluate(zones, estimate)
        addToTrail(estimate.latitude, estimate.longitude)

        val exited = verdicts.filter { it.exited }
        exited.lastOrNull()?.let {
            lastExit = ExitMark(it.zoneId, it.distanceM.roundToInt(), estimate.timeMs)
        }
        // Une zone sans objet n'a rien à rappeler : elle s'affiche mais n'alerte pas.
        val withItems = zones.filter { it.items.isNotEmpty() }.associateBy { it.id }
        val exits = exited.mapNotNull { v ->
            withItems[v.zoneId]?.let { ZoneExit(it, v.distanceM.roundToInt()) }
        }

        current = TrackingSnapshot(estimate, verdicts, trail.toList(), estimate.sources, lastExit)
        return PipelineOutput(current, exits)
    }

    /** Oublie les zones supprimées ou désactivées. */
    fun retainOnly(zoneIds: Collection<Long>) = policy.retainOnly(zoneIds)

    /** Nouveau départ : efface position, trajet et mémoire de la règle. */
    fun reset() {
        fusion.reset()
        policy.reset()
        trail.clear()
        lastExit = null
        current = TrackingSnapshot.EMPTY
    }

    /** N'ajoute un point que s'il s'est écoulé au moins 0,5 m : évite de noircir le radar quand on est immobile. */
    private fun addToTrail(lat: Double, lon: Double) {
        val last = trail.lastOrNull()
        if (last != null && GeoMath.distanceMeters(last.latitude, last.longitude, lat, lon) < MIN_TRAIL_STEP_M) return
        trail.addLast(TrailPoint(lat, lon))
        while (trail.size > config.trailSize) trail.removeFirst()
    }

    companion object {
        private const val MIN_TRAIL_STEP_M = 0.5

        /** Assemble la chaîne par défaut selon la configuration (point de choix unique des implémentations). */
        fun create(config: DetectionConfig = DetectionConfig()): TrackingPipeline = TrackingPipeline(
            config = config,
            fusion = GpsOnlyFusion(config.maxFixAccuracyM),
            policy = if (config.useLegacyPolicy) LegacyThresholdPolicy()
            else ConfidenceExitPolicy(config.confidenceK, config.confirmations, config.minSigmaM)
        )
    }
}
