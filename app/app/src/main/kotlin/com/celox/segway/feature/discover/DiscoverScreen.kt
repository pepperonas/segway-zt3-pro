package com.celox.segway.feature.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.celox.segway.R

/**
 * Placeholder Discover tab. The original Segway app uses this for community
 * features (riding circles, sun-portal feed). Stubbed for now until we have
 * an actual content backend.
 */
@Composable
fun DiscoverScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_discover)) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text(
                "Community feed will appear here once a content backend is configured.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
