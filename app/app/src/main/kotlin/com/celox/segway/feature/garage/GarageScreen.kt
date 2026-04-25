package com.celox.segway.feature.garage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ElectricMoped
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    onBack: () -> Unit,
    onPairClick: () -> Unit,
    vm: GarageViewModel = hiltViewModel(),
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<GarageViewModel.GarageEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<GarageViewModel.GarageEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garage") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPairClick,
                icon = { Icon(Icons.Outlined.ElectricMoped, null) },
                text = { Text("Add scooter") }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(onPairClick, modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.mac }) { entry ->
                GarageRow(
                    entry = entry,
                    onActivate = { vm.activate(entry) },
                    onRename = { renameTarget = entry },
                    onDelete = { deleteTarget = entry }
                )
            }
        }
    }

    renameTarget?.let { entry ->
        var name by remember { mutableStateOf(entry.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(entry.mac, name); renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Unpair ${entry.displayName}?") },
            text = { Text("This deletes the stored pairing tokens; you'll need a fresh handshake to reconnect.") },
            confirmButton = {
                TextButton(onClick = { vm.unpair(entry.mac); deleteTarget = null }) { Text("Unpair") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyState(onPairClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.ElectricMoped,
                null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(16.dp))
            Text("No scooters paired yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            Text(
                "Tap the + button to scan and pair your ZT3 Pro.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GarageRow(
    entry: GarageViewModel.GarageEntry,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onActivate() },
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (entry.isActive) Icons.Outlined.CheckCircle else Icons.Outlined.ElectricMoped,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${entry.model} • ${entry.mac}" + (entry.regionCode?.let { " • ${it}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, null) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
