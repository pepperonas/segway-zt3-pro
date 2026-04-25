# Segway Mobility (Reborn) – Android App

[![Min SDK](https://img.shields.io/badge/minSdk-26-blue)](#)
[![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose)](#)
[![Architecture](https://img.shields.io/badge/architecture-MVVM%20%2B%20Hilt-success)](#)
[![Maps](https://img.shields.io/badge/maps-OpenStreetMap-7EBC6F?logo=openstreetmap)](#)
[![BLE](https://img.shields.io/badge/BLE-Nordic%20UART-blue)](#)
[![Crypto Path](https://img.shields.io/badge/crypto-NinebotCrypto%20(5A%20A5)%20%E2%80%94%20handshake%20live%2C%20field--tuning-yellow)](#wichtig-ble-protokoll-pfad)

Native, open-source rebuild of the official Segway-Ninebot **Segway Mobility** companion app, focused on the **ZT3 Pro D**. Built with Jetpack Compose + Material 3, OpenStreetMap, and the reverse-engineered Ninebot 2nd-gen pairing protocol.

> ⚠ For private use / private property only. Tuning a StVZO-registered scooter voids warranty, insurance and street-legality.

---

## ⚡ Headline-Feature: Lock-by-Default + Stealth-Unlock

Vereinfachtes 2-State-Modell — Roller einschalten → 22 km/h, App-Unlock → 40 km/h:

| Zustand | Limit | Trigger |
|---|---|---|
| 🔒 **Locked** (Default beim Connect) | **22 km/h** | App schickt automatisch 1.5 s nach jedem Connect den Boot-Wert |
| 🔓 **Unlocked** | **40 km/h** | (a) `Unlock 40 km/h`-Button im Vehicle-Dashboard (mit optionalem PIN), **oder** (b) **Volume-Down 3× innerhalb 2 s** auch bei Screen-aus / App-Hintergrund (Accessibility-Service) |

**Wichtig**: Disconnect oder Roller-Power-Cycle setzt den Lock-State zurück → beim nächsten Connect wieder 22. **Unlock ist session-only.**

Optional:
- **Quick-Action-Profile** (Walk 6 / City 22 / Cruise 28) als manueller Override
- **Auto-Revert** auf das Boot-Profil nach N Minuten (Slider 0-60), live-Countdown-Banner
- **Auto-Apply-Toggle** in den Settings (Default ON; ausschaltbar wenn man manuell steuern will)

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
│   ├── ble/          BLE scan, GATT, FrameCodecClassic (Plaintext) + FrameCodecCrypto (NinebotCrypto), ECDH pairing
│   ├── crypto/       NinebotCrypto (AES-CBC-MAC + CTR, port of c6.c) + ECDH/AES-CCM/HKDF/HMAC primitives (BouncyCastle)
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
| **Auto-Apply Default ON** | Boot-Profil (22 km/h) wird automatisch nach jedem Connect gesendet — Lock-by-Default |
| **Re-Lock bei Disconnect** | Lokaler Unlock-State wird gelöscht, beim nächsten Connect wird wieder 22 geschickt |
| **GATT-Write-Mutex** | Kein „prior command not finished" mehr — Writes werden sequentiell durch eine Coroutine-Mutex serialisiert + auf `onCharacteristicWrite` gewartet |
| **BLE-Scan-Filter** | Nur Geräte mit Ninebot-Manufacturer-Prefix (`FF 4E 42` / `FF 4E 43`) werden in der Pair-Liste gezeigt — keine Headphones/TVs/Watches mehr |
| **Auto-Pair** | Beim ersten gefundenen Scooter im Pair-Screen wird automatisch verbunden + Pair-Screen schließt sich selbst |
| **`CancellationException`-Hygiene** | Coroutine-Cancel beim Pair-Screen-Close zeigt nicht mehr „StandaloneCoroutine was cancelled" als Fehler an |

### ⚠ Wichtig: BLE-Protokoll-Pfad — Stand 2026-04-26

Iteration 1 (ECDH `55 AB`) → **verworfen** (FIELD-TEST 2026-04-25: Roller ging in 5-km/h-Failsafe).
Iteration 2 (Plaintext-Stock `5A A5` ohne CRC-Encryption, SHU `c6.b#a()` Case 3) → **verworfen** (TX-Writes ohne RX-Notifies — Frame-Format akzeptiert, aber stumm).
Iteration 3 (**NinebotCrypto** `5A A5` mit AES-CBC-MAC + AES-CTR + Handshake, SHU Case 2) → **aktuell live**.

**Auflösung der Konflikt-Analyse**: Die HCI-Capture vom 2026-04-25 ([`2026-04-25-shu-flash-session.md`](../reverse-engineering/ble-captures/2026-04-25-shu-flash-session.md)) zeigt eindeutig Manufacturer-ID `0x434E` ("NC") und alle 3142 ATT-Payloads mit verschlüsseltem Body. SHU's `ScooterActivity.n():1046` wählt für Devices mit `usesCrypto=true` den `f0.a.NinebotCrypto`-Pfad — für ZT3 Pro D ist `usesCrypto=true`.

**Wire-Format (aus `c6/c.java#i()` direkt portiert):**

```
5A A5 [len] [src dst cmd arg ENC(payload)] [tag(4)] [ctrHi ctrLo]
       └─ payload-bytes only      └─ CBC-MAC, 4 byte    └─ 16-bit BE counter
```

**Schlüssel-Ableitung (`c6/c.java:73,199-205`):**

```
salt = {0x97, 0xCF, 0xB8, 0x02, 0x84, 0x41, 0x43, 0xDE,
        0x56, 0x00, 0x2B, 0x3B, 0x34, 0x78, 0x0A, 0x5D}   # global Ninebot salt
key  = SHA-1(scooterName[0..12] ++ salt[0..12])[0..16]
```

**Handshake**: erstes Frame nach Connect ist `5A A5 10 3E 21 5C 00 [16 random]` (counter=0, nur f-XOR-obfuscation). Roller antwortet mit Token, `key` wird als `SHA-1(scooterName ++ token)[0..16]` neu abgeleitet, anschließend laufen alle Frames mit echter Crypto + auf-/abzählendem 16-Bit-Counter.

**Code-Ort:**
- [`core/crypto/NinebotCrypto.kt`](app/src/main/kotlin/com/celox/segway/core/crypto/NinebotCrypto.kt) — port von `c6.c`
- [`core/ble/FrameCodecCrypto.kt`](app/src/main/kotlin/com/celox/segway/core/ble/FrameCodecCrypto.kt) — wrapper
- [`core/vehicle/Zt3ProVehicle.kt`](app/src/main/kotlin/com/celox/segway/core/vehicle/Zt3ProVehicle.kt) — `connect()` führt Handshake, `execute()` blockiert bis Handshake fertig

[`FrameCodecClassic.kt`](app/src/main/kotlin/com/celox/segway/core/ble/FrameCodecClassic.kt) (Plaintext-Pfad) bleibt für andere Modelle / Diagnose erhalten.

### Open items / „first ride" checklist

1. **Field-Test der Crypto-Implementation** (Iteration 3) — Field-Test 2026-04-26 zeigt 15-km/h-Anomalie statt commandet 22; Hypothesen:
   - **`scooterName`-Mismatch** zwischen App und Roller (Roller advertiset doppelt als `1K1Dx…` *und* `1K1Ux…` — wir verbinden uns mit dem ersten Treffer, SHU evtl. mit dem anderen)
   - Speed-Limit-Register `0x72` evtl. falsch (anderer Default-Limit-Slot wird getroffen)
   - Encoding `(kmh, 0x00)` vs. U8 vs. ×10-Skalierung
2. **Diagnostics-Logger** erweitern: Inner-Frame UND Wire-Frame nebeneinander loggen, plus `scooterName`-Debug-Anzeige
3. **0xB0 Register-Layout** gegen reale Notify-Frames cross-checken (sobald Crypto-Pfad RX-Notifies liefert)
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
