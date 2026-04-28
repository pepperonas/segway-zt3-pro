# zt3-fxx — Android App for ZT3 Pro D

ApplicationId: `io.celox.zt3fxx` · Native Kotlin / Compose / Material 3 / Hilt.

> ⚠ Privatgelände only. Tuning eines StVZO-Rollers verwirkt Betriebserlaubnis + Versicherung.

## Build

```bash
cd app

# Einmalig: Wrapper bootstrappen
gradle wrapper --gradle-version 8.10.2

# Oder direkt:
./gradlew :app:installDebug
```

Min-SDK 26 (Android 8). Debug-Builds bekommen das Suffix `.debug` und können parallel zur offiziellen Ninebot-App existieren.

## Modul-Layout

```
app/
├── core/
│   ├── ble/          BLE scan, GATT, FrameCodec (Plaintext + Crypto)
│   ├── crypto/       NinebotCrypto (AES-CBC-MAC + CTR, port von c6.c)
│   ├── ota/          IAP-Flash-State-Machine (cmd 0x07/08/09/0A)
│   ├── repo/         CFW-Repo HTTP-Client
│   ├── profile/      Speed-Profile + Auto-Apply + RideSessionRecorder
│   ├── vehicle/      Vehicle-Interface + ZT3-Implementation
│   ├── data/         DataStore + Room (Vehicles, Tracks, RideSessions)
│   └── util/         BleLog (Ring-Buffer aller TX/RX-Frames)
├── di/               Hilt-Module
├── ui/               Theme, Speedometer, Nav-Host
└── feature/
    ├── home/         Vehicle-Dashboard + Live-Trip-Card
    ├── pair/         BLE-Scan + Pairing-UI
    ├── garage/       Multi-Vehicle-Liste
    ├── firmware/     OTA-Flash-UI
    ├── profiles/     Speed-Profile-Editor + Stealth-Trigger
    ├── airlock/      Proximity-Auto-Unlock
    ├── trips/        Ride-Session-Historie (BLE-only, kein GPS)
    ├── track/        OSM-Map + GPS-Track-Recording
    ├── diagnostics/  Live-BLE-Frame-Log + Field-Test-Buttons
    ├── scooter_settings/  17 Bitfield-Toggles + 4 Slider + 3 Enums
    ├── battery/      Cell-Voltages + BMS-Detail
    ├── mine/         Settings-Hub
    └── about/        Disclaimer + Manual + Changelog
```

## Was funktioniert (Field-tested 2026-04-28)

**Connectivity**
- NinebotCrypto byte-genau gegen SHU verifiziert (FIELD-TEST-LOG Session 5)
- Per-MAC `cryptoRandom`-Resume — kein Fresh-Pair nach App-Restart
- Auto-Reconnect-Watchdog (8s) + Foreground-Service (`connectedDevice`-Type)
- BLE-Frame-Log als Ring-Buffer mit Share-as-Text

**UI / Telemetrie**
- Speedometer (BLE-Speed groß, GPS-Cross-Check klein) + adaptive Poll-Cadence (4 Hz Fahrt / 0.7 Hz Stand)
- Live-Trip-Card mit Strecke / Max / Ø / Energie (auto-detect via Speed-Register)
- Trip-History-DB mit Auto-Session-Detection (BLE-only)
- Battery-Detail: SoC, Voltage, Current, Power, 13 Cell-Voltages, Temp, Charge-Threshold
- 22 XiaoDash-equivalent Settings (17 Bitfields, 4 Slider, 3 Enums)

**Lock / Profile**
- Boot-Default 22 km/h, Unlock-Profil 40 km/h, 3 Quick-Actions
- Stealth-Unlock via Vol-Up-3× / Vol-Down-3× (auch Screen-Off via Accessibility-Service + MediaSession)
- Custom-Button-Doppel-Tap → Lock (Walk / Park / Hill-Hold / KERS)
- Auto-Apply Boot-Profil bei jedem Reconnect
- Auto-Revert-Timer mit Live-Countdown

