package com.celox.segway.feature.scooter_settings

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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.core.vehicle.VcuBitfield
import com.celox.segway.core.vehicle.VehicleState

/**
 * Scooter-side settings screen. Exposes the writable VCU bitfield toggles
 * and numeric sliders sourced from SHU's `zt3.json` register map.
 *
 * Reads are kept fresh by the periodic poll in [com.celox.segway.core.vehicle.Zt3ProVehicle];
 * writes flow through [ScooterSettingsViewModel] → vehicle.execute.
 */
@Composable
fun ScooterSettingsScreen(
    onBack: () -> Unit,
    vm: ScooterSettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val isReady = state.isReady && vehicle != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scooter_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isReady) {
                Text(
                    stringResource(R.string.scooter_settings_offline_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // === GENERAL TOGGLES (VCU 0x1D + 0x1E) ===
            Section(stringResource(R.string.scooter_settings_section_general)) {
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_traction_control),
                    raw = state.vcuBoolRaw, bit = VcuBitfield.TRACTION_CONTROL,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1D, VcuBitfield.TRACTION_CONTROL, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_imperial_units),
                    raw = state.vcuBoolRaw, bit = VcuBitfield.IMPERIAL_UNITS,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1D, VcuBitfield.IMPERIAL_UNITS, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_park_on_slope),
                    raw = state.vcuBoolRaw, bit = VcuBitfield.RAMP_PARKING,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1D, VcuBitfield.RAMP_PARKING, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_boost_mode),
                    raw = state.vcuBoolRaw, bit = VcuBitfield.BOOST_FUNCTION,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1D, VcuBitfield.BOOST_FUNCTION, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_indicator_sound),
                    raw = state.vcuBoolRaw, bit = VcuBitfield.TURN_SIGNAL_SOUNDS,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1D, VcuBitfield.TURN_SIGNAL_SOUNDS, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_app_function_tone),
                    raw = state.vcuBool2Raw, bit = VcuBitfield.APP_FUNCTION_TONE,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1E, VcuBitfield.APP_FUNCTION_TONE, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_alarm),
                    raw = state.vcuBoolRaw, bit = VcuBitfield.ALARM,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1D, VcuBitfield.ALARM, v) }
                )
            }

            // === MODE ENABLES (VCU 0x1D, 0x1E) ===
            Section(stringResource(R.string.scooter_settings_section_modes)) {
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_walk_mode),
                    raw = state.vcuBoolRaw, bit = VcuBitfield.ENABLE_WALK,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1D, VcuBitfield.ENABLE_WALK, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_drive_mode),
                    raw = state.vcuBool2Raw, bit = VcuBitfield.ENABLE_DRIVE,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1E, VcuBitfield.ENABLE_DRIVE, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_sports_mode),
                    raw = state.vcuBool2Raw, bit = VcuBitfield.ENABLE_SPORTS,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1E, VcuBitfield.ENABLE_SPORTS, v) }
                )
            }

            // === LIGHTS (VCU 0x1F) ===
            Section(stringResource(R.string.scooter_settings_section_lights)) {
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_auto_headlight),
                    raw = state.vcuBool3Raw, bit = VcuBitfield.AUTO_HEADLIGHT,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1F, VcuBitfield.AUTO_HEADLIGHT, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_front_position_lamp),
                    raw = state.vcuBool3Raw, bit = VcuBitfield.FRONT_POSITION_LAMP,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1F, VcuBitfield.FRONT_POSITION_LAMP, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_underglow_lights),
                    raw = state.vcuBool3Raw, bit = VcuBitfield.UNDERGLOW_LIGHTS,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1F, VcuBitfield.UNDERGLOW_LIGHTS, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_breathing_taillight),
                    raw = state.vcuBool3Raw, bit = VcuBitfield.CHARGING_BREATHING_LIGHT,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1F, VcuBitfield.CHARGING_BREATHING_LIGHT, v) }
                )
                EnumDropdown(
                    label = stringResource(R.string.scooter_settings_taillight_mode),
                    options = listOf(
                        0 to stringResource(R.string.scooter_settings_taillight_brighter),
                        1 to stringResource(R.string.scooter_settings_taillight_blinking),
                    ),
                    selected = state.tailLightMode,
                    enabled = isReady,
                    onChange = { v -> vm.writeVcuU16(0x5D, v) }
                )
            }

            // === FOLDING (VCU 0x1F) ===
            Section(stringResource(R.string.scooter_settings_section_folding)) {
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_power_off_folding),
                    raw = state.vcuBool3Raw, bit = VcuBitfield.POWER_OFF_FOLDING,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1F, VcuBitfield.POWER_OFF_FOLDING, v) }
                )
                BitfieldSwitch(
                    label = stringResource(R.string.scooter_settings_disable_alarm_folding),
                    raw = state.vcuBool3Raw, bit = VcuBitfield.FOLDING_DISABLE_ALARM,
                    enabled = isReady,
                    onToggle = { v -> vm.setBitfieldBit(0x1F, VcuBitfield.FOLDING_DISABLE_ALARM, v) }
                )
            }

            // === RIDING (VCU 0x42, 0x49, 0x6E, 0x70) ===
            Section(stringResource(R.string.scooter_settings_section_riding)) {
                IntSlider(
                    label = stringResource(R.string.scooter_settings_start_speed),
                    value = state.startSpeedKmh, range = 0..5,
                    unit = stringResource(R.string.unit_kmh),
                    enabled = isReady,
                    onCommit = { v -> vm.writeVcuU16(0x42, v) }
                )
                IntSlider(
                    label = stringResource(R.string.scooter_settings_auto_shutdown),
                    value = state.autoOffMinutes, range = 0..60,
                    unit = stringResource(R.string.unit_min),
                    enabled = isReady,
                    onCommit = { v -> vm.writeVcuU16(0x49, v) }
                )
                EnumDropdown(
                    label = stringResource(R.string.scooter_settings_acceleration),
                    options = listOf(
                        1 to stringResource(R.string.scooter_settings_acceleration_eco),
                        2 to stringResource(R.string.scooter_settings_acceleration_standard),
                        3 to stringResource(R.string.scooter_settings_acceleration_max),
                    ),
                    selected = state.accelerationLevel,
                    enabled = isReady,
                    onChange = { v -> vm.writeVcuU16(0x6E, v) }
                )
                EnumDropdown(
                    label = stringResource(R.string.scooter_settings_kers),
                    options = listOf(
                        0 to stringResource(R.string.scooter_settings_kers_off),
                        1 to stringResource(R.string.scooter_settings_kers_weak),
                        2 to stringResource(R.string.scooter_settings_kers_standard),
                    ),
                    selected = state.kersLevel,
                    enabled = isReady,
                    onChange = { v -> vm.writeVcuU16(0x70, v) }
                )
                Text(
                    stringResource(R.string.scooter_settings_kers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // === BATTERY (BMS 0x82) ===
            Section(stringResource(R.string.scooter_settings_section_battery)) {
                IntSlider(
                    label = stringResource(R.string.scooter_settings_max_charge_limit),
                    value = state.chargeThresholdPercent.coerceAtLeast(80),
                    range = 80..100,
                    unit = stringResource(R.string.unit_percent),
                    enabled = isReady,
                    onCommit = { v -> vm.writeBmsU16(0x82.toByte(), v) }
                )
                Text(
                    stringResource(R.string.scooter_settings_charge_limit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // === CUSTOM BUTTON (VCU 0x4A) ===
            Section(stringResource(R.string.scooter_settings_section_custom_button)) {
                Text(
                    stringResource(R.string.scooter_settings_custom_button_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumDropdown(
                    label = stringResource(R.string.scooter_settings_custom_button_action),
                    // Values from SHU bootstrap zt3.json. Value 4 is a gap
                    // (not present in the option_map) — firmware-reserved.
                    options = listOf(
                        0 to stringResource(R.string.scooter_settings_custom_button_off),
                        1 to stringResource(R.string.scooter_settings_custom_button_park_toggle),
                        2 to stringResource(R.string.scooter_settings_custom_button_hill_hold_toggle),
                        3 to stringResource(R.string.scooter_settings_custom_button_walk_toggle),
                        5 to stringResource(R.string.scooter_settings_custom_button_kers_cycle),
                        6 to stringResource(R.string.scooter_settings_custom_button_hazards),
                    ),
                    selected = state.customKeyMode,
                    enabled = isReady,
                    onChange = { v -> vm.writeVcuU16(0x4A, v) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.scooter_settings_custom_button_doubletap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BitfieldSwitch(
    label: String,
    raw: Int,
    bit: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val checked = ((raw ushr bit) and 1) == 1
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

@Composable
private fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    enabled: Boolean,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${local.toInt()} $unit".trim(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled,
        )
    }
}

/**
 * Dropdown-style enum picker that maps **explicit register values** (not
 * sequential indices) to display labels. Necessary for register fields like
 * `custom_key` whose option_map has gaps (values 1, 2, 3, 5, 6 — value 4
 * is firmware-reserved) and for fields like `acc_level` where 0 is invalid
 * (values are 1, 2, 3).
 *
 * The display shows the friendly label whenever [selected] matches an
 * entry, falling back to "Unbekannt (n)" if the scooter ever reports a
 * value we don't have a label for (e.g. SHFW custom firmware adding new
 * options).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val unknownLabel = stringResource(R.string.scooter_settings_unknown_value, selected)
    val currentLabel = options.firstOrNull { it.first == selected }?.second
        ?: unknownLabel
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded },
        ) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, enabled)
                    .fillMaxWidth(),
                enabled = enabled,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onChange(value)
                            expanded = false
                        },
                        trailingIcon = if (value == selected) {
                            { Text("✓", color = MaterialTheme.colorScheme.primary) }
                        } else null,
                    )
                }
            }
        }
    }
}
