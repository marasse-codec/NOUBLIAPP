package com.noubli.app.domain.policy

import com.noubli.app.domain.GeoMath
import com.noubli.app.domain.ProximityEvaluator
import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.sensing.PositionEstimate

/**
 * Règle de la v1 ([ProximityEvaluator]) exposée sous l'interface [ExitPolicy].
 * Activable avec `DetectionConfig.useLegacyPolicy` : filet de sécurité si la
 * nouvelle règle posait problème sur le terrain.
 */
class LegacyThresholdPolicy(
    private val evaluator: ProximityEvaluator = ProximityEvaluator()
) : ExitPolicy {

    override fun evaluate(zones: List<Zone>, estimate: PositionEstimate): List<ZoneVerdict> {
        val fix = GeoFix(
            latitude = estimate.latitude,
            longitude = estimate.longitude,
            accuracyM = estimate.sigmaM.toFloat(),
            timeMs = estimate.timeMs
        )
        val exitedIds = evaluator.onLocation(zones, fix).map { it.zone.id }.toSet()
        return zones.map { zone ->
            val distance = GeoMath.distanceMeters(
                estimate.latitude, estimate.longitude, zone.latitude, zone.longitude
            )
            val trigger = zone.radiusM + estimate.sigmaM * LEGACY_FACTOR
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
                sigmaM = estimate.sigmaM,
                exited = zone.id in exitedIds
            )
        }
    }

    override fun retainOnly(zoneIds: Collection<Long>) = evaluator.retainOnly(zoneIds)

    override fun reset() = evaluator.reset()

    private companion object {
        /** Même part d'imprécision que ProximityEvaluator (accuracyFactor). */
        const val LEGACY_FACTOR = 0.5
    }
}
