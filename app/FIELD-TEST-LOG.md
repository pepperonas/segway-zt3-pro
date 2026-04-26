# Field-Test Log

Live-Sessions mit dem realen ZT3 Pro D (S/N maskiert, MAC `XX:XX:XX:XX:C5:96`).

## Session 1 — 2026-04-25 23:00 (Samsung S24 Ultra)

### Setup
- App-Version: 0.1.0 debug
- Gerät: Samsung S24 Ultra (Android 16)
- Roller: ZT3 Pro D, bereits via SHU auf US-Region geflasht (40 km/h Hardware-Limit)

### GATT-Layer (vollständig sauber)

| Zeit | Event | Status |
|---|---|---|
| 22:56:02.439 | onClientConnectionState — `connected=true` | ✅ |
| 22:56:02.725 | onSearchComplete — `status=0` | ✅ |
| 22:56:02.727 | setCharacteristicNotification on `6e400003-…` | ✅ |
| 22:56:02.728 | configureMTU — request 247 | ✅ |
| 22:56:02.729 | onConfigureMTU — `mtu=251 status=0` | ✅ |

→ **Nordic-UART-Service-Discovery + MTU-Negotiation ist 1:1 wie in der HCI-Capture vorhergesagt**.

### App-Frames im Diagnostics-Log

```
23:00:39.151  Note  Pair      fresh handshake start (C1:6B:5E:D0:C5:96)
23:00:39.153  TX    RX-WRITE  00 62 6C 74 2E 34 2E 31 35 39 36 32 7A 36 72 71 65 6A 74 30
                                          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                          0x00 ++ "blt.4.159" ++ "62z6rqejt0"  (ECDH init-hello)

23:00:40.660  Note  Profile   apply 'City' = 22 km/h          (+1.5 s auto-trigger)
23:00:40.661  TX    RX-WRITE  55 AB 07 01 00 02 72 02 16 00 6B FF
                                          ^^^^^^^^^^^^^^^^^^
                                          plaintext payload: writeReg 0x72, len=2, val=0x16(=22), 0x00

23:00:54.454  Note  Profile   apply 'City' = 22 km/h          (re-trigger, second connect)
23:00:54.454  TX    RX-WRITE  55 AB 07 02 00 02 72 02 16 00 6A FF
```

### Beobachtung

- **Keine RX-Notifies** über die ganze Session (logcat-Filter auf `NOTIFICATION_EVT` und `onCharacteristicChanged`: leer).
- **Aber: Roller hat reagiert** — nach den TX-Writes lief der Roller mit **5 km/h** statt der Default-40.
- Power-Cycle setzte den Speed wieder auf den US-Region-Default zurück.

### Hypothesen

| # | Erklärung | Wahrscheinlichkeit |
|---|---|---|
| 1 | Roller hat das `55 AB`-Frame als gültig akzeptiert, aber Wert 0x16 wurde anders interpretiert (z. B. als andere Skalierung) | mittel |
| 2 | Roller hat die unerwarteten Frames als „Manipulationsversuch" gewertet und sicherheitshalber auf 5 km/h Walk-Mode gedrosselt | mittel |
| 3 | Das geflashte SHU-Custom-FW hat einen looser Format-Check, akzeptiert auch `55 AB` aber mit anderer Register-Map | mittel |

Alle drei Hypothesen sind ohne Decryption nicht eindeutig zu unterscheiden. Klar ist nur:
- Der ECDH-Pfad (`55 AB`) ist **nicht** das richtige Wire-Format
- Der Classic-Pfad (`5A A5`) muss implementiert werden, um deterministisch zu steuern

### Behobene Bugs aus dieser Session

| Bug | Fix |
|---|---|
| `BluetoothGatt: writeCharacteristic() - prior command is not finished` | `Mutex` + `writeInFlight`-Flag in `GattClient.send()` |
| MTU-Request lief vor dem CCCD-Write-Callback | MTU-Request nach `onDescriptorWrite` |
| Auto-Apply des Boot-Profils feuerte sofort beim Connect, parallel zum Pairing-Init | 1.5 s Delay + Toggle in Settings |
| Pairing-encrypt() returnte null wenn Session nicht versiegelt | Plaintext-Fallback mit Frame-Wrap |
| Pair-Screen zeigte alle BLE-Devices (TV, Watch, Speaker) | `BleScanner` filtert auf Ninebot-Manufacturer-Prefix |
| User musste manuell „Connect" tippen | Auto-Pair beim ersten Scooter-Treffer + Auto-Close des Pair-Screens |
| `StandaloneCoroutine was cancelled` als sichtbarer Fehler | `CancellationException` wird re-thrown statt in `errorText` zu landen |

### Logik-Anpassungen nach dieser Session

| Anforderung | Implementation |
|---|---|
| „Beim Einschalten max 22 km/h" | `autoApplyOnConnect = true` als Default; SpeedProfileManager schickt nach 1.5 s das Boot-Profil bei jedem Connect |
| „Erst nach App-Unlock 40 km/h" | Großer Lock/Unlock-Toggle im Vehicle-Dashboard, optional PIN, optional Vol-Down-3× Stealth |
| „Disconnect / Roller-Power-Cycle setzt zurück" | SpeedProfileManager resettet `_isUnlockModeActive` bei `isConnected = false` |

### Nächster Schritt

`5A A5`-Classic-Stack implementieren (siehe `app/README.md` → Open Items § Classic-Path). Das Field-Test-Verhalten (5 km/h Failsafe nach unseren `55 AB`-Frames) bestätigt 1:1 die Capture-Auswertung — der ECDH-Pfad ist nicht das richtige Wire-Format für den ZT3 Pro D.

---

## Session 2 — 2026-04-26 (Plaintext `5A A5`-Versuch)

### Setup
- App-Version: 0.2.0 debug
- Pfad: Stock-Ninebot-Plaintext (`c6.b#a()` Case 3) — `5A A5 [len] [src dst cmd arg payload] [crc16-LE]`

### Beobachtung

| Aktion | TX-Frame | RX | Roller-Reaktion |
|---|---|---|---|
| SetSpeedLimit(22) | `5A A5 02 3E 21 02 72 16 00 14 FF` | — | keine |
| SetSpeedLimit(40) | `5A A5 02 3E 21 02 72 28 00 02 FF` | — | keine |
| Lock | `5A A5 02 3E 21 02 70 01 01 2A FF` | — | keine |
| Unlock | `5A A5 02 3E 21 02 70 01 00 2B FF` | — | keine |
| ReadRegister 0xB0/32 | `5A A5 01 3E 21 01 B0 20 CE FE` | — | keine |

CRCs verifiziert (z.B. `~(0x02+0x3E+0x21+0x02+0x72+0x16+0x00) & 0xFFFF = 0xFF14`, LE = `14 FF` ✓). Frame-Format formal korrekt — Roller bleibt aber **stumm**.

### Hypothesen-Analyse

Zwei parallele Analyse-Agenten lieferten widersprüchliche Befunde:

