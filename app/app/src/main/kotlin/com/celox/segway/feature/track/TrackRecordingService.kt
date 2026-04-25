package com.celox.segway.feature.track

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.celox.segway.MainActivity
import com.celox.segway.R
import com.celox.segway.core.data.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that records the user's GPS path while a ride is active.
 *
 * On [stop] (or service destroy) the recorded points are persisted to Room via
 * [TrackRepository], aggregated into a single track row.
 */
@AndroidEntryPoint
class TrackRecordingService : Service() {

    @Inject lateinit var trackRepo: TrackRepository
    @Inject lateinit var userPrefs: UserPreferencesRepository

    private val _points = MutableStateFlow<List<Location>>(emptyList())
    val points: StateFlow<List<Location>> = _points

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private lateinit var locationManager: LocationManager
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedAt: Long = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _points.value = _points.value + location
        }
        @Deprecated("for older Android versions") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()
    inner class LocalBinder : Binder() { val service = this@TrackRecordingService }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (_isRecording.value) return START_STICKY
        startedAt = System.currentTimeMillis()
        _isRecording.value = true
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1_000L, 2f, listener
            )
        } catch (sec: SecurityException) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { locationManager.removeUpdates(listener) } catch (_: Throwable) {}
        persistAndReset()
        super.onDestroy()
    }

    private fun persistAndReset() {
        val pts = _points.value
        if (pts.size < 2) return
        val mac = ioScope.launch {
            val lastMac = userPrefs.flow.first().lastVehicleMac ?: "unknown"
            trackRepo.save(
                vehicleMac = lastMac,
                startedAt = startedAt,
                endedAt = System.currentTimeMillis(),
                points = pts
            )
        }
        _points.value = emptyList()
        _isRecording.value = false
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Track recording", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.track_title))
            .setContentText("Recording…")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "track_recording"
        const val NOTIFICATION_ID = 4712

        fun start(ctx: Context) =
            ctx.startForegroundService(Intent(ctx, TrackRecordingService::class.java))
        fun stop(ctx: Context) =
            ctx.stopService(Intent(ctx, TrackRecordingService::class.java))
    }
}
