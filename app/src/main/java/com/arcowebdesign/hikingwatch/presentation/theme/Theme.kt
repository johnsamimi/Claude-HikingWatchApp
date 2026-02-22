package com.arcowebdesign.hikingwatch.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors

// Hiking-themed color palette
val HikingGreen = Color(0xFF4CAF50)
val HikingYellow = Color(0xFFFFC107)
val HikingRed = Color(0xFFF44336)
val HikingBlue = Color(0xFF2196F3)
val HikingDarkBackground = Color(0xFF121212)
val HikingCardBackground = Color(0xFF1E1E1E)
val HikingOnSurface = Color(0xFFE0E0E0)
val HikingMuted = Color(0xFF757575)

private val HikingColors = Colors(
    primary = HikingGreen,
    primaryVariant = Color(0xFF388E3C),
    secondary = HikingBlue,
    background = HikingDarkBackground,
    surface = HikingCardBackground,
    error = HikingRed,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = HikingOnSurface,
    onSurface = HikingOnSurface,
    onError = Color.White
)

@Composable
fun HikingWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = HikingColors,
        content = content
    )
}
