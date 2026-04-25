package com.celox.segway.feature.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.data.PairingPrefs
import com.celox.segway.core.data.VehicleDao
import com.celox.segway.core.data.VehicleEntity
import com.celox.segway.feature.home.ActiveVehicleHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GarageViewModel @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val pairingPrefs: PairingPrefs,
    private val activeHolder: ActiveVehicleHolder,
) : ViewModel() {

    data class GarageEntry(
        val mac: String,
        val displayName: String,
        val model: String,
        val regionCode: String?,
        val isActive: Boolean,
    )

    val entries: StateFlow<List<GarageEntry>> = combine(
        vehicleDao.all(),
        activeHolder.activeVehicle
    ) { vehicles, active ->
        vehicles.map { v ->
            GarageEntry(
                mac = v.mac,
                displayName = v.displayName,
                model = v.model,
                regionCode = v.regionCode,
                isActive = active?.id == v.mac,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun activate(entry: GarageEntry) {
        if (entry.isActive) return
        activeHolder.bind(entry.mac, entry.displayName)
    }

    fun rename(mac: String, newName: String) = viewModelScope.launch {
        vehicleDao.get(mac)?.let { v ->
            vehicleDao.upsert(v.copy(displayName = newName))
        }
    }

    fun unpair(mac: String) = viewModelScope.launch {
        if (activeHolder.activeVehicle.value?.id == mac) activeHolder.unbind()
        vehicleDao.delete(mac)
        pairingPrefs.remove(mac)
    }
}
