package com.example.bajeti.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SereneColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = TealSurface,
    onPrimaryContainer = TealDark,
    secondary = TealLight,
    onSecondary = SurfaceWhite,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
)

@Composable
fun BajetiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SereneColorScheme,
        typography = Typography,
        content = content,
    )
}