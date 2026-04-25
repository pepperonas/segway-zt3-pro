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

@Database(
    entities = [VehicleEntity::class, TrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun trackDao(): TrackDao
}

@Singleton
class AppDatabaseProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "segway.db")
        .fallbackToDestructiveMigration()
        .build()
}
