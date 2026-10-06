package com.noubli.app.ui.zone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noubli.app.AppContainer
import com.noubli.app.domain.RadiusScale
import com.noubli.app.ui.common.AppPermissions
import com.noubli.app.ui.common.ErrorText
import com.noubli.app.ui.common.NoubliTopBar
import com.noubli.app.ui.common.SectionTitle
import com.noubli.app.ui.common.rememberPermissionRequest
import kotlin.math.roundToInt

/** Formulaire de création (zoneId = NEW_ZONE_ID) ou de modification d'une zone. */
@Composable
fun ZoneEditScreen(container: AppContainer, zoneId: Long, onClose: () -> Unit) {
    // key : un ViewModel distinct par zone, pour ne pas réutiliser un formulaire précédent.
    val viewModel: ZoneEditViewModel = viewModel(
        key = "zone_edit_$zoneId",
        factory = ZoneEditViewModel.factory(container, zoneId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form

    // Une fois la zone enregistrée, on referme l'écran.
    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    val requestLocation = rememberPermissionRequest(AppPermissions.location()) { outcome ->
        if (outcome.location) viewModel.useCurrentLocation()
        else viewModel.showError("Autorise la localisation précise pour utiliser ta position.")
    }

    Scaffold(
        topBar = {
            NoubliTopBar(
                title = if (state.isNew) "Nouvelle zone" else "Modifier la zone",
                onBack = onClose
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nom de la zone (Maison, Bureau…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Objets à ne pas oublier")
            ZoneItemsEditor(
                items = form.items,
                onAdd = viewModel::addItem,
                onRemove = viewModel::removeItem
            )

            SectionTitle("Position de la zone")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.latitude,
                    onValueChange = viewModel::onLatitudeChange,
                    label = { Text("Latitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.longitude,
                    onValueChange = viewModel::onLongitudeChange,
                    label = { Text("Longitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = requestLocation,
                enabled = !state.locating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Text(
                    text = if (state.locating) "  Localisation en cours…" else "  Utiliser ma position actuelle"
                )
            }

            SectionTitle("Distance d'alerte : ${form.radius.roundToInt()} m")
            // Échelle logarithmique : fin près de 1 m, large jusqu'à 500 m (voir RadiusScale).
            Slider(
                value = RadiusScale.toPosition(form.radius.roundToInt()),
                onValueChange = { viewModel.onRadiusChange(RadiusScale.toRadius(it).toFloat()) }
            )
            Text(
                text = "Dès 1 m, l'alerte reste prudente : elle part quand le GPS est sûr que tu es " +
                    "sorti, donc un peu après la distance choisie quand le signal est mauvais " +
                    "(l'écran « Suivi en direct » montre le seuil réel).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zone active", modifier = Modifier.weight(1f))
                Switch(checked = form.isActive, onCheckedChange = viewModel::onActiveChange)
            }

            ErrorText(state.error)

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.saving) "Enregistrement…" else "Enregistrer")
            }
        }
    }
}
