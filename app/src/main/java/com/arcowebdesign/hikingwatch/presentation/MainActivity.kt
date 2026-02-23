package com.arcowebdesign.hikingwatch.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import com.arcowebdesign.hikingwatch.presentation.map.MapScreen
import com.arcowebdesign.hikingwatch.presentation.stats.StatsScreen
import com.arcowebdesign.hikingwatch.presentation.summary.SummaryScreen
import com.arcowebdesign.hikingwatch.presentation.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: HikingViewModel by viewModels()
    private var isAmbient by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HikingWatchTheme {
                HikingApp(
                    viewModel = viewModel,
                    isAmbient = isAmbient
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAmbient = false
    }
}

@Composable
fun HikingApp(viewModel: HikingViewModel, isAmbient: Boolean) {
    val trackingState by viewModel.trackingState.collectAsState()
    var permissionsGranted by remember { mutableStateOf(false) }

    when {
        // Permissions gate
        !permissionsGranted -> {
            PermissionsScreen(onPermissionsGranted = { permissionsGranted = true })
        }

        // Session complete — show summary
        trackingState is TrackingState.Completed -> {
            val sessionId = (trackingState as TrackingState.Completed).sessionId
            SummaryScreen(
                sessionId = sessionId,
                viewModel = viewModel,
                onDone = { viewModel.resetToIdle() }
            )
        }

        // Ambient mode — minimal low-power display
        isAmbient -> {
            AmbientScreen(viewModel = viewModel)
        }

        // Main pager: Map (page 0) ↔ Stats (page 1)
        else -> {
            val pagerState = rememberPagerState(pageCount = { 2 })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> MapScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> StatsScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun AmbientScreen(viewModel: HikingViewModel) {
    val stats by viewModel.stats.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = viewModel.formatElapsedTime(stats.elapsedTimeSeconds),
                color = Color.White,
                fontSize = 22.sp
            )
            Text(
                text = viewModel.formatDistance(stats.totalDistanceMeters, settings.distanceUnit),
                color = Color.Gray,
                fontSize = 14.sp
            )
            if (stats.heartRateBpm > 0) {
                Text(
                    text = "♥ ${stats.heartRateBpm}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
