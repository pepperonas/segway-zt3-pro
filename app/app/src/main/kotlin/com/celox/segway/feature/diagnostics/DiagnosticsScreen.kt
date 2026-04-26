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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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

    /** Fire-and-forget channel for short user-facing notifications (Toasts). */
    val toasts = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun readFirmware() = sendCmd(VehicleCommand.ReadFirmware)
    fun readBlackBox() = sendCmd(VehicleCommand.ReadBlackBox)
    fun readStatus() = sendCmd(VehicleCommand.ReadRegister(0xB0, 32))

    fun sendSpeedLimit(kmh: Int) = sendCmd(VehicleCommand.SetSpeedLimit(kmh))
    fun lock() = sendCmd(VehicleCommand.Lock)
    fun unlock() = sendCmd(VehicleCommand.Unlock)

    /**
     * Brute-force read every register 0x00..0xFF (length=2) from MULTIPLE
     * destinations: VCU=0x16, MCU=0x02, BMS=0x07, Display=0x23.
     * Logs each response as a `SCAN`-tagged note so we can diff before/after
     * a state-change to find the right register.
     * Run this twice (before + after dashboard-mode-switch) and compare.
     */
    fun registerSweep() {
        val v = activeHolder.activeVehicle.value ?: return
        viewModelScope.launch {
            toasts.emit("Sweep startet — bitte ~30 s warten")
            for (dst in listOf(0x16.toByte(), 0x02.toByte(), 0x07.toByte(), 0x23.toByte())) {
                log.note("SCAN", "=== sweep dst=0x${"%02X".format(dst)} regs 0x00..0xFF len=2 ===")
                for (reg in 0x00..0xFF) {
                    v.execute(VehicleCommand.ReadRegister(reg, 2, dst))
                    kotlinx.coroutines.delay(35L)
                }
            }
            log.note("SCAN", "=== sweep end ===")
            toasts.emit("✓ Sweep fertig")
        }
    }

    /**
     * Phase counter for the button-hunt helper. UI uses it to show a big
     * "DRÜCK JETZT DEN BUTTON" hint between the two sweeps.
     *
     *  0 = idle
     *  1 = sweep A in flight (don't press yet)
     *  2 = waiting for user to press the custom button
     *  3 = sweep B in flight (don't release yet)
     *  4 = done — diff visible in the log as `DIFF` notes
     */
    val buttonHuntPhase = kotlinx.coroutines.flow.MutableStateFlow(0)

    /**
     * Two-sweep helper to find which register reflects a custom-button press.
     * VCU-only (dst=0x16) for speed; sweeps the same range twice with a user
     * press in between, then walks both captures and emits `DIFF | reg 0xNN
     * before=[..] after=[..]` notes for any byte that changed.
     */
    fun runButtonHunt() {
        val v = activeHolder.activeVehicle.value ?: return
        if (buttonHuntPhase.value != 0) return  // already running
        viewModelScope.launch {
            try {
                // Phase 1: sweep A, capture by walking BleLog entries afterwards.
                buttonHuntPhase.value = 1
                log.note("HUNT", "=== sweep A (BEFORE press) start ===")
                val seqBeforeA = log.entries.value.lastOrNull()?.seq ?: 0L
                for (reg in 0x00..0xFF) {
                    v.execute(VehicleCommand.ReadRegister(reg, 2, 0x16))
                    kotlinx.coroutines.delay(35L)
                }
                kotlinx.coroutines.delay(500L) // drain pipe
                val seqAfterA = log.entries.value.lastOrNull()?.seq ?: seqBeforeA
                val before = collectVcuReads(seqBeforeA, seqAfterA)
                log.note("HUNT", "sweep A captured ${before.size} regs — DRÜCK JETZT (5 s)")

                // Phase 2: 5 s for the user.
                buttonHuntPhase.value = 2
                kotlinx.coroutines.delay(5_000L)

                // Phase 3: sweep B, same idea.
                buttonHuntPhase.value = 3
                log.note("HUNT", "=== sweep B (AFTER press) start ===")
                val seqBeforeB = log.entries.value.lastOrNull()?.seq ?: 0L
                for (reg in 0x00..0xFF) {
                    v.execute(VehicleCommand.ReadRegister(reg, 2, 0x16))
                    kotlinx.coroutines.delay(35L)
                }
                kotlinx.coroutines.delay(500L)
                val seqAfterB = log.entries.value.lastOrNull()?.seq ?: seqBeforeB
                val after = collectVcuReads(seqBeforeB, seqAfterB)
                log.note("HUNT", "sweep B captured ${after.size} regs — diffing")

                // Phase 4: emit diffs.
                var diffs = 0
                for (reg in 0x00..0xFF) {
                    val a = before[reg] ?: continue
                    val b = after[reg] ?: continue
                    if (!a.contentEquals(b)) {
                        diffs++
                        log.note(
                            "DIFF",
                            "reg 0x%02X  before=[%s]  after=[%s]".format(
                                reg,
                                a.joinToString(" ") { "%02X".format(it) },
                                b.joinToString(" ") { "%02X".format(it) },
                            )
                        )
                    }
                }
                log.note("HUNT", "=== done — $diffs register(s) changed ===")
                buttonHuntPhase.value = 4
                kotlinx.coroutines.delay(3_000L)
                buttonHuntPhase.value = 0
            } catch (t: Throwable) {
                log.note("HUNT", "aborted: ${t.message}")
                buttonHuntPhase.value = 0
            }
        }
    }

    /**
     * Walk the BleLog entries between [startSeqExclusive] and [endSeq], pick
     * out the `RX-DEC src=16 ... arg=NN [HH HH ...]` notes from the VCU and
     * return register-id → bytes. The format is fixed (set in
     * Zt3ProVehicle.handleNotify) so a simple regex is enough.
     */
    private fun collectVcuReads(startSeqExclusive: Long, endSeq: Long): Map<Int, ByteArray> {
        val out = HashMap<Int, ByteArray>()
        val rx = Regex("src=16 dst=3E cmd=04 arg=([0-9A-Fa-f]{2}) \\[([0-9A-Fa-f ]+)\\]")
        for (e in log.entries.value) {
            if (e.seq <= startSeqExclusive || e.seq > endSeq) continue
            val msg = e.message ?: continue
            val m = rx.find(msg) ?: continue
            val reg = m.groupValues[1].toInt(16)
            val bytes = m.groupValues[2].trim().split(' ')
                .map { it.toInt(16).toByte() }.toByteArray()
            out[reg] = bytes
        }
        return out
    }

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
    LaunchedEffect(Unit) {
        vm.toasts.collect { msg ->
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
        }
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
                onSweep = vm::registerSweep,
                onButtonHunt = vm::runButtonHunt,
            )
            val huntPhase by vm.buttonHuntPhase.collectAsStateWithLifecycle()
            ButtonHuntBanner(huntPhase)
            FieldTestBar(
                isConnected = state.isConnected,
                onSpeed = vm::sendSpeedLimit,
                onLock = vm::lock,
                onUnlock = vm::unlock,
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
    onSweep: () -> Unit,
    onButtonHunt: () -> Unit,
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
            label = { Text("FW") }
        )
        AssistChip(
            onClick = onBlackBox,
            enabled = isConnected,
            leadingIcon = { Icon(Icons.Outlined.History, null) },
            label = { Text("Black-Box") }
        )
        AssistChip(
            onClick = onSweep,
            enabled = isConnected,
            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
            label = { Text("Sweep") }
        )
        AssistChip(
            onClick = onButtonHunt,
            enabled = isConnected,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            label = { Text("Btn-Hunt") }
        )
    }
}

