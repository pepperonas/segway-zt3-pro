package com.celox.segway.feature.home

import android.content.Context
import com.celox.segway.core.ble.BleConnectionService
import com.celox.segway.core.ble.EllipticPairing
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.data.PairingPrefs
import com.celox.segway.core.data.UserPreferencesRepository
import com.celox.segway.core.data.VehicleDao
import com.celox.segway.core.data.VehicleStateCache
import com.celox.segway.core.ota.FirmwareUpdater
import com.celox.segway.core.profile.RideSessionRecorder
import com.celox.segway.core.repo.FirmwareTarget
import com.celox.segway.core.util.BleLog
import com.celox.segway.core.vehicle.Vehicle
import com.celox.segway.core.vehicle.Zt3ProVehicle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    @ApplicationContext private val appContext: Context,
    private val gatt: GattClient,
    private val pairingPrefs: PairingPrefs,
    private val vehicleDao: VehicleDao,
    private val userPrefs: UserPreferencesRepository,
    private val stateCache: VehicleStateCache,
    private val bleLog: BleLog,
    private val rideSessionRecorder: RideSessionRecorder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _activeVehicle = MutableStateFlow<Vehicle?>(null)
    val activeVehicle: StateFlow<Vehicle?> = _activeVehicle.asStateFlow()

    /** Reference to the EllipticPairing of the active vehicle. Needed for OTA flashing. */
    private var activePairing: EllipticPairing? = null

    /** Watchdog that re-runs connect() while the BLE link is down. Cancelled on unbind. */
    private var reconnectJob: Job? = null

    fun bind(mac: String, displayName: String, scooterName: String = displayName) {
        val pairing = EllipticPairing(gatt, pairingPrefs, mac, bleLog)
        activePairing = pairing
        val vehicle = Zt3ProVehicle(
            id = mac,
            displayName = displayName,
            scooterName = scooterName,
            gatt = gatt,
            pairing = pairing,
            pairingPrefs = pairingPrefs,
            stateCache = stateCache,
            bleLog = bleLog,
            scope = scope
        )
        _activeVehicle.value = vehicle
        rideSessionRecorder.attach(vehicle)

        // Foreground service keeps the OS from killing our process when the
        // screen turns off — without this, BLE drops within seconds and the
        // SpeedProfileManager's reg-0x5A poll loop (custom-button double-tap
        // detector) goes silent. `connectedDevice` foreground type matches
        // the manifest declaration.
        BleConnectionService.start(appContext)

        scope.launch {
            userPrefs.setLastVehicle(mac)
            // ZT3 Pro D uses the NinebotCrypto path (5A A5 with AES-CBC-MAC + CTR).
            // The first BLE frame after connect() is a handshake init that yields
            // the session token; subsequent frames are full crypto. Pairing object
            // is kept around for OTA only.
            vehicle.connect()
        }

        // Auto-reconnect watchdog: while a vehicle is bound, ensure the BLE
        // link stays up. If the user opens the app with the scooter asleep,
        // the first connect() either hangs ~30 s (Android internal timeout)
        // and resolves to Error, or succeeds when the user presses the power
        // button. Either way, this loop retries until isConnected=true.
        // Loop is cancelled in unbind() so an explicit disconnect doesn't
        // immediately reconnect.
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (true) {
                delay(8_000L)
                val v = _activeVehicle.value ?: break
                if (!v.state.value.isConnected) {
                    bleLog.note("Reconnect", "watchdog → retry connect to $mac")
                    runCatching { v.connect() }
                }
            }
        }
    }

    fun unbind() {
        reconnectJob?.cancel()
        reconnectJob = null
        rideSessionRecorder.detach()
        val v = _activeVehicle.value
        scope.launch { v?.disconnect() }
        _activeVehicle.value = null
        activePairing = null
        BleConnectionService.stop(appContext)
    }

    /** Manual retry — used by the connection banner on the home screen. */
    fun reconnectActive() {
        val v = _activeVehicle.value ?: return
        scope.launch {
            bleLog.note("Reconnect", "manual retry from UI")
            runCatching { v.connect() }
        }
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
