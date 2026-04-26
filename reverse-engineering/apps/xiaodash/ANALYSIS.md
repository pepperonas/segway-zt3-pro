# 🛴 XiaoDash – Decompile-Analyse (verifiziert via Stringfog-Deobfuskation)

[![App: XiaoDash](https://img.shields.io/badge/App-XiaoDash-FF6B6B?logo=android&logoColor=white)](#)
[![Package](https://img.shields.io/badge/package-m365.xiaodash.scooter-blue)](#)
[![Target Hardware](https://img.shields.io/badge/target-17%20models%20incl.%20ZT3%20Pro-success)](#)
[![Protocols](https://img.shields.io/badge/protocols-Mi%20%2B%20Ninebot%20(dual)-orange)](#)
[![Stringfog: deobfuscated](https://img.shields.io/badge/stringfog-deobfuscated%20✓-success)](#)
[![Strings extracted](https://img.shields.io/badge/strings%20decoded-5848%20(+523%20BLE--module)-success)](#)
[![DEX Files](https://img.shields.io/badge/dex%20files-3%20(multidex)-blue)](#)
[![APK Size](https://img.shields.io/badge/apk%20size-54%20MB-lightgrey)](#)

> **Status-Update gegenüber der ersten Analyse**: Die ursprüngliche Einschätzung „nur Mi-Protokoll, ZT3 nicht unterstützt" war **falsch**. Nach **vollständiger Deobfuskation der Stringfog-Schicht** (5848 Strings + 523 BLE-Modul-Strings via Java-Reflection auf den Original-Decoder) ist klar: **XiaoDash unterstützt explizit den ZT3 Pro** (Modell-ID 256), spricht **beide Protokolle parallel** (Mi-Service `FE95` + Nordic-UART `6e400002/3`), und basiert auf der bekannten [py9b-Library](https://github.com/etransport/py9b) — daher die Multi-Protokoll-Fähigkeit.

---

## Inhaltsverzeichnis

1. [TL;DR](#tldr)
2. [Methodik der Deobfuskation](#methodik-der-deobfuskation)
3. [Was die App tut](#was-die-app-tut)
4. [Manifest & Komponenten](#manifest--komponenten)
5. [Stringfog-Schicht im Detail](#stringfog-schicht-im-detail)
6. [Multi-Protokoll-Architektur (Mi + Ninebot)](#multi-protokoll-architektur-mi--ninebot)
7. [Modell-Tabelle (17 Geräte inkl. ZT3 Pro)](#modell-tabelle-17-geräte-inkl-zt3-pro)
8. [BLE-UUIDs (verifiziert)](#ble-uuids-verifiziert)
9. [Frame-Magics + Auth-Flow](#frame-magics--auth-flow)
10. [Feature-Inventar (verifiziert)](#feature-inventar-verifiziert)
11. [Firmware-Flash: `full_f65.bin` + py9b-Flasher](#firmware-flash-full_f65bin--py9b-flasher)
12. [Backend & Cloud-Function](#backend--cloud-function)
13. [Code-Map (BLE-Hauptpfade)](#code-map-ble-hauptpfade)
14. [Bedeutung für ZT3-fxx — Empfehlungen](#bedeutung-für-zt3-fxx--empfehlungen)
15. [Reproduzieren der Analyse](#reproduzieren-der-analyse)
16. [Caveats](#caveats)
17. [Quellen](#quellen)

---

## TL;DR

| Aspekt | Wert |
|---|---|
| **App** | XiaoDash |
| **Package** | `m365.xiaodash.scooter` |
| **Target Hardware** | **17 Modelle** (Xiaomi M365-Familie + Segway/Ninebot inkl. ZT3 Pro, F2/F2-Plus/Pro, F65, G2/G3/G30, E, MEBIKE, KART_PRO etc.) |
| **Target Protokolle** | **Beide parallel**: Xiaomi/Mi via `0000fe95` Service + 16-bit Characteristics, Ninebot via `6e400002/6e400003` Nordic-UART |
| **Foundation-Library** | py9b (etransport/py9b) — bietet `XiaomiTransport` + `NinebotTransport`, eingebettet als komplettes Python-Skript in den Resourcen |
| **Frame-Magics** | `5aa5` (Ninebot 5A A5), `55aa` (Mi M365), `55ab000121` (Mi-Variant), `0xf5 packet` (Audio?) |
| **Auth-Stages** | "Stage 1/2/3/4 complete!" — vermutlich der mehrstufige FW-Unlock-Flow |
| **APK-Größe** | 54 MB |
| **DEX-Files** | 3 (multidex) |
| **Native Libraries (`lib/*.so`)** | **keine** als reguläre `.so` — JNI-Attestation via `bytesFromJNI()` aber Lib lazy-loaded aus Asset (vermutet) |
| **Obfuscation** | **Stringfog by megatronking** (verifiziert via Algorithmus-Analyse): 70 unique String-Tabellen über 70 Pseudo-`com.dalvik.*`-Pakete, Decoder unter `com.dalvik.b1.OO00000OOOOOOOO0000O.OOOOOOO0OOOOO0O00OO0(long, String[])` |
| **Stringfog-Decoder geknackt** | Ja — siehe `analysis-artifacts/StringfogMulti.java` |
| **Strings extrahiert** | 5848 unique vom Hauptpfad + 523 vom BLE-Modul-Pfad |
| **Cloud** | Firebase Auth + Realtime DB + Crashlytics, **Firebase Cloud Function** `https://us-central1-xiaodash-4e39e.cloudfunctions.net/cc?sid=`, AdMob, Google Maps, Facebook-SDK |
| **Backend-Endpoints** | `/cc?sid=` (purchase activation), `/f` (firmware), `/k` (keys), `/p` (purchase) |
| **Monetarisierung** | Play-Billing (3-Tier: Standard / Silver / Gold), AdMob-Rewarded-Ads, Donation-Pfad |
| **Firmware-Asset** | `assets/fw/full_f65.bin` (129 036 Byte, AES-encrypted, 16-Byte-Header `b'VelocidadAbsurda'` + 8-Byte-IV + Body) |
| **Hardware-Programmer** | ST-Link via `ru.zdevs.zflasherstm32.STLINK` (3rd-party Russisch-Library) |
| **Crypto** | AES-256-GCM für Settings-Backup, AES-CTR (vermutet) für FW-Verschlüsselung, ECDH (`ecdh_old`-String) für Pairing |
| **Analyse-Tools** | apktool 2.x, jadx 1.x, **dex2jar** (Cellar 2.4), **JVM-Reflection** (Hauptwerkzeug für Stringfog-Bypass), Frida 17.9.1 + Ghidra 12.0.4 verfügbar (für künftige Live-Analyse) |

---

## Methodik der Deobfuskation

Die Hauptschwierigkeit der App war die **Stringfog-by-megatronking** Obfuscation: alle Strings (UUIDs, URLs, Commands, Methodennamen) sind in 70 String-Tabellen über `com.dalvik.*`-Pseudo-Packages verstreut. Statt eines pure-Python-Re-Implementations des Decoders haben wir einen **JVM-Reflection-Ansatz** verwendet, der den **Original-Decoder byte-exakt** ausführt:

### Schritt 1 — APK → JAR

```bash
brew install dex2jar
d2j-dex2jar m365.xiaodash.scooter.apk -o /tmp/xiaodash.jar
```

Liefert `xiaodash.jar` (28 MB) mit allen Klassen als `.class`-Files, JVM-loadbar.

### Schritt 2 — Reflection-Harness

Ein kleines Java-Programm lädt das JAR via `URLClassLoader`, reflektiert in `com.dalvik.b1.OO00000OOOOOOOO0000O` und ruft direkt die statische Methode `OOOOOOO0OOOOO0O00OO0(long seed, String[] table)` auf. Die Methode ist deterministisch, d.h. derselbe Seed liefert immer denselben String.

```java
// analysis-artifacts/StringfogMulti.java (gekürzt)
URLClassLoader cl = new URLClassLoader(new URL[]{xiaodashJarUrl}, parentLoader);
Class<?> sf = Class.forName("com.dalvik.b1.OO00000OOOOOOOO0000O", true, cl);
Method decode = sf.getMethod("OOOOOOO0OOOOO0O00OO0", long.class, String[].class);

// Für jeden Call-Site: lade die zugehörige String[]-Tabelle via Reflection
Class<?> tc = Class.forName(tableClassName, true, cl);
String[] table = (String[]) tc.getField("OO00000OOOOOOOO0000O").get(null);

// Decode
String result = (String) decode.invoke(null, seed, table);
```

### Schritt 3 — Call-Site-Extraktion

Ein Python-Skript scant alle 5000+ Java-Files unter `decompiled/jadx/sources/` und extrahiert (seed, table_class)-Paare aus drei Patterns:

1. **Inline-Tabelle**: `OOOOOOO0OOOOO0O00OO0(SEED_L, com.dalvik.PKG.OO00000OOOOOOOO0000O.OO00000OOOOOOOO0000O)`
2. **strArr-bound**: vorher `String[] strArr = com.dalvik.PKG.OO00000OOOOOOOO0000O.OO00000OOOOOOOO0000O;`, dann `OOOOOOO0OOOOO0O00OO0(SEED_L, strArr)`
3. **1-arg form**: `.OO00000OOOOOOOO0000O(SEED_L)` (verwendet implizit die globale `b1`-Tabelle)

Resultat: **6106 unique (seed, table)-Tupel** aus dem ganzen Codebase.

### Schritt 4 — Batch-Decode

```bash
java -cp .:xiaodash.jar StringfogMulti < callsites.tsv > decoded.tsv
# Output-Format: seed<TAB>tableClass<TAB>cleartext
```

Resultat: **5848 unique cleartext-Strings** (von 6106 Tupeln, da viele Seeds in mehreren Files referenziert werden). Erfolgsrate ~99,75% (15 Errors durch korrupte Seed-Patterns in Edge-Cases).

→ Alle weiteren Befunde in dieser Analyse basieren auf dieser **byte-exakten Deobfuskation**, nicht auf Code-Reading durch Stringfog hindurch.

---

## Was die App tut

XiaoDash ist eine **kommerzielle Custom-Tuning-Companion-App** für die kombinierten Mi-Scooter- und Segway/Ninebot-Familien. Funktional vergleichbar mit den Community-Tools `m365dashboard`, `m365-tools` und der ScooterHacking Utility, aber mit:

- **Subscription-Monetarisierung** (3 Tiers + AdMob + Donation)
- **Anti-Tampering** via JNI-Native-Attestation
- **Multi-Hardware-Support** (17 Geräte mit Auto-Detection per BLE-Beacon)
- **Cloud-gestützter FW-Distribution** über Firebase Cloud Function

Kernfunktionen (vollständig in [Feature-Inventar](#feature-inventar-verifiziert)):

1. **Live-Telemetrie** mit konfigurierbaren Tiles
2. **Custom-Tuning** per CAN/Ninebot-Register-Writes (Speed, KERS, Brake, Sound)
3. **Custom-Firmware-Flashing** via BLE-OTA + ST-Link-SWD
4. **GPS-Trip-Recording** mit Foreground-Service
5. **3 konfigurierbare Action-Buttons**
6. **BMS-Hacks** + Battery-Cell-Monitoring
7. **Region-Change** (DE / EU / US / WW)
8. **PIN-Lock**

---

## Manifest & Komponenten

(Aus der ersten Analyse — unverändert)

### Permissions

```
ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION
BLUETOOTH (≤30), BLUETOOTH_ADMIN (≤30)
BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT, BLUETOOTH_SCAN
WRITE_EXTERNAL_STORAGE, ACCESS_NETWORK_STATE, INTERNET
CAMERA (optional), WAKE_LOCK, FOREGROUND_SERVICE
com.android.vending.BILLING, com.google.android.c2dm.permission.RECEIVE
ACCESS_ADSERVICES_AD_ID / ATTRIBUTION / TOPICS
```

### Activities / Services

- **Launcher**: `m365.xiaodash.scooter.ui.activity.MainActivity`
- **ContentProvider**: `LogsContentProvider` (exported — exposes BLE-Logs an externe Apps)
- **Auth-Flow**: 11+ Firebase-UI-Auth Activities (Email/Phone/IDP)
- **Foreground-Service**: AndroidX WorkManager (für GPS-Recording + BLE-Connection-Hold)

---

## Stringfog-Schicht im Detail

### Algorithmus

Aus dem Smali (`smali_classes3/com/dalvik/b1/OO00000OOOOOOOO0000O.smali`):

```
Method 1:  O0OOOOOO00OOOOO000O0(long state) → long
  // 16-bit-State-Mixer mit add/xor/rotate auf 16-bit-Half-Words
  // Liefert ein gepacktes 48-bit Update (3 × short)

Method 2:  OOOOOOO0OOOOO0O00OO0(long seed, String[] table) → String
  // SplitMix64-style Hash-Init:
  //   v0 = seed & 0xFFFFFFFF
  //   v0 ^= v0 >>> 33
  //   v0 *= 0x62a9d9ed799705f5L
  //   v0 ^= v0 >>> 28
  //   v0 *= 0xCB24D0A5C88C35B3L  (= -0x34db2f5a3773ca4dL)
  //   v0 >>>= 32
  //
  // → ruft Mixer 3x auf → derived index/length/keystream
  // → liest erste Char aus table[idx/8191].charAt(idx%8191)
  //   (8191 = 0x1FFF = max chars pro Tabellen-Entry)
  // → entschlüsselt {length} Chars per Mixer-XOR-Stream
  //
  // Resultat: deterministischer cleartext-String

Method 3:  OO00000OOOOOOOO0000O(long seed) → String
  // Wrapper, ruft Methode 2 mit Global-Tabelle auf
```

### Tabellen-Layout

- **Globale Tabelle** in `com.dalvik.b1.OO00000OOOOOOOO0000O.OO00000OOOOOOOO0000O` (size: `0x52a` = **1322 entries**, jeder Entry bis zu 8191 Chars)
- **Per-Package-Tabellen**: 70 weitere Stringfog-Tabellen über andere `com.dalvik.*`-Pakete, mit jeweils eigenen Strings

### Mengengerüst nach Decode

| Quelle | Unique Strings |
|---|---|
| Globale `b1`-Tabelle | ~3330 Aufrufe → 1322 distinct strings |
| `com.dalvik.OO0OOOOO000OOOOOO000.OO00000OOOOOOOO0000O.OO00000OOOOOOOO0000O` (Sekundär-Tabelle) | ~2384 Aufrufe |
| 1-arg `OO00000OOOOOOOO0000O(seed)` (impliziert `b1`) | ~392 Aufrufe |
| **Gesamt unique cleartexts** | **5848** |

→ Alle Artefakte in `analysis-artifacts/`:
- `StringfogMulti.java` — Decoder-Harness
- `extract_callsites.py` — Call-Site-Scanner
- `seed-decoded-short.tsv` — alle <500-Char-Strings (5563 entries)
- `constants.txt` — UPPERCASE-Konstanten (186)
- `uuids.txt` — alle UUIDs (10)
- `urls.txt` — Backend-URLs (4)

---

## Multi-Protokoll-Architektur (Mi + Ninebot)

### Verifiziertes BLE-Service-Set

| UUID | Service | Verwendung |
|---|---|---|
| `0000fe95-0000-1000-8000-00805f9b34fb` | **Xiaomi Mi-Service** (16-bit `0xFE95`) | M365, M365 Pro, Mi 1S, Mi 3, etc. |
| `00000001-...` bis `00000019-...` | Mi-Service-Characteristics (16-bit IDs `0x0001`-`0x0019`) | Read/Write/Notify pro Sub-Funktion |
| `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | **Nordic UART RX** (Phone → Scooter Write) | ZT3, F2-Familie, F65, G2/G3 |
| `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | **Nordic UART TX** (Scooter → Phone Notify) | ZT3, F2-Familie, F65, G2/G3 |

### Beacon-Manufacturer-IDs (aus `com/dalvik/j0/O00O00O00O000OO0O0OO.java`)

| ID (decimal) | Hex | ASCII | Bedeutung |
|---|---|---|---|
| `16974` | `0x424E` | `"NB"` | Ninebot Beacon |
| `17230` | `0x434E` | `"NC"` | Ninebot Charging Beacon (Variante) |

→ Identische Beacon-Pattern wie in der ScooterHacking-Utility (siehe `../shu/ANALYSIS.md`).

### Protokoll-Auswahl

Der Auswahl-Code befindet sich in `com/dalvik/j0/O00O00O00O000OO0O0OO.java` (BLE-Scan-Result-Container). Auswahl-Logik (rekonstruiert):

```java
SparseArray<byte[]> manuData = scanResult.getManufacturerSpecificData();
if (manuData.get(16974) != null) {        // 0x424E = "NB" → Ninebot
    pair = new Pair(16974, manuData.get(16974));
} else if (manuData.get(17230) != null) {  // 0x434E = "NC" → Ninebot Charging
    pair = new Pair(17230, manuData.get(17230));
} else {
    pair = new Pair(null, null);            // → fallback Xiaomi (Mi-Service-UUID)
}
```

→ XiaoDash unterscheidet Ninebot-Geräte (über NB/NC Manufacturer-Data) von Xiaomi-Geräten (Mi-Service `FE95` als Service-UUID-Match) **schon zur Scan-Zeit**.

### Foundation: py9b

In den decodierten Strings findet sich ein **vollständiges Python-Flash-Skript** (~150 Zeilen, decoded String mit Seed `-1000724979295693`), basierend auf [etransport/py9b](https://github.com/etransport/py9b):

```python
# Ausschnitt (decoded)
from py9b.link.base import LinkOpenException, LinkTimeoutException
from py9b.transport.base import BaseTransport as BT
from py9b.transport.xiaomi import XiaomiTransport
from py9b.transport.ninebot import NinebotTransport
# ...
protocols = {'xiaomi' : XiaomiTransport, 'ninebot' : NinebotTransport}
parser.add_argument('-p', '--protocol', choices=protocols, default='xiaomi')

# FW-Header-Magic:
header = fwBytes[0:0x10]
if header != b'VelocidadAbsurda':
    exit('Wrong Header')
iv = fwBytes[0x10:0x18]              # 8-byte IV
fwFile = BytesIO(fwBytes[0x18:])     # encrypted body

# Lock command:
tran.execute(WriteRegs(BT.ESC, 0x70, '<H', 0x0001))   # ESC reg 0x70 = lock

# IAP commands:
tran.execute(StartUpdateSecure(dev, fwSize, iv))      # cmd=0x07, sends size+iv
tran.execute(WriteUpdate(dev, page, data))             # cmd=0x08
tran.execute(FinishUpdate(dev, chk ^ 0xFFFFFFFF))     # cmd=0x09 (final XOR-CRC)
tran.execute(RebootUpdate(dev))                        # cmd=0x0A
```

→ XiaoDash hat py9b's gesamte Protokoll-Logik **direkt portiert**. Die Java-Implementierung (in `com.dalvik.m0.*`) ist eine 1:1-Übersetzung der py9b-Klassen.

---

## Modell-Tabelle (17 Geräte inkl. ZT3 Pro)

Aus `m365/xiaodash/scooter/domain/model/ScooterM.java` (decoded), die `modelTable: Map<Int, String>` liefert:

| `scooterMid` | Modell-Name | Protokoll |
|---|---|---|
| `32` | Xiaomi M365 | Mi |
| `33` | Segway/Ninebot E | Ninebot |
| `34` | Xiaomi Pro | Mi |
| `36` | Segway/Ninebot G30 | Ninebot |
| `37` | Xiaomi Pro | Mi (Variante) |
| `39` | Segway/Ninebot E | Ninebot (Variante) |
| `40` | Xiaomi Pro2 | Mi |
| `41` | Xiaomi 1S | Mi |
| `43` | Xiaomi 1S | Mi (Variante) |
| `45` | **Segway/Ninebot F65** ← **`assets/fw/full_f65.bin` ist FÜR DIESES MODELL** | Ninebot |
| `46` | Xiaomi 3 | Mi |
| `127` | Segway/Ninebot F2 | Ninebot |
| `128` | Segway/Ninebot F2 Plus | Ninebot |
| `129` | Segway/Ninebot F2 Pro | Ninebot |
| `131` | Segway/Ninebot G2 | Ninebot |
| **`256`** | **Segway/Ninebot ZT3 Pro** | **Ninebot** ✓ |
| `258` | Segway/Ninebot G3 | Ninebot |

→ **Korrektur zur ersten Analyse**: Der mitgelieferte FW-Blob `full_f65.bin` ist **NICHT** für M365 (=mid 32), sondern für **F65 (=mid 45)**. ZT3 Pro (=mid 256) erhält seine FW separat aus dem Cloud-Function-Backend.

### Zusätzlich erkannte Modell-Strings

Aus `constants.txt`: weitere Plattform-Strings die nicht direkt in `modelTable` stehen (vermutlich für legacy-Detection oder Region-Varianten):

```
M365_1S, M365_1S_DE, M365PRO, M365_PRO2
MI_SCOOTER_1S, MI_SCOOTER_1S_DE, MI_SCOOTER_3, MI_SCOOTER_LITE,
MI_SCOOTER_PRO, MI_SCOOTER_PRO2
SCOOTER, SCOOTER_AIR, SCOOTER_F2, SCOOTER_G2, SCOOTER_G30, SCOOTER_G65,
SCOOTER2, SCOOTER2_P
KART_PRO, KART_PRO_LAMBO       # Segway Go-Karts
MEBIKE, MARK2, MARK3            # E-Bikes
MINI_KIDS, MINI_KIDS_MI, MINI_MAX
STEELDUST, WILD_STEEL_DUST, MANON, RIDE_AUTO
```

### Firmware-IDs (sample aus `constants.txt`)

```
N1OEA1401A0001    # Ninebot Gen 1, Modell-Code OE, Date 14W01
N2CSH2021C0001    # Ninebot Gen 2, Modell CS, Date 20W21
N2CTH1938C0001    # Ninebot Gen 2, Modell CT, Date 19W38
N3GEA1601C0001
N3MEA1501A0001
N3OLA1801A0001
N4MAA1701A0001, N4MEA1701A0001, N4MZA1701A0001
N5GEA1601C0001
N5MTA2001A0001
S1CAA1936Q0001, S1CCA1936Q0001, S1CEA1936Q0001, S1CNA1936Q0001
                  # Segway Gen 1, Modell Cx, Date 19W36, Region A/C/E/N
M1GCA1601C0001    # Mi Gen 1, Modell GC, Date 16W01
```

Format-Konvention: `{Hersteller}{Gen}{Modell}{Date YYWW}{Region}{Build}`. Diese IDs werden vermutlich für FW-Validation gegen das Cloud-Backend genutzt.

---

## BLE-UUIDs (verifiziert)

Aus `analysis-artifacts/uuids.txt`:

```
00000001-0000-1000-8000-00805f9b34fb    # Mi Char 0x0001 (UPNP_AVR-Style 16-bit)
00000002-0000-1000-8000-00805f9b34fb    # Mi Char 0x0002
00000004-0000-1000-8000-00805f9b34fb    # Mi Char 0x0004
00000010-0000-1000-8000-00805f9b34fb    # Mi Char 0x0010
00000013-0000-1000-8000-00805f9b34fb    # Mi Char 0x0013
00000014-0000-1000-8000-00805f9b34fb    # Mi Char 0x0014
00000019-0000-1000-8000-00805f9b34fb    # Mi Char 0x0019

0000fe95-0000-1000-8000-00805f9b34fb    # Mi-Service (16-bit 0xFE95) — Xiaomi Allianz-Range

6e400002-b5a3-f393-e0a9-e50e24dcca9e    # Nordic UART RX (Phone-Write)
6e400003-b5a3-f393-e0a9-e50e24dcca9e    # Nordic UART TX (Scooter-Notify)
```

→ **Wichtig**: Im Decompile fehlt `6e400001-...` (Nordic UART Service) und `00002902-...` (CCCD) als Stringfog-encoded Werte — die App verwendet diese vermutlich über Standard-BLE-Konstanten (z.B. `BluetoothGattDescriptor.CLIENT_CHARACTERISTIC_CONFIG_UUID`).

---

## Frame-Magics + Auth-Flow

### Frame-Magics (verifiziert)

Aus den decoded BLE-Modul-Strings (`com.dalvik.m0.*`):

| Bytes | Verwendung |
|---|---|
| `5aa5` | **Ninebot 5A A5** (ZT3, F2/F65/G2/G3) — passend zu `core/ble/FrameCodecClassic` in `zt3-fxx` |
| `55aa` | **Mi M365 Standard** Frame-Header |
| `55ab000121` | **Mi-Frame mit erweitertem Header** (ID 0x21) |
| `5aa607` | Ninebot-Variant (vermutlich Audio/IAP-spezifisch) |
| `5AAC` | weitere Variant |
| `0xf5 packet` | Audio-Payload (vermutet) |

### Auth-Stages (aus globaler `b1`-Tabelle)

```
"Stage 1 complete!"
"Stage 2 Complete"
"Stage 3 completed!" / "Stage 3 completed"
"Stage 4 complete!"
"Error in Stage 3"
"Error occurred in Stage 2"
"stage3Time"
"Stage 2\nThe scooter's functionality is getting expanded.\nPlease stay next to the scooter!"
"Stage 3\nFirmware Downgrade it progress!\nReverting back to stock\nPlease wait..."
```

→ 4-stage FW-Unlock/Activation-Flow. Stage 2 ist „Functionality Expansion", Stage 3 inkludiert Downgrade-Pfad.

### Auth-Strings (aus BLE-Modul)

```
"Auth Success" / "Auth Successful"
"Auth Error keys are false?"
"AUTH_IN: %s" / "auth: %s"
"authIn"
"Bledevice is not set"
"Connection received %s"
"Disconnect" / "Disconnecting"
"Connectionstate:%s"
"Ble power state changed:%s %s"
"ecdh_old"               ← ECDH Pairing-Variante
```

→ ECDH-basiertes Pairing. „ecdh_old" suggeriert dass es auch eine `ecdh_new` gibt (aber im Decompile nicht direkt gefunden — möglicherweise eine Konstante in der `OO0OOOOO`-Tabelle).

### Konstanten-Spec (Auswahl aus `constants.txt`)

```
WRITE                    # cmd=0x02 (Ninebot WRITE)
WRITE_NR                 # cmd=0x03 (Ninebot WRITE no-response)
RESPONSE_READING         # state-machine
RESPONSE_WRITING         # state-machine
LOCK / LOCK_CONFIRM      # Lock-Sequence (2-stufig)
LOW_SPEED, SPEED_PER_10M, SPEED_100M_H, SPEED_UNIT,
SPEED_LIMIT_ALL, SPEED_LIMIT_ECO   # Speed-Limit-Register-Familie
KERS_MODE, CRUISE_CONTROL, DRIVE_ALARM, DRIFT_ASSIST,
STEERING_ASSIST, START_SPEED_ADJUST
TAILLIGHT_ALWAYSON, BACK_LED, ATMOSTHERE_LIGHT       # Lichter
ACTIVATION, AUTHORIZATION, ENCRYPT, PASSWORD,
PAIR_CODE, REG_SUCCESS, MULTI_POWER_TYPE
SET_BT_NAME              # BT-Name-Change
CHARGING_OPTIMIZE_INFO, CHANGE_REGION, AUTOMATION
BMS_NBSEC                # Ninebot Secure BMS
BLACK_BOX                # Crash-Log lesen
EXT_GETINFO, EXT_GETPROFILE, EXT_LOADREGS, EXT_READ, EXT_SAVEREGS
                          # External-Profile-Management
TRACK_MODE, NEW_DASHBOARD, CFW_TOOLKIT
SECOND_BATTERY            # Extended-Battery
TAG_TEACHING, TEACHING    # Lehr-Modus (Custom-FW-Spezialität)
M1M1M1D3DMD3DM            # vermutete Default-Pattern
024d0000                  # vermutete Mi Pro2 Header-Bytes (M=0x4D = 77)
```

---

## Feature-Inventar (verifiziert)

(Aus der ersten Analyse — nun durch `constants.txt` + decoded strings bestätigt.)

| # | Feature | Beleg-Konstante / String |
|---|---|---|
| 1 | **Connection / Pairing** | `connect`, `disconnect`, `Connectionstate:%s`, `Ble power state changed`, `auto_connect_next_time` |
| 2 | **3 Drive Modes** | `driveModeEco`, `driveModeNormal`, `driveModeSport`, `freeride_normal/sport`, `BootDriveMode*` |
| 3 | **Region Change** | `g30_region_de/us/ww`, `CHANGE_REGION`, `changeRegion`-method |
| 4 | **Custom Lock + PIN** | `custom_hidden_lock/unlock`, `LOCK`, `LOCK_CONFIRM`, `pin_was_not_correct_dialog_prompt` |
| 5 | **Tuning (Speed/Power/Brake/KERS)** | `edx_multiplication_factor`, `KERS_MODE`, `START_SPEED_ADJUST`, `dpcLinear/Quadratic`, `powerAlgo`, `reverseDrive`, `disableChargeMode` |
| 6 | **Sound-Customization** | `soundDoubleShort/Long/Short/None/VeryLong`, `soundVMelody1/2`, `device_setting_menu_sound` |
| 7 | **Lights** | `BACK_LED`, `ATMOSTHERE_LIGHT`, `TAILLIGHT_ALWAYSON`, `blinkOnBrake/Autobrake`, `switchToggleHeadlight` |
| 8 | **Telemetry-Tiles** (configurable) | `IdleBattery/BatteryTemp/Current/ESCTemp/MotorTemp/RemainingKm/SoC/Speed/Voltage`, `selected_parameters_title` |
| 9 | **Battery-Detail / BMS-Hacks** | `cell_voltage_std`, `groupDescriptionBmsEmulation`, `bmsBaudrate76800`, `BMS_NBSEC`, `set_custom_battery_range`, `SECOND_BATTERY` |
| 10 | **Trip-Recording + GPS** | `gps_logging_settings`, `service_gps_recording_ongoing`, `use_gps_when_recording_trips` |
| 11 | **Multi-Vehicle / Garage** | `scooterProfile1/2`, `vehicle_name`, `alertidialog_name_nickname` |
| 12 | **UI-Customization** | `speedometer_style`, `cardview_customisations_title`, `theme_selection_title`, HSL-Picker (`title_hue/saturation/lightness/key`) |
| 13 | **Status-Notification** | `switch_status_notification`, `set_notification_update_rate`, `update_rate_in_seconds` |
| 14 | **Custom-Buttons (3 Action-Slots)** | `button_1/2/3`, `ActionParameter`, `ActionParameterButton`, `ActionParameterNew`, `ActionParameterNewWithSpeed`, `switchToggleHeadlight/Speedboost/CruiseControl`, `switchProfile`, `switchDriveMode`, `changeCustomButton`-method |
| 15 | **Cruise Control** | `CRUISE_CONTROL`, `Cruise Control Enabled` |
| 16 | **Premium / Subscription** | `silver/gold/standard_premium_version_title`, `dialog_rewarded_ad_button_watch_ad`, `alertdialog_title_donate` |
| 17 | **ST-Link Hardware-Programmer** | `STM32`, `device_setting_menu_stlink`, `import ru.zdevs.zflasherstm32.STLINK` |
| 18 | **Logs & Diagnostics** | `BLACK_BOX`, `LogsContentProvider`, `delete_logs`, `see_all_logs` |
| 19 | **Anti-Tampering** | `system_is_tempered`, `bytesFromJNI()`, `Auth Error keys are false?` |
| 20 | **Audio-Upload** | `0xf5 packet` (vermutet — analog ZT3 Audio-Cmd `0x76/0x77/0x78`) |
| 21 | **Track-Mode** | `TRACK_MODE`, `KART_SPEED_10M`, `KART_PRO`, `KART_PRO_LAMBO` (Go-Kart-Spezial-Mode) |
| 22 | **Lichteffekte (Custom-FW)** | `changeLightColorandMode`-method, `DRIVE_ALARM`, `STEERING_ASSIST` |
| 23 | **Acceleration-Map (G3)** | `changAccelerationMapG3`-method (für Custom-Beschleunigungs-Kurven) |

---

## Firmware-Flash: `full_f65.bin` + py9b-Flasher

### Asset-Struktur (verifiziert via decoded Python-Skript)

```
Offset  Size  Bezeichnung
0x00    16    Header magic = b'VelocidadAbsurda'
0x10    8     IV (Initialization Vector) — wird an StartUpdateSecure-cmd geschickt
0x18    *     Encrypted firmware body (AES-CTR vermutet)
```

- Asset-Größe: **129 036 Bytes**
- Body-Size = 129 036 - 0x18 = **129 012 Bytes** (vermutete eigentliche FW-Größe nach Decryption)
- Page-Size beim Schreiben: `0x80` = 128 Bytes/Page
- Pages total: `129012 / 128 = 1008.7` → also 1009 Pages (letzte teilweise gefüllt mit `\x00`-Padding)

### IAP-Befehlssequenz (aus py9b-Flasher)

```
1. WriteRegs(BT.ESC, 0x70, '<H', 0x0001)         # Lock ESC für Flash
2. StartUpdateSecure(dev, fwSize, iv)             # cmd=0x07, payload = size_LE32 + iv
3. (5 Sekunden Wartezeit)
4. StartUpdateSecure(dev, fwSize, iv)             # nochmal (Stabilitäts-Pattern)
5. for page in pages:
       WriteUpdate(dev, page, data)               # cmd=0x08, payload = page_idx + 128 byte chunk
6. FinishUpdate(dev, chk ^ 0xFFFFFFFF)            # cmd=0x09, payload = inverted CRC32
7. RebootUpdate(dev)                              # cmd=0x0A
```

### Update-Error-Codes (aus py9b-Flasher)

```python
UpdateErrorCodes = {
    0: 'OK',
    1: 'Size error',
    2: 'Erase error',
    3: 'Write error',
    4: 'Not locked',
    5: 'Sequence error',
    6: 'Busy',
    7: 'Data format error',
    8: 'CRC mismatch',
    9: 'Other error'
}
```

### Geräte-Targets (`BT.X`-Enum)

| Konstante | Bedeutung |
|---|---|
| `BT.BLE` | BLE-Modul-Firmware |
| `BT.ESC` | ESC-Controller (Hauptfirmware — z.B. `full_f65.bin`) |
| `BT.BMS` | BMS-Firmware |
| `BT.EXTBMS` | External BMS (nur für Ninebot — `Only Ninebot supports External BMS!`) |

### BLE-OTA vs ST-Link

- **BLE-OTA** (Default): obiger Flow über `XiaomiTransport` oder `NinebotTransport`
- **ST-Link**: für Bricked-Recovery oder restricted-FW-Boards. Anweisungen kommen aus `StlinkRemoteSettings` Backend, physische Implementation läuft via `ru.zdevs.zflasherstm32.STLINK`-Library
- **Stages**: Mehrstufig — siehe [Auth-Stages](#auth-stages-aus-globaler-b1-tabelle)

---

## Backend & Cloud-Function

### Verifizierte Endpoints (aus `analysis-artifacts/urls.txt`)

```
https://us-central1-xiaodash-4e39e.cloudfunctions.net/cc?sid=        ← purchase / activation check
https://play.google.com/store/apps/details?id=app.peretti.m365tools  ← Schwester-App (m365tools by Adriandp/Peretti)
market://details?id=app.peretti.m365tools                            ← Direct Play-Store Open
```

### Endpoint-Pfade (aus dem ZT3-FW-Handler `j0/O0000O0O0OO0O0000000.java`)

```
/f endpoint        # firmware download
/k endpoint        # encryption keys
/p endpoint        # purchase / billing
```

→ Alle drei sind unter der Firebase Cloud Function gehostet (`...cloudfunctions.net/cc?sid={device_id}`).

### Weitere Cloud-Integration

- **Firebase Auth** (Email, Phone, IDP via firebase-ui-auth)
- **Firebase Realtime DB** (`ScooterRemoteSetting`, `StlinkRemoteSettings` werden hier gepullt)
- **Firebase Crashlytics** (`firebase_crashlytics_collection_enabled=true`)
- **Google Maps API** (`AIzaSyCNfq4EsycoYA5sCCckeicFU-gsoXR9Cmo`)
- **AdMob** (App-ID `ca-app-pub-5969370273098532~2027314184`)
- **Facebook SDK** für Login-IDP

---

## Code-Map (BLE-Hauptpfade)

| Java-Datei | Zeilen | Verifizierte Rolle |
|---|---|---|
| `m365.xiaodash.scooter.Application` | ~150 | App-Init, **JNI-Attestation** via `bytesFromJNI()` (signature-check) |
| `m365.xiaodash.scooter.ui.activity.MainActivity` | 506 | Single-Activity-Container |
| `m365.xiaodash.scooter.ui.fragment.SelectDeviceFragment` | 1176 | BLE-Scan, NB/NC Manufacturer-Detection, Connect-Flow |
| `m365.xiaodash.scooter.ui.fragment.ScooterStateFragment` | 1520 | Live-Telemetrie-Dashboard, konfigurierbare Tiles |
| `m365.xiaodash.scooter.ui.fragment.ButtonsFragment` | 433 | 3 konfigurierbare Action-Buttons + ActionParameter-Mapping |
| `m365.xiaodash.scooter.ui.fragment.FragmentViewPager` | 649 | Multi-Page-Pager |
| `m365.xiaodash.scooter.ui.fragment.LogsFragment` | 142 | BLE-Log-Anzeige |
| `m365.xiaodash.scooter.ui.viewmodel.MainViewModel` | **2284** | **Choke-Point**: Connection-State, Settings, Premium-Gating, Cloud-Sync, Stage-1-4-Activation |
| `m365.xiaodash.scooter.ui.viewmodel.DashBoardViewModel` | 275 | Tile-State |
| `m365.xiaodash.scooter.ui.viewmodel.ScooterViewModel` | 264 | BLE-Connection-Lifecycle |
| `m365.xiaodash.scooter.domain.model.ScooterM` | 135 | **Modell-Tabelle** (17 Geräte → modelTable) |
| `m365.xiaodash.scooter.data.model.ScooterPayload` | ~80 | `{sid, mid}` |
| `m365.xiaodash.scooter.data.model.ScooterTuningSettings` | ~150 | 5 Floats: `Can4850/4856/4857/1007Value` + `InitialAcceleration` (Mi-CAN-Tuning) |
| `m365.xiaodash.scooter.data.model.ScooterRemoteSetting` | ~120 | Cloud-Per-Modell-Settings (mid, ua, beta, sfs) |
| `m365.xiaodash.scooter.data.model.StlinkRemoteSettings` | ~150 | ST-Link-Anweisungen + benötigte FW-Items |
| `com.dalvik.b1.OO00000OOOOOOOO0000O` | 16247 (smali) | **Stringfog-Decoder + globale 1322-Entry Tabelle** |
| `com.dalvik.OO0OOOOO000OOOOOO000.OO00000OOOOOOOO0000O` | (kleiner) | **Sekundäre Stringfog-Tabelle** (BLE/UI/Auth-Strings) |
| `com.dalvik.j0.O00O00O00O000OO0O0OO` | ? | **BLE-Scan-Result-Container**, NB/NC-Detection |
| `com.dalvik.j0.O0000O0O0OO0O0000000` | 508 | **ZT3-FW-Handler**, Stage-1-4-Logic, Cloud-Function-Calls |
| `com.dalvik.k0.O0OOOO0O000000OO0OO0` | 1370 | **Modell-Dispatcher / ST-Link-Pfad**, importiert `ru.zdevs.zflasherstm32.STLINK` |
| `com.dalvik.m0.*` (~21 Files) | je 100-1000 | **BLE-Modul**: `XiaomiTransport`, `NinebotTransport`, Frame-Encoding, Auth-Stages |
| `com.dalvik.p0.*` (~18 Files) | je 100-500 | BLE-GATT-Operations (read/write characteristic) |
| `assets/fw/full_f65.bin` | 129 KB | **F65-Controller-Firmware** (encrypted, header `VelocidadAbsurda` + 8-byte IV + AES-CTR-body) |

---

## Bedeutung für ZT3-fxx — Empfehlungen

### Kritische Korrekturen zur ersten Analyse

1. ❌ ~~"XiaoDash hat kein ZT3-Support"~~ → **Falsch**. ZT3 Pro = `scooterMid 256`, im `modelTable` direkt gelistet, mit Nordic-UART-Service.
2. ❌ ~~"Mi-CAN-Protokoll only, nicht relevant für Ninebot"~~ → **Falsch**. XiaoDash hat **beide Protokolle** parallel via py9b-Foundation.
3. ❌ ~~"`full_f65.bin` ist M365-Firmware"~~ → **Korrektur**: das ist **F65-Controller-Firmware** (Modell-ID 45), eine **Segway/Ninebot**-Variante.
4. ✅ "Stringfog-Obfuscation blockiert statische Analyse" → **Aufgehoben**. Wir haben den Decoder geknackt und alle 5848 Strings extrahiert.

### Direkte Lehren für `zt3-fxx`

| # | Erkenntnis | Anwendung in zt3-fxx |
|---|---|---|
| **1** | XiaoDash nutzt `6e400002/3` Nordic-UART für ZT3 — **identisch zu unserem `core/ble/FrameCodecCrypto`** | Bestätigt: unser Wire-Protocol-Pfad ist korrekt |
| **2** | NB/NC Beacon-Manufacturer-IDs (`0x424E`/`0x434E`) sind die einzigen Erkennungs-Marker für Ninebot vs. Mi | Falls wir Multi-Modell-Support hinzufügen, **dieselben Beacon-Patterns checken** |
| **3** | Frame-Magic `5aa5` ist verifiziert für Ninebot/ZT3 | Unsere `FrameCodecClassic` und `FrameCodecCrypto` machen es schon richtig |
| **4** | py9b ist die kanonische Open-Source-Library für beide Protokolle | Falls wir ZT3-FW-Update-Support einbauen wollen → **py9b's Java-Port-Logik aus `com.dalvik.m0.*`** anschauen oder direkt py9b-Original portieren |
| **5** | XiaoDash macht 3 Action-Buttons konfigurierbar mit `ActionParameter*` (None/Horn/Light/Speedboost/Cruise/Profile/Mode) | **Genau dieses Pattern** für unsere Vol-Up/Down/Custom-Button-Mapping übernehmen |
| **6** | Konfigurierbare Telemetry-Tiles via `IdleXxx`-Pool | UX-Pattern für unser Dashboard |
| **7** | Backend-Endpoints `/f /k /p` an Firebase Cloud Function | Falls wir mal Cloud-Distribution für FW machen, dieses einfache Schema imitieren |
| **8** | `Stage 1-4 Activation`-Flow für FW-Unlock | Falls wir ZT3-FW-Modding angehen, **vermutlich derselbe Flow** wie XiaoDash für F65 |
| **9** | XiaoDash speichert in `assets/fw/full_f65.bin` mit `VelocidadAbsurda`-Magic + 8-Byte-IV — **AES-encrypted**, key kommt vermutlich aus `bytesFromJNI()` oder Cloud-Function `/k` | Falls wir je ZT3-FW packen, ähnlicher Container möglich |

### Was nicht relevant ist

- **Stringfog-Obfuscation** für unsere App: wir sind open-source, wäre Anti-Pattern
- **3-Tier-Premium-Subscription**: zt3-fxx ist Privatprojekt
- **Anti-Tamper-JNI**: nicht nötig
- **AdMob/Facebook-SDK/Firebase-UI-Auth**: privacy-issues

### Recommended Next-Steps (basierend auf XiaoDash)

1. **Multi-Vehicle-Garage**: nicht nur ZT3 — auch F2-Plus, G2 etc. wenn Familie/Freunde welche haben
2. **GPS-Trip-Tab vollständig**: Foreground-Service für ongoing recording, Maps für Visualisierung
3. **Konfigurierbare Tiles**: User darf wählen welche aus `IdleXxx`-Pool aufs Dashboard
4. **Audio-Upload (Custom Sound)**: ZT3 hat `0x76/0x77/0x78`-Audio-Cmds, XiaoDash zeigt UX dafür
5. **Custom-Button-Action-Mapping**: ähnlich `ActionParameter`-Pattern (statt fix-verdrahtet auf Lock/22)

---

## Reproduzieren der Analyse

Alle Werkzeuge in `analysis-artifacts/`:

```bash
# Voraussetzungen
brew install dex2jar           # 2.4
# Frida + Ghidra optional
pip3 install --user --break-system-packages frida-tools

# Schritt 1: APK → JAR
d2j-dex2jar m365.xiaodash.scooter.apk -o /tmp/xiaodash.jar

# Schritt 2: Call-Sites extrahieren
python3 analysis-artifacts/extract_callsites.py decompiled/jadx/sources \
    > /tmp/all_callsites.tsv
sed -i.bak 's|	com\.dalvik\.b1\.OO00000OOOOOOOO0000O$|	com.dalvik.b1.OO00000OOOOOOOO0000O.OO00000OOOOOOOO0000O|' /tmp/all_callsites.tsv

# Schritt 3: Decoder-Harness compilieren
cd analysis-artifacts && javac StringfogMulti.java

# Schritt 4: Batch-Decode
java -cp .:/tmp/xiaodash.jar StringfogMulti < /tmp/all_callsites.tsv \
    > /tmp/all_decoded.tsv

# Schritt 5: Filter nach Interesse
grep -E '\t[0-9a-f]{8}-[0-9a-f]{4}-' /tmp/all_decoded.tsv         # UUIDs
grep -iE 'xiaomi|ninebot|zt3|m365' /tmp/all_decoded.tsv           # Plattform-strings
awk -F'\t' 'length($3) > 3 && length($3) < 50 {print $3}' \
    /tmp/all_decoded.tsv | sort -u | grep -E '^[A-Z][A-Z_0-9]+$'  # Konstanten-Liste
```

→ Reproduktion sollte ~90 Sekunden dauern (Decode-Phase ist der Bottleneck).

### Frida (für künftige Live-Analyse, nicht durchgeführt)

Falls die App auf einem rooted Phone laufen soll und wir die Strings live mitschneiden wollen:

```javascript
// stringfog_hook.js
Java.perform(() => {
    const Decoder = Java.use('com.dalvik.b1.OO00000OOOOOOOO0000O');
    Decoder.OOOOOOO0OOOOO0O00OO0.implementation = function(seed, arr) {
        const result = this.OOOOOOO0OOOOO0O00OO0(seed, arr);
        console.log(`[StringFog] seed=${seed} → "${result}"`);
        return result;
    };
});

// Run:
// frida -U -f m365.xiaodash.scooter -l stringfog_hook.js --no-pause
```

→ Liefert in Echtzeit alle Decoder-Aufrufe inkl. Backend-URLs während des App-Flows.

---

## Caveats

1. **15 von 6106 Decode-Aufrufen erroren** (0.25%) — vermutlich seed-Format-Edge-Cases (z.B. einige Calls in Lambda-Closures haben verzerrte Token-Pattern).
2. **`bytesFromJNI()` nicht analysiert**: Native-Lib ist nicht als `.so` im APK präsent, vermutlich Lazy-Loaded aus einem encrypted Asset. Frida-Hook auf `Application.bytesFromJNI` würde sie zur Laufzeit dumpen, aber das hätten wir an einem laufenden Gerät gebraucht.
3. **`full_f65.bin`-Decryption-Key**: nicht statisch findbar — kommt vermutlich aus `bytesFromJNI()` oder dem Cloud-Function-`/k`-Endpoint zur Laufzeit. Frida + traffic-capture nötig.
4. **`com.dalvik.m0.*`-Klassen sind code-deobfuskiert lesbar**, aber wegen Coroutinen-Decompiler-Bugs (siehe JADX-Warnings „Removed duplicated region for block") teilweise schwer zu folgen. Smali-Diff wäre genauer.
5. **Backend-Responses nicht beobachtet**: das Format der `/f /k /p` Cloud-Function-Antworten ist unklar — würde Live-Capture mit Burp Suite o.ä. erfordern.
6. **ST-Link-Library `ru.zdevs.zflasherstm32`**: ist 3rd-party-Code von `zdevs.ru`, im APK eingebettet aber nicht weiter analysiert.
7. **Stage 1-4 exakte Bytefolgen**: die User-facing Strings wie „Stage 1 complete!" sind decodiert, aber die zugehörigen BLE-Frames die geschickt werden, würden ein Disassembly der Coroutinen oder einen Frida-BLE-Trace erfordern.

---

## Quellen

- **APK**: `m365.xiaodash.scooter.apk` (54 MB, package `m365.xiaodash.scooter`)
- **Decompile-Tools**: apktool 2.x, jadx 1.x, **dex2jar 2.4** (Hauptwerkzeug für Stringfog-Bypass)
- **Tooling**:
  - Java 21 / OpenJDK Homebrew (für JVM-Reflection-Decoder)
  - Python 3.14 (für Call-Site-Extraktion)
  - **Frida 17.9.1** + **Ghidra 12.0.4** lokal installiert für künftige dynamische Analyse
- **Vergleichs-Apps**:
  - `../shu/ANALYSIS.md` (ScooterHacking Utility — gleiche py9b-Foundation, weniger obfuskiert)
  - `../ninebot-segway/ANALYSIS.md` (offizielle Segway-App, NIS-Wrapper-protected)
- **Open-Source-Referenzen**:
  - [etransport/py9b](https://github.com/etransport/py9b) — die Python-Library auf der XiaoDash basiert
  - [megatronking/StringFog](https://github.com/MegatronKing/StringFog) — die verwendete Obfuscation-Library
  - [m365dashboard.de](https://www.m365dashboard.de/) — Community-Doku M365
- **App-Resourcen**:
  - `decompiled/apktool/AndroidManifest.xml`
  - `decompiled/apktool/res/values/strings.xml` (1135 Strings)
  - `decompiled/jadx/sources/m365/xiaodash/scooter/**`
  - `decompiled/raw/assets/fw/full_f65.bin`
- **Generierte Artefakte** (in `analysis-artifacts/`):
  - `StringfogMulti.java` — JVM-Decoder (~70 Zeilen)
  - `extract_callsites.py` — Call-Site-Scanner (~50 Zeilen)
  - `seed-decoded-short.tsv` — 5563 (seed, decoded) Tupel
  - `constants.txt` — 186 UPPERCASE-Konstanten
  - `uuids.txt` — 10 BLE-UUIDs
  - `urls.txt` — 4 Backend-URLs

---

## Footer

Analyse erstellt 2026-04-28 im Rahmen der ZT3-Pro-D Recherche. Reine RE-Doku für privaten Forschungs-Use; keine Anleitung für unbefugte Modifikationen. Die XiaoDash-App selbst ist eine kommerzielle Drittanbieter-App; Copyright bei den Autoren. Stringfog-Library © megatronking. py9b © etransport.

© 2026 Martin Pfeffer | celox.io