@Composable
private fun ButtonHuntBanner(phase: Int) {
    if (phase == 0) return
    val (text, color) = when (phase) {
        1 -> "📸 Sweep A läuft — NICHT drücken" to MaterialTheme.colorScheme.tertiary
        2 -> "▶ JETZT BUTTON DRÜCKEN UND HALTEN" to MaterialTheme.colorScheme.error
        3 -> "📸 Sweep B läuft — Button noch halten" to MaterialTheme.colorScheme.error
        4 -> "✓ Fertig — siehe DIFF-Zeilen unten" to MaterialTheme.colorScheme.primary
        else -> return
    }
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = color)
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = androidx.compose.ui.graphics.Color.White,
        )
    }
}

@Composable
private fun FieldTestBar(
    isConnected: Boolean,
    onSpeed: (Int) -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { onSpeed(22) }, enabled = isConnected,
            label = { Text("→ 22 km/h") }
        )
        AssistChip(
            onClick = { onSpeed(40) }, enabled = isConnected,
            label = { Text("→ 40 km/h") }
        )
        AssistChip(
            onClick = onLock, enabled = isConnected,
            label = { Text("Lock") }
        )
        AssistChip(
            onClick = onUnlock, enabled = isConnected,
            label = { Text("Unlock") }
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
