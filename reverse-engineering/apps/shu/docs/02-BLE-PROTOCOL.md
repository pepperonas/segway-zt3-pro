# BLE-Protokoll-Analyse (aus SHU-Source)

Dieses Dokument extrahiert das gesamte BLE-Protokoll-Wissen aus der SHU-Decompile.

## 1. GATT-Service-Topologie

Konstanten aus `sh.cfw.utility.services.g.java`:

| UUID | Bezeichnung | Verwendung |
|---|---|---|
| `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | Nordic UART Service (NUS) | Haupt-Service, von ALLEN modernen Ninebot/Segway-Scootern angeboten |
| `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | NUS RX Char | App-zu-Scooter (Write WithoutResponse) |
| `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | NUS TX Char | Scooter-zu-App (Notify) |
| `00002902-0000-1000-8000-00805f9b34fb` | CCCD | Standard-Descriptor zum Aktivieren der Notifications |
| `0000fe95-0000-1000-8000-00805f9b34fb` | Xiaomi Mi Service | nur Xiaomi M365 / Pro (klassisch, ohne Crypto) |
| `00000010-0000-1000-8000-00805f9b34fb` | Xiaomi Mi Char A | nur Xiaomi M365 |
| `00000019-0000-1000-8000-00805f9b34fb` | Xiaomi Mi Char B | nur Xiaomi M365 |

**Für ZT3 Pro relevant**: ausschließlich die NUS-UUIDs. SHU unterscheidet zur Laufzeit anhand der `isXiaomi`-Flag im Beacon-Eintrag.

## 2. Advertising-/Beacon-Format

Aus `sh.cfw.utility.classes.k.java`:

Die App scannt nach Manufacturer-Specific-Data im BLE-Adv-Frame mit zwei Magic-Prefixes:

| Prefix (hex) | ASCII | Bedeutung |
|---|---|---|
| `FF 4E 42` | `_NB` | "Ninebot" – klassisch, 1 Byte Modell-ID |
| `FF 4E 43` | `_NC` | "Ninebot Crypto" – neuer, 2 Byte Modell-ID, signalisiert Crypto-Support |

`FF` ist das Manufacturer-Specific-AD-Type-Tag, `4E 42`/`4E 43` ("NB"/"NC") ist die Company-ID-Kennung.

Frame-Layout nach Prefix (6 Byte):

| Bytes | NB-Format (klassisch) | NC-Format (Crypto) |
|---|---|---|
| 0-1 | `[modelID, ?]` | `[modelID_hi, modelID_lo]` |
| 2 | `?` | feature flag (==2 → Crypto-Pairing aktiv) |
| 3 | feature flag | `?` |
| 4 | `?` | `0` (sentinel) |
| 5 | `0` (sentinel) | `?` |

Die Modell-ID wird gegen eine **JSON-Liste** (`bootstrap.zip` vom Backend `apps-content.cfw.sh`) abgeglichen, die für jedes bekannte Modell folgende Felder enthält:

```json
{
  "id": 0xNNNN,
  "model": "G30",
  "variant": "MAX",
  "isXiaomi": false,
  "humanReadable": "Ninebot KickScooter MAX G30",
  "imageDrawable": "g30",
  "requestMTU": true
}
```

Das `requestMTU`-Flag steuert, ob die App nach Connect ein `requestMtu(247)` durchführt (für Modelle, die größere MTU unterstützen).

Zusätzlich beim NB-/NC-Frame wird – falls Pairing erforderlich – nach einer **128-Bit-UUID** im Adv-Frame gesucht (`new UUID(7944349750023943059L, -2258021889238840674L)`). Das ist die Service-UUID, die für aktive Crypto-Geräte zusätzlich beworben wird.

## 3. Frame-Struktur (Application-Layer auf NUS RX/TX)

Aus `sh.cfw.utility.crypto.elliptic.h.java`, Methode `A(byte[] payload)`:

```
+---+---+----------+-------+-------+------------------+----+----+
|55 |AB | len-1    | seq_l | seq_h | encrypted_body   | cs1| cs2|
+---+---+----------+-------+-------+------------------+----+----+
 0   1   2          3       4       5..N               N+1  N+2
```

| Feld | Größe | Inhalt |
|---|---|---|
| Magic | 2 B | `0x55 0xAB` (fixed) |
| Length | 1 B | `payload.length - 1` (gemessen ab Byte 1) |
| Seq | 2 B LE | inkrementierter Counter, beginnt bei 0 |
| Body | N B | AES/CCM-verschlüsselt, siehe unten |
| Checksum | 2 B LE | Negation der Byte-Summe (16-Bit) über Bytes 2…N |

### Encrypted Body (AES/CCM)

```java
Cipher.getInstance("AES/CCM/NoPadding")
.init(ENCRYPT, new SecretKeySpec(sessionKey, "AES"),
      new GCMParameterSpec(24 /*tag bits*/, nonce))
