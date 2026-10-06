package com.noubli.app.domain.policy

import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.sensing.PositionEstimate

/**
 * Situation de l'utilisateur par rapport à une zone, telle qu'affichée sur le radar :
 *  - INSIDE : le centre de l'estimation est dans le rayon ;
 *  - AMBIGUOUS : entre le rayon et le seuil de déclenchement (on ne peut pas conclure) ;
 *  - OUTSIDE : au-delà du seuil de déclenchement (clairement dehors).
 */
enum class ZoneState { INSIDE, AMBIGUOUS, OUTSIDE }

/**
 * Verdict de la règle de sortie pour une zone à un instant donné.
 *
 * @property marginM distance − rayon (négatif = à l'intérieur).
 * @property triggerDistanceM distance au centre à partir de laquelle on est « clairement dehors »
 *   (rayon + k × σ) : c'est le rayon d'alerte réellement appliqué.
 * @property exited vrai uniquement à la mesure qui confirme la sortie.
 */
data class ZoneVerdict(
    val zoneId: Long,
    val radiusM: Int,
    val state: ZoneState,
    val distanceM: Double,
    val marginM: Double,
    val triggerDistanceM: Double,
    val sigmaM: Double,
    val exited: Boolean
)

/** Port de décision : transforme une position estimée en verdicts par zone. */
interface ExitPolicy {

    /** Évalue toutes les zones fournies ; la règle garde sa propre mémoire entre deux appels. */
    fun evaluate(zones: List<Zone>, estimate: PositionEstimate): List<ZoneVerdict>

    /** Oublie les zones supprimées ou désactivées. */
    fun retainOnly(zoneIds: Collection<Long>)

    /** Remet toute la mémoire à zéro. */
    fun reset()
}
