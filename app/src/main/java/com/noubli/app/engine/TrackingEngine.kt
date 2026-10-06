package com.noubli.app.engine

import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.model.ZoneExit
import com.noubli.app.domain.sensing.DetectionConfig
import com.noubli.app.domain.sensing.PositionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Port de sortie : ce que fait l'application quand une sortie de zone est confirmée. */
fun interface AlertChannel {
    suspend fun alert(exit: ZoneExit)

    companion object {
        /** Canal muet : utilisé par la simulation, qui ne doit ni notifier ni écrire dans l'historique. */
        val None = AlertChannel { }
    }
}

/**
 * Orchestrateur du suivi : lit les positions d'une [PositionSource], les passe à la
 * [TrackingPipeline], publie le snapshot sur le [TrackingBus] et déclenche les alertes.
 *
 * Il ne connaît aucune classe Android : le même moteur sert au vrai GPS (service de
 * surveillance) et à la simulation (écran « Suivi en direct »).
 * Tout se déroule sur le dispatcher du [CoroutineScope] fourni (le thread principal en pratique),
 * il n'y a donc pas d'accès concurrent.
 */
class TrackingEngine(
    private val source: PositionSource,
    private val pipeline: TrackingPipeline,
    private val bus: TrackingBus,
    private val alerts: AlertChannel,
    private val config: DetectionConfig,
    /** Appelé si la source s'arrête sur une erreur (ex. permission retirée) : le moteur ne plante pas l'application. */
    private val onError: (Throwable) -> Unit = {}
) {
    private var job: Job? = null
    private var zones: List<Zone> = emptyList()

    val isRunning: Boolean get() = job?.isActive == true

    /** Met à jour les zones suivies (appelé à chaque modification en base). */
    fun setZones(list: List<Zone>) {
        zones = list
        pipeline.retainOnly(list.map { it.id })
    }

    /** Démarre la lecture des positions ; sans effet si déjà démarré. */
    fun start(scope: CoroutineScope) {
        if (isRunning) return
        pipeline.reset()
        job = scope.launch {
            try {
                source.fixes(config).collect(::handle)
            } catch (e: CancellationException) {
                throw e // arrêt normal demandé par stop()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /** Arrête la lecture et efface l'état publié. */
    fun stop() {
        job?.cancel()
        job = null
        bus.clear()
    }

    private suspend fun handle(fix: GeoFix) {
        val output = pipeline.onFix(fix, zones)
        bus.publish(output.snapshot)
        output.exits.forEach { alerts.alert(it) }
    }
}