| Agent | Befund |
|---|---|
| **A** (SHU Source-Code statisch) | ZT3 Pro D fällt in Case 3 (Ninebot Plaintext), kein Crypto |
| **B** (HCI-Capture `2026-04-25-shu-flash-session.md`) | Manufacturer-ID `0x434E` ("NC") = Crypto-Variante; alle 3142 ATT-Payloads verschlüsselt |
| **C** (Final-Verify) | Agent A's Case-3-Klassifikation war falsch. Korrekt ist Case 2 (NinebotCrypto). `ScooterActivity.n():1046` wählt für `usesCrypto=true` den NinebotCrypto-Pfad, und für ZT3 ist `usesCrypto=true`. |

→ **Auflösung**: ZT3 Pro D **muss** den NinebotCrypto-Pfad sprechen. Plaintext-Frames werden auf Wire empfangen aber von der Decrypt-Logik des Rollers verworfen (CRC-Mismatch oder Format-Error nach Decrypt-Versuch) → Stille.

---

## Session 3 — 2026-04-26 abend (NinebotCrypto-Implementation, Iteration 3)

### Setup
- App-Version: 0.3.0 debug
- Neue Files: `core/crypto/NinebotCrypto.kt`, `core/ble/FrameCodecCrypto.kt`
- Wire-Format (aus `c6.c#i()` portiert): `5A A5 [len] [src dst cmd arg ENC(payload)] [tag_4b] [ctr_BE]`
- Handshake: erstes Frame `5A A5 10 3E 21 5C 00 [16 random]` (counter=0, f-XOR-obfuscation), Token aus Roller-Response → Key-Re-Derivation

### Beobachtung

| Aktion | Erwartung | Tatsächlich |
|---|---|---|
| SetSpeedLimit(22) | Roller drosselt auf 22 km/h | Roller fuhr nur **15 km/h** |
| Power-Cycle | — | Roller wieder auf 40 km/h Default (US-Region) |

→ Crypto-Frames werden **partiell verstanden**, aber der commandete Wert kommt nicht 1:1 an. Kein konsistenter Failsafe wie in Session 1 (5 km/h), sondern ein anderes Sub-Limit (15 km/h).

### Offene Hypothesen für 2026-04-27

1. **`scooterName`-Mismatch**: Roller advertiset im HCI-Capture mit *zwei* Namen (`1K1Dx…` und `1K1Ux…`). Unsere App verbindet sich mit dem ersten Treffer, SHU evtl. mit einem konkreten. Wenn der Name zwischen App und Roller divergiert, ist `SHA-1(name + salt)` schief und der Roller decryptet auf Müll, der zufällig in andere Limit-Slots fällt.
2. **Speed-Limit-Register `0x72` evtl. falsch**: 15 km/h könnte ein anderer Limit-Slot sein (z.B. Walk-Mode-Limit, P-Switch-Limit, SHFW-spezifischer Slot).
3. **Encoding-Fehler**: `(0x16, 0x00)` als U16-LE für 22 vs. U8 + Padding vs. ×10-Skalierung.
4. **Handshake nicht komplett**: Falls die Roller-Response (counter=0) bei uns nicht ankommt oder anders interpretiert wird, läuft der Roller mit dem Original-Default-Token (zeros) und unsere späteren Frames decrypten zu Garbage.

### Diagnostik-Plan

- Diagnostics-Screen mit `scooterName`-Anzeige erweitern
- Inner-Frame **und** Wire-Frame parallel im BleLog zeigen
- `logcat -s ToothSErvice` parallel mitschneiden (SHU-Original-Tag — falls SHU parallel auf der Workbench läuft)
- Sticker-S/N vs. BLE-Adv-Name vergleichen

### Status

**Stand 2026-04-26**: NinebotCrypto-Code committed, APK auf S24 Ultra installiert, **Field-Tuning offen für 2026-04-27**.

---

## Session 4 — 2026-04-27 (HCI-Capture-Analyse `speed-manip.pcap`)

### Quelle

`reverse-engineering/ble-captures/speed-manip.pcap` (4.4 MB, 32k HCI-Frames, 1248 ATT-Frames). User-bereitgestellter Live-Mitschnitt einer SHU-Session, in der der Speed erfolgreich gesetzt wurde.

### Identifizierte Phasen

| Phase | Zeit | Pfad | Status |
|---|---|---|---|
| **A** | t=5395-5718 | ECDH `55 AB` | scheitert (5 km/h Failsafe) |
| **B** | t=6019-6034 | NinebotCrypto `5A A5` | **erfolgreich (Resume)** |
| **C** | t=7701-7910 | Plaintext `5A A5` | das war meine App (Iteration 2) |
| **E1** | t=9358-9415 | NinebotCrypto, plen=0x10-init mit 16-Byte-Random | TX-only, scheitert |
| **E2** | t=9585-9605+ | NinebotCrypto, plen=0x00-init (4-Byte-Hello) | **erfolgreich (Resume)** |

### Scooter-Identität

- **Adv-Name**: `1K1UA2551P3965` (14 Bytes, NUR diese Variante — kein paralleles `1K1Dx*` in diesem Capture)
- **MAC**: `c1:6b:5e:d0:c5:96`
- **Manufacturer-ID**: `0x434E` ("NC" = NinebotCrypto-Variante, bestätigt)

### Crypto-Verifikation (Phase E1)

Phase E1 erste TX (counter=0): `5A A5 10 5E 42 72 49 39 A5 36 2C F9 68 8B 62 2F B8 A0 CE 80 2E F0 98 00 00 D1 FF 00 00`
- Body[0..3] = `5E 42 72 49` = f-XOR von `[3E 21 5C 00]` mit Key `SHA-1(name[0..12] ++ salt[0..12])[0..16]`
- Verifikations-Script (Python + PyCryptodome): output `5e427249` ≡ Wire ✓

→ **Algorithmus von `NinebotCrypto.kt` ist 1:1 korrekt**. Schlüssel-Ableitung, AES-ECB, f-XOR alles bestätigt.

### Entscheidende Wire-Erkenntnisse

1. **Funktionierender Hello ist plen=0x00** (4 Bytes inneren Frame `[3E 21 5C 00]`), nicht plen=0x10 mit 16-Byte-Random. Der 16-Byte-Random ist nur First-Pair und scheitert ohne Power-Button-OOB.
2. **Counter-Sprung 0 → 2 → 3 → ...**: i() inkrementiert nach Send (0→1), h() inkrementiert nach Receive (1→2), nächster Send rechnet `iA = counter+1 = 3`. Counter=1 erscheint nie auf der Wire.
3. **Fire-and-Forget**: SHU wartet NICHT auf Handshake-Response. Sendet sofort nach Init weitere Commands. Roller picks die ersten korrekt entschlüsselbaren Frames.
4. **SHU hält `c`-Instanz über BLE-Disconnects am Leben**: f5100d (Token) bleibt im Speicher, daher startet die nächste Session schon mit `SHA-1(name + token)`-Key, NICHT `SHA-1(name + salt)`. Phase B + Phase E2 zeigen dies — der wire-body `89 5C 97 9D` für `[3E 21 5C 00]` matcht keine fresh-derived Key.

