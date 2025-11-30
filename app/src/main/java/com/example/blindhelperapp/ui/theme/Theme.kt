package com.example.blindhelperapp.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = White,
    primaryContainer = LightBlue,
    onPrimaryContainer = White,
    surface = White,
    onSurface = Black,
    background = LightGray,
    onBackground = Black,
    surfaceVariant = MediumGray,
    onSurfaceVariant = DarkGray
)

@Composable
fun BlindHelperAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}