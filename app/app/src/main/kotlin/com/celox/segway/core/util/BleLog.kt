package com.celox.segway.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide ring buffer of every BLE frame the app sends or receives,
 * plus protocol-level annotations (handshake stages, decrypted commands).
 *
 * Used by the [com.celox.segway.feature.diagnostics.DiagnosticsScreen]
 * for live debugging and field-testing the pairing assumptions.
 *
 * Capacity is fixed at 512 entries; oldest get dropped silently.
 */
@Singleton
class BleLog @Inject constructor() {

    enum class Direction { TX, RX, Note }

    data class Entry(
        val seq: Long,
        val timestamp: Long,
        val direction: Direction,
        val tag: String,
        val hex: String?,
        val message: String?,
    )

    private val nextSeq = AtomicLong(0)
    private val maxEntries = 512

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun tx(tag: String, bytes: ByteArray) = append(Direction.TX, tag, hex = bytes.toHex(), message = null)
    fun rx(tag: String, bytes: ByteArray) = append(Direction.RX, tag, hex = bytes.toHex(), message = null)
    fun note(tag: String, message: String) = append(Direction.Note, tag, hex = null, message = message)

    fun clear() = _entries.update { emptyList() }

    fun exportText(): String {
        val df = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("# BLE log export ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("# Format: <seq> <ts> <dir> <tag> <hex/msg>")
        for (e in _entries.value) {
            sb.append(e.seq.toString().padStart(4, '0'))
            sb.append(' ').append(df.format(Date(e.timestamp)))
            sb.append(' ').append(e.direction.name.padEnd(4))
            sb.append(' ').append(e.tag.padEnd(10))
            sb.append(' ').append(e.hex ?: e.message ?: "")
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun append(direction: Direction, tag: String, hex: String?, message: String?) {
        _entries.update { current ->
            val next = current + Entry(
                seq = nextSeq.incrementAndGet(),
                timestamp = System.currentTimeMillis(),
                direction = direction,
                tag = tag,
                hex = hex,
                message = message,
            )
            if (next.size > maxEntries) next.takeLast(maxEntries) else next
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(" ") { "%02X".format(it) }
}
