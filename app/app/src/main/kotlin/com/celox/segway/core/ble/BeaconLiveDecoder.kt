package com.celox.segway.core.ble

import com.celox.segway.core.crypto.EllipticCrypto

/**
 * Decrypts the encrypted payload that the Ninebot Crypto-variant ("NC") beacon
 * embeds in its advertising frame. With the [beaconKey] obtained during pairing,
 * we can read live speed / battery / lock-state without ever connecting GATT.
 *
 * The exact byte layout of the encrypted block is **not formally documented**;
 * what we know from the SHU decompile is:
 *   - The block follows the 6 bytes after the `FF 4E 43` prefix
 *   - It's AES-128/CCM with a beacon-specific 12-byte nonce, 4-byte MAC
 *   - The clear payload at minimum encodes: speed (cm/s), battery%, lock-flag
 *
 * This implementation is *best-effort*: feed it real beacon traffic + a known
 * beaconKey and refine the offsets once a sample plaintext is in hand.
 */
class BeaconLiveDecoder(private val beaconKey: ByteArray) {

    data class Live(
        val speedKmh: Float,
        val batteryPercent: Int,
        val isLocked: Boolean,
    )

    /**
     * @return [Live] data if the frame matches a Ninebot-Crypto advertising
     *   layout AND the supplied [beaconKey] decrypts the payload; null otherwise.
     */
    fun decode(adData: ByteArray): Live? {
        val info = BeaconParser.parse(adData) ?: return null
        if (!info.isCrypto) return null

        val ncOffset = findCryptoBlobOffset(adData) ?: return null
        // Heuristic: encrypted blob is the rest of the manufacturer-specific section,
        // typically 16-20 bytes (12-byte cipher + 4-byte tag).
        if (ncOffset + 16 > adData.size) return null
        val cipher = adData.copyOfRange(ncOffset, minOf(adData.size, ncOffset + 20))

        // Nonce = first 8 of beaconKey + 4 zero bytes (mirrors the GATT nonce shape)
        val nonce = beaconKey.copyOf(8) + ByteArray(4)
        val plain = runCatching {
            EllipticCrypto.aesCcmDecrypt(beaconKey, nonce, cipher, tagBits = 32)
        }.getOrNull() ?: return null

        if (plain.size < 4) return null
        val speedRaw = ((plain[0].toInt() and 0xFF) shl 8) or (plain[1].toInt() and 0xFF)
        val battery = plain[2].toInt() and 0xFF
        val flags = plain[3].toInt() and 0xFF
        return Live(
            speedKmh = speedRaw / 100f,        // cm/s → km/h heuristic
            batteryPercent = battery.coerceIn(0, 100),
            isLocked = (flags and 0x01) != 0
        )
    }

    private fun findCryptoBlobOffset(adData: ByteArray): Int? {
        // Search for the "FF 4E 43" prefix and skip the 6-byte header.
        for (i in 0..adData.size - 9) {
            if (adData[i] == 0xFF.toByte() && adData[i + 1] == 0x4E.toByte() && adData[i + 2] == 0x43.toByte()) {
                return i + 3 + 6
            }
        }
        return null
    }
}
