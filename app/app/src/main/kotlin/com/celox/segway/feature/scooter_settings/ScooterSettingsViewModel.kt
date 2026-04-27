package com.celox.segway.feature.scooter_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.vehicle.Vehicle
import com.celox.segway.core.vehicle.VehicleCommand
import com.celox.segway.core.vehicle.VehicleState
import com.celox.segway.feature.home.ActiveVehicleHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Scooter Settings screen. Wraps [ActiveVehicleHolder]
 * and provides typed write APIs for each setting; reads come straight
 * from [VehicleState] which the periodic poll keeps fresh.
 */
@HiltViewModel
class ScooterSettingsViewModel @Inject constructor(
    private val activeVehicleHolder: ActiveVehicleHolder,
) : ViewModel() {

    val vehicle: StateFlow<Vehicle?> = activeVehicleHolder.activeVehicle

    val state: StateFlow<VehicleState> = vehicle
        .flatMapLatest { v -> v?.state ?: MutableStateFlow(VehicleState()).asStateFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleState())

    /** Toggle a single bit in a VCU bitfield register (0x1D / 0x1E / 0x1F). */
    fun setBitfieldBit(offset: Byte, bit: Int, on: Boolean) = vehicle.value?.let { v ->
        viewModelScope.launch {
            v.execute(VehicleCommand.WriteVcuBitfieldBit(offset, bit, on))
        }
    }

    /** Write a uint16-LE value to a VCU register. */
    fun writeVcuU16(offset: Byte, value: Int) = vehicle.value?.let { v ->
        viewModelScope.launch { v.execute(VehicleCommand.WriteVcuU16(offset, value)) }
    }

    /** Write a uint16-LE value to a BMS register. */
    fun writeBmsU16(offset: Byte, value: Int) = vehicle.value?.let { v ->
        viewModelScope.launch { v.execute(VehicleCommand.WriteBmsU16(offset, value)) }
    }
}
