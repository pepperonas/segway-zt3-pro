package com.celox.segway.feature.home

import com.celox.segway.core.ble.EllipticPairing
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.data.PairingPrefs
import com.celox.segway.core.data.UserPreferencesRepository
import com.celox.segway.core.data.VehicleDao
import com.celox.segway.core.ota.FirmwareUpdater
import com.celox.segway.core.repo.FirmwareTarget
import com.celox.segway.core.util.BleLog
import com.celox.segway.core.vehicle.Vehicle
import com.celox.segway.core.vehicle.Zt3ProVehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder of the currently-active [Vehicle] instance. Wires up the
 * GATT client + ECDH-pairing layer for the vehicle the user selected last,
 * and exposes higher-level operations that need both layers (e.g. firmware
 * flashing).
 */
@Singleton
class ActiveVehicleHolder @Inject constructor(
    private val gatt: GattClient,
    private val pairingPrefs: PairingPrefs,
    private val vehicleDao: VehicleDao,
    private val userPrefs: UserPreferencesRepository,
    private val bleLog: BleLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _activeVehicle = MutableStateFlow<Vehicle?>(null)
    val activeVehicle: StateFlow<Vehicle?> = _activeVehicle.asStateFlow()

    /** Reference to the EllipticPairing of the active vehicle. Needed for OTA flashing. */
    private var activePairing: EllipticPairing? = null

    fun bind(mac: String, displayName: String) {
        val pairing = EllipticPairing(gatt, pairingPrefs, mac, bleLog)
        activePairing = pairing
        val vehicle = Zt3ProVehicle(
            id = mac,
            displayName = displayName,
            gatt = gatt,
            pairing = pairing,
            scope = scope
        )
        _activeVehicle.value = vehicle

        scope.launch {
            userPrefs.setLastVehicle(mac)
            val stored = pairingPrefs.get(mac)
            val mode = if (stored?.deviceToken != null && stored.deviceInfo != null && stored.beaconKey != null) {
                EllipticPairing.PairingMode.SessionResume(
                    deviceInfo = pairingPrefs.decodeBase64(stored.deviceInfo),
                    deviceToken = pairingPrefs.decodeBase64(stored.deviceToken),
                    beaconKey = pairingPrefs.decodeBase64(stored.beaconKey),
                )
            } else {
                EllipticPairing.PairingMode.FreshHandshake
            }
            vehicle.connect()
            pairing.start(scope, mode)
        }
    }

    fun unbind() {
        val v = _activeVehicle.value
        scope.launch { v?.disconnect() }
        _activeVehicle.value = null
        activePairing = null
    }

    /**
     * Try to reconnect to the last-used vehicle. Called once at app start so
     * the user doesn't have to manually re-pair after a relaunch.
     */
    fun tryAutoReconnect() {
        scope.launch {
            val mac = userPrefs.flow.first().lastVehicleMac ?: run {
                bleLog.note("Reconnect", "no last vehicle stored")
                return@launch
            }
            val vehicle = vehicleDao.get(mac) ?: run {
                bleLog.note("Reconnect", "MAC $mac not in garage anymore")
                return@launch
            }
            bleLog.note("Reconnect", "auto-reconnect to ${vehicle.displayName} ($mac)")
            bind(mac, vehicle.displayName)
        }
    }

    /**
     * Run an OTA flash against the active vehicle. Returns a flow of progress
     * updates from the [FirmwareUpdater]. Does nothing if no vehicle is bound.
     */
    fun flashOnActive(target: FirmwareTarget, image: ByteArray): Flow<FirmwareUpdater.State> {
        val pairing = activePairing ?: return flowOf(FirmwareUpdater.State.Failed("No active vehicle"))
        val updater = FirmwareUpdater(gatt, pairing, scope)
        updater.flash(target, image)
        return updater.state
    }
}
