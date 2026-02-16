package com.pomodoro.nostr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberpunkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberBlack,
    primaryContainer = CyberSurface,
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = CyberBlack,
    secondaryContainer = CyberDarkGray,
    onSecondaryContainer = NeonMagenta,
    tertiary = NeonPurple,
    onTertiary = CyberBlack,
    background = CyberBlack,
    onBackground = CyberWhite,
    surface = CyberDarkGray,
    onSurface = CyberWhite,
    surfaceVariant = CyberSurface,
    onSurfaceVariant = CyberGray,
    error = NeonMagenta,
    onError = CyberBlack,
    errorContainer = CyberDarkGray,
    onErrorContainer = NeonMagenta,
    outline = CyberGray,
    outlineVariant = CyberSurface,
)

@Composable
fun PomodoroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberpunkColorScheme,
        typography = Typography,
        content = content
    )
}
