package com.noubli.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de l'échelle logarithmique du curseur de rayon et des conversions locales. */
class RadiusScaleTest {

    @Test
    fun `les extremites du curseur donnent 1 m et 500 m`() {
        assertEquals(1, RadiusScale.toRadius(0f))
        assertEquals(500, RadiusScale.toRadius(1f))
    }

    @Test
    fun `la conversion aller-retour est exacte pour tous les rayons`() {
        for (r in Validators.MIN_RADIUS_M..Validators.MAX_RADIUS_M) {
            assertEquals("rayon $r", r, RadiusScale.toRadius(RadiusScale.toPosition(r)))
        }
    }

    @Test
    fun `l'echelle est fine pres de 1 m`() {
        // Les 10 premiers % du curseur couvrent seulement 1 à 2 m.
        assertTrue(RadiusScale.toRadius(0.1f) <= 2)
    }

    @Test
    fun `les valeurs hors bornes sont ramenees dans la plage`() {
        assertEquals(1, RadiusScale.toRadius(-3f))
        assertEquals(500, RadiusScale.toRadius(9f))
    }

    @Test
    fun `toLocalMeters et offset sont inverses`() {
        val p = GeoMath.offset(48.8566, 2.3522, eastM = 30.0, northM = -12.0)
        val local = GeoMath.toLocalMeters(48.8566, 2.3522, p.latitude, p.longitude)
        assertEquals(30.0, local.eastM, 0.001)
        assertEquals(-12.0, local.northM, 0.001)
    }

    @Test
    fun `offset est coherent avec la distance haversine`() {
        val p = GeoMath.offset(48.8566, 2.3522, eastM = 30.0, northM = 40.0)
        assertEquals(50.0, GeoMath.distanceMeters(48.8566, 2.3522, p.latitude, p.longitude), 0.1)
    }
}
