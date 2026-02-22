package com.arcowebdesign.hikingwatch.presentation.stats

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import com.arcowebdesign.hikingwatch.domain.model.*
import com.arcowebdesign.hikingwatch.presentation.HikingViewModel
import com.arcowebdesign.hikingwatch.presentation.theme.*

@Composable
fun StatsScreen(
    viewModel: HikingViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.stats.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            StatCard(
                label = "Heart Rate",
                value = if (stats.heartRateBpm > 0) "${stats.heartRateBpm} BPM" else "---",
                valueColor = heartRateZoneColor(stats.heartRateZone),
                subValue = stats.heartRateZone.displayName()
            )
        }
        item {
            StatCard(
                label = "Distance",
                value = viewModel.formatDistance(stats.totalDistanceMeters, settings.distanceUnit)
            )
        }
        item {
            StatCard(
                label = "Elapsed Time",
                value = viewModel.formatElapsedTime(stats.elapsedTimeSeconds)
            )
        }
        item {
            StatCard(
                label = "Elevation",
                value = "${"%.0f".format(stats.currentAltitudeMeters)}m",
                subValue = "+${"%.0f".format(stats.elevationGainMeters)}m  -${"%.0f".format(stats.elevationLossMeters)}m"
            )
        }
        item {
            StatCard(
                label = "Speed",
                value = viewModel.formatSpeed(stats.currentSpeedMps, settings.distanceUnit),
                subValue = "Avg: ${viewModel.formatSpeed(stats.averageSpeedMps, settings.distanceUnit)}"
            )
        }
        item {
            StatCard(
                label = "Calories",
                value = "${stats.caloriesBurned} kcal"
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStatCard(label = "Battery", value = "$batteryLevel%", color = batteryColor(batteryLevel))
                MiniStatCard(label = "GPS", value = gpsAccuracyLabel(stats.gpsAccuracyMeters), color = gpsAccuracyColor(stats.gpsAccuracyMeters))
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    valueColor: Color = HikingOnSurface,
    subValue: String? = null
) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = HikingMuted, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            subValue?.let {
                Text(text = it, color = HikingMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Text(text = label, color = HikingMuted, fontSize = 9.sp)
        Text(text = value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun heartRateZoneColor(zone: HeartRateZone): Color = when (zone) {
    HeartRateZone.NONE    -> HikingMuted
    HeartRateZone.RESTING -> HikingBlue
    HeartRateZone.AEROBIC -> HikingGreen
    HeartRateZone.CARDIO  -> HikingYellow
    HeartRateZone.PEAK    -> HikingRed
}
private fun HeartRateZone.displayName(): String = when (this) {
    HeartRateZone.NONE    -> "No Signal"
    HeartRateZone.RESTING -> "Resting"
    HeartRateZone.AEROBIC -> "Aerobic"
    HeartRateZone.CARDIO  -> "Cardio"
    HeartRateZone.PEAK    -> "Peak"
}
private fun batteryColor(level: Int): Color = when {
    level > 50 -> HikingGreen
    level > 20 -> HikingYellow
    else       -> HikingRed
}
private fun gpsAccuracyLabel(m: Float): String = when {
    m <= 0f  -> "---"
    m <= 5f  -> "Excellent"
    m <= 15f -> "Good"
    m <= 30f -> "Fair"
    else     -> "Poor"
}
private fun gpsAccuracyColor(m: Float): Color = when {
    m <= 0f  -> HikingMuted
    m <= 15f -> HikingGreen
    m <= 30f -> HikingYellow
    else     -> HikingRed
}
