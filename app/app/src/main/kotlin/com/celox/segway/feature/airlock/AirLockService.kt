package com.celox.segway.feature.airlock

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
import com.celox.segway.core.ble.BleScanner
import com.celox.segway.core.data.PairingPrefs
import com.celox.segway.core.data.UserPreferencesRepository
import com.celox.segway.core.util.BleLog
import com.celox.segway.core.vehicle.VehicleCommand
import com.celox.segway.feature.home.ActiveVehicleHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that auto-unlocks the active vehicle when its BLE
 * advertising frame is detected with sufficient signal strength (= user
 * is physically close).
 *
 * Behaviour:
 *  - Scans continuously (LOW_LATENCY)
 *  - For each frame from the bound MAC: take the running EMA of RSSI
 *  - If EMA crosses [rssiThresholdDbm] AND vehicle is locked → send Unlock
 *  - After unlocking, suppress further unlock attempts for 30 seconds
 *
 * The threshold defaults to -65 dBm (≈ 1-2 m). Adjust via [setSensitivity].
 */
@AndroidEntryPoint
class AirLockService : Service() {

    @Inject lateinit var scanner: BleScanner
    @Inject lateinit var holder: ActiveVehicleHolder
    @Inject lateinit var pairingPrefs: PairingPrefs
    @Inject lateinit var userPrefs: UserPreferencesRepository
    @Inject lateinit var bleLog: BleLog

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private var rssiThresholdDbm = -65
    private var rssiEma: Double = -100.0
    private var lastUnlockTimestamp = 0L

    override fun onBind(intent: Intent?): IBinder = LocalBinder()
    inner class LocalBinder : Binder() { val service = this@AirLockService }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Watching…"))
        bleLog.note("AirLock", "service started, threshold=${rssiThresholdDbm} dBm")
        startWatching()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getIntExtra(EXTRA_THRESHOLD, rssiThresholdDbm)?.let { setSensitivity(it) }
        return START_STICKY
    }

    fun setSensitivity(rssiDbm: Int) {
        rssiThresholdDbm = rssiDbm.coerceIn(-90, -40)
        bleLog.note("AirLock", "threshold updated to ${rssiThresholdDbm} dBm")
    }

    private fun startWatching() {
        job?.cancel()
        job = ioScope.launch {
            val activeMac = userPrefs.flow.first().lastVehicleMac
                ?: run {
                    bleLog.note("AirLock", "no active vehicle, stopping")
                    stopSelf()
                    return@launch
                }
            bleLog.note("AirLock", "watching $activeMac")
            scanner.scan().collect { device ->
                if (!device.address.equals(activeMac, ignoreCase = true)) return@collect
                rssiEma = 0.7 * rssiEma + 0.3 * device.rssi.toDouble()
                if (rssiEma > rssiThresholdDbm && shouldFire()) {
                    fireUnlock()
                }
            }
        }
    }

    private fun shouldFire(): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastUnlockTimestamp
        return elapsed > 30_000L
    }

    private fun fireUnlock() {
        val v = holder.activeVehicle.value ?: return
        bleLog.note("AirLock", "RSSI ema=${"%.1f".format(rssiEma)} → Unlock")
        lastUnlockTimestamp = System.currentTimeMillis()
        ioScope.launch { v.execute(VehicleCommand.Unlock) }
    }

    override fun onDestroy() {
        job?.cancel()
        bleLog.note("AirLock", "service stopped")
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AirLock", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String): Notification {
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirLock")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "airlock"
        const val NOTIFICATION_ID = 4713
        const val EXTRA_THRESHOLD = "threshold_dbm"

        fun start(ctx: Context, thresholdDbm: Int = -65) {
            val intent = Intent(ctx, AirLockService::class.java)
                .putExtra(EXTRA_THRESHOLD, thresholdDbm)
            ctx.startForegroundService(intent)
        }

        fun stop(ctx: Context) =
            ctx.stopService(Intent(ctx, AirLockService::class.java))
    }
}
