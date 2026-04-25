# Third-Party SDKs / Libraries

Quellen:
- `decompiled/raw/*.properties` (Firebase, Play-Services, etc. – Versionen)
- `decompiled/raw/META-INF/*.version` (AndroidX-Module)
- `decompiled/raw/META-INF/services/` (ServiceLoader-Provider)
- `assets/sdk_versions/` (Mapbox-Module)
- Manifest-Komponenten + `<meta-data>`
- `decompiled/raw/com/`, `decompiled/raw/google/`, `decompiled/raw/org/`, `decompiled/raw/okhttp3/`, `decompiled/raw/pinyindb/`

## Build-Toolchain

- Android Gradle Plugin: **8.6.1**
- Kotlin: **2.1.0** (sourceCompatibility/target=17)
- AAPT-Stamp: Google Play Distribution

## Google / Firebase SDKs

| SDK | Version |
|---|---|
| Firebase Analytics | 22.5.0 |
| Firebase Annotations | 16.2.0 |
| Firebase Core | 21.1.1 |
| Firebase Cloud Messaging (FCM) / IID | 21.1.0 / 17.1.0 |
| Firebase Encoders | 17.0.0 (proto: 16.0.0) |
| Firebase Datatransport | 18.1.7 |
| Firebase Measurement-Connector | 19.0.0 |
| Firebase Realtime Database | (Project: `ninebot-5`) |

## Google Play Services

| SDK | Version |
|---|---|
| play-services-ads-identifier | 18.0.0 |
| play-services-auth | 20.3.0 |
| play-services-auth-api-phone | 18.0.1 |
| play-services-auth-base | 18.0.4 |
| play-services-base / basement | 18.5.0 |
| play-services-cloud-messaging | 17.0.1 |
| play-services-location | 20.0.0 |
| play-services-maps | 18.1.0 |
| play-services-measurement* | 22.5.0 |
| play-services-stats | 17.0.2 |
| play-services-tasks | 18.2.0 |
| transport-api / runtime / backend-cct | 3.0.0 / 3.1.8 |
| `places` | 3.0.0 |

## Huawei HMS SDKs

- HMSCore-hatool (analytics)
- network-common, network-framework-compat, network-grs (HMS GRS routing)
- agconnect-core
- Huawei AppId (Manifest): `100187639`
- Huawei Push (Honor): `com.hihonor.push.sdk_version = 8.0.12.307`
- Huawei Scankit: `huawei_module_scankit_local = 21200301`, `scanplus = 2.12.0.301`
- Huawei dynamic-api: `1.0.24.300`
- Huawei Wear-Engine API-Level 6
- Huawei Quick App SDK Version: `3400`

## Maps / Navigation

| SDK | Hinweis |
|---|---|
| **Mapbox Maps Core / Navigation / Search / UI** | Vollständige Mapbox-Suite. Module unter `assets/sdk_versions/com.mapbox.*` |
| **HERE SDK** | Manifest deklariert `cn.ninebot.library.here.navi.HereNaviManager` als `NAVI_CLIENT`, `cn.ninebot.library.here.HereClient` als `NAVI_LOC_PICK_VIEW_CLIENT` |
| **Google Maps SDK** | als `MAP_CLIENT` (Default für Overseas) |
| **Tencent Maps SDK** | API-Key vorhanden (für Inland-Variante) |
| **Baidu LBS SDK** | API-Key vorhanden (für Inland-Variante) |
| **AltBeacon (org.altbeacon.beacon)** | iBeacon-Scanning, Service `BeaconService` registriert |
| **Nordic BLE Scanner** | `no.nordicsemi.android.support.v18.scanner` – Activity-Komponenten registriert |
| **Carbit Map SDK** | `com.carbit.map.sdk.db.MapTrackDataService`, `com.carbit.map.server.MapDataService` |

