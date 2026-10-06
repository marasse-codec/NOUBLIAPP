package com.noubli.app.replay

import com.noubli.app.data.TraceRepository
import com.noubli.app.domain.model.TracePoint
import com.noubli.app.engine.TrackingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enregistre en base les snapshots d'une zone pendant qu'on appuie sur « Enregistrer le trajet ».
 * Un trajet = une ligne `trace` + une ligne `trace_point` par mesure. Ces données serviront
 * à rejouer un trajet réel et à comparer les réglages sans ressortir (étape de réglage terrain).
 */
class TraceRecorder(private val repository: TraceRepository) {

    private var traceId: Long? = null
    private val _pointCount = MutableStateFlow(0)

    /** Nombre de mesures enregistrées dans le trajet en cours. */
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()

    val isRecording: Boolean get() = traceId != null

    suspend fun start(zoneId: Long, simulated: Boolean) {
        traceId = repository.begin(zoneId, simulated)
        _pointCount.value = 0
    }

    fun stop() {
        traceId = null
    }

    /** Ajoute la mesure courante de [zoneId] au trajet ; sans effet si l'enregistrement est arrêté. */
    suspend fun record(snapshot: TrackingSnapshot, zoneId: Long) {
        val id = traceId ?: return
        val estimate = snapshot.estimate ?: return
        val verdict = snapshot.verdicts.firstOrNull { it.zoneId == zoneId } ?: return
        repository.add(
            id,
            TracePoint(
                timeMs = estimate.timeMs,
                latitude = estimate.latitude,
                longitude = estimate.longitude,
                sigmaM = estimate.sigmaM,
                distanceM = verdict.distanceM,
                pathM = estimate.pathM
            )
        )
        _pointCount.value += 1
    }
}
