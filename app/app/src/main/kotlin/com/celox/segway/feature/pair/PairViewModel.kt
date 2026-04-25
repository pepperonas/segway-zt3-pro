package com.celox.segway.feature.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.ble.BleScanner
import com.celox.segway.core.ble.DiscoveredScooter
import com.celox.segway.core.data.VehicleDao
import com.celox.segway.core.data.VehicleEntity
import com.celox.segway.feature.home.ActiveVehicleHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairViewModel @Inject constructor(
    private val scanner: BleScanner,
    private val vehicleDao: VehicleDao,
    private val activeHolder: ActiveVehicleHolder,
) : ViewModel() {

    data class UiState(
        val scanning: Boolean = false,
        val devices: List<DiscoveredScooter> = emptyList(),
        val errorText: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, errorText = null, devices = emptyList()) }
        scanJob = viewModelScope.launch {
            try {
                scanner.scan().collect { device ->
                    _state.update { ui ->
                        val existing = ui.devices.indexOfFirst { it.address == device.address }
                        val newList = if (existing >= 0) {
                            ui.devices.toMutableList().also { it[existing] = device }
                        } else {
                            ui.devices + device
                        }
                        ui.copy(devices = newList.sortedByDescending { it.rssi })
                    }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorText = t.message ?: "Scan failed", scanning = false) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanning = false) }
    }

    fun pair(device: DiscoveredScooter) {
        viewModelScope.launch {
            val name = device.name ?: "ZT3 Pro"
            vehicleDao.upsert(
                VehicleEntity(
                    mac = device.address,
                    displayName = name,
                    model = "ZT3 Pro",
                )
            )
            stopScan()
            activeHolder.bind(device.address, name)
        }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
