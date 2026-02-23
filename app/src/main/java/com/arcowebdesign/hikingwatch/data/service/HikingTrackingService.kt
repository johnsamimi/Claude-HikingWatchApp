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
import com.arcowebdesign.hikingwatch.data.repository.HikingRepository
import com.arcowebdesign.hikingwatch.data.repository.SettingsRepository
import com.arcowebdesign.hikingwatch.domain.model.*
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow

@AndroidEntryPoint
class HikingTrackingService : Service(), SensorEventListener {

    @Inject lateinit var hikingRepository: HikingRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }

    private var currentSessionId: Long = -1L
    private var sessionStatus = SessionStatus.ACTIVE
    private var totalDistanceMeters = 0.0
    private var elevationGainMeters = 0.0
    private var elevationLossMeters = 0.0
    private var maxAltitudeMeters = 0.0
    private var lastAltitude: Double? = null
    private var startTimeMillis = 0L
    private var pausedDurationMs = 0L
    private var pauseStartMs = 0L
    private var lastWaypoint: Waypoint? = null
    private val speedSamples = mutableListOf<Float>()
    private val heartRateSamples = mutableListOf<Int>()
    private var currentBearing = 0f
    private var currentHeartRate = 0
    private val kalmanAltitude = KalmanFilter(processNoise = 1.0, measurementNoise = 10.0)

    private val _stats = MutableStateFlow(HikingStats())

    companion object {
        const val CHANNEL_ID = "hiking_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        private const val TAG = "HikingTrackingService"

        val serviceStats = MutableStateFlow(HikingStats())
        val servicePath = MutableStateFlow<List<LatLng>>(emptyList())
        val activeSessionId = MutableStateFlow(-1L)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                if (sessionStatus != SessionStatus.ACTIVE) return
                serviceScope.launch { processLocation(location) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerCompassSensor()
        // Simulate heart rate for devices without sensor (replace with real HealthServices if available)
        startSimulatedHeartRate()
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
        startForeground(NOTIFICATION_ID, buildNotification("Hiking session active"))
        serviceScope.launch {
            currentSessionId = hikingRepository.startNewSession()
            activeSessionId.value = currentSessionId
            startTimeMillis = System.currentTimeMillis()
            sessionStatus = SessionStatus.ACTIVE
            requestLocationUpdates()
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
            activeSessionId.value = -1L
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun processLocation(location: android.location.Location) {
        val smoothedAlt = kalmanAltitude.update(location.altitude)
        lastAltitude?.let { prev ->
            val delta = smoothedAlt - prev
            if (delta > 0.5) elevationGainMeters += delta
            else if (delta < -0.5) elevationLossMeters += abs(delta)
        }
        if (smoothedAlt > maxAltitudeMeters) maxAltitudeMeters = smoothedAlt
        lastAltitude = smoothedAlt

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
            if (dist > 2.0) {
                totalDistanceMeters += dist
                speedSamples.add(location.speed)
            }
        }
        lastWaypoint = newWaypoint
        hikingRepository.addWaypoint(newWaypoint)
        servicePath.value = servicePath.value + LatLng(location.latitude, location.longitude)

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
                gpsAccuracyMeters = location.accuracy,
                caloriesBurned = calculateCalories(elapsed, currentHeartRate),
                currentBearing = currentBearing
            )
        }
    }

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

    /** Simulates heart rate using device sensor (TYPE_HEART_RATE) if available */
    private fun startSimulatedHeartRate() {
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (hrSensor != null) {
            sensorManager.registerListener(this, hrSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(2f)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    private fun registerCompassSensor() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                currentBearing = Math.toDegrees(orientation[0].toDouble()).toFloat()
                updateStats { copy(currentBearing = currentBearing) }
            }
            Sensor.TYPE_HEART_RATE -> {
                if (event.values.isNotEmpty()) {
                    currentHeartRate = event.values[0].toInt()
                    heartRateSamples.add(currentHeartRate)
                    updateStats {
                        copy(heartRateBpm = currentHeartRate, heartRateZone = computeHrZone(currentHeartRate))
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun updateStats(update: HikingStats.() -> HikingStats) {
        _stats.value = _stats.value.update()
        serviceStats.value = _stats.value
    }

    private fun computeHrZone(bpm: Int): HeartRateZone = when {
        bpm <= 0   -> HeartRateZone.NONE
        bpm < 100  -> HeartRateZone.RESTING
        bpm < 130  -> HeartRateZone.AEROBIC
        bpm < 160  -> HeartRateZone.CARDIO
        else       -> HeartRateZone.PEAK
    }

    private fun calculateCalories(elapsedSeconds: Long, heartRateBpm: Int): Int {
        val hours = elapsedSeconds / 3600.0
        val met = when {
            heartRateBpm < 100 -> 4.0
            heartRateBpm < 130 -> 5.5
            heartRateBpm < 160 -> 7.0
            else               -> 9.0
        }
        return (met * 70.0 * hours).toInt()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Hiking Tracking", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Active hiking session"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
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
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(text))
    }
}

class KalmanFilter(private val processNoise: Double, private val measurementNoise: Double) {
    private var estimate = 0.0
    private var errorCovariance = 1.0
    fun update(measurement: Double): Double {
        errorCovariance += processNoise
        val gain = errorCovariance / (errorCovariance + measurementNoise)
        estimate += gain * (measurement - estimate)
        errorCovariance *= (1 - gain)
        return estimate
    }
}