**Garage / OTA**
- Multi-Vehicle-Garage (Room-DB)
- OTA-Flash mit IAP-State-Machine + CFW-Repo-Client
- Region-Change (D → U) als One-Tap
- AirLock (Proximity-Unlock per RSSI-EMA)
- OSM-Karte + GPS-Track-Recording mit History

## Was NICHT funktioniert (ZT3-Firmware-Restriktionen)

- **Mode-Wechsel (Eco/Drive/Sport)** via `0x5A`: Roller ackt mit `[01 00]`, ändert aber Display nicht. Vermutlich read-only auf ZT3 (im Gegensatz zu GT3/F3).
- **Headlight Manual Toggle** (`0x5B`): gleiche Symptomatik. Auto-Headlight via Bit in `0x1F` läuft firmware-internal.
- **Cruise Control**: keine Remote-Aktivierung — Throttle-halten 5+ s ist die einzige Methode.
- **Blinker-/Bremshebel-State**: in keinem Register exponiert (vollständig per `zt3-cli` gesweept). Custom-Button-Doppeltap funktioniert nur über Mode-Effekt-Detection (`0x5A` / `0x70`), nicht über Button-Roh-Press.

ZT3 Pro D ist register-kompatibel zu GT3/F3, aber **deutlich restriktiver welche Register tatsächlich beschreibbar sind**. Vollständige RE würde patched offizielle Segway-App, Frida-Hook, oder ESP32-MitM erfordern — letzteres ist in [`../ESP32-MITM-PLAN.md`](../ESP32-MITM-PLAN.md) eingeplant.

## Wire-Protokoll (Kurzform)

```
5A A5 [len] [src dst cmd arg ENC(payload)] [tag(4)] [ctrHi ctrLo]
```

| dst | Modul | Verwendung |
|---|---|---|
| `0x04` | Cellular | Crypto-Handshake |
| `0x16` | VCU | Status, Settings, Speed-Limit |
| `0x02` | MCU | Motor-Telemetrie |
| `0x07` | BMS | Battery-Telemetrie |

3-Stage Handshake: `0x5B` (getRandom) → `0x5C` (fresh) oder `setRandomAppData(persisted)` (resume) → `0x5D` (challenge-echo). Resume nach erstem Pair persistent in `PairingPrefs.Config`.

Vollständige Register-Map: [`../reverse-engineering/protocol/zt3-ble-register-reference.md`](../reverse-engineering/protocol/zt3-ble-register-reference.md). Settings-Register: [`../reverse-engineering/protocol/zt3-settings-registers.md`](../reverse-engineering/protocol/zt3-settings-registers.md).

## Changelog-Policy

User-visible-Changes (jede Release mit `versionName`-Bump in `app/build.gradle.kts`) MUSS einen Eintrag in [`feature/manual/Changelog.kt`](app/src/main/kotlin/com/celox/segway/feature/manual/Changelog.kt) bekommen. Der gleiche In-App-Screen rendert das verbatim.

- Eine Zeile pro user-perceptible Change. Refactors / pure-internal Cleanups nicht bullet'en.
- Wenn ein Bugfix einen UI-String / Default / Setting ändert → eintragen.
- Version + versionCode im Eintrag müssen mit `build.gradle.kts` übereinstimmen.
- Entries auf Englisch (Audience: Power-User / Devs).

## Diagnose

1. **Diagnostics-Page** öffnen, Field-Test-Buttons drücken (22 km/h / 40 / Lock / Unlock)
2. Im Frame-Log live alle TX/RX-Bytes mit Zeitstempel
3. **Share** → Mail / Slack / Gist
4. Schneller iterieren: [`../python/`](../python/) Mac-CLI für 50-ms-Cycles statt 10-s-installDebug

## Credits

- [SHU](https://utility.cfw.sh/) — Quelle für NinebotCrypto + ECDH-Pairing-Details
- [Nordic-Android-BLE-Library](https://github.com/NordicSemiconductor/Android-BLE-Library)
- [osmdroid](https://github.com/osmdroid/osmdroid)
- BouncyCastle