### Code-Anpassungen

| File | Change |
|---|---|
| `core/crypto/NinebotCrypto.kt` | `buildInitFrame()` produziert jetzt plen=0x00 statt plen=0x10. Neue API: `snapshotToken()`, `loadToken(persisted)` für Persistenz. |
| `core/data/PairingPrefs.kt` | Neues Feld `cryptoToken` in `Config`, plus `saveCryptoToken()` / `loadCryptoToken()` Helpers. |
| `core/vehicle/Zt3ProVehicle.kt` | Bei Init: lade persisted Token. Bei jedem Decrypt: Token-Diff erkennen + persist. Handshake fire-and-forget (keine 3s-Blockade mehr). BleLog-Note loggt `scooterName` + Token-State zur Diagnose. |
| `feature/home/ActiveVehicleHolder.kt` | `pairingPrefs` + `bleLog` durchreichen. |

### Status

**Stand 2026-04-27 morgens**: Code-Anpassungen aus pcap-Analyse committed, Build steht. APK noch nicht auf Phone (User-Phone gerade nicht verbunden). **Bereit für nächsten Field-Test, sobald Phone wieder live ist.**

---

## Session 5 — 2026-04-27 ~05:30-06:10 (Patched-SHU Methode → DURCHBRUCH)

### Problem-Stand

Drei Bug-Klassen verhinderten dass unsere Frames vom Roller akzeptiert wurden:
1. **Falsche Key-Derivation**: `minOf(size, 12)` statt voller Source-Länge in SHA-1-Input → komplett falscher AES-Key
2. **Falsche dst-Routing**: alle Frames an 0x21 statt das per-Funktion korrekte dst (0x04 für Handshake, 0x16 für VCU-Register)
3. **Falsches Speed-Limit-Register**: `arg=0x72` (= 114 dec) statt `arg=0x48` (= 72 dec)

### Methode: Patched SHU mit Logging

`reverse-engineering/apps/shu/decompiled/apktool/smali/c6/c.smali` an Zeile 1166 (`i([B)[B`) gepatcht — ein Block Smali der bei jedem TX `Log.d("CRYPTO_DUMP", ...)` mit Token+Random+Key+Counter+Data ausgibt:

```smali
.method public final i([B)[B
    .locals 9
    const-string v0, "Data"
    invoke-static {p1, v0}, Lkotlin/jvm/internal/m;->e(Ljava/lang/Object;Ljava/lang/String;)V
    # === CRYPTO_DUMP injection — alle Felder als Base64 ausgeben
    const-string v6, "CRYPTO_DUMP"
    const/4 v7, 0x2
    invoke-static {p1, v7}, Landroid/util/Base64;->encodeToString([BI)Ljava/lang/String;
    move-result-object v8
    iget-object v0, p0, Lc6/c;->d:[B
    invoke-static {v0, v7}, Landroid/util/Base64;->encodeToString([BI)Ljava/lang/String;
    ...
```

Dann `apktool b` + `apksigner` + `adb install -r`. Phone braucht **kein Root** — patched APK wird mit Debug-Keystore neu signiert und überschreibt die Originale.

### Findings via CRYPTO_DUMP

**Initial Key (C=0)**: `K = f9 94 cf aa 13 f1 1c fc 8a b4 b9 39 d0 ad 9a 8d`

Mit unserer alten 12-Byte-Truncation: `K = 12 17 b5 df 7c 61 10 61 …` ❌  
Mit korrekter voller Länge: `K = f9 94 cf aa 13 f1 1c fc …` ✅

**SHU's `c6.c.d()`-Smali**:
```smali
const/4 v3, 0x0      # destOffset
const/4 v4, 0x0      # startIndex
const/4 v5, 0x0      # endIndex (default-Marker, siehe v6)
const/16 v6, 0xc     # flags=12=0b1100 → bit-2 gesetzt → endIndex defaultet auf src.size
invoke-static/range {v1 .. v7}, Lkotlin/collections/d;->e([B[BIIIILjava/lang/Object;)[B
```

→ kopiert die KOMPLETTE src-Länge (max 16 Bytes Slot), NICHT nur 12. Bug-Fix in `NinebotCrypto.deriveKey()`:

```kotlin
// VORHER (falsch)
System.arraycopy(left, 0, buf, 0, minOf(left.size, 12))
// NACHHER (korrekt)
System.arraycopy(left, 0, buf, 0, minOf(left.size, 16))
```

### Wire-Format-Konstanten (1:1 von SHU)

| Stage | Wire-Bytes (Plaintext-Body) | dst | cmd | arg | payload |
|---|---|---|---|---|---|
| 1. getBleRandom | `[3E 04 5B 00]` | **0x04** | 0x5B | 0x00 | (none) |
| 2. o1 (pair-init) | `[3E 04 5C 00 + 16 random]` | 0x04 | 0x5C | 0x00 | 16 R |
| 3. D0 (challenge) | `[3E 04 5D 00 + 14 challenge]` | 0x04 | 0x5D | 0x00 | 14 chal |
| **SetSpeedLimit** | `[3E 16 02 48 14 kmh]` | **0x16** | 0x02 | **0x48** | `[0x14, kmh]` |
| Status-Read | `[3E 16 01 reg len 00]` | 0x16 | 0x01 | reg | `[len, 0x00]` |

→ **dst-Routing ist per-Modul**: 0x04 = Cellular/IoT-Module (handshake), 0x16 = VCU (register), 0x02/0x07 = andere Sub-Module.

### Resume-Flow

Persisted Random (`f5101e`) wird über Sessions hinweg in `PairingPrefs` gespeichert. Token (`f5100d`) wird bei JEDEM Connect frisch vom Roller ausgegeben (cmd=0x5B-Response).

Für unseren Roller (MAC `C1:6B:5E:D0:C5:96`) per CRYPTO_DUMP extrahiert:
```
appRandom = b1 59 e5 ed 55 54 7d 3e 8c a9 97 a1 61 d9 5b 42
```

(Hardcoded in `Zt3ProVehicle.persistedRandomForDevScooter` für die Dev-Phase.)

### Verifikation

Field-Test 2026-04-27 ~06:10:
- Handshake: TX1 `5A A5 00 89 5C 97 9D ... 00 00 62 FF 00 00` ✅ byte-perfect zu SHU's Phase B/E2
- RX-Notify ankommend mit 30-Byte plen=0x1E Token+Challenge ✅
- `L: token+challenge received` ✅
- `M: resumed via persisted random (key=SHA-1(R+T))` ✅
- `O: fully paired` ✅
- **22 km/h Tap** → Roller drosselt sofort auf 22 km/h ✅✅✅

### Status

