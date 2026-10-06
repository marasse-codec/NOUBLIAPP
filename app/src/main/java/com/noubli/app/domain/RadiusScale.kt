package com.noubli.app.domain

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Échelle logarithmique du curseur de rayon.
 *
 * Avec un curseur linéaire de 1 à 500 m, régler 1 m, 2 m ou 3 m serait impossible
 * au doigt. L'échelle logarithmique donne beaucoup de finesse près de 1 m et reste
 * utilisable jusqu'à 500 m. Position du curseur : 0.0 (= 1 m) à 1.0 (= 500 m).
 */
object RadiusScale {

    private val span = ln(Validators.MAX_RADIUS_M.toDouble() / Validators.MIN_RADIUS_M)

    /** Convertit la position du curseur (0..1) en rayon entier en mètres. */
    fun toRadius(position: Float): Int {
        val p = position.toDouble().coerceIn(0.0, 1.0)
        val radius = Validators.MIN_RADIUS_M * exp(p * span)
        return radius.roundToInt().coerceIn(Validators.MIN_RADIUS_M, Validators.MAX_RADIUS_M)
    }

    /** Position du curseur (0..1) correspondant à un rayon en mètres. */
    fun toPosition(radiusM: Int): Float {
        val r = radiusM.coerceIn(Validators.MIN_RADIUS_M, Validators.MAX_RADIUS_M)
        return (ln(r.toDouble() / Validators.MIN_RADIUS_M) / span).toFloat()
    }
}
