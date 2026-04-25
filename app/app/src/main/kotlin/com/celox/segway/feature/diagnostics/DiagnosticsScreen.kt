package com.celox.segway.feature.diagnostics

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.celox.segway.core.util.BleLog
import com.celox.segway.core.vehicle.VehicleCommand
import com.celox.segway.core.vehicle.VehicleState
import com.celox.segway.feature.home.ActiveVehicleHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    val log: BleLog,
    private val activeHolder: ActiveVehicleHolder,
) : ViewModel() {

    val vehicleState: StateFlow<VehicleState> = activeHolder.activeVehicle
        .flatMapLatest { v -> v?.state ?: MutableStateFlow(VehicleState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleState())

    fun readFirmware() = sendCmd(VehicleCommand.ReadFirmware)
    fun readBlackBox() = sendCmd(VehicleCommand.ReadBlackBox)
    fun readStatus() = sendCmd(VehicleCommand.ReadRegister(0xB0, 32))

    private fun sendCmd(cmd: VehicleCommand) {
        val v = activeHolder.activeVehicle.value ?: return
        viewModelScope.launch { v.execute(cmd) }
    }
}

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    vm: DiagnosticsViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val entries by vm.log.entries.collectAsStateWithLifecycle()
    val state by vm.vehicleState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val df = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { vm.log.clear() }) {
                        Icon(Icons.Outlined.ClearAll, null)
                    }
                    IconButton(onClick = {
                        val text = vm.log.exportText()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, "Segway-Reborn BLE log")
                        }
                        ctx.startActivity(Intent.createChooser(intent, "Share log"))
                    }) {
                        Icon(Icons.Outlined.Share, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ActionsBar(
                isConnected = state.isConnected,
                onStatus = vm::readStatus,
                onFirmware = vm::readFirmware,
                onBlackBox = vm::readBlackBox,
            )
            BlackBoxCard(state, df)
            FrameLogList(entries = entries, listState = listState, df = df, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionsBar(
    isConnected: Boolean,
    onStatus: () -> Unit,
    onFirmware: () -> Unit,
    onBlackBox: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = onStatus,
            enabled = isConnected,
            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
            label = { Text("Status") }
        )
        AssistChip(
            onClick = onFirmware,
            enabled = isConnected,
            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
            label = { Text("FW versions") }
        )
        AssistChip(
            onClick = onBlackBox,
            enabled = isConnected,
            leadingIcon = { Icon(Icons.Outlined.History, null) },
            label = { Text("Black-Box") }
        )
    }
}

@Composable
private fun BlackBoxCard(state: VehicleState, df: SimpleDateFormat) {
    val raw = state.blackBoxRaw ?: return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Black-Box (last 64 bytes from 0xF0)",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                raw.joinToString(" ") { "%02X".format(it) },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            state.errorCode.let { code ->
                if (code != 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Last error code: 0x%02X".format(code),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun FrameLogList(
    entries: List<BleLog.Entry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    df: SimpleDateFormat,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Empty log. Connect a vehicle to populate.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(entries, key = { it.seq }) { entry ->
            LogRow(entry, df)
        }
    }
}

@Composable
private fun LogRow(entry: BleLog.Entry, df: SimpleDateFormat) {
    val color = when (entry.direction) {
        BleLog.Direction.TX   -> MaterialTheme.colorScheme.tertiaryContainer
        BleLog.Direction.RX   -> MaterialTheme.colorScheme.primaryContainer
        BleLog.Direction.Note -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onColor = when (entry.direction) {
        BleLog.Direction.TX   -> MaterialTheme.colorScheme.onTertiaryContainer
        BleLog.Direction.RX   -> MaterialTheme.colorScheme.onPrimaryContainer
        BleLog.Direction.Note -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                df.format(Date(entry.timestamp)),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = onColor.copy(alpha = 0.7f)
            )
            Spacer(Modifier.size(8.dp))
            Text(entry.direction.name.padEnd(4), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = onColor)
            Spacer(Modifier.size(8.dp))
            Text(entry.tag.padEnd(10), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = onColor)
            Spacer(Modifier.size(8.dp))
            Text(entry.hex ?: entry.message ?: "", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = onColor)
        }
    }
}
