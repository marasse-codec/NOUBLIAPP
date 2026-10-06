package com.noubli.app.domain.sensing

import com.noubli.app.domain.model.GeoFix
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Ports et modèles de la couche « capteurs » (v2).
//
// Le moteur de suivi ne connaît que ces interfaces : GPS réel, trajet simulé ou
// trajet rejoué sont interchangeables. Ajouter un capteur (podomètre, cap…) =
// ajouter une implémentation, sans modifier le moteur (principe ouvert / fermé).
// ---------------------------------------------------------------------------

/** Types de capteurs pouvant contribuer à l'estimation de position. */
enum class SensorKind { GPS, STEPS, HEADING }

/**
 * Position estimée de l'utilisateur après fusion des capteurs.
 *
 * @property sigmaM incertitude (1 écart-type, en mètres) : plus elle est grande,
 *   plus le moteur exige un éloignement important avant d'alerter.
 * @property sources capteurs ayant réellement contribué à cette estimation.
 * @property steps nombre de pas comptés depuis le début du suivi (0 tant qu'il n'y a pas de podomètre).
 * @property pathM déplacement cumulé calculé, en mètres.
 */
data class PositionEstimate(
    val latitude: Double,
    val longitude: Double,
    val sigmaM: Double,
    val timeMs: Long,
    val sources: Set<SensorKind>,
    val steps: Int = 0,
    val pathM: Double = 0.0
)

/** Point du trajet calculé affiché sur le radar. */
data class TrailPoint(val latitude: Double, val longitude: Double)

/**
 * Tous les réglages de détection au même endroit : ajuster le comportement
 * ne demande aucun changement de code ailleurs.
 */
data class DetectionConfig(
    /** Intervalle demandé au GPS (1 s = suivi fin près d'une zone). */
    val sampleIntervalMs: Long = 1_000L,
    /** Les mesures moins précises que ce seuil (m) sont ignorées. */
    val maxFixAccuracyM: Float = 80f,
    /**
     * Prudence : l'alerte exige distance − rayon > k × σ. k = 3 a été retenu par simulation :
     * 0 fausse alerte sur 400 tests de 5 min immobile (k = 2 : 5 % de fausses alertes à 1 m de rayon).
     */
    val confidenceK: Double = 3.0,
    /** Mesures consécutives « clairement dehors » avant d'alerter (3 à 1 Hz ≈ 3 s). */
    val confirmations: Int = 3,
    /** Plancher d'incertitude (m) : évite un seuil irréaliste si le GPS se dit trop précis. */
    val minSigmaM: Double = 1.0,
    /** Nombre maximal de points conservés pour tracer le trajet. */
    val trailSize: Int = 200,
    /** Revient à la règle de la v1 (ProximityEvaluator) : filet de sécurité. */
    val useLegacyPolicy: Boolean = false
)

/** Source de positions (GPS Android, simulateur, rejeu d'un trajet enregistré…). */
interface PositionSource {

    /** Faux si la source ne peut pas fonctionner (ex. GPS désactivé dans les réglages). */
    val isAvailable: Boolean get() = true

    /**
     * Flux de positions. Il est « froid » : la collecte démarre la source et
     * son annulation l'arrête, ce qui évite toute gestion manuelle de start/stop.
     */
    fun fixes(config: DetectionConfig): Flow<GeoFix>
}
