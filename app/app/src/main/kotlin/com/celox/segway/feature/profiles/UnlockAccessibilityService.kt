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

    private val downTimes = ArrayDeque<Long>()
    private val upTimes = ArrayDeque<Long>()
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
        val now = System.currentTimeMillis()
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> handleTriple(downTimes, now, "Vol-Down 3× → re-lock 22") {
                profileManager.onAccessibilityVolumeDownTriggered()
            }
            KeyEvent.KEYCODE_VOLUME_UP -> handleTriple(upTimes, now, "Vol-Up 3× → unlock 40") {
                profileManager.onAccessibilityVolumeUpTriggered()
            }
            else -> false
        }
    }

    private fun handleTriple(
        queue: ArrayDeque<Long>,
        now: Long,
        logMsg: String,
        action: () -> Unit,
    ): Boolean {
        queue.addLast(now)
        while (queue.isNotEmpty() && now - queue.first() > windowMillis) queue.removeFirst()
        if (queue.size < needPresses) return false
        queue.clear()
        bleLog.note("A11y", logMsg)
        action()
        return true // consume so the volume slider doesn't move
    }
}
