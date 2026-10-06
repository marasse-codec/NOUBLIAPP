package com.noubli.app.domain

import com.noubli.app.domain.fusion.GpsOnlyFusion
import com.noubli.app.domain.model.GeoFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la fusion « GPS seul » : filtrage des mesures et déplacement cumulé. */
class GpsOnlyFusionTest {

    private val lat = 48.8566
    private val lon = 2.3522

    /** Mesure [northM] mètres au nord du point de référence. */
    private fun fix(northM: Double, accuracy: Float = 5f, timeMs: Long = 0L) =
        GeoFix(lat + northM / 111_195.0, lon, accuracy, timeMs)

    @Test
    fun `aucune estimation avant la premiere mesure`() {
        assertNull(GpsOnlyFusion().estimate)
    }

    @Test
    fun `une mesure trop imprecise est ignoree`() {
        val fusion = GpsOnlyFusion(maxFixAccuracyM = 50f)
        assertFalse(fusion.onFix(fix(0.0, accuracy = 120f)))
        assertNull(fusion.estimate)
    }

    @Test
    fun `l'estimation reprend la precision de la mesure`() {
        val fusion = GpsOnlyFusion()
        assertTrue(fusion.onFix(fix(0.0, accuracy = 6f)))
        assertEquals(6.0, fusion.estimate!!.sigmaM, 0.0001)
    }

    @Test
    fun `une mesure reseau grossiere ne degrade pas une position GPS recente`() {
        val fusion = GpsOnlyFusion()
        fusion.onFix(fix(0.0, accuracy = 4f, timeMs = 1_000))
        // 40 m de précision > 2 x 4 m, 1 s plus tard : ignorée.
        assertFalse(fusion.onFix(fix(30.0, accuracy = 40f, timeMs = 2_000)))
        assertEquals(4.0, fusion.estimate!!.sigmaM, 0.0001)
    }

    @Test
    fun `une mesure grossiere est acceptee quand l'estimation est perimee`() {
        val fusion = GpsOnlyFusion()
        fusion.onFix(fix(0.0, accuracy = 4f, timeMs = 1_000))
        assertTrue(fusion.onFix(fix(30.0, accuracy = 40f, timeMs = 20_000)))
        assertEquals(40.0, fusion.estimate!!.sigmaM, 0.0001)
    }

    @Test
    fun `une mesure plus ancienne que l'estimation est ignoree`() {
        val fusion = GpsOnlyFusion()
        fusion.onFix(fix(0.0, timeMs = 5_000))
        assertFalse(fusion.onFix(fix(1.0, timeMs = 4_000)))
    }

    @Test
    fun `le bruit immobile n'ajoute pas de deplacement`() {
        val fusion = GpsOnlyFusion()
        listOf(0.0, 1.0, -1.0, 0.5, -0.5, 1.2).forEachIndexed { i, n ->
            fusion.onFix(fix(n, accuracy = 5f, timeMs = i * 1_000L))
        }
        assertEquals(0.0, fusion.estimate!!.pathM, 0.0001)
    }

    @Test
    fun `une marche lente est comptee malgre des segments courts`() {
        val fusion = GpsOnlyFusion()
        // 1,3 m par seconde pendant 20 s : chaque segment est sous le seuil, mais le cumul est compté.
        for (i in 0..20) fusion.onFix(fix(i * 1.3, accuracy = 3f, timeMs = i * 1_000L))
        assertEquals(26.0, fusion.estimate!!.pathM, 3.0)
    }

    @Test
    fun `reset efface l'estimation`() {
        val fusion = GpsOnlyFusion()
        fusion.onFix(fix(0.0))
        assertNotNull(fusion.estimate)
        fusion.reset()
        assertNull(fusion.estimate)
    }
}
