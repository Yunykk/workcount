package com.workcount.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFCB5C1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD7BE),
    onPrimaryContainer = Color(0xFF3B1700),
    secondary = Color(0xFF136C68),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8F1EC),
    onSecondaryContainer = Color(0xFF00201E),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF231913),
    surfaceVariant = Color(0xFFF1E0D6),
    onSurfaceVariant = Color(0xFF53433A),
    background = Color(0xFFFFFBFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB689),
    onPrimary = Color(0xFF6E2B00),
    primaryContainer = Color(0xFF964200),
    onPrimaryContainer = Color(0xFFFFD7BE),
    secondary = Color(0xFF9BD5D0),
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF00504C),
    onSecondaryContainer = Color(0xFFB8F1EC),
    surface = Color(0xFF1B1411),
    onSurface = Color(0xFFF0DFD6),
    surfaceVariant = Color(0xFF53433A),
    onSurfaceVariant = Color(0xFFD6C2B7),
    background = Color(0xFF16120F),
)

@Composable
fun WorkCountTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
