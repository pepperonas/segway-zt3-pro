package com.celox.segway.feature.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.ble.BleScanner
import com.celox.segway.core.ble.DiscoveredScooter
import com.celox.segway.core.data.VehicleDao
import com.celox.segway.core.data.VehicleEntity
import com.celox.segway.feature.home.ActiveVehicleHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        val pairedAddress: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Fires once when auto-pair has bound an active vehicle — UI navigates back. */
    private val _pairedEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pairedEvent: SharedFlow<String> = _pairedEvent.asSharedFlow()

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
                    // Auto-pair the first scooter we see — the scanner already
                    // filtered to NB/NC manufacturer-prefix devices, so anything
                    // that arrives here is a Ninebot/Segway.
                    if (_state.value.pairedAddress == null) {
                        pair(device)
                    }
                }
            } catch (ce: CancellationException) {
                // Expected when the scope is torn down — don't surface it.
                throw ce
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
        if (_state.value.pairedAddress != null) return  // already pairing
        _state.update { it.copy(pairedAddress = device.address, scanning = false) }
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
            _pairedEvent.tryEmit(device.address)
        }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
