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
| Material 3 UI mit Bottom-Nav (4 Tabs + 7 Sub-Screens) | Vehicle, Discover, Track, Mine + Pair, Garage, Firmware, Profiles, AirLock, Diagnostics, Settings, About |
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

### ⚠ Wichtig: BLE-Protokoll-Pfad

Die App ist auf den **modernen ECDH-Pfad** (Frame-Magic `0x55 0xAB`, secp256r1, AES/CCM, HKDF-SHA-256) ausgelegt — das ist der Stack, den die SHU-App in `crypto/elliptic/d.java` für G3/F3/ZT3 zur Verfügung stellt.

**Aber: Die HCI-Snoop-Analyse einer realen ZT3-Pro-D-Session** ([`reverse-engineering/ble-captures/2026-04-25-shu-flash-session.md`](../reverse-engineering/ble-captures/2026-04-25-shu-flash-session.md)) zeigt, dass auf der Wire **der klassische NinebotCrypto-Pfad mit `5A A5`-Magic + AES + SHA-1 + 8-Bit-Counter** verwendet wird, **nicht** der `55 AB`-ECDH-Pfad.

Konsequenz für **deinen ZT3 Pro D**:

- 🔴 **Pairing wie aktuell implementiert wird wahrscheinlich nicht funktionieren** ohne weitere Anpassung
- Die App muss noch um einen **NinebotCrypto-Classic-Pfad** erweitert werden (Magic `0x5A 0xA5`, 8-Bit-Counter, AES+SHA-1-Pairing-KDF)
- Als Workaround: **Frida-Hook in SHU**, um den AES-Session-Key live zu extrahieren, dann unsere App damit füttern (Bypass des Pairings)

Status & Vorgehen: nächster Field-Test mit Diagnostics-Page → die unverschlüsselten Frame-Header lesen, um zu bestätigen welcher Magic gesendet wird → dann den passenden Codepfad bauen.

### Open items / „first ride" checklist

1. **Klassischer NinebotCrypto-Pfad** (`5A A5`) implementieren als Alternativ-Stack zu `EllipticPairing`
2. **HKDF-Salt/Info-Strings** des modernen Pfads verifizieren (für andere Modelle)
3. **0xB0 Register-Layout** gegen reale Notify-Frames cross-checken
4. **Region-Change** → vollständige SN-Read-Modify-Write-Sequenz (aktuell wird nur das Region-Byte gesendet)
5. **OTA-Chunk-ACK-Detection** robust machen (aktuell heuristisch)
6. Launcher-Icon polishen (aktuell Vector-Stub)

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
