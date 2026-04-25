# Backend-Endpoints, APIs, Domains

Quellen:
- `decompiled/raw/assets/Server.json` – Service-Cluster-Map
- `decompiled/raw/assets/JavaApiList.json` – API-Routing-Liste
- `decompiled/raw/assets/map_api_config.json`, `map_api_config_sandbox.json` – Carbit-Map-Backend
- `decompiled/raw/assets/grs_sdk_*.json` – Huawei GRS Service Routing
- `decompiled/apktool/res/xml/network_security_config.xml` – Cleartext-erlaubte Domains
- `decompiled/apktool/AndroidManifest.xml` – Test-Server-Meta-Daten

## Komplette Service-Cluster-Map (Server.json)

Die App kennt zwei Hauptregionen (`inland` = Festland-China, `overseas` = international) mit jeweils 9 Service-Clustern und 5 Umgebungen (dev/test/release/migrate_cloud/production).

### Inland (China) – Production-Endpoints

| Cluster | Production-URL |
|---|---|
| `php` (Legacy-API) | `https://api-jhcx-v6-bj.ninebot.com` |
| `java1` (OMS-Gateway) | `https://cnnx-omsgw.ninebot.com` |
| `java2` (CBU-Gateway) | `https://cn-cbu-gateway.ninebot.com` |
| `steeldust` (Telemetry/Track-Service) | `https://steeldust.ninebot.com` |
| `ebike` (E-Bike-Backend) | `https://ebike.ninebot.com` |
| `assisted_ebike` | `https://cnnx-omsgw.ninebot.com` |
| `web_url_v1` | `https://api5-h5-app-bj.ninebot.com` |
| `web_url_v2` | `https://h5-bj.ninebot.com` |
| `web_url_service` | `https://support-m.ninebot.com` |

### Overseas – Production-Endpoints

| Cluster | Production-URL |
|---|---|
| `php` | `https://api-jhcx-v6-%@.ninebot.com` (Region als `%@`-Platzhalter) |
| `java1` | `https://eu-oms-gateway.ninebot.com` |
| `java2` | `https://%@-oms-gateway.ninebot.com` (Region) |
| `steeldust` | `https://steeldust-os.ninebot.com` |
| `ebike` | `https://ebike-os.ninebot.com` |
| `assisted_ebike` | `https://eu-oms-gateway.ninebot.com` |
| `web_url_v1` | `https://api5-h5-app-%@.ninebot.com` |
| `web_url_v2` | `https://h5-%@.ninebot.com` |
| `web_url_service` | `https://service-m.segway.com` ← Custom-Domain auf segway.com! |

Die `%@`-Platzhalter werden zur Laufzeit durch Region-Codes ersetzt – mutmaßlich `eu`, `na`, `sg`, etc. Aus dem Manifest (`NEW_TEST_SERVER_ADDRESS`, `JAVA_TEST_SERVER_ADDRESS`) lässt sich ableiten, dass Singapur (`sg`) Default ist.

### Test/Staging-Endpoints (interessant für Recon)

| Region | Cluster | URL |
|---|---|---|
| Inland | php-dev | `https://api-jhcx-v6-dev-bj.ninebot.com` |
| Inland | php-test | `https://api-jhcx-v6-test-bj.ninebot.com` |
| Inland | java1-dev | `https://cn-dev-oms-gateway.ninebot.com` |
| Inland | steeldust-dev | `https://steeldust-dev.ninebot.com` |
| Inland | ebike-dev | `https://ebike-dev.ninebot.com` |
| Overseas | php-test | `https://api6-sg-test.ninebot.com` |
| Overseas | java1-test | `https://sg-test1-oms-gateway.ninebot.com` |
| Overseas | steeldust-test | `https://os-st-test.ninebot.com` |
| Overseas | ebike-test | `https://os-eb-test.ninebot.com` |
| Overseas | web-service-test | `https://test-mobile-service-os.ninebot.com` |

## API-Endpoints (Auszug aus JavaApiList.json)

29 API-Routen für `ApiList`, 6 für `Java1List`. Routing-Logik dürfte bestimmen, welcher Cluster (php vs. java1/2) den Request erhält.

```
/app-api/system-time/v1/get-time
/app-api/system-config/v1/detail
/app-api/app-version/v1/check
/app-api/privacy-policy/v1/latest
/app-api/settings/v1/set-pushid
/app-api/settings/v1/set-deviceinfo
/app-api/survey/v1/info
/app-api/ad/v1/list

/app-api/device/bind/my-vehicle
/app-api/device/bind/v2/my-vehicle             ← Java1
/app-api/device/bind/is-binding                 ← Java1

/app-api/common-user/get-invited-list
/app-api/common-user/v2/get-invited-list        ← Java1
/app-api/common-user/common-vehicle-info        ← Java1
/app-api/common-user/user-accept                ← Java1
/app-api/common-user/user-refuse                ← Java1

/app-api/road-book/participate/v1/status
/app-api/offline-activity/v1/pre-check
/app-api/offline-activity/user/v1/check
/app-api/offline-activity/theme/v1/list
/app-api/task-center/v1/finish

/sun-portal/api/circle/v1/tab-config/list-tab
/sun-portal/api/circle/v1/tab-config/get-tab-config
/sun-portal/api/circle/v1/tab-config/get-search-config
/sun-portal/api/circle-label/v1/label-detail
/sun-portal/api/circle-label/v1/search-label-list

/app-api/circle/v1/get-short-url
/app-api/circle/v1/share-callback
/app-api/circle/v1/circle/set-top
/app-api/circle/v1/circle/cancel-top
/app-api/circle/v1/circle/hide
/app-api/circle/v1/circle/cancel-hide
/app-api/circle/v1/circle/del-circle
/app-api/circle/v1/circle/get-translate
/app-api/circle/home/v1/list
```

