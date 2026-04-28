# SHU — BLE-Protokoll-Referenz für ZT3 Pro D

ScooterHacking Utility ist Open Source und R8-name-obfuskiert (klassisch dekompilierbar). Damit ist sie unsere primäre Wire-Protokoll-Quelle, weil die offizielle Ninebot-App via NetEase NIS gepackt ist.

> ⚠ **Wichtig**: SHU implementiert **zwei** Crypto-Pfade — den klassischen `0x5A 0xA5` NinebotCrypto und einen modernen ECDH/AES-CCM-Pfad mit Magic `0x55 0xAB`. Der ZT3 Pro D nutzt **ausschließlich den klassischen Pfad** (HCI-Capture verifiziert). Dieses Dokument konzentriert sich auf NinebotCrypto.

## GATT-Topologie

Aus `sh.cfw.utility.services.g.java`:

| UUID | Rolle |
|---|---|
| `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | Nordic UART Service |
| `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | RX (App→Roller, write-no-resp) |
| `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | TX (Roller→App, notify) |

## Advertising-Frame

Aus `sh.cfw.utility.classes.k.java`:

| Manufacturer-Bytes | ASCII | Bedeutung |
|---|---|---|
| `FF 4E 42` | `_NB` | Ninebot Stock (1-Byte ModelID, Plaintext) |
| `FF 4E 43` | `_NC` | **NinebotCrypto** (2-Byte ModelID, ZT3 Pro D) |

## Wire-Format (klassisch NinebotCrypto)

```
5A A5 [len] [src dst cmd arg ENC(payload)] [tag(4)] [ctrHi ctrLo]
       └─ inner-frame-bytes only         └─ CBC-MAC, 4 Byte    └─ 16-bit BE counter
```

`len` = Inner-Frame-Länge minus 4. Die ECUs (VCU=0x16, MCU=0x02, BMS=0x07) routen via `dst`-Byte; Phone identifiziert sich als `src=0x3E`.

## Schlüssel-Ableitung

Aus `c6/c.java:199-205`:

```
salt = {0x97, 0xCF, 0xB8, 0x02, 0x84, 0x41, 0x43, 0xDE,
        0x56, 0x00, 0x2B, 0x3B, 0x34, 0x78, 0x0A, 0x5D}
buf  = scooterName(14B) ++ zeros(2) ++ salt(16B)
key  = SHA-1(buf)[0..16]
```

> **Bug-Falle**: `c.d()` benutzt `kotlin.copyInto` mit Source-Length, nicht 12. Wer auf 12 hardcoded → Frames werden vom Roller verworfen.

## 3-Stage Handshake

Aus `ScooterActivity.java:484-514`:

| Stage | App→Roller | Roller→App | Effekt |
|---|---|---|---|
| 1 (`L`-Flag) | `[3E 04 5B 00]` | `5A A5 1E [rxAddr] 3E 5B [16 token + 14 challenge]` | Token captured, Key wird `SHA-1(name + token)` |
| 2 (`M`-Flag, **Fresh Pair**) | `[3E 04 5C 00 + 16 random]` | `5A A5 00 ... 3E 5C 01` | Random im Roller-NVRAM persistiert |
| 2 (Resume) | (kein Frame, App ruft `setRandomAppData(persisted)`) | – | Key transitioniert zu `SHA-1(R + T)` |
| 3 (`O`-Flag) | `[3E 04 5D 00 + 14 challenge]` | `5A A5 00 ... 3E 5D 01` | Vollständig paired |

Challenge in Stage 3 = ASCII-Bytes des Scooter-Namens.

## Per-Modul dst-Adressen

CRYPTO_DUMP-verifiziert (siehe [`../../../app/FIELD-TEST-LOG.md`](../../../app/FIELD-TEST-LOG.md) Session 5):

| dst | Modul | Verwendung |
|---|---|---|
| `0x04` | Cellular/IoT | Crypto-Handshake (`0x5B/5C/5D`), wenige Reads |
| `0x16` | VCU | Speed-Limit, Ride-State, Settings |
| `0x02` | MCU (ESC) | Motor-Telemetrie |
| `0x07` | BMS | Battery-Telemetrie + `charge_threshold` |

## Resume-Persistenz

Pro Scooter-MAC speichert SHU einen `cryptoRandom` (16 Byte) in den DataStore-Prefs (`ellipticConfiguration`). Beim Reconnect: Stage 1 läuft jedes Mal (Roller rotiert Token), Stage 2 wird durch direktes Setzen via `setRandomAppData(persisted)` ersetzt, Stage 3 läuft wieder. Effekt: ein einmaliger Fresh-Pair, danach ist kein Power-Button-Drücken mehr nötig.

## Kanonische Implementierungen

Statt direkter SHU-Smali-Lektüre, lies in dieser Reihenfolge:

1. [`../../../python/zt3_cli/crypto.py`](../../../python/zt3_cli/crypto.py) — kompakter, kommentierter Python-Port
2. [`../../../python/zt3_cli/handshake.py`](../../../python/zt3_cli/handshake.py) — Stage 1/2/3 inkl. Resume-Pfad
3. [`../../../app/app/src/main/kotlin/com/celox/segway/core/crypto/NinebotCrypto.kt`](../../../app/app/src/main/kotlin/com/celox/segway/core/crypto/NinebotCrypto.kt) — Kotlin-Production

Beide sind byte-genau gegen SHU-Wire-Output verifiziert via Patched-SHU-CRYPTO_DUMP-Methode.

## Reproduktion

```bash
brew install apktool jadx
cd reverse-engineering/apps/shu
apktool d -f -o decompiled/apktool ScooterHackingUtility-pre_release.open_beta-5.apk
jadx -d decompiled/jadx --no-res ScooterHackingUtility-pre_release.open_beta-5.apk
```

Schlüssel-Dateien: `c6/c.smali` (NinebotCrypto), `ScooterActivity.java` (Handshake-Orchestrierung), `services/g.java` (GATT-UUIDs).
