package com.noubli.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noubli.app.domain.LocalPoint
import com.noubli.app.domain.policy.ZoneState

/** Couleurs d'état partagées entre le radar et la pastille de statut. */
object LiveColors {
    val inside = Color(0xFF2E7D32)
    val ambiguous = Color(0xFFEF6C00)
    val outside = Color(0xFFC62828)

    fun of(state: ZoneState?): Color = when (state) {
        ZoneState.INSIDE -> inside
        ZoneState.AMBIGUOUS -> ambiguous
        ZoneState.OUTSIDE -> outside
        null -> Color.Gray
    }
}

/**
 * Radar : vue du dessus centrée sur le point de la zone, nord en haut.
 *  - cercle plein = rayon de la zone ;
 *  - cercle pointillé = seuil de déclenchement (rayon + k × σ) : là où l'alerte part vraiment ;
 *  - point + halo = position estimée et son incertitude ±σ ;
 *  - trait = trajet calculé.
 *
 * Tous les chiffres sont en mètres, relatifs au centre de la zone ; [scale] fixe l'échelle.
 */
@Composable
fun RadarCanvas(
    radiusM: Double,
    triggerM: Double,
    sigmaM: Double,
    position: LocalPoint?,
    trail: List<LocalPoint>,
    state: ZoneState?,
    scale: RadarGeometry.Scale,
    description: String,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
    val zoneColor = MaterialTheme.colorScheme.primary
    val stateColor = LiveColors.of(state)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = description }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val pxPerM = (size.minDimension / 2f) / scale.extentM.toFloat()
        // Conversion mètres (est, nord) -> pixels (l'axe vertical de l'écran est inversé).
        fun px(eastM: Double, northM: Double) =
            Offset(center.x + eastM.toFloat() * pxPerM, center.y - northM.toFloat() * pxPerM)

        drawGrid(scale, pxPerM, center, gridColor, axisColor)

        // Zone : disque léger + contour (au moins quelques pixels pour qu'un rayon de 1 m reste visible).
        val zoneRadiusPx = maxOf(radiusM.toFloat() * pxPerM, MIN_ZONE_PX)
        drawCircle(zoneColor.copy(alpha = 0.15f), zoneRadiusPx, center)
        drawCircle(zoneColor, zoneRadiusPx, center, style = Stroke(width = 2.dp.toPx()))

        // Seuil de déclenchement en pointillés.
        drawCircle(
            color = LiveColors.outside.copy(alpha = 0.85f),
            radius = triggerM.toFloat() * pxPerM,
            center = center,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
        )

        // Trajet calculé.
        if (trail.size > 1) {
            val path = Path()
            trail.forEachIndexed { i, p ->
                val o = px(p.eastM, p.northM)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, stateColor.copy(alpha = 0.7f), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }

        // Position estimée : halo d'incertitude puis point.
        if (position != null) {
            val o = px(position.eastM, position.northM)
            drawCircle(stateColor.copy(alpha = 0.18f), sigmaM.toFloat() * pxPerM, o)
            drawCircle(stateColor.copy(alpha = 0.6f), sigmaM.toFloat() * pxPerM, o, style = Stroke(width = 1.dp.toPx()))
            drawCircle(stateColor, 6.dp.toPx(), o)
        }
    }
}

/** Grille carrée dont le pas est celui de l'échelle ; les axes passant par le centre sont plus marqués. */
private fun DrawScope.drawGrid(
    scale: RadarGeometry.Scale,
    pxPerM: Float,
    center: Offset,
    gridColor: Color,
    axisColor: Color
) {
    val cells = (scale.extentM / scale.gridStepM).toInt()
    for (i in -cells..cells) {
        val offset = (i * scale.gridStepM).toFloat() * pxPerM
        val color = if (i == 0) axisColor else gridColor
        drawLine(color, Offset(center.x + offset, 0f), Offset(center.x + offset, size.height), 1.dp.toPx())
        drawLine(color, Offset(0f, center.y + offset), Offset(size.width, center.y + offset), 1.dp.toPx())
    }
}

/** Taille minimale (px) du disque de la zone. */
private const val MIN_ZONE_PX = 6f
