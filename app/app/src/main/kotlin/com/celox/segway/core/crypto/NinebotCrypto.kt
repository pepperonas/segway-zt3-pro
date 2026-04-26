package com.celox.segway.core.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Direct port of SHU's `c6.c` (NinebotCrypto). Re-implements the custom
 * AES-CBC-MAC + AES-CTR construction used by Segway-Ninebot scooters that
 * advertise with manufacturer-id `0x434E` ("NC") — i.e. the ZT3 Pro D.
 *
 * Wire format (`5A A5` magic, Case 2 in `c6/b.java`):
 *
 *   `[5A A5] [len] [src dst cmd arg ENC(payload)] [tag(4)] [ctrHi ctrLo]`
 *
 * Counter is shared between TX and RX (both increment on every frame).
 * The very first TX uses the `f()`-obfuscation path (counter 0); the RX
 * response carries a 16-byte token that re-keys the cipher.
 */
class NinebotCrypto(private val scooterName: String) {

    private val salt: ByteArray = byteArrayOf(
        0x97.toByte(), 0xCF.toByte(), 0xB8.toByte(), 0x02,
        0x84.toByte(), 0x41, 0x43, 0xDE.toByte(),
        0x56, 0x00, 0x2B, 0x3B, 0x34, 0x78, 0x0A, 0x5D
    )

    /** Token populated from the scooter's first response (post-handshake key2). */
    private val token = ByteArray(16)

    /** App-side random bytes sent in the init frame, echoed back by the scooter. */
    private val appRandom = ByteArray(16)

    /** Challenge bytes (14) carried in the cmd=0x5B response — must echo back via cmd=0x5D. */
    private val challenge = ByteArray(14)

    /** AES-128 key; re-derived twice — once at construction, once after handshake. */
    private val aesKey = ByteArray(16)

    /** 32-bit shared frame counter. Wraps via Int overflow (matches SHU's `p.a`). */
    private var counter: Int = 0

    /** Stages mirror SHU's L/M/O flags from `ScooterActivity.java`. */
    @Volatile var stageReceivedToken: Boolean = false; private set   // L
    @Volatile var stagePairedKey: Boolean = false; private set       // M
    @Volatile var stageFullyPaired: Boolean = false; private set     // O

    init {
        deriveKey(scooterName.toByteArray(Charsets.UTF_8), salt)
    }

    /**
     * Encrypt a complete inner frame `[5A A5 len src dst cmd arg payload]` to wire bytes.
     * Counter behaviour matches `c6.c#i()` exactly.
     */
    fun encrypt(data: ByteArray): ByteArray {
        val length = data.size
        val out = ByteArray(length + 6)
        System.arraycopy(data, 0, out, 0, 3) // copy `5A A5 len`

        val body = ByteArray(length - 3)
        System.arraycopy(data, 3, body, 0, length - 3)

        val current = counter
        if (current == 0) {
            // First-message path: only `f()`-obfuscation, plus 16-bit invsum CRC.
            val crc = invertedSum(body)
            val obf = fEncrypt(body)
            System.arraycopy(obf, 0, out, 3, obf.size)
            out[length] = 0
            out[length + 1] = 0
            out[length + 2] = crc[0]
            out[length + 3] = crc[1]
            out[length + 4] = 0
            out[length + 5] = 0
            counter = 1
        } else {
            counter = current + 1
            val tag = computeTag(data, counter)
            val enc = ctrCipher(body, counter)
            System.arraycopy(enc, 0, out, 3, enc.size)
            out[length] = tag[0]
            out[length + 1] = tag[1]
            out[length + 2] = tag[2]
            out[length + 3] = tag[3]
            out[length + 4] = ((counter ushr 8) and 0xFF).toByte()
            out[length + 5] = (counter and 0xFF).toByte()

            // Mirror SHU's `i()` lines 301-303: capture our own random app-data echo.
            if (data.size >= 23 &&
                data[0] == 0x5A.toByte() && data[1] == 0xA5.toByte() &&
                data[2] == 0x10.toByte() && data[3] == 0x3E.toByte() &&
                data[5] == 0x5C.toByte() && data[6] == 0x00.toByte()
            ) {
                System.arraycopy(data, 7, appRandom, 0, 16)
            }
        }
        return out
    }

