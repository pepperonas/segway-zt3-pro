package com.celox.segway.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBatteryDetailClick: () -> Unit = {},
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Parent SegwayApp Scaffold already excludes the bottom nav inset
        // via its own padding(padding) on NavHost. Without this override the
        // inner Scaffold would ADD the system-nav inset a second time,
        // creating a black gap above the bottom bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
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
            ConnectionBanner(
                isConnected = state.isConnected,
                isReady = state.isReady,
                onRetry = { viewModel.reconnect() },
            )

            LockStatusBanner(
                isUnlocked = isUnlockActive,
                autoRevertAt = autoRevertAt,
                bootKmh = profiles.boot.speedKmh,
                unlockKmh = profiles.unlock.speedKmh,
                onLockIconTap = {
                    // Mirror the big Lock/Unlock button logic so the icon is
                    // a one-tap shortcut: re-lock instantly if currently
                    // unlocked; otherwise unlock (with PIN dialog if required).
                    if (isUnlockActive) {
                        viewModel.reLock()
                    } else if (!viewModel.unlockRequiresPin()) {
                        coroutineScope.launch { viewModel.confirmUnlock(null) }
                    } else {
                        unlockShowError = false
                        unlockDialogVisible = true
                    }
                },
            )

            if (profiles.accessibilityTriggerEnabled) {
                AccessibilityServiceBanner()
            }

            Speedometer(speedKmh = state.speedKmh, maxSpeedKmh = profiles.unlock.speedKmh.toFloat())

            Spacer(Modifier.height(24.dp))

            // Mode-Switch — registers verified per ZT3 BLE register reference doc.
            SectionLabel(stringResource(R.string.vehicle_section_mode))
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RideMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(idx, RideMode.entries.size)
                    ) {
                        Text(when (mode) {
                            RideMode.Walk -> stringResource(R.string.vehicle_mode_walk)
                            RideMode.Eco -> stringResource(R.string.vehicle_mode_eco)
                            RideMode.Drive -> stringResource(R.string.vehicle_mode_drive)
                            RideMode.Sport -> stringResource(R.string.vehicle_mode_sport)
                        })
                    }
                }
            }
            if (state.mode == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.vehicle_reading_mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Lights toggle — VCU_LedMode at reg 0x5B, doc-form encoding.
            FilterChip(
                selected = state.isLightsOn,
                onClick = { viewModel.toggleLights() },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (state.isLightsOn) stringResource(R.string.vehicle_lights_on)
                        else stringResource(R.string.vehicle_lights_off)
                    )
                },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Lightbulb, null) },
            )

            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.vehicle_section_quick_profiles))
            Spacer(Modifier.height(8.dp))

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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.vehicle_lock_button, profiles.boot.speedKmh),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.RocketLaunch, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.vehicle_unlock_button, profiles.unlock.speedKmh),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.vehicle_section_live))
            Spacer(Modifier.height(8.dp))

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
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    icon = Icons.Outlined.RocketLaunch,
                    label = stringResource(R.string.vehicle_range),
                    value = if (state.rangeRemainingKm > 0f) "%.1f km".format(state.rangeRemainingKm) else "—",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Outlined.Speed,
                    label = stringResource(R.string.vehicle_total),
                    value = "%.0f km".format(state.odometerKm),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Battery deep telemetry from BMS — tap to open full detail screen
            BatteryDetailsCard(state, onClick = onBatteryDetailClick)

            Spacer(Modifier.height(16.dp))

            // Motor + ride telemetry
            DiagnosticsTelemetryCard(state)

            Spacer(Modifier.height(16.dp))

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
    onLockIconTap: () -> Unit = {},
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

    val activeKmh = if (isUnlocked) unlockKmh else bootKmh
    val gradientColors = if (isUnlocked) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainer,
        )
    }
    val onGradient = if (isUnlocked) MaterialTheme.colorScheme.onPrimary
                     else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .background(brush = Brush.linearGradient(gradientColors))
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(color = onGradient.copy(alpha = 0.15f), shape = CircleShape)
                            .clickable(onClick = onLockIconTap),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            if (isUnlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                            contentDescription = if (isUnlocked) stringResource(R.string.vehicle_lock)
                                                 else stringResource(R.string.vehicle_unlock),
                            tint = onGradient,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isUnlocked) stringResource(R.string.vehicle_lock_state_unlocked)
                            else stringResource(R.string.vehicle_lock_state_locked),
                            color = onGradient,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            if (isUnlocked) stringResource(R.string.vehicle_lock_subtitle_unlocked)
                            else stringResource(R.string.vehicle_lock_subtitle_locked),
                            color = onGradient.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (countdownText != null) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = onGradient.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.vehicle_lock_countdown, countdownText),
                                color = onGradient,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$activeKmh",
                        color = onGradient,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Text(
                        "km/h",
                        color = onGradient.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 14.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        isUnlocked && countdownText != null ->
                            stringResource(R.string.vehicle_lock_hint_auto_revert, countdownText)
                        isUnlocked ->
                            stringResource(R.string.vehicle_lock_hint_unlocked)
                        else ->
                            stringResource(R.string.vehicle_lock_hint_locked, unlockKmh)
                    },
                    color = onGradient.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    )
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
                    stringResource(R.string.vehicle_a11y_inactive_title),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.vehicle_a11y_inactive_body),
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.TextButton(onClick = { AccessibilityHelper.openSettings(ctx) }) {
                Text(
                    stringResource(R.string.vehicle_a11y_enable),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
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
                label = {
                    Text(stringResource(R.string.vehicle_quick_profile_chip, profile.label, profile.speedKmh))
                },
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


/**
 * Offline banner — appears only after the BLE link has been down for >4 s.
 * Mirrors SHU's pattern: silent reconnect during the brief handshake, banner
 * only when something is actually wrong. The handshake phase (isConnected=true,
 * isReady=false) is intentionally invisible — it's normally <1 s and the
 * cached telemetry stays on screen.
 */
@Composable
private fun ConnectionBanner(
    isConnected: Boolean,
    isReady: Boolean,
    onRetry: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isConnected) {
        if (isConnected) {
            visible = false
        } else {
            kotlinx.coroutines.delay(4_000L)
            visible = true
        }
    }
    if (!visible) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.vehicle_offline_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.vehicle_offline_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.vehicle_retry))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
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