`circle` = Community-Feature („Riding-Circles"), `sun-portal` = vermutlich ein Backoffice-System.

## Carbit-Map / EasyConnect (CarLink)

Die App enthält eine vollwertige **CarLink/EasyConnect**-Integration für KFZ-Spiegelung – das ist überraschend für eine Scooter-App, hängt aber mit Segway-Ninebot Off-Road-Vehicles (CFMOTO E-Bikes) zusammen.

### Production (`map_api_config.json`)
```json
{
  "base_url":   "https://cfdlapi.cfmoto.com/",
  "track_url":  "https://cfdlapi.cfmoto.com/",
  "group_url":  "https://talkiesdk.cfmoto.com/",
  "encode_type":"carbit_map",
  "group_project":"CFDL02"
}
```

### Sandbox (`map_api_config_sandbox.json`)
```json
{
  "base_url":   "http://smapboxapi.carbit.cn/",
  "track_url":  "http://smapboxapi.carbit.cn/",
  "group_url":  "https://stalkiesdk.carbit.cn",
  "encode_type":"carbit_map",
  "group_project":"CFDL02"
}
```

`CFDL02` ist offenbar die CFMOTO-Projekt-ID. `cfdlapi.cfmoto.com` ist die Live-Map-Backend-Domain, `cfmoto.com` gehört zum chinesischen Motorrad-/UTV-Hersteller CFMOTO.

## Cleartext-HTTP-Whitelist (network_security_config.xml)

Diese Domains dürfen unverschlüsselt (HTTP) erreicht werden:

```
mobile.carbit.com.cn
premobile.carbit.com.cn
cd.carbit.com.cn
reg.carbit.com.cn
sapi.carbit.com.cn
sapiota.carbit.com.cn
smobile.carbit.com.cn
sh5.carbit.com.cn
sdown.carbit.com.cn / down.carbit.com.cn
sdown.carbit.cn / down.carbit.cn
rom.carbit.lo                  ← internal .lo TLD!
spsn.carbit.cn
sjapi.carbit.cn
smapboxapi.carbit.cn
stalkiesdk.carbit.cn
wxlinktest.sinaapp.com         ← Sina AppEngine
wxlink.sinaapp.com
wxlink.vipsinaapp.com
120.24.62.11:8085              ← Aliyun Shenzhen, Plain HTTP
120.79.98.236                  ← Aliyun Shenzhen
```

`rom.carbit.lo` ist eine `.lo`-Domain (nicht .local, nicht .lo TLD existiert offiziell) – wirkt wie ein internes Carbit-Test-Subnet.

## Huawei GRS (Generic Routing Service)

Aus `assets/grs_sdk_server_config.json`:

```
https://grs.dbankcloud.com    (DR1 = China)
https://grs.dbankcloud.cn
https://grs.dbankcloud.asia
https://grs.platform.dbankcloud.ru   (DR4 = Russia)
https://grs.dbankcloud.eu     (DR5 = Europe)
```

GRS routet je nach `country_group` HMS-Aufrufe (Push, Analytics, Maps, ML-Kit). Die App registriert HMS-Push, ML-Kit-Hianalytics und Huawei Wear-Engine.

## CUCC (China-Unicom-Authn)

`assets/cucc/host_cucc.properties` (Quick-Phone-Login via Carrier):
```
PRODUCE_DZH = https://auth.wosms.cn
# Alternativen (auskommentiert):
#  https://daily.m.zzx9.cn   – statistik
#  https://m.zzx.cnklog.com  – test
#  https://ms.zzx9.cn        – Guangzhou intranet
```

## Firebase

Aus `strings/resources.arsc`: `https://ninebot-5.firebaseio.com` → Firebase-Project-Name **ninebot-5**.

## APMPlus (ByteDance Application Performance Monitor)

`assets/apmplus_hybrid/apmplus.hybrid.cn.js` ist das ByteDance APM+ JS-Hybrid-SDK – Telemetrie für H5-Module.

## Sensors Analytics

Manifest deklariert `com.sensorsdata.analytics.android` – chinesisches Analytics-SDK (ähnlich Mixpanel).

## Bugsnag (Crash-Reporting)

API-Key im Manifest (redacted, siehe `07-SECRETS-FOUND.md`). Release-Stage: `Online`.

## Zusammenfassung: Komplette eindeutige Hosts

```
*.ninebot.com                  – Hauptbackend
*.segway.com                   – Marken-Domain (nur web_url_service overseas)
*.cfmoto.com                   – CFMOTO Carlink-Backend
*.carbit.com.cn / *.carbit.cn  – Carbit (CarLink-Anbieter, CN/CDN)
*.dbankcloud.{com,cn,asia,eu}  – Huawei GRS
*.hicloud.com                  – Huawei ML-Kit Hianalytics
*.sinaapp.com                  – Weibo/WeChat-Login Helper
auth.wosms.cn                  – China-Unicom CUCC One-Click-Login
ninebot-5.firebaseio.com       – Firebase Realtime DB / Project
```
