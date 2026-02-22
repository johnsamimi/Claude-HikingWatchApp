package com.arcowebdesign.hikingwatch.data.repository

import android.content.Context
import com.arcowebdesign.hikingwatch.data.db.*
import com.arcowebdesign.hikingwatch.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class HikingRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val waypointDao: WaypointDao,
    @ApplicationContext private val context: Context
) {
    // ── Session Management ──────────────────────────────────────────────────

    suspend fun startNewSession(): Long {
        val entity = SessionEntity(startTime = System.currentTimeMillis())
        return sessionDao.insertSession(entity)
    }

    suspend fun pauseSession(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(session.copy(status = "PAUSED"))
    }

    suspend fun resumeSession(sessionId: Long) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(session.copy(status = "ACTIVE"))
    }

    suspend fun endSession(sessionId: Long, stats: HikingStats) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(
            session.copy(
                endTime = System.currentTimeMillis(),
                status = "COMPLETED",
                totalDistanceMeters = stats.totalDistanceMeters,
                elevationGainMeters = stats.elevationGainMeters,
                elevationLossMeters = stats.elevationLossMeters,
                maxAltitudeMeters = stats.currentAltitudeMeters,
                avgHeartRate = stats.heartRateBpm,
                caloriesBurned = stats.caloriesBurned
            )
        )
    }

    suspend fun getActiveSession(): SessionEntity? = sessionDao.getActiveSession()

    // ── Waypoints ───────────────────────────────────────────────────────────

    suspend fun addWaypoint(waypoint: Waypoint) {
        waypointDao.insertWaypoint(
            WaypointEntity(
                sessionId = waypoint.sessionId,
                latitude = waypoint.latitude,
                longitude = waypoint.longitude,
                altitude = waypoint.altitude,
                speed = waypoint.speed,
                bearing = waypoint.bearing,
                accuracy = waypoint.accuracy,
                timestamp = waypoint.timestamp
            )
        )
    }

    suspend fun getWaypointsForSession(sessionId: Long): List<Waypoint> =
        waypointDao.getWaypointsForSession(sessionId).map {
            Waypoint(
                id = it.id,
                sessionId = it.sessionId,
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.altitude,
                speed = it.speed,
                bearing = it.bearing,
                accuracy = it.accuracy,
                timestamp = it.timestamp
            )
        }

    // ── Session Summary ─────────────────────────────────────────────────────

    suspend fun buildSessionSummary(sessionId: Long): SessionSummary? {
        val session = sessionDao.getSessionById(sessionId) ?: return null
        val waypoints = getWaypointsForSession(sessionId)
        val totalTime = ((session.endTime ?: System.currentTimeMillis()) - session.startTime) / 1000L
        val avgSpeed = if (totalTime > 0) (session.totalDistanceMeters / totalTime).toFloat() else 0f

        return SessionSummary(
            sessionId = sessionId,
            totalDistanceMeters = session.totalDistanceMeters,
            totalTimeSeconds = totalTime,
            avgSpeedMps = avgSpeed,
            maxAltitudeMeters = session.maxAltitudeMeters,
            elevationGainMeters = session.elevationGainMeters,
            avgHeartRate = session.avgHeartRate,
            caloriesBurned = session.caloriesBurned,
            waypoints = waypoints
        )
    }

    // ── GPX Export ──────────────────────────────────────────────────────────

    suspend fun exportToGpx(sessionId: Long): File? = withContext(Dispatchers.IO) {
        val waypoints = getWaypointsForSession(sessionId)
        if (waypoints.isEmpty()) return@withContext null

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val gpxContent = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="HikingWatchApp" xmlns="http://www.topografix.com/GPX/1/1">""")
            appendLine("""  <trk><name>Hiking Session $sessionId</name><trkseg>""")
            waypoints.forEach { wp ->
                appendLine("""    <trkpt lat="${wp.latitude}" lon="${wp.longitude}">""")
                appendLine("""      <ele>${wp.altitude}</ele>""")
                appendLine("""      <time>${sdf.format(Date(wp.timestamp))}</time>""")
                appendLine("""      <speed>${wp.speed}</speed>""")
                appendLine("""    </trkpt>""")
            }
            appendLine("""  </trkseg></trk>""")
            appendLine("""</gpx>""")
        }

        val dir = File(context.filesDir, "gpx_exports").also { it.mkdirs() }
        val file = File(dir, "hike_session_$sessionId.gpx")
        file.writeText(gpxContent)
        file
    }

    // ── Haversine Distance ──────────────────────────────────────────────────

    companion object {
        fun haversineDistanceMeters(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val R = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)
            return R * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
