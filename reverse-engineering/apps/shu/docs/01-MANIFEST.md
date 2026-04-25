# AndroidManifest.xml – SHU

Datei: `decompiled/apktool/AndroidManifest.xml` (75 Zeilen, sehr schlank)

## Header

```xml
<manifest package="sh.cfw.utility.pre_release.open_beta"
          android:compileSdkVersion="34"
          platformBuildVersionCode="34"
          platformBuildVersionName="14">
```

Min-SDK: 26 (Android 8.0 Oreo) – `apktool.yml` zeigt `minSdkVersion: 26, targetSdkVersion: 34`.

## Permissions

| Permission | Zweck |
|---|---|
| `BLUETOOTH` (≤ Android 30) | Legacy BLE |
| `BLUETOOTH_ADMIN` (≤ Android 30) | Legacy BLE-Admin |
| `BLUETOOTH_SCAN` (`neverForLocation`) | BLE-Scan ohne Location-Inferenz |
| `BLUETOOTH_CONNECT` | BLE-Connection ab Android 12 |
| `ACCESS_COARSE_LOCATION` (≤ Android 30) | für BLE-Scan auf älteren Versionen |
| `ACCESS_FINE_LOCATION` (≤ Android 30) | dito |
| `INTERNET` | Update-Check, Firmware-Repo, Bug-Tracker |
| `ACCESS_NETWORK_STATE` | Connectivity-Check |
| `FOREGROUND_SERVICE` | Serial-Service (BLE-Bridge) |
| `WRITE_EXTERNAL_STORAGE` | Technical-Dump-Export |
| `REQUEST_INSTALL_PACKAGES` | Self-Update-Installer |

`<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>` – BLE ist Pflicht.

## Custom Permission

```xml
<permission
    android:name="sh.cfw.utility.pre_release.open_beta.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    android:protectionLevel="signature"/>
```

Standard-AndroidX-Pattern für `RECEIVER_NOT_EXPORTED`.

## Application

```xml
<application
    android:appComponentFactory="androidx.core.app.CoreComponentFactory"
    android:extractNativeLibs="false"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:networkSecurityConfig="@xml/network_security_config"
    android:theme="@style/Theme.App.Starting"
    android:windowSoftInputMode="stateAlwaysHidden|adjustResize"/>
```

Kein Custom-Application-Class, kein Backup-Verbot, **kein** `usesCleartextTraffic`. Net-Sec-Config:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="false">192.168.1.7</domain>
    </domain-config>
</network-security-config>
```

→ Genau eine LAN-IP `192.168.1.7` ist explizit auf `cleartext=false` gesetzt – das wirkt wie ein **vergessener Dev-Eintrag**: standardmäßig wäre Cleartext für diesen LAN-Host eh nicht erlaubt (modernes Android), und der Eintrag fügt nichts hinzu. Vermutlich ein Relikt aus einem Test-Setup.

## Komponenten

### Activities (4 + 2 Library)
| Activity | Rolle |
|---|---|
| `sh.cfw.utility.activities.UpdateCheckerActivity` | **Launcher**, exported, Theme `Theme.App.Starting` |
| `sh.cfw.utility.activities.EnrollActivity` | Beta-Enrollment (Pre-Release-Track) |
| `sh.cfw.utility.activities.ScannerActivity` | BLE-Scan-UI |
| `sh.cfw.utility.activities.ScooterActivity` | Haupt-UI nach Connect (1451 Java-Zeilen) |
| `com.franmontiel.attributionpresenter.AttributionActivity` | Open-Source-Lizenzen |
| `com.michaelflisar.changelog.internal.ChangelogActivity` | Changelog-View |

### Services (2)
| Service | foregroundServiceType |
|---|---|
| `sh.cfw.utility.services.SerialService` | `shortService` – BLE-Bridge zwischen UI und Scooter |
| `no.nordicsemi.android.support.v18.scanner.ScannerService` | (intern, von Nordic-Lib) |

### Receiver (4)
| Receiver | Notes |
|---|---|
| `sh.cfw.utility.services.MajsiHomeReceiver` | exported, Permission `TODO` (sic!) – Inter-App-Bridge zu `adriandp.m365dashboard`, `com.m365downgrade`, `com.basse.scootbatt` |
| `sh.cfw.utility.services.BroadcastSHFWProfileNamesByScooterUidReceiver` | exported, Permission `TODO` – sendet SHFW-Profil-Namen an `com.basse.scootbatt` |
| `no.nordicsemi.android.support.v18.scanner.PendingIntentReceiver` | Nordic-Scanner-Lib |
| `androidx.profileinstaller.ProfileInstallReceiver` | Baseline-Profile |

⚠ **Permission `TODO`** ist offensichtlich ein Platzhalter, der nie gesetzt wurde. Die Receiver sind effektiv `exported=true` ohne Schutz. Da die Aktionen aber sehr spezifisch sind (`adriandp.m365dashboard`, `com.basse.scootbatt`), ist das Risiko gering, aber kein guter Style.

### Provider (3)
- `sh.cfw.utility.pre_release.open_beta.provider` × 2 (`androidx.core.content.FileProvider`, beide identische Authority – Konflikt wie in der Ninebot-App!)
- `sh.cfw.utility.pre_release.open_beta.androidx-startup` (`androidx.startup.InitializationProvider`)

## Inter-App-Communication

Die App fragt explizit nach drei anderen Scooter-Apps:

```xml
<queries>
    <package android:name="adriandp.m365dashboard"/>  <!-- M365 Dashboard von Adrian DP -->
    <package android:name="adriandp.ninedash"/>        <!-- 9Dash (Ninebot-Variante) -->
    <package android:name="com.basse.scootbatt"/>      <!-- ScootBatt (Battery-Info) -->
</queries>
```

und broadcastet zu ihnen mit den Action-Strings:
- `adriandp.m365dashboard`
- `com.m365downgrade`
- `com.basse.scootbatt`

Das ist ein **Inter-App-Plugin-System** der Custom-Scooter-Community.

## Meta-Daten

Nur AndroidX-Startup-Initializer (EmojiCompat, ProcessLifecycle, ProfileInstaller). Keine API-Keys, keine Tokens, keine Secrets im Manifest.

## Vergleich zur Ninebot-App

| Aspekt | Ninebot-App | SHU |
|---|---|---|
| APK-Größe | 116 MB | 5.7 MB |
| Activities | 197 | 4 |
| Services | 54 | 2 |
| Permissions | 47 | 11 |
| Hartcodierte Secrets | viele (Mapbox sk., HERE, Bugsnag, …) | keine |
| Pack-Schutz | NetEase NIS | keiner |
| Code im Klartext | ~30 Wrapper-Klassen | komplettes `sh.cfw.utility.*` |
