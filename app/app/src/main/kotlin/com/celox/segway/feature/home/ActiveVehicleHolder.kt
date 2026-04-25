package com.celox.segway.feature.home

import com.celox.segway.core.ble.EllipticPairing
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.data.PairingPrefs
import com.celox.segway.core.data.UserPreferencesRepository
import com.celox.segway.core.data.VehicleDao
import com.celox.segway.core.vehicle.Vehicle
import com.celox.segway.core.vehicle.Zt3ProVehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder of the currently-active [Vehicle] instance. Wires up the
 * GATT client + ECDH-pairing layer for the vehicle the user selected last.
 */
@Singleton
class ActiveVehicleHolder @Inject constructor(
    private val gatt: GattClient,
    private val pairingPrefs: PairingPrefs,
    private val vehicleDao: VehicleDao,
    private val userPrefs: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _activeVehicle = MutableStateFlow<Vehicle?>(null)
    val activeVehicle: StateFlow<Vehicle?> = _activeVehicle.asStateFlow()

    fun bind(mac: String, displayName: String) {
        val pairing = EllipticPairing(gatt, pairingPrefs, mac)
        val vehicle = Zt3ProVehicle(
            id = mac,
            displayName = displayName,
            gatt = gatt,
            pairing = pairing,
            scope = scope
        )
        _activeVehicle.value = vehicle

        scope.launch {
            // Persist as last-used
            userPrefs.setLastVehicle(mac)
            // Try session resume if we have stored secrets
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
    }
}
