package com.example.helloworld.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YinHeColors = darkColorScheme(
    primary = Color(0xFFFF55D8),
    onPrimary = Color.White,
    secondary = Color(0xFF63E6FF),
    onSecondary = Color(0xFF071014),
    tertiary = Color(0xFF9E82FF),
    background = Color(0xFF0C0913),
    onBackground = Color(0xFFF8F4FF),
    surface = Color(0xFF15101F),
    onSurface = Color(0xFFF8F4FF),
    surfaceVariant = Color(0xFF21192E),
    onSurfaceVariant = Color(0xFFBBAEC9),
    outline = Color(0xFF3A2D4B)
)

@Composable
fun YinHeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YinHeColors,
        content = content
    )
}
