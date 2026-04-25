package com.celox.segway.feature.track

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celox.segway.R
import com.celox.segway.core.data.TrackEntity
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackScreen(vm: TrackViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    var recording by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.track_title)) },
                actions = {
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Outlined.Map, null)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(if (recording) stringResource(R.string.track_stop) else stringResource(R.string.track_start)) },
                icon = { Icon(if (recording) Icons.Filled.Stop else Icons.Filled.PlayArrow, null) },
                onClick = {
                    if (recording) TrackRecordingService.stop(ctx) else TrackRecordingService.start(ctx)
                    recording = !recording
                },
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Map
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    org.osmdroid.config.Configuration.getInstance().load(
                        context, PreferenceManager.getDefaultSharedPreferences(context)
                    )
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(52.5163, 13.3777))
                    }
                }
            )

            // History overlay
            if (showHistory) {
                HistoryOverlay(
                    tracks = tracks,
                    onDelete = vm::delete,
                    onDismiss = { showHistory = false }
                )
            }
        }
    }
}

@Composable
private fun HistoryOverlay(
    tracks: List<TrackEntity>,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val df = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .fillMaxHeight(0.6f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.track_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Map, null) }
        }
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.track_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Card
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(df.format(Date(track.startedAt)), fontWeight = FontWeight.SemiBold)
                            val durationMin = ((track.endedAt ?: track.startedAt) - track.startedAt) / 60_000
                            Text(
                                "%.2f km • ⌀ %.1f km/h • max %.1f km/h • %d min".format(
                                    track.distanceMeters / 1000f,
                                    track.avgSpeedKmh,
                                    track.maxSpeedKmh,
                                    durationMin
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDelete(track.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
