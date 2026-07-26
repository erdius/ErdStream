package com.erdman.erdstream.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

// Strictly grayscale palette -- no hue, no accent color, no album art. Built
// for e-ink displays (Mudita Kompakt, Light Phone 3) where color and imagery
// don't render usefully anyway.

private val LightColors = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF424242),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF333333),
    outline = Color(0xFF9E9E9E),
    error = Color(0xFF000000),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF757575),
    error = Color(0xFFFFFFFF),
    onError = Color(0xFF000000),
)

private val ErdStreamTypography = Typography(
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold),
)

@Composable
fun ErdStreamTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = ErdStreamTypography,
        content = content,
    )
}
