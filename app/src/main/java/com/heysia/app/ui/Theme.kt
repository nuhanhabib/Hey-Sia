package com.heysia.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Sia's look: deep purple with a violet-to-pink accent, like her icon. */
private val SiaColors = darkColorScheme(
    primary = Color(0xFFB9A4FF),
    onPrimary = Color(0xFF1E1147),
    primaryContainer = Color(0xFF3B2A7A),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFFFF9AD5),
    onSecondary = Color(0xFF3D0A2B),
    background = Color(0xFF120E2B),
    onBackground = Color(0xFFEDE7FF),
    surface = Color(0xFF120E2B),
    onSurface = Color(0xFFEDE7FF),
    surfaceVariant = Color(0xFF241C4D),
    onSurfaceVariant = Color(0xFFCFC4F2),
    outline = Color(0xFF6F62A6),
    error = Color(0xFFFFB4AB)
)

@Composable
fun SiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SiaColors, content = content)
}
