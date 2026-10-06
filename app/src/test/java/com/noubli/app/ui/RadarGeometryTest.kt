package com.noubli.app.ui

import com.noubli.app.domain.LocalPoint
import com.noubli.app.ui.live.RadarGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de l'échelle automatique du radar. */
class RadarGeometryTest {

    @Test
    fun `le seuil de declenchement tient toujours dans l'image`() {
        val extent = RadarGeometry.requiredExtent(triggerM = 13.0, position = null, sigmaM = 4.0, trail = emptyList())
        assertTrue(extent >= 13.0)
    }

    @Test
    fun `un utilisateur eloigne agrandit l'echelle`() {
        val near = RadarGeometry.requiredExtent(13.0, LocalPoint(2.0, 1.0), 4.0, emptyList())
        val far = RadarGeometry.requiredExtent(13.0, LocalPoint(40.0, 0.0), 4.0, emptyList())
        assertTrue(far > near)
        assertTrue(far >= 44.0) // position + halo
    }

    @Test
    fun `le pas de grille est rond et donne au plus 4 carreaux par cote`() {
        val scale = RadarGeometry.scaleFor(15.0)
        assertEquals(5.0, scale.gridStepM, 0.0001)
        assertEquals(15.0, scale.extentM, 0.0001)
        assertTrue(scale.extentM / scale.gridStepM <= 4.0)
    }

    @Test
    fun `l'echelle minimale reste lisible`() {
        val extent = RadarGeometry.requiredExtent(triggerM = 1.0, position = null, sigmaM = 0.5, trail = emptyList())
        assertEquals(3.0, extent, 0.0001)
        assertEquals(1.0, RadarGeometry.scaleFor(extent).gridStepM, 0.0001)
    }

    @Test
    fun `le trajet est pris en compte`() {
        val extent = RadarGeometry.requiredExtent(10.0, null, 3.0, listOf(LocalPoint(0.0, -30.0)))
        assertTrue(extent >= 30.0)
    }
}
