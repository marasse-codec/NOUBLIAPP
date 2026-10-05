package com.noubli.app.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Calculs géographiques purs (aucune dépendance Android). */
object GeoMath {

    /** Rayon moyen de la Terre en mètres. */
    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Distance en mètres entre deux points GPS (formule de haversine).
     * Précision largement suffisante (< 0,5 %) pour des rayons de quelques dizaines de mètres.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        // min(1.0, …) protège contre les arrondis flottants qui feraient dépasser 1.
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
