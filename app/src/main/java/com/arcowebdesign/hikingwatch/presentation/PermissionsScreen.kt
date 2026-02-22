package com.arcowebdesign.hikingwatch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.arcowebdesign.hikingwatch.presentation.theme.*
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(
    onPermissionsGranted: () -> Unit
) {
    val permissions = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BODY_SENSORS,
            android.Manifest.permission.ACTIVITY_RECOGNITION,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    )

    LaunchedEffect(permissions.allPermissionsGranted) {
        if (permissions.allPermissionsGranted) onPermissionsGranted()
    }

    if (permissions.allPermissionsGranted) {
        onPermissionsGranted()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Hiking Watch needs permissions to track your location and heart rate.",
            color = HikingOnSurface,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { permissions.launchMultiplePermissionRequest() },
            colors = ButtonDefaults.buttonColors(backgroundColor = HikingGreen)
        ) {
            Text("Grant Permissions", fontSize = 12.sp)
        }
    }
}
