# AndroidManifest.xml – Tiefgehende Analyse

Quelle: `decompiled/apktool/AndroidManifest.xml` (764 Zeilen)

## Header

```xml
<manifest package="com.ninebot.segway"
          android:compileSdkVersion="35"
          android:compileSdkVersionCodename="15"
          platformBuildVersionCode="35"
          platformBuildVersionName="15">
```

## Application-Tag

```xml
<application
    android:name="com.netease.nis.wrapper.MyApplication"   <!-- ← Wrapper, nicht echte App-Klasse -->
    android:allowBackup="false"
    android:allowNativeHeapPointerTagging="false"
    android:appComponentFactory="androidx.core.app.CoreComponentFactory"
    android:extractNativeLibs="false"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/nb_app_name"
    android:largeHeap="true"
    android:persistent="true"
    android:requestLegacyExternalStorage="true"
    android:supportsRtl="true"
    android:theme="@style/Theme.Ninebot6"
    android:usesCleartextTraffic="true"   <!-- ⚠ Cleartext zugelassen für Carbit-Domains, s. Network-Security-Config -->
/>
```

## Permissions (vollständige Liste)

### Bluetooth (für Scooter-Kommunikation)
- `BLUETOOTH` (maxSdk=30)
- `BLUETOOTH_ADMIN` (maxSdk=30)
- `BLUETOOTH_ADVERTISE`
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`

### Standort (BLE-Scan + Navi)
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `ACCESS_LOCATION_EXTRA_COMMANDS`

### Netzwerk
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_WIFI_STATE`
- `CHANGE_NETWORK_STATE`
- `CHANGE_WIFI_STATE`
- `CHANGE_WIFI_MULTICAST_STATE`
- `NEARBY_WIFI_DEVICES` (`neverForLocation`)

### Foreground-Services
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` – BLE-Pairing-Keep-Alive
- `FOREGROUND_SERVICE_DATA_SYNC` – Track-Upload, Filedownloader
- `FOREGROUND_SERVICE_LOCATION` – Navigation
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` – TrueMirror (Carlink-Screencast)
- `FOREGROUND_SERVICE_MICROPHONE`

### Speicher
- `READ_EXTERNAL_STORAGE` (maxSdk=32)
- `WRITE_EXTERNAL_STORAGE`
- `READ_MEDIA_AUDIO`
- `READ_MEDIA_VISUAL_USER_SELECTED`

### Health Connect (Distanz/Kalorien aus Fahrt)
- `health.WRITE_DISTANCE`
- `health.WRITE_EXERCISE`
- `health.WRITE_SPEED`
- `health.WRITE_TOTAL_CALORIES_BURNED`
- `health.WRITE_ACTIVE_CALORIES_BURNED`

### Sonstige
- `CAMERA` – QR-Codes, Self-Auth-Face-Detection
- `RECORD_AUDIO` – Sprachsteuerung
- `READ_PHONE_STATE`
- `READ_CONTACTS`
- `RECEIVE_BOOT_COMPLETED`, `RECEIVE_USER_PRESENT`
- `POST_NOTIFICATIONS`
- `SYSTEM_ALERT_WINDOW`, `SYSTEM_OVERLAY_WINDOW`
- `USB_PERMISSION` – Wired Carlink (UsbAccessory)
- `USE_BIOMETRIC`, `USE_FINGERPRINT`
- `VIBRATE`, `WAKE_LOCK`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION` – Privacy Sandbox

## Komponenten-Inventar

| Typ | Anzahl |
|---|---|
| Activities | 197 |
| Services | 54 |
| Receivers | 30 |
| Providers | 14 |
| Meta-Data-Tags | 95 |

Vollständige Listen: siehe `06-COMPONENTS.md`.

## Deep-Link-Schemata

```xml
<!-- ninebot:// scheme -->
<data android:host="navi"          android:scheme="ninebot"/>
<data android:host="carlink"       android:path="/bind"      android:scheme="ninebot"/>
<data android:host="carlink"       android:path="/unbind"    android:scheme="ninebot"/>
<data android:host="ninebot.ninebot.com" android:pathPrefix="/openPage" android:scheme="ninebot"/>

<!-- Facebook OAuth -->
<data android:host="cct.com.ninebot.segway" android:scheme="fbconnect"/>

<!-- Vivo Carlink -->
<data android:host="com.ninebot.ninebot" android:path="/deeplink" android:scheme="vivocar"/>
```

Angriffsfläche: Die `ninebot://carlink/bind|unbind`-Routen sind Activity-exposed (`exported=true`) und können von beliebigen anderen Apps aufgerufen werden. Sollte in einem dynamischen Test geprüft werden, ob hier Auth-Bypass-Möglichkeiten existieren.

## `<queries>`-Block (Package-Visibility)

App fragt explizit nach diesen anderen Apps (Android 11+ Package-Visibility):