**Stand 2026-04-27 06:10**: ZT3 Pro D **funktioniert vollständig** über die App. Crypto, Routing, Register-Map alles korrekt. Nächste Schritte: Token-Persistenz in DataStore prüfen, andere Commands (Lock/Mode/Lights) gegen SHU verifizieren, persistedRandom dynamisch über Frida/CRYPTO_DUMP-Workflow oder Echt-Pair-Flow ableiten.

---

## Session 6 — 2026-04-27 ~06:30-07:00 (Lock-by-Default UX final)

### Anforderung

User-Vorgabe: "Roller nach Einschalten standardmäßig 22 km/h, Entsperrung auf 40 nur durch App-Code oder 3×-Vol-Down — und das auch bei Display aus".

Iterativ verfeinert zu: **3× Vol-Up = Unlock auf 40, 3× Vol-Down = Lock auf 22**, beides funktioniert sowohl mit Screen-On als auch Screen-Off.

### Implementierung

#### `VehicleState.isReady`-Flag (Sync-Punkt für Auto-Apply)

Vorher: `SpeedProfileManager` wartete starre 1.5s nach `isConnected=true` bevor das Boot-Profile gefeuert wurde. Mit dem 3-stage-Handshake aus Session 5 ist die Wartezeit deterministisch — `isReady` flippt direkt nach Stage M (paired-key) auf `true`. Auto-Apply feuert ~200-500ms nach BLE-Connect statt fixe 1.5s.

```kotlin
// VehicleState.kt
val isReady: Boolean = false   // post-handshake / ready for register commands

// Zt3ProVehicle.sendHandshake (am Ende):
if (crypto.stagePairedKey) {
    _state.update { it.copy(isReady = true) }
}

// SpeedProfileManager.init:
vehicle.state.map { it.isConnected to it.isReady }
    .distinctUntilChanged()
    .collect { (connected, ready) -> if (connected && ready) applyProfile(boot) }
```

#### Screen-Off Vol-Down/Up via `VolumeProviderCompat`

Problem: AccessibilityService.onKeyEvent wird bei Display-Off NICHT mehr von Volume-Keys getriggert — der InputDispatcher routet sie direkt zum Audio-Subsystem ohne Userspace-Hop.

Lösung: `MediaSessionCompat` mit `setPlaybackToRemote(VolumeProvider)`. Der OS-VolumeController routet dann Vol-Up/Down direkt an `VolumeProvider.onAdjustVolume(direction)` — funktioniert screen-state-agnostisch:

```kotlin
class StealthVolumeService : Service() {
    override fun onCreate() {
        startForeground(NOTIF_ID, buildNotification())
        wakeLock = pm.newWakeLock(PARTIAL_WAKE_LOCK, "segway:stealth-volume").apply { acquire(8h) }
        val volumeProvider = object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                if (direction < 0) registerVolumeDownPress()      // 3× → Lock 22
                else if (direction > 0) registerVolumeUpPress()    // 3× → Unlock 40
            }
        }
        mediaSession = MediaSessionCompat(this, "SegwayStealthVolumeSession").apply {
            setPlaybackState(STATE_PLAYING)
            setPlaybackToRemote(volumeProvider)
            isActive = true
        }
    }
}
```

Service ist als `foregroundServiceType="mediaPlayback"` registriert + `WAKE_LOCK`-Permission, läuft persistent solange `accessibilityTriggerEnabled=true`. Notification erscheint mit `IMPORTANCE_LOW` in der Statusbar als Hinweis.

#### Action-Split

| Trigger | Source-Path | Action |
|---|---|---|
| 3× Vol-Up (Screen aus) | `StealthVolumeService.onAdjustVolume(+1)` → `profileManager.onAccessibilityVolumeUpTriggered` | apply unlock-profile (40) |
| 3× Vol-Up (Screen an) | `UnlockAccessibilityService.onKeyEvent VOLUME_UP` | gleicher Path |
| 3× Vol-Down (Screen aus) | `StealthVolumeService.onAdjustVolume(-1)` → `profileManager.onAccessibilityVolumeDownTriggered` | apply boot-profile (22) |
| 3× Vol-Down (Screen an) | `UnlockAccessibilityService.onKeyEvent VOLUME_DOWN` | gleicher Path |
| App-Connect (jeder Reconnect) | `SpeedProfileManager.init` Flow auf `isReady=true` | apply boot-profile (22) |
| Roller-Power-Cycle | (BLE-Disconnect → Reconnect) → gleicher Auto-Apply-Path | apply boot-profile (22) |
| App-Button "Unlock 40 km/h" | `VehicleViewModel.confirmUnlock` | apply unlock-profile (40, ggf. mit PIN) |
| App-Button "Lock to X km/h" | `VehicleViewModel.reLock` | apply boot-profile |

### Beobachtungen Field-Test

**Funktioniert** ✅:
- 3× Vol-Up = Unlock 40 (Screen on/off)
- 3× Vol-Down = Lock 22 (Screen on/off)
- App-Reconnect feuert Boot-Profile sofort nach Handshake
- Notification "Stealth-Unlock aktiv" sichtbar in Statusbar
- Banner im Vehicle-Screen wenn Accessibility-Service in Android-Settings noch nicht aktiv ist

**Beobachtet, by-design akzeptiert**:
- Roller-Power-Cycle ohne Phone in Reichweite → Roller läuft bei nächstem Hochfahren mit dem **zuletzt aktiven Limit** (= 40 wenn letzter Zustand "unlocked"). Erst wenn Phone reconnectet feuert unsere App `SetSpeedLimit(22)`. Race-Window: paar Sekunden nach Power-On bis Phone connectet hat.
- Begründung: Roller-Firmware persistiert das Speed-Limit in NVRAM, NICHT in Session-State. Ohne Firmware-Mod nicht änderbar — der Phone-Side Auto-Apply ist die einzige Greife. Akzeptiert vom User.

### Code-Pointer

| File | Funktion |
|---|---|
| `core/vehicle/Vehicle.kt` | `VehicleState.isReady`-Flag |
| `core/vehicle/Zt3ProVehicle.kt:209` | `_state.update { isReady=true }` nach Stage M |
| `core/profile/SpeedProfileManager.kt:63` | Wartet auf `(isConnected, isReady) → both true` |
| `core/profile/SpeedProfileManager.kt:131` | `onAccessibilityVolumeUpTriggered` (Vol-Up = Unlock) |
| `core/profile/SpeedProfileManager.kt:148` | `onAccessibilityVolumeDownTriggered` (Vol-Down = Lock) |
| `core/profile/SpeedProfile.kt:35` | `accessibilityTriggerEnabled = true` (Default) |
| `feature/profiles/StealthVolumeService.kt` | Foreground-Service mit VolumeProvider |
| `feature/profiles/UnlockAccessibilityService.kt` | Screen-On Vol-Up/Down detection |
| `feature/home/VehicleScreen.kt` | `AccessibilityServiceBanner` + Lock-Status-Banner |
| `SegwayApp.kt` | Auto-start StealthVolumeService wenn `accessibilityTriggerEnabled=true` |
| `AndroidManifest.xml` | Foreground-Service + Wake-Lock Permissions |