    /**
     * Decrypt a wire frame back to `[5A A5 len src dst cmd arg payload]`. Returns
     * null if size is invalid. Tag verification is not enforced — SHU also doesn't
     * (it warns on mismatch but proceeds), which is what we mimic.
     */
    fun decrypt(data: ByteArray): ByteArray? {
        if (data.size < 9) return null
        val out = ByteArray(data.size - 6)
        System.arraycopy(data, 0, out, 0, 3)

        // 16-bit BE counter from trailer (last two bytes).
        val ctrIncoming = ((data[data.size - 2].toInt() and 0xFF) shl 8) or
            (data[data.size - 1].toInt() and 0xFF)
        // Stitch with our high-16 bits — counter wraps after 0xFFFF.
        val combined = (counter and 0xFFFF.inv()) + ctrIncoming
        val effectiveCounter = combined

        val bodyLen = data.size - 9
        val body = ByteArray(bodyLen)
        System.arraycopy(data, 3, body, 0, bodyLen)

        val plain = if (effectiveCounter == 0) {
            fEncrypt(body) // self-inverse
        } else {
            ctrCipher(body, effectiveCounter)
        }
        System.arraycopy(plain, 0, out, 3, plain.size)

        // Stage 1 (L flag): cmd=0x5B token+challenge response at counter=0.
        // Inner frame: [5A A5 1E rxAddr 3E 5B arg token(16) challenge(14)]
        if (effectiveCounter == 0 &&
            out.size >= 37 &&
            out[0] == 0x5A.toByte() && out[1] == 0xA5.toByte() &&
            out[2] == 0x1E.toByte() &&
            out[4] == 0x3E.toByte() && out[5] == 0x5B.toByte()
        ) {
            System.arraycopy(out, 7, token, 0, 16)
            System.arraycopy(out, 23, challenge, 0, 14)
            deriveKey(scooterName.toByteArray(Charsets.UTF_8), token)
            stageReceivedToken = true
        }

        // Stage 2 (M flag): cmd=0x5C arg=0x01 paired-key confirmation at counter>0.
        // After this, the session key transitions to SHA-1(appRandom + token).
        if (effectiveCounter > 0 &&
            out.size >= 7 &&
            out[0] == 0x5A.toByte() && out[1] == 0xA5.toByte() &&
            out[4] == 0x3E.toByte() && out[5] == 0x5C.toByte() &&
            out[6] == 0x01.toByte()
        ) {
            deriveKey(appRandom, token)
            stagePairedKey = true
        }

        // Stage 3 (O flag): cmd=0x5D arg=0x01 fully-paired confirmation.
        if (effectiveCounter > 0 &&
            out.size >= 7 &&
            out[0] == 0x5A.toByte() && out[1] == 0xA5.toByte() &&
            out[4] == 0x3E.toByte() && out[5] == 0x5D.toByte() &&
            out[6] == 0x01.toByte()
        ) {
            stageFullyPaired = true
            stagePairedKey = true  // SHU also sets M=true here
        }

        counter = effectiveCounter + 1
        return out
    }

    /** Get the 14-byte challenge captured from the cmd=0x5B response (Stage 1). */
    fun snapshotChallenge(): ByteArray = challenge.copyOf()

    /** Reset to fresh-connect state (called on disconnect). */
    fun reset() {
        counter = 0
        stageReceivedToken = false
        stagePairedKey = false
        stageFullyPaired = false
        for (i in token.indices) token[i] = 0
        for (i in appRandom.indices) appRandom[i] = 0
        for (i in challenge.indices) challenge[i] = 0
        deriveKey(scooterName.toByteArray(Charsets.UTF_8), salt)
    }

    /**
     * Stage-1 frame — `getBleRandom()` from `ScooterActivity.java:971`.
     * Body: `[3E txAddr 5B 00]`, plen=0. Triggers the scooter to send back a
     * token+challenge response (`5A A5 1E … 3E 5B …`). Matches the wire bytes
     * of SHU's working sessions (Phase B/E2 in `speed-manip.pcap`) — CRC `62 FF`
     * verifies `txAddr=0x04` for the ZT3 Pro D.
     */
    fun buildGetRandomFrame(txAddr: Byte): ByteArray = byteArrayOf(
        0x5A.toByte(), 0xA5.toByte(), 0x00.toByte(),
        0x3E.toByte(), txAddr,
        0x5B.toByte(), 0x00.toByte()
    )

    /**
     * Stage-2 frame — `o1(R)` from `ScooterActivity.java:752`.
     * Body: `[3E txAddr 5C 00] + 16 random bytes`, plen=0x10. Tells the scooter our
     * appRandom; scooter answers with `5A A5 00 … 5C 01` and both sides re-key to
     * `SHA-1(appRandom + token)`.
     */
    fun buildPairInitFrame(txAddr: Byte): ByteArray = byteArrayOf(
        0x5A.toByte(), 0xA5.toByte(), 0x10.toByte(),
        0x3E.toByte(), txAddr,
        0x5C.toByte(), 0x00.toByte()
    ) + randomBytes(16)

    /** Legacy alias retained for callers that haven't migrated to the explicit stages. */
    fun buildInitFrame(txAddr: Byte): ByteArray = buildGetRandomFrame(txAddr)

    /** Snapshot the token so the caller can persist it across BLE disconnects. */
    fun snapshotToken(): ByteArray = token.copyOf()

    /** Restore a previously-persisted token and re-derive the AES key from it. */
    fun loadToken(persisted: ByteArray) {
        if (persisted.size != 16 || persisted.all { it == 0.toByte() }) return
        System.arraycopy(persisted, 0, token, 0, 16)
        deriveKey(scooterName.toByteArray(Charsets.UTF_8), token)
    }

    fun isHandshakeComplete(): Boolean = counter > 0 && token.any { it != 0.toByte() }

