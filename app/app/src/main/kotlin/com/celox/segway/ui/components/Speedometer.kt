package com.celox.segway.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3-styled circular speedometer with smooth value animation.
 *
 * @param speedKmh current speed reported by the scooter (BLE telemetry)
 * @param maxSpeedKmh maximum value the gauge can show
 * @param size diameter of the gauge (dp)
 * @param gpsSpeedKmh optional GPS-derived speed shown small below the BLE
 *   number for cross-check; pass `null` if no GPS fix or no permission.
 */
@Composable
fun Speedometer(
    speedKmh: Float,
    maxSpeedKmh: Float = 40f,
    size: androidx.compose.ui.unit.Dp = 240.dp,
    modifier: Modifier = Modifier,
    gpsSpeedKmh: Float? = null,
) {
    val animated by animateFloatAsState(
        targetValue = speedKmh.coerceIn(0f, maxSpeedKmh),
        // Shorter than the previous 600 ms — the dedicated 250 ms speed-only
        // poll feeds new samples 4×/sec, so a long animation would lag visibly
        // behind the actual scooter speed.
        animationSpec = tween(durationMillis = 200),
        label = "speed"
    )
    val sweepAngle = (animated / maxSpeedKmh) * 270f
    val track = MaterialTheme.colorScheme.surfaceVariant
    val arc = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            val topLeft = Offset(stroke.width / 2, stroke.width / 2)
            // Background track
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Active arc
            drawArc(
                color = arc,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f".format(animated),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "km/h",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (gpsSpeedKmh != null) {
                    Text(
                        text = "GPS %.1f".format(gpsSpeedKmh),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}
