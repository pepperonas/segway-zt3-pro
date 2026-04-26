package com.celox.segway

import android.app.Application
import com.celox.segway.core.profile.SpeedProfileRepository
import com.celox.segway.feature.profiles.StealthVolumeService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SegwayApp : Application() {

    @Inject lateinit var profileRepo: SpeedProfileRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // OSM tile cache and User-Agent (required by tile servers)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = filesDir.resolve("osmdroid-tiles")
        }
        // Start/stop the stealth volume service whenever any
        // background-running feature is toggled. The service uses a
        // MediaSession to capture Vol-Down 3× even with the screen off,
        // and (as a side effect) keeps the app process alive so the
        // SpeedProfileManager's reg-0x5A poll loop can detect custom-
        // button double-taps in the background. We start it if EITHER
        // accessibilityTriggerEnabled OR customButtonDoubleTapEnabled
        // is on.
        appScope.launch {
            profileRepo.flow
                .map { it.accessibilityTriggerEnabled || it.customButtonDoubleTapEnabled }
                .distinctUntilChanged()
                .collect { needed ->
                    if (needed) {
                        StealthVolumeService.start(this@SegwayApp)
                    } else {
                        StealthVolumeService.stop(this@SegwayApp)
                    }
                }
        }
    }
}
