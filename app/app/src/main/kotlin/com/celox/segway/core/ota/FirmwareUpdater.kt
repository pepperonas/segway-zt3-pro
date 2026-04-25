package com.celox.segway.core.ota

import com.celox.segway.core.ble.EllipticPairing
import com.celox.segway.core.ble.GattClient
import com.celox.segway.core.repo.FirmwareTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Drives the In-Application-Programming (IAP) flash protocol for Ninebot scooters.
 *
 * Wire-level commands (per the Ninebot 2nd-gen protocol — see `PRIOR-RESEARCH.md` §5):
 *   0x07  Start update      (arg = update size, big-endian)
 *   0x08  Write chunk       (arg = chunk index, payload = chunk bytes)
 *   0x09  Finish update     (arg = checksum/CRC32 over the full image)
 *   0x0A  Reboot
 *
 * Notes & assumptions:
 *  - Chunks are 16 bytes by default (smallest common denominator across firmwares).
 *    Some newer revisions accept up to (MTU - overhead). We default to 16.
 *  - We *wait* for an ACK notification before sending the next chunk. ACK is parsed
 *    as a register-write echo (0x02 ack). Implementation-defined for the moment;
 *    tighten once a real flash is observed.
 *  - The CRC32 algorithm used by the firmware bootloader varies. We implement the
 *    standard zlib polynomial here; if the device rejects the final 0x09, switch
 *    to CRC16-CCITT or sum-of-bytes as alternatives.
 */
class FirmwareUpdater(
    private val gatt: GattClient,
    private val pairing: EllipticPairing,
    private val scope: CoroutineScope,
) {

    sealed interface State {
        data object Idle : State
        data object Starting : State
        data class Uploading(val sent: Int, val total: Int) : State
        data object Finalising : State
        data object Rebooting : State
        data object Done : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Flash a firmware image. The [target] is informational — the actual register
     * stream is the same; the device infers the partition from the binary header.
     */
    fun flash(target: FirmwareTarget, image: ByteArray) {
        cancel()
        job = scope.launch {
            try {
                runFlash(target, image)
            } catch (t: Throwable) {
                Timber.e(t, "Flash failed")
                _state.value = State.Failed(t.message ?: t::class.simpleName ?: "error")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun runFlash(target: FirmwareTarget, image: ByteArray) {
        require(image.size in 1..2_000_000) { "image size out of range" }
        Timber.i("Flashing ${target.name} • ${image.size} bytes")

        _state.value = State.Starting
        if (!sendStartUpdate(image.size)) error("Start (0x07) failed")
        if (!awaitAck(0x07, timeoutMs = 5_000)) error("Start ACK timeout")

        val chunkSize = pickChunkSize(gatt.mtu.value)
        val chunks = image.toList().chunked(chunkSize).map { it.toByteArray() }
        Timber.i("Uploading ${chunks.size} chunks of $chunkSize bytes")

        chunks.forEachIndexed { idx, chunk ->
            if (!sendChunk(idx, chunk)) error("Chunk $idx send failed")
            if (!awaitAck(0x08, timeoutMs = 4_000)) error("Chunk $idx ACK timeout")
            _state.value = State.Uploading(idx + 1, chunks.size)
        }

        _state.value = State.Finalising
        val crc = crc32(image)
        if (!sendFinish(crc)) error("Finish (0x09) failed")
        if (!awaitAck(0x09, timeoutMs = 10_000)) error("Finish ACK timeout")

        _state.value = State.Rebooting
        sendReboot()
        delay(1_500)
        _state.value = State.Done
    }

    private fun pickChunkSize(mtu: Int): Int {
        // Frame overhead: magic(2) + len(1) + seq(2) + cmd+arg(2) + tag(3) + checksum(2) ≈ 12 bytes
        // Stay conservative; most stable Ninebot bootloaders use 16-byte chunks.
        return 16
    }

    private fun sendStartUpdate(size: Int): Boolean {
        val payload = byteArrayOf(0x07, 0x00, 0x04,
            ((size ushr 24) and 0xFF).toByte(),
            ((size ushr 16) and 0xFF).toByte(),
            ((size ushr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte())
        val frame = pairing.encrypt(payload) ?: return false
        return gatt.send(frame)
    }

    private fun sendChunk(idx: Int, chunk: ByteArray): Boolean {
        val payload = byteArrayOf(
            0x08,
            (idx and 0xFF).toByte(),
            chunk.size.toByte()
        ) + chunk
        val frame = pairing.encrypt(payload) ?: return false
        return gatt.send(frame)
    }

    private fun sendFinish(crc: Int): Boolean {
        val payload = byteArrayOf(0x09, 0x00, 0x04,
            ((crc ushr 24) and 0xFF).toByte(),
            ((crc ushr 16) and 0xFF).toByte(),
            ((crc ushr 8) and 0xFF).toByte(),
            (crc and 0xFF).toByte())
        val frame = pairing.encrypt(payload) ?: return false
        return gatt.send(frame)
    }

    private fun sendReboot(): Boolean {
        val payload = byteArrayOf(0x0A, 0x00)
        val frame = pairing.encrypt(payload) ?: return false
        return gatt.send(frame)
    }

    /**
     * Wait for an acknowledgement notification matching [forCmd]. ACK detection
     * is best-effort — the actual format depends on the firmware.
     */
    private suspend fun awaitAck(forCmd: Int, timeoutMs: Long): Boolean {
        val gotAck = withTimeoutOrNull(timeoutMs) {
            gatt.incoming.first { raw ->
                val plain = pairing.decryptResponse(raw) ?: return@first false
                plain.isNotEmpty() && (
                    plain[0].toInt() and 0xFF == 0x02 ||                  // generic write-ack
                    plain[0].toInt() and 0xFF == forCmd                   // direct echo
                )
            }
            true
        }
        return gotAck == true
    }

    private fun crc32(data: ByteArray): Int {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value.toInt()
    }
}

