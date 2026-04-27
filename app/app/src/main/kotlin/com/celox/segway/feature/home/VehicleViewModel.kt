package com.celox.segway.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.profile.SpeedProfile
import com.celox.segway.core.profile.SpeedProfileManager
import com.celox.segway.core.profile.SpeedProfileRepository
import com.celox.segway.core.profile.SpeedProfileSettings
import com.celox.segway.core.vehicle.RideMode
import com.celox.segway.core.vehicle.Vehicle
import com.celox.segway.core.vehicle.VehicleCommand
import com.celox.segway.core.vehicle.VehicleState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val activeVehicleHolder: ActiveVehicleHolder,
    private val profileManager: SpeedProfileManager,
    private val profileRepo: SpeedProfileRepository,
) : ViewModel() {

    val vehicle: StateFlow<Vehicle?> = activeVehicleHolder.activeVehicle

    val state: StateFlow<VehicleState> = vehicle
        .flatMapLatest { v -> v?.state ?: MutableStateFlow(VehicleState()).asStateFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleState())

    val profiles: StateFlow<SpeedProfileSettings> =
        profileRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, SpeedProfileSettings())

    val activeProfileId: StateFlow<String?> = profileManager.activeProfileId
    val autoRevertAt: StateFlow<Long?> = profileManager.autoRevertAt
    val isUnlockActive: StateFlow<Boolean> = profileManager.isUnlockModeActive

    private val _snackbar = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 4
    )
    val snackbar: kotlinx.coroutines.flow.SharedFlow<String> = _snackbar

    init {
        viewModelScope.launch {
            profileManager.customButtonTapEvents.collect {
                _snackbar.tryEmit("🔒 Doppel-Tap Walk-Knopf → ${profiles.value.boot.speedKmh} km/h")
            }
        }
        viewModelScope.launch {
            profileManager.brakeTripleTapEvents.collect {
                _snackbar.tryEmit("🔒 3× Bremse → ${profiles.value.boot.speedKmh} km/h")
            }
        }
        viewModelScope.launch {
            // Per-press feedback: makes it obvious to the user whether the
            // watcher is detecting brake events at all (especially when
            // testing — 0xD5 only changes during ride, not standstill).
            profileManager.brakeProgressEvents.collect { count ->
                _snackbar.tryEmit("Bremse $count/3")
            }
        }
    }

    fun toggleLock() = vehicle.value?.let { v ->
        viewModelScope.launch {
            v.execute(if (state.value.isLocked) VehicleCommand.Unlock else VehicleCommand.Lock)
        }
    }

    fun toggleLights() = vehicle.value?.let { v ->
        viewModelScope.launch { v.execute(VehicleCommand.SetLights(!state.value.isLightsOn)) }
    }

    fun toggleCruise() = vehicle.value?.let { v ->
        viewModelScope.launch { v.execute(VehicleCommand.SetCruise(!state.value.isCruiseOn)) }
    }

    fun setMode(mode: RideMode) = vehicle.value?.let { v ->
        viewModelScope.launch { v.execute(VehicleCommand.SetMode(mode)) }
    }

    fun applyProfile(profile: SpeedProfile) {
        viewModelScope.launch {
            profileManager.applyProfile(profile)
            _snackbar.tryEmit("Applied ${profile.label} (${profile.speedKmh} km/h)")
        }
    }

    /** Force-revert to the boot profile (= lock). */
    fun reLock() {
        viewModelScope.launch {
            profileManager.applyProfile(profiles.value.boot)
            _snackbar.tryEmit("Locked to ${profiles.value.boot.speedKmh} km/h")
        }
    }

    /** Returns whether a PIN is required (caller must show the dialog). */
    fun unlockRequiresPin(): Boolean = profiles.value.unlockPin.isNotEmpty()

    /**
     * Apply the unlock profile. If [pin] is wrong → returns false.
     */
    suspend fun confirmUnlock(pin: String?): Boolean {
        return when (profileManager.requestUnlock(SpeedProfileManager.UnlockTrigger.Pin, pin)) {
            SpeedProfileManager.UnlockResult.Success -> true
            SpeedProfileManager.UnlockResult.PinRequired -> false
        }
    }

    /**
     * Tear down the active vehicle: drop the BLE connection AND clear the
     * holder so the UI immediately falls back to the EmptyState. Without
     * the unbind() call the holder still points at the dead vehicle and
     * the screen looks like it's still connected.
     */
    fun disconnect() {
        activeVehicleHolder.unbind()
    }

    /** Re-run connect() on the active vehicle. Used by the offline banner. */
    fun reconnect() {
        activeVehicleHolder.reconnectActive()
    }
}
