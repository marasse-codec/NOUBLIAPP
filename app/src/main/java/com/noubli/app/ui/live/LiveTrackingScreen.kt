package com.noubli.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noubli.app.AppContainer
import com.noubli.app.domain.GeoMath
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.policy.ZoneState
import com.noubli.app.domain.policy.ZoneVerdict
import com.noubli.app.engine.TrackingSnapshot
import com.noubli.app.location.MonitoringStatus
import com.noubli.app.ui.common.EmptyState
import com.noubli.app.ui.common.NoubliTopBar
import com.noubli.app.ui.common.SectionTitle
import java.util.Locale

/**
 * Écran « Suivi en direct » : montre à l'utilisateur, pour une zone, où il se trouve
 * par rapport au rayon, avec la précision réelle du calcul (voir la maquette de la conception v2).
 */
@Composable
fun LiveTrackingScreen(container: AppContainer, zoneId: Long, onBack: () -> Unit) {
    val viewModel: LiveViewModel = viewModel(factory = LiveViewModel.factory(container, zoneId))
    val zone by viewModel.zone.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val simulating by viewModel.simulating.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val recordedPoints by viewModel.recordedPoints.collectAsStateWithLifecycle()
    val traceCount by viewModel.traceCount.collectAsStateWithLifecycle()
    val monitoring by MonitoringStatus.running.collectAsStateWithLifecycle()

    Scaffold(topBar = { NoubliTopBar(title = "Suivi : ${zone?.name.orEmpty()}", onBack = onBack) }) { padding ->
        val current = zone
        if (current == null) {
            EmptyState("Zone introuvable.")
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val verdict = snapshot.verdicts.firstOrNull { it.zoneId == zoneId }
            StatusPill(verdict)
            if (verdict == null) {
                WaitingHint(simulating = simulating, monitoring = monitoring)
            } else {
                RadarSection(current, verdict, snapshot)
                FiguresCard(verdict, snapshot, current)
                snapshot.lastExit?.takeIf { it.zoneId == zoneId }?.let { exit ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Sortie détectée à ${exit.distanceM} m du centre : c'est ici que l'alerte part.",
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Controls(
                simulating = simulating,
                recording = recording,
                recordedPoints = recordedPoints,
                traceCount = traceCount,
                onToggleSimulation = { if (simulating) viewModel.stopSimulation() else viewModel.startSimulation() },
                onToggleRecording = viewModel::toggleRecording
            )
        }
    }
}

/** Pastille d'état : DANS LA ZONE / INCERTAIN / HORS ZONE, avec la marge et la précision. */
@Composable
private fun StatusPill(verdict: ZoneVerdict?) {
    val color = LiveColors.of(verdict?.state)
    val title = when (verdict?.state) {
        ZoneState.INSIDE -> "DANS LA ZONE"
        ZoneState.AMBIGUOUS -> "INCERTAIN"
        ZoneState.OUTSIDE -> "HORS ZONE"
        null -> "EN ATTENTE"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = color, fontWeight = FontWeight.Bold)
        if (verdict != null) {
            Text("marge ${signed(verdict.marginM)} m · précision ±${num(verdict.sigmaM)} m")
        }
    }
}

/** Message affiché tant qu'aucune mesure n'est disponible. */
@Composable
private fun WaitingHint(simulating: Boolean, monitoring: Boolean) {
    val message = when {
        simulating -> "Simulation en cours…"
        monitoring -> "En attente d'une mesure GPS fiable. Sors ou approche-toi d'une fenêtre."
        else -> "La surveillance est arrêtée : active-la sur l'écran d'accueil pour voir ta position réelle, " +
            "ou lance une simulation ci-dessous."
    }
    Text(message, style = MaterialTheme.typography.bodyMedium)
}

/** Radar et sa légende (échelle, repères). */
@Composable
private fun RadarSection(zone: Zone, verdict: ZoneVerdict, snapshot: TrackingSnapshot) {
    val position = snapshot.estimate?.let {
        GeoMath.toLocalMeters(zone.latitude, zone.longitude, it.latitude, it.longitude)
    }
    val trail = snapshot.trail.map { GeoMath.toLocalMeters(zone.latitude, zone.longitude, it.latitude, it.longitude) }
    val scale = RadarGeometry.scaleFor(
        RadarGeometry.requiredExtent(verdict.triggerDistanceM, position, verdict.sigmaM, trail)
    )
    RadarCanvas(
        radiusM = zone.radiusM.toDouble(),
        triggerM = verdict.triggerDistanceM,
        sigmaM = verdict.sigmaM,
        position = position,
        trail = trail,
        state = verdict.state,
        scale = scale,
        description = "Radar : à ${num(verdict.distanceM)} m du centre de la zone, rayon ${zone.radiusM} m"
    )
    Text(
        text = "Nord en haut · 1 carreau = ${num(scale.gridStepM)} m · trait plein : rayon de la zone · " +
            "pointillé rouge : seuil de déclenchement · halo : incertitude du GPS",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Les chiffres du calcul : de quoi comprendre pourquoi l'alerte part (ou pas encore). */
@Composable
private fun FiguresCard(verdict: ZoneVerdict, snapshot: TrackingSnapshot, zone: Zone) {
    val estimate = snapshot.estimate
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FigureRow("Distance au centre", "${num(verdict.distanceM)} m")
            FigureRow("Rayon de la zone", "${zone.radiusM} m")
            FigureRow("Seuil de déclenchement", "${num(verdict.triggerDistanceM)} m")
            FigureRow(
                "Déplacement calculé",
                "${num(estimate?.pathM ?: 0.0)} m" + (estimate?.steps?.takeIf { it > 0 }?.let { " ($it pas)" } ?: "")
            )
            FigureRow("Précision (±σ)", "${num(verdict.sigmaM)} m")
            FigureRow("Capteurs actifs", snapshot.activeSources.joinToString(" · ") { it.name }.ifEmpty { "aucun" })
        }
    }
}

@Composable
private fun FigureRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** Boutons : simulation et enregistrement du trajet (stocké en base pour le réglage terrain). */
@Composable
private fun Controls(
    simulating: Boolean,
    recording: Boolean,
    recordedPoints: Int,
    traceCount: Int,
    onToggleSimulation: () -> Unit,
    onToggleRecording: () -> Unit
) {
    SectionTitle("Tester")
    Button(onClick = onToggleSimulation, modifier = Modifier.fillMaxWidth()) {
        Text(if (simulating) "Arrêter la simulation" else "Lancer une simulation de sortie")
    }
    Text(
        text = "La simulation te place au centre 8 s, puis te fait marcher en ligne droite avec un GPS à ±4 m. " +
            "Aucune notification n'est envoyée.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(onClick = onToggleRecording, modifier = Modifier.fillMaxWidth()) {
        Text(if (recording) "Arrêter l'enregistrement ($recordedPoints mesures)" else "Enregistrer le trajet")
    }
    Text(
        text = "Trajets enregistrés pour cette zone : $traceCount",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Nombre à une décimale, virgule française (3,2). */
private fun num(value: Double): String = String.format(Locale.FRANCE, "%.1f", value)

/** Nombre signé à une décimale (+3,2 ou −1,8). */
private fun signed(value: Double): String = String.format(Locale.FRANCE, "%+.1f", value)
