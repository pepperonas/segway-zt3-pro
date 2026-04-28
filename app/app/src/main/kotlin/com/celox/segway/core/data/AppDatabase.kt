package com.celox.segway.core.data

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val mac: String,
    val displayName: String,
    val model: String,           // e.g. "ZT3 Pro"
    val serialNumber: String? = null,
    val regionCode: String? = null,
    val lastSeen: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleMac: String,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceMeters: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    /** GeoJSON LineString of the track. */
    val geoJsonLine: String,
)

/**
 * BLE-only ride session — auto-detected from `vehicle.state.speedKmh` going
 * non-zero. No GPS required. Stores aggregates only (no point list); for the
 * map-route we still use [TrackEntity] which is GPS-driven.
 */
@Entity(tableName = "ride_sessions")
data class RideSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleMac: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val distanceKm: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    /** Energy used during the session in watt-hours (signed: negative = regen net positive). */
    val energyWh: Float,
    /** Battery % at session start (0..100). */
    val batteryStartPercent: Int,
    /** Battery % at session end. */
    val batteryEndPercent: Int,
)

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY lastSeen DESC")
    fun all(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE mac = :mac LIMIT 1")
    suspend fun get(mac: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE mac = :mac")
    suspend fun delete(mac: String)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY startedAt DESC")
    fun all(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity): Long

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RideSessionDao {
    @Query("SELECT * FROM ride_sessions ORDER BY startedAt DESC")
    fun all(): Flow<List<RideSessionEntity>>

    @Query("SELECT * FROM ride_sessions WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RideSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: RideSessionEntity): Long

    @Query("DELETE FROM ride_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [VehicleEntity::class, TrackEntity::class, RideSessionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun trackDao(): TrackDao
    abstract fun rideSessionDao(): RideSessionDao
}

/**
 * v1 → v2: adds the `ride_sessions` table for BLE-only ride aggregates.
 * Pure additive — no existing tables touched, so paired vehicles survive.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ride_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `vehicleMac` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                `distanceKm` REAL NOT NULL,
                `avgSpeedKmh` REAL NOT NULL,
                `maxSpeedKmh` REAL NOT NULL,
                `energyWh` REAL NOT NULL,
                `batteryStartPercent` INTEGER NOT NULL,
                `batteryEndPercent` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Singleton
class AppDatabaseProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "segway.db")
        .addMigrations(MIGRATION_1_2)
        // Keep destructive fallback as a last-resort safety net for unknown future jumps.
        .fallbackToDestructiveMigration()
        .build()
}
