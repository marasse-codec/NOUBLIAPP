package com.noubli.app.engine

import com.noubli.app.domain.policy.ZoneVerdict
import com.noubli.app.domain.sensing.PositionEstimate
import com.noubli.app.domain.sensing.SensorKind
import com.noubli.app.domain.sensing.TrailPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dernière sortie confirmée : conservée jusqu'au prochain démarrage pour rester visible à l'écran. */
data class ExitMark(val zoneId: Long, val distanceM: Int, val timeMs: Long)

/**
 * Photographie complète de l'état du suivi : c'est le SEUL objet lu par l'écran
 * « Suivi en direct ». Changer l'algorithme ne change donc pas l'interface.
 */
data class TrackingSnapshot(
    val estimate: PositionEstimate?,
    val verdicts: List<ZoneVerdict>,
    val trail: List<TrailPoint>,
    val activeSources: Set<SensorKind>,
    val lastExit: ExitMark?
) {
    companion object {
        /** État vide : aucun suivi en cours. */
        val EMPTY = TrackingSnapshot(null, emptyList(), emptyList(), emptySet(), null)
    }
}

/**
 * Canal de diffusion du snapshot : le moteur publie, l'UI observe.
 * Une instance « réelle » vit dans AppContainer ; la simulation en crée une à part.
 */
class TrackingBus {
    private val _snapshot = MutableStateFlow(TrackingSnapshot.EMPTY)
    val snapshot: StateFlow<TrackingSnapshot> = _snapshot.asStateFlow()

    fun publish(snapshot: TrackingSnapshot) {
        _snapshot.value = snapshot
    }

    /** Efface l'état (arrêt du suivi). */
    fun clear() {
        _snapshot.value = TrackingSnapshot.EMPTY
    }
}
