package com.celox.segway.feature.track

import android.location.Location
import com.celox.segway.core.data.TrackDao
import com.celox.segway.core.data.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Persistence layer for ride tracks. The recording service hands us a list of
 * raw [Location] samples; we compute aggregate stats and store them as a single
 * Room row with the full polyline encoded as compact GeoJSON.
 */
@Singleton
class TrackRepository @Inject constructor(
    private val trackDao: TrackDao,
) {
    @Serializable
    data class GeoJsonLineString(
        val type: String = "LineString",
        // [[lon, lat], …]
        val coordinates: List<List<Double>>
    )

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    val all: Flow<List<TrackEntity>> = trackDao.all()

    /** Persist the recording. Returns the new row id. */
    suspend fun save(
        vehicleMac: String,
        startedAt: Long,
        endedAt: Long,
        points: List<Location>,
    ): Long {
        val coords = points.map { listOf(it.longitude, it.latitude) }
        val polyline = json.encodeToString(GeoJsonLineString(coordinates = coords))
        val (distance, avg, max) = computeStats(points)
        return trackDao.upsert(
            TrackEntity(
                vehicleMac = vehicleMac,
                startedAt = startedAt,
                endedAt = endedAt,
                distanceMeters = distance,
                avgSpeedKmh = avg,
                maxSpeedKmh = max,
                geoJsonLine = polyline,
            )
        )
    }

    suspend fun delete(id: Long) = trackDao.delete(id)

    private fun computeStats(points: List<Location>): Triple<Float, Float, Float> {
        if (points.size < 2) return Triple(0f, 0f, 0f)
        var distance = 0f
        var maxSpeed = 0f
        for (i in 1 until points.size) {
            distance += points[i - 1].distanceTo(points[i])
            val s = points[i].speed * 3.6f   // m/s → km/h
            if (s > maxSpeed) maxSpeed = s
        }
        val durationSec = max(1L, (points.last().time - points.first().time) / 1000)
        val avgKmh = (distance / durationSec) * 3.6f
        return Triple(distance, avgKmh, maxSpeed)
    }
}
