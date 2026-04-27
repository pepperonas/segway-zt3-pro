package com.celox.segway.core.profile

import kotlinx.serialization.Serializable

/**
 * A user-configurable speed-limit preset.
 *
 * The user has 5 slots in total:
 *  - 1 "boot" profile that auto-applies right after pairing connects
 *  - up to 3 "quick action" profiles surfaced as taps on the Vehicle screen
 *  - 1 "unlock" profile (the one behind PIN / Volume-Down-3x stealth trigger)
 *
 * All four are just [SpeedProfile] instances; their role is decided by the
 * [SpeedProfileSettings] container, not by anything stored on the profile itself.
 */
@Serializable
data class SpeedProfile(
    val id: String,
    val label: String,
    val speedKmh: Int,
)

@Serializable
data class SpeedProfileSettings(
    val boot: SpeedProfile = SpeedProfile("boot", "City", 22),
    val quickActions: List<SpeedProfile> = listOf(
        SpeedProfile("walk", "Walk", 6),
        SpeedProfile("city", "City", 22),
        SpeedProfile("cruise", "Cruise", 28),
    ),
    val unlock: SpeedProfile = SpeedProfile("sport", "Sport", 40),
    /** PIN required to apply the unlock profile via in-app button. Empty = no PIN. */
    val unlockPin: String = "",
    /**
     * Master switch for the Vol-Down-3x stealth trigger from the
     * AccessibilityService. **Default ON** so the gesture works as soon as the
     * user enables the system-level accessibility-service in Android Settings.
     * (Apps can't enable that programmatically — only the user can.)
     */
    val accessibilityTriggerEnabled: Boolean = true,
    /** Optional auto-revert to [boot] after this many minutes in unlock mode. 0 = off. */
    val autoRevertMinutes: Int = 0,
    /**
     * If true, the [boot] profile is automatically applied 1.5 s after every
     * successful connect. **Default ON** — the whole point of this app is to
     * keep the scooter locked at the boot speed unless the user explicitly
     * unlocks. Switch it off only if you want to control everything manually.
     */
    val autoApplyOnConnect: Boolean = true,
    /**
     * If true, double-tapping the scooter's custom button (the Walk-mode
     * toggle button on the dashboard, ≤ 1.5 s between taps) re-applies the
     * [boot] profile (= locks to 22 km/h by default). Single tap stays the
     * normal Walk-toggle. Off by default — when on, the app fast-polls reg
     * 0x5A (VCU_DRIVE_MODE) at 250 ms to catch real double-taps.
     */
    val customButtonDoubleTapEnabled: Boolean = false,
)
