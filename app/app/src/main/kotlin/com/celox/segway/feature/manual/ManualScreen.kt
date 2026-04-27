package com.celox.segway.feature.manual

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.text.HtmlCompat
import androidx.compose.ui.unit.dp
import com.celox.segway.R

@Composable
fun ManualScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openBatterySettings() {
        // Try the per-app battery-optimisation prompt first; fall back to
        // the system-wide list if that's unavailable on this OEM build.
        val pkg = context.packageName
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            },
        )
        for (i in intents) {
            runCatching { context.startActivity(i) }.onSuccess { return }
        }
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manual_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DisclaimerCard()

            // === FAQ ===
            SectionHeader(stringResource(R.string.manual_section_faq))
            FaqItem(
                stringResource(R.string.manual_faq_q_repair),
                stringResource(R.string.manual_faq_a_repair),
            )
            FaqItem(
                stringResource(R.string.manual_faq_q_screenoff),
                stringResource(R.string.manual_faq_a_screenoff),
            )
            FaqItem(
                stringResource(R.string.manual_faq_q_profiles_vs_settings),
                stringResource(R.string.manual_faq_a_profiles_vs_settings),
            )
            FaqItem(
                stringResource(R.string.manual_faq_q_voldown),
                stringResource(R.string.manual_faq_a_voldown),
            )
            FaqItem(
                stringResource(R.string.manual_faq_q_walkforce),
                stringResource(R.string.manual_faq_a_walkforce),
            )
            FaqItem(
                stringResource(R.string.manual_faq_q_missing_settings),
                stringResource(R.string.manual_faq_a_missing_settings),
            )
            FaqItem(
                stringResource(R.string.manual_faq_q_legal),
                stringResource(R.string.manual_faq_a_legal),
            )

            // === Battery / background restrictions ===
            SectionHeader(stringResource(R.string.manual_section_battery))
            Text(
                stringResource(R.string.manual_battery_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(
                onClick = ::openBatterySettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.manual_battery_open_settings))
            }
            // Auto-detect OEM and put their entry first to save the user a scroll.
            val manufacturer = remember { Build.MANUFACTURER.lowercase() }
            val oemEntries = oemList(manufacturer)
            oemEntries.forEach { entry ->
                ExpandableCard(
                    title = stringResource(entry.titleRes),
                    initiallyExpanded = entry.matchesCurrentDevice,
                ) {
                    Text(
                        stringResource(entry.bodyRes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl("https://dontkillmyapp.com") }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.manual_battery_dontkillmyapp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // === Changelog ===
            SectionHeader(stringResource(R.string.manual_section_changelog))
            Text(
                stringResource(R.string.manual_changelog_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Changelog.entries.forEachIndexed { idx, entry ->
                ExpandableCard(
                    title = "v${entry.version} · ${entry.date}",
                    initiallyExpanded = idx == 0, // newest open by default
                ) {
                    entry.highlights.forEach { line ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                "• ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.manual_disclaimer_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            val htmlBody = stringResource(R.string.manual_disclaimer_body)
            // HtmlCompat → CharSequence keeps <b>...</b> as a SpanStyle,
            // Material3 Text renders Spanned correctly via toString-based
            // fallback (bold is lost but layout is fine; the wording itself
            // already carries the emphasis).
            val plain = remember(htmlBody) {
                HtmlCompat.fromHtml(htmlBody, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
            }
            Text(
                plain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun FaqItem(question: String, answer: String) {
    ExpandableCard(title = question) {
        val plain = remember(answer) {
            HtmlCompat.fromHtml(answer, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        }
        Text(
            plain,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ExpandableCard(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

private data class OemEntry(
    val match: List<String>,            // Build.MANUFACTURER prefixes that match
    val titleRes: Int,
    val bodyRes: Int,
    val matchesCurrentDevice: Boolean = false,
)

/**
 * Returns OEM entries with the current device's match floated to the top
 * and pre-expanded.
 */
private fun oemList(manufacturer: String): List<OemEntry> {
    val all = listOf(
        OemEntry(listOf("samsung"), R.string.manual_oem_samsung_title, R.string.manual_oem_samsung_body),
        OemEntry(listOf("xiaomi", "redmi", "poco"), R.string.manual_oem_xiaomi_title, R.string.manual_oem_xiaomi_body),
        OemEntry(listOf("huawei"), R.string.manual_oem_huawei_title, R.string.manual_oem_huawei_body),
        OemEntry(listOf("oneplus", "oppo", "realme"), R.string.manual_oem_oneplus_title, R.string.manual_oem_oneplus_body),
        OemEntry(listOf("vivo"), R.string.manual_oem_vivo_title, R.string.manual_oem_vivo_body),
        OemEntry(listOf("honor"), R.string.manual_oem_honor_title, R.string.manual_oem_honor_body),
        OemEntry(listOf("google"), R.string.manual_oem_pixel_title, R.string.manual_oem_pixel_body),
    )
    val matchedIdx = all.indexOfFirst { entry -> entry.match.any { manufacturer.startsWith(it) } }
    return if (matchedIdx == -1) {
        all
    } else {
        val matched = all[matchedIdx].copy(matchesCurrentDevice = true)
        listOf(matched) + all.filterIndexed { i, _ -> i != matchedIdx }
    }
}
