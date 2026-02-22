package com.arcowebdesign.hikingwatch.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.*
import com.arcowebdesign.hikingwatch.R
import com.arcowebdesign.hikingwatch.data.repository.HikingRepository
import com.arcowebdesign.hikingwatch.data.repository.SettingsRepository
import com.arcowebdesign.hikingwatch.domain.model.*
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class HikingTrackingService : Service(), SensorEventListener {

    @Inject lateinit var hikingRepository: HikingRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var healthServicesClient: HealthServicesClient
    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }

    private var currentSessionId: Long = -1L
    private var sessionStatus = SessionStatus.ACTIVE

    // Stats state
    private var totalDistanceMeters = 0.0
    private var elevationGainMeters = 0.0
    private var elevationLossMeters = 0.0
    private var maxAltitudeMeters = 0.0
    private var lastAltitude: Double? = null
    private var startTimeMillis = 0L
    private var pausedDurationMs = 0L
    private var pauseStartMs = 0L
    private var lastWaypoint: Waypoint? = null
    private var speedSamples = mutableListOf<Float>()
    private var heartRateSamples = mutableListOf<Int>()
    private var currentBearing = 0f

    // Kalman filter state for altitude smoothing
    private var kalmanAltitude = KalmanFilter(processNoise = 1.0, measurementNoise = 10.0)

    // Published state
    private val _stats = MutableStateFlow(HikingStats())
    val stats: StateFlow<HikingStats> = _stats.asStateFlow()

    private val _path = MutableStateFlow<List<LatLng>>(emptyList())
    val path: StateFlow<List<LatLng>> = _path.asStateFlow()

    companion object {
        const val CHANNEL_ID = "hiking_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        private const val TAG = "HikingTrackingService"

        // Singleton access for composables
        private val _serviceStats = MutableStateFlow(HikingStats())
        val serviceStats: StateFlow<HikingStats> = _serviceStats.asStateFlow()

        private val _servicePath = MutableStateFlow<List<LatLng>>(emptyList())
        val servicePath: StateFlow<List<LatLng>> = _servicePath.asStateFlow()

        private val _activeSessionId = MutableStateFlow(-1L)
        val activeSessionId: StateFlow<Long> = _activeSessionId.asStateFlow()
    }

    // ── Location Callback ──────────────────────────────────────────────────

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                if (sessionStatus != SessionStatus.ACTIVE) return
                serviceScope.launch {
                    processLocation(location)
                }
            }
        }
    }

    // ── Heart Rate Callback ────────────────────────────────────────────────

    private val heartRateCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}

        override fun onDataReceived(data: DataPointContainer) {
            val bpm = data.getData(DataType.HEART_RATE_BPM)
                .lastOrNull()?.value?.toInt() ?: return
            heartRateSamples.add(bpm)
            updateStats { copy(heartRateBpm = bpm, heartRateZone = computeHrZone(bpm)) }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerCompassSensor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopHeartRateMeasurement()
    }

    // ── Tracking Control ───────────────────────────────────────────────────

    private fun startTracking() {
        startForeground(NOTIFICATION_ID, buildNotification("Hiking session active"))
        serviceScope.launch {
            currentSessionId = hikingRepository.startNewSession()
            _activeSessionId.value = currentSessionId
            startTimeMillis = System.currentTimeMillis()
            sessionStatus = SessionStatus.ACTIVE
            requestLocationUpdates()
            startHeartRateMeasurement()
            startStatsUpdater()
        }
    }

    private fun pauseTracking() {
        sessionStatus = SessionStatus.PAUSED
        pauseStartMs = System.currentTimeMillis()
        serviceScope.launch { hikingRepository.pauseSession(currentSessionId) }
        updateNotification("Session paused")
    }

    private fun resumeTracking() {
        sessionStatus = SessionStatus.ACTIVE
        pausedDurationMs += System.currentTimeMillis() - pauseStartMs
        serviceScope.launch { hikingRepository.resumeSession(currentSessionId) }
        updateNotification("Hiking session active")
    }

    private fun stopTracking() {
        sessionStatus = SessionStatus.COMPLETED
        serviceScope.launch {
            hikingRepository.endSession(currentSessionId, _stats.value)
            _activeSessionId.value = -1L
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopHeartRateMeasurement()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Location Processing ────────────────────────────────────────────────

    private suspend fun processLocation(location: android.location.Location) {
        val smoothedAlt = kalmanAltitude.update(location.altitude)

        // Track elevation
        lastAltitude?.let { prev ->
            val delta = smoothedAlt - prev
            if (delta > 0.5) elevationGainMeters += delta
            else if (delta < -0.5) elevationLossMeters += abs(delta)
        }
        if (smoothedAlt > maxAltitudeMeters) maxAltitudeMeters = smoothedAlt
        lastAltitude = smoothedAlt

        // Track distance
        val newWaypoint = Waypoint(
            sessionId = currentSessionId,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = smoothedAlt,
            speed = location.speed,
            bearing = location.bearing,
            accuracy = location.accuracy
        )

        lastWaypoint?.let { prev ->
            val dist = HikingRepository.haversineDistanceMeters(
                prev.latitude, prev.longitude,
                newWaypoint.latitude, newWaypoint.longitude
            )
            if (dist > 2.0) { // filter GPS jitter < 2m
                totalDistanceMeters += dist
                speedSamples.add(location.speed)
            }
        }
        lastWaypoint = newWaypoint

        // Persist waypoint
        hikingRepository.addWaypoint(newWaypoint)

        // Update path
        _servicePath.value = _servicePath.value + LatLng(location.latitude, location.longitude)

        val avgSpeed = if (speedSamples.isNotEmpty()) speedSamples.average().toFloat() else 0f
        val elapsed = (System.currentTimeMillis() - startTimeMillis - pausedDurationMs) / 1000L

        updateStats {
            copy(
                totalDistanceMeters = totalDistanceMeters,
                elapsedTimeSeconds = elapsed,
                elevationGainMeters = elevationGainMeters,
                elevationLossMeters = elevationLossMeters,
                currentAltitudeMeters = smoothedAlt,
                currentSpeedMps = location.speed,
                averageSpeedMps = avgSpeed,
                caloriesBurned = calculateCalories(elapsed, heartRateBpm),
                currentBearing = currentBearing
            )
        }
    }

    // ── Stats Periodic Updater (elapsed time) ─────────────────────────────

    private fun startStatsUpdater() {
        serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (sessionStatus == SessionStatus.ACTIVE) {
                    val elapsed = (System.currentTimeMillis() - startTimeMillis - pausedDurationMs) / 1000L
                    updateStats { copy(elapsedTimeSeconds = elapsed) }
                }
            }
        }
    }

    // ── Location Updates ───────────────────────────────────────────────────

    private fun requestLocationUpdates() {
        val interval = serviceScope.async {
            settingsRepository.settings.first().gpsSamplingRate
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L // default 2 seconds; dynamically updated by settings
        ).apply {
            setMinUpdateDistanceMeters(2f)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    // ── Heart Rate ─────────────────────────────────────────────────────────

    private fun startHeartRateMeasurement() {
        serviceScope.launch {
            try {
                healthServicesClient.measureClient.registerMeasureCallback(
                    DataType.HEART_RATE_BPM,
                    heartRateCallback
                )
            } catch (e: Exception) {
                Log.e(TAG, "Heart rate unavailable", e)
            }
        }
    }

    private fun stopHeartRateMeasurement() {
        serviceScope.launch {
            try {
                healthServicesClient.measureClient.unregisterMeasureCallback(
                    DataType.HEART_RATE_BPM,
                    heartRateCallback
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping heart rate", e)
            }
        }
    }

    // ── Compass Sensor ─────────────────────────────────────────────────────

    private fun registerCompassSensor() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            currentBearing = Math.toDegrees(orientation[0].toDouble()).toFloat()
            updateStats { copy(currentBearing = currentBearing) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun updateStats(update: HikingStats.() -> HikingStats) {
        _stats.value = _stats.value.update()
        _serviceStats.value = _stats.value
    }

    private fun computeHrZone(bpm: Int): HeartRateZone {
        return when {
            bpm <= 0 -> HeartRateZone.NONE
            bpm < 100 -> HeartRateZone.RESTING
            bpm < 130 -> HeartRateZone.AEROBIC
            bpm < 160 -> HeartRateZone.CARDIO
            else -> HeartRateZone.PEAK
        }
    }

    /** MET-based calorie estimate (approximate for hiking ~5 MET) */
    private fun calculateCalories(elapsedSeconds: Long, heartRateBpm: Int): Int {
        val hours = elapsedSeconds / 3600.0
        val met = when {
            heartRateBpm < 100 -> 4.0
            heartRateBpm < 130 -> 5.5
            heartRateBpm < 160 -> 7.0
            else -> 9.0
        }
        val weightKg = 70.0 // default; ideally from user settings
        return (met * weightKg * hours).toInt()
    }

    // ── Notification ───────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hiking Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active hiking session tracker"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hiking Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}

// ── Kalman Filter for Altitude Smoothing ───────────────────────────────────────

class KalmanFilter(
    private val processNoise: Double,   // Q
    private val measurementNoise: Double // R
) {
    private var estimate = 0.0
    private var errorCovariance = 1.0

    fun update(measurement: Double): Double {
        // Prediction
        errorCovariance += processNoise
        // Update
        val kalmanGain = errorCovariance / (errorCovariance + measurementNoise)
        estimate += kalmanGain * (measurement - estimate)
        errorCovariance *= (1 - kalmanGain)
        return estimate
    }
}
