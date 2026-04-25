package com.celox.segway.core.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profileStore by preferencesDataStore("speed_profiles")

@Singleton
class SpeedProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("settings")

    val flow: Flow<SpeedProfileSettings> = context.profileStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<SpeedProfileSettings>(it) }.getOrNull() }
            ?: SpeedProfileSettings()
    }

    suspend fun update(transform: (SpeedProfileSettings) -> SpeedProfileSettings) {
        context.profileStore.edit { prefs ->
            val current = prefs[key]
                ?.let { runCatching { json.decodeFromString<SpeedProfileSettings>(it) }.getOrNull() }
                ?: SpeedProfileSettings()
            prefs[key] = json.encodeToString(transform(current))
        }
    }
}
