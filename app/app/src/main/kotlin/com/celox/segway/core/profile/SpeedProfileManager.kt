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

    /** Source of a re-lock action. Used for telemetry and the BleLog hint. */
    enum class LockTrigger { ManualButton, AccessibilityVolume, AutoRevert, ConnectAutoApply }

    init {
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
