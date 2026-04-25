package com.celox.segway.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.core.profile.SpeedProfile
import com.celox.segway.core.vehicle.RideMode
import com.celox.segway.feature.profiles.UnlockDialog
import com.celox.segway.ui.components.Speedometer
import com.celox.segway.ui.components.StatTile
import kotlinx.coroutines.launch

@Composable
fun VehicleScreen(
    onPairClick: () -> Unit,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val autoRevertAt by viewModel.autoRevertAt.collectAsStateWithLifecycle()
    val isUnlockActive by viewModel.isUnlockActive.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var unlockDialogVisible by remember { mutableStateOf(false) }
    var unlockShowError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(vehicle?.displayName ?: stringResource(R.string.app_name)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            UnlockBanner(
                isActive = isUnlockActive,
                autoRevertAt = autoRevertAt,
                speedKmh = profiles.unlock.speedKmh,
            )

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

            // Quick speed-profile actions
            QuickProfilesRow(
                quickActions = profiles.quickActions,
                activeProfileId = activeProfileId,
                onApply = { viewModel.applyProfile(it) }
            )

            Spacer(Modifier.height(8.dp))

            // Unlock 40 button (PIN-protected)
            UnlockButton(
                speedKmh = profiles.unlock.speedKmh,
                isActive = activeProfileId == profiles.unlock.id,
                onTap = {
                    if (!viewModel.unlockRequiresPin()) {
                        coroutineScope.launch { viewModel.confirmUnlock(null) }
                    } else {
                        unlockShowError = false
                        unlockDialogVisible = true
                    }
                }
            )

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

    if (unlockDialogVisible) {
        UnlockDialog(
            targetSpeedKmh = profiles.unlock.speedKmh,
            pinRequired = viewModel.unlockRequiresPin(),
            showError = unlockShowError,
            onDismiss = {
                unlockDialogVisible = false
                unlockShowError = false
            },
            onConfirm = { pin ->
                coroutineScope.launch {
                    val ok = viewModel.confirmUnlock(pin)
                    if (ok) {
                        unlockDialogVisible = false
                        unlockShowError = false
                    } else {
                        unlockShowError = true
                    }
                }
            }
        )
    }
}

@Composable
private fun UnlockBanner(
    isActive: Boolean,
    autoRevertAt: Long?,
    speedKmh: Int,
) {
    if (!isActive) return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(autoRevertAt) {
        // tick every second while banner is visible
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
        }
    }
    val countdownText: String? = autoRevertAt?.let { target ->
        val remaining = ((target - now) / 1000L).coerceAtLeast(0)
        val mm = remaining / 60
        val ss = remaining % 60
        "%02d:%02d".format(mm, ss)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.LockOpen,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Unlocked – $speedKmh km/h",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall
                )
                if (countdownText != null) {
                    Text(
                        "Auto-revert in $countdownText",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickProfilesRow(
    quickActions: List<SpeedProfile>,
    activeProfileId: String?,
    onApply: (SpeedProfile) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        quickActions.forEach { profile ->
            val isActive = activeProfileId == profile.id
            FilterChip(
                selected = isActive,
                onClick = { onApply(profile) },
                label = { Text("${profile.label}\n${profile.speedKmh} km/h") },
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        when (profile.speedKmh) {
                            in 0..10 -> Icons.Outlined.DirectionsRun
                            in 11..25 -> Icons.Outlined.Speed
                            else -> Icons.Outlined.Bolt
                        },
                        null
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UnlockButton(
    speedKmh: Int,
    isActive: Boolean,
    onTap: () -> Unit,
) {
    Button(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ) else ButtonDefaults.outlinedButtonColors()
    ) {
        androidx.compose.material3.Icon(
            if (isActive) Icons.Outlined.LockOpen else Icons.Outlined.RocketLaunch,
            null
        )
        Spacer(Modifier.width(8.dp))
        Text(if (isActive) "Unlocked: $speedKmh km/h" else "Unlock $speedKmh km/h")
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
