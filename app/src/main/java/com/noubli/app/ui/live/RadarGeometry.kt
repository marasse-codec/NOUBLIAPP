package com.noubli.app.ui.live

import com.noubli.app.domain.LocalPoint
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * Calculs purs du radar (sans Compose) : échelle automatique et pas de la grille.
 * Isolés du dessin pour être testés en JVM.
 */
object RadarGeometry {

    /** @property extentM demi-côté du radar en mètres ; @property gridStepM taille d'un carreau. */
    data class Scale(val extentM: Double, val gridStepM: Double)

    private val STEPS = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1_000.0)

    /** Au moins 3 m de demi-côté, pour que le radar reste lisible quand tout est très petit. */
    private const val MIN_EXTENT_M = 3.0

    /**
     * Demi-côté nécessaire pour que le seuil de déclenchement, la position (avec son halo ±σ)
     * et le trajet tiennent tous dans l'image, avec une marge de 15 %.
     */
    fun requiredExtent(
        triggerM: Double,
        position: LocalPoint?,
        sigmaM: Double,
        trail: List<LocalPoint>
    ): Double {
        var extent = triggerM * 1.15
        if (position != null) extent = max(extent, (hypot(position.eastM, position.northM) + sigmaM) * 1.15)
        for (p in trail) extent = max(extent, hypot(p.eastM, p.northM) * 1.15)
        return max(extent, MIN_EXTENT_M)
    }

    /** Choisit un pas de grille « rond » (1, 2, 5, 10…) donnant au plus 4 carreaux par demi-côté. */
    fun scaleFor(requiredExtentM: Double): Scale {
        val step = STEPS.firstOrNull { requiredExtentM / it <= MAX_CELLS_PER_SIDE } ?: STEPS.last()
        return Scale(extentM = ceil(requiredExtentM / step) * step, gridStepM = step)
    }

    private const val MAX_CELLS_PER_SIDE = 4
}
