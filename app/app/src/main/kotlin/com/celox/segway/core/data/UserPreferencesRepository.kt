package com.celox.segway.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.celox.segway.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore by preferencesDataStore("user_prefs")

/**
 * App-wide UI/UX preferences (theme, units, last-vehicle).
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Snapshot(
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
        val unitsMetric: Boolean,
        val keepScreenOn: Boolean,
        val lastVehicleMac: String?,
    )

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        private val KEY_UNITS = booleanPreferencesKey("units_metric")
        private val KEY_KEEP = booleanPreferencesKey("keep_screen_on")
        private val KEY_LAST = stringPreferencesKey("last_vehicle_mac")

        val defaults = Snapshot(
            themeMode = ThemeMode.System,
            dynamicColor = true,
            unitsMetric = true,
            keepScreenOn = true,
            lastVehicleMac = null,
        )
    }

    val flow: Flow<Snapshot> = context.userDataStore.data.map { prefs ->
        Snapshot(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            dynamicColor = prefs[KEY_DYNAMIC] ?: true,
            unitsMetric = prefs[KEY_UNITS] ?: true,
            keepScreenOn = prefs[KEY_KEEP] ?: true,
            lastVehicleMac = prefs[KEY_LAST],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.userDataStore.edit { it[KEY_THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.userDataStore.edit { it[KEY_DYNAMIC] = enabled }

    suspend fun setUnitsMetric(metric: Boolean) =
        context.userDataStore.edit { it[KEY_UNITS] = metric }

    suspend fun setKeepScreenOn(enabled: Boolean) =
        context.userDataStore.edit { it[KEY_KEEP] = enabled }

    suspend fun setLastVehicle(mac: String?) =
        context.userDataStore.edit { prefs ->
            if (mac == null) prefs.remove(KEY_LAST) else prefs[KEY_LAST] = mac
        }
}
