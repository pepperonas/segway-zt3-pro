package com.celox.segway.core.profile

import com.celox.segway.core.data.RideSessionDao
import com.celox.segway.core.data.RideSessionEntity
import com.celox.segway.core.util.BleLog
import com.celox.segway.core.vehicle.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Auto-detects and records ride sessions from BLE telemetry only — no GPS needed.
 *
 * State machine:
 * - **Idle**: speed = 0. Watching for first non-zero sample.
 * - **PendingStart**: at least one sample > [START_SPEED_KMH] within the last
 *   [START_WINDOW_MS]. Promotes to Active when sustained.
 * - **Active**: integrating distance/energy/max/avg. Demotes to PendingStop on
 *   first zero.
 * - **PendingStop**: speed has been 0 for ≥ [STOP_IDLE_MS]. Persists + back to Idle.
 *
 * Distance is integrated from speedKmh × Δt (no odometer trust — odometer registers
 * jump in 1-km units which is too coarse for short trips). Energy is integrated
 * from `|batteryCurrentA| × batteryVoltage × Δt`, in watt-hours.
 */
@Singleton
class RideSessionRecorder @Inject constructor(
    private val dao: RideSessionDao,
    private val bleLog: BleLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectJob: Job? = null

    private val _live = MutableStateFlow<LiveRideSession?>(null)
    val live: StateFlow<LiveRideSession?> = _live.asStateFlow()

    /** Last completed session (for brief post-trip summary). */
    private val _last = MutableStateFlow<RideSessionEntity?>(null)
    val last: StateFlow<RideSessionEntity?> = _last.asStateFlow()

    private var session: ActiveSession? = null

    fun attach(vehicle: Vehicle) {
        detach()
        collectJob = scope.launch {
            var lastSampleAt = 0L
            vehicle.state.collect { s ->
                if (!s.isReady) return@collect
                val now = System.currentTimeMillis()
                val dtSec = if (lastSampleAt == 0L) 0f else (now - lastSampleAt) / 1000f
                lastSampleAt = now
                onSample(
                    vehicleMac = vehicle.id,
                    nowMs = now,
                    dtSec = dtSec.coerceIn(0f, 5f),
                    speedKmh = s.speedKmh,
                    voltageV = s.batteryVoltage,
                    currentA = s.batteryCurrentA,
                    batteryPct = s.batteryPercent,
                )
            }
        }
    }

    fun detach() {
        collectJob?.cancel()
        collectJob = null
        // Finalize an active session if the user disconnects mid-ride.
        session?.let { s -> scope.launch { finalize(s, System.currentTimeMillis()) } }
        session = null
        _live.value = null
    }

    private suspend fun onSample(
        vehicleMac: String,
        nowMs: Long,
        dtSec: Float,
        speedKmh: Float,
        voltageV: Float,
        currentA: Float,
        batteryPct: Int,
    ) {
        val moving = speedKmh > START_SPEED_KMH
        val s = session

        if (s == null) {
            if (moving) {
                session = ActiveSession(
                    vehicleMac = vehicleMac,
                    startedAt = nowMs,
                    batteryStartPct = batteryPct,
                    pendingStartSince = nowMs,
                )
                bleLog.note("RideSession", "candidate start at $nowMs (${speedKmh} km/h)")
            }
            return
        }

        // Active or pending — accumulate stats either way; promote/demote based on speed.
        if (dtSec > 0f) {
            s.distanceKm += speedKmh / 3600f * dtSec  // km/h × sec → km/h × (sec/3600 h)
            s.maxSpeedKmh = max(s.maxSpeedKmh, speedKmh)
            s.sampleCount += 1
            s.speedSum += speedKmh
            // Energy: V × |A| × Δt / 3600 → Wh. Use abs because current is signed
            // (negative = discharge). We want gross energy moved, not net.
            if (voltageV > 0f && abs(currentA) > 0.05f) {
                s.energyWh += voltageV * abs(currentA) * dtSec / 3600f
            }
        }
        s.lastBatteryPct = batteryPct

        if (moving) {
            s.lastMovingAt = nowMs
            s.confirmed = s.confirmed || (nowMs - s.pendingStartSince >= START_WINDOW_MS)
        } else if (s.confirmed && nowMs - s.lastMovingAt >= STOP_IDLE_MS) {
            // Stable stop on a confirmed session → persist.
            finalize(s, nowMs)
            session = null
            _live.value = null
            return
        } else if (!s.confirmed && nowMs - s.pendingStartSince > START_WINDOW_MS) {
            // Never reached sustained motion (rolled briefly). Drop without persist.
            session = null
            _live.value = null
            return
        }

        // Publish live snapshot only for confirmed sessions.
        if (s.confirmed) {
            _live.value = LiveRideSession(
                startedAt = s.startedAt,
                durationSec = (nowMs - s.startedAt) / 1000,
                distanceKm = s.distanceKm,
                maxSpeedKmh = s.maxSpeedKmh,
                avgSpeedKmh = if (s.sampleCount > 0) s.speedSum / s.sampleCount else 0f,
                energyWh = s.energyWh,
                batteryStartPercent = s.batteryStartPct,
                batteryCurrentPercent = batteryPct,
            )
        }
    }

    private suspend fun finalize(s: ActiveSession, endedAt: Long) {
        if (!s.confirmed) return
        if (s.distanceKm < MIN_PERSIST_KM) {
            bleLog.note("RideSession", "discard short session ${"%.3f".format(s.distanceKm)} km")
            return
        }
        val durationSec = max(1L, (endedAt - s.startedAt) / 1000)
        val avg = if (s.sampleCount > 0) s.speedSum / s.sampleCount else 0f
        val entity = RideSessionEntity(
            vehicleMac = s.vehicleMac,
            startedAt = s.startedAt,
            endedAt = endedAt,
            durationSeconds = durationSec,
            distanceKm = s.distanceKm,
            avgSpeedKmh = avg,
            maxSpeedKmh = s.maxSpeedKmh,
            energyWh = s.energyWh,
            batteryStartPercent = s.batteryStartPct,
            batteryEndPercent = s.lastBatteryPct,
        )
        val id = dao.upsert(entity)
        _last.value = entity.copy(id = id)
        bleLog.note(
            "RideSession",
            "persisted #$id: ${"%.2f".format(entity.distanceKm)} km, max ${"%.1f".format(entity.maxSpeedKmh)} km/h, ${entity.energyWh.toInt()} Wh"
        )
    }

    private class ActiveSession(
        val vehicleMac: String,
        val startedAt: Long,
        val batteryStartPct: Int,
        val pendingStartSince: Long,
    ) {
        var confirmed: Boolean = false
        var lastMovingAt: Long = startedAt
        var lastBatteryPct: Int = batteryStartPct
        var distanceKm: Float = 0f
        var maxSpeedKmh: Float = 0f
        var sampleCount: Int = 0
        var speedSum: Float = 0f
        var energyWh: Float = 0f
    }

    companion object {
        /** Anything ≤ this is treated as "stopped" (filters BLE noise + brief rolls). */
        private const val START_SPEED_KMH = 1.0f

        /** Time of sustained motion before we commit to "this is a real ride". */
        private const val START_WINDOW_MS = 2_000L

        /** Time at zero before we close the session. Long enough to allow traffic-light pauses. */
        private const val STOP_IDLE_MS = 30_000L

        /** Sessions shorter than this aren't persisted (keeps test rolls out of history). */
        private const val MIN_PERSIST_KM = 0.05f  // 50 m
    }
}

data class LiveRideSession(
    val startedAt: Long,
    val durationSec: Long,
    val distanceKm: Float,
    val maxSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val energyWh: Float,
    val batteryStartPercent: Int,
    val batteryCurrentPercent: Int,
)
