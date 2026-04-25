# Asset-Inventar

`decompiled/raw/assets/` enthält 123 Top-Level-Einträge. Liste sortiert nach Größe:

## Top-Assets (>1 MB)

| Größe | Pfad | Inhalt |
|---|---|---|
| 35 MB | `nedata.db` | **NetEase NIS verschlüsselter Dex-Blob** (echter App-Code, siehe 02-NETEASE-SHIELDING.md) |
| 22 MB | `geoviz/` | HERE / Mapbox 3D-Models, Texturen für Geo-Visualisierung |
| 13 MB | `voice_assets/` | Sprachpakete (TTS/Voice-Prompts) für 30+ Locales |
| 10 MB | `localization/` | TMC-Events (Verkehrsmeldungen) für 60+ Locale-Imperial-Kombinationen |
| 7,6 MB | `svga/` | SVGA-Animationen (Lottie-Konkurrent, von YY Inc.) |
| 5,9 MB | `motor/` | SVGA-Animationen für Motor/Scooter-Status |
| 4,3 MB | `platform.zip` | **Hermes-Bytecode** der React-Native-Plattform (`platform.bundle`, ~680 KB Hermes v94) + Loading-SVGAs |
| 2,5 MB | `device_default/` | Default-Vehicle-Anzeige (G30 KickScooter MAX, Gokart Pro) |
| 2,4 MB | `common_strings.zip` | **Verschlüsselte Strings** für 14 Locales (de, ru, ko, zh-hant, en, it, fr, es, zh, vi, ja, pl, tr, nl) im `.nb`-Format |
| 1,5 MB | `handlebar/`, `ebike/` | SVGA-Tutorials für Handlebar-Kalibrierung & E-Bike |
| 984 KB | `pxc_rv.zip` | EasyConnect Remote-View-Server (`librvserver.so`, 1.9 MB unkomprimiert + `easyrv`/`easyrv_pie` Binaries) |

## Mittlere (100 KB – 1 MB)

| Größe | Pfad | Inhalt |
|---|---|---|
| 664 KB | `detect.ms` | Mapbox Style (Mapbox Style Spec) – `detect`-Layer |
| 624 KB | `models/` | MNN ML-Modelle: `blink_mobilenetv2_sim_fp16.mnn`, `mouth_gaze_mobilenetv2_sim_fp16.mnn`, `fd-quant.mnn` (Liveness-Detection) |
| 480 KB | `sps/` | SVGA-Animationen für SPS (Self-Propelling-System? – proprietäres Subsystem) |
| 436 KB | `slide_down_refresh.svga` | Pull-to-Refresh-Animation |
| 420 KB | `fonts/` | MiSans Latin (Bold/Medium/Regular) – Xiaomi-Open-Source-Font |
| 392 KB | `tracking.json` | Lottie-Animation für „Tracking"-Status (mit base64-PNG inline) |
| 372 KB | `corner.ms` | Mapbox Style |
| 280 KB | `unicode_to_hanyu_pinyin.txt` | Pinyin-DB für Chinesisch-Sortierung |
| 252 KB | `privacy.html` | Privacy-Policy-Embedded |
| 208 KB | `ai_publish.svga` | KI-Publish-Animation |
| 128 KB | `backup_config.nb` | **Verschlüsselte App-Config** (Backup-Defaults) |
| 124 KB | `angle.ms` | Mapbox Style |

## Konfiguration / kleine Dateien

| Pfad | Inhalt |
|---|---|
| `Server.json` | Backend-URL-Map (siehe 03-NETWORK-ENDPOINTS.md) |
| `JavaApiList.json` | API-Routing (siehe 03) |
| `softcoded.json` | Geräte-Override-Liste (Brand/Model/Annotation) für Xiaomi/Redmi/HONOR/Vivo etc. |
| `country_mutil_en.json` / `country_mutil_zh.json` | Länder-Locale-Mapping (~30 KB) |
| `device_dark.json` / `device_light.json` | Theme-Definitionen für Device-Tab |
| `discover_dark.json` / `discover_light.json` | Discover-Tab-Theme |
| `mine_dark.json` / `mine_light.json` | Mine-Tab-Theme |
| `moment_dark.json` / `moment_light.json` | Moment-(Community-)Tab-Theme |
| `service_dark.json` / `service_light.json` | Service-Tab-Theme |
| `store_dark.json` / `store_light.json` | Store-Tab-Theme |
| `map_api_config.json` / `map_api_config_sandbox.json` | Carbit-Map-Backend (cfmoto.com) |
| `grs_sdk_server_config.json` | Huawei GRS Hauptserver |
| `grs_sdk_global_route_config_mlkit.json` | Huawei ML-Kit GRS Routing |
| `rn_module_force_version.json` | RN-Bundle-Version-Pins |
| `ec-sdk_config.json` / `ec-sdk_config-default` | EasyConnect SDK Config (Default ist verschlüsselt!) |
| `ec-sdk_license` | EasyConnect Lizenz-Blob (192 Bytes) |
| `ec_dpi.json` | DPI-Mapping (3 Werte: 29610, 349, 44502 → 200) |
| `nedig.properties` | NetEase Digital-Lock binär (~570 Bytes) |
| `cucc/host_cucc.properties` | China-Unicom Quick-Login-Endpoint |
| `apmplus_hybrid/apmplus.hybrid.cn.js` | ByteDance APM+ Hybrid JS-SDK |
| `injectJSFunction.js` | WebView-Injection (versteckt `<select>`-Elemente alle 500ms – UI-Hack) |
| `knowledge_article_details.css` | CSS für Knowledge-Articles im WebView |
| `mobile.v2.27.4.html` | Offline-H5-Fallback |

