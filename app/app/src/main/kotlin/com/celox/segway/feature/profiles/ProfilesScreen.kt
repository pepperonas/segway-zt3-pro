package com.celox.segway.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.core.profile.SpeedProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    vm: ProfilesViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val a11yOn by vm.accessibilityEnabledOnDevice.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) }
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Boot profile
            Section(stringResource(R.string.profiles_boot)) {
                ProfileEditor(
                    profile = settings.boot,
                    onSpeedChange = vm::updateBoot,
                    onLabelChange = vm::updateBootLabel,
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-apply on connect", fontWeight = FontWeight.Medium)
                        Text(
                            "If on: the boot value is sent ~1.5 s after each connect. " +
                                "Leave OFF if you're unsure how the scooter reacts to the command.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoApplyOnConnect,
                        onCheckedChange = vm::setAutoApplyOnConnect,
                    )
                }
            }

            // Quick actions
            Section(stringResource(R.string.profiles_quick)) {
                settings.quickActions.forEachIndexed { idx, qa ->
                    ProfileEditor(
                        profile = qa,
                        onSpeedChange = { vm.updateQuickAction(idx, qa.copy(speedKmh = it)) },
                        onLabelChange = { vm.updateQuickAction(idx, qa.copy(label = it)) },
                    )
                }
            }

            // Unlock profile
            Section(stringResource(R.string.profiles_unlock)) {
                ProfileEditor(
                    profile = settings.unlock,
                    onSpeedChange = vm::updateUnlock,
                    onLabelChange = { /* readonly label */ },
                    labelEditable = false,
                    maxSpeed = 60,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = settings.unlockPin,
                    onValueChange = vm::updatePin,
                    label = { Text(stringResource(R.string.profiles_pin)) },
                    placeholder = { Text(stringResource(R.string.profiles_pin_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.profiles_accessibility), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.profiles_accessibility_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.accessibilityTriggerEnabled,
                        onCheckedChange = vm::setAccessibilityToggle,
                    )
                }

                if (settings.accessibilityTriggerEnabled && !a11yOn) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Permission missing",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap below to open Android's Accessibility settings, then enable \"Segway Mobility\" → Volume-Down trigger.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { AccessibilityHelper.openSettings(ctx) }) {
                                Text("Open Accessibility settings")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("${stringResource(R.string.profiles_auto_revert)}: ${settings.autoRevertMinutes} min", fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.autoRevertMinutes.toFloat(),
                    onValueChange = { vm.updateAutoRevert(it.toInt()) },
                    valueRange = 0f..60f,
                    steps = 11
                )
                Text(
                    "0 = stays unlocked until you reduce manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ProfileEditor(
    profile: SpeedProfile,
    onSpeedChange: (Int) -> Unit,
    onLabelChange: (String) -> Unit,
    labelEditable: Boolean = true,
    maxSpeed: Int = 40,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = profile.label,
            onValueChange = onLabelChange,
            singleLine = true,
            enabled = labelEditable,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.padding(start = 12.dp))
        Text("${profile.speedKmh} km/h", fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = profile.speedKmh.toFloat(),
        onValueChange = { onSpeedChange(it.toInt()) },
        valueRange = 5f..maxSpeed.toFloat(),
        steps = (maxSpeed - 5) - 1
    )
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
