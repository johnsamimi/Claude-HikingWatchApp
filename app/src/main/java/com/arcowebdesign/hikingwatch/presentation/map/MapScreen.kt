package com.arcowebdesign.hikingwatch.presentation.map

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.*
import com.arcowebdesign.hikingwatch.domain.model.*
import com.arcowebdesign.hikingwatch.presentation.HikingViewModel
import com.arcowebdesign.hikingwatch.presentation.TrackingState
import com.arcowebdesign.hikingwatch.presentation.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(
    viewModel: HikingViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.stats.collectAsState()
    val path by viewModel.path.collectAsState()
    val trackingState by viewModel.trackingState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        // ── Offline Map View ────────────────────────────────────────────────
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            path = path,
            context = context
        )

        // ── HUD Overlay (top) ───────────────────────────────────────────────
        HudOverlay(
            stats = stats,
            viewModel = viewModel,
            settings = settings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )

        // ── Control Buttons (bottom) ────────────────────────────────────────
        TrackingControls(
            trackingState = trackingState,
            onStart = { viewModel.startHike() },
            onPause = { viewModel.pauseHike() },
            onResume = { viewModel.resumeHike() },
            onStop = { viewModel.stopHike() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun OsmMapView(
    modifier: Modifier,
    path: List<LatLng>,
    context: Context
) {
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            // Offline tile cache location
            osmdroidBasePath = context.filesDir
            osmdroidTileCache = java.io.File(context.filesDir, "osm_tiles")
        }
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false) // watch has no multi-touch
            isFlingEnabled = true
            controller.setZoom(17.0)

            // My location overlay
            val locationOverlay = MyLocationNewOverlay(
                GpsMyLocationProvider(context), this
            ).apply { enableMyLocation(); enableFollowLocation() }
            overlays.add(locationOverlay)
        }
    }

    // Update path polyline whenever path changes
    LaunchedEffect(path) {
        if (path.size >= 2) {
            val polyline = Polyline().apply {
                color = HikingGreen.toArgb()
                width = 6f
                setPoints(path.map { GeoPoint(it.latitude, it.longitude) })
            }
            // Remove previous polylines, keep location overlay
            mapView.overlays.removeAll { it is Polyline }
            mapView.overlays.add(polyline)
            mapView.invalidate()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { /* handled via LaunchedEffect */ }
    )
}

@Composable
private fun HudOverlay(
    stats: HikingStats,
    viewModel: HikingViewModel,
    settings: com.arcowebdesign.hikingwatch.data.repository.UserSettings,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = viewModel.formatElapsedTime(stats.elapsedTimeSeconds),
            color = HikingGreen,
            fontSize = 12.sp
        )
        Text(
            text = "${"%.0f".format(stats.currentAltitudeMeters)}m",
            color = HikingOnSurface,
            fontSize = 12.sp
        )
        Text(
            text = viewModel.formatSpeed(stats.currentSpeedMps, settings.distanceUnit),
            color = HikingOnSurface,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TrackingControls(
    trackingState: TrackingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (trackingState) {
            is TrackingState.Idle -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = HikingGreen)
                ) {
                    Text("▶", color = Color.Black, fontSize = 20.sp)
                }
            }
            is TrackingState.Active -> {
                Button(
                    onClick = onPause,
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = HikingYellow)
                ) {
                    Text("⏸", color = Color.Black, fontSize = 16.sp)
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = HikingRed)
                ) {
                    Text("⏹", color = Color.White, fontSize = 16.sp)
                }
            }
            is TrackingState.Paused -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = HikingGreen)
                ) {
                    Text("▶", color = Color.Black, fontSize = 16.sp)
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = HikingRed)
                ) {
                    Text("⏹", color = Color.White, fontSize = 16.sp)
                }
            }
            else -> {}
        }
    }
}
