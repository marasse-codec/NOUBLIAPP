package com.noubli.app.engine

import com.noubli.app.domain.model.ReminderItem
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.policy.ZoneState
import com.noubli.app.domain.sensing.DetectionConfig
import com.noubli.app.replay.TraceScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de bout en bout de la chaîne mesure -> fusion -> règle de sortie,
 * sur des trajets simulés reproductibles. Ils chiffrent les promesses de la v2 :
 * zéro fausse alerte à l'arrêt, un délai de détection borné à la marche.
 */
class TrackingPipelineTest {

    private val lat = 48.8566
    private val lon = 2.3522

    private fun zone(radius: Int, items: List<ReminderItem> = listOf(ReminderItem(label = "clés"))) = Zone(
        id = 1, userId = 1, name = "Maison", latitude = lat, longitude = lon, radiusM = radius, items = items
    )

    /** Passe tout un scénario dans une chaîne neuve ; renvoie l'index de la 1re alerte (ou null) et leur nombre. */
    private fun run(script: TraceScript, zone: Zone): Pair<Int?, Int> {
        val pipeline = TrackingPipeline.create(DetectionConfig())
        var firstAlert: Int? = null
        var alerts = 0
        script.fixes().forEachIndexed { index, fix ->
            val out = pipeline.onFix(fix, listOf(zone))
            if (out.exits.isNotEmpty()) {
                alerts += out.exits.size
                if (firstAlert == null) firstAlert = index
            }
        }
        return firstAlert to alerts
    }

    @Test
    fun `aucune fausse alerte a l'arret avec un GPS bruite (50 scenarios)`() {
        for (seed in 1L..50L) {
            val script = TraceScript(lat, lon, gpsAccuracyM = 4.0, stationarySec = 300, walkSec = 0, seed = seed)
            val (_, alerts) = run(script, zone(radius = 1))
            assertEquals("seed $seed", 0, alerts)
        }
    }

    @Test
    fun `la sortie est detectee une seule fois dans un delai borne (50 scenarios)`() {
        for (seed in 1L..50L) {
            val script = TraceScript(lat, lon, gpsAccuracyM = 4.0, stationarySec = 10, walkSec = 40, seed = seed)
            val (first, alerts) = run(script, zone(radius = 5))
            assertEquals("une seule alerte, seed $seed", 1, alerts)
            // Seuil = 5 + 3 x 4 = 17 m, soit 13 s de marche, + 3 confirmations : environ 16 s en moyenne.
            val secondsAfterStart = first!! - script.walkStartIndex
            assertTrue("délai $secondsAfterStart s, seed $seed", secondsAfterStart in 6..30)
            // L'alerte ne doit jamais partir avant d'avoir réellement dépassé le rayon.
            assertTrue("trop tôt, seed $seed", script.trueDistanceM(first) > 5.0)
        }
    }

    @Test
    fun `le snapshot expose le verdict et le trajet`() {
        val script = TraceScript(lat, lon, stationarySec = 5, walkSec = 30, seed = 7)
        val pipeline = TrackingPipeline.create()
        var last = TrackingSnapshot.EMPTY
        script.fixes().forEach { last = pipeline.onFix(it, listOf(zone(5))).snapshot }

        val verdict = last.verdicts.single()
        assertEquals(ZoneState.OUTSIDE, verdict.state)
        assertTrue(verdict.distanceM > verdict.triggerDistanceM)
        assertTrue(last.trail.size > 10)
        assertNotNull(last.lastExit)
        assertTrue(last.estimate!!.pathM > 20.0)
    }

    @Test
    fun `une zone sans objet est evaluee mais n'alerte pas`() {
        val script = TraceScript(lat, lon, stationarySec = 5, walkSec = 30, seed = 3)
        val (_, alerts) = run(script, zone(5, items = emptyList()))
        assertEquals(0, alerts)
    }

    @Test
    fun `reset efface le trajet et la derniere sortie`() {
        val script = TraceScript(lat, lon, stationarySec = 5, walkSec = 30, seed = 3)
        val pipeline = TrackingPipeline.create()
        script.fixes().forEach { pipeline.onFix(it, listOf(zone(5))) }
        pipeline.reset()
        val out = pipeline.onFix(script.fixes().first(), listOf(zone(5)))
        assertEquals(1, out.snapshot.trail.size)
        assertNull(out.snapshot.lastExit)
    }

    @Test
    fun `la regle v1 reste utilisable en secours`() {
        val script = TraceScript(lat, lon, stationarySec = 10, walkSec = 40, seed = 5)
        val pipeline = TrackingPipeline.create(DetectionConfig(useLegacyPolicy = true))
        var alerts = 0
        script.fixes().forEach { alerts += pipeline.onFix(it, listOf(zone(20))).exits.size }
        assertEquals(1, alerts)
    }
}
