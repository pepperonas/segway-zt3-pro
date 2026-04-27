package com.celox.segway.feature.manual

/**
 * In-app changelog. Kept as Kotlin instead of an asset/markdown file so the
 * compiler enforces the structure and IDE search-and-rename works across
 * versions.
 *
 * **CONTRIBUTING**: every release that ships a user-visible change adds a new
 * [Entry] at the **top** of [entries]. Bug-fix-only point-releases that have
 * no user-visible behaviour change can be skipped. The version+code must
 * stay in sync with `app/build.gradle.kts`.
 *
 * Entry text stays in English — the changelog is technical, the audience is
 * developers/power-users, and translating every release note doubles the
 * maintenance load with little upside. Section headings ("Manual" → see
 * `strings.xml`) ARE translated; bullet points are not.
 */
object Changelog {

    data class Entry(
        val version: String,
        val versionCode: Int,
        val date: String,        // ISO yyyy-MM-dd
        val highlights: List<String>,
    )

    val entries: List<Entry> = listOf(
        Entry(
            version = "0.2.3",
            versionCode = 11,
            date = "2026-04-28",
            highlights = listOf(
                "3× right blinker → lock to 22 km/h: activate the right turn signal three times within 3 s while connected and the boot profile is re-applied. Identifying the register was done via the Diagnostics Reg-Hunt tool — turning the right blinker on flipped exactly bit 1 of VCU 0xFF, the signature of a state register. Watcher polls 0xFF every 200 ms and edge-detects bit-1 transitions 0→1.",
                "Per-activation feedback: snackbar \"Blinker N/3\" on every detected event so you can verify the gesture is registering.",
                "VehicleState surfaces blinkerLeftOn / blinkerRightOn / indicatorStatusRaw — left-blinker bit (assumed bit 0) is mirror-from-convention, not yet field-verified.",
            ),
        ),
        Entry(
            version = "0.2.2",
            versionCode = 10,
            date = "2026-04-28",
            highlights = listOf(
                "Removed the 3× brake-trigger feature: VCU 0xD5 (the documented brake-status register) was confirmed to stay stuck at 0x0000 on this ZT3 hardware regardless of brake input. Speed-delta fallback was also too noisy in field tests to ship.",
                "Right-blinker trigger pending: the register that exposes turn-signal state on ZT3 is not in any decompiled source we have. Use Diagnostics → Reg-Hunt to identify it (sweep VCU 0x00..0xFF before + after blinker-on, diff). Once known, the watcher will be wired in the next release.",
                "Renamed Diagnostics chip \"Btn-Hunt\" → \"Reg-Hunt\" and reworded the phase banners so the sweep tool reads as a generic register-hunting helper (works for any input: custom button, blinker, brake, etc).",
            ),
        ),
        Entry(
            version = "0.2.1",
            versionCode = 9,
            date = "2026-04-28",
            highlights = listOf(
                "3× brake → lock to 22 km/h: pull either brake lever 3 times within 3 s and the boot profile is re-applied. Watcher polls VCU 0xD5 (cruise/throttle/brake-status, value 0x0000 = brake) every 200 ms while enabled. Toggle in Speed profiles, parallel to the existing custom-button double-tap.",
                "Battery deep-detail: pack serial, manufacture date, series-cell count, rated voltage, designed capacity, and (while charging) time-to-full are now read from BMS 0x02 / 0x0A / 0x10 / 0x11 / 0x13 / 0x94 and shown in a new \"Pack identification\" section on the Battery-Detail screen.",
                "MCU identification: part number, mode, and pack voltage from the MCU's perspective added to pollPlan (MCU 0x10 / 0x83 / 0x8F).",
                "Cruise / throttle / brake status (VCU 0xD5) parsed properly: cruise-active and brake-applied flags now reflect real scooter state instead of being write-only.",
                "About screen: license card switched to proprietary (was incorrectly labelled \"MIT\"); no-liability card; legal links (Impressum + Datenschutz on celox.io).",
            ),
        ),
        Entry(
            version = "0.2.0",
            versionCode = 8,
            date = "2026-04-28",
            highlights = listOf(
                "Roller-Einstellungen: 22 writable settings extracted byte-perfect from SHU's bootstrap.zip — toggles for Traction Control, Imperial Units, Park-on-Slope, Boost, Indicator Sound, App Tone, Alarm, Walk/Drive/Sport mode enables, Auto Headlight, Front Position Lamp, UnderGlow, Breathing Charging Light, Folding behaviour; sliders for Start Speed, Auto-Shutdown, Charge Limit; enums for Taillight Mode, Acceleration Level, KERS / Motor Brake, Custom Button Action.",
                "Bitfield-write guard: toggling Alarm no longer flickers Indicator-Sound off-then-on (firmware emits an intermediate value during settle; we now ignore it for 3 s).",
                "Custom-button enum dropdown with the actual zt3.json option-map (custom_key value 4 is firmware-reserved — gap preserved). Off-by-one on acc_level fixed.",
                "Battery-detail screen (tap the battery card on Home): SoC, BMS-FW, Charge-Threshold (BMS 0x82), live V·I·W, Range@Full, all 13 cells with min/max diff.",
                "Per-MAC resume: cryptoRandom is persisted, app no longer prompts for fresh pair on every restart. \"Forget\" in Garage now actually forces a fresh pair.",
                "Auto-reconnect watchdog (8 s polling) + foreground service so BLE survives screen-off; offline banner only after 4 s of true disconnection.",
                "Vehicle-state cache: home screen shows last known telemetry immediately on cold-start instead of zeros until the first poll cycle finishes.",
                "Lock-icon on the speedometer banner is now tappable (one-tap lock/unlock with PIN dialog if configured).",
                "Scooter icon in bottom nav (was generic dashboard).",
                "About screen: author info card (name, year, celox.io, github) with tap-to-open links.",
                "Mine: \"Feedback / report bug\" entry (opens email to support@celox.io with version + device pre-filled). \"Manual\" entry — this screen.",
                "i18n pass: every UI string has English + German resources (302 keys identical in both locales).",
                "App rebranded: applicationId io.celox.zt3fxx, portrait-locked.",
                "MCU 0x40 (motor-temp peak) and 0x3E (overall MCU temp) now parsed and shown — were polled but silently dropped before."
            ),
        ),
        Entry(
            version = "0.1.6",
            versionCode = 7,
            date = "2026-04-28",
            highlights = listOf(
                "Custom-button double-tap → 22 km/h lock, fast-poll on reg 0x5A every 250 ms, 3 s detection window.",
            ),
        ),
        Entry(
            version = "0.1.5",
            versionCode = 6,
            date = "2026-04-27",
            highlights = listOf(
                "VCU firmware version read via reg 0x17.",
                "Region derived from serial-number prefix instead of a separate register.",
            ),
        ),
        Entry(
            version = "0.1.4",
            versionCode = 5,
            date = "2026-04-27",
            highlights = listOf(
                "Byte-perfect ZT3 mode mapping verified against scooter (1-indexed: 0x01=Eco, 0x02=Drive, 0x03=Sport, 0x04=Walk).",
                "Dashboard mode-switch loading state.",
                "Deep BMS/VCU readout with corrected scaling.",
            ),
        ),
        Entry(
            version = "0.1.0",
            versionCode = 1,
            date = "2026-04-25",
            highlights = listOf(
                "First field-tested release: NinebotCrypto (5A A5 / AES-CBC-MAC + AES-CTR) live against ZT3 Pro D, speed-limit write verified.",
                "Stealth Vol-Down-3× lock + Vol-Up-3× unlock with screen off.",
                "OSM map + GPS track recording.",
                "Multi-vehicle garage (Room DB).",
                "Diagnostics screen with live BLE frame log.",
            ),
        ),
    )
}
