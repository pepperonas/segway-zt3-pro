package com.celox.segway.feature.profiles

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.celox.segway.core.profile.SpeedProfileManager
import com.celox.segway.core.util.BleLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Accessibility-service that listens for the stealth unlock-gesture even when
 * the screen is off and the app is in the background.
 *
 * Trigger: **Volume-Down 3× within 2 seconds**.
 *
 * The service only "owns" the gesture if the [SpeedProfileSettings.accessibilityTriggerEnabled]
 * flag is set; otherwise it lets the event propagate to the system as normal
 * (returning false from [onKeyEvent]).
 */
@AndroidEntryPoint
class UnlockAccessibilityService : AccessibilityService() {

    @Inject lateinit var profileManager: SpeedProfileManager
    @Inject lateinit var bleLog: BleLog

    private val pressTimes = ArrayDeque<Long>()
    private val windowMillis = 2_000L
    private val needPresses = 3

    override fun onServiceConnected() {
        super.onServiceConnected()
        bleLog.note("A11y", "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        val now = System.currentTimeMillis()
        pressTimes.addLast(now)
        // Drop stale events
        while (pressTimes.isNotEmpty() && now - pressTimes.first() > windowMillis) {
            pressTimes.removeFirst()
        }

        if (pressTimes.size >= needPresses) {
            pressTimes.clear()
            bleLog.note("A11y", "Vol-Down-3x detected → unlock trigger")
            profileManager.onAccessibilityVolumeTriggered()
            // Consume the event so the volume actually doesn't drop. Returning
            // true tells the system "we handled it".
            return true
        }
        // Don't consume — system still adjusts media volume normally.
        return false
    }
}
