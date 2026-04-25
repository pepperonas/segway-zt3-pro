package com.celox.segway.core.ble

/**
 * Parses Manufacturer-Specific Data of BLE advertising frames to detect
 * Ninebot/Segway scooters.
 *
 * Two tag prefixes are known:
 *   • FF 4E 42  ("NB" – classic Ninebot, no crypto)
 *   • FF 4E 43  ("NC" – Ninebot Crypto, ZT3/F-series/G3 etc.)
 *
 * The 6 bytes after the prefix encode the model id and feature flags.
 * The exact layout is taken from the SHU decompile (`classes.k`).
 */
object BeaconParser {

    private val PREFIX_NB = byteArrayOf(0xFF.toByte(), 0x4E, 0x42)
    private val PREFIX_NC = byteArrayOf(0xFF.toByte(), 0x4E, 0x43)

    /**
     * Result of a beacon parse. Returns model-id when found, plus a flag whether
     * the device is using the modern crypto-capable advertising format.
     */
    data class BeaconInfo(
        val modelId: Int,
        val isCrypto: Boolean,
        val featureFlag: Int,
    )

    fun parse(adData: ByteArray): BeaconInfo? {
        // Try the crypto prefix first (ZT3 Pro)
        findPrefix(adData, PREFIX_NC)?.let { offset ->
            if (offset + 6 > adData.size) return null
            val sub = adData.copyOfRange(offset, offset + 6)
            val modelId = ((sub[0].toInt() and 0xFF) shl 8) or (sub[1].toInt() and 0xFF)
            val flag = sub[2].toInt() and 0xFF
            return BeaconInfo(modelId = modelId, isCrypto = true, featureFlag = flag)
        }
        findPrefix(adData, PREFIX_NB)?.let { offset ->
            if (offset + 6 > adData.size) return null
            val sub = adData.copyOfRange(offset, offset + 6)
            val modelId = sub[0].toInt() and 0xFF
            val flag = sub[1].toInt() and 0xFF
            return BeaconInfo(modelId = modelId, isCrypto = false, featureFlag = flag)
        }
        return null
    }

    private fun findPrefix(haystack: ByteArray, needle: ByteArray): Int? {
        if (haystack.size < needle.size) return null
        for (i in 0..haystack.size - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) { match = false; break }
            }
            if (match) return i + needle.size
        }
        return null
    }
}
