package com.celox.segway.core.profile

import com.celox.segway.core.util.BleLog
import com.celox.segway.core.vehicle.VehicleCommand
import com.celox.segway.feature.home.ActiveVehicleHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glues [SpeedProfileRepository] to the [ActiveVehicleHolder]:
 *
 *  • Auto-applies the boot profile right after [Vehicle.state] reports connected
 *  • Exposes a [requestUnlock] entry-point used by both the in-app PIN dialog
 *    and the [com.celox.segway.feature.profiles.UnlockAccessibilityService]
 *  • Tracks the currently applied profile so the UI can highlight it
 *  • Optional auto-revert to boot after N minutes
 *
 * The [unlockTriggers] flow exists so the AccessibilityService (which runs in
 * a different process scope) can publish a unidirectional "I detected the
 * stealth gesture" event. The manager listens to it from a long-lived scope.
 */
@Singleton
class SpeedProfileManager @Inject constructor(
    private val repo: SpeedProfileRepository,
    private val activeHolder: ActiveVehicleHolder,
    private val bleLog: BleLog,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    private val _unlockEvents = MutableSharedFlow<UnlockTrigger>(replay = 0, extraBufferCapacity = 4)
    val unlockTriggers: SharedFlow<UnlockTrigger> = _unlockEvents.asSharedFlow()

    private val _isUnlockModeActive = MutableStateFlow(false)
    val isUnlockModeActive: StateFlow<Boolean> = _isUnlockModeActive.asStateFlow()

    /** Timestamp (epoch ms) at which auto-revert will happen, or null if no revert scheduled. */
    private val _autoRevertAt = MutableStateFlow<Long?>(null)
    val autoRevertAt: StateFlow<Long?> = _autoRevertAt.asStateFlow()

    private var autoRevertJob: Job? = null

    enum class UnlockTrigger { Pin, AccessibilityVolume }

    private companion object {
        const val POLL_INTERVAL_MS = 200L
        // Empirically observed: real-world double-taps land 0.3-2.5 s apart
        // due to firmware debounce + BLE poll cadence. 3 s window covers
        // virtually all intentional taps without false-positives from
        // accidental mode-cycling 5+ s apart.
        const val DOUBLE_TAP_WINDOW_MS = 3000L
    }

    /** Source of a re-lock action. Used for telemetry and the BleLog hint. */
    enum class LockTrigger { ManualButton, AccessibilityVolume, AutoRevert, ConnectAutoApply }

    private val _customButtonTapEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 4)
    val customButtonTapEvents: SharedFlow<Unit> = _customButtonTapEvents.asSharedFlow()

    private val _blinkerRightTapEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 4)
    val blinkerRightTapEvents: SharedFlow<Unit> = _blinkerRightTapEvents.asSharedFlow()

    /** Per-press feedback so the user can verify each detected blinker activation. */
    private val _blinkerRightProgressEvents = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 4)
    val blinkerRightProgressEvents: SharedFlow<Int> = _blinkerRightProgressEvents.asSharedFlow()

    init {
        // Custom-button double-tap watcher — fast-polls reg 0x5A
        // (VCU_DRIVE_MODE) at 200 ms so we can resolve real double-taps.
        // The custom button on ZT3 toggles Walk mode, so each tap flips
        // reg 0x5A between Walk (0x04) and the previous mode. Two such
        // transitions within DOUBLE_TAP_WINDOW_MS = double-tap.
        //
        // Restart logic: the inner Flow combines (vehicle, isReady,
        // customButtonDoubleTapEnabled). Any change cancels the previous
        // watcher and starts a fresh one if all three are positive.
        scope.launch {
            var watcherJob: Job? = null
            kotlinx.coroutines.flow.combine(
                activeHolder.activeVehicle,
                repo.flow.map { it.customButtonDoubleTapEnabled }.distinctUntilChanged(),
            ) { v, enabled -> v to enabled }
                .collect { (vehicle, enabled) ->
                    watcherJob?.cancel()
                    watcherJob = null
                    if (vehicle == null || !enabled) return@collect
                    watcherJob = scope.launch {
                        // Wait until the vehicle is ready, then run.
                        vehicle.state.first { it.isReady }
                        runCustomButtonTapWatcher(vehicle)
                    }
                }
        }

        // Right-blinker triple-tap watcher — polls VCU 0xFF (indicator
        // bitfield, bit 1 = right blinker per Reg-Hunt diff) every 200 ms,
        // edge-detects bit-1 transitions 0→1, three within 3 s = boot lock.
        scope.launch {
            var watcherJob: Job? = null
            kotlinx.coroutines.flow.combine(
                activeHolder.activeVehicle,
                repo.flow.map { it.blinkerRightTripleTapEnabled }.distinctUntilChanged(),
            ) { v, enabled -> v to enabled }
                .collect { (vehicle, enabled) ->
                    watcherJob?.cancel()
                    watcherJob = null
                    if (vehicle == null || !enabled) return@collect
                    watcherJob = scope.launch {
                        vehicle.state.first { it.isReady }
                        runBlinkerRightTripleTapWatcher(vehicle)
                    }
                }
        }

        // Auto-apply boot profile whenever a vehicle becomes ready.
        // `isReady` flips to true only AFTER the crypto handshake has reached
        // at least Stage M (paired-key) — at that point write-register commands
        // are accepted by the scooter. No more arbitrary 1.5 s sleeps.
        scope.launch {
            activeHolder.activeVehicle
                .collect { vehicle ->
                    if (vehicle == null) return@collect
                    vehicle.state
                        .map { it.isConnected to it.isReady }
                        .distinctUntilChanged()
                        .collect { (connected, ready) ->
                            if (!connected) {
                                _isUnlockModeActive.value = false
                                _autoRevertAt.value = null
                                autoRevertJob?.cancel()
                                return@collect
                            }
                            if (!ready) return@collect
                            val settings = repo.flow.first()
                            if (settings.autoApplyOnConnect) {
                                bleLog.note("Profile", "ready → re-lock to ${settings.boot.label}")
                                applyProfile(settings.boot)
                            } else {
                                bleLog.note("Profile", "auto-apply disabled — boot not sent")
                            }
                        }
                }
        }
    }

    suspend fun applyProfile(profile: SpeedProfile) {
        val v = activeHolder.activeVehicle.value ?: return
        bleLog.note("Profile", "apply '${profile.label}' = ${profile.speedKmh} km/h")
        v.execute(VehicleCommand.SetSpeedLimit(profile.speedKmh))
        _activeProfileId.value = profile.id
        val settings = repo.flow.first()
        _isUnlockModeActive.value = profile.id == settings.unlock.id
        scheduleAutoRevertIfNeeded(settings)
    }

    /**
     * Public entry-point for unlock requests. The manager checks the PIN if set
     * and dispatches the unlock-profile [SpeedProfile.SetSpeedLimit] command.
     *
     * @param providedPin null if request comes from a stealth trigger that
     *   should bypass PIN (e.g. AccessibilityVolume with empty PIN).
     */
    suspend fun requestUnlock(via: UnlockTrigger, providedPin: String? = null): UnlockResult {
        val settings = repo.flow.first()
        val pinOk = settings.unlockPin.isEmpty() ||
            (providedPin != null && providedPin == settings.unlockPin)
        if (!pinOk) return UnlockResult.PinRequired
        applyProfile(settings.unlock)
        bleLog.note("Profile", "unlock via $via OK")
        _unlockEvents.tryEmit(via)
        return UnlockResult.Success
    }

    sealed interface UnlockResult {
        data object Success : UnlockResult
        data object PinRequired : UnlockResult
    }

    /**
     * Vol-Up 3× detected by either AccessibilityService or StealthVolumeService.
     * Triggers an unlock (= apply unlock-profile, default 40 km/h).
     */
    fun onAccessibilityVolumeUpTriggered() {
        scope.launch {
            val settings = repo.flow.first()
            if (!settings.accessibilityTriggerEnabled) {
                bleLog.note("Profile", "Vol-Up trigger ignored — disabled in settings")
                return@launch
            }
            if (settings.unlockPin.isEmpty()) {
                requestUnlock(UnlockTrigger.AccessibilityVolume, providedPin = null)
            } else {
                bleLog.note("Profile", "Vol-Up trigger ignored — PIN required")
                _unlockEvents.tryEmit(UnlockTrigger.AccessibilityVolume)
            }
        }
    }

    /**
     * Vol-Down 3× detected by either AccessibilityService or StealthVolumeService.
     * Triggers a re-lock (= apply boot-profile, default 22 km/h).
     */
    fun onAccessibilityVolumeDownTriggered() {
        scope.launch {
            val settings = repo.flow.first()
            if (!settings.accessibilityTriggerEnabled) {
                bleLog.note("Profile", "Vol-Down trigger ignored — disabled in settings")
                return@launch
            }
            bleLog.note("Profile", "Vol-Down 3× → re-lock to ${settings.boot.label}")
            applyProfile(settings.boot)
        }
    }

    /** Backwards-compat: legacy single-button trigger. Routes to Vol-Up = unlock. */
    @Deprecated("Use onAccessibilityVolumeUpTriggered or onAccessibilityVolumeDownTriggered")
    fun onAccessibilityVolumeTriggered() = onAccessibilityVolumeUpTriggered()

    /**
     * Fast-poll loop on reg 0x5A (VCU_DRIVE_MODE) to detect custom-button
     * taps. The custom button toggles between Walk and the previous mode,
     * so each tap flips the register value. Two such transitions within
     * [DOUBLE_TAP_WINDOW_MS] → re-lock to boot profile.
     */
    private suspend fun runCustomButtonTapWatcher(vehicle: com.celox.segway.core.vehicle.Vehicle) {
        bleLog.note("BtnTap", "watcher started — fast-polling 0x5A every 250 ms")
        // Note: we DO NOT force custom_key (VCU 0x4A) to 3 (Walk Mode)
        // anymore — that overwrote the user's own choice in Roller-
        // Einstellungen on every reconnect. If the user picks any value
        // other than 3, the double-tap watcher will simply see no
        // transitions on reg 0x5A (the button does something else now)
        // and stay silent — no false trigger, no surprise behaviour.
        // The hint text in the Custom-Button picker explains this.
        val taps = ArrayDeque<Long>()
        var lastMode: com.celox.segway.core.vehicle.RideMode? = null
        try {
            while (currentScopeIsActive() && repo.flow.first().customButtonDoubleTapEnabled) {
                if (vehicle.state.value.isConnected && vehicle.state.value.isReady) {
                    vehicle.execute(VehicleCommand.ReadRegister(0x5A, 2, 0x16))
                }
                delay(POLL_INTERVAL_MS)
                val current = vehicle.state.value.mode ?: continue
                if (lastMode == null) {
                    lastMode = current
                    continue
                }
                if (current != lastMode) {
                    val now = System.currentTimeMillis()
                    taps.addLast(now)
                    while (taps.isNotEmpty() && now - taps.first() > DOUBLE_TAP_WINDOW_MS) {
                        taps.removeFirst()
                    }
                    bleLog.note("BtnTap", "$lastMode → $current (recent=${taps.size})")
                    if (taps.size >= 2) {
                        bleLog.note("BtnTap", "double-tap → re-lock to boot")
                        val settings = repo.flow.first()
                        applyProfile(settings.boot)
                        _customButtonTapEvents.tryEmit(Unit)
                        taps.clear()
                    }
                    lastMode = current
                }
            }
        } finally {
            bleLog.note("BtnTap", "watcher stopped")
        }
    }

    /**
     * Fast-poll loop on VCU 0xFF (indicator bitfield) to detect three
     * right-blinker activations within DOUBLE_TAP_WINDOW_MS. Same edge-
     * detection pattern as the custom-button watcher: rising-edge of
     * bit 1 = "right blinker turned on" event.
     *
     * Identified register via Reg-Hunt diff on 2026-04-28: enabling the
     * right blinker flipped exactly bit 1 of 0xFF from 0→1.
     *
     * Most ZT3 blinkers are toggle-style (one click on, one click off)
     * so a "tap" here means: turn on, turn off, turn on, turn off, turn
     * on. Three on-presses → trigger.
     */
    private suspend fun runBlinkerRightTripleTapWatcher(vehicle: com.celox.segway.core.vehicle.Vehicle) {
        bleLog.note("BlinkerR", "watcher started — fast-polling VCU 0xFF every 200 ms")
        val taps = ArrayDeque<Long>()
        var lastOn = vehicle.state.value.blinkerRightOn
        // Guard against the watched bit being set at app start (which we
        // saw with VCU 0xFF — bit 1 is set in 0x0A06 because the register
        // is actually a slowly-incrementing counter, not a state bit).
        // Require at least one observed `false` reading before allowing
        // rising-edge detection. If the bit is constantly set (counter,
        // not state) we silently never trigger — better than spamming
        // a "Blinker 1/3" snackbar at every app launch.
        var seenOff = !lastOn
        try {
            while (currentScopeIsActive() && repo.flow.first().blinkerRightTripleTapEnabled) {
                if (vehicle.state.value.isConnected && vehicle.state.value.isReady) {
                    vehicle.execute(VehicleCommand.ReadRegister(0xFF, 2, 0x16))
                }
                delay(POLL_INTERVAL_MS)
                val current = vehicle.state.value.blinkerRightOn
                if (!current) seenOff = true
                if (current && !lastOn && seenOff) {
                    val now = System.currentTimeMillis()
                    taps.addLast(now)
                    while (taps.isNotEmpty() && now - taps.first() > DOUBLE_TAP_WINDOW_MS) {
                        taps.removeFirst()
                    }
                    bleLog.note("BlinkerR", "right-blinker on (recent=${taps.size})")
                    _blinkerRightProgressEvents.tryEmit(taps.size)
                    if (taps.size >= 3) {
                        bleLog.note("BlinkerR", "triple-tap → re-lock to boot")
                        val settings = repo.flow.first()
                        applyProfile(settings.boot)
                        _blinkerRightTapEvents.tryEmit(Unit)
                        taps.clear()
                    }
                }
                lastOn = current
            }
        } finally {
            bleLog.note("BlinkerR", "watcher stopped")
        }
    }

    private fun currentScopeIsActive(): Boolean = scope.isActive

    private fun scheduleAutoRevertIfNeeded(settings: SpeedProfileSettings) {
        autoRevertJob?.cancel()
        if (!_isUnlockModeActive.value) {
            _autoRevertAt.value = null
            return
        }
        if (settings.autoRevertMinutes <= 0) {
            _autoRevertAt.value = null
            return
        }
        val target = System.currentTimeMillis() + settings.autoRevertMinutes * 60_000L
        _autoRevertAt.value = target
        autoRevertJob = scope.launch {
            delay(settings.autoRevertMinutes * 60_000L)
            if (_isUnlockModeActive.value) {
                bleLog.note("Profile", "auto-revert after ${settings.autoRevertMinutes} min")
                applyProfile(settings.boot)
            }
        }
    }
}