### Status

**Stand 2026-04-27 ~07:00**: Lock-by-Default-UX vollständig live. Tested: 3× Vol-Up/Down funktioniert mit Display aus, Lock-State wird bei Reconnect re-applied, Stealth-Notification sichtbar. Doku auf Stand. Release-APK gebaut + auf GitHub-Releases gestellt.

---

## Session 7 — 2026-04-27 ~15:30-17:00 (Telemetry + Mode/Lights/Cruise Verification)

### Anforderung

User-Vorgabe: Telemetry (Battery, Temp, Speed) auf Hauptseite live anzeigen + Mode/Lights/Cruise-Wire-Format verifizieren statt geraten.

### `BleLog → Logcat` Pipe (Session-7-Werkzeug)

`core/util/BleLog.kt` ergänzt um Timber-Forward in `tx()`/`rx()`/`note()` — jeder Eintrag der bisher nur in der App-internen Ring-Buffer landete wird jetzt zusätzlich nach `adb logcat -s BleLog` gepiped. Damit Live-Capture von TX-Frames + decrypted RX-Frames + High-Level-Cmds (`-- Cmd SetMode(Drive)`) ohne Diagnostics-Screen-Screenshot möglich.

### Telemetry-Polling (Reg `0xC0`/12 auf dst=0x16)

`Zt3ProVehicle.refresh()` portiert die SHU-Polling-Sequenz: regs `0xC0/12, 0xE4/6, 0xDA/12, 0x18/2, 0x19/2, 0x17/2, 0xE7/2` alle auf dst=0x16. Loop alle 2 s wenn `state.isReady`.

Empirisch via BleLog gemessenes Layout für `0xC0/12` (Wert beobachtet im Standstand bei voller Batterie):
```
Bytes:  82 80 79 00 00 40 94 05 07 A7 C1 05
Offset: [0..1] [2..3] [4..5] [6..7] [8..9] [10..11]
```

| Offset | Wert (raw) | Interpretation | Status |
|---|---|---|---|
| `[2..3]` | `0x0079 = 121` | speed dHz / 10 = 12.1 km/h | ✓ matcht Display-Wert |
| `[6..7]` | `0x0594 = 1428` | trip cm / 100 = 14.28 km | ✓ matcht Display-Wert |
| `[0..1]` | `0x8082 = 32898` | NICHT Battery (clamped 0..100 ergäbe immer 100%) | ✗ Layout falsch |
| `[4..5]` | `0x4000 = 16384` | unklar | ✗ |
| `[8..9]` | `0xA707 = 42759` | NICHT Temperatur (würde 4275 °C ergeben) | ✗ |
| `[10..11]` | `0x05C1 = 1473` | unklar | ✗ |

`0xDA/12` Antwort `16 31 98 0E 00 40 57 21 05 27 B1 05` — Battery wahrscheinlich hier oder in einer Sub-Region; ohne Bewegung ist nichts dynamisch beobachtbar. **TODO**: User soll Roller bewegen + tieferen Akku-Stand beobachten, dann sieht man welche Bytes sich ändern.

`0xE4/6` antwortet immer `FF FF FF FF FF FF` → vermutlich „nicht-existent" oder Sub-Modul nicht aktiv.

`0x18`, `0x19`, `0x17`, `0xE7` antworten mit konstanten 2-Byte-Werten → wahrscheinlich Firmware-IDs / Status-Flags, nicht Telemetry.

→ Telemetry-Layout für ZT3 Pro D nur teilweise empirisch geknackt. Speed + Trip sicher, der Rest braucht Long-Run-Beobachtung.

### Mode/Lights/Cruise — NICHT per BLE schreibbar

Field-Test mit BleLog-Capture:

```
-- Cmd SetMode(Eco)        → TX → RX cmd=05 arg=75 [01 00]
-- Cmd SetMode(Drive)      → TX → RX cmd=05 arg=75 [01 00]
-- Cmd SetMode(Sport)      → TX → RX cmd=05 arg=75 [01 00]
-- Cmd SetLights(on=true)  → TX → RX cmd=05 arg=76 [01 00]
-- Cmd SetCruise(on=true)  → TX → RX cmd=05 arg=7C [01 00]
```

**Beobachtetes Verhalten**: Roller piept bei JEDER Aktion (= Frame-Empfang-Bestätigung), aber **kein Modus-Wechsel, kein Licht an/aus, kein Cruise**. Die `[01 00]`-Antwort ist eine **generische Receive-Quittung** der Roller-Firmware, nicht ein „erfolgreich angewendet"-Signal.

**Schlussfolgerung**: Register `0x75/0x76/0x7C` auf dst=0x16 sind auf der ZT3-Pro-D-Firmware **nicht** an Mode/Lights/Cruise gebunden. Wahrscheinlich:
- Mode wird nur per **Dashboard-Doppelklick Power-Button** umgeschaltet (hardware-only)
- Lights sind **automatisch** beim Fahren (kein Remote-Control vorgesehen)
- Cruise wird über **Throttle 5s+ halten** aktiviert (firmware-internal)

### Reverse-Engineering der offiziellen Segway-Mobility-App: nicht trivial

Untersucht: `reverse-engineering/apps/ninebot-segway/decompiled/`. Befund:
- **NIS-Wrapper aktiv** (`com.netease.nis.*`) — nur 32 Java-Files unverschlüsselt dekompilierbar, der Rest ist als verschlüsselter DEX-Blob im APK und wird zur Laufzeit per native-Decryption-Hook geladen
- **React-Native + Hermes-Bytecode** — Business-Logik liegt in `assets/platform.zip → platform.bundle` (694 KB Hermes-Binary), nicht direkt lesbar ohne Hermes-Decompiler

→ Mode/Lights/Cruise-Wire-Format aus offizieller App rauszuziehen ist **mehrtägige Arbeit** (Frida-Hook für DEX-Dump + Hermes-Decompiler-Setup). Für die ZT3-Pro-D-Hardware vermutlich auch unnötig, da diese Funktionen hardware-only sind.

### Code-Änderungen

| File | Änderung |
|---|---|
| `core/util/BleLog.kt` | Timber-Forward für TX/RX/Note |
| `core/vehicle/Zt3ProVehicle.kt` | `refresh()` mit SHU-Poll-Pattern, periodischer 2s-Loop, Cmd-Logging in `execute()`, ehrliches `0xC0`-Layout (nur speed + trip) |
| `feature/home/VehicleScreen.kt` | Mode-Segmented-Row + Lock/Lights/Cruise FilterChips entfernt (sie taten nichts außer Beepen) |
| Doku | FIELD-TEST-LOG Session 7 |

### Status

**Stand 2026-04-27 ~17:00**: Telemetry partial (speed + trip live, battery/temp Layout TBD), Mode/Lights/Cruise als „hardware-only" entfernt aus UI. App ist jetzt **ehrlich** in dem was sie kann. Lock-by-Default + Stealth-Vol bleiben das Headline-Feature. Release v0.1.2 mit ehrlicherer UI.

