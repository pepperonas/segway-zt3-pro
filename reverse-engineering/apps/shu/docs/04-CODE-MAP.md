# Code-Map – `sh.cfw.utility.*`

70 Java-Dateien im echten App-Code. Der Rest der 3645 jadx-Outputs sind Libraries (R8-obfuskiert).

## Architektur

```
                                ┌──────────────────────┐
                                │  UpdateCheckerActivity│  ← Launcher
                                │  (auch Disclaimer/EULA)
                                └──────────┬───────────┘
                                           │ "Continue"
                                ┌──────────▼───────────┐
                                │   ScannerActivity    │  ← BLE-Scan-UI (Nordic Scanner)
                                │   - filtert per BeaconParser (k.java)
                                │   - zeigt Modell-Icons aus i0
                                └──────────┬───────────┘
                                           │ Tap Scooter
                                ┌──────────▼───────────┐
                                │   ScooterActivity    │  ← Haupt-UI (1451 Zeilen)
                                │   - Tabs: Info, Settings, Logs, Bug, …
                                └──────────┬───────────┘
                                           │ bindService
                                ┌──────────▼───────────┐
                                │   SerialService      │  ← Foreground-Service
                                │   (BluetoothGattCallback aus services/g.java)
                                └──────────┬───────────┘
                                           │ NUS RX/TX
                              ┌────────────▼────────────┐
                              │   crypto.elliptic.e     │  ← Pairing-Facade
                              └────────────┬────────────┘
                                           │
                              ┌────────────▼────────────┐
                              │   crypto.elliptic.h     │  ← State-Machine
                              │   (extends i)
                              └────────────┬────────────┘
                                           │
                              ┌────────────▼────────────┐
                              │   crypto.elliptic.d     │  ← Crypto-Primitive
                              │   (ECDH, AES/CCM, HKDF, HMAC, Checksum)
                              └─────────────────────────┘
```

## Paket-Übersicht

### `sh.cfw.utility.activities`

| Datei | Rolle |
|---|---|
| `UpdateCheckerActivity.java` | Launcher; Update-Check, EULA, Track-Status |
| `EnrollActivity.java` | Beta-Track-Enrollment-UI |
| `ScannerActivity.java` | BLE-Scan-Liste mit Pull-to-Refresh |
| `ScooterActivity.java` | Haupt-Bildschirm nach Connect (1451 Zeilen) |

### `sh.cfw.utility.services`

| Datei | Rolle |
|---|---|
| `SerialService.java` | Foreground-Service, hält BLE-Connection am Leben |
| `g.java` | BluetoothGattCallback – **GATT-UUIDs hier!** |
| `a.java` | BLE-Helper |
| `MajsiHomeReceiver.java` | Inter-App: empfängt Anfragen von M365-Dashboard etc. |
| `BroadcastSHFWProfileNamesByScooterUidReceiver.java` | Inter-App: sendet SHFW-Profile-Namen zu ScootBatt |

### `sh.cfw.utility.crypto.elliptic`

| Datei | LoC | Rolle |
|---|---|---|
| `ConfigurationElliptic.java` | 49 | DTO (deviceInfo, deviceToken, beaconKey, ssid) |
| `EllipticPreferences.java` | 145 | SharedPrefs-Persistenz pro Scooter-MAC |
| `c.java` | 144 | Sealed-Class-Events (Connected, RegFail, BroadCastToApps, WriteLog) |
| `d.java` | 213 | **Crypto-Primitive** (ECDH/AES-CCM/HKDF/HMAC/Checksum) |
| `e.java` | 421 | **Public-Facade** (`init`, `encrypt`, `decrypt`) |
| `h.java` | 867 | **State-Machine** (Pairing-Stages B/C/D/F/H/J – Coroutines) |
| `i.java` | 633 | Basisklasse für `h` (Frame-Aufbau) |
| `b.java` | 341 | BLE-IO-Wrapper über GATT |
| `a.java` | 10 | BLE-Interface |
| `f.java` | 26 | Utility (Hex-Encoding) |
| `g.java` | 57 | DataClass für 11-State-Liste |
| `j.java` | 5 | leere Hilfsklasse |

### `sh.cfw.utility.classes`

Allgemeine Helper- und Repository-Klassen:

| Datei | Rolle |
|---|---|
| `i0.java` | **Singleton-Repository** für Bootstrap- und Data-ZIPs (Modell-DB, Vehicle-Images) |
| `k.java` | **BeaconParser** (NB/NC-Frame-Decoder, 195 Zeilen) |
| `c0.java` | HTTP-Wrapper (lädt `repo/v4/<name>.zip`) |
| `e0.java` | weitere HTTP-Helper (250 Zeilen) |
| `q.java` | Settings-Repository (847 Zeilen – größte Helper-Klasse) |
| `j.java` | Disclaimer-Dialog-Builder |
| `h0.java` / `i0.java` | Asset-/Theme-Helper |
| `j.java` `k.java` | Settings, Theme |
| `s.java` `r.java` `x.java` | UI-Models |
| `GearProgressView.java` | Custom-View (Tacho/Fortschrittsanzeige) |
| `w.java` | Util |
| `d0.java` `f0.java` | Tiny-DTOs |