```

- **Algorithmus**: AES-128 in CCM-Modus mit 24-Bit-MAC (3 Byte Tag, kurz – ungewöhnlich aber nicht unsicher)
- **Nonce**: `deviceToken (?) ++ 4×0x00 ++ seq_lo ++ seq_hi ++ ?? ++ ??` (12 Bytes total)
- **Plaintext**: `[origHeader[3]] ++ [origPayload] ++ [4 zero bytes]` (die 4 Nullen werden vor der Verschlüsselung angefügt)
- **Sessionkey** (`sessionKey`/`f12530j`): aus HKDF abgeleitet (siehe Pairing-Flow)

### Checksum

```java
short s = 0;
for (byte b : data) s += (b & 0xFF);
short cs = ~s;
return [(byte)(cs & 0xFF), (byte)(cs >> 8 & 0xFF)];
```

Klassische 16-Bit Inverted-Sum (wie m365 + 1's-complement).

## 4. Pairing-Flow (ECDH-basiert)

Die Hauptklasse: `sh.cfw.utility.crypto.elliptic.h` (extends `i`). Die Methoden `B`, `C`, `D`, `F`, `H`, `J` sind Pairing-Coroutines (decompile-incomplete).

### Schritt 1 – Session-Init (App-Seite)

```java
// d.java:154
KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDH");
kpg.initialize(ECNamedCurveTable.getParameterSpec("secp256r1"));
KeyPair clientKeys = kpg.generateKeyPair();
```

Eindeutige Kurve: **secp256r1 = NIST P-256**.

### Schritt 2 – Pubkey-Encoding für BLE

```java
// d.java:139
byte[] pub = ((ECPublicKey) keyPair.getPublic()).getQ().getEncoded(false);
// pub[0] == 0x04 (uncompressed marker)
return Arrays.copyOfRange(pub, 1, pub.length); // strip the 0x04 → 64 Bytes
```

Pubkey wird als rohe 64 Bytes (X || Y, big-endian) übertragen, ohne den 0x04-Prefix. Auf der Empfangsseite (`d.i()`) wird der Prefix wieder vorgehängt.

### Schritt 3 – Init-Header (App→Scooter)

```java
// d.java:132
byte[] header = [0x00] ++ "blt.4.159".getBytes() ++ random_lowercase_alnum(10);
```

→ `[00 62 6c 74 2e 34 2e 31 35 39 ...10 random chars]` – das ist ein typisches Ninebot-2nd-gen-Pairing-Hello.

### Schritt 4 – ECDH

```java
// d.java:108
KeyAgreement ka = KeyAgreement.getInstance("ECDH");
ka.init(clientKeys.getPrivate());
ka.doPhase(scooterPubKey, true);
byte[] sharedSecret = ka.generateSecret(); // 32 Bytes
```

### Schritt 5 – Key-Derivation (HKDF)

```java
// d.java:183 – HKDF-Expand-and-Extract mit SHA-256
HKDFParameters params = new HKDFParameters(sharedSecret, salt, info.getBytes("UTF-8"));
HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
hkdf.init(params);
byte[] derivedKey = new byte[length]; // 32 oder 64 Bytes
hkdf.generateBytes(derivedKey, 0, length);
```

Aus dem Shared Secret werden 64 Byte abgeleitet → in zwei AES-Keys aufgeteilt:
- `sessionKey` (App→Scooter)
- `responseKey` (Scooter→App)

(Daneben existieren `deviceToken`, `beaconKey` etc. die nach erfolgreichem Pairing dauerhaft gespeichert werden – siehe Schritt 6.)

### Schritt 6 – Persistierung

`EllipticPreferences.d(deviceInfo, deviceToken, beaconKey)` schreibt drei Byte-Arrays in die `SharedPreferences` unter dem Schlüssel `ellipticConfiguration` als JSON-Array (gson-serialisiert), eindeutig pro Scooter-MAC. Diese drei Werte werden bei künftigen Verbindungen wiederverwendet, sodass der ECDH-Handshake **nur einmal pro App-Install** notwendig ist.

```json
{
  "ssid":        "AA:BB:CC:DD:EE:FF",
  "deviceInfo":  [0x..., 0x..., ...],
  "deviceToken": [0x..., 0x..., ...],
  "beaconKey":   [0x..., 0x..., ...]
}
```

`beaconKey` dient zur Entschlüsselung des Beacon-Frames bei BLE-Adv (für Live-Geschwindigkeit/Status-Anzeige im Scanner-Bildschirm, ohne Connect).

### HMAC

`d.m(key, data)`:

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(key, "HmacSHA256"));
return mac.doFinal(data);
```

Wird zur Bestätigung der Pairing-Schritte verwendet – das schützt vor MITM, sofern der erste Pairing-Vorgang in einer trusted Umgebung stattfindet (Trust-On-First-Use, TOFU).

## 5. Frame-Sequenz beim Connect

