package com.arcowebdesign.hikingwatch.data.repository

import com.arcowebdesign.hikingwatch.data.db.*
import com.arcowebdesign.hikingwatch.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class HikingRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val waypointDao: WaypointDao
) {
    suspend fun startNewSession(): Long =
        sessionDao.insertSession(SessionEntity(startTimeMs = System.currentTimeMillis()))

    suspend fun addWaypoint(sessionId: Long, lat: Double, lon: Double,
                            alt: Double, speed: Float, bearing: Float, accuracy: Float) {
        waypointDao.insertWaypoint(WaypointEntity(
            sessionId = sessionId, latitude = lat, longitude = lon,
            altitude = alt, speed = speed, bearing = bearing, accuracy = accuracy
        ))
    }

    suspend fun pauseSession(sessionId: Long) {
        sessionDao.getSession(sessionId)?.let {
            sessionDao.updateSession(it.copy(status = "PAUSED"))
        }
    }

    suspend fun resumeSession(sessionId: Long) {
        sessionDao.getSession(sessionId)?.let {
            sessionDao.updateSession(it.copy(status = "ACTIVE"))
        }
    }

    suspend fun endSession(sessionId: Long, stats: HikingStats) {
        sessionDao.getSession(sessionId)?.let {
            sessionDao.updateSession(it.copy(
                endTimeMs = System.currentTimeMillis(),
                totalDistanceMeters = stats.totalDistanceMeters,
                elevationGainMeters = stats.elevationGainMeters,
                elevationLossMeters = stats.elevationLossMeters,
                maxAltitudeMeters = stats.currentAltitudeMeters,
                avgSpeedMps = stats.averageSpeedMps,
                caloriesBurned = stats.caloriesBurned,
                status = "COMPLETED"
            ))
        }
    }

    // Bug 3b fix: builds summary from DB after waypoints are fully flushed
    suspend fun buildSessionSummary(sessionId: Long): SessionSummary {
        val session = sessionDao.getSession(sessionId) ?: return SessionSummary()
        val waypoints = waypointDao.getWaypointsForSession(sessionId)

        var totalDist = 0.0
        var maxAlt = Double.MIN_VALUE
        var elevGain = 0.0
        var lastAlt: Double? = null
        var heartSum = 0L
        var heartCount = 0

        for (i in 1 until waypoints.size) {
            val prev = waypoints[i - 1]
            val curr = waypoints[i]
            val d = haversineDistanceMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            if (d > 2.0) totalDist += d
            if (curr.altitude > maxAlt) maxAlt = curr.altitude
            lastAlt?.let { prev2 ->
                val delta = curr.altitude - prev2
                if (delta > 0.5) elevGain += delta
            }
            lastAlt = curr.altitude
            if (curr.speed > 0) { heartSum += curr.speed.toLong(); heartCount++ }
        }

        val durationMs = if (session.endTimeMs > session.startTimeMs)
            session.endTimeMs - session.startTimeMs else 0L
        val durationSec = durationMs / 1000L
        val avgSpeed = if (durationSec > 0) (totalDist / durationSec).toFloat() else 0f

        // Use stored totalDistanceMeters if waypoint recalc is 0 (fallback)
        val finalDist = if (totalDist > 0.0) totalDist else session.totalDistanceMeters

        return SessionSummary(
            totalDistanceMeters = finalDist,
            totalTimeSeconds = durationSec,
            avgSpeedMps = avgSpeed,
            maxAltitudeMeters = if (maxAlt == Double.MIN_VALUE) 0.0 else maxAlt,
            elevationGainMeters = elevGain,
            avgHeartRate = session.avgHeartRate,
            caloriesBurned = session.caloriesBurned
        )
    }

    suspend fun getWaypointsAsLatLng(sessionId: Long): List<LatLng> =
        waypointDao.getWaypointsForSession(sessionId).map { LatLng(it.latitude, it.longitude) }

    suspend fun exportGpx(sessionId: Long): String {
        val waypoints = waypointDao.getWaypointsForSession(sessionId)
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="HikingWatch">""")
            appendLine("""  <trk><trkseg>""")
            waypoints.forEach { wp ->
                appendLine("""    <trkpt lat="${wp.latitude}" lon="${wp.longitude}">""")
                appendLine("""      <ele>${wp.altitude}</ele>""")
                appendLine("""    </trkpt>""")
            }
            appendLine("""  </trkseg></trk>""")
            appendLine("""</gpx>""")
        }
    }

    companion object {
        fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return R * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
