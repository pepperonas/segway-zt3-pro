# Limitierungen & Empfehlungen für tiefergehende Analyse

## Was diese statische Analyse NICHT liefern kann

### 1. Echter Anwendungs-Java-Code
Wegen NetEase-NIS-Wrapper sind alle eigentlichen `cn.ninebot.*`-/`com.ninebot.*`-Klassen verschlüsselt in `assets/nedata.db` verpackt. Folge:

- **Keine BLE GATT-Service-/Characteristic-UUIDs** der ZT3 Pro auf statischem Wege
- **Keine Paketstruktur des Ninebot-/Segway-BLE-Protokolls** (Header-Magic, CRC-16-Implementierung, AES-CBC-IV-Strategie)
- **Kein Firmware-Update-Flow** (Bootloader-Befehle, Chunk-Größen, Signaturen)
- **Keine SetSpeed/SetMode/Lock-Befehle** im Klartext
- **Keine Telemetrie-Field-Mappings** (Geschwindigkeit, Akkuzellspannungen, Temperaturen)

### 2. Native Libraries (`*.so`)
Diese APK ist die **Base-APK eines App-Bundles**. Die nativen Libraries (vermutlich `arm64-v8a`/`armeabi-v7a`) liegen in den separat verteilten ABI-Splits:
- `config.arm64_v8a.apk`
- `config.armeabi_v7a.apk`
- `config.x86_64.apk` (laut Wrapper-Code unterstützt)

Ohne diese Splits ist auch die Native-Code-Analyse von `libnesec.so` nicht möglich.

### 3. Hermes-Bytecode der React-Native-Module
`assets/platform.zip → platform.bundle` ist Hermes-Bytecode v94 (~680 KB). Statisch lesbar, aber nicht mit Standard-Tools dekompilierbar zu lesbarem JS.

### 4. Verschlüsselte String-Tabellen
`assets/common_strings.zip` enthält Locale-Strings als `*.nb`-Dateien. Verschlüsselungsalgorithmus identisch zu nedata.db (NetEase-NIS) und damit ebenfalls Laufzeit-only.

## Wie man die ZT3 Pro BLE-Protokoll-Analyse fortsetzt

### Option A: Public Domain Knowledge (empfohlen)

Das Ninebot/Segway-BLE-Protokoll ist seit Jahren in der Community dokumentiert (für G30 KickScooter MAX, ES2/ES4, F-Series). Der ZT3 Pro nutzt mit hoher Wahrscheinlichkeit eine **Variante des gleichen Protokolls**:

