package com.noubli.app.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Résultat d'une demande de permissions. */
data class PermissionOutcome(val location: Boolean, val notifications: Boolean)

/** Listes de permissions nécessaires selon l'usage. */
object AppPermissions {

    /** Localisation seule (bouton « Utiliser ma position »). */
    fun location(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** Localisation + notifications (Android 13+) pour activer la surveillance. */
    fun monitoring(): Array<String> {
        val list = location().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }
}

/**
 * Prépare une demande de permissions et renvoie la fonction qui la lance.
 * Si les permissions sont déjà accordées, le système répond immédiatement sans dialogue.
 */
@Composable
fun rememberPermissionRequest(
    permissions: Array<String>,
    onResult: (PermissionOutcome) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val location = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        // Avant Android 13, la permission de notification n'existe pas : elle est considérée accordée.
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        onResult(PermissionOutcome(location, notifications))
    }
    return remember(launcher) { { launcher.launch(permissions) } }
}
