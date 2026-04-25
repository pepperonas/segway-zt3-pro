# Hartcodierte Secrets, API-Keys, Crypto-Material

> Alle Werte hier sind **direkt aus der APK** extrahierbar (Manifest, Asset-Files) und damit **nicht vertraulich** im kryptographischen Sinn. Trotzdem stellen sie eine Sicherheitsschwäche dar, falls die Backend-Validierung sich allein auf den Besitz dieser Tokens stützt.

## Mapbox Secret Token (KRITISCH)

```xml
<meta-data
    android:name="com.ninebot.android.MAPBOX_TOKEN"
    android:value="sk.<REDACTED-MAPBOX-SECRET-TOKEN>"/>
```

> Der vollständige Wert ist in der APK enthalten und kann mit `apktool d` gegen
> `reverse-engineering/apps/ninebot-segway/com.ninebot.segway.apk` reproduziert
> werden. Aus diesem Repo wird er bewusst herausredacted, um GitHub-
> Secret-Scanning nicht zu triggern und um nicht zur Token-Rotation
> beizutragen.

JWT-Header decoded (öffentlicher Header-Teil):
```json
{"u":"kay198XXXXX","a":"ckfXXXXXXXXXXXXXXXXXXXXXX"}
```

Der **`sk.`-Prefix** kennzeichnet ein **Secret-Token** mit Schreib-/API-Management-Rechten – NICHT das übliche Public-Token (`pk.`). Mit einem `sk.`-Token können API-Schlüssel rotiert, Tilesets gelöscht, Daten hochgeladen werden. **Das ist ein gravierender Befund.** Account-User-ID maskiert.

## HERE Maps Credentials

```xml
<meta-data android:name="com.ninebot.android.HERE_KEY_ID"
           android:value="<REDACTED-HERE-KEY-ID>"/>
<meta-data android:name="com.ninebot.android.HERE_KEY_SECRET"
           android:value="<REDACTED-HERE-KEY-SECRET>"/>
```

HERE-OAuth-2.0-Style Access-Key-Pair – kann zum Abruf von HERE-Routing/Maps-Quotas verwendet werden. Sollte regelmäßig rotiert werden.

## Tencent Maps SDK Key

```xml
<meta-data android:name="TencentMapSDK"
           android:value="<REDACTED-TENCENT-MAPS-KEY>"/>
```

## Baidu LBS API Key

```xml
<meta-data android:name="com.baidu.lbsapi.API_KEY"
           android:value="<REDACTED-BAIDU-LBS-KEY>"/>
```

## Google Maps API Key

```xml
<meta-data android:name="com.google.android.geo.API_KEY"
           android:value="@string/google_maps_key"/>
```

Wert liegt in `res/values/strings.xml` als String-Ressource (für Overseas-Build typischerweise mit Bundle-Restrictions abgesichert).

## Bugsnag API Key

```xml
<meta-data android:name="com.bugsnag.android.API_KEY"
           android:value="<REDACTED-BUGSNAG-API-KEY>"/>
```

## Facebook App Credentials

```xml
<meta-data android:name="com.facebook.sdk.ApplicationId"
           android:value="@string/facebook_app_id"/>
<meta-data android:name="com.facebook.sdk.ClientToken"
           android:value="<REDACTED-FACEBOOK-CLIENT-TOKEN>"/>
```

OAuth-Redirect-Host: `cct.com.ninebot.segway` (in `<data android:scheme="fbconnect"/>`).

## Google Sign-In OAuth Client-ID

```xml
<meta-data android:name="cn.ninebot.google.CLIENT_ID"
           android:value="<REDACTED-GOOGLE-OAUTH-CLIENT-ID>.apps.googleusercontent.com"/>
```

## Ninebot Passport (interne Auth-Schicht)

```xml
<meta-data android:name="cn.ninebot.ninebot.PASSPORT_CLIENT_ID"
           android:value="vehicle_app_overseas"/>
<meta-data android:name="cn.ninebot.ninebot.PASSPORT_CLIENT_KEY"
           android:value="<REDACTED-PASSPORT-CLIENT-KEY-UUID>"/>
```

UUID-Style Client-Key. Vermutlich für OAuth-Client-Credentials gegenüber `*-oms-gateway.ninebot.com`.

## Huawei HMS

```xml
<meta-data android:name="com.huawei.hms.client.appid" android:value="100187639"/>
```

## Oppo Color-OS Carlink Auth-Code

```xml
<meta-data android:name="com.coloros.ocs.car.AUTH_CODE"
           android:value="<REDACTED-OPPO-CARLINK-AUTH-CODE>"/>
<meta-data android:name="oplus.app.carlink.sdk.companyid" android:value="20029"/>
```

Base64-Blob, vermutlich Oppo-OAUTH-Token.

## Carlink-Verifikations-Fingerprint