Mapbox-Submodule (aus `assets/sdk_versions/`):
```
com.mapbox.common.core
com.mapbox.maps.core
com.mapbox.navigation, navigation.core
com.mapbox.navigation.ui.{base,maneuver,maps,resources,shield,speedlimit,status,tripprogress,utils,voice}
com.mapbox.search, search.{autocomplete,autofill,base,common,offline,ui}
```

## React Native / JavaScript-Frontend

- **React Native Webview** (`com.reactnativecommunity.webview.RNCWebViewFileProvider`)
- **Hermes-Bytecode**: `assets/platform.zip → platform/assets/platform.bundle` (Hermes JavaScript bytecode, version 94, ~680 KB)
- App nutzt RN-Module mit Versionsanforderungen aus `assets/rn_module_force_version.json`:
  - Mainland & Overseas: VehicleControl, VehicleAuth, Firmware, Track, Service, BatteryInfo, Mine, NewcomerGuide, etc.
  - Platform-Bundle MD5: `e35ea586c403e7284c6c8eb345759213` (Version 49)
- **Bumptech Glide** + Glide-OkHttp3-Integration (Bilder)
- ByteDance **APMPlus Hybrid** JS-SDK (`apmplus.hybrid.cn.js` – Hybrid-Telemetrie)

## Networking

- **OkHttp3** + PublicSuffix-DB (`okhttp3/internal/publicsuffix/publicsuffixes.gz`)
- **gRPC** (Provider-Definitionen in `META-INF/services/io.grpc.*`)
- **Liulishuo FileDownloader** (`com.liulishuo.filedownloader`) – downloads in eigenem Prozess `:filedownloader`
- **NanoHTTPD** (`META-INF/nanohttpd/`) – embedded HTTP-Server (vermutlich für CarLink-Lokalproxy)
- **Apache FtpServer** (`org/apache/ftpserver/`) – ja, die App enthält einen vollwertigen FTP-Server (vermutlich für Datei-Sharing über CarLink)

## Auth / Login / Account

- **Facebook Login SDK** (FacebookInitProvider, Activity-Komponenten, Client Token im Manifest)
- **Google Sign-In** (Client-ID: `<REDACTED>.apps.googleusercontent.com` – siehe `07-SECRETS-FOUND.md`)
- **NetEase Quick-Login** (`com.netease.nis.quicklogin.ui.*`)
- **Alipay-SDK** (`com.alipay.sdk.app.*`)
- **Huawei AccountKit** + Tencent QQ + WeChat (Login)
- Salesforce Service-Cloud Chat: `com.salesforce.android.chat.core.internal.service.ChatService`, `LiveAgentLoggingService` (Customer-Support-Chat)

## CarLink / Vehicle Mirroring

Die App enthält den kompletten **EasyConnect/Carbit CarLink**-Stack:
- `net.easyconn.carman.*` (Music, Speech, Common, Server, SDK)
- `net.easyconn.carman.TrueMirrorService` (Screen-Mirroring)
- `net.easyconn.carman.ecsocksserver.SocksService` (lokaler SOCKS-Proxy für KFZ-Tunnel)
- `net.easyconn.carman.sdk_communication.PXCKeepAliveService`
- `cn.ninebot.nbvapp.*` (NbV-App = nb VirtualApp – sandboxed Container für CarLink-Apps)
- Oppo Color-OS Carlink (`com.coloros.ocs.carlink.inner.OplusCarReceiver`, AUTH_CODE im Manifest)
- Vivo Car Networking SDK (`com.vivo.car.networking.sdk.nearby.NearbyService`)

`pxc_rv.zip` enthält native Binaries für den RV(Remote-View)-Server: `librvserver.so` (1.9 MB), `easyrv`, `easyrv_pie` (PIE-fähige Binary).

## Imaging / ML

- **MNN** (Alibaba Mobile Neural Network) – `assets/models/*.mnn`:
  - `blink_mobilenetv2_sim_fp16.mnn` – Blink-Detection (Liveness)
  - `mouth_gaze_mobilenetv2_sim_fp16.mnn` – Mouth-Gaze (Liveness/Selfie)
  - `fd-quant.mnn` – Face-Detection
