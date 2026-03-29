package com.arcowebdesign.hikingwatch.domain.model

data class LatLng(val latitude: Double, val longitude: Double)

enum class DistanceUnit { METRIC, IMPERIAL }

enum class HeartRateZone { NONE, RESTING, AEROBIC, CARDIO, PEAK }

enum class SessionStatus { ACTIVE, PAUSED, COMPLETED }

data class HikingStats(
    val totalDistanceMeters: Double = 0.0,
    val elapsedTimeSeconds: Long = 0L,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val currentAltitudeMeters: Double = 0.0,
    val currentSpeedMps: Float = 0f,
    val averageSpeedMps: Float = 0f,
    val heartRateBpm: Int = 0,
    val heartRateZone: HeartRateZone = HeartRateZone.NONE,
    val gpsAccuracyMeters: Float = 0f,
    val caloriesBurned: Int = 0,
    val currentBearing: Float = 0f
)

// Bug 3c fix: consistent field names used everywhere
data class SessionSummary(
    val totalDistanceMeters: Double = 0.0,
    val totalTimeSeconds: Long = 0L,
    val avgSpeedMps: Float = 0f,
    val maxAltitudeMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val avgHeartRate: Int = 0,
    val caloriesBurned: Int = 0
)

data class UserSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val gpsSamplingRate: Long = 5000L,
    val userAge: Int = 35,
    val userWeightKg: Float = 70f
)
