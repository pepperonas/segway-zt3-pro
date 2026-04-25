package com.celox.segway.feature.profiles

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Helpers around the Android Accessibility-Settings page. Apps cannot enable
 * the service themselves — only the user can, via the system settings — so
 * we just need to (a) check the flag, (b) deep-link the user there.
 */
object AccessibilityHelper {

    fun isOurServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, UnlockAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
