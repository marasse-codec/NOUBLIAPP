package com.noubli.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.noubli.app.AppContainer
import com.noubli.app.domain.model.Zone
import com.noubli.app.engine.AlertChannel
import com.noubli.app.engine.TrackingBus
import com.noubli.app.engine.TrackingEngine
import com.noubli.app.engine.TrackingPipeline
import com.noubli.app.engine.TrackingSnapshot
import com.noubli.app.replay.SimulatedPositionSource
import com.noubli.app.replay.TraceRecorder
import com.noubli.app.replay.TraceScript
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Logique de l'écran « Suivi en direct » d'une zone.
 *
 * Deux sources d'affichage, au choix : le moteur réel (celui du service de surveillance,
 * via le bus partagé) ou une simulation locale qui a son propre moteur, son propre bus et
 * aucun canal d'alerte : elle n'envoie donc ni notification ni entrée d'historique.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModel(
    private val zoneId: Long,
    container: AppContainer
) : ViewModel() {

    private val config = container.detectionConfig
    private val realBus = container.trackingBus
    private val simBus = TrackingBus()
    private var simEngine: TrackingEngine? = null
    private val recorder = TraceRecorder(container.traceRepository)

    /** La zone affichée (null si elle a été supprimée entre-temps). */
    val zone: StateFlow<Zone?> = container.session.userId
        .flatMapLatest { userId ->
            if (userId == null) flowOf<Zone?>(null)
            else container.zoneRepository.observeZones(userId).map { list -> list.firstOrNull { it.id == zoneId } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _simulating = MutableStateFlow(false)
    val simulating: StateFlow<Boolean> = _simulating.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    /** Mesures du trajet en cours d'enregistrement. */
    val recordedPoints: StateFlow<Int> = recorder.pointCount

    /** Nombre de trajets déjà enregistrés pour cette zone. */
    val traceCount: StateFlow<Int> = container.traceRepository.observeTraceCount(zoneId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** Ce que l'écran affiche : la simulation si elle est lancée, sinon le suivi réel. */
    val snapshot: StateFlow<TrackingSnapshot> = _simulating
        .flatMapLatest { sim -> if (sim) simBus.snapshot else realBus.snapshot }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TrackingSnapshot.EMPTY)

    init {
        // Chaque nouveau snapshot est ajouté au trajet tant que l'enregistrement est actif.
        viewModelScope.launch {
            snapshot.collect { if (_recording.value) recorder.record(it, zoneId) }
        }
    }

    /** Lance (ou relance) la simulation : 8 s immobile au centre, puis 40 s de marche, GPS à ±4 m. */
    fun startSimulation() {
        val target = zone.value ?: return
        stopSimulation()
        stopRecording() // le trajet en cours venait d'une autre source
        val script = TraceScript(
            centerLat = target.latitude,
            centerLon = target.longitude,
            gpsAccuracyM = SIM_GPS_ACCURACY_M,
            stationarySec = 8,
            walkSec = 40,
            seed = Random.nextLong()
        )
        val engine = TrackingEngine(
            source = SimulatedPositionSource(script, timeScale = SIM_TIME_SCALE),
            pipeline = TrackingPipeline.create(config),
            bus = simBus,
            alerts = AlertChannel.None,
            config = config
        )
        engine.setZones(listOf(target))
        engine.start(viewModelScope)
        simEngine = engine
        _simulating.value = true
    }

    fun stopSimulation() {
        simEngine?.stop()
        simEngine = null
        _simulating.value = false
        stopRecording()
    }

    /** Démarre ou arrête l'enregistrement du trajet affiché. */
    fun toggleRecording() {
        if (_recording.value) {
            stopRecording()
        } else {
            viewModelScope.launch {
                recorder.start(zoneId, simulated = _simulating.value)
                _recording.value = true
            }
        }
    }

    private fun stopRecording() {
        recorder.stop()
        _recording.value = false
    }

    override fun onCleared() {
        simEngine?.stop()
        recorder.stop()
        super.onCleared()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SIM_GPS_ACCURACY_M = 4.0
        /** La simulation va deux fois plus vite que le temps réel (48 s de scénario en 24 s). */
        private const val SIM_TIME_SCALE = 2.0

        fun factory(container: AppContainer, zoneId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { LiveViewModel(zoneId, container) }
        }
    }
}
