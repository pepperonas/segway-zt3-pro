# Segway Mobility (Reborn) – Android App

[![Min SDK](https://img.shields.io/badge/minSdk-26-blue)](#)
[![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose)](#)
[![Architecture](https://img.shields.io/badge/architecture-MVVM%20%2B%20Hilt-success)](#)
[![Maps](https://img.shields.io/badge/maps-OpenStreetMap-7EBC6F?logo=openstreetmap)](#)
[![BLE](https://img.shields.io/badge/BLE-Nordic%20UART-blue)](#)
[![Crypto Path](https://img.shields.io/badge/crypto-ECDH%20path%20(field--untested%20on%20ZT3)-orange)](#wichtig-ble-protokoll-pfad)

Native, open-source rebuild of the official Segway-Ninebot **Segway Mobility** companion app, focused on the **ZT3 Pro D**. Built with Jetpack Compose + Material 3, OpenStreetMap, and the reverse-engineered Ninebot 2nd-gen pairing protocol.

> ⚠ For private use / private property only. Tuning a StVZO-registered scooter voids warranty, insurance and street-legality.

---

## ⚡ Headline-Feature: Speed-Profiles + Stealth-Unlock

Das Kern-Feature für ZT3-Pro-D-User – **„German-Manöver"** in software:

| Profil | Default | Trigger |
|---|---|---|
| **Boot** (legal-look) | 22 km/h | automatisch nach jedem Connect |
| **Walk / City / Cruise** (3 frei konfigurierbare Quick-Actions) | 6 / 22 / 28 km/h | Tap im Vehicle-Dashboard |
| **Unlock** | 40 km/h | (a) PIN-Dialog im UI, **oder** (b) **Volume-Down 3× innerhalb 2 s** — auch bei Screen-aus + App im Hintergrund (Accessibility-Service) |

Optional: **Auto-Revert** auf das Boot-Profil nach N Minuten (Slider 0-60), live-Countdown-Banner auf dem Dashboard.

## Build & install

```bash
cd app

# 1) Bootstrap the Gradle wrapper (only needed once on a fresh checkout)
gradle wrapper --gradle-version 8.10.2

# 2) Open in Android Studio (Hedgehog or newer)
#    – or build from CLI:
./gradlew :app:installDebug
```

Die App nutzt das Package `com.celox.segway` (`.debug`-Suffix in Debug-Builds — kann parallel zur offiziellen Ninebot-App existieren).

## Module layout

```
app/
├── core/
│   ├── ble/          BLE scan, GATT, frame codec, ECDH pairing
│   ├── crypto/       ECDH/AES-CCM/HKDF/HMAC primitives (BouncyCastle)
│   ├── ota/          IAP-flash state machine (cmd 0x07/08/09/0A)
│   ├── repo/         CFW-repo HTTP client (apps-data.cfw.sh)
│   ├── profile/      Speed profiles + auto-apply manager
│   ├── vehicle/      Vehicle interface + ZT3 Pro implementation
│   ├── data/         DataStore, Room DB, PairingPrefs
│   └── util/         BleLog (ring-buffer of all TX/RX frames)
├── di/               Hilt modules
├── ui/
│   ├── theme/        Material 3 theme + dynamic color
│   ├── components/   Speedometer, StatTile, etc.
│   └── SegwayApp.kt  NavHost + bottom nav
└── feature/
    ├── home/         Vehicle dashboard (incl. quick-actions + unlock banner)
    ├── pair/         BLE scan + pairing UI
    ├── garage/       Multi-vehicle list
    ├── firmware/     OTA flash UI (VCU/MCU + region change)
    ├── profiles/     Speed-profile editor + AccessibilityService
    ├── airlock/      Proximity-unlock service
    ├── diagnostics/  Live BLE log + field-test buttons + black-box readout
    ├── track/        OSM map + GPS recording
    ├── discover/     Community feed (stub)
    ├── mine/         Settings entry
    ├── settings/     Theme, units, dynamic color
    └── about/        Disclaimer
```

## Implementation status

### Erledigt ✅

| Bereich | Detail |
|---|---|
| Material 3 UI mit Bottom-Nav (3 Tabs + 7 Sub-Screens) | Vehicle, Track, Mine + Pair, Garage, Firmware, Profiles, AirLock, Diagnostics, Settings, About — Discover-Modul wurde 2026-04-25 entfernt |
| BLE-Scanner | Nordic-Lib, NB/NC-Beacon-Parser |
| GATT-Client | Nordic-UART, MTU-Negotiation, Auto-CCCD |
| **Speed-Profile-System** | DataStore-Repo, Auto-Apply on Connect, 3 Quick-Actions, 1 Unlock-Profile, optional Auto-Revert |
| **Stealth-Unlock** | (a) PIN-Dialog **+** (b) Vol-Down-3× via AccessibilityService (auch Screen aus / Background) |
| **OTA-Flash** | IAP state machine (cmd `0x07/08/09/0A`), HTTP-Client zu `apps-data.cfw.sh`, Flash-UI mit Progress |
| **Region-Change** | One-Tap nach US (mit Confirm-Dialog) |
| **Black-Box-Readout** | Cmd `0x01 0xF0 0x40` → Crash-Log-Hex-Dump |
| **FW-Versions-Lesen** | Cmd `0x01 0x1A 0x10` → VCU/MCU/BLE-Version-Strings |
| **AirLock** (Proximity-Unlock) | RSSI-EMA + konfigurierbare Schwelle + 30 s Cooldown |
| **Live-Beacon-Decoder** | Speed/Battery/Lock aus Adv-Frame mit gespeichertem `beaconKey` (ohne Connect) |
| **Multi-Vehicle Garage** | Room-DB, aktivieren/umbenennen/entkoppeln |
| **Track-Recording** | Foreground-Service mit `LocationManager`, Aggregation auf Room, Map-Overlay-History |
| **OSM-Karte** | osmdroid mit Pull-Zoom, Track-Visualisierung |
| **BLE Field-Test-Logger** | Ring-Buffer 512 Frames, monospace-Anzeige, Share-as-Text-Export |
| **Field-Test-Buttons** in Diagnostics | Quick-Send 22/40 km/h, Lock/Unlock — sofort sichtbar im Frame-Log |
| **Polish-Pass** | Snackbar bei Quick-Action-Apply, Live-Countdown-Banner im Unlock-Modus |
| **Auto-Reconnect bei App-Start** | `MainActivity.onCreate` ruft `ActiveVehicleHolder.tryAutoReconnect()` zur letzten gespeicherten MAC |
| **Auto-Apply opt-in** | Default OFF — Boot-Profil wird nur gesendet wenn der User es in den Settings aktiviert (sicherer Default nach Field-Test 2026-04-25) |
| **GATT-Write-Mutex** | Kein „prior command not finished" mehr — Writes werden sequentiell durch eine Coroutine-Mutex serialisiert + auf `onCharacteristicWrite` gewartet |

### ⚠ Wichtig: BLE-Protokoll-Pfad — durch Field-Test bestätigt

Die App spricht aktuell den **modernen ECDH-Pfad** (Magic `0x55 0xAB`, secp256r1 + AES/CCM + HKDF-SHA-256). Der **Field-Test am 2026-04-25** ([`FIELD-TEST-LOG.md`](FIELD-TEST-LOG.md)) hat bestätigt:

- ✅ GATT-Layer (Connect, Service-Discovery, MTU 251) funktioniert sauber
- ✅ App schreibt erfolgreich auf NUS-RX (`6e400002-…`)
- ❌ **Roller schickt keine RX-Notifies zurück** → unser Wire-Format wird nicht akzeptiert
- ⚠ Aber: Der Roller **reagierte** (drosselte sich nach unseren Frames auf 5 km/h) — wahrscheinlich Failsafe-Mode

→ Damit ist klar: für die **deterministische Steuerung** des ZT3 Pro D braucht es den **NinebotCrypto-Classic-Pfad**:
- Frame-Magic `0x5A 0xA5`, 8-Bit-Counter
- AES + SHA-1 Pairing-KDF
- Hello-Sequenz `3E 21 5B 00`, OOB-Auth via Power-Button
- 16-Bit-CRC im Trailer

Das wird der nächste Implementierungs-Schritt. Als Workaround steht **Frida-Hook in SHU** zur Verfügung, um den AES-Session-Key live zu extrahieren.

### Open items / „first ride" checklist

1. **Classic-Path** (`5A A5`) implementieren als Alternativ-Stack zu `EllipticPairing` — **TOP-Priorität nach Field-Test**
   - Frame-Codec: `5A A5 [len] [src] [dst] [cmd] [arg] [payload] [crc16]`
   - Pairing: `3E 21 5B 00`-Hello, Power-Button OOB, `21 3E 5D 01`-Final
   - Default-Pairing-Key (`97 CF B8 24 …`)
2. **Beacon-basierte Stack-Auswahl** im `ActiveVehicleHolder`: `NB`-Beacon → Classic, `NC` → ECDH
3. **HKDF-Salt/Info-Strings** des modernen Pfads verifizieren (für andere Modelle als ZT3)
4. **0xB0 Register-Layout** gegen reale Notify-Frames cross-checken (sobald Classic-Pfad RX-Notifies liefert)
5. **Region-Change** → vollständige SN-Read-Modify-Write-Sequenz (aktuell wird nur das Region-Byte gesendet)
6. **OTA-Chunk-ACK-Detection** robust machen (aktuell heuristisch)
7. Launcher-Icon polishen (aktuell Vector-Stub)

## Wie weiter testen / debuggen

1. **Diagnostics-Page** öffnen, Roller verbinden, **Field-Test-Buttons** drücken (22 km/h / 40 km/h / Lock / Unlock)
2. Im Frame-Log siehst du alle TX/RX-Bytes mit Zeitstempel
3. **Share** den Log als Text per Mail / Slack / GitHub-Gist
4. Cross-check gegen `2026-04-25-shu-flash-session.md`: zeigt deine App `5A A5` oder `55 AB`?

## Wie für andere Scooter erweitern

Das `Vehicle`-Interface ist der Erweiterungspunkt:

```kotlin
class G3Vehicle(
    override val id: String,
    override val displayName: String,
    private val gatt: GattClient,
    private val pairing: EllipticPairing,
) : Vehicle {
    /* override execute() with the G3-specific register map */
}
```

…und in `ActiveVehicleHolder` statt `Zt3ProVehicle` binden. Crypto-/Frame-Layer sind modell-agnostisch (sobald der Classic-Pfad mit drin ist).

## Credits

Diese App baut auf:

- [ScooterHacking Utility (SHU)](https://utility.cfw.sh/) – Quelle der ECDH-Pairing-Details
- [scooterteam/ZT3Tools](https://github.com/scooterteam/ZT3Tools/) – ST-Link Memory-Layout-Referenz
- [Nordic Semiconductor's Android-BLE-Library](https://github.com/NordicSemiconductor/Android-BLE-Library)
- [osmdroid](https://github.com/osmdroid/osmdroid)
- BouncyCastle, BCprov-jdk18on
- Eigene HCI-Snoop-Analyse: [`reverse-engineering/ble-captures/`](../reverse-engineering/ble-captures/)
