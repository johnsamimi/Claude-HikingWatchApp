package com.arcowebdesign.hikingwatch.presentation.summary

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.arcowebdesign.hikingwatch.presentation.HikingViewModel
import com.arcowebdesign.hikingwatch.presentation.theme.*

@Composable
fun SummaryScreen(
    sessionId: Long,
    viewModel: HikingViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Bug 3e fix: collect as state — recomposes automatically when summary loads
    val summary by viewModel.sessionSummary.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val listState = rememberScalingLazyListState()

    if (summary == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Saving...", color = HikingMuted, fontSize = 11.sp)
            }
        }
        return
    }

    val s = summary!!

    ScalingLazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text("Session Complete", color = HikingGreen,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }

        // Bug 3f fix: distance row is first, above the fold, always visible
        item {
            SummaryRow(
                label = "Distance",
                // Bug 3d fix: formatDistance always returns a value, never empty
                value = viewModel.formatDistance(s.totalDistanceMeters, settings.distanceUnit)
            )
        }
        item { SummaryRow("Time", viewModel.formatElapsedTime(s.totalTimeSeconds)) }
        item { SummaryRow("Avg Speed", viewModel.formatSpeed(s.avgSpeedMps, settings.distanceUnit)) }
        item { SummaryRow("Max Alt", "${"%.0f".format(s.maxAltitudeMeters)} m") }
        item { SummaryRow("Elev. Gain", "+${"%.0f".format(s.elevationGainMeters)} m") }
        item { SummaryRow("Avg HR", if (s.avgHeartRate > 0) "${s.avgHeartRate} BPM" else "---") }
        item { SummaryRow("Calories", "${s.caloriesBurned} kcal") }

        item { Spacer(Modifier.height(6.dp)) }

        item {
            Button(
                onClick = { viewModel.exportGpx(sessionId) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = HikingBlue)
            ) {
                Text("Save GPX", fontSize = 12.sp)
            }
        }
        item {
            Chip(
                onClick = { viewModel.resetToIdle(); onDone() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                label = { Text("Done", fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = HikingMuted, fontSize = 11.sp)
        Text(value, color = HikingOnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
