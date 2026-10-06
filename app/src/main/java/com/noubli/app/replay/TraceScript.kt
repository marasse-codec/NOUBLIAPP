package com.noubli.app.replay

import com.noubli.app.domain.GeoMath
import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.sensing.DetectionConfig
import com.noubli.app.domain.sensing.PositionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Scénario de test déterministe : l'utilisateur reste immobile au centre de la zone,
 * puis marche en ligne droite. Les mesures GPS sont bruitées de façon réaliste :
 * l'erreur varie lentement (autocorrélation), comme sur un vrai récepteur, ce qui est
 * plus sévère pour la détection qu'un bruit indépendant à chaque seconde.
 *
 * La classe est pure (aucune horloge, aucun Android) : avec la même [seed],
 * on obtient exactement les mêmes mesures, ce qui permet des tests reproductibles.
 */
class TraceScript(
    private val centerLat: Double,
    private val centerLon: Double,
    /** Précision annoncée par le « GPS » simulé (m) ; l'erreur réelle est du même ordre. */
    private val gpsAccuracyM: Double = 4.0,
    private val stationarySec: Int = 10,
    private val walkSec: Int = 40,
    private val speedMps: Double = 1.3,
    private val bearingDeg: Double = 90.0,
    private val seed: Long = 42,
    private val startTimeMs: Long = 0,
    val intervalMs: Long = 1_000L
) {
    /** Durée totale du scénario en mesures (une par intervalle). */
    val totalFixes: Int get() = stationarySec + walkSec + 1

    /** Instant (index de mesure) où la marche commence. */
    val walkStartIndex: Int get() = stationarySec

    /** Position réelle (sans bruit) de l'utilisateur à la mesure [index], en mètres depuis le centre. */
    fun trueDistanceM(index: Int): Double = speedMps * (index - stationarySec).coerceAtLeast(0)

    fun fixes(): Sequence<GeoFix> = sequence {
        val random = Random(seed)
        val bearing = Math.toRadians(bearingDeg)
        // Écart-type par axe : la précision annoncée est un rayon à 68 %, soit ≈ 1,5 σ d'axe.
        val axisSigma = gpsAccuracyM / 1.5
        var errEast = 0.0
        var errNorth = 0.0
        for (i in 0 until totalFixes) {
            // Bruit AR(1) : chaque erreur dépend à 80 % de la précédente.
            errEast = RHO * errEast + sqrt(1 - RHO * RHO) * axisSigma * gaussian(random)
            errNorth = RHO * errNorth + sqrt(1 - RHO * RHO) * axisSigma * gaussian(random)
            val d = trueDistanceM(i)
            val point = GeoMath.offset(
                centerLat, centerLon,
                eastM = d * sin(bearing) + errEast,
                northM = d * cos(bearing) + errNorth
            )
            yield(GeoFix(point.latitude, point.longitude, gpsAccuracyM.toFloat(), startTimeMs + i * intervalMs))
        }
    }

    /** Loi normale centrée réduite (transformation de Box-Muller). */
    private fun gaussian(random: Random): Double {
        val u1 = 1.0 - random.nextDouble() // évite ln(0)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }

    private companion object {
        const val RHO = 0.8
    }
}

/**
 * Source de positions qui rejoue un [TraceScript] en temps réel (ou accéléré).
 * Elle remplace le GPS dans l'écran « Suivi en direct » pour tester sans sortir de chez soi.
 *
 * @param timeScale 1.0 = temps réel ; 4.0 = quatre fois plus vite.
 */
class SimulatedPositionSource(
    private val script: TraceScript,
    private val timeScale: Double = 1.0
) : PositionSource {

    override fun fixes(config: DetectionConfig): Flow<GeoFix> = flow {
        for (fix in script.fixes()) {
            emit(fix)
            delay((script.intervalMs / timeScale).toLong().coerceAtLeast(1L))
        }
    }
}
