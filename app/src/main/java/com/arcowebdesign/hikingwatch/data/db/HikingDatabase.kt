package com.arcowebdesign.hikingwatch.data.db

import androidx.room.*

// ─── Entities ────────────────────────────────────────────────────────────────

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val maxAltitudeMeters: Double = 0.0,
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
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'ACTIVE' OR status = 'PAUSED' LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: WaypointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<WaypointEntity>)

    @Query("SELECT * FROM waypoints WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getWaypointsForSession(sessionId: Long): List<WaypointEntity>

    @Query("SELECT COUNT(*) FROM waypoints WHERE sessionId = :sessionId")
    suspend fun getWaypointCount(sessionId: Long): Int

    @Query("DELETE FROM waypoints WHERE sessionId = :sessionId")
    suspend fun deleteWaypointsForSession(sessionId: Long)
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [SessionEntity::class, WaypointEntity::class],
    version = 1,
    exportSchema = true
)
abstract class HikingDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun waypointDao(): WaypointDao
}
