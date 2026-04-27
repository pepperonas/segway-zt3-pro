package com.celox.segway.feature.mine

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ElectricMoped
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.celox.segway.BuildConfig
import com.celox.segway.R

@Composable
fun MineScreen(
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onGarageClick: () -> Unit,
    onFirmwareClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onAirLockClick: () -> Unit,
    onProfilesClick: () -> Unit,
    onScooterSettingsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val feedbackSubject = stringResource(R.string.feedback_email_subject)
    val feedbackBody = stringResource(
        R.string.feedback_email_body_template,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE,
        "${Build.MANUFACTURER} ${Build.MODEL}",
        "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )
    val noEmailMessage = stringResource(R.string.feedback_no_email_app)

    val onFeedbackClick: () -> Unit = {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@celox.io"))
            putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
            putExtra(Intent.EXTRA_TEXT, feedbackBody)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, noEmailMessage, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_mine)) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_speed_profiles_title)) },
                supportingContent = { Text(stringResource(R.string.mine_speed_profiles_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.Speed, null) },
                modifier = Modifier.clickable { onProfilesClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_scooter_settings_title)) },
                supportingContent = { Text(stringResource(R.string.mine_scooter_settings_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.Tune, null) },
                modifier = Modifier.clickable { onScooterSettingsClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_garage_title)) },
                supportingContent = { Text(stringResource(R.string.mine_garage_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.ElectricMoped, null) },
                modifier = Modifier.clickable { onGarageClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_firmware_title)) },
                supportingContent = { Text(stringResource(R.string.mine_firmware_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.SystemUpdate, null) },
                modifier = Modifier.clickable { onFirmwareClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_airlock_title)) },
                supportingContent = { Text(stringResource(R.string.mine_airlock_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.LockOpen, null) },
                modifier = Modifier.clickable { onAirLockClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_diagnostics_title)) },
                supportingContent = { Text(stringResource(R.string.mine_diagnostics_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.BugReport, null) },
                modifier = Modifier.clickable { onDiagnosticsClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.mine_feedback_title)) },
                supportingContent = { Text(stringResource(R.string.mine_feedback_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.Email, null) },
                modifier = Modifier.clickable(onClick = onFeedbackClick)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_title)) },
                leadingContent = { Icon(Icons.Outlined.Settings, null) },
                modifier = Modifier.clickable { onSettingsClick() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                modifier = Modifier.clickable { onAboutClick() }
            )
        }
    }
}
