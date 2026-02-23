package com.arcowebdesign.hikingwatch.domain.model

data class LatLng(val latitude: Double, val longitude: Double)

data class Waypoint(
    val id: Long = 0,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,        // meters
    val speed: Float,            // m/s
    val bearing: Float,          // degrees
    val accuracy: Float,         // meters
    val timestamp: Long = System.currentTimeMillis()
)

data class HikingSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val maxAltitudeMeters: Double = 0.0,
    val avgHeartRate: Int = 0,
    val caloriesBurned: Int = 0,
    val status: SessionStatus = SessionStatus.ACTIVE
)

enum class SessionStatus { ACTIVE, PAUSED, COMPLETED }

data class HikingStats(
    val heartRateBpm: Int = 0,
    val heartRateZone: HeartRateZone = HeartRateZone.NONE,
    val totalDistanceMeters: Double = 0.0,
    val elapsedTimeSeconds: Long = 0L,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val currentAltitudeMeters: Double = 0.0,
    val currentSpeedMps: Float = 0f,
    val averageSpeedMps: Float = 0f,
    val batteryPercent: Int = 0,
    val gpsAccuracyMeters: Float = 0f,
    val caloriesBurned: Int = 0,
    val currentBearing: Float = 0f
)

enum class HeartRateZone {
    NONE,
    RESTING,   // < 60% max
    AEROBIC,   // 60-70% max  (green)
    CARDIO,    // 70-85% max  (yellow)
    PEAK       // > 85% max   (red)
}

data class SessionSummary(
    val sessionId: Long,
    val totalDistanceMeters: Double,
    val totalTimeSeconds: Long,
    val avgSpeedMps: Float,
    val maxAltitudeMeters: Double,
    val elevationGainMeters: Double,
    val avgHeartRate: Int,
    val caloriesBurned: Int,
    val waypoints: List<Waypoint>
)

enum class DistanceUnit { METRIC, IMPERIAL }
enum class GpsSamplingRate { HIGH_ACCURACY, BATTERY_SAVER }
