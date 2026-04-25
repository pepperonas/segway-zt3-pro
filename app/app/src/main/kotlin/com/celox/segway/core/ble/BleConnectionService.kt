package com.celox.segway.core.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.celox.segway.MainActivity
import com.celox.segway.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps the BLE connection alive while the user
 * navigates between activities or backgrounds the app.
 */
@AndroidEntryPoint
class BleConnectionService : Service() {

    @Inject lateinit var gattClient: GattClient

    inner class LocalBinder : Binder() { val service = this@BleConnectionService }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Idle"))
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID, "Vehicle Connection", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_short_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        gattClient.disconnect()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ble_connection"
        const val NOTIFICATION_ID = 4711

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, BleConnectionService::class.java))
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BleConnectionService::class.java))
        }
    }
}
