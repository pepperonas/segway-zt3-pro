package com.celox.segway.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.celox.segway.feature.profiles.AccessibilityHelper
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
            LockStatusBanner(
                isUnlocked = isUnlockActive,
                autoRevertAt = autoRevertAt,
                bootKmh = profiles.boot.speedKmh,
                unlockKmh = profiles.unlock.speedKmh,
            )

            if (profiles.accessibilityTriggerEnabled) {
                AccessibilityServiceBanner()
            }

            Speedometer(speedKmh = state.speedKmh, maxSpeedKmh = profiles.unlock.speedKmh.toFloat())

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

            // Big toggle: Unlock if locked, Re-lock if unlocked
            if (isUnlockActive) {
                Button(
                    onClick = viewModel::reLock,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Lock to ${profiles.boot.speedKmh} km/h")
                }
            } else {
                Button(
                    onClick = {
                        if (!viewModel.unlockRequiresPin()) {
                            coroutineScope.launch { viewModel.confirmUnlock(null) }
                        } else {
                            unlockShowError = false
                            unlockDialogVisible = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.RocketLaunch, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock ${profiles.unlock.speedKmh} km/h")
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
private fun LockStatusBanner(
    isUnlocked: Boolean,
    autoRevertAt: Long?,
    bootKmh: Int,
    unlockKmh: Int,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isUnlocked, autoRevertAt) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
        }
    }
    val countdownText: String? = if (isUnlocked) autoRevertAt?.let { target ->
        val remaining = ((target - now) / 1000L).coerceAtLeast(0)
        "%02d:%02d".format(remaining / 60, remaining % 60)
    } else null

    val container = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                if (isUnlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                null,
                tint = onContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isUnlocked) "Unlocked – max $unlockKmh km/h" else "Locked – max $bootKmh km/h",
                    color = onContainer,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    when {
                        isUnlocked && countdownText != null -> "Auto-revert in $countdownText"
                        isUnlocked -> "Stays unlocked until you re-lock or disconnect"
                        else -> "Tap below to unlock $unlockKmh km/h"
                    },
                    color = onContainer.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AccessibilityServiceBanner() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(AccessibilityHelper.isOurServiceEnabled(ctx)) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                enabled = AccessibilityHelper.isOurServiceEnabled(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    if (enabled) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.LockOpen, null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Stealth-Unlock inaktiv",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Vol-Down-3× funktioniert erst, wenn der Accessibility-Service in Android-Einstellungen erlaubt ist.",
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.TextButton(onClick = { AccessibilityHelper.openSettings(ctx) }) {
                Text("Aktivieren", color = MaterialTheme.colorScheme.onErrorContainer)
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
