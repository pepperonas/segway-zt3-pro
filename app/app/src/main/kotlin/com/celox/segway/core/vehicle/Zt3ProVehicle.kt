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
    /**
     * Poll plan based on the verified ZT3 register reference doc:
     * `reverse-engineering/protocol/zt3-ble-register-reference.md`.
     */
    private val pollPlan: List<Triple<Byte, Byte, Int>> = listOf(
        Triple(0x16, 0x55.toByte(), 2),   // VCU_BATTPCT — battery %
        Triple(0x16, 0x57.toByte(), 2),   // VCU_Speed — throttle
        Triple(0x16, 0x5A.toByte(), 2),   // VCU_DRIVE_MODE
        Triple(0x16, 0x68.toByte(), 4),   // VCU_SingleMileage — trip
        Triple(0x16, 0x62.toByte(), 4),   // VCU_Mileage — total
        Triple(0x16, 0x6B.toByte(), 2),   // VCU_BodyTemp — °C × 10
        Triple(0x16, 0x58.toByte(), 2),   // VCU_ErrorCode
        Triple(0x16, 0x59.toByte(), 2),   // VCU_WarnCode
        Triple(0x07, 0x8F.toByte(), 2),   // BMS_SOC — actual battery
        Triple(0x07, 0x8C.toByte(), 2),   // BMS_VOLTAGE
        Triple(0x07, 0x96.toByte(), 4),   // BMS_Temps
        Triple(0x02, 0x86.toByte(), 2),   // MCU_SPEED — actual current speed
        Triple(0x02, 0x48.toByte(), 2),   // MCU_TEMP_A — motor temperature
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
        // Pattern-Lock register (LRRL key combo). Verified register 0x71 from
        // segMod/x3regs.h. Lock/Unlock here is the scooter's *anti-theft* lock,
        // independent of our app's speed-limit lock.
        VehicleCommand.Lock ->
            codec.writeRegister(0x16, 0x71, byteArrayOf(0x01, 0x00))
        VehicleCommand.Unlock ->
            codec.writeRegister(0x16, 0x71, byteArrayOf(0x00, 0x00))
        // VCU_DRIVE_MODE — reg 0x5A. Standard `cmd=0x02 (WRITE), arg=register`
        // wire form (Format A). The doc's "Format B" puts the register byte
        // in the cmd slot which collides with handshake opcodes 0x5B/5C/5D
        // (PRE_COMM/SET_PWD/AUTH) — confirmed in 2026-04-28 field test.
        // Values per ZT3 register reference: 1=Walk, 2=Eco, 3=Sport, 4=Race.
        is VehicleCommand.SetMode ->
            codec.writeRegister(
                0x16, 0x5A,
                byteArrayOf(
                    when (cmd.mode) { RideMode.Eco -> 0x02; RideMode.Drive -> 0x03; RideMode.Sport -> 0x04 }.toByte(),
                    0x00
                )
            )
        // VCU_LedMode — reg 0x5B. 0=off, 1=low, 2=high, 3=auto-low, 4=auto-high, 5=auto-off.
        is VehicleCommand.SetLights ->
            codec.writeRegister(0x16, 0x5B, byteArrayOf(if (cmd.on) 0x01 else 0x00, 0x00))
        // VCU_TailLightMode — reg 0x5D. `00 00` brighter when braking, `01 00` flash.
        is VehicleCommand.SetCruise ->
            codec.writeRegister(0x16, 0x5D, byteArrayOf(if (cmd.on) 0x01 else 0x00, 0x00))
        is VehicleCommand.SetSpeedLimit ->
            // Verified against SHU's wire (CRYPTO_DUMP C=50, 2026-04-27):
            //   `5A A5 02 3E 16 02 48 14 16` for "set City to 22 km/h"
            //   = write reg 0x48 on dst=0x16, payload=[eco_limit=0x14, city_limit=kmh]
            codec.writeRegister(
                0x16.toByte(), 0x48, byteArrayOf(0x14, cmd.kmh.toByte())
            )
        // VCU_EGear — power. Standard write to reg 0x79 with `02 00` for OFF.
        VehicleCommand.Reboot ->
            codec.writeRegister(0x16, 0x79, byteArrayOf(0x02, 0x00))
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

        // ZT3 register layout per `reverse-engineering/protocol/zt3-ble-register-reference.md`.
        // Branch by source-ECU since the same `arg` byte means different things on
        // different sub-modules (e.g., 0x86 on dst=0x02 = MCU_SPEED, would be unrelated on 0x16).
        val src = parsed.src.toInt() and 0xFF
        when (src) {
            0x16 -> handleVcuRegister(offset, data)
            0x07 -> handleBmsRegister(offset, data)
            0x02 -> handleMcuRegister(offset, data)
        }
    }

    private fun handleVcuRegister(offset: Int, data: ByteArray) {
        when (offset) {
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
            0x55 -> if (data.size >= 1) {
                _state.update { it.copy(batteryPercent = (data[0].toInt() and 0xFF).coerceIn(0, 100)) }
            }
            0x57 -> if (data.size >= 2) {
                _state.update { it.copy(speedKmh = (leU16(data, 0).coerceIn(0, 800)) / 10f) }
            }
            0x5A -> if (data.size >= 1) {
                val mode = when (data[0].toInt() and 0xFF) {
                    0x02 -> RideMode.Eco
                    0x03 -> RideMode.Drive
                    0x04 -> RideMode.Sport
                    else -> RideMode.Drive
                }
                _state.update { it.copy(mode = mode) }
            }
            0x58 -> if (data.size >= 2) _state.update { it.copy(errorCode = leU16(data, 0)) }
            // VCU_Mileage / VCU_SingleMileage — empirically observed to be in
            // 10-meter units → divide by 100 for km, but ZT3 returns very large
            // values implying a different scaling. Field-test 2026-04-27 shows
            // tripKm = 136970.25 km with /100, suggesting the actual unit is
            // smaller. /100000 brings it to ~14 km which matches the dashboard.
            0x62 -> if (data.size >= 4) _state.update { it.copy(odometerKm = leU32(data, 0) / 100000f) }
            0x68 -> if (data.size >= 4) _state.update { it.copy(tripKm = leU32(data, 0) / 100000f) }
            0x6B -> if (data.size >= 2) {
                _state.update { it.copy(temperatureC = leU16Signed(data, 0) / 10f) }
            }
            0xF0 -> _state.update { it.copy(blackBoxRaw = data) }
        }
    }

    private fun handleBmsRegister(offset: Int, data: ByteArray) {
        when (offset) {
            // BMS_SOC — actual battery state-of-charge, more accurate than VCU_BATTPCT.
            0x8F -> if (data.size >= 1) {
                _state.update { it.copy(batteryPercent = (data[0].toInt() and 0xFF).coerceIn(0, 100)) }
            }
        }
    }

    private fun handleMcuRegister(offset: Int, data: ByteArray) {
        when (offset) {
            // MCU_SPEED — current actual speed, km/h × 10.
            0x86 -> if (data.size >= 2) {
                _state.update { it.copy(speedKmh = leU16(data, 0) / 10f) }
            }
        }
    }

    private fun leU16(b: ByteArray, idx: Int): Int =
        (b[idx].toInt() and 0xFF) or ((b[idx + 1].toInt() and 0xFF) shl 8)

    private fun leU32(b: ByteArray, idx: Int): Int =
        (b[idx].toInt() and 0xFF) or
            ((b[idx + 1].toInt() and 0xFF) shl 8) or
            ((b[idx + 2].toInt() and 0xFF) shl 16) or
            ((b[idx + 3].toInt() and 0xFF) shl 24)

    private fun leU16Signed(b: ByteArray, idx: Int): Int {
        val u = leU16(b, idx)
        return if (u >= 0x8000) u - 0x10000 else u
    }
}
