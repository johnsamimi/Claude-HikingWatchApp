package com.arcowebdesign.hikingwatch.presentation.map

import android.content.Context
import android.util.Log
import android.view.MotionEvent
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
import com.arcowebdesign.hikingwatch.domain.model.LatLng
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
import java.io.File

// Bug 2 fix: NonInterceptingMapView — passes all touch events through to OSMDroid
// so horizontal panning works and is not stolen by parent Compose containers
class NonInterceptingMapView(context: Context) : MapView(context) {
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Tell every parent in the hierarchy: do NOT intercept this touch
        parent?.requestDisallowInterceptTouchEvent(true)
        return super.onTouchEvent(ev)
    }
}

@Composable
fun MapScreen(
    viewModel: HikingViewModel,
    modifier: Modifier = Modifier,
    onNavigateToStats: () -> Unit   // Bug 2 fix: callback instead of swipe nav
) {
    val stats by viewModel.stats.collectAsState()
    val path by viewModel.path.collectAsState()
    val trackingState by viewModel.trackingState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {

        // Map fills entire screen
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            path = path,
            context = context
        )

        // HUD — elapsed time / altitude / speed at top
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(viewModel.formatElapsedTime(stats.elapsedTimeSeconds), color = HikingGreen, fontSize = 11.sp)
            Text("${"%.0f".format(stats.currentAltitudeMeters)}m", color = HikingOnSurface, fontSize = 11.sp)
            Text(viewModel.formatSpeed(stats.currentSpeedMps, settings.distanceUnit), color = HikingOnSurface, fontSize = 11.sp)
        }

        // Stats toggle button — top right (Bug 2 fix: replaces SwipeToDismissBox)
        Chip(
            onClick = onNavigateToStats,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .height(24.dp),
            label = { Text("Stats", fontSize = 9.sp) },
            colors = ChipDefaults.chipColors(backgroundColor = Color.Black.copy(alpha = 0.6f))
        )

        // Tracking control buttons at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (trackingState) {
                is TrackingState.Idle -> {
                    Button(
                        onClick = { viewModel.startHike() },
                        modifier = Modifier.size(56.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = HikingGreen)
                    ) { Text("GO", color = Color.Black, fontSize = 14.sp) }
                }
                is TrackingState.Active -> {
                    Button(
                        onClick = { viewModel.pauseHike() },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = HikingYellow)
                    ) { Text("II", color = Color.Black, fontSize = 14.sp) }
                    Button(
                        onClick = { viewModel.stopHike() },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = HikingRed)
                    ) { Text("■", color = Color.White, fontSize = 14.sp) }
                }
                is TrackingState.Paused -> {
                    Button(
                        onClick = { viewModel.resumeHike() },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = HikingGreen)
                    ) { Text("▶", color = Color.Black, fontSize = 14.sp) }
                    Button(
                        onClick = { viewModel.stopHike() },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = HikingRed)
                    ) { Text("■", color = Color.White, fontSize = 14.sp) }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun OsmMapView(modifier: Modifier, path: List<LatLng>, context: Context) {
    val mapView = remember {
        // Bug 4b fix: set userAgentValue BEFORE MapView is created
        // OSMDroid's tile server blocks requests without a valid user agent
        Configuration.getInstance().apply {
            userAgentValue = context.packageName  // "com.arcowebdesign.hikingwatch"
            osmdroidBasePath = context.filesDir
            // Bug 4c fix: writable internal cache directory
            osmdroidTileCache = File(context.filesDir, "osm_tile_cache")
        }
        // Ensure cache directory exists
        File(context.filesDir, "osm_tile_cache").mkdirs()

        // Bug 2 fix: NonInterceptingMapView instead of plain MapView
        NonInterceptingMapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTileSource(TileSourceFactory.MAPNIK)
            // Bug 4a fix: allow tile network downloads (was blocked, causing blank map)
            setUseDataConnection(true)
            // Bug 2 fix: enable multi-touch and fling for full pan/zoom support
            setMultiTouchControls(true)
            isFlingEnabled = true
            // Bug 1e fix: do NOT keep screen on — let ambient mode handle dimming
            keepScreenOn = false
            // Bug 4e fix: start at zoom 14 (fewer tiles to fetch than 17)
            controller.setZoom(14.0)
            minZoomLevel = 10.0
            maxZoomLevel = 19.0

            // Bug 4f fix: wrap location overlay in try/catch
            // A failed GPS provider must not block tile rendering
            try {
                val locOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                locOverlay.enableMyLocation()
                // Do NOT call enableFollowLocation() at init — wait for first GPS fix
                overlays.add(locOverlay)
            } catch (e: Exception) {
                Log.w("MapScreen", "Location overlay init failed: ${e.message}")
                // Map tiles will still render without this overlay
            }
        }
    }

    // Bug 4g fix: add polyline AFTER tile layer (at end of overlays list, not index 0)
    LaunchedEffect(path) {
        if (path.size >= 2) {
            val polyline = Polyline().apply {
                color = HikingGreen.toArgb()
                width = 6f
                setPoints(path.map { GeoPoint(it.latitude, it.longitude) })
            }
            mapView.overlays.removeAll { it is Polyline }
            mapView.overlays.add(polyline)  // add at end = on top of tiles
            mapView.invalidate()

            // Center map on latest point
            val last = path.last()
            mapView.controller.animateTo(GeoPoint(last.latitude, last.longitude))
        }
    }

    // Bug 2 fix: update block ensures parent never intercepts touches
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.parent?.requestDisallowInterceptTouchEvent(true)
        }
    )
}
