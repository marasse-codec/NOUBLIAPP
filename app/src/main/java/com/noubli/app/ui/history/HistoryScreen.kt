package com.noubli.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noubli.app.AppContainer
import com.noubli.app.domain.model.AlertEvent
import com.noubli.app.ui.common.EmptyState
import com.noubli.app.ui.common.NoubliTopBar
import java.text.DateFormat
import java.util.Date

/** Historique des alertes émises (zone, date, distance, objets, acquittée ou non). */
@Composable
fun HistoryScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val events by viewModel.events.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NoubliTopBar(
                title = "Historique",
                onBack = onBack,
                actions = {
                    if (events.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Vider l'historique")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            Column(modifier = Modifier.padding(padding)) {
                EmptyState("Aucune alerte pour l'instant.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events, key = { it.id }) { event -> AlertCard(event) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Vider l'historique ?") },
            text = { Text("Toutes les alertes passées seront supprimées.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text("Vider") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } }
        )
    }
}

/** Une ligne de l'historique. */
@Composable
private fun AlertCard(event: AlertEvent) {
    // Format de date localisé (selon la langue du téléphone).
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(event.triggeredAt))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = event.zoneName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = "$date · à ${event.distanceM} m du centre", style = MaterialTheme.typography.bodySmall)
            Text(text = "Objets : ${event.itemsSummary}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (event.isAcknowledged) "✓ Acquittée (« J'ai tout »)" else "En attente d'acquittement",
                style = MaterialTheme.typography.labelMedium,
                color = if (event.isAcknowledged) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary
            )
        }
    }
}
