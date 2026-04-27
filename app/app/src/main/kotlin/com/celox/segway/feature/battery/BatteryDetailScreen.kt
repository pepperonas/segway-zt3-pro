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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                title = { Text("Battery Details") },
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
            Section("State of Charge") {
                LinearProgressIndicator(
                    progress = { state.batteryPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("Battery", "${state.batteryPercent} %")
                InfoRow("State of Health", "${state.batteryHealthPercent} %")
                InfoRow("Cycle Count", "${state.batteryCycleCount}")
                if (state.chargeThresholdPercent in 80..100) {
                    InfoRow("Max Charge Limit", "${state.chargeThresholdPercent} %")
                }
            }

            Section("Live Power") {
                InfoRow("Voltage", "%.2f V".format(state.batteryVoltage))
                InfoRow("Current", "%+.2f A".format(state.batteryCurrentA))
                // Derived: P = V × I. Sign matches current (negative = discharge).
                val power = state.batteryVoltage * state.batteryCurrentA
                InfoRow("Power", "%+.1f W".format(power))
                InfoRow(
                    "Mode",
                    when {
                        state.chargingState == 1 -> "Charging"
                        state.chargingState == 2 -> "Fully charged"
                        state.batteryCurrentA < -0.1f -> "Discharging"
                        else -> "Idle"
                    }
                )
            }

            Section("Range Estimation") {
                InfoRow("Remaining (live)", "%.1f km".format(state.rangeRemainingKm))
                // Derived: at full SOC, project current range linearly.
                val pct = state.batteryPercent
                val rangeAtFull = if (pct in 1..100) state.rangeRemainingKm * 100f / pct else 0f
                if (rangeAtFull > 0f) {
                    InfoRow("Range @ full battery (extrapolated)", "%.1f km".format(rangeAtFull))
                }
            }

            Section("Pack Temperature") {
                InfoRow("Battery Temp", "%.1f °C".format(state.batteryTempC))
            }

            // Cell voltages: list every cell + min/max diff.
            if (state.cellVoltagesMv.isNotEmpty()) {
                Section("Cells (${state.cellVoltagesMv.size}S)") {
                    val mn = state.cellVoltagesMv.min()
                    val mx = state.cellVoltagesMv.max()
                    InfoRow("Min", "%.3f V".format(mn / 1000f))
                    InfoRow("Max", "%.3f V".format(mx / 1000f))
                    InfoRow("Diff", "%d mV".format(abs(mx - mn)))
                    Spacer(Modifier.height(4.dp))
                    state.cellVoltagesMv.forEachIndexed { i, mv ->
                        InfoRow("Cell %02d".format(i + 1), "%.3f V".format(mv / 1000f))
                    }
                }
            }

            Section("Firmware") {
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
