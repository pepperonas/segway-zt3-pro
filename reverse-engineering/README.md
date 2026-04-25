# ZT3 Pro – Reverse Engineering

Statische Analyse von Android-Apps rund um den Segway-Ninebot **ZT3 Pro** E-Scooter. Stand: 2026-04-25.

## Apps in diesem Projekt

| App | Package | Größe | Schutz | Doku |
|---|---|---|---|---|
| **Segway Mobility** (offizielle Ninebot-App) | `com.ninebot.segway` v7.6.3 | 116 MB | NetEase NIS Wrapper – Code verschlüsselt | [`apps/ninebot-segway/docs/`](apps/ninebot-segway/docs/) |
| **ScooterHacking Utility (SHU)** | `sh.cfw.utility.pre_release.open_beta` v3.0+pre_release.open_beta | 5.7 MB | nur R8-Name-Obfuscation, **Open Source** | [`apps/shu/docs/`](apps/shu/docs/) |

## Projekt-Struktur

```
reverse-engineering/
├── README.md                        # diese Datei
└── apps/
    ├── ninebot-segway/
    │   ├── com.ninebot.segway.apk
    │   ├── decompiled/
    │   │   ├── raw/                 # ZIP-Extraktion
    │   │   ├── apktool/             # smali + decoded resources
    │   │   ├── jadx/                # Java-Source (nur Wrapper-Klassen sichtbar)
    │   │   └── extracted/           # entpackte Sub-Archive
    │   └── docs/                    # 10 Dokumente
    │       ├── README.md
    │       ├── 00-OVERVIEW.md
    │       ├── 01-MANIFEST.md
    │       ├── 02-NETEASE-SHIELDING.md
    │       ├── 03-NETWORK-ENDPOINTS.md
    │       ├── 04-SDKS-LIBRARIES.md
    │       ├── 05-ASSETS-INVENTORY.md
    │       ├── 06-COMPONENTS.md
    │       ├── 07-SECRETS-FOUND.md
    │       └── 08-LIMITATIONS-NEXT-STEPS.md
    └── shu/
        ├── ScooterHackingUtility-pre_release.open_beta-5.apk
        ├── decompiled/
        │   ├── raw/
        │   ├── apktool/
        │   └── jadx/
        └── docs/                    # 6 Dokumente
            ├── 00-OVERVIEW.md
            ├── 01-MANIFEST.md
            ├── 02-BLE-PROTOCOL.md   ← Nordic-UART, Frame-Format, ECDH-Pairing
            ├── 03-BACKEND.md
            ├── 04-CODE-MAP.md
            └── 05-RELEVANCE-FOR-ZT3.md
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
- **Mapbox Secret-Token** (`sk.…`) hartcodiert im Manifest (Details siehe `apps/ninebot-segway/docs/07-SECRETS-FOUND.md`)
- App komplett mit NetEase NIS gepackt – BLE-Crypto-Code aus statisch nicht extrahierbar

### 🟢 Positiv (SHU)
- **Vollständig Open Source** – ECDH-Pairing-Code im Klartext lesbar
- Saubere Crypto-Wahl (P-256 + AES-CCM + HKDF + HMAC-SHA-256)
- Keine Secrets, keine Telemetrie

### 📌 Synthese
Die SHU-App liefert genau die Information, die in der Ninebot-App fehlt: das BLE-Pairing-Protokoll. Wenn der ZT3 Pro im SHU-Modell-Repo (`bootstrap.zip` von `apps-content.cfw.sh`) gelistet ist, ist eine eigenständige Steuerung ohne Reverse-Engineering möglich.

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
