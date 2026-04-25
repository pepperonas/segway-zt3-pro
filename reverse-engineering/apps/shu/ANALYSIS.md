# 🛴 ScooterHacking Utility (SHU) – Decompile-Analyse

[![App: SHU](https://img.shields.io/badge/App-ScooterHacking%20Utility-3DDC84?logo=android&logoColor=white)](https://utility.cfw.sh/)
[![Version](https://img.shields.io/badge/version-3.0+pre__release.open__beta--5-blue)](#)
[![License: Open Source](https://img.shields.io/badge/license-Open%20Source-success)](https://utility.cfw.sh/)
[![Pack Protection: None](https://img.shields.io/badge/pack--protection-none-success)](#)
[![Obfuscation: R8 (names only)](https://img.shields.io/badge/obfuscation-R8%20names--only-yellow)](#)
[![Java Files](https://img.shields.io/badge/java%20files-3645%20(70%20app--code)-blue)](#)
[![APK Size](https://img.shields.io/badge/apk%20size-5.7%20MB-lightgrey)](#)

> Im Gegensatz zur offiziellen Ninebot-App ist diese **vollständig analysierbar**. Sie liefert die komplette **BLE-Pairing-Crypto** sowie die **GATT-UUIDs im Klartext** – die Goldmine für die ZT3-Pro-Recherche.

---

## Inhaltsverzeichnis

1. [TL;DR](#tldr)
2. [Was die App tut](#was-die-app-tut)
3. [Manifest & Komponenten](#manifest--komponenten)
4. [BLE-Protokoll (Hauptbefund)](#ble-protokoll-hauptbefund)
5. [Crypto-Stack im Detail](#crypto-stack-im-detail)
6. [Pairing-Flow](#pairing-flow)
7. [Backend (`cfw.sh`)](#backend-cfwsh)
8. [Code-Map](#code-map)
9. [Bedeutung für den ZT3 Pro](#bedeutung-für-den-zt3-pro)
10. [Quellen](#quellen)

---

## TL;DR

| Aspekt | Wert |
|---|---|
| **App** | ScooterHacking Utility (SHU) |
| **Package** | `sh.cfw.utility.pre_release.open_beta` |
| **Version** | `3.0+pre_release.open_beta` (Build 5) |
| **Min/Target SDK** | 26 / 34 (Android 8 – 14) |
| **APK-Größe** | 5.7 MB |
| **Build** | AGP 8.x, Kotlin 1.9.x, ohne Hermes/JS, kein nativer Code (kein `lib/`) |
| **Schutz** | Nur R8-Name-Obfuscation – keine Verschlüsselung. App-Code (`sh.cfw.utility.*`) komplett im Klartext. |
| **Analyse-Tools** | apktool 2.12.1, jadx 1.5.3 |
| **Smali-Klassen** | 7260 |
| **Decompiled Java** | 3645 Files (davon 70 echte `sh.cfw.utility.*`, Rest sind Libraries) |

---

## Was die App tut

SHU ist eine **Custom-Firmware-Tool-Suite** für Xiaomi/Ninebot-E-Scooter aus der ScooterHacking-Community:

- BLE-Verbindung mit dem Scooter aufbauen (Nordic UART Service)
- **ECDH-Pairing** für moderne, verschlüsselte Ninebot-Modelle (G30, F-Series, ZT3 etc.)
- "SHFW" (Scooter Hacking Firmware) Profile lesen / schreiben / flashen
- Custom Firmware installieren, Settings ändern, Speed-Limits umgehen
- Batterie-Daten und Logs auslesen
- BeaconParser für Manufacturer-Specific-Data (`FF 4E 42` / `FF 4E 43` = "NB" / "NC" – Ninebot Beacon Standard)

Schwester-Tools (im `<queries>`-Block deklariert):

- `adriandp.m365dashboard`
- `adriandp.ninedash`
- `com.basse.scootbatt`

---

## Manifest & Komponenten

### Permissions (11 Stück, alle sinnvoll)

```
BLUETOOTH (≤30), BLUETOOTH_ADMIN (≤30), BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_CONNECT
ACCESS_COARSE_LOCATION (≤30), ACCESS_FINE_LOCATION (≤30)
INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE
WRITE_EXTERNAL_STORAGE, REQUEST_INSTALL_PACKAGES
```

`<uses-feature android:name="android.hardware.bluetooth_le" required="true"/>` – BLE ist Pflicht.

### Komponenten

| Typ | Name | Rolle |
|---|---|---|
| **Activity** (Launcher) | `UpdateCheckerActivity` | Update-Check, EULA, Track-Status |
| Activity | `EnrollActivity` | Beta-Track-Enrollment |
| Activity | `ScannerActivity` | BLE-Scan-Liste |
| Activity | `ScooterActivity` | Haupt-UI (1451 Java-Zeilen) |
| Service | `SerialService` | Foreground-Service, BLE-Bridge |
| Service | `ScannerService` (Nordic-Lib) | BLE-Scanner |
| Receiver ⚠ | `MajsiHomeReceiver` | exported, `permission="TODO"` (Platzhalter, nicht gesetzt!) – Inter-App-Bridge |
| Receiver ⚠ | `BroadcastSHFWProfileNamesByScooterUidReceiver` | exported, ebenfalls `permission="TODO"` |
| Provider | `FileProvider` (×2) | Doppelt registriert – Konflikt, aber harmlos |

### Network-Security-Config

```xml
<domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="false">192.168.1.7</domain>
</domain-config>
```

Genau eine LAN-IP – **leeres Dev-Relikt**. Keine echten Cleartext-Domains. Keine Secrets im Manifest.

### Inter-App-Communication

```xml
<queries>
    <package android:name="adriandp.m365dashboard"/>
    <package android:name="adriandp.ninedash"/>
    <package android:name="com.basse.scootbatt"/>
</queries>
```

→ Plugin-System der Custom-Scooter-Community.

### Vergleich zur Ninebot-App

| Aspekt | Ninebot Segway | SHU |
|---|---|---|
| APK-Größe | 116 MB | 5.7 MB |
| Activities | 197 | 4 |
| Services | 54 | 2 |
| Permissions | 47 | 11 |
| Hartcodierte Secrets | viele (Mapbox `sk.`, HERE, Bugsnag, …) | **keine** |
| Pack-Schutz | NetEase NIS | **keiner** |
| Code im Klartext | ~30 Wrapper-Klassen | **komplettes `sh.cfw.utility.*`** |

---

## BLE-Protokoll (Hauptbefund)

### GATT-Service-Topologie

Konstanten aus `sh.cfw.utility.services.g.java`:

| UUID | Service / Characteristic |
|---|---|
| `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | **Nordic UART Service** (NUS) — alle modernen Ninebot/Segway |
| `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | NUS RX (App → Roller, **Write Without Response**) |
| `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | NUS TX (Roller → App, **Notify**) |
| `00002902-0000-1000-8000-00805f9b34fb` | CCCD (Standard) – Notifications enablen |
| `0000fe95-0000-1000-8000-00805f9b34fb` | Xiaomi Mi Service – nur klassische M365 |
| `00000010-0000-1000-8000-00805f9b34fb` | Xiaomi Mi Char A – nur M365 |
| `00000019-0000-1000-8000-00805f9b34fb` | Xiaomi Mi Char B – nur M365 |

> **Für ZT3 Pro relevant**: ausschließlich die NUS-UUIDs.

### Advertising-Frame-Format

Aus `sh.cfw.utility.classes.k.java`:

| Manufacturer-Prefix (hex) | ASCII | Bedeutung |
|---|---|---|
| `FF 4E 42` | `_NB` | "Ninebot" – klassisch, 1 Byte Modell-ID |
| `FF 4E 43` | `_NC` | "Ninebot Crypto" – neuer, 2 Byte Modell-ID, signalisiert Crypto-Support |

Frame-Layout nach Prefix (6 Byte):

| Bytes | NB-Format (klassisch) | NC-Format (Crypto) |
|---|---|---|
| 0-1 | `[modelID, ?]` | `[modelID_hi, modelID_lo]` |
| 2 | `?` | feature flag (== 2 → Crypto-Pairing aktiv) |
| 3 | feature flag | `?` |
| 4 | `?` | `0` (sentinel) |
| 5 | `0` (sentinel) | `?` |

Modell-IDs werden gegen eine **JSON-Liste** (`bootstrap.zip` vom Backend `apps-content.cfw.sh`) abgeglichen, mit Feldern wie `id`, `model`, `humanReadable`, `isXiaomi`, `imageDrawable`, `requestMTU`.

### Application-Layer-Frame-Struktur (auf NUS RX/TX)

```
+---+---+----------+-------+-------+------------------+----+----+
|55 |AB | len-1    | seq_l | seq_h | encrypted_body   | cs1| cs2|
+---+---+----------+-------+-------+------------------+----+----+
 0   1   2          3       4       5..N               N+1  N+2
```

| Feld | Größe | Inhalt |
|---|---|---|
| **Magic** | 2 B | `0x55 0xAB` (fixed) |
| **Length** | 1 B | `payload.length - 1` |
| **Seq** | 2 B LE | Counter, beginnt bei 0 |
| **Body** | N B | AES/CCM-verschlüsselt |
| **Checksum** | 2 B LE | Inverted-Sum 16-Bit über Bytes 2…N |

#### Inverted-Sum-Checksum (klassisch m365)

```java
short s = 0;
for (byte b : data) s += (b & 0xFF);
short cs = ~s;
return [(byte)(cs & 0xFF), (byte)(cs >> 8 & 0xFF)];
```

---

## Crypto-Stack im Detail

Aus `sh.cfw.utility.crypto.elliptic.d.java` extrahiert. **Eindeutig moderne Wahl.**

| Primitive | Algorithmus | Implementation |
|---|---|---|
| **Schlüsseleinigung** | ECDH auf `secp256r1` (NIST P-256) | BouncyCastle / SpongyCastle |
| **Symmetrische Verschlüsselung** | AES-128 / CCM / NoPadding | 24-Bit MAC (3 Byte Tag) |
| **Key-Derivation** | HKDF mit SHA-256 | SpongyCastle HKDF |
| **MAC** | HMAC-SHA-256 | Standard |
| **Pubkey-Encoding für BLE** | unkomprimierter Punkt ohne 0x04-Prefix | 64 Bytes raw `X \|\| Y` |
| **Init-Hello** | `0x00 ++ "blt.4.159" ++ rand[10 lowercase alnum]` | Ninebot-2nd-Gen-Standard |

### Persistente Pairing-State

Pro Scooter-MAC werden in `SharedPreferences` (Key `ellipticConfiguration`) drei Byte-Arrays gespeichert:

```json
{
  "ssid":        "AA:BB:CC:DD:EE:FF",
  "deviceInfo":  [0x..., 0x..., ...],
  "deviceToken": [0x..., 0x..., ...],
  "beaconKey":   [0x..., 0x..., ...]
}
```

| Feld | Zweck |
|---|---|
| `deviceInfo` | Gerät-ID-Blob, vom Scooter beim Pairing erhalten |
| `deviceToken` | Authentication-Token für Folge-Sessions (TOFU) |
| `beaconKey` | AES-Key zum Entschlüsseln des Manufacturer-Specific-Data im Adv-Frame |

→ Der **ECDH-Handshake muss nur einmal pro App-Install** durchlaufen werden. Folge-Verbindungen reauth-en über `deviceToken`.

---

## Pairing-Flow

Rekonstruiert aus `crypto.elliptic.h.java` + bekanntem Ninebot-2nd-Gen-Verhalten:

```
1. GATT Connect → Service Discovery für NUS
2. CCCD auf TX-Char (6e400003) schreiben → Notifications enable
3. App schreibt Init-Hello auf RX-Char (6e400002):
     0x00 ++ "blt.4.159" ++ rand[10]
4. Scooter antwortet mit eigenem ECDH-Pubkey + Random-Challenge
5. App schickt eigenen Pubkey + HMAC-Bestätigung
6. Scooter antwortet mit (deviceInfo + deviceToken + beaconKey),
   verschlüsselt mit dem geleiteten Schlüssel
7. App entschlüsselt, persistiert in SharedPreferences
8. Folge-Frames nutzen 0x55 0xAB-Wrapper mit AES/CCM
```

**Bekannte Dialekte** (3 Pairing-Varianten in SHU sichtbar):

- **Klassisch (Xiaomi M365)** – kein Crypto, einfache Frames mit `0x55 0xAA` (statt `0xAB`!)
- **Ninebot 1st-Gen** – KeyExchange mit fixem Default-Key
- **Ninebot 2nd-Gen** – ECDH+AES-CCM (oben beschrieben), in der App `elliptic` genannt — **ZT3 Pro fällt hier rein**

---

## Backend (`cfw.sh`)

Kompakte Backend-Infrastruktur unter Domain `cfw.sh` ("CFW" = Custom Firmware).

| URL | Zweck | Aufrufer |
|---|---|---|
| `https://apps-data.cfw.sh/utility/update` | Update-Manifest abfragen (POST) | `UpdateCheckerActivity` |
| `https://apps-data.cfw.sh/utility/download_apk` | Self-Update-APK | `UpdateCheckerActivity` |
| `https://apps-content.cfw.sh/repo/v4/<name>.zip` | **Scooter-Modell-DB** + Asset-Bundles | `c0.java:168` |
| `https://apps-data.cfw.sh/shfw/v8/{config,fetch,releases}` | SHFW-Endpoints | SHFW-Modul |
| `https://utility.cfw.sh/` | Web-Landingpage | Disclaimer-Dialog |
| `https://cfw.sh/eula` | EULA | – |
| `https://scooterhack.in/bugreport` | Bug-Tracker | – |
| `https://scooterhack.in/shutprivacy` | Privacy-Policy | – |
| `mailto:scamwatch@scooterhacking.org` | Scam-Meldung | `j.java:138` |

### Repo-Schema

Die App lädt `bootstrap.zip` ins App-internen Storage und liest daraus eine `beacons.json` mit Modell-Definitionen. Damit ist die **Modell-Datenbank dynamisch updatbar**, ohne neue APK.

```bash
# Selbst prüfen, ob ZT3 Pro im Repo bekannt ist:
curl -o bootstrap.zip https://apps-content.cfw.sh/repo/v4/bootstrap.zip
unzip -p bootstrap.zip beacons.json | jq '.[] | select(.humanReadable | test("ZT3"; "i"))'
```

---

## Code-Map

### Architektur

```
                                ┌────────────────────────┐
                                │ UpdateCheckerActivity  │  ← Launcher
                                └──────────┬─────────────┘
                                           │ "Continue"
                                ┌──────────▼─────────────┐
                                │   ScannerActivity      │  ← BLE-Scan, BeaconParser
                                └──────────┬─────────────┘
                                           │ Tap Scooter
                                ┌──────────▼─────────────┐
                                │   ScooterActivity      │  ← Haupt-UI (1451 Z.)
                                └──────────┬─────────────┘
                                           │ bindService
                                ┌──────────▼─────────────┐
                                │   SerialService        │  ← Foreground-Service
                                │ (BluetoothGattCallback in services/g.java)
                                └──────────┬─────────────┘
                                           │ NUS RX/TX
                              ┌────────────▼─────────────┐
                              │  crypto.elliptic.e       │  ← Pairing-Facade
                              └────────────┬─────────────┘
                                           │
                              ┌────────────▼─────────────┐
                              │  crypto.elliptic.h       │  ← State-Machine
                              └────────────┬─────────────┘
                                           │
                              ┌────────────▼─────────────┐
                              │  crypto.elliptic.d       │  ← Crypto-Primitive
                              └──────────────────────────┘
```

### Schlüssel-Dateien (im jadx-Output)

| Datei | LoC | Rolle |
|---|---|---|
| `sh.cfw.utility.crypto.elliptic.d.java` | 213 | **Crypto-Primitive** (ECDH, AES/CCM, HKDF, HMAC, Checksum) |
| `sh.cfw.utility.crypto.elliptic.h.java` | 867 | **State-Machine** (Pairing-Stages B/C/D/F/H/J – Coroutines) |
| `sh.cfw.utility.crypto.elliptic.i.java` | 633 | Basisklasse für `h` (Frame-Aufbau) |
| `sh.cfw.utility.crypto.elliptic.b.java` | 341 | BLE-IO-Wrapper über GATT |
| `sh.cfw.utility.crypto.elliptic.e.java` | 421 | Public-Facade (`init`, `encrypt`, `decrypt`) |
| `sh.cfw.utility.crypto.elliptic.EllipticPreferences.java` | 145 | Persistenz pro MAC |
| `sh.cfw.utility.classes.k.java` | 195 | **BeaconParser** (NB/NC-Frame-Decoder) |
| `sh.cfw.utility.classes.i0.java` | – | Singleton-Repo für `bootstrap.zip` |
| `sh.cfw.utility.services.g.java` | – | GATT-Callback (UUID-Konstanten) |
| `sh.cfw.utility.services.SerialService.java` | 328 | Foreground-Service der BLE-Bridge |
| `sh.cfw.utility.activities.ScooterActivity.java` | 1451 | Haupt-UI |

### Verwendete Bibliotheken

| Library | Zweck |
|---|---|
| AndroidX (activity, appcompat, biometric, camera2, core, lifecycle, navigation, room, work, …) | Standard |
| Kotlin Coroutines | für Pairing-State-Machine |
| OkHttp3 + Public-Suffix-DB | HTTP |
| Gson | JSON |
| **SpongyCastle** (`org.spongycastle.*`) | Crypto-Provider |
| Nordic BLE Scanner (`no.nordicsemi.android.support.v18.scanner`) | Robuster Scanner |
| AltBeacon | iBeacon-style Detection |
| Material Components | UI |
| Material-About-Library, Iconics, MaterialDrawer, AttributionPresenter, Changelog | UI Frameworks |

### Decompile-Qualität-Hinweis

~6 Methoden in `h.java` wurden **incomplete decompiled** (`Method dump skipped`) – die Pairing-Stages. Für die exakten HKDF-Salts/Infos und Nonce-Strukturen bei Bedarf neu mit `jadx --show-bad-code --comments-level debug` laufen lassen oder direkt Smali in `decompiled/apktool/smali/sh/cfw/utility/crypto/elliptic/h.smali` lesen.

---

## Bedeutung für den ZT3 Pro

### Hypothese

Der **ZT3 Pro** verwendet das **gleiche BLE-Protokoll wie alle modernen Ninebot-Crypto-Scooter** (G30 MAX, F-Series, F2, ZT3, …). Quelle: vollständige SHU-Decompile.

### Verifikation – einfache Tests

| Test | Tool | Erwartung |
|---|---|---|
| **Beacon-Sniffing** | nRF Connect / `bleak` | Manufacturer-Data startet mit `4E 43` (NC) |
| **GATT-Service-Discovery** | nRF Connect | Service `6e400001-b5a3-f393-e0a9-e50e24dcca9e` mit `…0002` Write + `…0003` Notify |
| **Modell-DB-Check** | `bootstrap.zip` herunterladen | ZT3 Pro in `beacons.json` enthalten? |

```bash
curl -o bootstrap.zip https://apps-content.cfw.sh/repo/v4/bootstrap.zip
unzip -p bootstrap.zip beacons.json | jq '.[] | select(.humanReadable | test("ZT3|Z3T"; "i"))'
```

> ⚠ **Achtung**: Das [bastelpichi-Wiki](https://wiki.bastelpichi.de/compatibility.html) listet ZT3 Pro **explizit als nicht unterstützt** für SHU/SHFW. Kein direkter Use-Case mit dieser App – aber das Protokoll-Wissen daraus ist wertvoll für eigene Tools (siehe `UNLOCK-PLAN.md`, Phase 3 mit ZT3Tools).

### Pairing-Skelett für eigene Python-Tools

```python
import asyncio, os, random, string
from bleak import BleakClient
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.ciphers.aead import AESCCM

NUS_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
NUS_RX      = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"   # write
NUS_TX      = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"   # notify

def init_hello() -> bytes:
    rnd = "".join(random.choices(string.ascii_lowercase + string.digits, k=10))
    return b"\x00" + b"blt.4.159" + rnd.encode()

def inv_sum_checksum(data: bytes) -> bytes:
    s  = sum(data) & 0xFFFF
    cs = (~s) & 0xFFFF
    return bytes([cs & 0xFF, (cs >> 8) & 0xFF])

def wrap_frame(seq: int, body: bytes) -> bytes:
    payload = bytes([len(body) + 4, seq & 0xFF, (seq >> 8) & 0xFF]) + body
    head    = b"\x55\xAB" + payload
    return head + inv_sum_checksum(head[2:])

async def pair(mac: str):
    async with BleakClient(mac) as client:
        rx_q = asyncio.Queue()
        await client.start_notify(NUS_TX, lambda c, d: rx_q.put_nowait(bytes(d)))

        # 1. ECDH key generation (secp256r1)
        priv = ec.generate_private_key(ec.SECP256R1())
        pub  = priv.public_key().public_bytes(
            serialization.Encoding.X962,
            serialization.PublicFormat.UncompressedPoint
        )[1:]   # strip 0x04 → 64 raw bytes

        # 2. send init hello
        await client.write_gatt_char(NUS_RX, init_hello())
        scooter_pub_resp = await rx_q.get()

        # 3. … ECDH + HKDF + send pubkey + verify HMAC + receive deviceInfo/Token/beaconKey
        # exakte Salt/Info-Strings aus crypto/elliptic/h.smali oder via HCI-Snoop
```

→ Für die **exakten** HKDF-Salt/Info-Strings und Nonce-Konstruktion: entweder besseren jadx-Pass machen oder ein HCI-Snoop-Capture eines bekannten Pairings mit der offiziellen App.

### Warum SHU für ZT3 generell interessant ist

| Aspekt | Ninebot Segway | SHU |
|---|---|---|
| Account-Pflicht | ja, Cloud-Login | nein |
| Cloud-Telemetrie | ja | nein |
| Speedlimit-Override | nein | ja (über SHFW) |
| Custom Profile | nein | ja |
| Source verfügbar | nein, gepackt | **ja** |
| Funktioniert offline | nein (Login) | ja (nach 1× bootstrap) |
| ZT3 Pro Support | offiziell ja | aktuell nein, aber Protokoll-Wissen nutzbar |

---

## Quellen

### Tools / Repos
- [scooterteam/ZT3Tools](https://github.com/scooterteam/ZT3Tools/) – ST-Link-Toolchain (archiviert Juli 2025)
- [lekrsu/shfw-walkthrough](https://github.com/lekrsu/shfw-walkthrough) – Open-Source-Walkthrough generischer
- [bastelpichi.de Kompatibilitäts-Wiki](https://wiki.bastelpichi.de/compatibility.html)
- [ScooterHacking Utility offiziell](https://utility.cfw.sh/)
- [XiaoDash für ZT3](https://www.xiaodash.app/zt3)

### Reproduktion der Analyse

```bash
# Aus dem Repo-Root:
cd reverse-engineering/apps/shu
apktool d -f -o decompiled/apktool ScooterHackingUtility-pre_release.open_beta-5.apk
jadx -d decompiled/jadx --no-res ScooterHackingUtility-pre_release.open_beta-5.apk
```

→ Decompile-Output ist gitignored (~110 MB), wird zur Laufzeit erzeugt.
