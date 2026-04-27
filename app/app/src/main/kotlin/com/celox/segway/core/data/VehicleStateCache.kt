package com.celox.segway.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vehicleStateDataStore by preferencesDataStore("vehicle_state_cache")

/**
 * Last-known telemetry per scooter. Restored into [VehicleState] at app start
 * so the home screen shows real values immediately while the silent BLE
 * reconnect runs in the background. Mirrors SHU's pattern where a long-lived
 * service holds state that survives Activity recreation — without this users
 * see all-zero readouts on every cold start and rightly assume the app is broken.
 *
 * Only stable fields are cached. Live-only data (speedKmh, batteryCurrentA,
 * cellVoltagesMv) is NOT persisted — those are only meaningful in real time.
 */
@Singleton
class VehicleStateCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Serializable
    data class Snapshot(
        val mac: String,
        val batteryPercent: Int = 0,
        val batteryVoltage: Float = 0f,
        val batteryHealthPercent: Int = 0,
        val rangeRemainingKm: Float = 0f,
        val odometerKm: Float = 0f,
        val tripKm: Float = 0f,
        val temperatureC: Float = 0f,
        val mode: String? = null,
        val isLocked: Boolean = false,
        val isLightsOn: Boolean = false,
        val firmwareVcu: String = "",
        val firmwareMcu: String = "",
        val firmwareBle: String = "",
        val firmwareBms: String = "",
        val serialNumber: String = "",
        val regionCode: String = "",
        val chargeThresholdPercent: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun keyFor(mac: String) = stringPreferencesKey("snap_${mac.lowercase()}")

    suspend fun load(mac: String): Snapshot? {
        val raw = context.vehicleStateDataStore.data.first()[keyFor(mac)] ?: return null
        return runCatching { json.decodeFromString<Snapshot>(raw) }.getOrNull()
    }

    suspend fun save(snap: Snapshot) {
        context.vehicleStateDataStore.edit { it[keyFor(snap.mac)] = json.encodeToString(snap) }
    }
}
