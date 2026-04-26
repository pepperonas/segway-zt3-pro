package com.celox.segway.feature.profiles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.VolumeProviderCompat
import com.celox.segway.MainActivity
import com.celox.segway.R
import com.celox.segway.core.profile.SpeedProfileManager
import com.celox.segway.core.util.BleLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that captures Volume-Down key-events even when the screen
 * is off. The vanilla [UnlockAccessibilityService] only fires `onKeyEvent` while
 * the input dispatcher routes keys to userspace — which it stops doing once the
 * device sleeps.
 *
 * Workaround: register a [MediaSessionCompat] with `FLAG_HANDLES_MEDIA_BUTTONS`
 * and request audio-focus. The system then routes media-button events
 * (including volume keys) to our session callback. A foreground notification
 * (with `connectedDevice` type) keeps the service alive across doze / standby.
 */
@AndroidEntryPoint
class StealthVolumeService : android.app.Service() {

    @Inject lateinit var profileManager: SpeedProfileManager
    @Inject lateinit var bleLog: BleLog

    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val downTimes = ArrayDeque<Long>()
    private val upTimes = ArrayDeque<Long>()
    private val windowMillis = 2_000L
    private val needPresses = 3

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        acquireWakeLock()
        installMediaSession()
        bleLog.note("Stealth", "service started — listening for Vol-Down (screen-off OK)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY so Android revives us if killed.
        return START_STICKY
    }

    override fun onDestroy() {
        try { mediaSession?.isActive = false } catch (_: Throwable) {}
        try { mediaSession?.release() } catch (_: Throwable) {}
        try { wakeLock?.release() } catch (_: Throwable) {}
        audioFocusRequest?.let {
            (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(it)
        }
        bleLog.note("Stealth", "service stopped")
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "segway:stealth-volume"
        ).apply { acquire(/*timeout*/ 8 * 60 * 60 * 1000L) }
    }

    private fun installMediaSession() {
        // VolumeProvider intercepts volume key adjustments at the OS level.
        // When `setPlaybackToRemote` is called on an *active* media session,
        // hardware Vol-Up/Down keys are routed to `onAdjustVolume` regardless
        // of screen state — this is the only reliable way to catch them with
        // the screen off, since the InputDispatcher doesn't deliver to
        // userspace AccessibilityServices when the device is sleeping.
        val volumeProvider = object : VolumeProviderCompat(
            /* volumeControl = */ VOLUME_CONTROL_RELATIVE,
            /* maxVolume = */ 100,
            /* currentVolume = */ 50
        ) {
            override fun onAdjustVolume(direction: Int) {
                // direction: -1 = Vol-Down (= lock to 22), +1 = Vol-Up (= unlock to 40),
                // 0 = mute toggle (ignored).
                when {
                    direction < 0 -> registerVolumeDownPress()
                    direction > 0 -> registerVolumeUpPress()
                }
                // Never modify currentVolume — keep at 50 so the OS thinks nothing
                // happened (no real volume change leaks to other media apps).
            }
        }

        val session = MediaSessionCompat(this, "SegwayStealthVolumeSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .build()
            )
            setPlaybackToRemote(volumeProvider)
            isActive = true
        }
        mediaSession = session

        // Audio focus: required on some ROMs for media-button routing. We grab
        // a brief focus, hold it, and never play actual sound. Other media apps
        // duck around us — that's the cost of stealth.
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { /* ignore — we don't actually play */ }
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun registerVolumeDownPress() {
        val now = System.currentTimeMillis()
        downTimes.addLast(now)
        while (downTimes.isNotEmpty() && now - downTimes.first() > windowMillis) {
            downTimes.removeFirst()
        }
        if (downTimes.size >= needPresses) {
            downTimes.clear()
            bleLog.note("Stealth", "Vol-Down-3× → re-lock (22 km/h)")
            profileManager.onAccessibilityVolumeDownTriggered()
        }
    }

    private fun registerVolumeUpPress() {
        val now = System.currentTimeMillis()
        upTimes.addLast(now)
        while (upTimes.isNotEmpty() && now - upTimes.first() > windowMillis) {
            upTimes.removeFirst()
        }
        if (upTimes.size >= needPresses) {
            upTimes.clear()
            bleLog.note("Stealth", "Vol-Up-3× → unlock (40 km/h)")
            profileManager.onAccessibilityVolumeUpTriggered()
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "Stealth Unlock", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Hört auf Vol-Down 3× während der Bildschirm aus ist"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm?.createNotificationChannel(channel)

        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Stealth-Unlock aktiv")
            .setContentText("Vol-Down 3× zum Entsperren — auch bei Screen aus")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        // Bump version when importance changes — Android caches channel importance
        // and ignores subsequent changes for the same id.
        private const val CHANNEL_ID = "stealth_unlock_v2"
        private const val NOTIF_ID = 0xC0DE

        fun start(context: Context) {
            val intent = Intent(context, StealthVolumeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StealthVolumeService::class.java))
        }
    }
}