Rekonstruktion aus `h.B/C/D/F/H` (incomplete decompile, basiert auf Variablen-Namen + bekanntem Ninebot-2nd-Gen-Protokoll):

1. **GATT Connect** zum Scooter (NUS Service-Discovery)
2. **CCCD** auf TX-Char (`6e400003`) schreiben → Notifications enable
3. App schreibt **Init-Header** (Schritt 3 oben) auf RX-Char (`6e400002`)
4. Scooter antwortet mit eigenem ECDH-Pubkey + Random-Challenge auf TX
5. App schickt eigenen Pubkey + HMAC-Bestätigung
6. Scooter antwortet mit `deviceInfo + deviceToken + beaconKey`, verschlüsselt mit dem geleiteten Schlüssel
7. App entschlüsselt, persistiert in `SharedPreferences`
8. Ab jetzt ist die Session aktiv – Folge-Frames nutzen `A(payload)` (siehe oben) zum Verschlüsseln

## 6. Bekannte Dialekte

In SHU sieht man Indizien für **drei Pairing-Varianten** (über den `f12533m` und `f12535o` Counter sowie 11-elementige Liste `f12534n`):

- **Klassisch (Xiaomi M365)** – kein Crypto, einfache Frames mit `0x55 0xAA` (nicht `0xAB`!)
- **Ninebot 1st-Gen** – KeyExchange mit fixem Default-Key
- **Ninebot 2nd-Gen** – ECDH+AES-CCM (oben beschrieben), in der App `elliptic` genannt

ZT3 Pro fällt mit hoher Wahrscheinlichkeit in die 2nd-Gen-Klasse.

## 7. Was das für eigene Tools heißt

Für ein eigenes Pairing-Tool (z. B. Python + `bleak`) braucht man:

```python
# Pseudocode
import bleak
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes, hmac
from cryptography.hazmat.primitives.ciphers.aead import AESCCM

NUS_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
NUS_RX      = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  # write
NUS_TX      = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  # notify

# 1. ECDH on P-256
priv = ec.generate_private_key(ec.SECP256R1())
pub_raw = priv.public_key().public_bytes(...)[1:]  # strip 0x04, 64 bytes

# 2. Send init-frame: 0x00 + b"blt.4.159" + 10 random lowercase alphanum
# 3. Receive scooter pub (64 bytes, no prefix) + token blob
# 4. Compute shared secret via ECDH
# 5. HKDF-SHA256(shared, salt=…, info=…) → 64 byte → split into two AES keys
# 6. Wrap further frames in: 0x55 0xAB | len-1 | seq[2] | AESCCM(key, nonce, plain) | inv-sum-checksum[2]
```

Die genauen `info`/`salt`-Strings für HKDF sowie die exakte Nonce-Konstruktion sind in den incomplete-decompile-Methoden `B`/`C` versteckt – hier hilft entweder eine bessere Decompile-Pass (jadx mit `--show-bad-code`) oder ein **HCI-Snoop-Capture** vom Pairing eines bekannten Scooters.

## 8. Quelldateien (für tiefere Recherche)

| Datei | Zweck |
|---|---|
| `sh.cfw.utility.crypto.elliptic.d.java` | **Crypto-Primitive** (ECDH, AES/CCM, HKDF, HMAC, Checksum) |
| `sh.cfw.utility.crypto.elliptic.h.java` | **Pairing-State-Machine** (incomplete decompile, 867 Zeilen) |
| `sh.cfw.utility.crypto.elliptic.i.java` | Basisklasse (Frame-Aufbau, 633 Zeilen) |
| `sh.cfw.utility.crypto.elliptic.b.java` | BLE-Wrapper über NUS-UUIDs, 341 Zeilen |
| `sh.cfw.utility.crypto.elliptic.e.java` | Public-API (`sendEncrypted`, `decryptResponse`) |
| `sh.cfw.utility.crypto.elliptic.EllipticPreferences.java` | Persistenz pro MAC |
| `sh.cfw.utility.crypto.elliptic.ConfigurationElliptic.java` | DTO |
| `sh.cfw.utility.classes.k.java` | **BeaconParser** (NB/NC-Frame-Decoder) |
| `sh.cfw.utility.services.g.java` | GATT-Callback (UUID-Konstanten hier!) |
| `sh.cfw.utility.services.SerialService.java` | Foreground-Service der BLE-Bridge |

## 9. Open-Source-Referenz

ScooterHacking ist eine **bekannte, dokumentierte Community**. Die Repos und Doku findet man unter:

- `https://utility.cfw.sh/`
- `https://cfw.sh/eula`
- `https://scooterhack.in/bugreport`
- GitHub-Org `scooterhacking` (öffentliche Repos für SHFW, Tools)

Wenn der ZT3 Pro in der Beacon-DB der App auftaucht (per `bootstrap.zip` Update vom `apps-content.cfw.sh`-Repo), ist alles Nötige für Pairing/Steuerung schon implementiert – kein eigener Reverse-Aufwand mehr nötig.
