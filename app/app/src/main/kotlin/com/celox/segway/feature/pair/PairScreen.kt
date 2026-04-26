package com.celox.segway.feature.pair

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.core.ble.DiscoveredScooter
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@Composable
fun PairScreen(
    onClose: () -> Unit,
    viewModel: PairViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()

    val perms = rememberMultiplePermissionsState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    )

    LaunchedEffect(perms.allPermissionsGranted) {
        if (perms.allPermissionsGranted) viewModel.startScan()
    }

    // Auto-close when the auto-pair has bound a vehicle.
    LaunchedEffect(viewModel) {
        viewModel.pairedEvent.collect { onClose() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
                .padding(16.dp)
        ) {
            if (!perms.allPermissionsGranted) {
                PermissionPrompt(perms)
                return@Scaffold
            }

            // Wake-up hint: scooter must be powered on for BLE to advertise
            // AND for the handshake to complete. Always visible on this screen
            // — most pairing timeouts are caused by a sleeping scooter, which
            // only the user can fix. Once paired, navigation leaves this
            // screen entirely so the hint disappears naturally.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.PowerSettingsNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(
                        stringResource(R.string.pair_press_power),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ui.scanning) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 12.dp))
                Text(
                    if (ui.scanning) stringResource(R.string.pair_scanning) else stringResource(R.string.pair_no_devices),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(12.dp))

            ui.errorText?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // While we're handshaking, take over the screen so the user
            // doesn't navigate away thinking the connection is up.
            if (ui.pairedAddress != null && ui.pairingStatus != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column {
                            Text(
                                ui.pairingStatus!!,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "Pairing ${ui.pairedAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ui.devices, key = { it.address }) { device ->
                        DeviceCard(device, onPair = { viewModel.pair(device) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(perms: MultiplePermissionsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.error_permissions), style = MaterialTheme.typography.titleMedium)
        Button(onClick = { perms.launchMultiplePermissionRequest() }) {
            Text(stringResource(R.string.action_grant))
        }
    }
}

@Composable
private fun DeviceCard(device: DiscoveredScooter, onPair: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (device.isCrypto) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.isCrypto) Icons.Outlined.Lock else Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: device.address, fontWeight = FontWeight.SemiBold)
                Text(
                    "${device.address}  •  ${device.rssi} dBm" + (device.beacon?.let { "  •  model 0x%04X".format(it.modelId) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onPair) { Text(stringResource(R.string.pair_connect)) }
        }
    }
}

