package com.arcowebdesign.hikingwatch.data.service

import android.app.*
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arcowebdesign.hikingwatch.data.repository.HikingRepository
import com.arcowebdesign.hikingwatch.domain.model.*
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class HikingTrackingService : Service(), SensorEventListener {

    @Inject lateinit var hikingRepository: HikingRepository
    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }

    private var currentSessionId = -1L
    private var sessionStatus = SessionStatus.ACTIVE
    private var totalDistanceMeters = 0.0
    private var elevationGainMeters = 0.0
    private var elevationLossMeters = 0.0
    private var maxAltitudeMeters = 0.0
    private var lastAltitude: Double? = null
    private var startTimeMillis = 0L
    private var pausedDurationMs = 0L
    private var pauseStartMs = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var speedSamples = mutableListOf<Float>()
    private var currentBearing = 0f
    private var currentHeartRate = 0
    private var lastHeartRateUpdate = 0L  // Bug 1b: debounce HR
    private val kalman = KalmanFilter()

    companion object {
        const val CHANNEL_ID = "hiking_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START = "START"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val ACTION_STOP = "STOP"
        private const val TAG = "HikingService"
        private const val HR_DEBOUNCE_MS = 30_000L  // Bug 1b: 30s HR debounce

        val serviceStats = MutableStateFlow(HikingStats())
        val servicePath = MutableStateFlow<List<LatLng>>(emptyList())
        val activeSessionId = MutableStateFlow(-1L)
        val sessionComplete = MutableStateFlow(false)
    }

    // Bug 1a: adaptive location callback
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            if (sessionStatus != SessionStatus.ACTIVE) return
            serviceScope.launch { processLocation(loc) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Bug 1b: SENSOR_DELAY_NORMAL (5 Hz) instead of SENSOR_DELAY_UI (60 Hz)
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> startTracking()
            ACTION_PAUSE  -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP   -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startTracking() {
        startForeground(NOTIF_ID, buildNotification("Hiking session active"))
        serviceScope.launch {
            currentSessionId = hikingRepository.startNewSession()
            activeSessionId.value = currentSessionId
            sessionComplete.value = false
            startTimeMillis = System.currentTimeMillis()
            sessionStatus = SessionStatus.ACTIVE
            requestLocationUpdates()
            startStatsTimer()
        }
    }

    private fun pauseTracking() {
        sessionStatus = SessionStatus.PAUSED
        pauseStartMs = System.currentTimeMillis()
        serviceScope.launch { hikingRepository.pauseSession(currentSessionId) }
        updateNotif("Session paused")
    }

    private fun resumeTracking() {
        sessionStatus = SessionStatus.ACTIVE
        pausedDurationMs += System.currentTimeMillis() - pauseStartMs
        serviceScope.launch { hikingRepository.resumeSession(currentSessionId) }
        updateNotif("Hiking session active")
    }

    private fun stopTracking() {
        sessionStatus = SessionStatus.COMPLETED
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.launch {
            // Bug 3b: flush remaining state, then end session
            delay(500L)
            val finalStats = serviceStats.value.copy(
                totalDistanceMeters = totalDistanceMeters,
                elevationGainMeters = elevationGainMeters,
                elevationLossMeters = elevationLossMeters
            )
            hikingRepository.endSession(currentSessionId, finalStats)
            // Signal ViewModel that session is complete
            sessionComplete.value = true
            activeSessionId.value = -1L
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun processLocation(loc: android.location.Location) {
        val smoothAlt = kalman.update(loc.altitude)
        lastAltitude?.let { prev ->
            val delta = smoothAlt - prev
            if (delta > 0.5) elevationGainMeters += delta
            else if (delta < -0.5) elevationLossMeters += abs(delta)
        }
        if (smoothAlt > maxAltitudeMeters) maxAltitudeMeters = smoothAlt
        lastAltitude = smoothAlt

        // Distance accumulation (Bug 1a: filter jitter < 5m)
        if (lastLat != 0.0 || lastLon != 0.0) {
            val d = HikingRepository.haversineDistanceMeters(lastLat, lastLon, loc.latitude, loc.longitude)
            if (d > 5.0) {
                totalDistanceMeters += d
                speedSamples.add(loc.speed)
            }
        }
        lastLat = loc.latitude
        lastLon = loc.longitude

        // Persist waypoint
        hikingRepository.addWaypoint(
            currentSessionId, loc.latitude, loc.longitude,
            smoothAlt, loc.speed, loc.bearing, loc.accuracy
        )

        // Update live path for map polyline
        servicePath.value = servicePath.value + LatLng(loc.latitude, loc.longitude)

        val avgSpeed = if (speedSamples.isNotEmpty()) speedSamples.average().toFloat() else 0f
        val elapsed = elapsedSeconds()
        emit(elapsed, avgSpeed, smoothAlt, loc.speed, loc.accuracy)

        // Bug 1a: adaptive GPS rate — slow down when stationary
        adjustLocationInterval(loc.speed)
    }

    private fun emit(elapsed: Long, avgSpeed: Float, alt: Double, speed: Float, accuracy: Float) {
        serviceStats.value = serviceStats.value.copy(
            totalDistanceMeters = totalDistanceMeters,
            elapsedTimeSeconds = elapsed,
            elevationGainMeters = elevationGainMeters,
            elevationLossMeters = elevationLossMeters,
            currentAltitudeMeters = alt,
            currentSpeedMps = speed,
            averageSpeedMps = avgSpeed,
            heartRateBpm = currentHeartRate,
            heartRateZone = hrZone(currentHeartRate),
            gpsAccuracyMeters = accuracy,
            caloriesBurned = calcCalories(elapsed, currentHeartRate),
            currentBearing = currentBearing
        )
    }

    // Bug 1c: stats timer every 5s instead of 1s
    private fun startStatsTimer() {
        serviceScope.launch {
            while (isActive) {
                delay(5000L)
                if (sessionStatus == SessionStatus.ACTIVE) {
                    serviceStats.value = serviceStats.value.copy(elapsedTimeSeconds = elapsedSeconds())
                }
            }
        }
    }

    // Bug 1a: adaptive GPS sampling
    private var currentLocationRequest: LocationRequest? = null
    private fun adjustLocationInterval(speed: Float) {
        val newInterval = if (speed > 0.5f) 5000L else 30000L
        if (currentLocationRequest?.intervalMillis == newInterval) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        requestLocationUpdates(newInterval)
    }

    private fun requestLocationUpdates(intervalMs: Long = 5000L) {
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(false)
            .build()
        currentLocationRequest = req
        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rot = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                val orient = FloatArray(3)
                SensorManager.getOrientation(rot, orient)
                currentBearing = Math.toDegrees(orient[0].toDouble()).toFloat()
            }
            Sensor.TYPE_HEART_RATE -> {
                val now = System.currentTimeMillis()
                // Bug 1b: debounce heart rate to max once per 30 seconds
                if (now - lastHeartRateUpdate >= HR_DEBOUNCE_MS && event.values.isNotEmpty()) {
                    currentHeartRate = event.values[0].toInt()
                    lastHeartRateUpdate = now
                    serviceStats.value = serviceStats.value.copy(
                        heartRateBpm = currentHeartRate,
                        heartRateZone = hrZone(currentHeartRate)
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun elapsedSeconds(): Long =
        (System.currentTimeMillis() - startTimeMillis - pausedDurationMs) / 1000L

    private fun hrZone(bpm: Int): HeartRateZone = when {
        bpm <= 0  -> HeartRateZone.NONE
        bpm < 100 -> HeartRateZone.RESTING
        bpm < 130 -> HeartRateZone.AEROBIC
        bpm < 160 -> HeartRateZone.CARDIO
        else      -> HeartRateZone.PEAK
    }

    private fun calcCalories(elapsed: Long, hr: Int): Int {
        val h = elapsed / 3600.0
        val met = when { hr < 100 -> 4.0; hr < 130 -> 5.5; hr < 160 -> 7.0; else -> 9.0 }
        return (met * 70.0 * h).toInt()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Hiking Tracking", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hiking Active").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi).setOngoing(true).build()
    }

    private fun updateNotif(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(text))
    }
}

class KalmanFilter(private val q: Double = 1.0, private val r: Double = 10.0) {
    private var x = 0.0; private var p = 1.0
    fun update(z: Double): Double {
        p += q; val k = p / (p + r); x += k * (z - x); p *= (1 - k); return x
    }
}
