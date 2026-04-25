# 🛡️ Segway Mobility (com.ninebot.segway) – Decompile-Analyse

[![App: Segway Mobility](https://img.shields.io/badge/App-Segway%20Mobility-orange?logo=android&logoColor=white)](https://www.segway.com)
[![Version](https://img.shields.io/badge/version-7.6.3%20%7C%20build%20969-blue)](#)
[![APK Size](https://img.shields.io/badge/apk%20size-116%20MB-lightgrey)](#)
[![Pack: NetEase NIS](https://img.shields.io/badge/pack-NetEase%20NIS-critical)](#netease-nis-app-shielding-hauptbefund)
[![Code: Encrypted](https://img.shields.io/badge/code-encrypted%20at%20rest-red)](#netease-nis-app-shielding-hauptbefund)
[![Architecture: AAB Base](https://img.shields.io/badge/distribution-AAB%20base%20APK-blue)](#)
[![Hybrid: RN + Hermes](https://img.shields.io/badge/hybrid-React%20Native%20%28Hermes%29-purple)](#)

[![Activities](https://img.shields.io/badge/activities-197-yellow)](#komponenten-kompakt)
[![Services](https://img.shields.io/badge/services-54-yellow)](#komponenten-kompakt)
[![Receivers](https://img.shields.io/badge/receivers-30-yellow)](#komponenten-kompakt)
[![Providers](https://img.shields.io/badge/providers-14-yellow)](#komponenten-kompakt)
[![Permissions](https://img.shields.io/badge/permissions-47-yellow)](#permissions)

[![Backend Clusters](https://img.shields.io/badge/backend%20clusters-9%20×%202%20region%20×%205%20env-blue)](#network-endpoints)
[![Cleartext Domains](https://img.shields.io/badge/cleartext--http-22%20domains-orange)](#cleartext-http-whitelist)
[![Hardcoded Secrets](https://img.shields.io/badge/hardcoded%20secrets-multiple-critical)](#hartcodierte-secrets)

> Statische Analyse von `com.ninebot.segway.apk`. Die App ist mit dem kommerziellen App-Shielding **NetEase NIS** gepackt – der eigentliche Anwendungscode ist verschlüsselt und nur zur Laufzeit lesbar. Aus dem **Manifest, den Assets und den Konfigs** lassen sich trotzdem 90 % aller relevanten Befunde gewinnen.

---

## Inhaltsverzeichnis

1. [TL;DR](#tldr)
2. [Was die Analyse leisten kann](#was-die-analyse-leisten-kann)
3. [AndroidManifest](#androidmanifest)
4. [NetEase-NIS-App-Shielding (Hauptbefund)](#netease-nis-app-shielding-hauptbefund)
5. [Network-Endpoints](#network-endpoints)
6. [Eingebettete SDKs / Bibliotheken](#eingebettete-sdks--bibliotheken)
7. [Asset-Inventar](#asset-inventar)
8. [Komponenten (kompakt)](#komponenten-kompakt)
9. [Hartcodierte Secrets](#hartcodierte-secrets)
10. [Limitierungen & nächste Schritte](#limitierungen--nächste-schritte)
11. [Quellen / Reproduktion](#quellen--reproduktion)

---

## TL;DR

| Aspekt | Wert |
|---|---|
| **Package** | `com.ninebot.segway` |
| **Version** | `7.6.3` (Build 969) |
| **APK** | 116 MB (Base-APK eines Android-App-Bundles) |
| **Splits** | 13 Sprachen + ABI/Density (nicht in dieser Datei) |
| **compileSdk** | 35 (Android 15) |
| **Schutz** | **NetEase NIS Wrapper** – Code in `assets/nedata.db` (35 MB) verschlüsselt; native `libnesec.so` dekodiert zur Laufzeit |
| **Architektur** | Native Android-Shell + React Native (Hermes-Bytecode v94) + WebView/H5 |
| **Application-Class** | `com.netease.nis.wrapper.MyApplication` (Wrapper, nicht Real-App) |
| **Sichtbare Klassen** | 30 Smali-Dateien (alle NetEase-Stub) |
| **Build** | AGP 8.6.1, Kotlin 2.1.0, Source/Target 17, Gradle 8.7 |
| **Build-UUID (Bugsnag)** | `dc9f2841-d7f3-433a-b8aa-ce466e53ed9d` |
| **Repo-Revision (intern)** | `fb1ef26e90dd5d91a29a9b390ccc15f242a7bc5c` |

---

## Was die Analyse leisten kann

| Bereich | Möglich (statisch) | Nur dynamisch (Frida / Native-RE) |
|---|---|---|
| Manifest, Permissions, Komponenten | ✅ Vollständig | – |
| Verwendete SDKs / Bibliotheken | ✅ Vollständig | – |
| Backend-URLs & API-Routen | ✅ Vollständig (`Server.json`, `JavaApiList.json`) | Request-Bodies, Auth-Flows |
| Hartcodierte Secrets im Manifest | ✅ Komplett | – |
| BLE GATT-Service-/Char-UUIDs | ❌ verschlüsselt | ✅ via Hooking oder Native-Disassembly |
| Scooter-Paket-Format (Ninebot-Protokoll) | ❌ verschlüsselt | ✅ aus SHU-Decompile bekannt |
| BLE-Auth / Pairing-Crypto | ❌ verschlüsselt | ✅ |

→ Für die BLE-Protokoll-Details siehe [`apps/shu/ANALYSIS.md`](../shu/ANALYSIS.md).

---

## AndroidManifest

### Application-Tag

```xml
<application
    android:name="com.netease.nis.wrapper.MyApplication"   <!-- ← Wrapper, NICHT echte App-Klasse -->
    android:allowBackup="false"
    android:allowNativeHeapPointerTagging="false"
    android:appComponentFactory="androidx.core.app.CoreComponentFactory"
    android:extractNativeLibs="false"
    android:largeHeap="true"
    android:persistent="true"
    android:requestLegacyExternalStorage="true"
    android:theme="@style/Theme.Ninebot6"
    android:usesCleartextTraffic="true"   <!-- ⚠ Cleartext für Carbit-Domains -->
/>
```

### Permissions

47 Permissions in folgenden Gruppen:

**Bluetooth (BLE-Scooter-Kommunikation)**
- `BLUETOOTH`, `BLUETOOTH_ADMIN` (≤Sdk30)
- `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`

**Standort (BLE-Scan + Navi)**
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`, `ACCESS_LOCATION_EXTRA_COMMANDS`

**Netzwerk**
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`
- `NEARBY_WIFI_DEVICES` (`neverForLocation`)

**Foreground-Services** (BLE-Pair, Navigation, Track-Upload, Mirror, Mic) – 6 Subtypes

**Speicher** – READ/WRITE_EXTERNAL_STORAGE, READ_MEDIA_AUDIO

**Health Connect** (Distanz/Kalorien aus der Fahrt) – 5 Permissions

**Sonstige** – CAMERA (QR/Self-Auth), RECORD_AUDIO, USB_PERMISSION (Wired Carlink), USE_BIOMETRIC, SYSTEM_ALERT_WINDOW, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, ACCESS_ADSERVICES_AD_ID, …

### Deep-Link-Schemata

```xml
<!-- ninebot:// scheme -->
<data android:host="navi"             android:scheme="ninebot"/>
<data android:host="carlink" android:path="/bind"   android:scheme="ninebot"/>
<data android:host="carlink" android:path="/unbind" android:scheme="ninebot"/>
<data android:host="ninebot.ninebot.com" android:pathPrefix="/openPage" android:scheme="ninebot"/>

<!-- Facebook OAuth -->
<data android:host="cct.com.ninebot.segway" android:scheme="fbconnect"/>

<!-- Vivo Carlink -->
<data android:host="com.ninebot.ninebot" android:path="/deeplink" android:scheme="vivocar"/>
```

→ Die `ninebot://carlink/bind|unbind`-Routen sind als `exported=true` für externe Apps offen. **Auth-Bypass-Recherche-Kandidat** (dynamisch zu testen).

### `<queries>`-Block (Package-Visibility)

App fragt explizit nach: WeChat, QQ, Tencent/Baidu/Autonavi/Google-Maps, Ninebot Lite, Health Connect, Heytap, OPPO Carlink, Huawei HMS, AlipayGphone, Sina Weibo, Vivo Wallet u. v. m.

### Wichtige Meta-Daten (Auswahl – ohne Secret-Werte)

| Key | Value |
|---|---|
| `STORAGE_PLATFORM_NAME` | `Aws` (S3) |
| `NB_HTTP_HOST_ENV_TYPE` | `production` |
| `IS_DOMESTIC` | `false` (Overseas-Build) |
| `MAP_CLIENT` | `cn.ninebot.library.map.google.GoogleMapClient` |
| `NAVI_CLIENT` | `cn.ninebot.library.here.navi.HereNaviManager` |
| `PUSH_CLIENT` | `cn.ninebot.library.googlepush.GooglePushClient` |
| `cn.ninebot.ninebot.PASSPORT_CLIENT_ID` | `vehicle_app_overseas` |
| `NEW_TEST_SERVER_ADDRESS` | `https://sg-test1-oms-gateway.ninebot.com/` |
| `bugsnag.RELEASE_STAGE` | `Online` |
| `huawei.hms.client.appid` | `100187639` |
| `oplus.carlink.companyid` | `20029` |
| `targetSignature` | `56059A9193DD9FF053F86DF755A835B7` (MD5 der Carlink-Target-Signatur `com.ninebot.ninebot`) |

→ Secrets/API-Keys siehe [Hartcodierte Secrets](#hartcodierte-secrets).

### Network-Security-Config (Cleartext-erlaubt)

22 Domains, im Wesentlichen die **Carbit/EasyConnect-Infrastruktur** + zwei rohe Aliyun-IPs:

```
mobile.carbit.com.cn / premobile.carbit.com.cn / smobile.carbit.com.cn
sapi.carbit.com.cn / sapiota.carbit.com.cn / sh5.carbit.com.cn
sdown.carbit.com.cn / down.carbit.com.cn / sdown.carbit.cn / down.carbit.cn
rom.carbit.lo                       ← interner .lo TLD
spsn.carbit.cn / sjapi.carbit.cn
smapboxapi.carbit.cn / stalkiesdk.carbit.cn
wxlinktest.sinaapp.com / wxlink.sinaapp.com / wxlink.vipsinaapp.com
120.24.62.11:8085                   ← Aliyun, Plain HTTP
120.79.98.236
```

---

## NetEase-NIS-App-Shielding (Hauptbefund)

Die App ist mit dem kommerziellen Schutzpacker **NetEase NIS Wrapper** (易盾应用加固) versehen. Das ist der größte Befund: ohne diesen Schritt zu verstehen, ist eine sinnvolle Code-Analyse unmöglich.

### Beweise dafür

1. `classes.dex` ist nur **208 KB groß** und enthält **30 Klassen** – für eine 116-MB-App mit RN-Anteilen unmöglich klein
2. Application-Class im Manifest: `com.netease.nis.wrapper.MyApplication`
3. Großer verschlüsselter Blob: `assets/nedata.db` (35 MB)
4. Verschlüsselte Konfig: `assets/nedig.properties` (~570 Bytes binär)
5. Verschlüsselte Sub-Archive: `assets/common_strings.zip` und `assets/backup_config.nb`, `assets/ec-sdk_config-default`
6. Native-Lib-Pfad-Hinweise im Wrapper: `libnesec.so`, `libdexfix.so`, `libneguard.so`, `libnesec64.so`

### String-Deobfuscation (`a.auu.a.c`)

Alle Strings im Wrapper sind via `a.auu.a.c("...")` verschlüsselt:

```python
import base64
KEY = b"Netease"   # 7 Bytes, zyklisch
def dec(s):
    b = base64.b64decode(s)
    return bytes(c ^ KEY[i % 7] for i, c in enumerate(b)).decode("utf-8", "replace")
```

Beispiele:

| Obfuskiert | Klartext |
|---|---|
| `ORcVFREWFw==` | `wrapper` |
| `LQoZSw8WESsEBwBPHQw9SwMXAAMVKxdaKBgyFT4JHQYABwwhCw==` | `com.netease.nis.wrapper.MyApplication` |
| `eUtCS1IsXHhc` | `7.6.3_969` |
| `IgwW` | `lib` |
| `qcTagM/p` | `确定` (chinesisch „OK") |

### Anti-Analyse-Detection-Klassen

Aus den entschlüsselten Englisch- und Chinesisch-Strings nachgewiesen:

| Englisch-String | Detection |
|---|---|
| Detected the presence of a debugger. | Debugger (ptrace, TracerPid) |
| Detected that the app is running on an emulator. | Emulator (Genymotion, QEMU, …) |
| Detected that the app is running in a rooted environment. | Root (su-Binary, Magisk) |
| Detected that the app is running in an environment with cloud phone. | Cloud-Phone (RedFinger, Bluestacks Cloud) |
| Detected that the app is running in an Xposed environment. | Xposed/EdXposed |
| Detected that the app is running in a hooking environment. | Frida, Substrate, Riru |
| Detected that the app has been injected. | LD_PRELOAD |
| Detected that the application has been tampered. | Signature-Tampering |
| Detected an abnormal ROM. | Custom-ROM |
| Detected that the USB debugging mode is enabled. | `adb_enabled` Setting |
| Detected that a VPN/proxy is being used. | VPN-Interface, HTTP-Proxy |
| Detected that the app is running in a dual app environment. | Parallel-Space, MultiApp |
| Detected that the app is running in an environment with screen share. | Screen-Recording |
| Detected that the app is running in an environment with fake location. | Mock-Location |
| Detected that the app is running in an environment with simulated clicks. | UIAutomator-Tap-Tools |
| Detected that the app is a trial version, please do not release it directly. | NetEase Trial-Watermark – **NICHT aktiv** → kommerzielle Lizenz |

Bei Detection wird via `NEDialog` ein blockierender Modal-Dialog angezeigt.

### Lade-Flow

```
Activity-Manager startet com.netease.nis.wrapper.MyApplication
  ↓
attachBaseContext():
   – o.a(ctx, "nesec")        → Multi-ABI-Extraction lib/<abi>/libnesec.so
   – Native init (n0110…)     → entschlüsselt nedata.db → tmp dex(es)
  ↓
onCreate():
   – ProxyComponentFactory installiert ClassLoader-Hooks
   – Echte Application-Klasse aus entschlüsseltem Code instanziiert
   – Echte Activity-Klassen werden über InstrumentationProxy umgeleitet
```

Konfigurations-Toggles (entschlüsselte Strings):

| Key | Bedeutung |
|---|---|
| `extract_switch_0` | Native-Libs in `files/lib/` extrahieren |
| `provider_switch_1` | Component-Factory-Proxy aktiv |
| `shell_limit_0` | App-Start-Throttling (Trial) |
| `x86_switch_1` | x86-ABI-Support |
| `BUGRPT_SWITCH` | Bugsnag-Crash-Reporting via NetEase-Wrapper |

---

## Network-Endpoints

### Service-Cluster-Map (`assets/Server.json`)

Zwei Hauptregionen (`inland` = China, `overseas`) × 9 Cluster × 5 Umgebungen (dev / test / release / migrate_cloud / production) = **90 URLs**.

#### Inland (China) – Production

| Cluster | URL |
|---|---|
| `php` (Legacy) | `https://api-jhcx-v6-bj.ninebot.com` |
| `java1` (OMS-Gateway) | `https://cnnx-omsgw.ninebot.com` |
| `java2` (CBU-Gateway) | `https://cn-cbu-gateway.ninebot.com` |
| `steeldust` (Telemetry) | `https://steeldust.ninebot.com` |
| `ebike` | `https://ebike.ninebot.com` |
| `web_url_v1` | `https://api5-h5-app-bj.ninebot.com` |
| `web_url_v2` | `https://h5-bj.ninebot.com` |
| `web_url_service` | `https://support-m.ninebot.com` |

#### Overseas – Production

| Cluster | URL |
|---|---|
| `php` | `https://api-jhcx-v6-%@.ninebot.com` (`%@` = Region-Code zur Laufzeit) |
| `java1` | `https://eu-oms-gateway.ninebot.com` |
| `java2` | `https://%@-oms-gateway.ninebot.com` |
| `steeldust` | `https://steeldust-os.ninebot.com` |
| `ebike` | `https://ebike-os.ninebot.com` |
| `web_url_v1` | `https://api5-h5-app-%@.ninebot.com` |
| `web_url_v2` | `https://h5-%@.ninebot.com` |
| `web_url_service` | `https://service-m.segway.com` ← Custom-Domain auf segway.com! |

### API-Routen (Auszug aus `JavaApiList.json`)

29 `ApiList` + 6 `Java1List`-Endpoints. Auswahl:

```
/app-api/system-time/v1/get-time
/app-api/system-config/v1/detail
/app-api/app-version/v1/check
/app-api/privacy-policy/v1/latest
/app-api/settings/v1/{set-pushid, set-deviceinfo}
/app-api/device/bind/{my-vehicle, v2/my-vehicle, is-binding}
/app-api/common-user/{get-invited-list, common-vehicle-info, user-accept, user-refuse}
/app-api/road-book/participate/v1/status
/app-api/offline-activity/{v1/pre-check, user/v1/check, theme/v1/list}
/app-api/task-center/v1/finish
/sun-portal/api/circle/v1/tab-config/{list-tab, get-tab-config, get-search-config}
/sun-portal/api/circle-label/v1/{label-detail, search-label-list}
/app-api/circle/{home/v1/list, v1/get-short-url, v1/share-callback,
                 v1/circle/{set-top, cancel-top, hide, cancel-hide, del-circle, get-translate}}
```

`circle` = Community-Feature ("Riding-Circles"), `sun-portal` = Backoffice-System.

### Carbit-Map / EasyConnect (CarLink)

Vollwertige **CarLink/EasyConnect-Integration** für KFZ-Spiegelung (überraschend für eine Scooter-App, hängt mit Segway-Off-Road-Vehicles / CFMOTO E-Bikes zusammen).

```json
// map_api_config.json (Production)
{
  "base_url":   "https://cfdlapi.cfmoto.com/",
  "track_url":  "https://cfdlapi.cfmoto.com/",
  "group_url":  "https://talkiesdk.cfmoto.com/",
  "encode_type":"carbit_map",
  "group_project":"CFDL02"
}
```

`CFDL02` ist die CFMOTO-Projekt-ID; `cfmoto.com` gehört zum chinesischen UTV/Motorrad-Hersteller.

### Cleartext-HTTP-Whitelist

22 Domains (siehe Manifest-Sektion oben). Auffällig: `rom.carbit.lo` (`.lo`-TLD existiert nicht offiziell – internes Carbit-Test-Subnet).

### Huawei GRS / China Unicom / Firebase

- **Huawei GRS**: `grs.dbankcloud.{com, cn, asia, eu}`, `grs.platform.dbankcloud.ru`
- **China Unicom (Quick-Login)**: `https://auth.wosms.cn` (Production)
- **Firebase Realtime DB**: `https://ninebot-5.firebaseio.com` (Project: `ninebot-5`)
- **ByteDance APMPlus**: in `assets/apmplus_hybrid/apmplus.hybrid.cn.js` (Hybrid-Telemetrie)

### Eindeutige Hosts (Zusammenfassung)

```
*.ninebot.com         – Hauptbackend
*.segway.com          – Marken-Domain (web_url_service overseas)
*.cfmoto.com          – CFMOTO Carlink
*.carbit.{com.cn,cn}  – Carbit (CarLink-Anbieter)
*.dbankcloud.{com,cn,asia,eu} – Huawei GRS
*.hicloud.com         – Huawei ML-Kit Hianalytics
*.sinaapp.com         – Weibo/WeChat-Login Helper
auth.wosms.cn         – China-Unicom CUCC One-Click-Login
ninebot-5.firebaseio.com – Firebase Realtime DB
```

---

## Eingebettete SDKs / Bibliotheken

Aus `META-INF/*.version`, `*.properties` und `assets/sdk_versions/`.

### Build-Toolchain
- AGP **8.6.1**, Kotlin **2.1.0**, source/target Java **17**

### Google / Firebase

| SDK | Version |
|---|---|
| Firebase Analytics | 22.5.0 |
| Firebase Cloud Messaging (FCM/IID) | 21.1.0 / 17.1.0 |
| Firebase Datatransport | 18.1.7 |
| play-services-* (auth, base, location, maps, measurement, …) | 18.x – 22.5 |
| transport-{api, runtime, backend-cct} | 3.0 / 3.1.8 |

### Huawei HMS

- HMSCore-hatool, agconnect-core, network-{common, framework-compat, grs}
- App-ID `100187639`, Push (Honor) `8.0.12.307`, Scankit `2.12.0.301`, dynamic-api `1.0.24.300`, Wear-Engine API-Level 6

### Maps / Navigation

| SDK | Hinweis |
|---|---|
| **Mapbox** Maps Core / Navigation / Search / UI | Vollständige Suite |
| **HERE SDK** | als `NAVI_CLIENT` (Default) |
| **Google Maps SDK** | als `MAP_CLIENT` (Overseas-Default) |
| **Tencent Maps SDK** | für Inland-Variante |
| **Baidu LBS SDK** | für Inland-Variante |
| **AltBeacon** | iBeacon-Scanning, `BeaconService` registriert |
| **Nordic BLE Scanner** | `no.nordicsemi.android.support.v18.scanner` |
| **Carbit Map SDK** | `MapTrackDataService`, `MapDataService` |

### React Native / Hybrid

- React Native Webview (`com.reactnativecommunity.webview`)
- **Hermes-Bytecode** (`assets/platform.zip → platform.bundle`, ~680 KB, v94)
- RN-Module mit Versions-Pins via `assets/rn_module_force_version.json` (Platform-Bundle MD5: `e35ea586c403e7284c6c8eb345759213`)
- **Bumptech Glide** + Glide-OkHttp3
- ByteDance **APMPlus Hybrid** JS-SDK

### Networking

- **OkHttp3** + PublicSuffix-DB
- **gRPC** (LoadBalancerProvider, ManagedChannelProvider, NameResolverProvider)
- **Liulishuo FileDownloader** (eigener Prozess `:filedownloader`)
- **NanoHTTPD** + **Apache FtpServer** – die App enthält einen vollwertigen embedded FTP-Server (vermutlich für Datei-Sharing über CarLink). Erhebliche Angriffsfläche im KFZ-Kontext.

### Auth / Login

- Facebook Login SDK
- Google Sign-In (Client-ID redacted)
- NetEase Quick-Login (`com.netease.nis.quicklogin`)
- Alipay-SDK
- Huawei AccountKit + Tencent QQ + WeChat
- Salesforce Service-Cloud Chat (Customer-Support)

### CarLink / Vehicle Mirroring

- `net.easyconn.carman.{music, media, speech, common, server, sdk}`
- `TrueMirrorService` (Screen-Mirroring, MediaProjection)
- `ecsocksserver.SocksService` (lokaler SOCKS-Proxy für KFZ-Tunnel im Sub-Prozess `:socks`)
- `cn.ninebot.nbvapp.*` (NbV-App – sandboxed Container für CarLink-Apps)
- Oppo Color-OS Carlink + Vivo Car Networking
- `pxc_rv.zip`: `librvserver.so` (1.9 MB), `easyrv`, `easyrv_pie` Binaries

### Imaging / ML

- **MNN** (Alibaba Mobile NN) – `assets/models/*.mnn`
  - `blink_mobilenetv2_sim_fp16.mnn` (Liveness-Blink)
  - `mouth_gaze_mobilenetv2_sim_fp16.mnn` (Mouth-Gaze)
  - `fd-quant.mnn` (Face-Detection)
- Huawei ML Kit (Face / Liveness / Scankit)
- **JavaCV** (`org/bytedeco/javacv/*.cl` – OpenCL-Kernel)
- **HBRecorder** (Bildschirm-Recording)

### Analytics / Crash

- Bugsnag (Build-UUID `dc9f2841-…`)
- Firebase Analytics + Measurement
- ByteDance APMPlus Hybrid
- Sensors Data Analytics (`com.sensorsdata.analytics.android`)

### Sicherheit / Crypto

- **Google Tink** (`tink-android-unshaded.jar`)
- AndroidX Security-Crypto + Biometric (1.1.0)
- Eigene RSA-Public-Keys (`config_rsa_public_key.pem`, `rsa_public_key.pem` – beide RSA-1024, ⚠ deprecated)
- Huawei BKS-Truststores (`grs_sp.bks`, `hmsincas.bks`, `hmsrootcas.bks`)
- **NetEase NIS Wrapper** (s. o.)

---

## Asset-Inventar

123 Top-Level-Einträge in `assets/`. Sortiert nach Größe:

### Top-Assets (>1 MB)

| Größe | Pfad | Inhalt |
|---|---|---|
| 35 MB | `nedata.db` | **NetEase NIS verschlüsselter Dex-Blob** (echter App-Code) |
| 22 MB | `geoviz/` | HERE / Mapbox 3D-Models, Texturen |
| 13 MB | `voice_assets/` | TTS/Voice-Prompts für 30+ Locales |
| 10 MB | `localization/` | TMC-Events (Verkehr) für 60+ Locale-Imperial-Kombis |
| 7,6 MB | `svga/` | SVGA-Animationen (Lottie-Konkurrent) |
| 5,9 MB | `motor/` | SVGA-Animationen Motor/Scooter-Status |
| 4,3 MB | `platform.zip` | Hermes-Bytecode der RN-Plattform |
| 2,5 MB | `device_default/` | Default-Vehicle (G30 KickScooter MAX, Gokart Pro) |
| 2,4 MB | `common_strings.zip` | **Verschlüsselte Strings** für 14 Locales (`.nb`) |
| 1,5 MB | `handlebar/` + `ebike/` | SVGA-Tutorials |
| 984 KB | `pxc_rv.zip` | EasyConnect Remote-View-Server |

### Wichtige Konfig-Dateien

| Pfad | Inhalt |
|---|---|
| `Server.json` | Backend-URL-Map |
| `JavaApiList.json` | API-Routing |
| `softcoded.json` | Geräte-Override-Liste (Xiaomi, Redmi, HONOR, …) |
| `country_mutil_{en,zh}.json` | Länder-Locale-Mapping |
| `device_{dark,light}.json` | Theme-Definitionen |
| `discover_*.json`, `mine_*.json`, `moment_*.json`, `service_*.json`, `store_*.json` | Tab-Themes |
| `map_api_config.json` / `_sandbox.json` | Carbit-Map-Backend |
| `grs_sdk_*.json` | Huawei GRS Routing |
| `rn_module_force_version.json` | RN-Bundle-Version-Pins |
| `ec-sdk_config.json` / `_default` | EasyConnect SDK Config (verschlüsselt!) |
| `nedig.properties` | NetEase Digital-Lock binär |
| `cucc/host_cucc.properties` | China-Unicom Quick-Login-Endpoint |
| `injectJSFunction.js` | WebView-Injection (versteckt `<select>`-Elemente) |

### Crypto-/Trust-Material

| Pfad | Inhalt |
|---|---|
| `config_rsa_public_key.pem` | RSA-1024 Public Key |
| `rsa_public_key.pem` | RSA-1024 Public Key (anderer Modulus) |
| `grs_sp.bks` | Huawei GRS Truststore |
| `hmsincas.bks` | Huawei intermediate CAs |
| `hmsrootcas.bks` | Huawei Root-CAs |
| `net_easyconn_blacklist` | EasyConnect Geräte-Blacklist |
| `net_easyconn_machinecfg` | EasyConnect Machine-Config |

### Locale-Coverage

`localization/tmcevents.*` deckt: bg, bs, cs, da, de, el, en, es, et, fi, fr, hr, hu, id, it, ja, ko, lt, lv, ms, nl, no, pl, pt, ro, ru, sk, sl, sr, sv, tr, uk, vi, zh – jeweils metric + imperial.

`voice_assets/voice_package_*` deckt: en-GB, ru-RU, es-{MX,AR}, ca-ES, nl-NL, gu-IN (Gujarati), kn-IN (Kannada), ar-SA und ~25 weitere → globale Marktreichweite.

### Auffälligkeiten

1. **Pinyin-DB** (280 KB) auch in der Overseas-Variante → Code-Sharing mit der China-App
2. **Kein einziges `.so`** in `lib/` → bestätigt App-Bundle-Format (Native-Libs in ABI-Splits)
3. **Liveness-Detection-Models** + `FaceDetectionActivity` → KYC-Flow für Self-Auth
4. **CFMOTO-Backend-URL + Carbit-Print-Asset** → starke Carbit/CFMOTO-Verzahnung

---

## Komponenten (kompakt)

| Typ | Anzahl | Top-Pakete |
|---|---|---|
| Activities | **197** | `cn.ninebot.{account, device, ninebot.mainshell, library, …}`, `com.business.accountoversea`, `com.alipay.sdk.app`, `com.facebook`, `com.netease.nis.quicklogin.ui`, `net.easyconn.carman` |
| Services | **54** | BLE-Keepalive (Local/Remote/Xiaomi/Huawei), Track-Service, Navi-Service, Salesforce-Chat, FCM, Mapbox-Navigation, EasyConnect (Music, Speech, Mirror, SOCKS) |
| Receivers | **30** | Push, App-Widgets, AirLock-Notification, Track-Net-State, Facebook-Auth, FCM, WorkManager, Oppo Carlink, EasyConnect |
| Providers | **14** | FileProvider (×2 mit gleicher Authority – Konflikt!), `network.track`, `androidx-startup`, `firebaseinitprovider`, `FacebookInitProvider`, `vapp.init` (NbV VirtualApp) |
| Meta-Data | 95 | – |

### Hauptaktivitäten (Entry-Points)

```
cn.ninebot.ninebot.mainshell.SplashActivity            ← exported, Theme.Ninebot6.Splash
cn.ninebot.ninebot.mainshell.MainActivity              ← Hauptbildschirm
cn.ninebot.ninebot.mainshell.MainOverseaActivity       ← Hauptbildschirm (Overseas)
cn.ninebot.ninebot.mainshell.UserAgreementActivity     ← First-run
cn.ninebot.ninebot.mainshell.ShareDataReceiveActivity  ← Share-Intent (exported)
cn.ninebot.ninebot.mainshell.ActionViewReceiveActivity ← Deep-Link-Handler (ninebot://)
```

### BLE-Pairing-Activities (Air-Lock / Beacon)

Multiple Implementierungen je Anwendungsfall:

```
cn.ninebot.device.motor.beacon.ble.ui.BleUnlockActivity        ← BLE-AirLock (Auto-Unlock)
cn.ninebot.device.sps.beacon.ble.ui.SpsBleUnlockActivity       ← SPS-BLE-Unlock
cn.ninebot.device.motor.beacon.hfp.ui.HfpActivity              ← Hands-Free-Profile
cn.ninebot.device.motor.beacon.hid.ui.HidActivity              ← HID-Pairing
cn.ninebot.device.motor.beacon.hidbr.{HidBrActivity, …}        ← HID-Bluetooth-Classic
cn.ninebot.device.scooter.beacon.hidbr.*                       ← Scooter-Variante
```

### Sub-Prozesse

- `:beacon` – `RemoteForegroundService` (BLE-Keepalive)
- `:filedownloader` – `FileDownloadService$SeparateProcessService`
- `:socks` – `SocksService` (lokaler SOCKS-Proxy für CarLink)
- `:ec_app_remote` – EasyConnect Apps-Service

### Custom Permissions (von CarLink-Komponenten erwartet)

- `easyconn.sdk.permission.DATA_SERVICE` (Protection-Level: dangerous)
- `com.vivo.car.networking.CAR_PERMISSION`
- `com.oppo.permission.safe.BLUETOOTH`

---

## Hartcodierte Secrets

> Alle Werte sind direkt aus der APK extrahierbar (Manifest, Asset-Files) und damit **nicht vertraulich** im kryptographischen Sinn. Trotzdem stellen sie eine Sicherheitsschwäche dar, falls Backend-Validierung sich allein auf den Besitz dieser Tokens stützt.

> ⚠ **Werte hier herausredacted**, um GitHub-Secret-Scanning nicht zu triggern und um nicht zur unautorisierten Token-Nutzung beizutragen. Vollständige Werte finden sich in der APK selbst (mit `apktool d` reproduzierbar).

### Mapbox Secret Token (KRITISCH 🔴)

```xml
<meta-data android:name="com.ninebot.android.MAPBOX_TOKEN"
           android:value="sk.<REDACTED-MAPBOX-SECRET-TOKEN>"/>
```

Der **`sk.`-Prefix** kennzeichnet ein **Secret-Token** mit Schreib-/API-Management-Rechten – NICHT das übliche Public-Token (`pk.`). Mit einem `sk.`-Token können API-Schlüssel rotiert, Tilesets gelöscht, Daten hochgeladen werden. **Gravierender Befund.**

JWT-Header (öffentlicher Header-Teil):
```json
{"u":"kay198XXXXX","a":"ckfXXXXXXXXXXXXXXXXXXXXXX"}
```

### Weitere geleakte Credentials

| Provider | Manifest-Key | Status |
|---|---|---|
| HERE Maps | `HERE_KEY_ID` + `HERE_KEY_SECRET` | OAuth-Style Access-Key-Pair |
| Tencent Maps | `TencentMapSDK` | für Inland-Variante |
| Baidu LBS | `com.baidu.lbsapi.API_KEY` | für Inland-Variante |
| Google Maps | `com.google.android.geo.API_KEY` | als String-Resource (Bundle-Restrictions empfohlen) |
| Bugsnag | `com.bugsnag.android.API_KEY` | Crash-Reporting |
| Facebook | `com.facebook.sdk.ClientToken` | OAuth-Redirect: `cct.com.ninebot.segway` |
| Google Sign-In | `cn.ninebot.google.CLIENT_ID` | OAuth Client-ID |
| Ninebot Passport | `PASSPORT_CLIENT_KEY` | UUID-Style Key für `*-oms-gateway.ninebot.com` |
| Huawei HMS | `client.appid = 100187639` | nicht geheim, App-Identifier |
| Oppo Color-OS Carlink | `AUTH_CODE` (Base64-Blob) + `companyid = 20029` | OAuth-Token |

### Carlink-Verifikations-Fingerprint

```xml
<meta-data android:name="fingerPrint" android:value="com.ninebot.ninebot_<88-byte-base64-blob>"/>
<meta-data android:name="targetSignature" android:value="56059A9193DD9FF053F86DF755A835B7"/>
<meta-data android:name="targetPackage" android:value="com.ninebot.ninebot"/>
```

`targetSignature` ist die MD5 der Original-Ninebot-China-App `com.ninebot.ninebot` – **Anker für Carlink-Validierung** (nur eine App mit dieser exakten Signatur wird als „echte Ninebot-App" akzeptiert).

### RSA Public Keys (Embedded, ⚠ RSA-1024 deprecated)

`assets/config_rsa_public_key.pem` (Modulus 1):
```
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDbVh6/cqYaI6Lgs//Mf2zViMgh
f9jvSabDKnlu6L6Ti0owwDJUKlDxuHzNyxexDkdbseNb5pFQTqelxX9ugHwDurPG
D9CuXYzhwmnEj6ka7UuCK5UChT/jd9MktHZofeMv+XJ85bbArbnMWB/ZIWLjYUJl
P/NRiXQSdQpl+NCkPQIDAQAB
-----END PUBLIC KEY-----
```

`assets/rsa_public_key.pem` (Modulus 2):
```
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC6maFY3dEhgav1147RW2gVWzCv
agkiRySnCDRSTM67YhHvLcrUSMnngxJl0A2liFLJydpn65E58oh0Phtu+t4Kkkfe
GIHsr931wRMRtkila4F/RF3U5pqSt42k/10U087QEhGMGvdOzF/5ziGXJod6ovBx
yk6pJlzNhxLTVJSzkQIDAQAB
-----END PUBLIC KEY-----
```

⚠ RSA-1024 gilt heute als **zu schwach** (NIST: deprecated seit 2014). Sollte gegen RSA-2048+ oder ECDSA-P-256 ausgetauscht werden.

### Empfehlungen (für Ninebot)

1. `MAPBOX_TOKEN` (`sk.…`) **sofort rotieren** und auf `pk.…` mit URL-Restrictions umstellen
2. `HERE_KEY_SECRET` rotieren und an Server-Seite verlagern (Token-Exchange-Endpunkt)
3. Bugsnag-Key, Facebook-Client-Token in `BuildConfig` statt `<meta-data>` für leichtere Rotation
4. RSA-1024 → RSA-2048 oder ECDSA-P-256
5. `Passport-Client-Key` durch Device-Attestation (Play-Integrity) zusätzlich absichern

---

## Limitierungen & nächste Schritte

### Was statisch NICHT geht (wegen NetEase NIS)

- ❌ Echter Anwendungs-Java-Code (`cn.ninebot.*` / `com.ninebot.*`)
- ❌ BLE GATT-Service-/Char-UUIDs der ZT3 Pro
- ❌ Paketstruktur des Ninebot-Protokolls (Header-Magic, CRC, Crypto-IV)
- ❌ Firmware-Update-Flow / Bootloader-Befehle
- ❌ SetSpeed/SetMode/Lock-Befehle
- ❌ Telemetrie-Field-Mappings
- ❌ Native Libraries (`*.so`) – sind in den Split-APKs (`config.arm64_v8a.apk` etc.), die hier nicht vorliegen
- ❌ Hermes-Bytecode der RN-Module (lesbar aber nicht zu JS dekompilierbar)
- ❌ Verschlüsselte String-Tabellen (`common_strings.zip`)

### Vorgehen (Priorität, nach Aufwand)

#### 1️⃣ Public-Domain-Wissen nutzen (einfachster Weg)

Das Ninebot/Segway-BLE-Protokoll ist seit Jahren in der Community dokumentiert (G30, ES2/4, F-Series). ZT3 Pro nutzt mit hoher Wahrscheinlichkeit die **moderne Variante** (siehe [SHU-Analyse](../shu/ANALYSIS.md)):

| Quelle | Inhalt |
|---|---|
| `m365-tools` (GitHub) | Original Xiaomi M365, Header `55 AA` |
| `ninebot-protocol` div. Repos | G30/E-Series mit AES |
| `NbCrypto` | AES-CCM + Pairing-Key-Exchange |
| **Diese Repo: SHU-Decompile** | ECDH/secp256r1 + AES-CCM + HKDF + HMAC-SHA-256 |

**Erste Schritte**: nRF Connect → Service-Liste vom ZT3 Pro abgreifen → mit `6e400001-b5a3-f393-e0a9-e50e24dcca9e` (NUS) abgleichen → wenn match: bestehende Tools direkt anwenden.

#### 2️⃣ HCI-Snoop-Capture

1. Android Developer-Options → "Bluetooth HCI snoop log" aktivieren
2. Phone ↔ ZT3 Pro normal über die Ninebot-App verbinden
3. Aktionen: Lock, Unlock, SetSpeed, ReadBattery
4. `/sdcard/btsnoop_hci.log` ziehen, mit Wireshark öffnen
5. ATT-Write-Pakete an die ZT3-Pro-Char zeigen die verschlüsselten Frames
6. Mit bekannten Ninebot-Frame-Mustern abgleichen → Header-Position klar
7. Bei AES-CCM: Pairing-Key durch Re-Pairing + Frida-Hook erfassen

#### 3️⃣ Frida-Memory-Dump (rooted Gerät)

```javascript
// frida-dexdump-style script
Java.perform(() => {
  const ClassLoader = Java.use("java.lang.ClassLoader");
  ClassLoader.loadClass.overload("java.lang.String").implementation = function (name) {
    if (name.startsWith("cn.ninebot") || name.startsWith("com.ninebot")) {
      console.log("[+] Loading: " + name);
    }
    return this.loadClass(name);
  };
  // Search memory for DEX magic
  Process.enumerateRangesSync({protection: 'r--', coalesce: true}).forEach(range => {
    try { Memory.scanSync(range.base, range.size, 'de 78 0a 03 35 00 00 00'); } catch (e) {}
  });
});
```

Bewährte Tools:
- [**frida-dexdump**](https://github.com/hluwa/FRIDA-DEXDump) – funktioniert oft direkt mit NetEase NIS
- **Youpk / yu-pk** – Custom-AOSP-Image für Laufzeit-Dex-Dump
- **NPManager** – Android-App, erkennt NetEase-Pack
- **Drizzle-DumpDex**, **SLJ-Dexdump**

##### BLE-Hooks parallel setzen

```javascript
Java.perform(() => {
  const BluetoothGatt = Java.use("android.bluetooth.BluetoothGatt");
  BluetoothGatt.writeCharacteristic.overload(
    "android.bluetooth.BluetoothGattCharacteristic"
  ).implementation = function (c) {
    console.log(`[BLE WRITE] ${c.getUuid()}: ${hexdump(c.getValue())}`);
    return this.writeCharacteristic(c);
  };
});
```

Pairing-Key extrahieren: `javax.crypto.Cipher.init` hooken und auf AES/CCM/CBC filtern.

#### 4️⃣ Native-Lib-Reverse (`libnesec.so`)

Wenn die Split-APK `config.arm64_v8a.apk` (aus SAI/APK-Combine) verfügbar ist:

1. Extrahieren von `lib/arm64-v8a/libnesec.so`
2. IDA Pro / Ghidra mit aktuellem ARM64-Plugin
3. Erwartung: stark obfuskierter Code (OLLVM Control-Flow-Flattening + Bogus-Control-Flow + String-Encryption)
4. Suche nach Crypto-Konstanten: AES-S-Box, SHA-256-Init-Werte, RC4-Stream
5. Tools wie **APKiD**, **D810** (OLLVM-Deobfuscator)

### Tool-Quickref

| Tool | Zweck |
|---|---|
| `apktool` 2.12.1 | APK → Smali + Resources |
| `jadx` 1.5.3 | Smali → Java |
| `dex2jar` / `bytecode-viewer` | DEX → JAR / All-in-one |
| `frida` ≥ 16 + `frida-dexdump` | Dynamic Instrumentation, Memory-Dump |
| `nRF Connect` (Android) / `LightBlue` (iOS) | BLE-Inspector |
| `Wireshark` + `BtSnoop` | HCI-Trace-Analyse |
| `IDA Pro` / `Ghidra` | Native ARM64 Reverse |
| `hbctool` / `hermes-decompiler` | Hermes-Bytecode → JS |
| `r2frida` | Radare2 + Frida-Bridge |

### Rechtlicher Hinweis

Reverse-Engineering der App und des ZT3-Pro-BLE-Protokolls ist in der EU unter **§ 69e UrhG** (Dekompilierung zur Herstellung von Interoperabilität) zulässig. **Veröffentlichung** der entschlüsselten Java-Klassen (z. B. `nedata.db`-Dump) ist hingegen problematisch. Reine BLE-Frame-Dokumentation aus HCI-Snoop ist unbedenklich.

---

## Quellen / Reproduktion

### Decompile reproduzieren

```bash
# Tools (einmalig)
brew install apktool jadx

# Aus dem Repo-Root
cd reverse-engineering/apps/ninebot-segway
apktool d -f -o decompiled/apktool com.ninebot.segway.apk
jadx -d decompiled/jadx --no-res com.ninebot.segway.apk
```

→ Decompile-Output ist gitignored (~350 MB), wird zur Laufzeit erzeugt.

### NetEase-String-Deobfuscator

```python
import base64
KEY = b"Netease"
def dec(s):
    b = base64.b64decode(s)
    return bytes(c ^ KEY[i % 7] for i, c in enumerate(b)).decode("utf-8", "replace")
```

### Verwandte Dokumente in diesem Repo

- [`apps/shu/ANALYSIS.md`](../shu/ANALYSIS.md) – Open-Source-Konkurrenz-App, BLE-Protokoll im Klartext
- [`../../UNLOCK-PLAN.md`](../../../UNLOCK-PLAN.md) – Konkreter Aktionsplan für ZT3 Pro D
- [`../../PRIOR-RESEARCH.md`](../../../PRIOR-RESEARCH.md) – Vorrecherche zu Pairing-Flow und Command-Tabelle