| Quelle | Inhalt |
|---|---|
| `m365-tools` (GitHub) | Original Xiaomi M365-Protokoll, Header `55 AA` |
| `ninebot-protocol` div. Repos | G30/E-Series-Erweiterungen mit AES-Verschlüsselung |
| `NbCrypto` | AES-CCM-Implementierung mit Pairing-Key-Exchange |
| Frame-Format | `[0x5A 0xA5][len][src][dst][cmd][arg][payload…][CRC16-LE]` |
| GATT-Service typisch | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` (Nordic UART), Char `…0002` (RX), `…0003` (TX) |

Für ZT3 Pro müsste man:
1. Mit nRF Connect (Android) oder LightBlue (iOS) den Scooter scannen
2. GATT-Service-Liste mit den bekannten Ninebot-UUIDs vergleichen
3. Wenn übereinstimmend: bestehende Tools direkt anwenden
4. Wenn abweichend: Capture mit `btsnoop_hci.log` (Android-Developer-Options „Enable Bluetooth HCI snoop log")

### Option B: Dynamische Analyse mit Frida (rooted Gerät)

#### Schritt 1: Dex-Dump aus Memory

```javascript
// frida-dexdump-style script
Java.perform(() => {
  // Hook die NetEase-Wrapper-Klassenladung
  const ClassLoader = Java.use("java.lang.ClassLoader");
  ClassLoader.loadClass.overload("java.lang.String").implementation = function (name) {
    if (name.startsWith("cn.ninebot") || name.startsWith("com.ninebot")) {
      console.log("[+] Loading: " + name);
    }
    return this.loadClass(name);
  };

  // Suche alle Memory-Regions nach DEX-Magic
  Process.enumerateRangesSync({protection: 'r--', coalesce: true}).forEach(range => {
    try {
      const ptr = Memory.scanSync(range.base, range.size, 'de 78 0a 03 35 00 00 00');
      // …
    } catch (e) {}
  });
});
```

Bewährte Tools:
- **frida-dexdump** (https://github.com/hluwa/FRIDA-DEXDump) – funktioniert oft direkt mit NetEase NIS
- **Youpk / yu-pk** – Custom-AOSP-Image, dumpt Dex zur Laufzeit
- **NPManager** – Android-App, die NetEase-Pack erkennt
- **Drizzle-DumpDex** / **SLJ-Dexdump**

#### Schritt 2: BLE-Hooks setzen

```javascript
Java.perform(() => {
  const BluetoothGatt = Java.use("android.bluetooth.BluetoothGatt");
  const BluetoothGattCharacteristic = Java.use("android.bluetooth.BluetoothGattCharacteristic");

  BluetoothGatt.writeCharacteristic.overload(
    "android.bluetooth.BluetoothGattCharacteristic"
  ).implementation = function (c) {
    const uuid = c.getUuid().toString();
    const val  = c.getValue();
    console.log(`[BLE WRITE] ${uuid}: ${hexdump(val)}`);
    return this.writeCharacteristic(c);
  };

  // Notifications/Reads spiegelbildlich auf onCharacteristicChanged
});
```

#### Schritt 3: Pairing-Key extrahieren

Der Ninebot-Pairing-Key wird typischerweise per ECDH oder festem 16-Byte-Default-Key (`97 cf b8 24 6c a4 31 b8 ...`) etabliert. Hooke `javax.crypto.Cipher.init` und filtere auf AES/CCM/CBC um den Session-Key zu sehen.

### Option C: HCI-Snoop + Reverse von einer bekannten Sequenz

1. Aktiviere „Bluetooth HCI snoop log" in Android Developer Options
2. Verbinde Phone↔ZT3 Pro normal über die App
3. Mache typische Aktionen: Lock, Unlock, SetSpeed, ReadBattery
4. Ziehe `/sdcard/btsnoop_hci.log` und analysiere mit Wireshark
5. ATT-Write-Pakete an die ZT3-Pro-Char zeigen das verschlüsselte Frame
6. Vergleich mit bekannten Ninebot-Frame-Mustern → Header-Position klar
7. Wenn AES-CCM: Pairing-Key kann durch Re-Pairing + Frida-Hook erfasst werden

### Option D: Native-Lib-Reverse (`libnesec.so`)

Wenn du die Split-APK-Datei `config.arm64_v8a.apk` aus einer SAI/APK-Combine-Quelle bekommst:

1. Extrahiere `lib/arm64-v8a/libnesec.so`
2. IDA Pro / Ghidra mit aktuellem ARM64-Plugin
3. Erwartungswert: stark obfuskierter Code (OLLVM Control-Flow-Flattening + Bogus-Control-Flow + String-Encryption)
4. Suche nach Crypto-Konstanten: AES-S-Box, SHA-256-Init-Werte, RC4-Stream
5. Mit Tools wie **dexripper**, **APKiD**, **D810** (ollvm-deobfuscator)

## Empfohlene Vorgehensweise (Priorität)

1. **ZUERST**: Public-Tool-Vergleich (`m365-tools`, `Bluez-DLL` etc.) – möglicherweise löst das die Frage komplett ohne Reverse-Engineering
2. **DANN**: HCI-Snoop-Capture, um zu sehen ob das Protokoll bekannt ist
3. **WENN UNBEKANNT**: Frida-DexDump um den entschlüsselten Code zu erhalten
4. **NUR WENN NÖTIG**: `libnesec.so` Native-Reverse

## Tools-Liste (Schnellreferenz)

| Tool | Zweck |
|---|---|
| `apktool` 2.12.1 | APK → Smali + Resources |
| `jadx` 1.5.3 | Smali → Java |
| `dex2jar` | DEX → JAR |
| `bytecode-viewer` | All-in-one Java-Reverse |
| `frida` ≥ 16 | Dynamic Instrumentation |
| `frida-dexdump` | Memory-Dump verschlüsselter Dex |
| `nRF Connect` | Android BLE-Inspector |
| `Wireshark` + `BtSnoop` | HCI-Trace-Analyse |
| `IDA Pro` / `Ghidra` | Native ARM64 Reverse |
| `hbctool` / `hermes-decompiler` | Hermes-Bytecode → JS |
| `r2frida` | Radare2 + Frida-Bridge |

## Rechtlicher Hinweis

Reverse-Engineering der Ninebot-App und des ZT3-Pro-BLE-Protokolls ist in der EU unter **§ 69e UrhG** (Dekompilierung zur Herstellung von Interoperabilität) zulässig. Veröffentlichung der entschlüsselten Java-Klassen (z. B. nedata.db dump) ist hingegen problematisch. **Reine BLE-Frame-Dokumentation aus HCI-Snoop ist unbedenklich.**