@Composable
private fun BatteryDetailsCard(
    state: com.celox.segway.core.vehicle.VehicleState,
    onClick: () -> Unit = {},
) {
    val cells = state.cellVoltagesMv
    val bmsKnown = cells.isNotEmpty() || state.batteryVoltage > 0f
    val cellSpread = if (cells.size >= 2) (cells.max() - cells.min()) else 0
    val chargingLabel = when (state.chargingState) {
        0 -> stringResource(R.string.vehicle_battery_charge_idle)
        1 -> stringResource(R.string.vehicle_battery_charge_charging)
        2 -> stringResource(R.string.vehicle_battery_charge_full)
        else -> stringResource(R.string.vehicle_battery_charge_status_other, state.chargingState)
    }
    val dash = stringResource(R.string.value_unknown_dash)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.vehicle_battery_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text("→", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            InfoRow(
                stringResource(R.string.vehicle_battery_voltage),
                if (state.batteryVoltage > 0f) "%.2f V".format(state.batteryVoltage) else dash,
            )
            InfoRow(
                stringResource(R.string.vehicle_battery_current),
                if (bmsKnown) "%+.2f A".format(state.batteryCurrentA) else dash,
            )
            if (bmsKnown) {
                InfoRow(
                    stringResource(R.string.vehicle_battery_power),
                    "%+.0f W".format(state.batteryVoltage * state.batteryCurrentA),
                )
            }
            InfoRow(
                stringResource(R.string.vehicle_battery_temp),
                if (state.batteryTempC != 0f) "%.1f °C".format(state.batteryTempC) else dash,
            )
            InfoRow(
                stringResource(R.string.vehicle_battery_charging_state),
                if (bmsKnown) chargingLabel else dash,
            )
            InfoRow(
                stringResource(R.string.vehicle_battery_health),
                if (bmsKnown) "${state.batteryHealthPercent} %" else dash,
            )
            InfoRow(
                stringResource(R.string.vehicle_battery_cycles),
                if (bmsKnown) state.batteryCycleCount.toString() else dash,
            )
            if (cells.isNotEmpty()) {
                InfoRow(
                    stringResource(R.string.vehicle_battery_cells),
                    stringResource(R.string.vehicle_battery_cells_template, cells.size, cells.average().toInt()),
                )
                InfoRow(
                    stringResource(R.string.vehicle_battery_spread),
                    stringResource(R.string.vehicle_battery_spread_template, cellSpread),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsTelemetryCard(state: com.celox.segway.core.vehicle.VehicleState) {
    val dash = stringResource(R.string.value_unknown_dash)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.vehicle_motor_card_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            InfoRow(
                stringResource(R.string.vehicle_motor_temp_a),
                if (state.motorTempAC != 0f) "%.1f °C".format(state.motorTempAC) else dash
            )
            InfoRow(
                stringResource(R.string.vehicle_motor_temp_b),
                if (state.motorTempBC != 0f) "%.1f °C".format(state.motorTempBC) else dash
            )
            InfoRow(
                stringResource(R.string.vehicle_motor_temp_peak),
                if (state.motorTempMaxC != 0f) "%.1f °C".format(state.motorTempMaxC) else dash
            )
            InfoRow(
                stringResource(R.string.vehicle_mcu_temp),
                if (state.mcuTempC != 0f) "%.1f °C".format(state.mcuTempC) else dash
            )
            InfoRow(stringResource(R.string.vehicle_trip_time), formatDuration(state.tripDurationSeconds))
            InfoRow(stringResource(R.string.vehicle_total_runtime), formatDuration(state.totalRuntimeSeconds))
            if (state.errorCode != 0)
                InfoRow(stringResource(R.string.vehicle_error_code), "0x%04X".format(state.errorCode))
            if (state.warnCode != 0)
                InfoRow(stringResource(R.string.vehicle_warn_code), "0x%04X".format(state.warnCode))
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d h %02d m".format(h, m) else "%d m %02d s".format(m, s)
}
