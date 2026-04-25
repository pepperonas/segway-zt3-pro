# APK-Analyse: Segway Mobility (com.ninebot.segway)

Statische Reverse-Engineering-Analyse der offiziellen Begleit-App für Segway-Ninebot E-Scooter (inkl. ZT3 Pro). Analyse vom **2026-04-25**.

## TL;DR

- **App**: Segway Mobility (Ninebot Overseas), Package `com.ninebot.segway`
- **Version**: `7.6.3` (Build `969`)
- **Form-Faktor**: Android App Bundle (AAB) – diese APK ist nur die **Base-APK** (~116 MB)
- **Schutz**: Vollständig **gepackt mit NetEase NIS Wrapper** (kommerzielles Anti-Tamper / App-Shielding). Der eigentliche Code liegt verschlüsselt in `assets/nedata.db` und wird zur Laufzeit von `libnesec.so` über JNI dekodiert.
- **Frontend**: Hybride Architektur – nativer Android-Shell + **React Native (Hermes-Bytecode)** + **WebView/H5-Module**
- **Backend**: 9 verschiedene Service-Cluster (PHP, Java1, Java2, Steeldust, Ebike, …) je dev/test/release/migrate_cloud/production, getrennt für China („inland") und Overseas
- **Wichtigster Befund**: In `AndroidManifest.xml` und `assets/Server.json` finden sich **mehrere hartcodierte API-Tokens und Secrets** im Klartext (Mapbox Secret Token, HERE-Key/Secret, Tencent-Maps-Key, Baidu-LBS-Key, Bugsnag-Key, Facebook Client Token, Passport-Client-Key)

## Was diese Analyse leisten kann

Aufgrund der NetEase-NIS-Verschlüsselung lässt sich der eigentliche Anwendungscode (BLE-Protokoll für ZT3 Pro, Crypto, Telemetrie) **nicht statisch** rekonstruieren. Die Java-Klassen `cn.ninebot.*` und `com.ninebot.*` sind in `nedata.db` (35 MB Blob) gepackt und werden erst nach Anti-Debug-/Anti-Hook-Checks dekodiert.

Diese Doku liefert daher:

| Bereich | Möglich (statisch) | Nur dynamisch (Frida/RE der Native-Lib) |
|---|---|---|
| Manifest, Permissions, Komponenten | ✅ Vollständig | – |
| Verwendete SDKs / Bibliotheken | ✅ Vollständig | – |
| Backend-URLs & API-Routen | ✅ Vollständig (Server.json, JavaApiList.json) | Request-Bodies, Auth-Flows |
| Hartcodierte Secrets im Manifest | ✅ Komplett | – |
| BLE GATT-Service-/Char-UUIDs | ❌ verschlüsselt | ✅ via Hooking oder Native-Disassembly |
| Scooter-Paket-Format (Ninebot/Segway-Protokoll) | ❌ verschlüsselt | ✅ (öffentlich dokumentiert in Community-Tools, s.u.) |
| BLE-Auth / Pairing-Crypto | ❌ verschlüsselt | ✅ |

## Verzeichnis dieser Doku

| Datei | Inhalt |
|---|---|
| `00-OVERVIEW.md` | Diese Datei |
| `01-MANIFEST.md` | Permissions, Activities, Services, Deep Links, Meta-Daten |
| `02-NETEASE-SHIELDING.md` | Funktionsweise des NetEase-NIS-Wrappers, String-Deobfuscator, Anti-Tamper-Liste |
| `03-NETWORK-ENDPOINTS.md` | Vollständige Backend-Service-Map, API-Routen, GRS, Cleartext-Domains |
| `04-SDKS-LIBRARIES.md` | Third-Party-SDKs mit Versionen |
| `05-ASSETS-INVENTORY.md` | Übersicht der eingebetteten Assets |
| `06-COMPONENTS.md` | Vollständige Activity-/Service-/Provider-Liste |
| `07-SECRETS-FOUND.md` | Hartcodierte API-Keys, Tokens, Crypto-Material |
| `08-LIMITATIONS-NEXT-STEPS.md` | Was nicht statisch geht und wie man weitermacht |

## Decompile-Output

Generiert mit **apktool 2.12.1** und **jadx 1.5.3**:

```
reverse-engineering/
├── com.ninebot.segway.apk        # Original-APK (116 MB)
├── decompiled/
│   ├── raw/        # rohe ZIP-Extraktion (170 MB) – Assets, Manifest binär, classes.dex
│   ├── apktool/    # smali, dekodierte Resources, Manifest dekodiert (173 MB)
│   ├── jadx/       # Java-Source der Wrapper-Klassen (2 MB) – nur die NetEase-Stub-Klassen
│   └── extracted/  # entpackte Sub-Archive (platform.zip, common_strings.zip)
└── docs/           # diese Dokumentation
```

## Build-Metadaten der APK

- `compileSdkVersion=35` (Android 15)
- `platformBuildVersionName=15`
- Kotlin: 2.1.0 (Build-System Gradle 8.7), source/target compatibility 17
- AAPT: AndroidGradlePlugin 8.6.1
- Splits: 13 Sprach-Varianten + ABI/Density-Splits (nur Base hier vorhanden)
- Bugsnag Build UUID: `dc9f2841-d7f3-433a-b8aa-ce466e53ed9d`
- Quell-Repo-Revision (laut `META-INF/version-control-info.textproto`): `fb1ef26e90dd5d91a29a9b390ccc15f242a7bc5c`

## Wichtige Resource-Dateien (App-Bundle-Hinweise)

- `res/xml/splits0.xml` – listet 13 Sprach-Splits (de, en, es, fr, it, ja, ko, nl, pl, ru, tr, vi, zh)
- `stamp-cert-sha256` – Google-Play-Signaturstempel (32 Byte) → APK kommt aus dem Play Store
- `meta-data com.android.vending.derived.apk.id = 3` → das ist die dritte abgeleitete APK in der Bundle-Verteilung
