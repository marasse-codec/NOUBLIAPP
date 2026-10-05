package com.noubli.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests du calcul de distance (haversine). */
class GeoMathTest {

    @Test
    fun `distance entre deux points identiques est nulle`() {
        val d = GeoMath.distanceMeters(48.8566, 2.3522, 48.8566, 2.3522)
        assertEquals(0.0, d, 0.001)
    }

    @Test
    fun `un degre de latitude vaut environ 111 km`() {
        val d = GeoMath.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, d, 200.0)
    }

    @Test
    fun `la distance est symetrique`() {
        val ab = GeoMath.distanceMeters(48.8566, 2.3522, 45.7640, 4.8357)
        val ba = GeoMath.distanceMeters(45.7640, 4.8357, 48.8566, 2.3522)
        assertEquals(ab, ba, 0.001)
    }

    @Test
    fun `Paris-Lyon est proche de 392 km`() {
        val d = GeoMath.distanceMeters(48.8566, 2.3522, 45.7640, 4.8357)
        assertEquals(392_000.0, d, 3_000.0)
    }
}
