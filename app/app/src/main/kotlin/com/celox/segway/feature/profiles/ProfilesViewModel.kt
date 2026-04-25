package com.celox.segway.feature.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.profile.SpeedProfile
import com.celox.segway.core.profile.SpeedProfileManager
import com.celox.segway.core.profile.SpeedProfileRepository
import com.celox.segway.core.profile.SpeedProfileSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repo: SpeedProfileRepository,
    private val manager: SpeedProfileManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings: StateFlow<SpeedProfileSettings> =
        repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, SpeedProfileSettings())

    val accessibilityEnabledOnDevice: StateFlow<Boolean> = MutableStateFlow(
        AccessibilityHelper.isOurServiceEnabled(context)
    ).asStateFlow()

    fun updateBoot(speedKmh: Int) = viewModelScope.launch {
        repo.update { it.copy(boot = it.boot.copy(speedKmh = speedKmh.coerceIn(5, 40))) }
    }

    fun updateBootLabel(label: String) = viewModelScope.launch {
        repo.update { it.copy(boot = it.boot.copy(label = label.take(16))) }
    }

    fun updateQuickAction(index: Int, profile: SpeedProfile) = viewModelScope.launch {
        repo.update { current ->
            val updated = current.quickActions.toMutableList()
            if (index in updated.indices) updated[index] = profile.copy(
                speedKmh = profile.speedKmh.coerceIn(5, 40),
                label = profile.label.take(16)
            )
            current.copy(quickActions = updated.toList())
        }
    }

    fun updateUnlock(speedKmh: Int) = viewModelScope.launch {
        repo.update { it.copy(unlock = it.unlock.copy(speedKmh = speedKmh.coerceIn(5, 60))) }
    }

    fun updatePin(pin: String) = viewModelScope.launch {
        // Allow empty (= no PIN) or 4-8 digits
        if (pin.isNotEmpty() && (pin.length !in 4..8 || !pin.all { it.isDigit() })) return@launch
        repo.update { it.copy(unlockPin = pin) }
    }

    fun setAccessibilityToggle(enabled: Boolean) = viewModelScope.launch {
        repo.update { it.copy(accessibilityTriggerEnabled = enabled) }
    }

    fun updateAutoRevert(minutes: Int) = viewModelScope.launch {
        repo.update { it.copy(autoRevertMinutes = minutes.coerceIn(0, 240)) }
    }
}
