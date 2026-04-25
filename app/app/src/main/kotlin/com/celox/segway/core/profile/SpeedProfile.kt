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
    /** Master switch for the Vol-Down-3x stealth trigger from the AccessibilityService. */
    val accessibilityTriggerEnabled: Boolean = false,
    /** Optional auto-revert to [boot] after this many minutes in unlock mode. 0 = off. */
    val autoRevertMinutes: Int = 0,
    /**
     * If true, the [boot] profile is automatically applied 1.5 s after every
     * successful connect. Default OFF — too easy to accidentally clamp the
     * scooter to a low limit otherwise.
     */
    val autoApplyOnConnect: Boolean = false,
)