## Crypto / Trust

| Pfad | Inhalt |
|---|---|
| `config_rsa_public_key.pem` | RSA-1024 Public Key (modulus n=…) |
| `rsa_public_key.pem` | RSA-1024 Public Key (anderer modulus) |
| `grs_sp.bks` | Huawei GRS Truststore (BouncyCastle Keystore, 1.4 KB) |
| `hmsincas.bks` | Huawei intermediate-CAs |
| `hmsrootcas.bks` | Huawei Root-CAs |
| `net_easyconn_blacklist` | EasyConnect Blacklist (3.7 KB, vermutlich Geräte-Blacklist) |
| `net_easyconn_machinecfg` | EasyConnect Machine-Config (5.2 KB) |

## 3D-Models / Texturen

| Pfad | Inhalt |
|---|---|
| `arrow_cap_medium.obj` | OBJ-3D-Model für Navi-Pfeil |
| `plane.obj` | OBJ-Plane für 3D-View |
| `location_indicator_navigation.obj/.png` | 3D-Navi-Indikator |
| `location_indicator_pedestrian.obj/.png` | 3D-Pedestrian-Indikator |
| `location_indicator_halo.obj/.png` | Halo-Effekt |

## Mapbox-Style-Dateien (`*.ms`)

`angle.ms`, `corner.ms`, `detect.ms` – Mapbox-Style-Spec-Files für maßgeschneiderte Map-Layer.

## Bilder / SVG

- `HERE_logo_full.svg`, `HERE_logo_full_inverted.svg` – HERE Maps Branding
- `carbit_print.png` – Carbit-Logo
- `nb_ic_move_marker.png`, `nb_ic_navi_screencast.png` – Navi-Icons
- `pic_error_*.zip/.png` – Error-State-Bilder
- Diverse SVGA-Animationen für Lock/Unlock/Charge/Connect/Bluetooth-Connecting

## Sub-Archive (rekursiv)

`platform.zip` enthält:
```
platform/config.json                                (138 B)
platform/assets/platform.bundle                     (Hermes-Bytecode v94, 680 KB)
platform/res/raw/src_components_loadingsvga_*.svga  (~10 SVGAs für Loading)
```

`common_strings.zip` enthält 14 verschlüsselte `.nb`-Dateien (Locale-Strings) + `link.nb` + `manifest.nb`.

`pxc_rv.zip` enthält:
```
easyrv          (96 KB)
easyrv_pie      (100 KB)  ← Position-Independent-Executable für Android 5+
librvserver.so  (1.9 MB)  ← Native CarLink Remote-View Server
rv.cfg          (208 B)
```

## Locale-Coverage

`localization/tmcevents.*` deckt: bg, bs, cs, da, de, el, en, es, et, fi, fr, hr, hu, id, it, ja, ko, lt, lv, ms, nl, no, pl, pt, ro, ru, sk, sl, sr, sv, tr, uk, vi, zh – jeweils metric + imperial.

`voice_assets/voice_package_*` deckt: en-GB, ru-RU, es-{MX,AR}, ca-ES, nl-NL, gu-IN, ar-SA, kn-IN und ~25 weitere.

## Bemerkenswerte Auffälligkeiten

1. **Pinyin-DB** (280 KB) ist auch in der Overseas-Variante mit drin – Code-Sharing mit der China-App.
2. **Kein einziges `.so`** in `lib/` – bestätigt App-Bundle-Format. Native Libraries wurden in ABI-Splits ausgelagert (`config.arm64_v8a.apk` etc.).
3. **Sprachpakete für sehr seltene Sprachen** (gu-IN Gujarati, kn-IN Kannada) → globale Marktreichweite.
4. **Liveness-Detection-Models** (`blink_mobilenetv2`, `mouth_gaze_mobilenetv2`) sind im selben Asset-Verzeichnis – aktiviert durch die `FaceDetectionActivity` für Self-Auth.
5. **Carbit-Print-Asset + CFMOTO-Backend-URL** → starke Carbit-/CFMOTO-Verzahnung.
