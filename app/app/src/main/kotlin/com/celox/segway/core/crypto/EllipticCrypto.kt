package com.celox.segway.core.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPublicKeySpec
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Crypto primitives for the Ninebot 2nd-gen ("Crypto") pairing protocol.
 *
 * Reverse-engineered from the open-source ScooterHacking Utility (SHU)
 * — see `reverse-engineering/apps/shu/ANALYSIS.md` for context.
 *
 * Stack:
 *   • ECDH on secp256r1 (NIST P-256)
 *   • HKDF with SHA-256 for key derivation
 *   • AES-128/CCM with 24-bit MAC for symmetric encryption
 *   • HMAC-SHA-256 for authentication tags
 */
object EllipticCrypto {

    init {
        // BouncyCastle adds itself idempotently
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private const val CURVE = "secp256r1"
    private val secureRandom = SecureRandom()

    /** Generate a fresh ECDH keypair on P-256. */
    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("ECDH", "BC")
        kpg.initialize(ECNamedCurveTable.getParameterSpec(CURVE), secureRandom)
        return kpg.generateKeyPair()
    }

    /**
     * Encode a public key for BLE transmission.
     * Drops the leading 0x04 marker → 64 raw bytes (X || Y, big-endian).
     */
    fun encodePublicKey(keyPair: KeyPair): ByteArray {
        val publicKey = keyPair.public as ECPublicKey
        val encoded = publicKey.q.getEncoded(false) // 0x04 || X || Y
        return encoded.copyOfRange(1, encoded.size)
    }

    /** Decode a 64-byte raw public key back into an ECPublicKey. */
    fun decodePublicKey(rawXY: ByteArray): ECPublicKey {
        require(rawXY.size == 64) { "Public key must be 64 bytes (X||Y), got ${rawXY.size}" }
        val keyFactory = KeyFactory.getInstance("ECDH", "BC")
        val params = ECNamedCurveTable.getParameterSpec(CURVE)
        val q = params.curve.decodePoint(byteArrayOf(0x04) + rawXY)
        return keyFactory.generatePublic(ECPublicKeySpec(q, params)) as ECPublicKey
    }

    /** ECDH key agreement → 32-byte shared secret. */
    fun ecdh(privateKeyPair: KeyPair, peerPublic: ECPublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH", "BC")
        ka.init(privateKeyPair.private)
        ka.doPhase(peerPublic, true)
        return ka.generateSecret()
    }

    /**
     * HKDF-Expand-and-Extract with SHA-256.
     * Default output length 64 bytes, default empty info string.
     */
    fun hkdf(
        ikm: ByteArray,
        salt: ByteArray = ByteArray(0),
        info: String = "",
        length: Int = 64
    ): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, salt, info.toByteArray(Charsets.UTF_8)))
        return ByteArray(length).also { gen.generateBytes(it, 0, length) }
    }

    /** HMAC-SHA-256 → 32-byte tag. */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * AES-128 / CCM encryption (no padding).
     *
     * NOTE: Java's GCMParameterSpec is reused here for the tag length —
     * Android's BC binding accepts it for CCM mode as well. The default
     * 24-bit MAC matches the SHU decompile.
     *
     * @param key  16-byte AES key
     * @param nonce  12-byte nonce
     * @param plaintext payload
     * @param aad optional additional authenticated data
     * @param tagBits MAC length in bits (default 24, i.e. 3-byte tag)
     */
    fun aesCcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray? = null,
        tagBits: Int = 24,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CCM/NoPadding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagBits, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    /** Reverse of [aesCcmEncrypt]. Returns the plaintext or throws on tag failure. */
    fun aesCcmDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray? = null,
        tagBits: Int = 24,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CCM/NoPadding", "BC")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagBits, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    /** Produce N cryptographically random bytes. */
    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { secureRandom.nextBytes(it) }

    /** SHU's init-hello: 0x00 ++ "blt.4.159" ++ 10 random lowercase alnum chars. */
    fun buildInitHello(): ByteArray {
        val chars = ('a'..'z') + ('0'..'9')
        val random = (1..10).map { chars[Random.nextInt(chars.size)] }.joinToString("")
        return byteArrayOf(0x00) + "blt.4.159".toByteArray() + random.toByteArray()
    }

    /**
     * 16-bit inverted-sum checksum used in the Ninebot frame layer.
     * Matches the original m365 protocol.
     */
    fun invertedSumChecksum(data: ByteArray): ByteArray {
        var sum = 0
        for (b in data) sum = (sum + (b.toInt() and 0xFF)) and 0xFFFF
        val cs = sum.inv() and 0xFFFF
        return byteArrayOf((cs and 0xFF).toByte(), ((cs ushr 8) and 0xFF).toByte())
    }
}
