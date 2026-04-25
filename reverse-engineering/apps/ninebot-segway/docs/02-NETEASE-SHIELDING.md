# NetEase NIS Wrapper – Anti-Tamper / App-Shielding

Die App ist mit dem kommerziellen Schutzpacker **NetEase NIS Wrapper** (易盾应用加固) versehen. Das ist der größte Befund dieser Analyse: ohne diesen Schritt zu verstehen, ist eine sinnvolle Code-Analyse unmöglich.

## Beobachtungen, die den Pack-Status beweisen

1. `classes.dex` ist nur **208 KB groß** und enthält nur **30 Klassen** (Smali) – für eine 116-MB-App mit RN-Anteilen unmöglich klein
2. Application-Class im Manifest: `com.netease.nis.wrapper.MyApplication`
3. Großer verschlüsselter Blob: `assets/nedata.db` (35 MB)
4. Verschlüsselte Konfiguration: `assets/nedig.properties` (~570 Bytes binär)
5. Verschlüsselte Sub-Archive: `assets/common_strings.zip` (Strings sind XOR-verschlüsselt) und `assets/backup_config.nb`, `assets/ec-sdk_config-default`
6. Native-Lib-Pfad-Hinweise im Wrapper-Code: `libnesec.so`, `libdexfix.so`, `libneguard.so`, `libnesec64.so`

## Sichtbare Klassen (alle aus dem Wrapper)

Aus `decompiled/jadx/sources/`:

```
a/auu/a.java                                    – String-Deobfuscator
com/ninebot/segway/R.java                       – generierte Resource-IDs
com/netease/nis/wrapper/Entry.java              – abstrakte Entry-Klasse für Real-App
com/netease/nis/wrapper/MyApplication.java      – Wrapper-Application
com/netease/nis/wrapper/MyJni.java              – JNI-Bridge (alle Native-Calls)
com/netease/nis/wrapper/Utils.java
com/netease/nis/wrapper/NEDialog.java           – „Tampered"-Dialog
com/netease/nis/wrapper/o.java                  – Native-Library-Loader (Multi-ABI)
com/netease/nis/wrapper/{a..p}.java             – obfuskierte Helfer
com/netease/nis/wrapper/plugin/InstrumentationProxy.java
com/netease/nis/wrapper/plugin/{a..g}.java
```

## String-Deobfuscation (`a.auu.a.c`)

Alle Strings im Wrapper sind via `a.auu.a.c("...")` verschlüsselt. Algorithmus:

```
input  = Base64-Decode(s)
output = bytes(c ^ key[i % 7]   for i, c in enumerate(input))
key    = b"Netease"             # 7 Bytes
```

Beispiel:

