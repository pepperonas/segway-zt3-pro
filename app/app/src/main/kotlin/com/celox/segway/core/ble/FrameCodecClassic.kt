package com.celox.segway.core.ble

/**
 * Stock Ninebot frame format (`Ninebot` path in SHU's `c6.b#a()`):
 *
 *     [0x5A][0xA5][len][src][dst][cmd][arg][payload…][crc_lo][crc_hi]
 *
 * Where:
 *  - `len`     = `(payload + 4)` – 4   →   payload length only (excludes src/dst/cmd/arg)
 *  - `src`     = source address  (phone = `0x3E`)
 *  - `dst`     = destination address (`0x21` = VCU, `0x22` = MCU/ESC, `0x23` = BMS)
 *  - `cmd`     = `0x02` write-register, `0x03` read-register
 *                (`0x32`/`0x33` for SHFW custom-firmware register space)
 *  - `arg`     = register offset
 *  - `payload` = raw bytes
 *  - CRC       = `~sum_of_bytes_from_len_onwards & 0xFFFF`, little-endian
 *
 * This is the encoding the SHU app actually puts on the wire for the
 * "Ninebot Stock" path (decompile: `c6.b.a()` case `i7 == 3`).
 */
object FrameCodecClassic {

    const val MAGIC_HI: Byte = 0x5A
    const val MAGIC_LO: Byte = 0xA5.toByte()

    const val SRC_PHONE: Byte = 0x3E

    /**
     * txAddr for the ZT3 Pro D crypto-handshake target. Derived from CRC analysis
     * of SHU's working session (`speed-manip.pcap` Phase B + E2): the wire CRC
     * `62 FF` for the 4-byte body `[3E XX 5B 00]` is only consistent with `XX=0x04`.
     * Using `0x21` (the typical VCU address) gets no response — confirmed by SHU's
     * own Phase E1 attempt with txAddr=0x21 also failing.
     */
    const val DST_VCU: Byte = 0x04
    const val DST_MCU: Byte = 0x22
    const val DST_BMS: Byte = 0x23

    const val CMD_READ_REGULAR: Byte = 0x01
    const val CMD_WRITE_REGULAR: Byte = 0x02
    const val CMD_READ_SHFW: Byte = 0x33
    const val CMD_WRITE_SHFW: Byte = 0x32

    /**
     * Build a complete frame ready for the BLE write (incl. magic + CRC).
     */
    fun wrap(
        src: Byte = SRC_PHONE,
        dst: Byte,
        cmd: Byte,
        arg: Byte,
        payload: ByteArray,
    ): ByteArray {
        // Inner buffer matches SHU's bArr passed into c6.b#a()
        val inner = ByteArray(payload.size + 4).apply {
            this[0] = src
            this[1] = dst
            this[2] = cmd
            this[3] = arg
            System.arraycopy(payload, 0, this, 4, payload.size)
        }

        val out = ByteArray(inner.size + 5)
        out[0] = MAGIC_HI
        out[1] = MAGIC_LO
        out[2] = (inner.size - 4).toByte()      // payload length only, not header
        System.arraycopy(inner, 0, out, 3, inner.size)

        // CRC = ~sum_of_bytes(out[2 .. inner.size+2]) & 0xFFFF, LE
        var sum = 0
        for (i in 2 until inner.size + 3) sum += out[i].toInt() and 0xFF
        val crc = sum.inv() and 0xFFFF
        out[inner.size + 3] = (crc and 0xFF).toByte()
        out[inner.size + 4] = ((crc ushr 8) and 0xFF).toByte()
        return out
    }

    /** Convenience: writeRegister to a specific dst at register offset with payload bytes. */
    fun writeRegister(dst: Byte, register: Byte, payload: ByteArray): ByteArray =
        wrap(SRC_PHONE, dst, CMD_WRITE_REGULAR, register, payload)

    /** Convenience: readRegister of [length] bytes from [dst] at offset [register]. */
    fun readRegister(dst: Byte, register: Byte, length: Int): ByteArray =
        wrap(SRC_PHONE, dst, CMD_READ_REGULAR, register, byteArrayOf(length.toByte()))

    /** Quick sanity-check parse — returns null on bad magic / CRC. */
    fun parse(frame: ByteArray): Decoded? {
        if (frame.size < 8) return null
        if (frame[0] != MAGIC_HI) return null
        if (frame[1] != MAGIC_LO) return null
        val len = frame[2].toInt() and 0xFF
        if (frame.size < len + 7) return null
        var sum = 0
        for (i in 2 until len + 5) sum += frame[i].toInt() and 0xFF
        val expected = sum.inv() and 0xFFFF
        val actual = (frame[len + 5].toInt() and 0xFF) or ((frame[len + 6].toInt() and 0xFF) shl 8)
        if (expected != actual) return null
        return Decoded(
            src = frame[3],
            dst = frame[4],
            cmd = frame[5],
            arg = frame[6],
            payload = frame.copyOfRange(7, len + 5)
        )
    }

    data class Decoded(val src: Byte, val dst: Byte, val cmd: Byte, val arg: Byte, val payload: ByteArray)
}
