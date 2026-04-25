# ZT3 Pro – Reverse Engineering

Statische Analyse von Android-Apps rund um den Segway-Ninebot **ZT3 Pro** E-Scooter. Stand: 2026-04-25.

## Apps in diesem Projekt

| App | Package | Größe | Schutz | Doku |
|---|---|---|---|---|
| **Segway Mobility** (offizielle Ninebot-App) | `com.ninebot.segway` v7.6.3 | 116 MB | NetEase NIS Wrapper – Code verschlüsselt | [`apps/ninebot-segway/ANALYSIS.md`](apps/ninebot-segway/ANALYSIS.md) |
| **ScooterHacking Utility (SHU)** | `sh.cfw.utility.pre_release.open_beta` v3.0+pre_release.open_beta | 5.7 MB | nur R8-Name-Obfuscation, **Open Source** | [`apps/shu/ANALYSIS.md`](apps/shu/ANALYSIS.md) |

## Projekt-Struktur

```
reverse-engineering/
├── README.md                        # diese Datei
└── apps/
    ├── ninebot-segway/
    │   ├── com.ninebot.segway.apk
    │   ├── decompiled/              # gitignored
    │   └── ANALYSIS.md              # konsolidierte Analyse (Manifest, NetEase Shielding,
    │                                #   Network, SDKs, Assets, Components, Secrets,
    │                                #   Limitations & nächste Schritte)
    └── shu/
        ├── ScooterHackingUtility-pre_release.open_beta-5.apk
        ├── decompiled/              # gitignored
        └── ANALYSIS.md              # konsolidierte Analyse (Manifest, BLE, Crypto, Backend, Code-Map, ZT3-Bezug)
```

## Quick-Reference – ZT3 Pro BLE-Stack (synthese aus beiden Apps)

| Aspekt | Wert | Quelle |
|---|---|---|
| BLE-Service | Nordic UART `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | SHU `services/g.java` |
| RX-Char (App→Scooter) | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | SHU `services/g.java` |
| TX-Char (Scooter→App) | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | SHU `services/g.java` |
| Adv-Manufacturer-Prefix | `FF 4E 43` ("NC", Crypto-Variante) | SHU `classes/k.java` |
| Frame-Magic | `0x55 0xAB` | SHU `crypto/elliptic/h.java` |
| ECDH-Kurve | secp256r1 (NIST P-256) | SHU `crypto/elliptic/d.java` |
| Symm. Verschlüsselung | AES-128/CCM, 24-Bit MAC | SHU `crypto/elliptic/d.java` |
| KDF | HKDF-SHA-256 | SHU `crypto/elliptic/d.java` |
| MAC | HMAC-SHA-256 | SHU `crypto/elliptic/d.java` |
| Init-Hello | `0x00 ++ "blt.4.159" ++ rand[10]` | SHU `crypto/elliptic/d.java` |
| Frame-Checksum | 16-Bit Inverted-Sum | SHU `crypto/elliptic/d.java` |

## Top-Findings über beide Apps hinweg

### 🔴 Kritisch (Ninebot)
- **Mapbox Secret-Token** (`sk.…`) hartcodiert im Manifest (Details siehe `apps/ninebot-segway/ANALYSIS.md#hartcodierte-secrets`)
- App komplett mit NetEase NIS gepackt – BLE-Crypto-Code aus statisch nicht extrahierbar

### 🟢 Positiv (SHU)
- **Vollständig Open Source** – ECDH-Pairing-Code im Klartext lesbar
- Saubere Crypto-Wahl (P-256 + AES-CCM + HKDF + HMAC-SHA-256)
- Keine Secrets, keine Telemetrie

### 📌 Synthese
Die SHU-App liefert genau die Information, die in der Ninebot-App fehlt: das BLE-Pairing-Protokoll. **Update April 2026**: Die in diesem Repo enthaltene SHU-Beta-APK (`pre_release.open_beta-5`) unterstützt die x3-Reihe (G3, ZT3, F3) sogar offiziell – via FLASH-Repo-Workflow und Region-Change auf US. Voraussetzung: VPN außerhalb der EU, Android-Phone. Anleitung: [`UNLOCK-PLAN.md`](../UNLOCK-PLAN.md) Phase 1.

## Reproduktion

Tools: `apktool 2.12.1`, `jadx 1.5.3`. Beide APKs sind unverändert in den jeweiligen Verzeichnissen archiviert.

```bash
# Beispiel: Ninebot
cd apps/ninebot-segway
apktool d -f -o decompiled/apktool com.ninebot.segway.apk
jadx -d decompiled/jadx --no-res com.ninebot.segway.apk

# Beispiel: SHU
cd apps/shu
apktool d -f -o decompiled/apktool ScooterHackingUtility-pre_release.open_beta-5.apk
jadx -d decompiled/jadx --no-res ScooterHackingUtility-pre_release.open_beta-5.apk
```
