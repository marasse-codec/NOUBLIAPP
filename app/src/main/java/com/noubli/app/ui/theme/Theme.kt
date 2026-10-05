package com.noubli.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette volontairement simple : un bleu principal et un orange pour les accents.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFFEF6C00),
    onSecondary = Color.White,
    background = Color(0xFFF7F9FC),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D2A4D),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF3B2300),
    background = Color(0xFF10151B),
    surface = Color(0xFF1A2027)
)

/** Thème Material 3 de l'application : clair ou sombre selon le réglage du téléphone. */
@Composable
fun NoubliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