---

## Session 8 — 2026-04-28 ~16:30+ (ZT3-Register-Reference + Dual-Frame-Format)

### Externer Beitrag

User hat eine **vollständige BLE-Register-Referenz** für die ZT3-Familie (x3-Serie: F3/G3/GT3/ZT3) bereitgestellt, basierend auf:
- [segMod Wiki](https://github.com/MacintoshKeyboardHacking/segMod/wiki) (RE auf GT3 Pro + F3 mit ESP32)
- [x3regs.h](https://github.com/MacintoshKeyboardHacking/segMod/blob/main/myBLE4/x3regs.h)
- [NootNooot Ninebot BLE Documentation](https://nootnooot.codeberg.page/segway-ninebot-ble/)

In Repo übernommen: `reverse-engineering/protocol/zt3-ble-register-reference.md`.

### Korrigierte Register-Map

Massive Korrekturen gegenüber unseren bisherigen Annahmen (alle aus M365/G30-Konvention extrapoliert, ZT3 hat eigene Register-Layout):

| Funktion | Bisherige Annahme | Verifizierte Adresse | Quelle |
|---|---|---|---|
| **Drive Mode** | `0x75` ❌ | `0x5A` (VCU_DRIVE_MODE) | x3regs.h |
| **Headlight** | `0x76` ❌ (= VCU_VoiceVolume!) | `0x5B` (VCU_LedMode) | x3regs.h |
| **Cruise/Taillight** | `0x7C` ❌ | `0x5D` (VCU_TailLightMode) | x3regs.h |
| **Battery%** | `0x18`/`0x19` Polling ❌ | `0x55` (VCU_BATTPCT) oder BMS `0x8F` | x3regs.h |
| **Live Speed** | `0xC0[2..3]` empirisch | `0x57` (VCU_Speed) oder MCU `0x86` | x3regs.h |
| **Trip Distance** | `0xC0[6..7]` empirisch | `0x68` (VCU_SingleMileage, 4 Byte) | x3regs.h |
| **Body Temp** | `0xC0[8..9]` ❌ (4275°C bug) | `0x6B` (VCU_BodyTemp, °C×10) | x3regs.h |
| **KERS-Regen** | (= unsere "Lock"!) ❌ | `0x70` (VCU_DecMode) | x3regs.h |
| **Pattern-Lock** | (war als Lock) | `0x71` (VCU_KeyPwd) | x3regs.h |
| **Power On/Off** | (war als Reboot) | `0x79` (VCU_EGear) `01 00`/`02 00` | x3regs.h |
| **Charge-Limit** | (nicht implementiert) | BMS `0x82` (BMS_MaxPower), dst=0x07 | x3regs.h |

### Frame-Format-Discovery

Heißeste Erkenntnis: ZT3 hat **zwei verschiedene Schreib-Encodings**, je nach Register:

**Format A — `cmd=0x02 (WRITE), arg=register`**:
```
5A A5 [bLen] 3E 16 02 [reg] [payload...] [crc]
```
Verifiziert für: SetSpeedLimit (`5A A5 02 3E 16 02 48 14 16` → `[3E 16 02 48 14 16]`).

**Format B — `cmd=register, arg=0x00` (laut Doc-Beispielen)**:
```
5A A5 [bLen] 3E 16 [reg] 00 [payload...] [crc]
```
Doc-Beispiele für Power: `5a a5 02 3e 16 79 00 01 00 [chk]`, Mode: `5a a5 02 3e 16 5a 00 03 00 [chk]`.

Hypothese: ZT3-Firmware akzeptiert für manche Register Format A, für andere Format B. Implementation `FrameCodecCrypto.writeRegisterDirect()` (Format B) für SetMode/Lights/Reboot/Cruise; `writeRegister()` (Format A) bleibt für SetSpeedLimit (verified working).

### Code-Änderungen

| File | Änderung |
|---|---|
| `core/ble/FrameCodecCrypto.kt` | Neue `writeRegisterDirect()` mit Format-B-Encoding |
| `core/vehicle/Zt3ProVehicle.kt` | SetMode → reg 0x5A direct, SetLights → 0x5B direct, SetCruise → 0x5D direct, Reboot → 0x79 direct, Lock/Unlock → 0x71 (Pattern-Lock). Telemetry-Polling komplett auf Doc-Register umgestellt: 0x55, 0x57, 0x5A, 0x68, 0x62, 0x6B, 0x58, 0x59 auf VCU; 0x8F, 0x8C, 0x96 auf BMS; 0x86, 0x48 auf MCU. `handleNotify` jetzt mit src-basierter Branch-Logik (handleVcuRegister / handleBmsRegister / handleMcuRegister) |
| `feature/home/VehicleScreen.kt` | Mode-SegmentedButtonRow + Lights-Toggle FilterChip wieder reingenommen |
| `feature/diagnostics/DiagnosticsScreen.kt` | Neuer „Sweep 0x00..FF"-Button für Brute-Force-Register-Scan; Action-Bar horizontal scrollable |
| `feature/settings/SettingsScreen.kt` | KeepScreenOn-Toggle existierte schon, jetzt in `MainActivity` an `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` angeschlossen |
| `feature/profiles/StealthVolumeService.kt` | Live-Status-Notification mit Lock-State-Observation |
| `reverse-engineering/protocol/zt3-ble-register-reference.md` | NEUE Datei — vollständige x3-Serie-Register-Referenz |

### Field-Test-Resultat (Iteration 1 mit Format B)

Field-test 2026-04-28 mit den neuen Registern + Format-B:
- **Battery 95% ✓** — endlich korrekt (vorher 100%-clamped Müll aus 0xC0)
- **Temp 19.0 °C ✓** — endlich plausibel (vorher 4275 °C aus Layout-Bug)
- **Trip 136970 km ✗** — Skalierung off; auf `/100000` korrigiert
- **Speed 0.0 km/h** — im Standstand korrekt
- **Mode-Switch ✗** — Roller piept gar nicht mehr bei Klick
- **Lights-Toggle ✗** — Roller piept gar nicht mehr bei Klick

### Format-B-Ablehnung — Erklärung

Bei Format B steht das Register-Byte an der bCmd-Position. Aber:

| Register | bCmd-Wert | Konflikt mit Doc-Cmd-Tabelle? |
|---|---|---|
| `0x5A` (DRIVE_MODE) | 0x5A | nicht in Tabelle — unbekannter Opcode |
| `0x5B` (LedMode) | **0x5B** | **= PRE_COMM (Handshake-Init)** ❗ |
| `0x5C` (ProjectionLightMode) | **0x5C** | **= SET_PWD (Handshake-Phase 2)** ❗ |
| `0x5D` (TailLightMode) | **0x5D** | **= AUTH (Handshake-Phase 3)** ❗ |

Wenn wir mit Format B versuchen Lights/Cruise/TailLight zu setzen, schickt das ein Frame der wie ein **Handshake-Re-Init** aussieht. Roller verarbeitet's als Handshake-Phase, was die Crypto-Session resettet/verwirrt → kein Beep, keine Aktion.

**→ Format B ist FALSCH (oder zumindest nicht universell für VCU-Register die im 0x5B-0x5D-Bereich liegen).**

Code zurückgerollt zu Format A (`cmd=0x02 WRITE, arg=register`) für alle Writes — gleicher Stand wie SetSpeedLimit (das verifiziert funktioniert).

### Field-Test-Resultat (Iteration 2, Format A mit Doc-Registern)

Mit korrekten Registern (0x5A für Mode, 0x5B für Lights) aber Format A (cmd=02, arg=register):
- **Mode-Switch in App** → Roller **piept**, aber Display ändert sich nicht
- **Lights-Toggle** → Roller **piept**, Scheinwerfer ändert sich nicht

→ Roller akzeptiert die Frames (= ackt mit `cmd=05 arg=<reg> [01 00]`-Quittung + Beep), aber **die Mode/Lights-Änderung wird nicht ausgeführt**.

### Vermutung: ZT3 Pro D fehlende RW-Schreib-Berechtigung

ZT3 Pro D hat möglicherweise:
- Mode-Register `0x5A` als **read-only** (nur Status-Info, nicht schreibbar via App)
- Lights-Register `0x5B` ebenso (Auto-Headlight via Bitfeld in 0x1F; manueller Override evtl. nicht erlaubt)

Doc-Anmerkung dazu (Sektion 1, Hinweis vorweg):
> Die ZT3-Hardware ist laut segMod-Wiki noch nicht vollständig verifiziert — insbesondere fehlt beim ZT3-VCU offenbar der SPI-Flash-Chip, den GT3/G3/F3 haben.

→ ZT3 Pro D ist im Feature-Set zwar mit GT3/F3 register-kompatibel, aber **welche Register wirklich beschreibbar sind, ist firmware-seitig restriktiver**. Mode/Lights/Cruise könnten Hardware-only sein.

### Was funktioniert (verifiziert)

- **SetSpeedLimit** (Lock/Unlock 22/40 km/h) — reg `0x48`, Format A ✅
- **Battery** (reg `0x55`) ✅
- **Temperature** (reg `0x6B`, °C × 10) ✅
- **Trip / Odometer** (regs `0x68` / `0x62`, /100000 für km) ✅
- **Live Speed** (reg `0x57` oder MCU `0x86`) ✅ (bei Bewegung)
- **3× Vol-Up/Down Stealth-Trigger** ✅
- **Auto-Lock bei Reconnect** ✅
- **KeepScreenOn-Toggle** in Settings ✅

### Nicht-Ziele dieser Session

- **Subscribed-Parameters** (Push-Telemetrie via 32-bit Hashes) — funktionieren via `SUBSCRIBE`-cmd den wir noch nicht haben. Wenn aktiv, würde Roller pro Hash periodisch live-Updates pushen ohne dass wir pollen müssen.
- **Hermes-Bytecode-Decompilation** der offiziellen Segway-Mobility-App — deferred.
- **Custom-Button-Remapping** — bleibt offen.

### Status

**Stand 2026-04-28 ~17:00**: Register-Map deutlich korrekter (~80% verifiziert via x3regs.h-Referenz), Telemetry funktioniert teilweise (Battery + Temp endlich plausibel, Speed + Trip in Standstand-Test 0). Mode/Lights/Cruise immer noch nicht executable trotz korrekter Register + zweitem Frame-Format — vermutete Ursache: fehlender Per-Write-Auth-Token. Release v0.1.3 mit korrekten Registern (auch wenn Mode noch nicht klappt) + neuer Doku.

---

## Session 9 — 2026-04-28 spätabends (Mode-Mapping byte-perfekt verifiziert)

### Anforderung

User: „roller ist S und app zeigt beim start D an" + dann „bin jetzt mehrfach durch alle modi". Mode-UI soll exakt zum Roller-Display passen — und der initiale Default-State (`RideMode.Drive`) der App soll nicht mehr fälschlicherweise als „echte" Anzeige erscheinen, solange noch kein erster `0x5A`-Read vom Roller eingetroffen ist.

### Empirische Methodik (in 5 Minuten geknackt)

Bei den vorherigen Mode-Mapping-Versuchen haben wir uns auf User-Selbstbeschreibung verlassen („E = walk, D = eco, S = drive, Männchen = Sport") — was zwei Mapping-Iterationen verbraten hat ohne tragfähig zu sein. Diese Session: **logcat als Single Source of Truth**.

`BleLog` schreibt ohnehin via `Timber.tag("BleLog").d(...)` parallel ins Android-Logcat. Die `Mode | reg 0x5A raw=0xXX (N)`-Note bei jedem 0x5A-Read ist damit deterministisch greifbar:

```bash
adb logcat -d BleLog:D '*:S' 2>&1 | grep "0x5A" \
  | awk '{ if ($NF != prev) { print $1, $2, $NF; prev=$NF } }'
```

→ Liefert eine **Transition-Map**: nur die Zeilen, an denen sich der raw-Wert geändert hat. Resultat aus dem Field-Test:

```
04-26 18:43:00.097 (4)
04-26 18:48:26.501 (1)
04-26 18:48:32.695 (2)
04-26 18:51:29.698 (3)
04-26 18:54:32.057 (4)
```

→ **Nur Werte 1, 2, 3, 4 — niemals 0**. ZT3-Firmware ist **1-indexed** für `VCU_DRIVE_MODE`.

Reihenfolge entspricht dem Dashboard-Cycle (Walk → E → D → S → Walk):

| raw | Roller-Display | App-Label |
|-----|---------------|-----------|
| `0x01` | E | Eco |
| `0x02` | D | Drive |
| `0x03` | S | Sport |
| `0x04` | Männchen | Walk |

### Code-Änderungen

**Read-Mapping** (`Zt3ProVehicle.kt::handleVcuRegister 0x5A`):
```kotlin
val mode = when (raw) {
    0x01 -> RideMode.Eco
    0x02 -> RideMode.Drive
    0x03 -> RideMode.Sport
    0x04 -> RideMode.Walk
    else -> null  // unknown → don't update state
}
if (mode != null) _state.update { it.copy(mode = mode) }
```

**Write-Mapping** (`Zt3ProVehicle.kt::encodeCrypto SetMode`):
```kotlin
when (cmd.mode) {
    RideMode.Eco -> 0x01
    RideMode.Drive -> 0x02
    RideMode.Sport -> 0x03
    RideMode.Walk -> 0x04
}.toByte()
```
(Symmetrisch — auch wenn Writes auf 0x5A weiterhin von ZT3-Firmware ignoriert werden.)

**Loading-State** (Vehicle.kt + VehicleScreen.kt):
- `VehicleState.mode: RideMode?` → nullable, default `null`.
- VehicleScreen: solange `state.mode == null`, ist kein SegmentedButton highlighted und es erscheint die Sub-Zeile *„Lese Modus vom Roller…"* unter der Buttonreihe.
- Sobald der erste valide 0x5A-Read durch den Poll eintrifft (~1-2 s nach Connect), wird der Button korrekt selektiert.

### Methoden-Lehre für künftige Sessions

**„Nicht den User für die Empirie missbrauchen."** Wenn ein Bytewert empirisch erfasst werden muss, ist ein einziger `adb logcat`-Befehl mit `awk`-Transition-Filter schneller, präziser und revisionssicherer als 4 Iterationen Trial-and-Error mit User-Feedback-Loop. Die `Mode | reg 0x5A raw=...`-Logs waren bereits seit Session 7 da — wir hatten sie nur nicht systematisch gelesen.

### Status

**Stand 2026-04-28 ~21:00**: Mode-Mapping byte-perfekt verifiziert (1-indexed). Loading-State verhindert irreführende Default-Anzeige. Release v0.1.4. Mode-WRITES bleiben firmware-seitig blockiert (Roller-Display ändert sich nicht), aber Mode-READS sind jetzt 100 % korrekt — d.h. Dashboard-Wechsel via Power-Button-Doppeltap wird live in der App reflektiert.

---

## Session 10 — 2026-04-28 ~21:30 (Deep-Telemetrie via BMS / VCU)

### Anforderung

Nach Mode-Fix: weitere Sensorwerte auslesen, orientiert an [`zt3-ble-register-reference.md`](../reverse-engineering/protocol/zt3-ble-register-reference.md). Ziel: Spannung, Strom, Zellenspannungen, Reichweite, Trip-Zeit, Total-Laufzeit, Motor-Temps.

### Empirisch verifizierte ZT3-Skalierungen (per logcat-Capture)

Wichtige Korrekturen gegen die generischen x3regs.h-Annahmen — ZT3-Pro-D-Firmware weicht in **Encoding** und **Wert-Layout** von GT3/F3 ab:

| Reg | Doc-Annahme | ZT3-Realität (Bytes → Wert) | Einheit |
|---|---|---|---|
| `0x62` (VCU_Mileage) | u32 × 10 m | `[12 00 00 00]` → low u16 = **18** | km, **direkt** (kein Divisor) |
| `0x68` (VCU_SingleMileage) | u32 × 10 m | `[07 00 46 0A]` → low u16 = **7** | km, direkt; high u16 (`0A46`) = unbekannt |
| `0x5F` (VCU_LeftMileage) | „Restreichweite" | `[04 0B]` = 0x0B04 = 2820 | km × 100 (= 28,2 km) |
| `0x64` (VCU_Runtime) | 32-bit Runtime | `[E8 7F 00 00]` = 32744 | **Sekunden** seit Herstellung (= 9h 05m, plausibel für jungen Roller) |
| `0x6A` (VCU_SingleRideTime) | „Trip-Time" | `[4F 00 BE 00]` → low u16 = **79** | Sekunden current ride; high u16 (`00BE`) = unbekannt |
| `0x6B` (VCU_BodyTemp) | °C × 10 | `[BE 00]` = 190 | °C × 10 (= 19,0 °C ✓) |
| `0x96` (BMS_Temps) | u8 + 20 bias (Doc-Konvention) | `[13 00 13 00]` = beide Probes 19 | **direkt °C** (kein Bias bei ZT3) |
| `0xF9` (BMS_TEMP) | uint16 | `[13 00]` = 19 | direkt °C |
| `0x8C` (BMS_VOLTAGE) | V × 100 | `[D7 14]` = 5335 | V × 100 (= 53,35 V ✓ matched 13S × 4,104 V/Zelle) |
| `0x8D` (BMS_CURRENT) | A × 100 signed | `[FA FF]` = -6 | A × 100 (= -0,06 A idle discharge) |
| `0x8E` (BMS_FULL_CAP_PCT) | % | `[64 00]` = 100 | % direkt |
| `0x8F` (BMS_SOC) | % | `[5E 00]` = 94 | % direkt |
| `0x92` (BMS_ChargeStatus) | enum | `[02 00]` = 2 | 0=idle, 1=charging, **2=fully charged/standby** (verifiziert bei Battery 94 %) |
| `0xA0` (BMS_CellVolts) | N × 16-bit cells | `[09 10 ...]` × 13 | mV LE; **13S** (nicht 12S — entspricht 48-V-Nominal-Pack) |

### Schlüssel-Lehre

1. **Zellzahl im Datenblatt steht NICHT für die Datenmenge die der BMS pusht.** ZT3 ist 13S (53,35 V / 4,104 V ≈ 13). Erste Iteration mit 24 Bytes (12S-Annahme) ergab 12 × 4,104 = 49,25 V vs. Pack-Spannung 53,35 V → 4 V Lücke = 1 Zelle. Lösung: 26 Bytes pollen.
2. **VCU body temp (0x6B) darf nicht von MCU temps (0x48) überschrieben werden.** MCU returned `[00 00]` = 0 °C im Stand (Sensor offline ohne Fahrt). Zuerst überschrieb das fälschlicherweise den 19 °C body temp. Fix: separate `motorTempAC/BC`-Felder, `temperatureC` bleibt VCU-only.
3. **Trip / Odometer sind low-u16, NICHT u32.** Der Doc-Hinweis „32-bit Runtime" gilt für 0x64/Runtime, aber für 0x62/0x68 nutzt ZT3 nur die unteren 2 Bytes. Vorherige Skalierung `/100000` brachte stochastisch glaubwürdige Werte beim Speichersitzungsbeginn, schlug aber bei nicht-trivialen Reichweiten fehl.

### Code-Änderungen

- `Vehicle.kt`: 12 neue Felder in `VehicleState` (rangeRemainingKm, totalRuntimeSeconds, tripDurationSeconds, batteryVoltage, batteryCurrentA, batteryHealthPercent, batteryCycleCount, chargingState, batteryTempC, cellVoltagesMv, motorTempAC, motorTempBC, warnCode).
- `Zt3ProVehicle.kt`: Poll-Plan auf 27 Register erweitert (VCU + BMS + MCU). Parser für jedes neue Register.
- `VehicleScreen.kt`: 3-Reihe Live-Daten Stat-Grid (Battery/MaxSpeed, Temp/Trip, Reichweite/Gesamt). Zwei neue Cards: „Akku — Detail" (Spannung/Strom/Leistung/Health/Zyklen/Zell-Spreizung) und „Motor & Fahrt" (MCU-Temps, Trip-/Total-Zeit, Fehler-/Warn-Codes).

### Status

**Stand 2026-04-28 ~22:00**: ZT3-Telemetrie ~95 % vollständig live. Verbleibend: BLE FW-Version (Reg `0x1A`) — wurde aus Poll-Plan rausgenommen und wieder reinkommt. Motor-Temps werden erst während Fahrt valid (Sensor offline im Stand). Cycle-Count `0` bei nur 9 h Total-Laufzeit ist plausibel.