### `sh.cfw.utility.classes.shfw`

| Datei | Vermutete Rolle (R8-namen) |
|---|---|
| `f.java` | SHFW-Profile-DTO |
| `k.java` `r.java` `u.java` `v.java` | SHFW-Module (Konstanten, Mapper) |

### `sh.cfw.utility.models.shfw`

12 Datei-Modelle (`d.java` … `q.java`) – alle SHFW-Profil-Submodelle (Acceleration, BatterySettings, BootSettings, etc.). Genaue Bedeutung muss dynamisch oder anhand der `bootstrap.zip` rekonstruiert werden.

### `sh.cfw.utility.ui` & `sh.cfw.utility.ui.shfw`

Fragments und Bottom-Sheet-Dialoge für die Haupt-UI:

- `b.java` `n.java` `p.java` – Top-Level-Fragments
- `shfw/c.java`, `shfw/e0.java`, `shfw/i1.java`, `shfw/o0.java`, `shfw/o1.java`, `shfw/p1.java`, `shfw/s.java`, `shfw/w.java`, `shfw/x1.java`, `shfw/y0.java` – SHFW-spezifische Fragmente

### `sh.cfw.utility.viewModels`

| Datei | Vermutete Rolle |
|---|---|
| `d.java` | Main-ViewModel |
| `e.java` | Scanner-ViewModel |
| `f.java` | Scooter-ViewModel (referenziert von `ScooterActivity`) |

### `sh.cfw.utility.pre_release.open_beta`

Generierte `R.java` (Resource-IDs).

## Bibliothekes-Inventar

Aus `META-INF/*.version`-Dateien (im raw-Output) und Imports:

| Bibliothek | Hinweise |
|---|---|
| **AndroidX** (activity, appcompat, biometric, browser, camera2, core, fragment, lifecycle, navigation, preference, recyclerview, room, swiperefreshlayout, work, …) | Standard |
| **Kotlin Coroutines** | für Pairing-State-Machine |
| **OkHttp3** + Public-Suffix-DB | HTTP |
| **Gson** (`o1.d` ist die obfuskierte Klasse `com.google.gson.Gson`) | JSON |
| **SpongyCastle** (`org.spongycastle.*`) | Crypto-Provider (statt BouncyCastle, da Android-historische Kompatibilität) |
| **AltBeacon Library** (`com.basse.scootbatt`-Bridge?) | indirekt via `org.altbeacon.beacon.*` (referenziert in `b6.g`) |
| **Nordic BLE Scanner** (`no.nordicsemi.android.support.v18.scanner`) | Robuster BLE-Scanner |
| **Material Components** (`com.google.android.material.*`) | UI |
| **JUnit** (im `META-INF`!) | Versehentlich miteingepackt – Test-Code im Release-APK; harmlos aber unsauber |
| **Material-About-Library** (`com.danielstone.materialaboutlibrary`) | About-Screen |
| **MaterialIze**, **Iconics** (`com.mikepenz.*`) | Icons |
| **MaterialDrawer** | Drawer |
| **AttributionPresenter** (`com.franmontiel.attributionpresenter`) | Open-Source-Lizenzen |
| **Changelog** (`com.michaelflisar.changelog`) | Changelog-View |
| **Sentry** (vermutlich – BugReport-Logik) | nicht eindeutig im Code, aber Crash-Dump-Export legt das nahe |

## Code-Style-Hinweise

- App ist mostly Kotlin (`@p1.c("…")`-Annotations sind die obfuskierte gson `@SerializedName`)
- Viele Coroutines (`f0`, `g0`, `s0` aus `kotlinx.coroutines`)
- Pairing läuft auf `Dispatchers.IO`
- Sealed Classes (`c.java`) – idiomatisches Kotlin
- Encapsulation ist clean, kein Reflection-Overuse

## Decompile-Qualität-Hinweise

- ~6 Klassen wurden **incomplete decompiled** (`Method dump skipped`) – darunter alle Pairing-Stages in `h.java`. Für genaues Verständnis dieser Stages müsste man:
  - jadx mit `--show-bad-code --comments-level debug` neu laufen lassen, oder
  - in Smali (apktool-Output) direkt schauen (`decompiled/apktool/smali/sh/cfw/utility/crypto/elliptic/h.smali`)
  - oder JEB / IDA Pro / Ghidra mit Dex-Plugin nehmen
