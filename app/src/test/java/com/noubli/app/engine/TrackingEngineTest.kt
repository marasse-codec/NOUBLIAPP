package com.noubli.app.engine

import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.model.ReminderItem
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.model.ZoneExit
import com.noubli.app.domain.sensing.DetectionConfig
import com.noubli.app.domain.sensing.PositionSource
import com.noubli.app.replay.SimulatedPositionSource
import com.noubli.app.replay.TraceScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests du moteur coroutine : source simulée accélérée, canal d'alerte compté, panne de source. */
class TrackingEngineTest {

    private val lat = 48.8566
    private val lon = 2.3522
    private val config = DetectionConfig()
    private val zone = Zone(
        id = 1, userId = 1, name = "Maison", latitude = lat, longitude = lon, radiusM = 5,
        items = listOf(ReminderItem(label = "clés"))
    )

    /** Attend la fin de la lecture de la source (au plus 10 s). */
    private suspend fun awaitEnd(engine: TrackingEngine) = withTimeout(10_000) {
        while (engine.isRunning) delay(5)
    }

    @Test
    fun `le moteur publie le snapshot et declenche une seule alerte`() = runBlocking {
        val exits = mutableListOf<ZoneExit>()
        val bus = TrackingBus()
        val script = TraceScript(lat, lon, stationarySec = 8, walkSec = 40, seed = 11)
        val engine = TrackingEngine(
            source = SimulatedPositionSource(script, timeScale = 100_000.0),
            pipeline = TrackingPipeline.create(config),
            bus = bus,
            alerts = AlertChannel { exits += it },
            config = config
        )
        engine.setZones(listOf(zone))
        engine.start(CoroutineScope(Dispatchers.Default))
        awaitEnd(engine)

        assertEquals(1, exits.size)
        assertTrue(exits.single().distanceM > 5)
        assertNotNull(bus.snapshot.value.lastExit)
        assertEquals(1, bus.snapshot.value.verdicts.size)
    }

    @Test
    fun `stop efface l'etat publie`() = runBlocking {
        val bus = TrackingBus()
        val script = TraceScript(lat, lon, stationarySec = 3, walkSec = 3, seed = 2)
        val engine = TrackingEngine(
            SimulatedPositionSource(script, timeScale = 100_000.0), TrackingPipeline.create(config),
            bus, AlertChannel.None, config
        )
        engine.setZones(listOf(zone))
        engine.start(CoroutineScope(Dispatchers.Default))
        awaitEnd(engine)
        assertNotNull(bus.snapshot.value.estimate)
        engine.stop()
        assertNull(bus.snapshot.value.estimate)
    }

    @Test
    fun `une panne de la source est signalee sans faire planter le moteur`() = runBlocking {
        val failing = object : PositionSource {
            override fun fixes(config: DetectionConfig): Flow<GeoFix> = flow {
                throw IllegalStateException("GPS indisponible")
            }
        }
        var error: Throwable? = null
        val engine = TrackingEngine(
            failing, TrackingPipeline.create(config), TrackingBus(), AlertChannel.None, config,
            onError = { error = it }
        )
        engine.start(CoroutineScope(Dispatchers.Default))
        awaitEnd(engine)
        assertEquals("GPS indisponible", error?.message)
    }
}
