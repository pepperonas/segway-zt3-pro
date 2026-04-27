package com.celox.segway.feature.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ElectricMoped
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.celox.segway.R

@Composable
fun MineScreen(
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onGarageClick: () -> Unit,
    onFirmwareClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onAirLockClick: () -> Unit,
    onProfilesClick: () -> Unit,
    onScooterSettingsClick: () -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_mine)) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("Speed profiles") },
                supportingContent = { Text("Boot, quick actions, unlock") },
                leadingContent = { Icon(Icons.Outlined.Speed, null) },
                modifier = Modifier.clickable { onProfilesClick() }
            )
            ListItem(
                headlineContent = { Text("Roller-Einstellungen") },
                supportingContent = { Text("Lichter, Modi, KERS, Akku-Limit, …") },
                leadingContent = { Icon(Icons.Outlined.Tune, null) },
                modifier = Modifier.clickable { onScooterSettingsClick() }
            )
            ListItem(
                headlineContent = { Text("Garage") },
                supportingContent = { Text("Manage paired scooters") },
                leadingContent = { Icon(Icons.Outlined.ElectricMoped, null) },
                modifier = Modifier.clickable { onGarageClick() }
            )
            ListItem(
                headlineContent = { Text("Firmware") },
                supportingContent = { Text("Flash VCU/MCU, change region") },
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, null) },
                modifier = Modifier.clickable { onFirmwareClick() }
            )
            ListItem(
                headlineContent = { Text("AirLock") },
                supportingContent = { Text("Auto-unlock by proximity") },
                leadingContent = { Icon(Icons.Outlined.LockOpen, null) },
                modifier = Modifier.clickable { onAirLockClick() }
            )
            ListItem(
                headlineContent = { Text("Diagnostics") },
                supportingContent = { Text("Live BLE log + crash records") },
                leadingContent = { Icon(Icons.Outlined.BugReport, null) },
                modifier = Modifier.clickable { onDiagnosticsClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_title)) },
                leadingContent = { Icon(Icons.Outlined.Settings, null) },
                modifier = Modifier.clickable { onSettingsClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                modifier = Modifier.clickable { onAboutClick() }
            )
        }
    }
}
