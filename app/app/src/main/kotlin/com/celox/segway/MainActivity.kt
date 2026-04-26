package com.celox.segway

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.celox.segway.core.data.UserPreferencesRepository
import com.celox.segway.feature.home.ActiveVehicleHolder
import com.celox.segway.ui.SegwayApp
import com.celox.segway.ui.theme.SegwayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPrefs: UserPreferencesRepository
    @Inject lateinit var activeVehicleHolder: ActiveVehicleHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Auto-reconnect to the last-paired vehicle (no-op if nothing stored)
        activeVehicleHolder.tryAutoReconnect()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0, 0),
            navigationBarStyle = SystemBarStyle.auto(0, 0)
        )
        setContent {
            val prefs = userPrefs.flow.collectAsState(initial = UserPreferencesRepository.defaults).value

            // Keep-screen-on toggle from settings → window flag
            DisposableEffect(prefs.keepScreenOn) {
                if (prefs.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            SegwayTheme(
                themeMode = prefs.themeMode,
                useDynamicColor = prefs.dynamicColor
            ) {
                SegwayApp()
            }
        }
    }
}
