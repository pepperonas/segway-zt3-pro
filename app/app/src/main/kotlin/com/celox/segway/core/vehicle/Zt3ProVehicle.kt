package com.celox.segway.core.vehicle

import com.celox.segway.core.ble.EllipticPairing
import com.celox.segway.core.ble.FrameCodecClassic
import com.celox.segway.core.ble.FrameCodecCrypto
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.ble.GattState
import com.celox.segway.core.crypto.NinebotCrypto
import com.celox.segway.core.data.PairingPrefs
import com.celox.segway.core.util.BleLog
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
 * The first TX after connect is a 4-byte hello `[3E 21 5C 00]` (plen=0). The
 * scooter responds with `5A A5 1E [rxAddr] 3E 5B …token…` (plen=30) which our
 * [crypto] instance re-keys against. Subsequent commands use AES-CBC-MAC + AES-CTR.
 *
 * The handshake is fire-and-forget: pcap analysis of real SHU sessions
 * (`reverse-engineering/ble-captures/speed-manip.pcap`) shows SHU never blocks
 * on the response — it just keeps firing commands and the scooter accepts the
 * ones whose decryption succeeds.
 */
class Zt3ProVehicle(
    override val id: String,
    override val displayName: String,
    private val scooterName: String,
    private val gatt: GattClient,
    private val pairing: EllipticPairing,             // kept for API compat — not used by the wire layer
    private val pairingPrefs: PairingPrefs? = null,
    private val bleLog: BleLog? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Vehicle {

    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val crypto = NinebotCrypto(scooterName)
    private val codec = FrameCodecCrypto(crypto)

    @Volatile private var handshakeSent = false

    init {
        scope.launch {
            // Try restoring a previously-persisted token so the very first command
            // already encrypts under the resume-key — matches what SHU does in-memory
            // across reconnects.
            pairingPrefs?.loadCryptoToken(id)?.let { crypto.loadToken(it) }
        }
        scope.launch {
            gatt.state.collect { gs ->
                _state.update { it.copy(isConnected = gs == GattState.Ready) }
                if (gs != GattState.Ready) {
                    crypto.reset()
                    handshakeSent = false
                }
            }
        }
        scope.launch {
            var lastTokenSeen: ByteArray? = null
            gatt.incoming.collect { raw ->
                val parsed = codec.parse(raw) ?: return@collect
                handleNotify(parsed)
                // Persist the token whenever decrypt() flipped it to a new value.
                val current = crypto.snapshotToken()
                if (current.any { it != 0.toByte() } && !current.contentEquals(lastTokenSeen)) {
                    lastTokenSeen = current
                    pairingPrefs?.let { prefs -> scope.launch { prefs.saveCryptoToken(id, current) } }
                }
            }
        }
    }

    override suspend fun connect() {
        gatt.connect(id)
        gatt.state.first { it == GattState.Ready || it == GattState.Error }
        if (gatt.state.value == GattState.Ready) sendHandshake()
    }

    override suspend fun disconnect() = gatt.disconnect()

    override suspend fun execute(command: VehicleCommand): Result<Unit> = runCatching {
        if (!handshakeSent) sendHandshake()
        val frame = encodeCrypto(command) ?: error("Cannot encode $command")
        require(gatt.send(frame)) { "BLE write failed" }
    }.onFailure { Timber.w(it, "execute($command) failed") }

    override suspend fun refresh(): Result<Unit> = runCatching {
        if (!handshakeSent) sendHandshake()
        val frame = codec.readRegister(FrameCodecClassic.DST_VCU, 0xB0.toByte(), 32)
        require(gatt.send(frame))
        withTimeoutOrNull(2_000L) { /* responses arrive via incoming */ }
    }

    /**
     * Three-stage handshake matching SHU's `ScooterActivity.java:484-514`:
     *  1. Send `getBleRandom` (`[3E 04 5B 00]`, cmd=0x5B) until L flag (token+challenge captured)
     *  2. Send `o1(R)` (`[3E 04 5C 00 R…]`, cmd=0x5C, plen=0x10) until M flag (paired-key)
     *  3. Send `D0(challenge)` (`[3E 04 5D 00 chal…]`, cmd=0x5D, plen=0x0E) until O flag (fully paired)
     *
     * After stage 1 the wire-key transitions to `SHA-1(name + token)`; after stage 2 it
     * settles on `SHA-1(appRandom + token)` which becomes the session key. If a stage
     * doesn't complete we still set `handshakeSent = true` so user commands flow — the
     * scooter will then ignore them and the diagnostic log will show what stage we're
     * stuck at.
     */
    private suspend fun sendHandshake() {
        bleLog?.note(
            "Crypto",
            "scooterName='$scooterName' tokenLoaded=${crypto.isHandshakeComplete()}"
        )

        // Stage 1: getBleRandom → wait for L (token+challenge).
        val s1Frame = crypto.buildGetRandomFrame(FrameCodecClassic.DST_VCU)
        for (attempt in 0 until 6) {
            if (crypto.stageReceivedToken) break
            gatt.send(crypto.encrypt(s1Frame.copyOf()))
            withTimeoutOrNull(600L) {
                while (!crypto.stageReceivedToken) delay(20)
            }
            if (crypto.stageReceivedToken) break
            delay(300L)
        }
        if (!crypto.stageReceivedToken) {
            bleLog?.note("Crypto", "stage 1 (L) timed out — no token from scooter")
            handshakeSent = true
            return
        }
        bleLog?.note("Crypto", "L: token+challenge received")

        // Stage 2: o1(R) → wait for M (paired-key).
        // Build random ONCE so every retry sends the same payload (matches SHU's loop).
        val pairInit = crypto.buildPairInitFrame(FrameCodecClassic.DST_VCU)
        for (attempt in 0 until 6) {
            if (crypto.stagePairedKey) break
            gatt.send(crypto.encrypt(pairInit.copyOf()))
            withTimeoutOrNull(600L) {
                while (!crypto.stagePairedKey) delay(20)
            }
            if (crypto.stagePairedKey) break
            delay(300L)
        }
        if (!crypto.stagePairedKey) {
            bleLog?.note("Crypto", "stage 2 (M) timed out — pair-init not acked")
            handshakeSent = true
            return
        }
        bleLog?.note("Crypto", "M: paired-key (SHA-1(R+T))")

        // Stage 3: D0(challenge) → wait for O (fully paired).
        val challengeBytes = crypto.snapshotChallenge()
        for (attempt in 0 until 4) {
            if (crypto.stageFullyPaired) break
            gatt.send(codec.challengeResponse(FrameCodecClassic.DST_VCU, challengeBytes))
            withTimeoutOrNull(600L) {
                while (!crypto.stageFullyPaired) delay(20)
            }
            if (crypto.stageFullyPaired) break
            delay(300L)
        }
        if (crypto.stageFullyPaired) {
            bleLog?.note("Crypto", "O: fully paired — ready for commands")
        } else {
            bleLog?.note("Crypto", "stage 3 (O) timed out — challenge-echo not acked")
        }

        handshakeSent = true
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
