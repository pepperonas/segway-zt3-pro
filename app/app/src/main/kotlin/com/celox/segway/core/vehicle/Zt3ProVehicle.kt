package com.celox.segway.core.vehicle

import com.celox.segway.core.ble.EllipticPairing
import com.celox.segway.core.ble.FrameCodecClassic
import com.celox.segway.core.ble.FrameCodecCrypto
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.ble.GattState
import com.celox.segway.core.crypto.NinebotCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * ZT3 Pro D implementation built on the **NinebotCrypto wire format** (case 2 in
 * SHU's `c6.b#a()`). The scooter advertises with manufacturer-id `0x434E` ("NC")
 * which marks it as a crypto-variant — plaintext frames are silently dropped.
 *
 * Frame structure:
 *
 *   `5A A5 [len] [src=3E] [dst=21] [cmd] [arg] [enc payload] [tag(4)] [ctrHi ctrLo]`
 *
 * The very first frame after connect must be the handshake `5A A5 10 3E 21 5C 00
 * [16-byte random]`. The scooter responds with a 16-byte token; our [crypto]
 * instance then re-keys with `SHA-1(scooterName ++ token)` and the rest of the
 * session uses real AES-CBC-MAC + AES-CTR encryption.
 */
class Zt3ProVehicle(
    override val id: String,
    override val displayName: String,
    private val scooterName: String,
    private val gatt: GattClient,
    private val pairing: EllipticPairing,             // kept for API compat — not used by the wire layer
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Vehicle {

    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val crypto = NinebotCrypto(scooterName)
    private val codec = FrameCodecCrypto(crypto)

    @Volatile private var handshakeDone = false

    init {
        scope.launch {
            gatt.state.collect { gs ->
                _state.update { it.copy(isConnected = gs == GattState.Ready) }
                if (gs != GattState.Ready) {
                    crypto.reset()
                    handshakeDone = false
                }
            }
        }
        scope.launch {
            gatt.incoming.collect { raw ->
                val parsed = codec.parse(raw) ?: return@collect
                handleNotify(parsed)
                handshakeDone = crypto.isHandshakeComplete()
            }
        }
    }

    override suspend fun connect() {
        gatt.connect(id)
        gatt.state.first { it == GattState.Ready || it == GattState.Error }
        if (gatt.state.value == GattState.Ready) runHandshake()
    }

    override suspend fun disconnect() = gatt.disconnect()

    override suspend fun execute(command: VehicleCommand): Result<Unit> = runCatching {
        ensureHandshake()
        val frame = encodeCrypto(command) ?: error("Cannot encode $command")
        require(gatt.send(frame)) { "BLE write failed" }
    }.onFailure { Timber.w(it, "execute($command) failed") }

    override suspend fun refresh(): Result<Unit> = runCatching {
        ensureHandshake()
        val frame = codec.readRegister(FrameCodecClassic.DST_VCU, 0xB0.toByte(), 32)
        require(gatt.send(frame))
        withTimeoutOrNull(2_000L) { /* responses arrive via incoming */ }
    }

    private suspend fun runHandshake() {
        if (handshakeDone) return
        val initFrame = crypto.buildInitFrame(FrameCodecClassic.DST_VCU)
        // First TX has counter=0 → encrypt() routes through the f-XOR path and
        // produces the handshake wire-frame. We pass it through the codec so the
        // BleLog entry shows both inner and wire bytes.
        val wire = crypto.encrypt(initFrame)
        require(gatt.send(wire)) { "Handshake init send failed" }
        // Wait up to 3s for a response that flips the handshake-complete flag.
        withTimeoutOrNull(3_000L) {
            while (!crypto.isHandshakeComplete()) delay(50)
        }
    }

    private suspend fun ensureHandshake() {
        if (!handshakeDone) runHandshake()
    }

    private fun encodeCrypto(cmd: VehicleCommand): ByteArray? = when (cmd) {
        VehicleCommand.Lock ->
            codec.writeRegister(FrameCodecClassic.DST_VCU, 0x70, byteArrayOf(0x01, 0x01))
        VehicleCommand.Unlock ->
            codec.writeRegister(FrameCodecClassic.DST_VCU, 0x70, byteArrayOf(0x01, 0x00))
        is VehicleCommand.SetMode ->
            codec.writeRegister(
                FrameCodecClassic.DST_VCU, 0x75,
                byteArrayOf(
                    0x01,
                    when (cmd.mode) { RideMode.Eco -> 0; RideMode.Drive -> 1; RideMode.Sport -> 2 }.toByte()
                )
            )
        is VehicleCommand.SetLights ->
            codec.writeRegister(
                FrameCodecClassic.DST_VCU, 0x76, byteArrayOf(0x01, if (cmd.on) 1 else 0)
            )
        is VehicleCommand.SetCruise ->
            codec.writeRegister(
                FrameCodecClassic.DST_VCU, 0x7C, byteArrayOf(0x01, if (cmd.on) 1 else 0)
            )
        is VehicleCommand.SetSpeedLimit ->
            codec.writeRegister(
                FrameCodecClassic.DST_VCU, 0x72, byteArrayOf(cmd.kmh.toByte(), 0x00)
            )
        VehicleCommand.Reboot ->
            codec.writeRegister(FrameCodecClassic.DST_VCU, 0x79, byteArrayOf(0x01, 0x01))
        is VehicleCommand.ChangeRegion ->
            codec.writeRegister(
                FrameCodecClassic.DST_VCU, 0x10, cmd.region.toByteArray(Charsets.US_ASCII)
            )
        is VehicleCommand.WriteSerial ->
            codec.writeRegister(
                FrameCodecClassic.DST_VCU, 0x10, cmd.newSerial.toByteArray(Charsets.US_ASCII)
            )
        is VehicleCommand.ReadRegister ->
            codec.readRegister(FrameCodecClassic.DST_VCU, cmd.offset.toByte(), cmd.length)
        VehicleCommand.ReadBlackBox ->
            codec.readRegister(FrameCodecClassic.DST_VCU, 0xF0.toByte(), 64)
        VehicleCommand.ReadFirmware ->
            codec.readRegister(FrameCodecClassic.DST_VCU, 0x1A, 16)
    }

    private fun handleNotify(parsed: FrameCodecClassic.Decoded) {
        if (parsed.cmd == FrameCodecClassic.CMD_READ_REGULAR) {
            _state.update { it.copy(lastRegisterRead = (parsed.arg.toInt() and 0xFF) to parsed.payload) }
        }
        val offset = parsed.arg.toInt() and 0xFF
        val data = parsed.payload
        when (offset) {
            0xB0 -> if (data.size >= 10) {
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
            0x1A -> if (data.size >= 6) {
                _state.update { st ->
                    st.copy(
                        firmwareVcu = "%d.%d.%d".format(data[1].toInt() and 0xFF, (data[0].toInt() ushr 4) and 0x0F, data[0].toInt() and 0x0F),
                        firmwareMcu = "%d.%d.%d".format(data[3].toInt() and 0xFF, (data[2].toInt() ushr 4) and 0x0F, data[2].toInt() and 0x0F),
                        firmwareBle = "%d.%d.%d".format(data[5].toInt() and 0xFF, (data[4].toInt() ushr 4) and 0x0F, data[4].toInt() and 0x0F),
                    )
                }
            }
            0xF0 -> _state.update { it.copy(blackBoxRaw = data) }
        }
    }

    private fun leU16(b: ByteArray, idx: Int): Int =
        (b[idx].toInt() and 0xFF) or ((b[idx + 1].toInt() and 0xFF) shl 8)
}