| Package | Zweck |
|---|---|
| `com.tencent.mm` | WeChat-Login/Share |
| `com.tencent.mobileqq` | QQ-Login/Share |
| `com.tencent.map`, `com.baidu.BaiduMap`, `com.autonavi.minimap`, `com.google.android.apps.maps` | Externe Karten-Apps |
| `cn.ninebot.lite`, `com.ninebot.lite` | Lite-Variante (Datenmigration) |
| `com.heytap.health`, `com.google.android.apps.healthdata` | Health-Sync |
| `com.facebook.katana`, `com.sina.weibo` | Share |
| `com.heytap.opluscarlink`, `com.heytap.accessory`, `com.coloros.ocs.opencapabilityservice`, `com.oplus.ocs` | Oppo/Color-OS-Carlink |
| `com.huawei.hff`, `com.huawei.hms`, `com.huawei.hwid`, `com.huawei.hwid.tv` | Huawei HMS / Wearables |
| `com.eg.android.AlipayGphone`, `com.finshell.wallet`, `com.coloros.wallet` | Payment |

Plus Intents: `android.intent.action.PICK` (image/*), `IMAGE_CAPTURE`, `TTS_SERVICE`, `VIEW` (https + tbopen://).

## Wichtige Meta-Daten (Auswahl – Secrets siehe `07-SECRETS-FOUND.md`)

| Key | Value |
|---|---|
| `com.ninebot.android.STORAGE_PLATFORM_NAME` | `Aws` (Cloud-Speicher: AWS S3) |
| `com.ninebot.android.NB_HTTP_HOST_ENV_TYPE` | `production` |
| `com.ninebot.android.IS_DOMESTIC` | `false` (Overseas-Variante) |
| `com.ninebot.android.MAP_CLIENT` | `cn.ninebot.library.map.google.GoogleMapClient` |
| `com.ninebot.android.NAVI_CLIENT` | `cn.ninebot.library.here.navi.HereNaviManager` |
| `com.ninebot.android.PUSH_CLIENT` | `cn.ninebot.library.googlepush.GooglePushClient` |
| `cn.ninebot.ninebot.PASSPORT_CLIENT_ID` | `vehicle_app_overseas` |
| `com.ninebot.android.TEST_SERVER` | `false` |
| `com.ninebot.android.NEW_TEST_SERVER_ADDRESS` | `https://sg-test1-oms-gateway.ninebot.com/` |
| `com.bugsnag.android.RELEASE_STAGE` | `Online` |
| `com.bugsnag.android.BUILD_UUID` | `dc9f2841-d7f3-433a-b8aa-ce466e53ed9d` |
| `com.facebook.soloader.enabled` | `false` |
| `com.huawei.hms.client.appid` | `100187639` |
| `oplus.app.carlink.sdk.companyid` | `20029` |
| `targetSignature` | `56059A9193DD9FF053F86DF755A835B7` (MD5 der Signatur des Carlink-Targets `com.ninebot.ninebot`) |

## Network-Security-Config (`res/xml/network_security_config.xml`)

Cleartext-HTTP ist global aktiviert (`usesCleartextTraffic=true`) und zusätzlich für 22 Domains explizit erlaubt – im Wesentlichen die **Carbit/EasyConnect-Infrastruktur** (siehe `03-NETWORK-ENDPOINTS.md`). Auffällig: zwei rohe IPs `120.24.62.11:8085` und `120.79.98.236` stehen in der Whitelist – beides Aliyun-Hosts in Shenzhen/Hangzhou.

## App-Widgets

Vier App-Widgets registriert (Mini/Middle x normal/transparent), alle vom Typ `cn.ninebot.device.appWidget.widget.*`. XML-Definitionen unter `res/xml-v31/`.

## Backup-Verbot

`allowBackup="false"` – ADB-Backup deaktiviert. Plus `allowNativeHeapPointerTagging="false"` (kompatibilitätshalber, vermutlich wegen NetEase Native-Lib).

## Interessante Service-Tags

- `cn.ninebot.device.motor.beacon.ble.keepalive.RemoteForegroundService` läuft im Sub-Prozess `:beacon`
- `net.easyconn.carman.ecsocksserver.SocksService` läuft im Sub-Prozess `:socks` – **lokaler SOCKS-Proxy**, vermutlich für CarLink Daten-Tunnel
- `com.liulishuo.filedownloader.services.FileDownloadService$SeparateProcessService` im Sub-Prozess `:filedownloader`
- `cn.ninebot.nbvapp.provider.NbVAppServiceContentProvider` ist `multiprocess="true"` – das ist `nbvapp` (offenbar VirtualApp-artiges Sandboxing für CarLink-Apps)
- Zwei `FileProvider` mit identischer Authority `com.ninebot.segway.fileprovider` (einmal `androidx.core.content.FileProvider`, einmal `com.reactnativecommunity.webview.RNCWebViewFileProvider`) – potenziell konfliktanfällig
