package com.celox.segway.feature.firmware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.core.ota.FirmwareUpdater
import com.celox.segway.core.repo.FirmwareRelease
import com.celox.segway.core.repo.FirmwareTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareScreen(
    onBack: () -> Unit,
    vm: FirmwareViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var pendingRelease by remember { mutableStateOf<FirmwareRelease?>(null) }
    var confirmRegionChange by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Firmware") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = vm::refreshReleases) {
                        Icon(Icons.Outlined.Refresh, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            ui.errorText?.let { msg ->
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "Tip: enable VPN outside the EU, then refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (!ui.isVehicleBound) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )) {
                    Text(
                        "No vehicle connected. Pair first to enable flashing.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Active flash progress (most prominent)
            FlashStatusCard(ui)

            Spacer(Modifier.height(16.dp))

            // Region change action
            OutlinedButton(
                onClick = { confirmRegionChange = true },
                enabled = ui.isVehicleBound,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Public, null); Spacer(Modifier.size(8.dp))
                Text("Change region → US (40 km/h)")
            }

            Spacer(Modifier.height(16.dp))

            if (ui.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Loading releases…")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ui.vcuReleases.isNotEmpty()) {
                    item { SectionHeader(Icons.Outlined.Build, "VCU firmware") }
                    items(ui.vcuReleases, key = { it.id }) { rel ->
                        ReleaseRow(rel) { pendingRelease = rel }
                    }
                }
                if (ui.mcuReleases.isNotEmpty()) {
                    item { SectionHeader(Icons.Outlined.Memory, "MCU firmware") }
                    items(ui.mcuReleases, key = { it.id }) { rel ->
                        ReleaseRow(rel) { pendingRelease = rel }
                    }
                }
            }
        }
    }

    pendingRelease?.let { rel ->
        AlertDialog(
            onDismissRequest = { pendingRelease = null },
            title = { Text("Flash ${rel.target.name} ${rel.version}?") },
            text = {
                Column {
                    Text(rel.releaseNotes ?: "No release notes provided.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Do not power off the scooter during flashing. Battery should be > 50%.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.flash(rel); pendingRelease = null }) { Text("Flash") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRelease = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmRegionChange) {
        AlertDialog(
            onDismissRequest = { confirmRegionChange = false },
            title = { Text("Change region to US?") },
            text = {
                Text(
                    "This will rewrite the scooter's serial number to start with 'U' (40 km/h limit). " +
                            "This is irreversible without an ST-Link memory restore. Make sure VCU/MCU are flashed first.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.changeRegionToUS(); confirmRegionChange = false }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegionChange = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FlashStatusCard(ui: FirmwareViewModel.UiState) {
    val state = ui.flashState
    val progress = ui.downloadProgress
    if (state is FirmwareUpdater.State.Idle && progress == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val label = when {
                progress != null && progress < 1f -> "Downloading… %.0f %%".format(progress * 100)
                state is FirmwareUpdater.State.Starting -> "Starting flash…"
                state is FirmwareUpdater.State.Uploading ->
                    "Uploading chunk ${state.sent}/${state.total}"
                state is FirmwareUpdater.State.Finalising -> "Finalising…"
                state is FirmwareUpdater.State.Rebooting -> "Rebooting scooter…"
                state is FirmwareUpdater.State.Done -> "✓ Flash complete"
                state is FirmwareUpdater.State.Failed -> "✗ ${state.reason}"
                else -> "…"
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val progressValue = when {
                progress != null && progress < 1f -> progress
                state is FirmwareUpdater.State.Uploading -> state.sent.toFloat() / state.total
                state is FirmwareUpdater.State.Done -> 1f
                else -> null
            }
            if (progressValue != null) {
                LinearProgressIndicator(
                    progress = { progressValue.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReleaseRow(release: FirmwareRelease, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    release.displayName ?: release.version,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${release.target.name} • ${release.version}" +
                            (release.sizeBytes?.let { " • ${it / 1024} kB" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onClick) { Text("Flash") }
        }
    }
}