    private fun deriveKey(left: ByteArray, right: ByteArray) {
        val buf = ByteArray(32)
        System.arraycopy(left, 0, buf, 0, minOf(left.size, 12))
        System.arraycopy(right, 0, buf, 16, minOf(right.size, 12))
        val md = MessageDigest.getInstance("SHA-1").digest(buf)
        System.arraycopy(md, 0, aesKey, 0, 16)
    }

    private fun aesEcb(input: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(input)
    }

    /** XOR utility: `out[i] = a[i] ^ b[i]` for `min(a.len, b.len)` bytes. */
    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        val out = a.copyOf()
        val n = minOf(a.size, b.size)
        for (i in 0 until n) out[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        return out
    }

    /**
     * `f()` from `c6.c.f()` — XOR each 16-byte block with `AES_ECB(salt, key)`
     * (a fixed keystream). Self-inverse. Used only on the first frame.
     */
    private fun fEncrypt(input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        val keystream = aesEcb(salt, aesKey)
        var i = 0
        while (i < input.size) {
            val n = minOf(16, input.size - i)
            for (j in 0 until n) out[i + j] = (input[i + j].toInt() xor keystream[j].toInt()).toByte()
            i += n
        }
        return out
    }

    /**
     * `g()` from `c6.c.g()` — AES-CTR with a 16-byte counter block:
     *   `[01][counter32-BE][token[0..8]][...zeros...][block_index]`
     * where `block_index` (the last byte) increments per 16-byte block.
     */
    private fun ctrCipher(input: ByteArray, ctr: Int): ByteArray {
        val out = ByteArray(input.size)
        val ctrBlock = ByteArray(16)
        ctrBlock[0] = 0x01
        ctrBlock[1] = ((ctr ushr 24) and 0xFF).toByte()
        ctrBlock[2] = ((ctr ushr 16) and 0xFF).toByte()
        ctrBlock[3] = ((ctr ushr 8) and 0xFF).toByte()
        ctrBlock[4] = (ctr and 0xFF).toByte()
        System.arraycopy(token, 0, ctrBlock, 5, 8)
        ctrBlock[15] = 0
        var i = 0
        while (i < input.size) {
            ctrBlock[15] = (ctrBlock[15] + 1).toByte()
            val keystream = aesEcb(ctrBlock, aesKey)
            val n = minOf(16, input.size - i)
            for (j in 0 until n) out[i + j] = (input[i + j].toInt() xor keystream[j].toInt()).toByte()
            i += n
        }
        return out
    }

    /**
     * `c()` from `c6.c.c()` — custom CBC-MAC over the full data frame, returning
     * a 4-byte authentication tag. The B0 block layout is non-standard CCM
     * (flag=0x59, length-byte at idx 15).
     */
    private fun computeTag(data: ByteArray, ctr: Int): ByteArray {
        val length = data.size - 3
        val b0 = ByteArray(16)
        b0[0] = 0x59
        b0[1] = ((ctr ushr 24) and 0xFF).toByte()
        b0[2] = ((ctr ushr 16) and 0xFF).toByte()
        b0[3] = ((ctr ushr 8) and 0xFF).toByte()
        b0[4] = (ctr and 0xFF).toByte()
        System.arraycopy(token, 0, b0, 5, 8)
        b0[15] = length.toByte()

        var x = aesEcb(b0, aesKey)

        // First absorbed block: data[0..3] padded into 16 bytes — only first 3 bytes used,
        // rest stays zero (matches SHU's `bArr4 = new byte[16]; arraycopy(bArr, 0, bArr4, 0, 3)`).
        val first = ByteArray(16)
        System.arraycopy(data, 0, first, 0, 3)
        x = aesEcb(xor(first, x), aesKey)

        var idx = 3
        var remaining = length
        while (remaining > 0) {
            val n = minOf(16, remaining)
            val block = ByteArray(16)
            System.arraycopy(data, idx, block, 0, n)
            x = aesEcb(xor(block, x), aesKey)
            remaining -= n
            idx += n
        }

        // S0 block: `[01][counter32][token[0..8]][...]` with byte 15 = 0.
        val s0 = ByteArray(16)
        s0[0] = 0x01
        s0[1] = b0[1]; s0[2] = b0[2]; s0[3] = b0[3]; s0[4] = b0[4]
        System.arraycopy(token, 0, s0, 5, 8)
        s0[15] = 0
        val sEnc = aesEcb(s0, aesKey)

        val tag = ByteArray(4)
        for (i in 0 until 4) tag[i] = (sEnc[i].toInt() xor x[i].toInt()).toByte()
        return tag
    }

    /** 16-bit inverted-sum CRC over the body, returned little-endian. */
    private fun invertedSum(input: ByteArray): ByteArray {
        var sum = 0L
        for (b in input) sum += b.toInt() // signed sum, matches SHU's `j7 += b7`
        val inv = sum.inv()
        return byteArrayOf((inv and 0xFF).toByte(), ((inv shr 8) and 0xFF).toByte())
    }

    companion object {
        private val random = java.security.SecureRandom()

        fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }
    }
}
