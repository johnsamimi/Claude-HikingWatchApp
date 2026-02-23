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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
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
        OsmMapView(modifier = Modifier.fillMaxSize(), path = path, context = context)

        // HUD top overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(viewModel.formatElapsedTime(stats.elapsedTimeSeconds), color = HikingGreen, fontSize = 12.sp)
            Text("${"%.0f".format(stats.currentAltitudeMeters)}m", color = HikingOnSurface, fontSize = 12.sp)
            Text(viewModel.formatSpeed(stats.currentSpeedMps, settings.distanceUnit), color = HikingOnSurface, fontSize = 12.sp)
        }

        // Control buttons bottom overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
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
                    ) { Text("[]", color = Color.White, fontSize = 14.sp) }
                }
                is TrackingState.Paused -> {
                    Button(
                        onClick = { viewModel.resumeHike() },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = HikingGreen)
                    ) { Text("GO", color = Color.Black, fontSize = 14.sp) }
                    Button(
                        onClick = { viewModel.stopHike() },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = HikingRed)
                    ) { Text("[]", color = Color.White, fontSize = 14.sp) }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun OsmMapView(modifier: Modifier, path: List<LatLng>, context: Context) {
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.filesDir
            osmdroidTileCache = File(context.filesDir, "osm_tiles")
        }
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            isFlingEnabled = true
            controller.setZoom(17.0)
            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
            locationOverlay.enableMyLocation()
            locationOverlay.enableFollowLocation()
            overlays.add(locationOverlay)
        }
    }

    LaunchedEffect(path) {
        if (path.size >= 2) {
            val polyline = Polyline().apply {
                color = HikingGreen.toArgb()
                width = 6f
                setPoints(path.map { GeoPoint(it.latitude, it.longitude) })
            }
            mapView.overlays.removeAll { it is Polyline }
            mapView.overlays.add(polyline)
            mapView.invalidate()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
