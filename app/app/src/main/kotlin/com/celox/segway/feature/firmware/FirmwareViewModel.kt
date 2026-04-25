package com.celox.segway.feature.firmware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.ota.FirmwareUpdater
import com.celox.segway.core.repo.CfwRepoClient
import com.celox.segway.core.repo.FirmwareRelease
import com.celox.segway.core.repo.FirmwareTarget
import com.celox.segway.core.vehicle.VehicleCommand
import com.celox.segway.feature.home.ActiveVehicleHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FirmwareViewModel @Inject constructor(
    private val repo: CfwRepoClient,
    private val activeHolder: ActiveVehicleHolder,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val errorText: String? = null,
        val vcuReleases: List<FirmwareRelease> = emptyList(),
        val mcuReleases: List<FirmwareRelease> = emptyList(),
        val flashState: FirmwareUpdater.State = FirmwareUpdater.State.Idle,
        val downloadProgress: Float? = null,
        val isVehicleBound: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            activeHolder.activeVehicle.collect { v ->
                _state.update { it.copy(isVehicleBound = v != null) }
            }
        }
        refreshReleases()
    }

    fun refreshReleases() {
        _state.update { it.copy(loading = true, errorText = null) }
        viewModelScope.launch {
            val vcu = repo.releases(FirmwareTarget.VCU).getOrElse {
                _state.update { s -> s.copy(errorText = pretty(it)) }
                emptyList()
            }
            val mcu = repo.releases(FirmwareTarget.MCU).getOrElse { emptyList() }
            _state.update { it.copy(loading = false, vcuReleases = vcu, mcuReleases = mcu) }
        }
    }

    fun flash(release: FirmwareRelease) {
        if (!_state.value.isVehicleBound) {
            _state.update { it.copy(errorText = "Connect a vehicle first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(downloadProgress = 0f, errorText = null) }

            val image = repo.downloadBinary(release) { downloaded, total ->
                _state.update { s ->
                    s.copy(downloadProgress = total?.let { downloaded.toFloat() / it.toFloat() })
                }
            }.getOrElse {
                _state.update { s -> s.copy(downloadProgress = null, errorText = pretty(it)) }
                return@launch
            }

            _state.update { it.copy(downloadProgress = null) }

            activeHolder.flashOnActive(release.target, image)
                .collect { newState ->
                    _state.update { it.copy(flashState = newState) }
                }
        }
    }

    fun changeRegionToUS() {
        val v = activeHolder.activeVehicle.value ?: return
        viewModelScope.launch {
            v.execute(VehicleCommand.ChangeRegion("U"))
                .onFailure { Timber.w(it, "Region change failed") }
        }
    }

    private fun pretty(t: Throwable): String = t.message ?: t::class.simpleName ?: "error"
}