| Obfuskiert | Klartext |
|---|---|
| `ORcVFREWFw==` | `wrapper` |
| `LQoZSw8WESsEBwBPHQw9SwMXAAMVKxdaKBgyFT4JHQYABwwhCw==` | `com.netease.nis.wrapper.MyApplication` |
| `eUtCS1IsXHhc` | `7.6.3_969` |
| `IgwW` | `lib` |
| `qcTagM/p` | `确定` (chinesisch „OK") |

Python-Decoder:

```python
import base64
KEY = b"Netease"
def dec(s):
    b = base64.b64decode(s)
    return bytes(c ^ KEY[i % 7] for i, c in enumerate(b)).decode("utf-8", "replace")
```

Komplette Liste aller dekodierten Strings: siehe Abschnitt „Decoded String Inventory" unten.

## Lade-Flow (rekonstruiert)

```
Activity-Manager startet com.netease.nis.wrapper.MyApplication
  ↓
attachBaseContext():
   – o.a(ctx, "nesec")             [optional Multi-ABI: extrahiert lib/<abi>/libnesec.so aus Split-APKs nach files/lib/]
   – n0110…() (JNI Native-Methode) [entschlüsselt nedata.db -> tmp dex(es)]
   – (klassische DexClassLoader-Hot-Patch-Technik)
  ↓
onCreate():
   – ProxyComponentFactory installiert ClassLoader-Hooks
   – Echte Application-Klasse aus entschlüsseltem Code wird instanziiert
   – Echte Activity-Klassen werden über InstrumentationProxy umgeleitet
```

Konfigurations-Toggles aus den entschlüsselten Strings:

| Key (Klartext) | Bedeutung |
|---|---|
| `extract_switch_0` | Flag, ob Native-Libs in `files/lib/` extrahiert werden müssen |
| `provider_switch_1` | Component-Factory-Proxy aktiv |
| `shell_limit_0` | App-Start-Throttling (in Trial) |
| `x86_switch_1` | x86-ABI-Support |
| `debug_switch_0` | Debug-Logs |
| `BUGRPT_SWITCH` | Bugsnag-Crash-Reporting via NetEase-Wrapper |

## Anti-Analyse-Checks (statisch nachgewiesen)

Aus den entschlüsselten Englisch- und Chinesisch-Strings im Wrapper sind folgende Detection-Klassen sicher implementiert:

| Englisch-String | Chinesisch | Detection |
|---|---|---|
| `Detected the presence of a debugger.` | 检测到调试器 | Debugger (ptrace, TracerPid) |
| `Detected that the app is running on an emulator.` | 检测到在模拟器中运行 | Emulator (Genymotion, QEMU, etc.) |
| `Detected that the app is running in a rooted environment.` | 检测到该应用在root环境中运行 | Root (su-Binary, Magisk, Selinux) |
| `Detected that the app is running in an environment with cloud phone.` | 检测到云手机环境 | Cloud-Phone (RedFinger, Bluestacks Cloud) |
| `Detected that the app is running in an Xposed environment.` | 检测到Xposed环境 | Xposed/EdXposed |
| `Detected that the app is running in a hooking environment.` | 检测到该应用在hook环境中运行 | Frida, Substrate, Riru |
| `Detected that the app has been injected.` (so) | 检测到该应用被注入so | LD_PRELOAD-Injection |
| `Detected that the application has been tampered.` | – | Signature-Tampering |
| `Detected an abnormal ROM.` | 检测到Rom异常 | Custom-ROM-Detection |
| `Detected that the USB debugging mode is enabled.` | 检测到USB调试开关打开 | `adb_enabled` Setting |
| `Detected that a VPN is being used.` | 检测到VPN开启 | VPN-Interface |
| `Detected that a proxy is being used.` | – | HTTP-Proxy |
| `Detected that the app is running in a dual app environment.` | 检测到在双开app中运行 | Parallel-Space, MultiApp |
| `Detected that the app is running in an environment with virtual machine.` | – | VirtualApp/VirtualXposed |
| `Detected that the app is running in an environment with simulated clicks.` | 检测到模拟点击环境 | UIAutomator-Tap-Tools |
| `Detected that the app is running in an environment with screen share.` | – | Screen-Recording |
| `Detected that the app is running in an environment with fake location.` | – | Mock-Location |
| `Detected that the app is running in an environment with network sniffing.` | – | Promiscuous-Mode / Wireshark-on-device |
| `Detected that the app is a trial version, please do not release it directly.` | 当前版本为加固试用版，请勿直接发布上线 | NetEase Trial-Watermark (NICHT aktiv → kommerzielle Lizenz) |
| – | 请前往官方渠道下载正版APP | „Please download the official app from the official channel" |

Bei Detection wird via `NEDialog` ein modaler Dialog gezeigt, der die App-Nutzung blockiert (`确定`-Button).

## Komplette Liste der dekodierten Wrapper-Strings

```
'/cmdline'
'/libnesec.so'
'/proc/'
'/proc/self/maps'
'/system/bin/app_process'
'/templib'
'/templib/libs.zip'
'adb_enabled'
'again loadLibrary '
'android.app.ActivityThread'
'appInfo'
'arm64-v8a'
'armeabi'
'array['
'assets/.nesec_patch'
'BUGRPT_SWITCH'
'bugrpt'
'class '
'classTable'
'com.netease.nis.'
'com.netease.nis.wrapper.CrashHandler'
'com.netease.nis.wrapper.MyApplication'
'com.netease.nis.wrapper'
'com.ninebot.segway'
'Comp loadLibrary '
'copy '
'cpu:'
'currentActivityThread'
'dalvik.system.BaseDexClassLoader'
'extract_switch_0'
'field name:%s type:%s'
'files'
'find entry '
'findLoadedClass'
'get linker arch failed:'
'get method:'
'getApplicationInfo failed : '
'getFieldSCDesc name:%s'
'getFieldSCDesc require name:%s type:%s'
'init bugrpt error:'
'isClassLoaded error: '
'leaveBreadcrumb'
'libdexfix.so'
'libneguard.so'
'libnesec.so'
'libnesec64'
'mBoundApplication'
'nativeLibraryDirectories'
'nesec-x86'
'nesec'
'NoClassDefFoundError'
'pathList'
'provider_switch_1'
'ProxyComponentFactory init, classLoader = '
'relink load '
'setClassTable error: '
'setFilterWords'
'setUserTag'
'shell_limit_0'
'start unzip '
'System.loadLibrary '
'wrapper'
'x86_64', 'x86_switch_1', 'x86'
'.CrashHandler'
'.user.UserStrategy'
```

## Konsequenz für die Analyse

- **Java-Code-Analyse**: ❌ ohne Frida-Dump des entschlüsselten Dex aus dem Speicher nicht möglich
- **Empfohlene Vorgehensweise** (nicht in dieser Doku ausgeführt):
  1. Realgerät, Frida-Server (rooted)
  2. Frida-Script: `Process.enumerateModules()` → Module mit `nedata`/`nesec` identifizieren
  3. Memory-Dump des entschlüsselten Dex via `Process.enumerateRangesSync('rwx')` und Magic-Bytes-Suche `dex\n035`/`dex\n037`/`dex\n038`/`dex\n039`
  4. Alternativ: Tools wie **FRIDA-DEXDump**, **dexcaller**, **Youpk** (Yu-Pk) oder **NPManager** auto-extrahieren NetEase-NIS-Dexes
  5. Als letztes Mittel: Reverse der `libnesec.so` aus den split-APK-Dateien (z. B. `config.arm64_v8a.apk` der originalen AAB), allerdings hat NetEase NIS auch native Code-Obfuscation (OLLVM-bf-fla)
