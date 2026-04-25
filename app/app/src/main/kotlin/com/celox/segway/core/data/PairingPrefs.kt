package com.celox.segway.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairingDataStore by preferencesDataStore("pairing")

/**
 * Stores per-MAC ECDH-derived secrets needed to resume a session without
 * a fresh handshake.
 *
 * Layout: a single JSON list under the "ellipticConfiguration" key, mirroring
 * the SHU app's own SharedPreferences structure.
 */
@Singleton
class PairingPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Serializable
    data class Config(
        val ssid: String,
        val deviceInfo: String? = null,   // base64
        val deviceToken: String? = null,
        val beaconKey: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val key = stringPreferencesKey("ellipticConfiguration")

    val all: Flow<List<Config>> = context.pairingDataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<Config>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun get(mac: String): Config? = all.first().firstOrNull { it.ssid.equals(mac, true) }

    suspend fun save(
        mac: String,
        deviceInfo: ByteArray,
        deviceToken: ByteArray,
        beaconKey: ByteArray,
    ) {
        val newCfg = Config(
            ssid = mac,
            deviceInfo = android.util.Base64.encodeToString(deviceInfo, android.util.Base64.NO_WRAP),
            deviceToken = android.util.Base64.encodeToString(deviceToken, android.util.Base64.NO_WRAP),
            beaconKey = android.util.Base64.encodeToString(beaconKey, android.util.Base64.NO_WRAP),
        )
        context.pairingDataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<Config>>(prefs[key] ?: "[]") }
                .getOrDefault(emptyList()).toMutableList()
            val idx = current.indexOfFirst { it.ssid.equals(mac, true) }
            if (idx >= 0) current[idx] = newCfg else current.add(newCfg)
            prefs[key] = json.encodeToString(current.toList())
        }
    }

    suspend fun remove(mac: String) {
        context.pairingDataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<Config>>(prefs[key] ?: "[]") }
                .getOrDefault(emptyList()).filterNot { it.ssid.equals(mac, true) }
            prefs[key] = json.encodeToString(current)
        }
    }

    fun decodeBase64(b64: String): ByteArray =
        android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
}
