package com.arcowebdesign.hikingwatch.data.db

import androidx.room.*

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long = System.currentTimeMillis(),
    val endTimeMs: Long = 0L,
    val totalDistanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val maxAltitudeMeters: Double = 0.0,
    val avgSpeedMps: Float = 0f,
    val avgHeartRate: Int = 0,
    val caloriesBurned: Int = 0,
    val status: String = "ACTIVE"
)

@Entity(
    tableName = "waypoints",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val accuracy: Float
)

@Dao
interface SessionDao {
    @Insert suspend fun insertSession(session: SessionEntity): Long
    @Update suspend fun updateSession(session: SessionEntity)
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun getSession(id: Long): SessionEntity?
    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC") suspend fun getAllSessions(): List<SessionEntity>
}

@Dao
interface WaypointDao {
    @Insert suspend fun insertWaypoint(waypoint: WaypointEntity): Long
    @Query("SELECT * FROM waypoints WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getWaypointsForSession(sessionId: Long): List<WaypointEntity>
    @Query("SELECT COUNT(*) FROM waypoints WHERE sessionId = :sessionId")
    suspend fun getWaypointCount(sessionId: Long): Int
}

@Database(entities = [SessionEntity::class, WaypointEntity::class], version = 1, exportSchema = false)
abstract class HikingDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun waypointDao(): WaypointDao
}
