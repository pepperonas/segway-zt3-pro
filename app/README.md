# Segway Mobility (Reborn) – Android App

[![Min SDK](https://img.shields.io/badge/minSdk-26-blue)](#)
[![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose)](#)
[![Architecture](https://img.shields.io/badge/architecture-MVVM%20%2B%20Hilt-success)](#)
[![Maps](https://img.shields.io/badge/maps-OpenStreetMap-7EBC6F?logo=openstreetmap)](#)
[![BLE](https://img.shields.io/badge/BLE-Nordic%20UART-blue)](#)
[![Crypto Path](https://img.shields.io/badge/crypto-NinebotCrypto%20(5A%20A5)%20%E2%80%94%20FIELD--TESTED%20%E2%9C%85-success)](#wichtig-ble-protokoll-pfad)

Native, open-source rebuild of the official Segway-Ninebot **Segway Mobility** companion app, focused on the **ZT3 Pro D**. Built with Jetpack Compose + Material 3, OpenStreetMap, and the reverse-engineered Ninebot 2nd-gen pairing protocol.

> ⚠ For private use / private property only. Tuning a StVZO-registered scooter voids warranty, insurance and street-legality.

---

## ✅ Was funktioniert (Field-tested 2026-04-28)

- **Speed-Limit setzen** (22 / 40 km/h Lock-Mode + custom kmh) — `dst=0x16, reg=0x48, payload=[0x14, kmh]`, byte-perfekt gegen SHU verifiziert (FIELD-TEST-LOG Session 5)
- **3× Vol-Up Stealth-Unlock auf 40, 3× Vol-Down Stealth-Lock auf 22** — auch mit ausgeschaltetem Display via `MediaSessionCompat.VolumeProvider` + Foreground-Service (Session 6)
- **Live-Status-Notification** — zeigt aktuellen Lock-Zustand + nächste Aktion, updated automatisch
- **Auto-Lock bei jedem Reconnect** — sobald Phone in Reichweite + Handshake durch, wird Boot-Profile (22 km/h) gesetzt
- **Live-Telemetrie** mit verifizierten Registern (Session 8 + [`zt3-ble-register-reference.md`](../reverse-engineering/protocol/zt3-ble-register-reference.md)):
  - Battery (reg `0x55` VCU oder `0x8F` BMS) ✓
  - Temperature (reg `0x6B`, °C × 10) ✓
  - Trip + Odometer (regs `0x68` / `0x62`) ✓
  - Live-Speed (reg `0x57` oder MCU `0x86`) ✓
- **Mode-Anzeige live** — Roller-Display-Wechsel (Walk → E → D → S via Power-Button-Doppeltap) wird in der App reflektiert. ZT3-Firmware ist 1-indexed: `0x01=E, 0x02=D, 0x03=S, 0x04=Männchen` (Session 9)
- **KeepScreenOn-Toggle** in Settings → App-Bildschirm bleibt aktiv solange offen
- **Register-Sweep-Button** in Diagnostics für eigene Reverse-Engineering-Sessions

## ❌ Was NICHT funktioniert (ZT3-Pro-D-Firmware-Restriktionen)

- **Mode-Wechsel (Eco/Drive/Sport)**: reg `0x5A` (VCU_DRIVE_MODE) ist laut [x3regs.h](https://github.com/MacintoshKeyboardHacking/segMod/blob/main/myBLE4/x3regs.h) für GT3/F3 schreibbar, aber auf ZT3 Pro D firmware-seitig restriktiv: Roller ackt unsere Writes mit `[01 00]`-Beep, ändert aber Display nicht. Vermutlich nur read-only auf ZT3.
- **Headlight Manual Toggle**: reg `0x5B` (VCU_LedMode) — gleiche Symptomatik. Auto-Headlight via Bit in `0x1F` läuft firmware-internal beim Fahren.
- **Cruise Control**: keine Remote-Aktivierung — Throttle-halten 5+ s ist die einzige Methode (firmware-internal).
- **Custom-Button-Remapping** (Hill-Hold → Speed-22): Roller sendet kein BLE-Notify bei Button-Press → keine App-vermittelte Reaktion möglich.

ZT3 Pro D ist register-kompatibel zu GT3/F3 aber **deutlich restriktiver welche Register tatsächlich beschreibbar sind**. Doc-Hinweis: ZT3-VCU hat im Gegensatz zu GT3/G3/F3 keinen SPI-Flash-Chip — möglicherweise hängt das mit den fehlenden Schreibrechten zusammen. Vollständige RE würde einen der drei Wege erfordern:
1. **Patched offizielle Segway-Mobility-App** mit Logging — NIS-Wrapper + Hermes-Bytecode, mehrtägig
2. **Frida-Hook zur Laufzeit** auf entweder Segway-App oder libnbcrypto.so
3. **CAN-MITM-Sniffing** zwischen BLE-Modul und VCU per ESP32 (segMod-Ansatz)

## 📚 Wire-Protokoll-Referenz

Vollständige BLE-Register- + Frame-Format-Doku in [`reverse-engineering/protocol/zt3-ble-register-reference.md`](../reverse-engineering/protocol/zt3-ble-register-reference.md). Quellen:
- [segMod Wiki](https://github.com/MacintoshKeyboardHacking/segMod/wiki)
- [x3regs.h](https://github.com/MacintoshKeyboardHacking/segMod/blob/main/myBLE4/x3regs.h)
- [NootNooot Ninebot BLE](https://nootnooot.codeberg.page/segway-ninebot-ble/)
- Eigene Field-Tests + Patched-SHU CRYPTO_DUMP-Methode (FIELD-TEST-LOG)

## ⚡ Headline-Feature: Lock-by-Default + Stealth-Volume-Triggers

Field-tested 2026-04-27 — funktioniert auf realer ZT3 Pro D Hardware.

### State-Modell

| Zustand | Speed-Limit | Wie hin? |
|---|---|---|
| 🔒 **Locked** (Default-State der App) | **22 km/h** | (a) **3× Vol-Down** innerhalb 2 s — Screen on **oder off**, **oder** (b) "Lock"-Button im Vehicle-Dashboard, **oder** (c) automatisch bei jedem App-Reconnect (= nach Roller-Power-Cycle, sobald Phone in BLE-Reichweite) |
| 🔓 **Unlocked** | **40 km/h** | (a) **3× Vol-Up** innerhalb 2 s — Screen on/off, **oder** (b) "Unlock 40 km/h"-Button (optional mit PIN) |

### Wichtig zu wissen

- **Stealth-Trigger funktionieren mit Display aus** dank Foreground-Service mit `MediaSessionCompat.setPlaybackToRemote(VolumeProvider)`. Der OS-VolumeController routet Vol-Keys direkt an unsere `onAdjustVolume`-Callback — funktioniert auch bei gesperrtem Phone, weil die Routing-Pipeline nicht den InputDispatcher braucht.
- **Auto-Apply 22 km/h bei jedem Reconnect**: Sobald Phone via BLE den Roller findet und der 3-stage NinebotCrypto-Handshake durch ist (~200-500 ms), feuert unsere App automatisch `SetSpeedLimit(22)`. Damit ist der "Lock" defaultmäßig aktiv für jede neue Session.
- **Roller-NVRAM behält das letzte Limit**: Wenn du den Roller off → on machst und das Phone NICHT in Reichweite ist, läuft der Roller mit dem zuletzt aktiven Limit (= 40, falls letzter Zustand "unlocked" war). Erst nach App-Reconnect wird Lock-State wiederhergestellt. Race-Window: einige Sekunden zwischen Roller-On und Phone-Connect — by-design, ohne Firmware-Mod nicht änderbar.

### Stealth-Service-Setup

Damit 3× Vol-Up/Down im Screen-Off funktionieren, muss in den **Android-Settings → Bedienungshilfen → Segway** der Accessibility-Service einmal aktiviert werden. Die App zeigt einen roten Banner im Vehicle-Screen wenn das nicht der Fall ist + Direktlink zur System-Einstellung.

Dazu läuft persistent ein **Foreground-Service** ("Stealth-Unlock aktiv" in der Statusbar), der die Volume-Key-Events einfängt. Notification ist `IMPORTANCE_LOW`, kein Sound, keine Vibration.

### Optional

- **Quick-Action-Profile** (Walk 6 / City 22 / Cruise 28) als manueller Override im Vehicle-Dashboard
- **Auto-Revert** auf Boot-Profile nach N Minuten (Slider 0-60), live-Countdown-Banner während Unlock-Mode
- **Stealth-Trigger-Toggle** in Settings (Default ON; ausschaltbar wenn man die Volume-Tasten "normal" für Media-Volume nutzen will — dann gehen Vol-Up/Down wieder ans Audio-System statt an unsere App)
- **Optional PIN** für Unlock — wenn gesetzt, wird Vol-Up-3× ignoriert und stattdessen das Vehicle-Screen-Modal mit PIN-Prompt geöffnet

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

### ⚠ Wichtig: BLE-Protokoll-Pfad — Stand 2026-04-27 ✅ FUNKTIONIERT

Iteration 1 (ECDH `55 AB`) → verworfen (5-km/h-Failsafe).
Iteration 2 (Plaintext-Stock `5A A5` Case 3) → verworfen (TX ohne RX).
Iteration 3 (**NinebotCrypto** `5A A5` Case 2 + 3-stage Handshake) → **funktioniert** nach Bug-Fix-Triple in Session 5.

**Drei kritische Bugs** verhinderten dass Frames vom Roller akzeptiert wurden — alle drei via "Patched-SHU mit `Log.d`-Logging"-Methode aufgedeckt (siehe FIELD-TEST-LOG Session 5):

1. **Key-Derivation**: SHU's `c.d()` kopiert die volle Source-Länge (kotlin `copyInto` mit flags=12 → endIndex=src.size), nicht nur die ersten 12 Bytes. Wir hatten 12 hardcoded.
2. **Per-Modul dst-Routing**: Der ZT3 Pro D hat MEHRERE BLE-Module mit eigenen Adressen.
3. **Speed-Limit-Register-Adresse**: `0x48` (= 72 dezimal), nicht `0x72` hex (= 114 dezimal). Wir hatten den Hex/Dezimal-Mismatch.

**Auflösung der Konflikt-Analyse**: Die HCI-Capture vom 2026-04-25 ([`2026-04-25-shu-flash-session.md`](../reverse-engineering/ble-captures/2026-04-25-shu-flash-session.md)) zeigt eindeutig Manufacturer-ID `0x434E` ("NC") und alle 3142 ATT-Payloads mit verschlüsseltem Body. SHU's `ScooterActivity.n():1046` wählt für Devices mit `usesCrypto=true` den `f0.a.NinebotCrypto`-Pfad — für ZT3 Pro D ist `usesCrypto=true`.

**Wire-Format (aus `c6/c.java#i()` direkt portiert):**

```
5A A5 [len] [src dst cmd arg ENC(payload)] [tag(4)] [ctrHi ctrLo]
       └─ payload-bytes only      └─ CBC-MAC, 4 byte    └─ 16-bit BE counter
```

**Schlüssel-Ableitung (`c6/c.java:199-205`, smali-flags-bitmask analysiert):**

```
salt = {0x97, 0xCF, 0xB8, 0x02, 0x84, 0x41, 0x43, 0xDE,
        0x56, 0x00, 0x2B, 0x3B, 0x34, 0x78, 0x0A, 0x5D}   # global Ninebot salt
buf  = scooterName(14B) ++ zeros(2) ++ salt(16B)          # 32-byte SHA-1 input
key  = SHA-1(buf)[0..16]                                   # full src.size copyInto, NOT 12-byte
```

**Per-Modul dst-Adressen** (ZT3 Pro D-spezifisch, via CRYPTO_DUMP verifiziert):

| dst | Modul | Verwendung |
|---|---|---|
| `0x04` | Cellular/IoT | Crypto-Handshake (cmd=0x5B/5C/5D), wenige reads (reg 0x01) |
| `0x16` | VCU | Speed-Limit (reg 0x48), Status-Reads (0x18/0x19/0x17/0xC0/0xE7/0xDA/0xE4) |
| `0x02` | ESC | manche Reads (reg 0xE4) |
| `0x07` | BMS? | reg 0x82 |

**Drei-Stage-Handshake** (matcht `ScooterActivity.java:484-514`):
1. **Stage 1** (`L`-Flag): App → Roller `[3E 04 5B 00]` plen=0 → Roller antwortet mit `5A A5 1E [rxAddr] 3E 5B [16 token + 14 challenge]`. App speichert Token, Key wird zu `SHA-1(name + token)`.
2. **Stage 2** (`M`-Flag): App → Roller `[3E 04 5C 00 + 16 random]` plen=0x10 — ODER bei Resume: App ruft intern `setRandomAppData(persisted_random)` (kein Frame nötig). Roller antwortet mit `5A A5 00 ... 3E 5C 01`. Key transitioniert zu `SHA-1(R + T)`.
3. **Stage 3** (`O`-Flag): App → Roller `[3E 04 5D 00 + 14 challenge]` plen=0x0E (Challenge ist die ASCII-Form des Scooter-Namens). Roller bestätigt mit `5A A5 00 ... 3E 5D 01`.

**SetSpeedLimit** (verifiziert via patched-SHU): `dst=0x16, cmd=0x02, arg=0x48, payload=[0x14, kmh]`.

**Code-Ort:**
- [`core/crypto/NinebotCrypto.kt`](app/src/main/kotlin/com/celox/segway/core/crypto/NinebotCrypto.kt) — port von `c6.c`
- [`core/ble/FrameCodecCrypto.kt`](app/src/main/kotlin/com/celox/segway/core/ble/FrameCodecCrypto.kt) — wrapper
- [`core/vehicle/Zt3ProVehicle.kt`](app/src/main/kotlin/com/celox/segway/core/vehicle/Zt3ProVehicle.kt) — `connect()` führt Handshake, `execute()` blockiert bis Handshake fertig

[`FrameCodecClassic.kt`](app/src/main/kotlin/com/celox/segway/core/ble/FrameCodecClassic.kt) (Plaintext-Pfad) bleibt für andere Modelle / Diagnose erhalten.

### Patched-SHU als Referenz-Tool

Das Reverse-Engineering der drei oben genannten Bugs erfolgte über eine **gepatchte SHU-APK mit `Log.d`-Injection**:

- Original-Smali: `reverse-engineering/apps/shu/decompiled/apktool/smali/c6/c.smali`
- Patch-Stelle: `i([B)[B` (encrypt-Methode), Zeile 1166. Block direkt nach der `kotlin.jvm.internal.m.e()`-Validation eingefügt, der per Base64 alle Crypto-Felder loggt.
- Build: `apktool b /tmp/shu-patched.apk` + `apksigner sign --ks ~/.android/debug.keystore`. Kein Root nötig.
- Lese-Pipeline: `adb logcat -s CRYPTO_DUMP -v time` zeigt für jeden TX `D=<base64> T=<token> R=<random> K=<aesKey> C=<counter>`.

So lassen sich jederzeit weitere SHU-Befehle byte-für-byte verifizieren (Mode-Wechsel, Lights, Lock, OTA-Chunks, etc.).

### Open items

1. **Übrige Commands gegen SHU verifizieren**: Mode-Wechsel (Eco/Drive/Sport), Lights, Lock, Cruise-Toggle — Register/dst sind aktuell noch unsere Annahmen, sollten via patched-SHU einmal jeweils gecaptured werden.
2. **Persisted-Random für andere Roller-MACs**: Aktuell ist `f5101e` für `C1:6B:5E:D0:C5:96` hardcoded. Für ein generisches App-Verteilen brauchen wir entweder einen sauberen Fresh-First-Pair-Flow (16-Byte-Random + Power-Button-OOB) oder eine UI um `f5101e` aus `CRYPTO_DUMP` per Hand einzutippen.
3. **Custom-Button-Firmware-Remapping** (Weg B): Aktuell beobachten wir nur den Hill-Hold-Notify und reagieren in der App (Weg A — funktioniert nur wenn Phone verbunden). Für eine permanente Roller-seitige Änderung müsste das Custom-Button-Mapping-Register gefunden werden — entweder via patched offizielle Segway-Mobility-App (NIS-Wrapper, deutlich aufwändiger) oder Trial-and-Error auf den verdächtigen Registern (`0x82` dst=0x07, `0xC0` dst=0x16). Risiko: „Set Speed Limit X km/h" ist möglicherweise gar keine valide Function-ID des Roller-Firmware — die Standard-Mappings sind Hill-Hold, Cruise, Headlight, Mode-Toggle, Lock.
4. **OTA-Chunk-ACK-Detection** robust machen (aktuell heuristisch)
5. **Token-Persistenz**: Mehrfache Disconnect/Reconnect-Tests — das `PairingPrefs.cryptoToken`-Feld wird beim Decrypt-Sucess gespeichert, sollte nach App-Restart resume-fähig sein.
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
