package com.celox.segway.core.vehicle

import com.celox.segway.core.ble.EllipticPairing
import com.celox.segway.core.ble.FrameCodecClassic
import com.celox.segway.core.ble.FrameCodecCrypto
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.ble.GattState
import com.celox.segway.core.crypto.NinebotCrypto
import com.celox.segway.core.data.PairingPrefs
import com.celox.segway.core.data.VehicleStateCache
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
    private val stateCache: VehicleStateCache? = null,
    private val bleLog: BleLog? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Vehicle {

    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val crypto = NinebotCrypto(scooterName)
    private val codec = FrameCodecCrypto(crypto)

    @Volatile private var handshakeSent = false

    init {
        // NB: do NOT pre-load a persisted cryptoToken into the cipher here.
        // The scooter rotates the token on every cmd=0x5B handshake, so the
        // saved value is always stale; loading it derives `SHA-1(name+token)`
        // as the AES key and breaks Stage-1 (which the scooter still expects
        // to be encrypted with `SHA-1(name+salt)`). The token is captured
        // fresh inside [decrypt] when the cmd=0x5B response arrives.
        // Hydrate from cache: restore the last-known telemetry so the UI shows
        // real values immediately on cold start, instead of zeros until the
        // first poll cycle completes (~2 s after handshake). Live-only fields
        // (speed, current, cells) are NOT cached and stay at default until BLE
        // delivers fresh data.
        scope.launch {
            stateCache?.load(id)?.let { snap ->
                _state.update { s ->
                    s.copy(
                        batteryPercent = snap.batteryPercent,
                        batteryVoltage = snap.batteryVoltage,
                        batteryHealthPercent = snap.batteryHealthPercent,
                        rangeRemainingKm = snap.rangeRemainingKm,
                        odometerKm = snap.odometerKm,
                        tripKm = snap.tripKm,
                        temperatureC = snap.temperatureC,
                        mode = snap.mode?.let { name ->
                            runCatching { RideMode.valueOf(name) }.getOrNull()
                        },
                        isLocked = snap.isLocked,
                        isLightsOn = snap.isLightsOn,
                        firmwareVcu = snap.firmwareVcu,
                        firmwareMcu = snap.firmwareMcu,
                        firmwareBle = snap.firmwareBle,
                        firmwareBms = snap.firmwareBms,
                        serialNumber = snap.serialNumber,
                        regionCode = snap.regionCode,
                        chargeThresholdPercent = snap.chargeThresholdPercent,
                    )
                }
            }
        }
        // Persist updates back to the cache, throttled to every 5 s to avoid
        // hammering DataStore on every poll cycle.
        scope.launch {
            var lastSave = 0L
            _state.collect { s ->
                if (!s.isReady) return@collect
                val now = System.currentTimeMillis()
                if (now - lastSave < 5_000L) return@collect
                lastSave = now
                stateCache?.save(
                    VehicleStateCache.Snapshot(
                        mac = id,
                        batteryPercent = s.batteryPercent,
                        batteryVoltage = s.batteryVoltage,
                        batteryHealthPercent = s.batteryHealthPercent,
                        rangeRemainingKm = s.rangeRemainingKm,
                        odometerKm = s.odometerKm,
                        tripKm = s.tripKm,
                        temperatureC = s.temperatureC,
                        mode = s.mode?.name,
                        isLocked = s.isLocked,
                        isLightsOn = s.isLightsOn,
                        firmwareVcu = s.firmwareVcu,
                        firmwareMcu = s.firmwareMcu,
                        firmwareBle = s.firmwareBle,
                        firmwareBms = s.firmwareBms,
                        serialNumber = s.serialNumber,
                        regionCode = s.regionCode,
                        chargeThresholdPercent = s.chargeThresholdPercent,
                    )
                )
            }
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
            gatt.incoming.collect { raw ->
                val parsed = codec.parse(raw) ?: return@collect
                handleNotify(parsed)
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
        if (command is VehicleCommand.WriteVcuBitfieldBit) {
            executeBitfieldFlip(command)
            return@runCatching
        }
        val frame = encodeCrypto(command) ?: error("Cannot encode $command")
        require(gatt.send(frame)) { "BLE write failed" }
    }.onFailure { Timber.w(it, "execute($command) failed") }

    /**
     * Bitfield write = read current value (from cached state, fresh poll
     * keeps it within ~1s of reality), flip the target bit, write back the
     * full uint16-LE. Updates _state optimistically so the UI flips
     * immediately without waiting for the next poll.
     *
     * NOT atomic on the wire — if two flips race, the later wins. Acceptable
     * for user-driven settings (humans can't toggle two switches at exactly
     * the same instant).
     */
    private suspend fun executeBitfieldFlip(cmd: VehicleCommand.WriteVcuBitfieldBit) {
        val s = _state.value
        val current = when (cmd.offset.toInt() and 0xFF) {
            0x1D -> s.vcuBoolRaw
            0x1E -> s.vcuBool2Raw
            0x1F -> s.vcuBool3Raw
            else -> error("Unknown bitfield offset 0x%02X".format(cmd.offset))
        }
        val newRaw = if (cmd.on) current or (1 shl cmd.bit) else current and (1 shl cmd.bit).inv()
        val payload = byteArrayOf((newRaw and 0xFF).toByte(), ((newRaw ushr 8) and 0xFF).toByte())
        require(gatt.send(codec.writeRegister(0x16, cmd.offset, payload))) { "BLE write failed" }
        // Optimistic state update so the toggle reflects the user action.
        _state.update {
            when (cmd.offset.toInt() and 0xFF) {
                0x1D -> it.copy(vcuBoolRaw = newRaw)
                0x1E -> it.copy(vcuBool2Raw = newRaw)
                0x1F -> it.copy(vcuBool3Raw = newRaw)
                else -> it
            }
        }
    }

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
        // VCU identity (rarely changes; cheap to re-poll)
        Triple(0x16, 0x10.toByte(), 14),  // VCU_SN — serial number
        Triple(0x16, 0x17.toByte(), 2),   // VCU_CtrlV — VCU firmware (the controller itself)
        Triple(0x16, 0x19.toByte(), 2),   // bms_version — BMS FW (mediated via VCU per zt3.json)
        Triple(0x16, 0x1A.toByte(), 16),  // MCU + BLE firmware live at offsets 2-3, 4-5 of this block
        // VCU live state
        Triple(0x16, 0x55.toByte(), 2),   // VCU_BATTPCT — battery %
        Triple(0x16, 0x57.toByte(), 2),   // VCU_Speed — throttle
        Triple(0x16, 0x5A.toByte(), 2),   // VCU_DRIVE_MODE
        Triple(0x16, 0x5B.toByte(), 2),   // VCU_LedMode
        Triple(0x16, 0x5F.toByte(), 2),   // VCU_LeftMileage — range remaining (km × 10?)
        Triple(0x16, 0x68.toByte(), 4),   // VCU_SingleMileage — trip
        Triple(0x16, 0x62.toByte(), 4),   // VCU_Mileage — total
        Triple(0x16, 0x64.toByte(), 4),   // VCU_Runtime — total seconds since manufacture
        Triple(0x16, 0x6A.toByte(), 4),   // VCU_SingleRideTime — trip seconds
        Triple(0x16, 0x6B.toByte(), 2),   // VCU_BodyTemp — °C × 10
        Triple(0x16, 0x58.toByte(), 2),   // VCU_ErrorCode
        Triple(0x16, 0x59.toByte(), 2),   // VCU_WarnCode
        // VCU settings (R/W) — sourced from SHU bootstrap zt3.json. Polled
        // so the Settings screen has fresh values when opened.
        Triple(0x16, 0x1D.toByte(), 2),   // vcu_bool — bitfield (traction, imperial, walk, ramp_parking, boost, indicator sound, alarm)
        Triple(0x16, 0x1E.toByte(), 2),   // vcu_bool_2 — bitfield (app_function_tone, enable_drive, enable_sports)
        Triple(0x16, 0x1F.toByte(), 2),   // vcu_bool_3 — bitfield (auto_headlight, breathing, underglow, charge_now, fold powerOff/disable_alarm, front_position_lamp)
        Triple(0x16, 0x42.toByte(), 2),   // start_speed
        Triple(0x16, 0x49.toByte(), 2),   // auto_off_time (minutes)
        Triple(0x16, 0x4A.toByte(), 2),   // custom_key (Custom Button Action enum)
        Triple(0x16, 0x5D.toByte(), 2),   // tail_light_mode (enum)
        Triple(0x16, 0x6E.toByte(), 2),   // acc_level (acceleration level enum)
        Triple(0x16, 0x70.toByte(), 2),   // kers_level (Energy Recovery enum)
        // BMS deep telemetry
        Triple(0x07, 0x8F.toByte(), 2),   // BMS_SOC — actual battery
        Triple(0x07, 0x8C.toByte(), 2),   // BMS_VOLTAGE — pack voltage
        Triple(0x07, 0x8D.toByte(), 2),   // BMS_CURRENT — pack current (signed)
        Triple(0x07, 0x8E.toByte(), 2),   // BMS_FULL_CAP_PCT — health %
        Triple(0x07, 0x59.toByte(), 2),   // BMS_CycleCountLT — lifetime cycles
        Triple(0x07, 0x92.toByte(), 2),   // BMS_ChargeStatus
        Triple(0x07, 0x96.toByte(), 4),   // BMS_Temps — pack temperatures
        Triple(0x07, 0xF9.toByte(), 2),   // BMS_TEMP — alt temp register
        Triple(0x07, 0xA0.toByte(), 26),  // BMS_CellVolts — 13S pack (verified: 53.35 V / 4.10 V/cell)
        Triple(0x07, 0x82.toByte(), 2),   // charge_threshold — Battery Max Charge % (R/W)
        // MCU
        Triple(0x02, 0x86.toByte(), 2),   // MCU_SPEED — actual current speed
        Triple(0x02, 0x48.toByte(), 2),   // MCU_TEMP_A — motor controller temp A
        Triple(0x02, 0x49.toByte(), 2),   // MCU_TEMP_B — sensor B
        Triple(0x02, 0x40.toByte(), 2),   // MCU_TEMP_A_LASTMAX — historic max
        Triple(0x02, 0x3E.toByte(), 2),   // MCU_TEMP — overall MCU temp
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
     *  2. **If a validated 16-byte appRandom is in [pairingPrefs] for this MAC**:
     *     skip o1 and call `setRandomAppData(persisted)` — SHU's resume path.
     *     Else: send `o1(R)` (cmd=0x5C, plen=0x10) until M flag, then persist
     *     the freshly-generated random for next-time resume.
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

        // Stage 2: try resume first (if we have a validated persisted random),
        // then fall back to fresh pair (o1) if Stage 3 times out — that catches
        // the case where the scooter regenerated its pair-key while we weren't
        // looking (e.g. after the user paired with the official Segway app or
        // SHU). Length and zero-checks happen inside loadCryptoRandom.
        val persisted = pairingPrefs?.loadCryptoRandom(id)
        var resumedFromPersisted = false
        if (persisted != null) {
            crypto.setRandomAppData(persisted)
            bleLog?.note("Crypto", "M: resumed via persisted random (key=SHA-1(R+T))")
            resumedFromPersisted = true
        } else {
            if (!sendStage2FreshPair()) return
        }

        // Stage 3: D0(challenge) → wait for O (fully paired). If we resumed
        // from a persisted random and Stage 3 times out, the scooter has
        // rotated its pair-key on us (e.g. after pairing with the stock
        // Segway app, SHU, or XiaoDash). Drop persisted, do fresh o1, retry.
        if (!sendStage3ChallengeEcho()) {
            if (resumedFromPersisted) {
                bleLog?.note("Crypto", "Stage 3 failed on resume — fall back to fresh pair")
                crypto.resetPairingState()  // wipe stale stage-2 key, keep token
                if (!sendStage2FreshPair()) return
                if (!sendStage3ChallengeEcho()) {
                    bleLog?.note("Crypto", "stage 3 (O) timed out even after fresh-pair fallback")
                }
            } else {
                bleLog?.note("Crypto", "stage 3 (O) timed out — challenge-echo not acked")
            }
        }
        if (crypto.stageFullyPaired) {
            bleLog?.note("Crypto", "O: fully paired — ready for commands")
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

    /**
     * Stage 2 — fresh pair: send `o1(R)` (cmd=0x5C, plen=0x10) until M flag.
     * Returns true on success. On failure, sets handshakeSent=true and logs.
     * On success persists the freshly-generated appRandom to [pairingPrefs]
     * so the next connect can take the resume path without an OOB press.
     */
    private suspend fun sendStage2FreshPair(): Boolean {
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
            return false
        }
        bleLog?.note("Crypto", "M: paired-key (SHA-1(R+T))")
        // Persist the freshly-generated appRandom so the next connect can
        // resume without bothering the user for a power-button press.
        pairingPrefs?.let { prefs ->
            runCatching { prefs.saveCryptoRandom(id, crypto.snapshotRandom()) }
                .onFailure { bleLog?.note("Crypto", "saveCryptoRandom failed: ${it.message}") }
        }
        return true
    }

    /**
     * Stage 3 — challenge-echo: send `D0(challenge)` (cmd=0x5D) until O flag.
     * Returns true on success, false on timeout.
     */
    private suspend fun sendStage3ChallengeEcho(): Boolean {
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
        return crypto.stageFullyPaired
    }

    private fun encodeCrypto(cmd: VehicleCommand): ByteArray? = when (cmd) {
        // Pattern-Lock register (LRRL key combo). Verified register 0x71 from
        // segMod/x3regs.h. Lock/Unlock here is the scooter's *anti-theft* lock,
        // independent of our app's speed-limit lock.
        VehicleCommand.Lock ->
            codec.writeRegister(0x16, 0x71, byteArrayOf(0x01, 0x00))
        VehicleCommand.Unlock ->
            codec.writeRegister(0x16, 0x71, byteArrayOf(0x00, 0x00))
        // VCU_DRIVE_MODE — reg 0x5A. ZT3 Pro D firmware treats this register as
        // **read-only via BLE** (verified 2026-04-28: write with cmd=0x02 acks
        // with beep but readback unchanged; cmd=0x06 doesn't even ack). Mode is
        // hardware-only (dashboard double-press of power button). We still send
        // the WRITE for completeness in case a future firmware enables it.
        is VehicleCommand.SetMode ->
            codec.writeRegister(
                0x16, 0x5A,
                byteArrayOf(
                    when (cmd.mode) {
                        RideMode.Eco -> 0x01
                        RideMode.Drive -> 0x02
                        RideMode.Sport -> 0x03
                        RideMode.Walk -> 0x04
                    }.toByte(),
                    0x00
                )
            )
        // VCU_LedMode — reg 0x5B. Same read-only situation as Mode on ZT3 Pro D.
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
            codec.readRegister(cmd.dst, cmd.offset.toByte(), cmd.length)
        VehicleCommand.ReadBlackBox ->
            codec.readRegister(0x16, 0xF0.toByte(), 64)
        VehicleCommand.ReadFirmware ->
            codec.readRegister(0x16, 0x1A, 16)

        // Generic numeric writes (uint16-LE). Used by the Settings screen
        // for sliders + enums whose register addresses come from
        // zt3-settings-registers.md.
        is VehicleCommand.WriteVcuU16 ->
            codec.writeRegister(0x16, cmd.offset, byteArrayOf((cmd.value and 0xFF).toByte(), ((cmd.value ushr 8) and 0xFF).toByte()))
        is VehicleCommand.WriteBmsU16 ->
            codec.writeRegister(0x07, cmd.offset, byteArrayOf((cmd.value and 0xFF).toByte(), ((cmd.value ushr 8) and 0xFF).toByte()))

        // Bitfield bit-flip is NOT a single encode — it's read-modify-write
        // and is intercepted in execute() before this function is called.
        is VehicleCommand.WriteVcuBitfieldBit -> null
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
                val sn = String(data, 0, 14, Charsets.US_ASCII)
                // Region is encoded in the SN — verified via SHU decompile
                // (sources/j6/p.java + g6/p1.java): there is NO dedicated
                // region register on the x3 platform. SHU pattern-matches
                // the SN against a `sn_region_map` JSON loaded from its
                // backend. ZT3 prefix `1K1` + 4th char = region letter:
                //   U = US (40 km/h, unrestricted throttle)
                //   D = DE  E = EU  G = GB  F = FR  C = CN  K = KR  J = JP
                val regionLetter = sn.getOrNull(3)?.uppercaseChar()
                val region = when (regionLetter) {
                    'U' -> "US"
                    'D' -> "DE"
                    'E' -> "EU"
                    'G' -> "GB"
                    'F' -> "FR"
                    'C' -> "CN"
                    'K' -> "KR"
                    'J' -> "JP"
                    null -> ""
                    else -> regionLetter.toString()
                }
                _state.update { it.copy(serialNumber = sn, regionCode = region) }
            }
            0x17 -> if (data.size >= 2) {
                _state.update {
                    it.copy(firmwareVcu = "%d.%d.%d".format(data[1].toInt() and 0xFF, (data[0].toInt() ushr 4) and 0x0F, data[0].toInt() and 0x0F))
                }
            }
            0x19 -> if (data.size >= 2) {
                // bms_version (BMS firmware), mediated via VCU per zt3.json.
                // Same nibble-packed format as VCU/MCU/BLE versions.
                _state.update {
                    it.copy(firmwareBms = "%d.%d.%d".format(data[1].toInt() and 0xFF, (data[0].toInt() ushr 4) and 0x0F, data[0].toInt() and 0x0F))
                }
            }
            0x1A -> if (data.size >= 6) {
                // ZT3: reg 0x1A itself (BMS2_VER) is unused; MCU lives at
                // offset 2-3 (= reg 0x1B?) and BLE at 4-5 — verified
                // empirically (MCU 39.9.15, BLE 8.0.9, both match dashboard).
                _state.update { st ->
                    st.copy(
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
                // ZT3 firmware is 1-indexed for VCU_DRIVE_MODE (verified
                // 2026-04-28 by capturing all 4 modes via dashboard cycle):
                //   0x01 = E (Eco), 0x02 = D (Drive), 0x03 = S (Sport),
                //   0x04 = laufendes Männchen (Walk).
                val raw = data[0].toInt() and 0xFF
                bleLog?.note("Mode", "reg 0x5A raw=0x%02X (%d)".format(raw, raw))
                val mode = when (raw) {
                    0x01 -> RideMode.Eco
                    0x02 -> RideMode.Drive
                    0x03 -> RideMode.Sport
                    0x04 -> RideMode.Walk
                    else -> null
                }
                if (mode != null) _state.update { it.copy(mode = mode) }
            }
            0x5B -> if (data.size >= 1) {
                // VCU_LedMode: 0=off, others=on (low/high/auto-modes).
                _state.update { it.copy(isLightsOn = (data[0].toInt() and 0xFF) != 0) }
            }
            0x58 -> if (data.size >= 2) _state.update { it.copy(errorCode = leU16(data, 0)) }
            0x59 -> if (data.size >= 2) _state.update { it.copy(warnCode = leU16(data, 0)) }
            // Settings (R/W). All uint16-LE per zt3.json.
            0x1D -> if (data.size >= 2) _state.update { it.copy(vcuBoolRaw = leU16(data, 0)) }
            0x1E -> if (data.size >= 2) _state.update { it.copy(vcuBool2Raw = leU16(data, 0)) }
            0x1F -> if (data.size >= 2) _state.update { it.copy(vcuBool3Raw = leU16(data, 0)) }
            0x42 -> if (data.size >= 2) _state.update { it.copy(startSpeedKmh = leU16(data, 0).coerceIn(0, 5)) }
            0x49 -> if (data.size >= 2) _state.update { it.copy(autoOffMinutes = leU16(data, 0).coerceAtMost(60)) }
            0x4A -> if (data.size >= 2) _state.update { it.copy(customKeyMode = leU16(data, 0)) }
            0x5D -> if (data.size >= 2) _state.update { it.copy(tailLightMode = leU16(data, 0)) }
            0x6E -> if (data.size >= 2) _state.update { it.copy(accelerationLevel = leU16(data, 0)) }
            0x70 -> if (data.size >= 2) _state.update { it.copy(kersLevel = leU16(data, 0)) }
            // VCU_Mileage / VCU_SingleMileage — empirically (2026-04-28 logcat
            // capture) the meaningful value sits in the low u16: e.g.
            // odometer reg 0x62 = `[12 00 00 00]` → 18 km, trip reg 0x68 =
            // `[07 00 46 0A]` → 7 km. The high u16 contains either a counter
            // (#rides) or a precision-fractional component we don't decode.
            0x62 -> if (data.size >= 2) _state.update { it.copy(odometerKm = leU16(data, 0).toFloat()) }
            0x68 -> if (data.size >= 2) _state.update { it.copy(tripKm = leU16(data, 0).toFloat()) }
            // VCU_LeftMileage — verified `[04 0B]` = 2820 → 28.2 km matches
            // dashboard at 94 % SOC, so unit is km × 100.
            0x5F -> if (data.size >= 2) _state.update { it.copy(rangeRemainingKm = leU16(data, 0) / 100f) }
            // VCU_Runtime — verified `[E8 7F 00 00]` = 32744 sec = 9h 05m as
            // 32-bit second counter. Reasonable for a young scooter.
            0x64 -> if (data.size >= 4) _state.update { it.copy(totalRuntimeSeconds = leU32(data, 0).toLong() and 0xFFFFFFFFL) }
            // VCU_SingleRideTime — verified `[4F 00 BE 00]` → low u16 = 79 sec
            // (= current ride, makes sense). The high u16 might be number of
            // rides or peak duration, leave undecoded.
            0x6A -> if (data.size >= 2) _state.update { it.copy(tripDurationSeconds = leU16(data, 0).toLong()) }
            0x6B -> if (data.size >= 2) {
                _state.update { it.copy(temperatureC = leU16Signed(data, 0) / 10f) }
            }
            0xF0 -> _state.update { it.copy(blackBoxRaw = data) }
        }
    }

    private fun handleBmsRegister(offset: Int, data: ByteArray) {
        when (offset) {
            // BMS_CycleCountLT — lifetime cycle count.
            0x59 -> if (data.size >= 2) _state.update { it.copy(batteryCycleCount = leU16(data, 0)) }
            // BMS_VOLTAGE — pack voltage, raw V × 100 (e.g. 4200 = 42.00 V).
            0x8C -> if (data.size >= 2) _state.update { it.copy(batteryVoltage = leU16(data, 0) / 100f) }
            // BMS_CURRENT — pack current, signed A × 100. Negative = discharge.
            0x8D -> if (data.size >= 2) _state.update { it.copy(batteryCurrentA = leU16Signed(data, 0) / 100f) }
            // BMS_FULL_CAP_PCT — battery state-of-health (% of original capacity).
            0x8E -> if (data.size >= 1) _state.update { it.copy(batteryHealthPercent = (data[0].toInt() and 0xFF).coerceIn(0, 100)) }
            // BMS_SOC — actual battery state-of-charge, more accurate than VCU_BATTPCT.
            0x8F -> if (data.size >= 1) {
                _state.update { it.copy(batteryPercent = (data[0].toInt() and 0xFF).coerceIn(0, 100)) }
            }
            // BMS_ChargeStatus.
            0x92 -> if (data.size >= 2) _state.update { it.copy(chargingState = leU16(data, 0)) }
            // BMS_Temps — verified `[13 00 13 00]` = both probes 19 °C raw,
            // direct °C (no bias). Two probes packed as 4 × u8.
            0x96 -> if (data.size >= 2) {
                val t1 = data[0].toInt() and 0xFF
                val t2 = data[1].toInt() and 0xFF
                _state.update { it.copy(batteryTempC = maxOf(t1, t2).toFloat()) }
            }
            // BMS_TEMP — single probe, also direct °C in u16 LE (verified [13 00] = 19).
            0xF9 -> if (data.size >= 2) {
                val t = leU16Signed(data, 0)
                if (t in -40..120) _state.update { it.copy(batteryTempC = t.toFloat()) }
            }
            // BMS_CellVolts — N × 16-bit cell voltages in millivolts (LE).
            0xA0 -> if (data.size >= 4 && data.size % 2 == 0) {
                val cells = IntArray(data.size / 2) { i -> leU16(data, i * 2) }
                _state.update { it.copy(cellVoltagesMv = cells) }
            }
            // charge_threshold — Battery Max Charge % cutoff (R/W, 80-100).
            // Per zt3.json bootstrap: BMS dst=0x07, offset=0x82, uint16-LE.
            0x82 -> if (data.size >= 2) {
                _state.update { it.copy(chargeThresholdPercent = leU16(data, 0).coerceIn(0, 100)) }
            }
        }
    }

    private fun handleMcuRegister(offset: Int, data: ByteArray) {
        when (offset) {
            // MCU_SPEED — current actual speed, km/h × 10.
            0x86 -> if (data.size >= 2) {
                _state.update { it.copy(speedKmh = leU16(data, 0) / 10f) }
            }
            // MCU_TEMP_A — motor-controller temperature, °C × 10. While
            // standing still it returns `[00 00]` (= 0 °C), so we don't
            // override `temperatureC` (which carries VCU body temp 0x6B).
            // Motor temps live separately in motorTempAC/BC and are surfaced
            // by the dedicated motor card.
            0x48 -> if (data.size >= 2) {
                val v = leU16Signed(data, 0) / 10f
                if (v in -20f..150f) _state.update { it.copy(motorTempAC = v) }
            }
            0x49 -> if (data.size >= 2) {
                val v = leU16Signed(data, 0) / 10f
                if (v in -20f..150f) _state.update { it.copy(motorTempBC = v) }
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
