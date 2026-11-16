package com.alphascalp.quant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Emerald,
    secondary = Amber,
    tertiary = Cyan,
    background = Canvas,
    surface = SurfaceLow,
    onPrimary = Canvas,
    onSecondary = Canvas,
    onTertiary = Canvas,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Crimson
)

@Composable
fun AlphaScalpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
