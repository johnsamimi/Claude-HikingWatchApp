package com.arcowebdesign.hikingwatch.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme

val HikingGreen   = Color(0xFF4CAF50)
val HikingBlue    = Color(0xFF2196F3)
val HikingYellow  = Color(0xFFFFC107)
val HikingRed     = Color(0xFFF44336)
val HikingMuted   = Color(0xFF9E9E9E)
val HikingOnSurface = Color(0xFFEEEEEE)
val HikingSurface = Color(0xFF1E1E1E)

@Composable
fun HikingWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
