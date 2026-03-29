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
import androidx.wear.compose.material.*
import com.arcowebdesign.hikingwatch.domain.model.*
import com.arcowebdesign.hikingwatch.presentation.HikingViewModel
import com.arcowebdesign.hikingwatch.presentation.theme.*

@Composable
fun StatsScreen(
    viewModel: HikingViewModel,
    modifier: Modifier = Modifier,
    onNavigateToMap: () -> Unit   // Bug 2 fix: back-to-map button
) {
    val stats by viewModel.stats.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Back to Map button
            Chip(
                onClick = onNavigateToMap,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(28.dp),
                label = { Text("◀ Map", fontSize = 10.sp) },
                colors = ChipDefaults.chipColors(backgroundColor = HikingSurface)
            )
        }
        item {
            StatCard("Heart Rate",
                if (stats.heartRateBpm > 0) "${stats.heartRateBpm} BPM" else "---",
                hrColor(stats.heartRateZone), stats.heartRateZone.label())
        }
        item {
            StatCard("Distance",
                viewModel.formatDistance(stats.totalDistanceMeters, settings.distanceUnit))
        }
        item {
            StatCard("Time", viewModel.formatElapsedTime(stats.elapsedTimeSeconds))
        }
        item {
            StatCard("Elevation",
                "${"%.0f".format(stats.currentAltitudeMeters)} m",
                subValue = "+${"%.0f".format(stats.elevationGainMeters)}m / -${"%.0f".format(stats.elevationLossMeters)}m")
        }
        item {
            StatCard("Speed",
                viewModel.formatSpeed(stats.currentSpeedMps, settings.distanceUnit),
                subValue = "Avg: ${viewModel.formatSpeed(stats.averageSpeedMps, settings.distanceUnit)}")
        }
        item {
            StatCard("Calories", "${stats.caloriesBurned} kcal")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("Battery", "$batteryLevel%", battColor(batteryLevel))
                MiniStat("GPS", gpsLabel(stats.gpsAccuracyMeters), gpsColor(stats.gpsAccuracyMeters))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String,
                     valueColor: Color = HikingOnSurface, subValue: String? = null) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = HikingMuted, fontSize = 10.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, color = valueColor, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            subValue?.let { Text(it, color = HikingMuted, fontSize = 9.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Text(label, color = HikingMuted, fontSize = 9.sp)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun hrColor(z: HeartRateZone) = when(z) {
    HeartRateZone.NONE -> HikingMuted; HeartRateZone.RESTING -> HikingBlue
    HeartRateZone.AEROBIC -> HikingGreen; HeartRateZone.CARDIO -> HikingYellow
    HeartRateZone.PEAK -> HikingRed
}
private fun HeartRateZone.label() = when(this) {
    HeartRateZone.NONE -> "No Signal"; HeartRateZone.RESTING -> "Resting"
    HeartRateZone.AEROBIC -> "Aerobic"; HeartRateZone.CARDIO -> "Cardio"
    HeartRateZone.PEAK -> "Peak"
}
private fun battColor(l: Int) = when { l > 50 -> HikingGreen; l > 20 -> HikingYellow; else -> HikingRed }
private fun gpsLabel(m: Float) = when { m <= 0f -> "---"; m <= 5f -> "Excellent"; m <= 15f -> "Good"; m <= 30f -> "Fair"; else -> "Poor" }
private fun gpsColor(m: Float) = when { m <= 0f -> HikingMuted; m <= 15f -> HikingGreen; m <= 30f -> HikingYellow; else -> HikingRed }
