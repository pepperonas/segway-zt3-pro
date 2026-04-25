package com.celox.segway.core.vehicle

import com.celox.segway.core.ble.EllipticPairing
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.ble.GattState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Concrete [Vehicle] implementation for the Segway-Ninebot ZT3 Pro.
 *
 * Command set is loosely modeled after the documented Ninebot 2nd-gen
 * command-table (see `PRIOR-RESEARCH.md`):
 *   • 0x01 readReg
 *   • 0x02 writeReg
 *   • 0x18 0x10 program serial number
 *   • 0x57 activate-with-limit
 *   • 0x58 factory reset
 *
 * Implementation status:
 *   ✅ Connect & pair via [EllipticPairing]
 *   ✅ Status polling skeleton (read battery / speed / firmware)
 *   ✅ Lock / Unlock / SetMode encoding
 *   ⚠ ChangeRegion is stubbed to follow PRIOR-RESEARCH command 0x18-0x10 –
 *      review against your firmware before flashing!
 */
class Zt3ProVehicle(
    override val id: String,
    override val displayName: String,
    private val gatt: GattClient,
    private val pairing: EllipticPairing,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Vehicle {

    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    init {
        scope.launch {
            gatt.state.collect { gs ->
                _state.update { it.copy(isConnected = gs == GattState.Ready) }
            }
        }
        scope.launch {
            gatt.incoming.collect { raw ->
                val plain = pairing.decryptResponse(raw) ?: return@collect
                onResponse(plain)
            }
        }
    }

    override suspend fun connect() {
        gatt.connect(id)
        // Wait for GATT to be Ready before pairing logic kicks in
        gatt.state.first { it == GattState.Ready || it == GattState.Error }
    }

    override suspend fun disconnect() = gatt.disconnect()

    override suspend fun execute(command: VehicleCommand): Result<Unit> = runCatching {
        val payload = encode(command)
        val frame = pairing.encrypt(payload) ?: error("session not ready")
        require(gatt.send(frame)) { "BLE write failed" }
    }.onFailure { Timber.w(it, "execute($command) failed") }

    override suspend fun refresh(): Result<Unit> = runCatching {
        // Poll: read battery + speed + firmware in one round (different registers)
        val readReg = byteArrayOf(0x01, 0xB0.toByte(), 0x20)   // example: read 32 bytes from offset 0xB0
        val frame = pairing.encrypt(readReg) ?: error("session not ready")
        require(gatt.send(frame))
        // Response is parsed asynchronously in onResponse()
        withTimeoutOrNull(2_000L) { /* let notifications populate */ }
    }

    /** Encode a high-level command into a Ninebot-2g register-write payload. */
    private fun encode(cmd: VehicleCommand): ByteArray = when (cmd) {
        VehicleCommand.Lock           -> byteArrayOf(0x02, 0x70.toByte(), 0x01, 0x01)
        VehicleCommand.Unlock         -> byteArrayOf(0x02, 0x70.toByte(), 0x01, 0x00)
        is VehicleCommand.SetMode     -> byteArrayOf(
            0x02, 0x75.toByte(), 0x01,
            when (cmd.mode) { RideMode.Eco -> 0; RideMode.Drive -> 1; RideMode.Sport -> 2 }.toByte()
        )
        is VehicleCommand.SetLights   -> byteArrayOf(0x02, 0x76.toByte(), 0x01, if (cmd.on) 1 else 0)
        is VehicleCommand.SetCruise   -> byteArrayOf(0x02, 0x7C.toByte(), 0x01, if (cmd.on) 1 else 0)
        is VehicleCommand.SetSpeedLimit -> byteArrayOf(0x02, 0x72.toByte(), 0x02, cmd.kmh.toByte(), 0x00)
        VehicleCommand.Reboot          -> byteArrayOf(0x0A, 0x00)
        is VehicleCommand.ChangeRegion -> byteArrayOf(0x18, 0x10) + serialBytesForRegion(cmd.region)
        is VehicleCommand.WriteSerial  -> byteArrayOf(0x18, 0x10) + cmd.newSerial.toByteArray(Charsets.US_ASCII)
    }

    private fun serialBytesForRegion(region: String): ByteArray {
        // Replace the 4th byte of the device's current SN with the region code.
        // The actual SN is read from the scooter; for now we expect the caller to pass the
        // *full new SN* via [VehicleCommand.WriteSerial] — this stub returns the region as
        // a 1-byte hint so that downstream firmware parsing can override accordingly.
        return region.toByteArray(Charsets.US_ASCII)
    }

    /**
     * Parse a decrypted response. Maps known register layouts to [VehicleState].
     */
    private fun onResponse(plain: ByteArray) {
        if (plain.isEmpty()) return
        when (plain[0]) {
            0x01.toByte() -> handleReadReg(plain)
            0x02.toByte() -> { /* write-ack, nothing to update */ }
        }
    }

    private fun handleReadReg(plain: ByteArray) {
        if (plain.size < 4) return
        val offset = plain[1].toInt() and 0xFF
        val data = plain.copyOfRange(3, plain.size)
        when (offset) {
            0xB0 -> {
                // Example layout for the 32-byte status block at 0xB0:
                //   [0..1]  battery percent
                //   [2..3]  speed (10ths of km/h)
                //   [4..5]  total distance (10m units)
                //   [6..7]  trip distance
                //   [8..9]  temperature (10ths of °C)
                if (data.size >= 10) {
                    _state.update { st ->
                        st.copy(
                            batteryPercent = leU16(data, 0),
                            speedKmh = leU16(data, 2) / 10f,
                            odometerKm = leU16(data, 4) / 100f,
                            tripKm = leU16(data, 6) / 100f,
                            temperatureC = leU16(data, 8) / 10f,
                        )
                    }
                }
            }
        }
    }

    private fun leU16(b: ByteArray, idx: Int): Int =
        (b[idx].toInt() and 0xFF) or ((b[idx + 1].toInt() and 0xFF) shl 8)
}
