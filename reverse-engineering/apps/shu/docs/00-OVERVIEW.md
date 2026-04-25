# Scooter Hacking Utility (SHU) – Decompile Analysis

Statische Analyse von `ScooterHackingUtility-pre_release.open_beta-5.apk`. Im Gegensatz zur Ninebot-App ist diese **Open Source** (Quelle: `cfw.sh` / scooterhacking.org) und liefert **die komplette BLE-Pairing-Crypto sowie GATT-UUIDs im Klartext**.

## TL;DR

- **App**: ScooterHacking Utility (SHU)
- **Package**: `sh.cfw.utility.pre_release.open_beta`
- **Version**: `3.0+pre_release.open_beta` (Build 5)
- **Min/Target SDK**: 26 / 34 (Android 8 – 14)
- **Größe**: 5.7 MB (klein, da reine native Android-App ohne RN/Hybrid)
- **Schutz**: nur R8-Name-Obfuscation (keine Verschlüsselung). Die App-eigenen Klassen (`sh.cfw.utility.*`) sind **vollständig im Klartext**.
- **Build**: AGP 8.x, Kotlin 1.9.x, ohne Hermes/JS, ohne nativen Code (kein `lib/`)
- **Schwester-Tools** (in `<queries>` deklariert): `adriandp.m365dashboard`, `adriandp.ninedash`, `com.basse.scootbatt`

## Was diese App tut

SHU ist eine Custom-Firmware-Tool-Suite für Xiaomi/Ninebot-E-Scooter:

- BLE-Verbindung mit dem Scooter aufbauen (Nordic UART Service)
- ECDH-Pairing für moderne, verschlüsselte Ninebot-Modelle (G30, F-Series, ZT3 etc.)
- "SHFW" (Scooter Hacking Firmware) Profile lesen/schreiben
- Custom Firmware flashen, Settings verändern, Geschwindigkeitslimits umgehen
- Batterie-Daten und Logs lesen
- BeaconParser für Manufacturer-Daten (`FF 4E 42` / `FF 4E 43` = "NB"/"NC" – Ninebot Beacon Standard)

## Wichtigste Befunde (relevant für ZT3 Pro)

### BLE-GATT-Service-/Characteristic-UUIDs (Nordic UART Service)
```
Service:       6e400001-b5a3-f393-e0a9-e50e24dcca9e
RX (App→Roller, Write):    6e400002-b5a3-f393-e0a9-e50e24dcca9e
TX (Roller→App, Notify):   6e400003-b5a3-f393-e0a9-e50e24dcca9e
CCCD (Standard):           00002902-0000-1000-8000-00805f9b34fb
```

### Xiaomi Mi BLE (für M365/Pro – nicht ZT3 relevant)
```
Service:       0000fe95-0000-1000-8000-00805f9b34fb
Char A:        00000010-0000-1000-8000-00805f9b34fb
Char B:        00000019-0000-1000-8000-00805f9b34fb
```

### Crypto-Stack (für moderne Ninebot incl. ZT3 Pro vermutet)
- **Schlüsseleinigung**: ECDH auf Kurve `secp256r1` (NIST P-256, BouncyCastle / SpongyCastle)
- **Symmetrisch**: AES/CCM/NoPadding (24-Bit MAC, 4 Byte Random Nonce-Suffix)
- **KDF**: HKDF mit SHA-256
- **MAC**: HMAC-SHA-256
- **Frame-Magic**: `0x55 0xAB <len-1> <seq[2 LE]> <encrypted-payload> <checksum[2]>`
- **Checksum**: 16-Bit Negation-Sum (Zweier-Komplement der Byte-Summe)

### Persistente Pairing-State
Pro Scooter-MAC werden 3 Geheimnisse gespeichert (`SharedPreferences` → `ellipticConfiguration`):
- `deviceInfo` – Gerät-ID-Blob (vom Scooter beim Pairing erhalten)
- `deviceToken` – Authentication-Token
- `beaconKey` – AES-Key für Manufacturer-Specific-Data im Beacon

## Doku-Index

| Datei | Inhalt |
|---|---|
| [00-OVERVIEW.md](00-OVERVIEW.md) | Diese Datei |
| [01-MANIFEST.md](01-MANIFEST.md) | Manifest, Permissions, Komponenten |
| [02-BLE-PROTOCOL.md](02-BLE-PROTOCOL.md) | Nordic-UART, Frame-Format, Pairing-Flow, Crypto |
| [03-BACKEND.md](03-BACKEND.md) | `cfw.sh`-Backends, Repo-Format, Update-Mechanismus |
| [04-CODE-MAP.md](04-CODE-MAP.md) | Wegweiser durch das `sh.cfw.utility.*`-Package |
| [05-RELEVANCE-FOR-ZT3.md](05-RELEVANCE-FOR-ZT3.md) | Wie SHU für die ZT3-Pro-Analyse genutzt werden kann |

## Decompile-Output

```
shu/
├── ScooterHackingUtility-pre_release.open_beta-5.apk  # Original (5.7 MB)
├── decompiled/
│   ├── raw/      # ZIP-Extraktion (ca. 12 MB)
│   ├── apktool/  # Smali (7260 Klassen) + decoded resources
│   └── jadx/     # Java-Source (3645 Dateien, davon 70 echte sh.cfw.utility.*)
└── docs/         # diese Dokumentation
```

Tools: `apktool 2.12.1`, `jadx 1.5.3`. Kein Pack-Schutz – statische Analyse vollständig möglich.
