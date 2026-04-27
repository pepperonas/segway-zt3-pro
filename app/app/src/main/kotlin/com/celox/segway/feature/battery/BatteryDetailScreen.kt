package com.celox.segway.feature.battery

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.feature.home.VehicleViewModel
import kotlin.math.abs

@Composable
fun BatteryDetailScreen(
    onBack: () -> Unit,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // SoC bar.
            Section(stringResource(R.string.battery_section_soc)) {
                LinearProgressIndicator(
                    progress = { state.batteryPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.battery_label_battery), "${state.batteryPercent} %")
                InfoRow(stringResource(R.string.battery_label_health), "${state.batteryHealthPercent} %")
                InfoRow(stringResource(R.string.battery_label_cycles), "${state.batteryCycleCount}")
                if (state.chargeThresholdPercent in 80..100) {
                    InfoRow(
                        stringResource(R.string.battery_label_max_charge_limit),
                        "${state.chargeThresholdPercent} %",
                    )
                }
            }

            Section(stringResource(R.string.battery_section_live_power)) {
                InfoRow(stringResource(R.string.battery_label_voltage), "%.2f V".format(state.batteryVoltage))
                InfoRow(stringResource(R.string.battery_label_current), "%+.2f A".format(state.batteryCurrentA))
                // Derived: P = V × I. Sign matches current (negative = discharge).
                val power = state.batteryVoltage * state.batteryCurrentA
                InfoRow(stringResource(R.string.battery_label_power), "%+.1f W".format(power))
                InfoRow(
                    stringResource(R.string.battery_label_mode),
                    when {
                        state.chargingState == 1 -> stringResource(R.string.battery_mode_charging)
                        state.chargingState == 2 -> stringResource(R.string.battery_mode_full)
                        state.batteryCurrentA < -0.1f -> stringResource(R.string.battery_mode_discharging)
                        else -> stringResource(R.string.battery_mode_idle)
                    }
                )
            }

            Section(stringResource(R.string.battery_section_range)) {
                InfoRow(stringResource(R.string.battery_label_remaining), "%.1f km".format(state.rangeRemainingKm))
                // Derived: at full SOC, project current range linearly.
                val pct = state.batteryPercent
                val rangeAtFull = if (pct in 1..100) state.rangeRemainingKm * 100f / pct else 0f
                if (rangeAtFull > 0f) {
                    InfoRow(
                        stringResource(R.string.battery_label_range_full),
                        "%.1f km".format(rangeAtFull),
                    )
                }
            }

            Section(stringResource(R.string.battery_section_temperature)) {
                InfoRow(stringResource(R.string.battery_label_temp), "%.1f °C".format(state.batteryTempC))
            }

            // Cell voltages: list every cell + min/max diff.
            if (state.cellVoltagesMv.isNotEmpty()) {
                Section(stringResource(R.string.battery_section_cells, state.cellVoltagesMv.size)) {
                    val mn = state.cellVoltagesMv.min()
                    val mx = state.cellVoltagesMv.max()
                    InfoRow(stringResource(R.string.battery_label_min), "%.3f V".format(mn / 1000f))
                    InfoRow(stringResource(R.string.battery_label_max), "%.3f V".format(mx / 1000f))
                    InfoRow(stringResource(R.string.battery_label_diff), "%d mV".format(abs(mx - mn)))
                    Spacer(Modifier.height(4.dp))
                    state.cellVoltagesMv.forEachIndexed { i, mv ->
                        InfoRow(
                            stringResource(R.string.battery_cell_label, i + 1),
                            "%.3f V".format(mv / 1000f),
                        )
                    }
                }
            }

            Section(stringResource(R.string.battery_section_firmware)) {
                if (state.firmwareBms.isNotBlank()) InfoRow("BMS", state.firmwareBms)
                if (state.firmwareVcu.isNotBlank()) InfoRow("VCU", state.firmwareVcu)
                if (state.firmwareMcu.isNotBlank()) InfoRow("MCU", state.firmwareMcu)
                if (state.firmwareBle.isNotBlank()) InfoRow("BLE", state.firmwareBle)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
