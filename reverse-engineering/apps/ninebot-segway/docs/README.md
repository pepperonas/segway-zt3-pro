# Segway Mobility (com.ninebot.segway) – APK Reverse-Engineering

Statische Analyse von `com.ninebot.segway.apk` v7.6.3 (Build 969), Stand 2026-04-25.

## Quick-Reference

- **App**: Segway Mobility (Overseas-Variante), für u. a. ZT3 Pro, KickScooter MAX G30, Gokart Pro
- **Schutz**: NetEase NIS Wrapper – Anwendungscode verschlüsselt, statisch nicht analysierbar
- **Architektur**: Native Android-Shell + React-Native-(Hermes)-Module + WebView/H5
- **Backend**: Multi-Region Multi-Cluster (`*.ninebot.com`, `*.segway.com`)
- **Build**: AGP 8.6.1, Kotlin 2.1.0, compileSdk 35

## Doku-Index

1. [00-OVERVIEW.md](00-OVERVIEW.md) – Zusammenfassung & was die Analyse leisten kann
2. [01-MANIFEST.md](01-MANIFEST.md) – Permissions, Application-Tag, Deep Links, Meta-Daten
3. [02-NETEASE-SHIELDING.md](02-NETEASE-SHIELDING.md) – Funktionsweise des Packers, Anti-Tamper-Liste, String-Deobfuscator (decoded)
4. [03-NETWORK-ENDPOINTS.md](03-NETWORK-ENDPOINTS.md) – Backend-URLs (inland & overseas, alle Umgebungen), API-Routen, GRS, Cleartext-Domains
5. [04-SDKS-LIBRARIES.md](04-SDKS-LIBRARIES.md) – Eingebettete SDKs mit Versionen
6. [05-ASSETS-INVENTORY.md](05-ASSETS-INVENTORY.md) – Asset-Übersicht
7. [06-COMPONENTS.md](06-COMPONENTS.md) – Activities/Services/Receiver/Provider katalogisiert
8. [07-SECRETS-FOUND.md](07-SECRETS-FOUND.md) – Hartcodierte API-Keys, Tokens, Crypto-Material
9. [08-LIMITATIONS-NEXT-STEPS.md](08-LIMITATIONS-NEXT-STEPS.md) – Was statisch nicht geht, dynamische Methoden

## Top-3-Findings

| Priorität | Befund |
|---|---|
| 🔴 **HOCH** | Mapbox **Secret-Token** (`sk.…`) im Manifest – ermöglicht Token-Rotation/Tileset-Manipulation. Vollständiger Wert in der APK, Details in [`07-SECRETS-FOUND.md`](07-SECRETS-FOUND.md). |
| 🟠 **MITTEL** | App ist mit NetEase NIS gepackt – statische Reverse-Engineering-Methoden sind blockiert. Für BLE-Protokoll-Analyse muss Frida-Memory-Dump oder HCI-Snoop verwendet werden |
| 🟠 **MITTEL** | Zwei RSA-1024-Schlüssel embedded (`config_rsa_public_key.pem`, `rsa_public_key.pem`) – kryptographisch unter heutigem NIST-Standard |

## Reproduzierbarkeit

Alle Analysen basieren auf der unveränderten APK unter `../com.ninebot.segway.apk` (SHA-256 in apktool.yml). Decompile-Output liegt parallel:

```
reverse-engineering/
├── com.ninebot.segway.apk
├── decompiled/
│   ├── raw/      # ZIP-Extraktion
│   ├── apktool/  # Smali + decoded resources
│   ├── jadx/     # Java-Source (nur Wrapper)
│   └── extracted/# entpackte Sub-Archive
└── docs/         # diese Dokumentation
```

Tools: `apktool 2.12.1`, `jadx 1.5.3`. Alle Schritte sind in der Shell-History reproduzierbar.
