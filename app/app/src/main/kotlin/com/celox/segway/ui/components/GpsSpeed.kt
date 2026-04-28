package com.celox.segway.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Subscribes to GPS-provider location updates while the calling composable is
 * in the composition, and returns the latest speed in km/h.
 *
 * Returns `null` if:
 * - location permission is not granted yet (caller should not request it
 *   from inside the speedometer — this is a passive observer);
 * - the GPS provider is unavailable;
 * - or no fix has been received yet.
 *
 * Updates are throttled to 1 s / 0 m, which is enough granularity for a
 * head-up reading next to the BLE speed and stays well under battery limits.
 */
@Composable
fun rememberGpsSpeedKmh(): Float? {
    val ctx = LocalContext.current
    var speed by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(ctx) {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val listener = LocationListener { loc ->
            speed = if (loc.hasSpeed()) loc.speed * 3.6f else null
        }
        if (granted && lm != null) {
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1_000L, 0f, listener
                )
            } catch (_: SecurityException) {
                // Race: permission revoked between check and call. Stay silent.
            } catch (_: IllegalArgumentException) {
                // No GPS provider on this device.
            }
        }
        onDispose {
            try { lm?.removeUpdates(listener) } catch (_: Throwable) {}
        }
    }
    return speed
}
