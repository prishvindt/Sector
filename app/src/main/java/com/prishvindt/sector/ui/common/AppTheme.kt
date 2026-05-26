package com.prishvindt.sector.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF236C4A),
    onPrimary = Color.White,
    secondary = Color(0xFF2A2F35),
    surface = Color(0xFFF7F8F8),
    surfaceVariant = Color(0xFFE0E5E3),
    onSurface = Color(0xFF15191D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF39D98A),
    onPrimary = Color(0xFF062314),
    secondary = Color(0xFFB7C1BC),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF252C31),
    onSurface = Color(0xFFE8ECEA)
)

@Composable
fun SectorTheme(
    colorScheme: ColorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