- Huawei ML Kit (Face / Liveness / Scankit)
- **JavaCV** (`org/bytedeco/javacv/*.cl` – OpenCL-Kernel-Sources) – Bild-Pipeline
- **OpenSL ES** / Camera2 (Camera-Workflow)
- **HBRecorder** (`com.hbisoft.hbrecorder.ScreenRecordService`) – Bildschirm-Recording

## Crash / Analytics

- Bugsnag (API-Key im Manifest, Build-UUID `dc9f2841-d7f3-433a-b8aa-ce466e53ed9d`)
- Firebase Analytics + Measurement
- ByteDance APMPlus Hybrid
- Sensors Data Analytics (`com.sensorsdata.analytics.android`)

## Sicherheit / Crypto

- **Google Tink** (`build-data: tink-android-unshaded.jar`) – Verschlüsselungs-Library
- **AndroidX Security-Crypto**
- **AndroidX Biometric** (1.1.0) + Fingerprint
- Eigene RSA-Public-Keys (`assets/config_rsa_public_key.pem`, `assets/rsa_public_key.pem` – beide 1024-Bit; siehe `07-SECRETS-FOUND.md`)
- **NetEase NIS Wrapper** (Anti-Tamper, ausführlich in `02-NETEASE-SHIELDING.md`)
- Huawei BKS-Truststores: `assets/grs_sp.bks`, `hmsincas.bks`, `hmsrootcas.bks`

## React Native / Web-View

- React Native Bridge (Hermes engine)
- Bundled HTML: `assets/mobile.v2.27.4.html` – mutmaßlich offline H5-Fallback
- Privacy: `assets/privacy.html` (252 KB)
- Knowledge UI Analytics + Chat Analytics (`ChatAnalyticsmd`, `KnowledgeUIAnalyticsmd` – Markdown ohne Extension)

## AndroidX (Auswahl)

| Modul | Version |
|---|---|
| activity / activity-ktx | 1.8.0 |
| appcompat | 1.3.1 |
| camera-* | 1.4.2 |
| core / core-ktx | 1.9.0 / 1.6.0 |
| datastore | 1.0.0 |
| databinding | 8.6.1 |
| biometric | 1.1.0 |
| security-crypto | (vorhanden) |
| Health-Platform-Client | (HealthDataSdkService deklariert) |
| WorkManager | (mehrere Receiver/Services) |
| Room | + MultiInstanceInvalidationService |

## Sonstige Auffälligkeiten

- **Apache FTP-Server** + **NanoHTTPD** im selben APK → der „nbvapp"-Container kann offenbar selbst lokale HTTP/FTP-Server hochfahren (für CarLink). Das ist eine erhebliche Angriffsfläche, falls in einer CarLink-Sitzung exposed.
- **JavaCV mit OpenCL-Kerneln** in einer Scooter-App ist überraschend – vermutlich für Foto-/Video-Bearbeitung in der Community-Funktion oder Liveness-Detection.
- **Pinyin-DB** (`pinyindb/unicode_to_hanyu_pinyin.txt`, 280 KB) – Chinesisch-Pinyin-Mapping für Suche/Sortierung.
- **gRPC + Protobuf** – Push-Service kommuniziert via Protobuf (`messaging_event.proto`, `messaging_event_extension.proto`).
- **Reactive Streams** (`META-INF/rxjava.properties`) – RxJava verwendet.
- **Jackson** (`com.fasterxml.jackson.core` – mit JsonFactory ServiceLoader)

## ServiceLoader-Provider (META-INF/services)

```
cn.ninebot.nbvapp.api.core.IVAppServiceLoader      ← Custom VirtualApp-API
com.fasterxml.jackson.core.JsonFactory
io.grpc.LoadBalancerProvider
io.grpc.ManagedChannelProvider
io.grpc.NameResolverProvider
kotlinx.coroutines.CoroutineExceptionHandler
kotlinx.coroutines.internal.MainDispatcherFactory
```
