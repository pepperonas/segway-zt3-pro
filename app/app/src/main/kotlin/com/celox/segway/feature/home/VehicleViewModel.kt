package com.celox.segway.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    /** Currently bound vehicle, or null if nothing paired. */
    val vehicle: StateFlow<Vehicle?> = activeVehicleHolder.activeVehicle

    /** Live state stream (empty defaults if no vehicle). */
    val state: StateFlow<VehicleState> = vehicle
        .flatMapLatest { v -> v?.state ?: MutableStateFlow(VehicleState()).asStateFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleState())

    fun toggleLock() = vehicle.value?.let { v ->
        viewModelScope.launch {
            v.execute(if (state.value.isLocked) VehicleCommand.Unlock else VehicleCommand.Lock)
        }
    }

    fun toggleLights() = vehicle.value?.let { v ->
        viewModelScope.launch {
            v.execute(VehicleCommand.SetLights(!state.value.isLightsOn))
        }
    }

    fun toggleCruise() = vehicle.value?.let { v ->
        viewModelScope.launch {
            v.execute(VehicleCommand.SetCruise(!state.value.isCruiseOn))
        }
    }

    fun setMode(mode: RideMode) = vehicle.value?.let { v ->
        viewModelScope.launch { v.execute(VehicleCommand.SetMode(mode)) }
    }

    fun disconnect() = vehicle.value?.let { v ->
        viewModelScope.launch { v.disconnect() }
    }
}
