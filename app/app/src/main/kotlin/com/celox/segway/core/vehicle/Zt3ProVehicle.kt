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

    /**
     * Persisted appRandom (`f5101e`) extracted from a successful SHU pair via
     * the patched-SHU `CRYPTO_DUMP` log at 2026-04-27. SHU keeps this in its
     * SharedPreferences keyed by MAC; we reuse it on every connect so we don't
     * need a fresh first-pair (which would require the user to press the power
     * button for OOB confirmation).
     *
     * The token (`f5100d`) is NOT persisted — the scooter issues a fresh one
     * in every cmd=0x5B handshake response. Only the random survives.
     */
    private val persistedRandomForDevScooter: ByteArray? =
        if (id.equals("C1:6B:5E:D0:C5:96", ignoreCase = true)) byteArrayOf(
            0xB1.toByte(), 0x59, 0xE5.toByte(), 0xED.toByte(), 0x55, 0x54, 0x7D, 0x3E,
            0x8C.toByte(), 0xA9.toByte(), 0x97.toByte(), 0xA1.toByte(), 0x61, 0xD9.toByte(), 0x5B, 0x42
        ) else null

    init {
        scope.launch {
            pairingPrefs?.loadCryptoToken(id)?.let { crypto.loadToken(it) }
        }
        scope.launch {
            gatt.state.collect { gs ->
                val connected = gs == GattState.Ready
                _state.update { it.copy(isConnected = connected, isReady = if (!connected) false else it.isReady) }
                if (!connected) {
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
                val current = crypto.snapshotToken()
                if (current.any { it != 0.toByte() } && !current.contentEquals(lastTokenSeen)) {
                    lastTokenSeen = current
                    pairingPrefs?.let { prefs -> scope.launch { prefs.saveCryptoToken(id, current) } }
                }
            }
        }
        // Periodic telemetry poll: every 2s while ready, fetch the SHU-style
        // status block + secondary registers. Responses populate VehicleState
        // via handleNotify().
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(2_000L)
                if (_state.value.isReady) {
                    runCatching { refresh() }
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
        bleLog?.note("Cmd", command.toString())
        val frame = encodeCrypto(command) ?: error("Cannot encode $command")
        require(gatt.send(frame)) { "BLE write failed" }
    }.onFailure { Timber.w(it, "execute($command) failed") }

    /**
     * Status-poll pattern lifted 1:1 from SHU's `CRYPTO_DUMP` capture (dst=0x16):
     *   reg 0x10 / 14 → S/N (string)
     *   reg 0xC0 / 12 → status block (likely battery/speed/odo, layout TBD)
     *   reg 0xE4 / 6  → modes/lights/cruise state
     *   reg 0x18, 0x19, 0x17 / 2 → small status flags
     */
    private val pollPlan: List<Triple<Byte, Byte, Int>> = listOf(
        Triple(0x16, 0xC0.toByte(), 12),  // primary status block
        Triple(0x16, 0xE4.toByte(), 6),   // mode/lights/cruise
        Triple(0x16, 0xDA.toByte(), 12),  // secondary status
        Triple(0x16, 0x18.toByte(), 2),
        Triple(0x16, 0x19.toByte(), 2),
        Triple(0x16, 0x17.toByte(), 2),
        Triple(0x16, 0xE7.toByte(), 2),
    )

    override suspend fun refresh(): Result<Unit> = runCatching {
        if (!handshakeSent) sendHandshake()
        for ((dst, reg, len) in pollPlan) {
            gatt.send(codec.readRegister(dst, reg, len))
            kotlinx.coroutines.delay(60L) // small gap so the scooter can answer between writes
        }
        withTimeoutOrNull(1_500L) { /* responses arrive via incoming */ }
    }

    /**
     * Resume-or-pair handshake matching `ScooterActivity.java:484-514`:
     *  1. Send `getBleRandom` (`[3E 04 5B 00]`, cmd=0x5B) until L flag (token+challenge captured)
     *  2. **If `persistedRandomForDevScooter != null`**: skip o1 and call `setRandomAppData(persisted)`
     *     directly — SHU's resume path. Else: send `o1(R)` (cmd=0x5C, plen=0x10) until M flag.
     *  3. Send `D0(challenge)` (cmd=0x5D, plen=0x0E) until O flag (fully paired)
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
        val s1Frame = crypto.buildGetRandomFrame(FrameCodecClassic.DST_HANDSHAKE)
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

        // Stage 2: either inject persisted random (resume) or do o1 first-pair.
        val persisted = persistedRandomForDevScooter
        if (persisted != null) {
            crypto.setRandomAppData(persisted)
            bleLog?.note("Crypto", "M: resumed via persisted random (key=SHA-1(R+T))")
        } else {
            val pairInit = crypto.buildPairInitFrame(FrameCodecClassic.DST_HANDSHAKE)
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
        }

        // Stage 3: D0(challenge) → wait for O (fully paired).
        val challengeBytes = crypto.snapshotChallenge()
        for (attempt in 0 until 4) {
            if (crypto.stageFullyPaired) break
            gatt.send(codec.challengeResponse(FrameCodecClassic.DST_HANDSHAKE, challengeBytes))
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
        // Flag the vehicle as ready so callers waiting for the handshake (e.g.
        // SpeedProfileManager's auto-apply) can fire commands immediately
        // without an arbitrary sleep. We set isReady even on partial-success
        // (M reached but O didn't) — once M flips, the session key is correct
        // and write-register commands are accepted.
        if (crypto.stagePairedKey) {
            _state.update { it.copy(isReady = true) }
        }
    }

    private fun encodeCrypto(cmd: VehicleCommand): ByteArray? = when (cmd) {
        VehicleCommand.Lock ->
            // dst=0x16 (VCU on ZT3 — same target as speed-limit writes).
            // Register/payload layout best-guess from M365/G30 conventions.
            codec.writeRegister(0x16, 0x70, byteArrayOf(0x01, 0x01))
        VehicleCommand.Unlock ->
            codec.writeRegister(0x16, 0x70, byteArrayOf(0x01, 0x00))
        is VehicleCommand.SetMode ->
            codec.writeRegister(
                0x16, 0x75,
                byteArrayOf(
                    0x01,
                    when (cmd.mode) { RideMode.Eco -> 0; RideMode.Drive -> 1; RideMode.Sport -> 2 }.toByte()
                )
            )
        is VehicleCommand.SetLights ->
            codec.writeRegister(0x16, 0x76, byteArrayOf(0x01, if (cmd.on) 1 else 0))
        is VehicleCommand.SetCruise ->
            codec.writeRegister(0x16, 0x7C, byteArrayOf(0x01, if (cmd.on) 1 else 0))
        is VehicleCommand.SetSpeedLimit ->
            // Verified against SHU's wire (CRYPTO_DUMP C=50, 2026-04-27):
            //   `5A A5 02 3E 16 02 48 14 16` for "set City to 22 km/h"
            //   = write reg 0x48 on dst=0x16, payload=[eco_limit=0x14, city_limit=kmh]
            codec.writeRegister(
                0x16.toByte(), 0x48, byteArrayOf(0x14, cmd.kmh.toByte())
            )
        VehicleCommand.Reboot ->
            codec.writeRegister(0x16, 0x79, byteArrayOf(0x01, 0x01))
        is VehicleCommand.ChangeRegion ->
            codec.writeRegister(0x16, 0x10, cmd.region.toByteArray(Charsets.US_ASCII))
        is VehicleCommand.WriteSerial ->
            codec.writeRegister(0x16, 0x10, cmd.newSerial.toByteArray(Charsets.US_ASCII))
        is VehicleCommand.ReadRegister ->
            codec.readRegister(0x16, cmd.offset.toByte(), cmd.length)
        VehicleCommand.ReadBlackBox ->
            codec.readRegister(0x16, 0xF0.toByte(), 64)
        VehicleCommand.ReadFirmware ->
            codec.readRegister(0x16, 0x1A, 16)
    }

    private fun handleNotify(parsed: FrameCodecClassic.Decoded) {
        // Always log decrypted RX so unknown notify-patterns can be identified.
        bleLog?.note(
            "RX-DEC",
            "src=%02X dst=%02X cmd=%02X arg=%02X [%s]".format(
                parsed.src, parsed.dst, parsed.cmd, parsed.arg,
                parsed.payload.joinToString(" ") { "%02X".format(it) }
            )
        )

        // Mirror the answer for any incoming notify (regardless of cmd byte) into
        // lastRegisterRead so the diagnostics screen can render it. Real ZT3
        // responses use cmd=0x05 not the legacy 0x01.
        _state.update {
            it.copy(lastRegisterRead = (parsed.arg.toInt() and 0xFF) to parsed.payload)
        }

        val offset = parsed.arg.toInt() and 0xFF
        val data = parsed.payload

        // ZT3-Pro-D-specific register layout (empirically derived from SHU's
        // polling pattern + observed responses). Slot widths inferred from the
        // poll lengths; field offsets within a slot are best-effort and may
        // drift across firmware revs.
        when (offset) {
            0xC0 -> if (data.size >= 12) {
                // Empirically validated layout (ZT3 Pro D, 2026-04-27 log):
                //   [2..3] le-u16 / 10 → matches dashboard speed
                //   [6..7] le-u16 / 100 → matches dashboard trip
                // [0..1], [4..5], [8..11] not yet decoded — see FIELD-TEST-LOG.
                _state.update { st ->
                    st.copy(
                        speedKmh = leU16(data, 2) / 10f,
                        tripKm = leU16(data, 6) / 100f,
                    )
                }
            }
            0xDA -> if (data.size >= 12) {
                // Secondary status — battery / voltage / temperature suspected here.
                // Layout TBD; for now mirror raw to lastRegisterRead only.
            }
            0xE4 -> if (data.size >= 6) {
                // Mode/lights/cruise state — exact layout TBD via SHU capture.
                // For now we just keep the raw data in lastRegisterRead.
            }
            0x10 -> if (data.size >= 14) {
                _state.update { it.copy(serialNumber = String(data, 0, 14, Charsets.US_ASCII)) }
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
