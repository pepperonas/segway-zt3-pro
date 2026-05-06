# ZT3 Pro D — Reverse-Engineering & Companion App

Native Android-App + Mac-CLI für den **Segway-Ninebot ZT3 Pro D**, mit kompletter NinebotCrypto-Implementation und Live-BLE-Telemetrie. Aktueller Schwerpunkt: ESP32-MitM, um den Roller mit eigenen Funktionen auszustatten ohne die Stock-Firmware zu modifizieren.

> ⚠ Privatgelände-Use only. Tuning eines StVZO-Rollers verwirkt Betriebserlaubnis, Versicherung, Garantie.

---

## Inhalt

| Pfad | Beschreibung |
|---|---|
| [`app/`](app/) | Android-App (Kotlin · Compose · Hilt). Speed-Profile, Stealth-Lock, Live-Telemetrie, OTA, OSM-Track. Setup: [`app/README.md`](app/README.md) |
| [`python/`](python/) | Mac/Linux BLE-CLI `zt3-cli` — Python-Port der NinebotCrypto. `read`/`write`/`sweep`/`watch`/`hunt` zur Register-Discovery. Setup: [`python/README.md`](python/README.md) |
| [`esp32/`](esp32/) | (geplant) Stem-Bus-Bridge — ESP32-C3 im Dashboard-Gehäuse, sniffed Single-wire UART zwischen Dashboard und VCU, BLE-Central für Lock-Trigger, optional 2. Knoten im Deck via ESP-NOW. Roadmap: [`ESP32-BRIDGE-PLAN.md`](ESP32-BRIDGE-PLAN.md) |
| [`can-bus/`](can-bus/) | **Externer VCU-CAN-Bus Reverse-Engineering** — Logic-Analyzer-Workflow, Frame-Reference, Python-Parser. Plain-CAN, keine Crypto. Status: Throttle/Bremse/Speed gemappt. Index: [`can-bus/README.md`](can-bus/README.md) |
| [`can-data/`](can-data/) | KingstVIS CSV-Captures (eine Datei pro isolierter Aktion am Roller) |
| [`reverse-engineering/`](reverse-engineering/) | BLE-Wire-Format-Doku, Capture-Methodik, SHU-Crypto-Analyse |
| [`FLASH-NOTES.md`](FLASH-NOTES.md) | SHU-Beta-Workflow für 40 km/h + Region-Change US (so habe ich es selbst gemacht) |

## Quick-Reference — ZT3 Pro D BLE-Stack

| Aspekt | Wert |
|---|---|
| BLE-Service | Nordic UART `6e400001-b5a3-f393-e0a9-e50e24dcca9e` |
| RX-Char (App→Roller) | `6e400002-…` (write-no-resp) |
| TX-Char (Roller→App) | `6e400003-…` (notify) |
| Adv-Manufacturer-Prefix | `FF 4E 43` ("NC" = NinebotCrypto) |
| Frame-Magic | `0x5A 0xA5` (klassisch NinebotCrypto, **bestätigt für ZT3 Pro D**) |
| Symm. Verschlüsselung | AES-128 CBC-MAC + AES-CTR, Salt + SHA-1-derived Session-Key |
| Pairing-Flow | 3-stage Stage 1/2/3 (`0x5B`/`0x5C`/`0x5D`), Resume via `setRandomAppData` |

Komplette Register-Map: [`reverse-engineering/protocol/zt3-ble-register-reference.md`](reverse-engineering/protocol/zt3-ble-register-reference.md). Settings-Register (XiaoDash-equivalent): [`reverse-engineering/protocol/zt3-settings-registers.md`](reverse-engineering/protocol/zt3-settings-registers.md).

## App-Status (2026-04-28)

Production-Ready auf realer ZT3 Pro D Hardware. Highlights:

- **NinebotCrypto byte-genau** verifiziert via patched-SHU CRYPTO_DUMP-Methode
- **Per-MAC Resume** (kein Power-Button-Drücken nach App-Restart)
- **Speedometer + Live-Trip-Card** (Distanz, Max, Ø, Energie, GPS-Cross-Check)
- **Stealth-Unlock** via Vol-Down-3× / Vol-Up-3× (auch Screen-Off)
- **Custom-Button-Doppel-Tap** → Lock-Profil (Walk/Park/Hill-Hold/KERS)
- **Roller-Einstellungen** mit 17 Bitfield-Toggles + 4 Slidern + 3 Enums (byte-genau aus SHU-Bootstrap)
- **Multi-Vehicle-Garage** + **OTA-Flash** + **Region-Change** + **AirLock**
- **OSM-Karte** + **GPS-Track-Recording**
- **Trip-History-DB** mit Auto-Session-Detection (BLE-only, kein GPS nötig)

Detail: [`app/README.md`](app/README.md). Field-Test-Historie: [`app/FIELD-TEST-LOG.md`](app/FIELD-TEST-LOG.md).

## Setup für Entwicklung

```bash
# Android-App
cd app && ./gradlew :app:installDebug

# Mac-CLI
cd python && python3 -m venv .venv && source .venv/bin/activate
pip install -e .
zt3 scan
```

Workflow Mac-CLI (resume-only, **niemals fresh-pair vom Mac**):
1. In Android-App pairen
2. `adb shell run-as io.celox.zt3fxx.debug cat files/datastore/pairing.preferences_pb` → `cryptoRandom` extrahieren
3. `zt3 import-random "<base64>" --mac <CoreBluetoothUUID>`
4. `zt3 connect` läuft den Resume-Path

## Repo-Konventionen

- APKs (Ninebot, SHU) sind via Git LFS versioniert — `git lfs install` vor Klon
- `decompiled/`, `node_modules/`, `build/`, `.gradle/` sind gitignored
- Changelog der App: [`app/app/src/main/kotlin/com/celox/segway/feature/manual/Changelog.kt`](app/app/src/main/kotlin/com/celox/segway/feature/manual/Changelog.kt) — eine Zeile pro user-perceptible Change

---

🛴 © 2026 Martin Pfeffer · MIT License
