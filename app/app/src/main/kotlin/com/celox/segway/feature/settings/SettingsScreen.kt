package com.celox.segway.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.core.data.UserPreferencesRepository
import com.celox.segway.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: UserPreferencesRepository
) : ViewModel() {
    val state: StateFlow<UserPreferencesRepository.Snapshot> =
        prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferencesRepository.defaults)
}

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .padding(horizontal = 16.dp)
        ) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { scope.launch { vm.prefs.setThemeMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                    ) {
                        Text(when (mode) {
                            ThemeMode.System -> stringResource(R.string.settings_theme_system)
                            ThemeMode.Light -> stringResource(R.string.settings_theme_light)
                            ThemeMode.Dark -> stringResource(R.string.settings_theme_dark)
                        })
                    }
                }
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                trailingContent = {
                    Switch(
                        checked = state.dynamicColor,
                        onCheckedChange = { scope.launch { vm.prefs.setDynamicColor(it) } }
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
                trailingContent = {
                    Switch(
                        checked = state.keepScreenOn,
                        onCheckedChange = { scope.launch { vm.prefs.setKeepScreenOn(it) } }
                    )
                }
            )

            Text(stringResource(R.string.settings_units), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.unitsMetric,
                    onClick = { scope.launch { vm.prefs.setUnitsMetric(true) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(stringResource(R.string.settings_units_metric)) }
                SegmentedButton(
                    selected = !state.unitsMetric,
                    onClick = { scope.launch { vm.prefs.setUnitsMetric(false) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text(stringResource(R.string.settings_units_imperial)) }
            }
        }
    }
}
