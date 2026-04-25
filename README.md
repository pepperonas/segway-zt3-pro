# ZT3 Pro D Reverse-Engineering & Unlock-Research

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Status: Active Research](https://img.shields.io/badge/status-active%20research-brightgreen.svg)](#)
[![Stand](https://img.shields.io/badge/Stand-2026--04-blue.svg)](#)
[![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Made with Markdown](https://img.shields.io/badge/Made%20with-Markdown-1f425f.svg)](https://daringfireball.net/projects/markdown/)

[![Scooter: Segway-Ninebot ZT3 Pro D](https://img.shields.io/badge/Scooter-Segway--Ninebot%20ZT3%20Pro%20D-orange.svg)](https://www.segway.com)
[![Region: D (DE)](https://img.shields.io/badge/Region-D%20(DE)-red.svg)](#)
[![Stock Limit: 20 km/h](https://img.shields.io/badge/Stock%20Limit-20%20km%2Fh-red.svg)](#)
[![Target: 40 km/h](https://img.shields.io/badge/Target-40%20km%2Fh-success.svg)](#)

[![apktool](https://img.shields.io/badge/apktool-2.12.1-blue.svg)](https://apktool.org/)
[![jadx](https://img.shields.io/badge/jadx-1.5.3-blue.svg)](https://github.com/skylot/jadx)
[![Git LFS](https://img.shields.io/badge/Git-LFS-F64935?logo=gitlfs&logoColor=white)](https://git-lfs.github.com/)
[![Decompiled with ❤](https://img.shields.io/badge/decompiled%20with-%E2%99%A5-red.svg)](#)

[![Ninebot APK](https://img.shields.io/badge/Ninebot%20Segway-v7.6.3%20%7C%20116%20MB-lightgrey.svg)](reverse-engineering/apps/ninebot-segway/)
[![SHU APK](https://img.shields.io/badge/SHU-v3.0%20open__beta--5%20%7C%205.7%20MB-lightgrey.svg)](reverse-engineering/apps/shu/)
[![NetEase NIS Pack](https://img.shields.io/badge/Ninebot%20Pack-NetEase%20NIS-critical.svg)](reverse-engineering/apps/ninebot-segway/ANALYSIS.md#netease-nis-app-shielding-hauptbefund)
[![SHU: Open Source](https://img.shields.io/badge/SHU-Open%20Source-success.svg)](reverse-engineering/apps/shu/ANALYSIS.md)

[![BLE: Nordic UART](https://img.shields.io/badge/BLE-Nordic%20UART%20Service-blue.svg)](reverse-engineering/apps/shu/ANALYSIS.md#ble-protokoll-hauptbefund)
[![Crypto: ECDH P-256](https://img.shields.io/badge/Crypto-ECDH%20secp256r1-yellow.svg)](reverse-engineering/apps/shu/ANALYSIS.md#ble-protokoll-hauptbefund)
[![AES-CCM](https://img.shields.io/badge/AES--128-CCM-yellow.svg)](reverse-engineering/apps/shu/ANALYSIS.md#ble-protokoll-hauptbefund)
[![HKDF-SHA-256](https://img.shields.io/badge/KDF-HKDF--SHA--256-yellow.svg)](reverse-engineering/apps/shu/ANALYSIS.md#ble-protokoll-hauptbefund)
[![Frame Magic](https://img.shields.io/badge/Frame-0x55%200xAB-purple.svg)](reverse-engineering/apps/shu/ANALYSIS.md#ble-protokoll-hauptbefund)

> ⚠ **Rechtlicher Hinweis**: Tuning eines StVZO-zugelassenen E-Scooters führt zu Verlust der Betriebserlaubnis, Versicherungsschutz und Garantie. Inhalte hier sind ausschließlich für **Reverse-Engineering / Privatgelände** dokumentiert.

---

## Inhalt

| Datei / Verzeichnis | Beschreibung |
|---|---|
| [`UNLOCK-PLAN.md`](UNLOCK-PLAN.md) | Schritt-für-Schritt-Anleitung mit allen 5 Methoden, Software- und Hardware-Listen, Quellen |
| [`PRIOR-RESEARCH.md`](PRIOR-RESEARCH.md) | Vorrecherche zu ZT3 Pro D (Stand 2026-04-22) – Pairing-Flow, Command-Tabelle, Vergleich G3 vs. ZT3 |
| [`app/`](app/) | **Eigene Android-App** (Kotlin / Compose / Material 3) – Reborn der Segway Mobility App mit Speed-Profiles, Stealth-Unlock, OTA-Flash, Multi-Vehicle, OSM-Karte, Diagnostics |
| [`reverse-engineering/`](reverse-engineering/) | Statische Decompile-Analyse beider APKs **+ dynamische BLE-Capture-Auswertung** |
| ↳ [`apps/ninebot-segway/`](reverse-engineering/apps/ninebot-segway/) | Offizielle Segway Mobility App – durch NetEase NIS gepackt |
| ↳ [`apps/shu/`](reverse-engineering/apps/shu/) | ScooterHacking Utility (SHU) – Open Source, BLE-Protokoll im Klartext |
| ↳ [`ble-captures/`](reverse-engineering/ble-captures/) | HCI-Snoop-Mitschnitte echter Sessions (z. B. SHU-Multi-Komponenten-Flash) — bestätigt Wire-Verhalten |

## Quick-Reference – ZT3 Pro D BLE-Stack

| Aspekt | Wert | Quelle |
|---|---|---|
| BLE-Service | Nordic UART `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | SHU `services/g.java` |
| RX-Char | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` (App→Roller, Write) | SHU `services/g.java` |
| TX-Char | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` (Roller→App, Notify) | SHU `services/g.java` |
| Adv-Manufacturer-Prefix | `FF 4E 43` ("NC", Crypto-Variante) | SHU `classes/k.java` |
| Frame-Magic | `0x5A 0xA5` (klassisch NinebotCrypto, **bestätigt für ZT3 Pro D**) bzw. `0x55 0xAB` (moderner ECDH-Pfad, andere Modelle) | [BLE-Capture 2026-04-25](reverse-engineering/ble-captures/2026-04-25-shu-flash-session.md) |
| ECDH-Kurve | secp256r1 (NIST P-256) | SHU `crypto/elliptic/d.java` |
| Symm. Verschlüsselung | AES-128/CCM, 24-Bit MAC | SHU `crypto/elliptic/d.java` |
| KDF | HKDF-SHA-256 | SHU `crypto/elliptic/d.java` |
| MAC | HMAC-SHA-256 | SHU `crypto/elliptic/d.java` |
| Init-Hello | `0x00 ++ "blt.4.159" ++ rand[10]` | SHU `crypto/elliptic/d.java` |

## Empfohlene Unlock-Pfade (Kurzform)

| # | Methode | Kosten | Reversibel | Endgeschw. | Schwierigkeit |
|---|---|---|---|---|---|
| **1** ⭐ | **SHU v3 Beta + Region-Change** (VPN nötig) | **kostenlos** | ✅ ja | **40 km/h** | ⭐ trivial |
| 2 | NBT Unlock Key (Web-BLE) | ~129 € | ✅ ja | 38–40 km/h | ⭐ trivial |
| 3 | Dashboard-Tausch (China/US) | ~50–60 € | ✅ ja | 31–40 km/h | ⭐⭐⭐ |
| 4 | ZT3Tools + ST-Link V2 (DIY) | ~15–25 € | ⚠ nur mit Backup | 40 km/h | ⭐⭐⭐⭐ |
| 5 | XiaoDash Custom Firmware | Lizenz | ⚠ teils | 40 km/h + Profile | ⭐⭐⭐⭐ |

> 🎯 **Methode 1** ist die klar empfohlene: Open Source, kostenlos, ohne Werkzeug. Verwendet die SHU-Beta aus diesem Repo + Android + VPN außerhalb der EU.

Vollständige Anleitung: [`UNLOCK-PLAN.md`](UNLOCK-PLAN.md).

## Dekompilieren – Reproduktion

Die Decompile-Outputs sind **nicht im Repo enthalten** (~460 MB) und werden zur Laufzeit erzeugt:

```bash
# Tools (einmalig)
brew install apktool jadx

# Ninebot
cd reverse-engineering/apps/ninebot-segway
apktool d -f -o decompiled/apktool com.ninebot.segway.apk
jadx -d decompiled/jadx --no-res com.ninebot.segway.apk

# SHU
cd reverse-engineering/apps/shu
apktool d -f -o decompiled/apktool ScooterHackingUtility-pre_release.open_beta-5.apk
jadx -d decompiled/jadx --no-res ScooterHackingUtility-pre_release.open_beta-5.apk
```

Die APKs selbst sind via **Git LFS** versioniert (siehe `.gitattributes`). LFS muss vor dem Klon installiert sein:

```bash
brew install git-lfs && git lfs install
git clone https://github.com/pepperonas/segway-zt3-pro.git
```

## Eigene App – aktueller Stand

[![App: Reborn](https://img.shields.io/badge/app-Segway%20Mobility%20Reborn-FF6F1F)](app/)
[![Compose M3](https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose)](app/)
[![Hilt](https://img.shields.io/badge/DI-Hilt-2C2D72)](app/)
[![Stealth Unlock](https://img.shields.io/badge/feature-Vol--Down--3%C3%97%20Stealth%20Unlock-success)](app/)

Native Kotlin-App mit:
- **Speed-Profiles** (Boot-Default 22 km/h, 3 Quick-Actions, Unlock-Profil 40 km/h)
- **Stealth-Unlock** via PIN-Dialog **oder** Volume-Down-3× (Accessibility-Service, auch bei Screen aus)
- **Auto-Apply-on-Connect** (Boot-Profil setzt sich automatisch nach jedem Pairing)
- **Auto-Revert-Timer** mit Live-Countdown-Banner
- **OTA-Flash** mit IAP-State-Machine + CFW-Repo-Client
- **Region-Change** (D → U) als One-Tap-Aktion
- **Multi-Vehicle Garage** (Room-DB, aktivieren / umbenennen / entkoppeln)
- **OSM-Karte** + GPS-Track-Recording mit Persistenz und History-Overlay
- **Diagnostics** mit Live-BLE-Frame-Log + Field-Test-Buttons (Send 22 / 40 / Lock / Unlock)
- **AirLock** (Proximity-Auto-Unlock per RSSI-EMA)
- **Beacon-Live-Decoder** (Speed/Battery aus Adv ohne Connect)

Setup: `cd app && ./gradlew :app:installDebug`. Min-SDK 26 (Android 8). Details: [`app/README.md`](app/README.md).

> ⚠ **Field-Test offen**: Die App ist auf den modernen ECDH-Pfad (`0x55 0xAB`) ausgelegt; die [BLE-Capture-Auswertung](reverse-engineering/ble-captures/2026-04-25-shu-flash-session.md) zeigt aber, dass der ZT3 Pro D **den klassischen NinebotCrypto-Pfad (`0x5A 0xA5`, AES + SHA-1, 8-Bit-Counter)** verwendet. Der Classic-Pfad ist als nächster Implementierungsschritt vorgesehen — die Diagnostics-Page eignet sich zum Verifizieren beim ersten Versuch.

## Top-Findings

| # | Finding |
|---|---|
| 🟢 | **SHU v3 Beta unterstützt die x3-Reihe (G3, ZT3, F3)** – über FLASH-Repo + Region-Change auf US werden 40 km/h freigeschaltet (VPN außerhalb EU + Android nötig) |
| 🟢 | SHU liefert das **komplette ECDH-Pairing-Protokoll im Klartext** – damit ist eigene Tool-Entwicklung jederzeit möglich |
| 🔴 | **Mapbox Secret-Token (`sk.…`) hartcodiert** in der Ninebot-App (siehe [Hartcodierte Secrets](reverse-engineering/apps/ninebot-segway/ANALYSIS.md#hartcodierte-secrets)) |
| 🟠 | Ninebot-App vollständig **NetEase-NIS-gepackt** – BLE-Crypto nicht statisch extrahierbar (aber durch SHU bereits dokumentiert) |

## Doku-Struktur (Detail)

```
zt3pro/
├── README.md                                       # diese Datei
├── UNLOCK-PLAN.md                                  # 5 Methoden, Software/Hardware-Listen
├── PRIOR-RESEARCH.md                               # ZT3 Pro D Tiefen-Recherche, Command-Tabelle
├── .gitignore                                      # decompiled/* aus Git ausgeschlossen
├── .gitattributes                                  # Git LFS für *.apk
│
├── app/                                            # eigene Android-App (Kotlin/Compose)
│   ├── README.md                                   # Setup, Status, Module-Layout
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle/libs.versions.toml
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/celox/segway/
│       │   ├── core/{ble,crypto,ota,profile,repo,vehicle,data,util}/
│       │   ├── feature/{home,pair,garage,firmware,profiles,airlock,
│       │   │           diagnostics,track,discover,mine,settings,about}/
│       │   ├── ui/{theme,components,nav}/
│       │   └── di/AppModule.kt
│       └── res/
│
└── reverse-engineering/
    ├── README.md                                   # Methodik-Übersicht
    ├── apps/                                       # statische APK-Analyse
    │   ├── ninebot-segway/
    │   │   ├── com.ninebot.segway.apk              # via Git LFS (116 MB)
    │   │   ├── decompiled/                         # gitignored
    │   │   └── ANALYSIS.md                         # konsolidierte Komplett-Analyse
    │   └── shu/
    │       ├── ScooterHackingUtility-pre_release.open_beta-5.apk
    │       ├── decompiled/                         # gitignored
    │       └── ANALYSIS.md                         # konsolidierte Komplett-Analyse
    └── ble-captures/                               # dynamische Wire-Analyse
        ├── README.md                               # Methodik (tshark, btsnoop)
        └── 2026-04-25-shu-flash-session.md         # Multi-Komponenten-Flash, ~420 KB
```

## Lizenz & Quellen

Inhalte unter [MIT License](LICENSE).

Wichtige externe Quellen sind in den jeweiligen Dokumenten verlinkt:

- [scooterteam/ZT3Tools](https://github.com/scooterteam/ZT3Tools/) (archiviert Juli 2025)
- [bastelpichi-Wiki – SHU/SHFW Kompatibilität](https://wiki.bastelpichi.de/compatibility.html)
- [ScooterHacking Utility](https://utility.cfw.sh/)
- [XiaoDash für ZT3](https://www.xiaodash.app/zt3)
- [RollerPlausch – ZT3 Pro Unlock-Thread](https://rollerplausch.com/threads/zt3-pro-unlock-40-kmh-dashboard-tausch-oder-st-link-vcu-1-4-8-1-4-10-max-tempomat-zt3scripts.12501/)

---

🛴 **Made with curiosity** – © 2026 Martin Pfeffer | [celox.io](https://celox.io)
