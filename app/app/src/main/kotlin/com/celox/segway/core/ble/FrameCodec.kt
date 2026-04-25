package com.celox.segway.core.ble

import com.celox.segway.core.crypto.EllipticCrypto

/**
 * Application-layer frame format (Ninebot 2nd-gen Crypto):
 *
 *   +---+---+----------+-------+-------+------------------+----+----+
 *   |55 |AB | len-1    | seq_l | seq_h | encrypted_body   | cs1| cs2|
 *   +---+---+----------+-------+-------+------------------+----+----+
 *    0   1   2          3       4       5..N               N+1  N+2
 *
 * Notes:
 *  - Magic 0x55 0xAB is the modern (encrypted) variant. Classic M365 uses 0x55 0xAA
 *    and is currently NOT implemented here (ZT3 Pro uses 0x55 0xAB).
 *  - Length byte is `bodyLen - 1` (where bodyLen counts from byte 2 onwards).
 *  - Sequence is little-endian.
 *  - Checksum is the 16-bit inverted sum over bytes 2..N (i.e. excluding the magic).
 */
object FrameCodec {

    const val MAGIC_HI: Byte = 0x55
    const val MAGIC_LO_MODERN: Byte = 0xAB.toByte()
    const val MAGIC_LO_CLASSIC: Byte = 0xAA.toByte()

    /**
     * Wrap an already-encrypted payload into a transport frame.
     *
     * @param seq frame sequence (auto-increment counter, 16-bit)
     * @param encryptedBody result of AES/CCM
     */
    fun wrap(seq: Int, encryptedBody: ByteArray): ByteArray {
        val seqBytes = byteArrayOf(
            (seq and 0xFF).toByte(),
            ((seq ushr 8) and 0xFF).toByte()
        )
        // length-byte = (everything after the magic) length - 1
        val lenByte = (encryptedBody.size + seqBytes.size).toByte()
        val withoutMagicAndChecksum = byteArrayOf(lenByte) + seqBytes + encryptedBody
        val checksum = EllipticCrypto.invertedSumChecksum(withoutMagicAndChecksum)
        return byteArrayOf(MAGIC_HI, MAGIC_LO_MODERN) + withoutMagicAndChecksum + checksum
    }

    /**
     * Sanity-check a received frame.
     *
     * @return null if invalid (bad magic / bad checksum), otherwise [Decoded] with the
     *   pieces ready for further decryption.
     */
    fun parse(frame: ByteArray): Decoded? {
        if (frame.size < 7) return null
        if (frame[0] != MAGIC_HI) return null
        if (frame[1] != MAGIC_LO_MODERN && frame[1] != MAGIC_LO_CLASSIC) return null
        val checksum = frame.copyOfRange(frame.size - 2, frame.size)
        val expected = EllipticCrypto.invertedSumChecksum(frame.copyOfRange(2, frame.size - 2))
        if (!expected.contentEquals(checksum)) return null

        val len = frame[2].toInt() and 0xFF
        val seq = (frame[3].toInt() and 0xFF) or ((frame[4].toInt() and 0xFF) shl 8)
        val body = frame.copyOfRange(5, frame.size - 2)
        return Decoded(
            magicLo = frame[1],
            length = len,
            seq = seq,
            encryptedBody = body
        )
    }

    data class Decoded(
        val magicLo: Byte,
        val length: Int,
        val seq: Int,
        val encryptedBody: ByteArray,
    ) {
        val isModernCrypto: Boolean get() = magicLo == MAGIC_LO_MODERN
    }
}
