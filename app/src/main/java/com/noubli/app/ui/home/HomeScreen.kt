package com.noubli.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noubli.app.AppContainer
import com.noubli.app.domain.model.Zone
import com.noubli.app.location.MonitoringController
import com.noubli.app.location.MonitoringStatus
import com.noubli.app.ui.common.AppPermissions
import com.noubli.app.ui.common.EmptyState
import com.noubli.app.ui.common.NoubliTopBar
import com.noubli.app.ui.common.rememberPermissionRequest
import kotlinx.coroutines.launch

/** Écran d'accueil : interrupteur de surveillance et liste des zones. */
@Composable
fun HomeScreen(
    container: AppContainer,
    onAddZone: () -> Unit,
    onEditZone: (Long) -> Unit,
    onOpenLive: (Long) -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val running by MonitoringStatus.running.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var zoneToDelete by remember { mutableStateOf<Zone?>(null) }

    fun showMessage(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    // Demande des permissions, puis démarrage du service si tout est en ordre.
    val requestPermissions = rememberPermissionRequest(AppPermissions.monitoring()) { outcome ->
        when {
            !outcome.location ->
                showMessage("La localisation précise est nécessaire pour surveiller tes zones.")
            !container.locationProvider.isLocationEnabled() ->
                showMessage("Active la localisation (GPS) dans les réglages du téléphone.")
            else -> {
                MonitoringController.start(context)
                if (!outcome.notifications) {
                    showMessage("Notifications refusées : les alertes ne s'afficheront pas.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            NoubliTopBar(
                title = "Noubli",
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Historique")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Se déconnecter")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddZone) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter une zone")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MonitoringCard(
                    running = running,
                    onToggle = { on ->
                        if (on) requestPermissions() else MonitoringController.stop(context)
                    }
                )
            }
            if (zones.isEmpty()) {
                item {
                    EmptyState("Aucune zone pour l'instant.\nAppuie sur + pour créer ta première zone (maison, bureau…).")
                }
            } else {
                items(zones, key = { it.id }) { zone ->
                    ZoneCard(
                        zone = zone,
                        onActiveChange = { viewModel.setZoneActive(zone, it) },
                        onLive = { onOpenLive(zone.id) },
                        onEdit = { onEditZone(zone.id) },
                        onDelete = { zoneToDelete = zone }
                    )
                }
            }
        }
    }

    // Confirmation avant suppression (l'action est irréversible).
    zoneToDelete?.let { zone ->
        AlertDialog(
            onDismissRequest = { zoneToDelete = null },
            title = { Text("Supprimer la zone ?") },
            text = { Text("« ${zone.name} » et son historique d'alertes seront supprimés.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteZone(zone)
                    zoneToDelete = null
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { zoneToDelete = null }) { Text("Annuler") }
            }
        )
    }
}
