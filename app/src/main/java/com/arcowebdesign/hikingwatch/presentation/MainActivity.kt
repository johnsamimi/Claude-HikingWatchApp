package com.arcowebdesign.hikingwatch.presentation

import android.os.Bundle
import android.view.WindowManager
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
import androidx.wear.compose.material.*
import com.arcowebdesign.hikingwatch.presentation.map.MapScreen
import com.arcowebdesign.hikingwatch.presentation.stats.StatsScreen
import com.arcowebdesign.hikingwatch.presentation.summary.SummaryScreen
import com.arcowebdesign.hikingwatch.presentation.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: HikingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bug 1e fix: do NOT keep screen on — let watch ambient mode handle it
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            HikingWatchTheme {
                HikingApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun HikingApp(viewModel: HikingViewModel) {
    val trackingState by viewModel.trackingState.collectAsState()
    var permissionsGranted by remember { mutableStateOf(false) }

    // Bug 2 fix: simple boolean state nav — NO SwipeToDismissBox
    // SwipeToDismissBox was stealing horizontal touches before OSMDroid saw them
    var showStats by remember { mutableStateOf(false) }

    when {
        !permissionsGranted -> {
            PermissionsScreen(onPermissionsGranted = { permissionsGranted = true })
        }

        trackingState is TrackingState.Completed -> {
            val sessionId = (trackingState as TrackingState.Completed).sessionId
            SummaryScreen(
                sessionId = sessionId,
                viewModel = viewModel,
                onDone = { viewModel.resetToIdle(); showStats = false }
            )
        }

        showStats -> {
            StatsScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onNavigateToMap = { showStats = false }
            )
        }

        else -> {
            MapScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onNavigateToStats = { showStats = true }
            )
        }
    }
}
