package com.celox.segway.core.ble

import com.celox.segway.core.crypto.NinebotCrypto

/**
 * Crypto-mode wrapper that mirrors [FrameCodecClassic]'s API but routes
 * everything through [NinebotCrypto]. This corresponds to SHU's
 * `c6.b#a()` Case 2 (`f0.a.NinebotCrypto`), which is the path the ZT3 Pro D
 * actually expects on the wire.
 */
class FrameCodecCrypto(private val crypto: NinebotCrypto) {

    fun writeRegister(dst: Byte, register: Byte, payload: ByteArray): ByteArray =
        wrap(FrameCodecClassic.SRC_PHONE, dst, FrameCodecClassic.CMD_WRITE_REGULAR, register, payload)

    fun readRegister(dst: Byte, register: Byte, length: Int): ByteArray =
        wrap(
            FrameCodecClassic.SRC_PHONE, dst,
            FrameCodecClassic.CMD_READ_REGULAR, register,
            byteArrayOf(length.toByte())
        )

    /** Build the inner frame (matches `j6.m`'s f6.b layout) and encrypt to wire bytes. */
    private fun wrap(src: Byte, dst: Byte, cmd: Byte, arg: Byte, payload: ByteArray): ByteArray {
        // Inner = [src dst cmd arg payload] — same as Case 3, except wrapper differs.
        val inner = ByteArray(4 + payload.size).apply {
            this[0] = src
            this[1] = dst
            this[2] = cmd
            this[3] = arg
            System.arraycopy(payload, 0, this, 4, payload.size)
        }
        // Plain layout passed to NinebotCrypto.encrypt:
        //   [5A A5 (inner.size - 4)] [inner]   ← same prefix `c6.b.a()` Case 2 builds.
        val plain = ByteArray(inner.size + 3).apply {
            this[0] = FrameCodecClassic.MAGIC_HI
            this[1] = FrameCodecClassic.MAGIC_LO
            this[2] = (inner.size - 4).toByte()
            System.arraycopy(inner, 0, this, 3, inner.size)
        }
        return crypto.encrypt(plain)
    }

    /**
     * Parse and decrypt an incoming wire frame. Returns null on bad magic / size.
     * The decoded inner frame is then exposed as a [FrameCodecClassic.Decoded]
     * so existing notify-handlers don't need to know about the crypto layer.
     */
    fun parse(frame: ByteArray): FrameCodecClassic.Decoded? {
        if (frame.size < 9) return null
        if (frame[0] != FrameCodecClassic.MAGIC_HI) return null
        if (frame[1] != FrameCodecClassic.MAGIC_LO) return null
        val plain = crypto.decrypt(frame) ?: return null
        if (plain.size < 7) return null
        val len = plain[2].toInt() and 0xFF
        if (plain.size < len + 7) return null
        return FrameCodecClassic.Decoded(
            src = plain[3],
            dst = plain[4],
            cmd = plain[5],
            arg = plain[6],
            payload = plain.copyOfRange(7, len + 7)
        )
    }
}
