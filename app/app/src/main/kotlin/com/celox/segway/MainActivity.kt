package com.celox.segway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import com.celox.segway.core.data.UserPreferencesRepository
import com.celox.segway.ui.SegwayApp
import com.celox.segway.ui.theme.SegwayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPrefs: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0, 0),
            navigationBarStyle = SystemBarStyle.auto(0, 0)
        )
        setContent {
            val prefs = userPrefs.flow.collectAsState(initial = UserPreferencesRepository.defaults).value
            SegwayTheme(
                themeMode = prefs.themeMode,
                useDynamicColor = prefs.dynamicColor
            ) {
                SegwayApp()
            }
        }
    }
}