```xml
<meta-data android:name="fingerPrint"
           android:value="com.ninebot.ninebot_BJp95IeCenJ3Mr5UmmZxgKgX+Pg9eIKmlTUcpNRaBzinmW03rq5WBtZ+oqa7pB1LTFXZQlrYcwkW+XSsBDPOb1o="/>
<meta-data android:name="targetSignature"
           android:value="56059A9193DD9FF053F86DF755A835B7"/>
<meta-data android:name="targetPackage"
           android:value="com.ninebot.ninebot"/>
```

`fingerPrint` ist ein Base64-Blob (88 Bytes nach Base64-Decode), `targetSignature` ist die MD5 der Signatur des Carlink-Targets. Wird vermutlich von einer der Carlink-Partner-Bibliotheken geprüft.

## RSA Public Keys (Embedded)

### `assets/config_rsa_public_key.pem` (RSA-1024)
```
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDbVh6/cqYaI6Lgs//Mf2zViMgh
f9jvSabDKnlu6L6Ti0owwDJUKlDxuHzNyxexDkdbseNb5pFQTqelxX9ugHwDurPG
D9CuXYzhwmnEj6ka7UuCK5UChT/jd9MktHZofeMv+XJ85bbArbnMWB/ZIWLjYUJl
P/NRiXQSdQpl+NCkPQIDAQAB
-----END PUBLIC KEY-----
```

### `assets/rsa_public_key.pem` (RSA-1024, anderer Modulus)
```
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC6maFY3dEhgav1147RW2gVWzCv
agkiRySnCDRSTM67YhHvLcrUSMnngxJl0A2liFLJydpn65E58oh0Phtu+t4Kkkfe
GIHsr931wRMRtkila4F/RF3U5pqSt42k/10U087QEhGMGvdOzF/5ziGXJod6ovBx
yk6pJlzNhxLTVJSzkQIDAQAB
-----END PUBLIC KEY-----
```

⚠ **RSA-1024 gilt heute als zu schwach** (NIST: deprecated seit 2014, nicht mehr zugelassen). Dass die App noch zwei separate RSA-1024-Schlüssel embedded, deutet darauf hin, dass:
- entweder Legacy-Endpoints (`carbit.cn` / `wxlinktest.sinaapp.com`) damit verschlüsselt werden,
- oder die App-Side eine Symmetric-Key-Wrap-Operation für Login-Payloads damit ausführt.

Beide Keys sollten gegen RSA-2048+ ausgetauscht werden.

## Einbettete BKS Truststores

| Datei | Inhalt |
|---|---|
| `assets/grs_sp.bks` | Huawei GRS Self-Signed Pinning (1.4 KB) |
| `assets/hmsincas.bks` | Huawei intermediate CAs (3.5 KB) |
| `assets/hmsrootcas.bks` | Huawei root CAs (33 KB) |

Diese sind für Cert-Pinning gegenüber Huawei-Cloud-Endpoints konfiguriert.

## NetEase NIS Konfiguration

`assets/nedig.properties` (~570 Bytes binär) und `assets/nedata.db` (35 MB) sind verschlüsselt – siehe `02-NETEASE-SHIELDING.md`. Der Decryption-Key ist im obfuskierten Native-Code (`libnesec.so`) zur Laufzeit verankert und nicht statisch extrahierbar.

## EasyConnect SDK License

`assets/ec-sdk_license` (192 Bytes) und `assets/ec-sdk_config-default` (2.2 KB) sind ein Carbit/EasyConnect-Lizenz-Token. Format unbekannt, vermutlich verschlüsselt + signiert.

## Carlink-Targets (signature-bound)

Der Manifest-Eintrag `targetSignature=56059A9193DD9FF053F86DF755A835B7` ist die MD5-Signatur (16 Bytes hex) der Original-Ninebot-China-App `com.ninebot.ninebot`. Das ist der **Anker für Carlink-Validierung**: nur eine App mit dieser exakten Signatur wird als „echte Ninebot-App" akzeptiert.

## Empfehlungen (für Ninebot)

1. `MAPBOX_TOKEN` (`sk.…`) **sofort rotieren** und auf `pk.…` mit URL-Restrictions umstellen
2. `HERE_KEY_SECRET` rotieren und an Server-Seite verlagern (Token-Exchange-Endpunkt)
3. Bugsnag-Key, Facebook-Client-Token sind tolerierbar (sind „pseudo-public") – aber sollten dennoch in `BuildConfig` statt `<meta-data>` für leichtere Rotation
4. RSA-1024 → RSA-2048 oder ECDSA-P-256
5. `Passport-Client-Key` (`b9f21b9e-…`) ist effektiv geheim, aber im APK extrahierbar – Auth-Modell sollte zusätzlich Device-Attestation (Play-Integrity) erzwingen
