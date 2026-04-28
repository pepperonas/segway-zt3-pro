package com.celox.segway.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.core.data.RideSessionEntity
import java.text.DateFormat
import java.util.Date

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TripHistoryScreen(
    onBack: () -> Unit,
    viewModel: TripHistoryViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyState(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        // Aggregate header
        val totalKm = sessions.sumOf { it.distanceKm.toDouble() }.toFloat()
        val totalSec = sessions.sumOf { it.durationSeconds }
        val totalWh = sessions.sumOf { it.energyWh.toDouble() }.toFloat()
        val maxKmh = sessions.maxOfOrNull { it.maxSpeedKmh } ?: 0f

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCard(
                    rideCount = sessions.size,
                    totalKm = totalKm,
                    totalSec = totalSec,
                    totalWh = totalWh,
                    maxKmh = maxKmh,
                )
            }
            items(sessions, key = { it.id }) { s ->
                TripCard(s, onDelete = { viewModel.delete(s.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(
    rideCount: Int,
    totalKm: Float,
    totalSec: Long,
    totalWh: Float,
    maxKmh: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.trips_summary_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                StatBlock(stringResource(R.string.trips_total_rides), "$rideCount", Modifier.weight(1f))
                StatBlock(stringResource(R.string.trips_total_distance), "%.1f km".format(totalKm), Modifier.weight(1f))
                StatBlock(stringResource(R.string.trips_total_time), formatDuration(totalSec), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                StatBlock(stringResource(R.string.trips_total_energy), "%.0f Wh".format(totalWh), Modifier.weight(1f))
                StatBlock(stringResource(R.string.trips_top_speed), "%.1f km/h".format(maxKmh), Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TripCard(s: RideSessionEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDateTime(s.startedAt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.trips_delete))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                StatBlock(stringResource(R.string.vehicle_live_distance), formatDistance(s.distanceKm), Modifier.weight(1f))
                StatBlock(stringResource(R.string.trips_duration), formatDuration(s.durationSeconds), Modifier.weight(1f))
                StatBlock(stringResource(R.string.vehicle_live_max), "%.1f".format(s.maxSpeedKmh), Modifier.weight(1f))
                StatBlock(stringResource(R.string.vehicle_live_avg), "%.1f".format(s.avgSpeedKmh), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                StatBlock(stringResource(R.string.vehicle_live_energy), "%.0f Wh".format(s.energyWh), Modifier.weight(1f))
                StatBlock(
                    stringResource(R.string.trips_battery_used),
                    "${(s.batteryStartPercent - s.batteryEndPercent).coerceAtLeast(0)} %",
                    Modifier.weight(1f)
                )
                Spacer(Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.History, null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.trips_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDistance(km: Float): String {
    val m = km * 1000f
    return if (m < 1000f) "%d m".format(m.toInt()) else "%.2f km".format(km)
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d h %02d m".format(h, m) else "%d m %02d s".format(m, s)
}

private fun formatDateTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
