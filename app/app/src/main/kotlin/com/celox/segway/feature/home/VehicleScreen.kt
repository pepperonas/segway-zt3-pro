package com.celox.segway.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.core.vehicle.RideMode
import com.celox.segway.ui.components.Speedometer
import com.celox.segway.ui.components.StatTile

@Composable
fun VehicleScreen(
    onPairClick: () -> Unit,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(vehicle?.displayName ?: stringResource(R.string.app_name)) })
        }
    ) { padding ->
        if (vehicle == null) {
            EmptyState(onPairClick, modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Speedometer(speedKmh = state.speedKmh, maxSpeedKmh = 40f)

            Spacer(Modifier.height(24.dp))

            // Mode chooser
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RideMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(idx, RideMode.entries.size)
                    ) {
                        Text(when (mode) {
                            RideMode.Eco -> stringResource(R.string.vehicle_mode_eco)
                            RideMode.Drive -> stringResource(R.string.vehicle_mode_drive)
                            RideMode.Sport -> stringResource(R.string.vehicle_mode_sport)
                        })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stat grid (2x2)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    icon = Icons.Outlined.Battery5Bar,
                    label = stringResource(R.string.vehicle_battery),
                    value = "${state.batteryPercent} %",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Outlined.Speed,
                    label = stringResource(R.string.track_max_speed),
                    value = "%.1f km/h".format(state.speedKmh),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    icon = Icons.Outlined.Thermostat,
                    label = stringResource(R.string.vehicle_temperature),
                    value = "%.1f °C".format(state.temperatureC),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Outlined.Bolt,
                    label = stringResource(R.string.vehicle_trip),
                    value = "%.2f km".format(state.tripKm),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Quick actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.isLocked,
                    onClick = { viewModel.toggleLock() },
                    label = { Text(if (state.isLocked) stringResource(R.string.vehicle_unlock) else stringResource(R.string.vehicle_lock)) },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Lock, null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.isLightsOn,
                    onClick = { viewModel.toggleLights() },
                    label = { Text(stringResource(R.string.vehicle_lights)) },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Lightbulb, null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.isCruiseOn,
                    onClick = { viewModel.toggleCruise() },
                    label = { Text(stringResource(R.string.vehicle_cruise)) },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Bookmarks, null) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Firmware info
            FirmwareInfoCard(state)

            Spacer(Modifier.height(16.dp))

            // Disconnect
            Button(
                onClick = viewModel::disconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.vehicle_disconnect))
            }
        }
    }
}

@Composable
private fun EmptyState(onPairClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.vehicle_no_paired),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPairClick) { Text(stringResource(R.string.vehicle_pair_now)) }
    }
}

@Composable
private fun FirmwareInfoCard(state: com.celox.segway.core.vehicle.VehicleState) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(stringResource(R.string.vehicle_serial), state.serialNumber.ifBlank { "—" })
            InfoRow(stringResource(R.string.vehicle_region), state.regionCode.ifBlank { "—" })
            InfoRow(stringResource(R.string.vehicle_firmware_vcu), state.firmwareVcu.ifBlank { "—" })
            InfoRow(stringResource(R.string.vehicle_firmware_mcu), state.firmwareMcu.ifBlank { "—" })
            InfoRow(stringResource(R.string.vehicle_firmware_ble), state.firmwareBle.ifBlank { "—" })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
