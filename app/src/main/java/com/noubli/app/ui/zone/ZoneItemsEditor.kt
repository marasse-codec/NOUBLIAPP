package com.noubli.app.ui.zone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Éditeur de la liste d'objets : un champ de saisie + un bouton « ajouter »,
 * puis la liste des objets déjà saisis, chacun avec un bouton de suppression.
 */
@Composable
fun ZoneItemsEditor(
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }

    // Ajoute l'objet saisi puis vide le champ (ignoré si le champ est vide).
    fun submit() {
        if (input.isNotBlank()) {
            onAdd(input)
            input = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Ex. clés, portefeuille, badge…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            IconButton(onClick = { submit() }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter l'objet")
            }
        }

        items.forEachIndexed { index, label ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "• $label",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Default.Close, contentDescription = "Retirer $label")
                }
            }
        }
    }
}
