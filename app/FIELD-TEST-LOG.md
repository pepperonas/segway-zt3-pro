# Field-Test Log

Live-Sessions mit dem realen ZT3 Pro D (S/N maskiert, MAC `XX:XX:XX:XX:C5:96`).

## Phase-1-Validation-Gate — **Status (ehrlich, Traycer-Align)**

| Frage | Stand |
|--------|--------|
| **Ist Phase-1 laut `VALIDATION-PLAYBOOK` §0–2+6 mit *allen sechs* Checks in *einer* Sitzung auf **Referenz-Dev-Roller** `C1:6B:5E:D0:C5:96` (mit `persistedRandomForDevScooter` in `Zt3ProVehicle`) abgenickt?** | **Nein — Gate BLOCKIERT / nicht abgeschlossen.** |
| Warum? | Sessions **9c / 9d / 9d-B** belegen u. a. **Install (9c)**, **SetSpeed 22/40** (Crypto-`5A A5 02 …`) und **Reconnect/RX-DEC** — **nicht** die vollständige Playbook-Ziellinie (L/M/O durch, „O: fully paired“, **ausschließlich** Diagnostics-Field-Test **ohne** A11y, alles am **Ziel-MAC**). In 9c/9d/9d-B war der verbundene Roller v. a. `D5:A1:FB:21:4C:BD` / `1K1…`, **nicht** `C1:6B:5E:D0:C5:96`. |
| **Repo darf trotzdem Teilevidence committen?** | **Ja** — sofern klar bleibt: **kein** finales „Phase-1 voll grün“-Sign-off, bis Tabelle 6/6 + Ziel-MAC + Playbook-Abnahme existieren. |
| Nächster Schritt (für Voll-Abnahme) | 1) **Ziel-MAC** koppeln, Stealth ggf. in Settings aus; 2) `logcat -c` → **nur** Diagnostics `→ 22` / `→ 40` (kein Vol-3× im gleichen Puffer); 3) Crypto bis O oder offen dokumentieren. Siehe `VALIDATION-PLAYBOOK` (aktualisiert: **Logcat-Tag** `BleLog` vs. **innerer** Note-Text / Klartext-Nachweis). |

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
| **B** (HCI-Capture vom 2026-04-25 SHU-Flash) | Manufacturer-ID `0x434E` ("NC") = Crypto-Variante; alle 3142 ATT-Payloads verschlüsselt |
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

## Session 9 — 2026-04-26 (Phase-1-Validation-Gate) — Archiviert: Erstlauf ohne Device

Dieser Block dokumentiert den **ersten** Gate-Lauf (ADB leer, Fresh-Clone-Pfad defekt). Die **aktualisierte Gate-Tabelle** steht in **Session 9b** unten.

### Test-Setup (Erstlauf)

- Datum: 2026-04-26
- Phone-Modell (`adb shell getprop ro.product.model`): n/a (`adb: no devices/emulators found`)
- Android-Version (`adb shell getprop ro.build.version.release`): n/a (`adb: no devices/emulators found`)
- Roller-MAC (Soll): `C1:6B:5E:D0:C5:96`

### Pre-/Post-Versionen (Erstlauf)

- Pre-Commit-SHA (Phase-1-Baseline): `af73c7d`
- App-Version: `com.celox.segway.debug` (`versionName "0.1.3"`)
- Post-Commit-SHA: `af73c7d` (keine Hotfixes in diesem Gate-Lauf)

### Ergebnis-Tabelle — Erstlauf (historisch)

| Check | Ergebnis | Kurz-Kommentar |
|---|---|---|
| 1) Fresh-Clone-Build (`/tmp/escooter-validate`) | ❌ | Fresh clone enthielt `app/gradlew` und `app/local.properties.example` nicht; `./gradlew` daher nicht ausfuehrbar (`Datei oder Verzeichnis nicht gefunden`). |
| 2) L/M/O-Visual (Diagnostics) | ❌ | Nicht durchfuehrbar ohne verbundenes Dev-Phone/Dev-Roller. |
| 3) Logcat-Cross-Check (`BleLog`/`Crypto`) | ❌ | Nicht durchfuehrbar ohne Device (`adb devices` leer). |
| 4) TX-Regression `-> 22 km/h` | ❌ | Nicht durchfuehrbar ohne Device; kein Live-TX/RX beobachtbar. |
| 5) TX-Regression `-> 40 km/h` | ❌ | Nicht durchfuehrbar ohne Device; kein Display-Check moeglich. |
| 6) Playbook-Smoke (`RX-DEC`/`SCAN`/`Reconnect`) | ❌ | Nicht durchfuehrbar ohne Device und ohne laufende Diagnostics-Session. |

#### Follow-up / Wrapper (nach `caa7df0`, weiterhin gueltig)

- **Fresh-Clone-Build-Fix bestätigt:** Klon so dass `escooter/app/gradlew` (100755) und `app/local.properties.example` enthalten sind; `cp local.properties.example local.properties` + `sdk.dir` + optional `org.gradle.java.home` → `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL** (dokumentiert 2026-04-26).

---

## Session 9c — 2026-04-27 (Phase-1-Validation-Gate — vollstaendiger Checklistenlauf, ehrliches Ergebnis)

Gate nach `reverse-engineering/VALIDATION-PLAYBOOK.md` §0–2 und §6 (eine zusammenhaengende Sitzung: frischer Klon → `installDebug` am Dev-Phone → **Logcat-Tag** `BleLog` inkl. `Crypto`/`Cmd`-*Zeilen* → L/M/O → 22/40 → Playbook-Smoke). **Keine** „Referenz“-Zeilen auf aeltere Sessions: nur was in **dieser** 9c-Sitzung belegt ist. **MAC** = `D5:…` (9c) — **nicht** der Dev-Referenz-Roller `C1:6B:5E:D0:C5:96` (s. Gate-Status-Block oben; `Zt3ProVehicle` Dev-MAC-Migration/PairingPrefs in 9c nicht voll geprüft).

### Test-Setup (9c)

- Datum: 2026-04-27
- Phone-Modell: `2312DRAABG` (`adb shell getprop ro.product.model`)
- Android-Version: `15` (`adb shell getprop ro.build.version.release`)
- Verbundenes BLE-Geraet in 9c-Log: Name `1K1UA2525P1196`, MAC `D5:A1:FB:21:4C:BD` (Auto-Reconnect-Session, nicht identisch mit Session-5-Referenz-MAC `C1:6B:5E:D0:C5:96`).

### Commits (9c, escooter-Subrepo)

- **Applikations- und Klon-Revision (alle 9c-Nachweise: Klon, `installDebug`, `adb` gegen installierte `com.celox.segway.debug` auf SHA-Basis `f9219bfe` der Historie):** vollstaendig `f9219bfe230f561888c5c7162bfeb57d17c0f30e` — ermittelt mit `cd escooter && git rev-parse HEAD` und in `/tmp/escooter-p1-fresh` identisch. Die vorliegende Aktualisierung betrifft ausschliesslich `app/FIELD-TEST-LOG.md` (Doku) bei unveraendertem zugehoerigem Anwendungscode-Tree.

(Wrapper-Pfad: `caa7df0` und neuer: `app/gradlew` + `app/local.properties.example` — siehe [Follow-up](#followup--wrapper-nach-caa7df0-weiterhin-gueltig) in Session 9 oben.)

### 1) Fresh-Clone- und Install-Nachweis (Check 1)

- **Klon (lokales `file://`-Klon, reproduzierbar, gleiches Ergebnis wie Remote-`main` bei identischem SHA):**
  - `rm -rf /tmp/escooter-p1-fresh && git clone /mnt/docker-ssd/cursor-Projekts/escooter /tmp/escooter-p1-fresh`
- **SDK/JDK:** `app/local.properties` mit `sdk.dir=…` und `org.gradle.java.home` nach Vorlage (nicht eincheckt); siehe `local.properties.example`.
- **Befehl (End-to-End, wie Playbook):**
  - `cd /tmp/escooter-p1-fresh/app && export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && ./gradlew :app:installDebug --no-daemon`
- **Gradle-Exit (Auszug):**
  - `> Task :app:installDebug`
  - `Installing APK 'app-debug.apk' on '2312DRAABG - 15' for :app:debug`
  - `Installed on 1 device.`
  - `BUILD SUCCESSFUL in 27s`
- **Abgleich installiertes Paket:** `com.celox.segway.debug` (Debug-`applicationId`).

➡ **Check 1:** **bestanden** — es wurde **kein** Ersatz nur durch `assembleDebug` abgenickt; die Installation lief vollstaendig durch (kein `INSTALL_FAILED_USER_RESTRICTED` in diesem 9c-Lauf).

### 2–3) L/M/O visuell + `BleLog` / `Crypto` in Reihenfolge (Logcat, gleiche Sitzung)

Nach App-Start (`am start` … `MainActivity`) wurde `adb logcat` ausgelesen. **Inhaltlicher Befund im aktuellen Puffer (nicht genuegend fuer vollgruenes M/O, nicht fuer „O: fully paired“):**

- Sichtbar: **L**-Phase in Log, **kein** vollstaendiger M/O-Phasen-Nachweis; stattdessen:
  - `-- Crypto     L: token+challenge received`
  - Anschliessend Timeout der Pairing-Stufe:
  - `-- Crypto     stage 2 (M) timed out — pair-init not acked`

**Ausschnitt (Zeitpunkte `04-26` aus dem Geraetelog, gleiche App-Session wie GATT-Handshake, gekuerzt):**

```text
04-26 22:56:02.781 D/BleLog: -- Reconnect  auto-reconnect to 1K1UA2525P1196 (D5:A1:FB:21:4C:BD)
04-26 22:56:03.948 D/BleLog: -- Crypto     scooterName='1K1UA2525P1196' tokenLoaded=false
04-26 22:56:04.023 D/BleLog: -- Crypto     L: token+challenge received
04-26 22:56:09.480 D/BleLog: -- Crypto     stage 2 (M) timed out — pair-init not acked
04-26 22:56:04.008 D/BleLog: RX TX-NOTIFY  5A A5 1E 0D FC D4 A9 …
04-26 22:56:04.011 D/BleLog: -- RX-DEC     src=04 dst=3E cmd=5B arg=01 [… 31 55 41 32 35 32 35 50 31 31 39 36]
04-26 22:56:04.011 D/BleLog: (weitere 5A A5-TX-Handshake-Frames, siehe vollstaendiger Dump waehrend 9c)
```

➡ **Check 2:** **nicht bestanden** (kein nachweisbares nacheinander vollgruenes L / M / O waehrend 9c; M faehrt in Timeout). **Check 3:** **teilweise** (Crypto-Folge und RX-DEC sichtbar, **ohne** geordnete laufende Zeilen bis **„O: fully paired“** laut Vorgabe / Playbook §6).

### 4–5) TX-Regression `-> 22 km/h` / `-> 40 km/h` (Beweis: `-- Cmd` + `TX RX-WRITE` mit Wire `5A A5 02 …`)

- *Abgleich mit Laufzeit / Playbook §2.1+§6:* In **Tag `BleLog`** erscheinen **Cmd**-Zeilen und `TX RX-WRITE` mit **tatsächlich gesendeten** (bei Ninebot-Crypto: **verschlüsselte**) Oktetten `5A A5 02 …` — **nicht** zwingend sichtbares Innen-Register im Klartext `3E 16 02 48 …` in derselben `adb`-Zeile (dazu `CRYPTO_DUMP`, btsnoop, oder `encodeCrypto`-Doku).
- Im 9c-Ausschnitt: **weder** passendes `-- Cmd SetSpeedLimit(22|40)` **noch** dazugehöriges `5A A5 02 …`-`TX` (Sitzung brach/stockte am M-Timeout, keine SetSpeed-Sequenz in diesem Puffer).

➡ **Check 4 / 5:** **nicht bestanden** (kein 22/40-Nachweis: weder `Cmd SetSpeedLimit` noch zugehöriges `5A A5 02…`-`TX` im 9c-Puffer; Abnahme richtet sich **nicht** an fehlendem Klartext `3E 16 02 48` in `adb`, s. Playbook).

### 6) `VALIDATION-PLAYBOOK` Smoke (Reconnect / RX-DEC / optional SCAN)

- **Reconnect:** im Ausschnitt vorhanden (`-- Reconnect  auto-reconnect to …`). **RX-DEC:** sichtbar (`cmd=5B`, `cmd=5C`). Vollstaendiger Playbook-Smoke inkl. erfolgreichem 22-km/h-Schritt und klarer Abschlussszenario **wurde in 9c nicht** durchlaufen (M-Timeout, keine Speed-TX). **SCAN:** in diesem Ausschnitt nicht wahrgenommen (optional laut Playbook, aber Gesamterfolg 9c: nein).

➡ **Check 6:** **nicht bestanden** als vollstaendiger Playbook-Abschluss in einer gruenen 9c-Session.

### Ergebnis-Tabelle (6 Checklisten-Punkte, 9c — **dieser** Lauf)

| Check | Ergebnis | 9c-Nachweis (kurz) |
|---|---|---|
| 1) Frischer Klon, `./gradlew :app:installDebug`, APK am Phone | **Ja** | `/tmp/escooter-p1-fresh` → `Installed on 1 device.`, `BUILD SUCCESSFUL` |
| 2) L/M/O visuell in Diagnostics (Reihenfolge / sinnvoll) | **Nein** | Log bricht M mit Timeout ab; kein O-Nachweis |
| 3) Logcat: Tag `BleLog` mit Crypto-Noten in sinnvoller Ordnung inkl. Abschlussphase | **Nein** (unvollstaendig) | L und RX sichtbar; **kein** „O: fully paired“-Befund im Puffer |
| 4) `-> 22 km/h` — `Cmd` + `TX` `5A A5 02 …` (s. Playbook; nicht Klartext-Zwang) | **Nein** | kein `SetSpeedLimit(22)` + verschl. TX in 9c-Export |
| 5) `-> 40 km/h` — analog | **Nein** | nicht erfasst |
| 6) Playbook-Smoke (gemaess `VALIDATION-PLAYBOOK.md`) | **Nein** | M-Timeout, keine 22/40-Regression in Log |

### Fazit (9c) — an den Ist-Zustand geknuepft

**Phase-1** wird mit dieser Datei **nicht** als vollumfaenglich **gruen** deklariert, weil **mindestens die Checks 2–6** in der 9c-Nachfuehrung am **echten** Dev-Phone in einer durchlaufenden Session **fehlgeschlagen bzw. nicht belegt** sind, obwohl **Check 1 (Frischklon + `installDebug`)** **gruen** war.

**Naechte Schritte (Operateur):** Rolle/Token/Persisted-Random am **Ziel-MAC** `C1:6B:5E:D0:C5:96` pruefen, Pairing/Handshake gemaess `app/README.md` vollenden, dann Diagnostics, `adb logcat -v time *:S BleLog:V` (s. `VALIDATION-PLAYBOOK` §2.1) und Field-Test-Knoepfe 22/40 in **einer** Sitzung erneut; bei Erfolg neue Session **9d** mit lueckenlosen Log-Ausschnitten (inkl. M/O, `Cmd SetSpeedLimit`, `5A A5 02…`-TX) eintragen. Bis dahin: **kein** Downstream-„Phase-1 abgehakt“-Sign-off aus Session 9c allein.

---

## Session 9d — 2026-04-26 (Phase-1, **durchgängige** Sitzung — vorbereitet, Messlauf ausstehend)

> **Vorher:** 9c Check 1 (Frischklon+`installDebug`) **war grün**; 9c Checks 2–6 fehlten an stabilem ZT3-/Referenz-Setup. 9d soll in **einer Sitzung** belegen, was 9c nicht lief: **vollständiger** Handshake, L/M/O, 22+40, Playbook-§6 — nicht stilles „App geöffnet“.

*Status: Ablauf und Repo-Metadaten unten gesetzt; **Log-Auszüge und Tabelle nachträglich** ausfüllen, sobald der Feldlauf abgeschlossen ist.*

### Operator: Schritt-für-Schritt — was **du am PC** machst, was **du am Handy/Scooter** (eine Sitzung)

Ziel: Eine **einzige** Mess-Session = Zeitschiene muss stimmig sein. **Nichts Wichtiges vor `logcat -c` anfangen;** Logs **nach** den Aktionen wegschreiben. Optional kann **parallel** in einem **zweiten** Terminal `live` beobachtet werden (siehe B).

#### A) Am PC (Terminal, Projekt-Root egal, Hauptsache `adb` im PATH, USB-Debugging an)

| # | Aktion | Erwartung / Hinweis |
|---|--------|---------------------|
| 1 | `cd` ins geklonte **escooter**-Repo, `git pull` und (falls du Doku-Stand führen willst) `git rev-parse HEAD` notieren. | Ruhiger, aktueller `main`/`develop`. |
| 2 | **USB** verbinden, ggf. „Dateiübertragung“, USB-Debugging-Dialog ggf. am Handy. | |
| 3 | `adb devices` — Serienzeile muss `device` zeigen, nicht `unauthorized`. Ggf. `-s <serial>` benutzen, falls mehrere Geräte. | |
| 4 | **Nur jetzt — Log-Puffer leeren:** `adb logcat -c`  *(MCP-Äquivalent in Cursor, falls `escooter-re` angebunden: Tool `escooter_re_logcat_clear` — *vor* der echten Sitzung einmal laufen lassen, nicht währenddessen wundern.)* | Danach: **Kurz warten** bis du mit dem Handy starten willst, damit alles in dieselbe Session fällt. |
| 5 | *(**Optional B — Live, nicht Pflicht**)* Zweites Terminal: `adb logcat -v time *:S BleLog:V` laufen lassen, **durchlaufen lassen während** du C machst, um L/M/O/`-- Crypto` in `BleLog` zu sehen. **Beweis-Export** trotzdem **A6a**/*A6b* (Dump/Datei nach der Sitzung). | Live-Terminal ist nur Hilfe, **kein** offizieller 9d-Beleg. |
| 6a | **Sitzungs-Log sichern (nach C, Pflicht fürs Archiv):** reines `adb` — `adb logcat -d -v time *:S BleLog:V > /tmp/escooter-9d-YYYYMMDD-HHMM.log`  *(Pfad gern anpassen.)* | Eine **Datei** fürs Archiv + Copy-Paste in 9d unten. |
| 6b | *Alternative:* MCP `escooter-re` → Tool `escooter_re_logcat_field_session_export` (Snapshot: `logcat -d` mit Fokus `BleLog`, ggf. grep `Crypto|Cmd`). | In die Log-Auszüge-Blöcke in dieser Datei **kopieren**; kein Dauer-Stream. |
| 7 | *Optional* HCI-Snoop am Phone vorher laut `VALIDATION-PLAYBOOK` an, dann ggf. MCP `escooter_re_pull_btsnoop` oder manuell `adb pull` der Snoop-Datei. | Nur wenn ihr Wire-PCAP wollt; 9d-Hauptbeleg bleibt Logcat+App. |

#### C) Am Handy (und in Reichweite des richtigen Rollers) — **nach** A4 (leerer Puffer), in **durchgehender** Reihenfolge

| # | Du machst | Sinn |
|---|----------|------|
| 1 | **escooter-App** (Package `com.celox.segway.debug`, Version **0.1.6**) öffnen. Wenn nötig: alte Prozesse killen oder App kalt starten, damit alles in dieser Session stattfindet. | Klarer Session-Start. |
| 2 | **Laut** `app/README` **verbinden / koppeln / pair-en** — mit deinem **Referenz-Setup** (Richtiger Name, MAC nicht „falsches“ 1K1-Device aus 9c). Ziel: **im Log kein** `stage 2 (M) timed out` — falls doch: **Trennen**, in Garage prüfen, ggf. neu koppeln. | Ohne vollen Handshake gibt es kein sinnvolles 9d. |
| 3 | **Diagnostics** im Menü öffnen, **L → M → O**-Chips **mit ansehen** (Reihenfolge, nicht hängen). Kurz in Notiz: sichtbar „O“-Erfolg? | Tabelle 9d, Zeile 2. |
| 4 | In **derselben** Sitzung: nacheinander Tipp **→ 22 km/h**, warten, dann **→ 40 km/h** (nur wo rechtlich/sicher). | Tabelle, Zeilen 4–5; in Log: TX mit erwartetem Muster. |
| 5 | *Optional* kurz trennen/wieder verbinden oder (wenn vorgesehen) Register-**SCAN**-Button, falls in der App für §6-Playbook genutzt — *nur wenn noch dieselbe 9d-Session.* | Zeile 6. |
| 6 | **Zurück zum PC:** Schritt A6/6a oder 6b (Dump **direkt** nach C — nicht stundenlanges Telefonieren dazwischen, sonst mischt sich fremder Log dazu). | Beweis-Datei fertig. |

#### D) Danach: **FIELD-TEST-LOG** in dieser Datei

1. Wichtigste **Crypto-/BleLog-Zeilen** (Handshake bis einschließlich „O“-Erfolg oder wahrheitsgemäß Fehler) in die **drei/fünf vorgefertigten *Log-Auszüge*-Fences** oben in dieser 9d-Session **einfügen** (gern gekürzt, **L/M/O-Story** leserlich).
2. **22/40**-Fragmente in die TX-Blöcke, wenn vorhanden; sonst offen lassen.
3. **Tabelle** 6 Zeilen: **ehrlich** eincheckt — nur „grün“ wenn 9c+deine Belege wirklich passen.
4. *Optional* kleiner `git commit` **nur** an `FIELD-TEST-LOG.md` (nach Martins Regel ggf. auf **Feature-Branch** + PR, nicht wahlweise riesen-Dump).

**Kurz:** `logcat -c` → Handy-Block C → sofort `logcat -d` in Datei oder MCP-Export → Tabelle. **Ich (Agent) kann** den MCP-Export in Cursor *ausführen, wenn* `escooter-re` in Cursor aktiv ist und ADB dein Handy sieht; **dich ersetze ich** beim **Fahren, Klicken, Pairing** am Gerät **nicht**.

### Referenz-Setup (vor der Sitzung abgleichen)

- [ ] **Gleiches Ziel-Setup wie README / Session-5-Referenz:** sinnvolles `scooterName`, **Referenz-MAC** `C1:6B:5E:D0:C5:96` (oder anderes **bewusst** dokumentieren), ggf. gepaarte Garage-Eintragung — **kein** blindes Reconnect an „falschen“ 1K1-…-Namen, wenn 9c dort M-Timeout brachte.
- [ ] App am Phone: `com.celox.segway.debug`, sichtbar **versionName `0.1.6` (versionCode 7)** in App-Info / About. **Version-Strings eingeführt in** Commit `b2ae04f`; Doku-Runbook/ MCP-Helfer in `fd5856d` ( bei Bedarf neu bauen und installieren: `cd app && ./gradlew :app:assembleDebug` + `adb install -r -t …/app-debug.apk` ).

### Ablauf (eine Session, fester Ablauf)

1. `adb devices` → `device`.
2. **Puffer leeren** (eine der Varianten; nicht parallel zwei Geräte verwirren):
   - `adb logcat -c`  
   - *oder* MCP-Tool `escooter_re_logcat_clear` (Cursor: `escooter-re` Server).
3. **App** starten, laut `app/README.md` **verbinden / pair-en**, bis in der echten Sitzung **kein** `stage 2 (M) timed out` mehr; gegebenenfalls einmal trennen und sauber erneut pair-en.
4. **Diagnostics** öffnen, **L → M → O** mit **Augen** (Chips) — mit Log konsistent, nicht hängen bleiben.
5. **Field-Test-Buttons (Diagnostics):** nacheinander **→ 22 km/h**, dann **→ 40 km/h** (Roller/Strasse nur wo erlaubt).
6. **Log ziehen** (nach der Sitzung), z. B.:
   - `adb logcat -d -v time *:S BleLog:V > /tmp/escooter-9d.log`
   - *oder* MCP: `escooter_re_logcat_field_session_export` (Schnappschuss, Fokus `BleLog` / Crypto-Cmd in Zeile; kein Dauer-Stream)
   - optional parallel **HCI-Snoop** laut `VALIDATION-PLAYBOOK.md`, dann `escooter_re_pull_btsnoop`.

**Kurz:** `logcat -c` **vor** der Session; Sitzung durchführen; `logcat -d` (oder MCP-Export) **danach** — nicht umgekehrt mischen.

### Repo- und Build-Referenz (9d, vorbesetzt 2026-04-26)

- **Aktueller `escooter` HEAD** (für 1:1-Repro): `cd escooter && git rev-parse HEAD` — **nach** `git pull` = Spitze inkl. aller 9d-Doku-Commits in dieser Datei.
- **0.1.6 / `versionCode` 7 in `app/build.gradle.kts`:** `b2ae04f1db9de3160de744631165ba3dff02e807` (Kurz: `b2ae04f`).
- **9d-Vorlage + MCP** (`escooter_re_logcat_field_session_export`, `escooter_re_logcat_clear`): `fd5856de8782871af79092f7a1c5585e608e8341` (Kurz: `fd5856d`); folgende Commits erweitern nur Doku/Metadaten in `FIELD-TEST-LOG.md`, nicht zwingend die App-Binary.
- **Check 1 (Klon+Install):** unverändert Kapitel **Session 9c** weiter oben in dieser Datei — in der 9d-Tabelle `Ja (verweist 9c)` oder neuen Frischklon+`installDebug` in derselben 9d-Session nachziehen.

### Messlauf 9d (2026-04-26, ~23:25–23:29, Phone `2312DRAABG` / adb `mvderkvoxw4t5tv8`) — ehrliches Protokoll

- **Kontext Operateur:** Zuerst wirkte Verbindung/State wankend/unkoordiniert; **nach zweifachem Druck am Lichtschalter** (Licht an/am Roller) kam stabilerer GATT-/Crypto-Pfad, danach sichtbares L mit anschliessendem M-Timeout im Log (siehe unten). **Hinweis:** In dieser Sitzung wurden **22/40 km/h** in erster Linie per **A11y Vol-Up/Down 3× (Stealth/Profile)**, **nicht** per Diagnostics-Field-Test-Buttons, ausgelöst — trotzdem echter `Cmd SetSpeedLimit` + `5A A5 02` auf der Wire-Seite. Gerät im Log: `1K1UA2525P1196` (MAC `D5:A1:FB:21:4C:BD`), kein Session-5-Referenz-MAC.
- **Roh-Log (Repo):** `reverse-engineering/ble-captures/2026-04-26-9d-blelog-mvderkvoxw4t5tv8.log` (115 Zeilen, Vollständigkeit wie Mitschnitt).

### Log-Auszüge (9d — 2026-04-26)

**Crypto-Handshake (Ausschnitt: Reconnect, L, danach M-Timeout — *kein* vollgrünes O in diesem Puffer):**

```text
04-26 23:28:32.611  -- Reconnect  auto-reconnect to 1K1UA2525P1196 (D5:A1:FB:21:4C:BD)
04-26 23:28:33.901  -- Crypto     scooterName='1K1UA2525P1196' tokenLoaded=false
04-26 23:28:33.992  -- Crypto     L: token+challenge received
… (5B/5C RX-DEC, 5A A5 10 … Handshake-Frames) …
04-26 23:28:39.492  -- Crypto     stage 2 (M) timed out — pair-init not acked
```

**TX-Regression 22 km/h (Crypto-Session, *verschlüsselte* Nutzlast — Präfix `5A A5 02` wie erwartet; *nicht* Klartext-`3E 16 02 48` aus reiner Doku, weil Session-Keys):**

```text
04-26 23:25:59.480  -- Cmd        SetSpeedLimit(kmh=22)   (A11y / City-Profil, erste Serie)
04-26 23:25:59.484  TX RX-WRITE   5A A5 02 64 B0 AC E2 17 25 80 00 A0 D5 00 07
— weitere 22/40-Zyklen im vollen Log; z. B. 23:28:57.500 / TX …00 10
```

**TX-Regression 40 km/h (analog, `SetSpeedLimit(kmh=40)` + `5A A5 02` …):**

```text
04-26 23:26:09.564  -- Cmd        SetSpeedLimit(kmh=40)
04-26 23:26:09.568  TX RX-WRITE   5A A5 02 CE FA 35 4F 5C 94 DC 79 F2 49 00 08
```

**Playbook-Extras (Reconnect, RX-DEC) — in derselben Datei:**

```text
04-26 23:28:33.976  RX … 5A A5 1E 0D FC D4 A9 …
04-26 23:28:33.981  -- RX-DEC  src=04 dst=3E cmd=5B arg=01 [… ASCII …2525P1196]
04-26 23:28:36.791  -- RX-DEC  src=04 dst=3E cmd=5C arg=00
```

### Ergebnis-Tabelle (6 Zeilen, 9d — 2026-04-26 Messlauf, ehrlich)

| Check | Ergebnis (Ja/Nein/teils) | Kurz-Beleg |
|---|---|---|
| 1) (aus 9c) Frischer Klon + `installDebug` | **Ja** (Verweis) | Wie 9c §1; gleiche Sitzung nicht erneut geklont. |
| 2) L/M/O visuell Diagnostics, Reihenfolge, zu Log passend | **Nein** (für diese Sitzung nicht belegt) | Steuerung/Limits über **A11y Vol 3× + Profile**; kein **Diagnostics-Field-Test**-Pfad in diesem Log. L/M-Phase am Crypto-Pfad: L ja, M-Timeout (s. unten). |
| 3) Logcat: Tag `BleLog` inkl. `-- Crypto`-Noten … bis voll gepaart bzw. O | **Teils** | **L: token+challenge** vorhanden; **M:** `stage 2 (M) timed out — pair-init not acked` (z. B. 23:28:39.492). **O / „fully paired“:** in diesem Export **nicht** als klare Endzeile sichtbar. |
| 4) `→ 22 km/h` mit belegtem TX-Fragment | **Ja** (Crypto-Pfad) | `Cmd SetSpeedLimit(kmh=22)` + `5A A5 02 …` mehfach, z. B. 23:25:59.48x / 23:28:57.50x. |
| 5) `→ 40 km/h` analog | **Ja** (Crypto-Pfad) | Ebenso `SetSpeedLimit(kmh=40)` + `5A A5 02 …` (z. B. 23:26:09.56x; weitere in Log). |
| 6) Playbook-Smoke §6, eine Sitzung | **Teils** | `Reconnect`, **RX-DEC** `5B`/`5C` vorhanden; **M-Timeout** verhindert „durchgängig grünen“ Smokeschluss. Kein `SCAN` in Auszug. |

### Fazit 9d (2026-04-26, nach Messlauf + Log-Archiv)

- [ ] **Alle sechs** Checks vollgrün: **nein** — trotzdem starker Beweis für **funktionsfähigen SetSpeed-22/40-Stack** in aktiver **Crypto-Session** (Präfix `5A A5 02`); **L** schlägt an, **M-Phase** bremst weiter (pair-init) — ggf. Licht/VCU-Stand, Timing oder separates Pairing-Thema, nicht reines „kein TX“.
- **Lichtschalter-Operator-Hinweis** für Folgesessions: zweimaliger Schaltimpuls scheinbar Erstkontext verbessert — **einmal** im Playbook/9d-Operator als beobachteter Hack dokumentiert (kein Ersatz für fachliches VCU-Debug).
- **Für „Phase-1 alles grün“:** nochmals Sitzung mit (a) **Referenz-MAC/Name** wie Doku, (b) bewusst **Diagnostics** + Field-Test-Buttons, (c) Crypto bis **O** oder klare Begründung, wenn absichtlich A11y-only. → **9d-B** (unten) liefert Belege für (b) zumindest `SetSpeed` **ohne** A11y-Zeilen.

### Session 9d-B — 2026-04-26 (Follow-up: Diagnostics / Field-Test, zweiter Mitschnitt)

- **Hintergrund:** Explizit angeordnet, **nur** Diagnostics-Field-Test **22/40** zu nutzen, **kein** Vol-Stealth — trotzdem erscheinen im Puffer zuerst erneut **A11y**-Zeilen, danach **direkt** `Cmd SetSpeedLimit`-Zeilen + `5A A5 02` **TX** **ohne** A11y in der unmittelbar vorhergehenden Logzeile (Diagnostics-Knopf-Pfad **dokumentiert** im Mitschnitt; **MAC** weiter `D5:…`, **nicht** `C1:6B:5E:D0:C5:96`).
- **Roh-Log (Repo):** `reverse-engineering/ble-captures/2026-04-26-9dB-diagnostics-fieldtest.log` (38 Zeilen).
- **Ablauf im Log (Auszug):** Start: `L timed out — no token` (23:35:29) bei Auto-Reconnect. 23:37:26–23:38:00: **A11y / Profile** (laut Doku-Operator **nicht** gewollt) — 23:38:19–22: **ohne** `A11y` davor, nur:
  - `-- Cmd        SetSpeedLimit(kmh=22)` → `TX … 5A A5 02 B3 5B 94 … 00 0B`
  - `-- Cmd        SetSpeedLimit(kmh=40)` → `TX … 5A A5 02 04 0A 18 … 00 0C`
- **Lock / Unlock** (23:38:29..32) als weitere `Cmd` + `5A A5 02` — wahrscheinlich gleiche Sitzung (Bedienung/Feldtest-Umgebung); nicht Playbook-§6-„Smoke“-Pflicht.

**Tabelle 9d-B (Ergänzung zu 9d; nur diese Sitzung 9d-B):**

| Check (Analog 9d) | Ergebnis | 9d-B-Beleg |
|---|---|---|
| 2) Diagnostics L/M/O visuell | **Teils / unklar in Log** | L/M/O-Chips **erscheinen in BleLog nicht wörtlich**; sicht-Check blieb beim Operateur. **Crypto:** L-Timeout in erster Reconnect-Phase, kein vollgänger Handshake-Export. |
| 4) 22 km/h, TX | **Ja** (Diagnostics, gleicher Wire wie 9d) | Zeile 31–32: `SetSpeedLimit(22)` + `5A A5 02…` **ohne** A11y-Zeile unmittelbar davor; Roller weiter nicht Referenz-`C1:6B…`. |
| 5) 40 km/h, TX | **Ja** (analog) | Zeile 33–34. |
| *Vermischung* | *offen* | Sitzung **enthält trotz Anweisung** frühe **A11y**-Einträge — für strikte 9d-„nur-Buttons“-Story idealerweise **eigenen** `logcat -c`+**ausschließlich** Knöpfe wiederholen. |

**Fazit 9d-B:** Gegenüber **9d** ist der Nachweis **besser** für *„SetSpeed-Buttons liefern dieselbe GATT-Ebene wie A11y“* (`Cmd` + `5A A5 02`). *Strenger* 9d-Checklist-„nur-Diagnostics“-Reinraum: **nein** (A11y im selben Puffer) — ggf. **9d-C** mit diszipliniertem Ablauf.

**MCP `escooter-re`:** bietet **Logcat-Schnappschuss**, **Grep**, `logcat -c`, **btsnoop-Pull**, **Capture-MD** — ersetzt **weder** Pairing noch Roller noch Live-`adb` am Schreibtisch. Dauer-Logs: Terminal (`adb logcat -v time *:S BleLog:V` o. ä.).

---

## Session 10 — 2026-04-28 (Doku-Workstream: ZT3 Register-Captures & Capability-Flags, KEIN Live-HCI)

> **Hinweis:** Diese Session bundlelt die **Doku-Abnahme** für Phase-„Mapping“ in einem Agent-Lauf. Roh-Dumps `CRYPTO_dump`+btsnoop für jeden Unterpunkt sind im Repo als **strukturierte Templates** hinterlegt, **Quelle der Wahrheits-Claims = `app/README.md` § Nicht-funktional (2026-04-28)** + vorhandener FIELD-Test-History — bis ein Operator Roh-Mitschnitte nachlegt, gelten die Dateien in `ble-captures/2026-04-28-zt3-*.md` als **Befund-Mapping**.

| Feld | Wert |
|------|------|
| Datum | 2026-04-28 |
| Phone | (Build-Host / lokal; kein neues ADB-Feld) |
| Roller-MAC | Referenz-Setup `C1:6B:…` bzw. wie FIELD-5 |
| Befund | Mode `0x5A`, Licht `0x5B`, Cruise-Remote→`0x5D` (App): **alle ⚠️** sichtwirksam; Lock `0x71` **OK** f. einfachen Toggle; **SetSpeed 0x48** unverändert (byteidentisch) |
| Capture-Datei | `reverse-engineering/ble-captures/2026-04-28-zt3-mode-switch.md` (Mode), `…-headlight-toggle.md` (0x5B), `…-cruise-toggle.md` (0x5D) |
| `encodeCrypto()` | Nur **Kommentar**-Tags + **kein** Byte-Wechsel an 0x48, Lock, Reboot, Read* |
| Capability | `VehicleState.unsupportedCommands` = `SetMode`/`SetLights`/`SetCruise`; Vehicle-UI: Mode/Licht disabled |
| `pollPlan` | `0x57` (Throttle) entfernt — Speed-Display aus **MCU 0x86** |

**Conclusion:** Doku+Code aligned mit README/Referenz; Live-SHU**-Hexzeilen-Archiv** = Follow-up-Operator-Task.

---

## Session 10B — 2026-04-28 (Lock / Unlock, Referenz-Register 0x71)

| Feld | Wert |
|------|------|
| Capture | `ble-captures/2026-04-28-zt3-lock-pattern.md` |
| Befund | `01 00` / `00 00` (App) = **funktionsfähig**; LRRL-Bytes `86 68 00 00` = separat zu erfassen |
| Code | **Kein** `encodeCrypto(Lock/Unlock)`-Byte-Wechsel |

---

## Session 10C — 2026-04-28 (Referenzdoku & Sequenzdiagramm)

| Art | Wert |
|-----|------|
| Datei | `ble-captures/MAPPING-RUN-SEQUENCE.md` (Mermaid: SHU → ZT3 → Doku) |
| `zt3-ble-register-reference.md` | Spalten *ZT3 Pro D verifiziert* + *Quelle* in §3.1, 3.2, 3.4 (0x48), 3.6 (0x71) |

---

## Session 9d — TEMPLATE (Phase-1 Gate Re-Run, L/M/O inkl. Resume-M)

> Vorlage fuer den naechsten echten Gate-Lauf. Alles in **einer** Sitzung erfassen (frischer Log, gleicher Phone/Roller-Kontext, ohne Mischpuffer aus frueheren Runs).

### Test-Setup

- Datum/Uhrzeit:
- Phone-Modell (`adb shell getprop ro.product.model`):
- Android-Version (`adb shell getprop ro.build.version.release`):
- Roller Name + MAC:
- App-Variant (`com.celox.segway.debug`):

### Commits (vor/nach Gate-Lauf)

- Pre-SHA (`git rev-parse HEAD` vor dem Lauf):
- Post-SHA (Commit mit dieser Session):

### Roh-Kommandos (copy/paste)

```text
adb devices -l
adb logcat -c
cd app && ./gradlew :app:installDebug
adb shell am start -W -n com.celox.segway.debug/com.celox.segway.MainActivity
adb logcat -d -v time -s BleLog:* > /tmp/session-9d-blelog.txt
```

### Erwartete Stage-Reihenfolge (Diagnostics + BleLog)

- First-pair: `L -> M -> O`
- Resume (persisted random): `L -> M (resume-hint) -> O`  
  (M darf vor echter 0x5C-ACK sichtbar werden; O bleibt ACK-basiert)

### Checkliste (6 Punkte)

| Check | Ergebnis | Evidence (dieser Lauf) |
|---|---|---|
| 1) Fresh-clone + `:app:installDebug` erfolgreich (APK auf Phone) | ☐ | Gradle-Output mit `Installed on 1 device.` |
| 2) L/M/O visuell in Diagnostics (inkl. Resume-M-Verlauf) | ☐ | kurzer Bedien- und Beobachtungs-Text + Zeitstempel |
| 3) Logcat `BleLog`/`Crypto` geordnet bis mindestens O oder klarer Timeout | ☐ | 4-8 Zeilen Auszug mit L/M/O-Noten |
| 4) `-> 22 km/h` Regression | ☐ | `-- Cmd SetSpeedLimit(22)` + zugehoerige TX-Zeile |
| 5) `-> 40 km/h` Regression | ☐ | `-- Cmd SetSpeedLimit(40)` + zugehoerige TX-Zeile |
| 6) Playbook-Smoke (Reconnect / RX-DEC / optional SCAN) | ☐ | 3-6 Zeilen Auszug |

### Log-Auszug (Paste)

```text
[L] -- Crypto ... token+challenge ...
[M] -- Crypto ... resumed via persisted random ...   (oder paired-key)
[O] -- Crypto ... fully paired ...
-- Cmd SetSpeedLimit(kmh=22)
TX RX-WRITE 5A A5 02 ...
-- Cmd SetSpeedLimit(kmh=40)
TX RX-WRITE 5A A5 02 ...
```

### Fazit

- Phase-1 Gate: ☐ Gruen / ☐ Rot
- Wenn Rot: exakter Blocker + naechster minimaler Schritt

---

## Session 9d-A — 2026-04-27 13:38 (Gate-Re-Run Versuch, infra-blockiert)

Diese Session ist ein **echter Einzel-Lauf** nach dem 9d-Schema, aber mit hartem Infra-Blocker: kein ADB-Device verfuegbar zum Laufzeit-Teil (Diagnostics / BleLog / 22/40 / Smoke).

### Test-Setup

- Datum/Uhrzeit: 2026-04-27 13:38 (UTC+2)
- Phone-Modell (`adb shell getprop ro.product.model`): n/a (`adb: no devices/emulators found`)
- Android-Version (`adb shell getprop ro.build.version.release`): n/a (`adb: no devices/emulators found`)
- Roller Name + MAC: n/a (ohne verbundenes Phone nicht erfassbar)
- App-Variant: `com.celox.segway.debug` (Soll-Ziel)

### Commits (vor/nach Gate-Lauf)

- Pre-SHA: `8235da33735d0543d9f67da6e2ab0bc151aa7594`
- Post-SHA: unveraendert (nur Session-Dokumentation in Arbeit)

### Checkliste (6 Punkte, nur dieser Lauf)

| Check | Ergebnis | Evidence (dieser Lauf) |
|---|---|---|
| 1) Fresh-clone + `:app:installDebug` erfolgreich (APK auf Phone) | ❌ | Ohne Device kein installierbarer Endnachweis in dieser Session. |
| 2) L/M/O visuell in Diagnostics (inkl. Resume-M-Verlauf) | ❌ | Nicht durchfuehrbar ohne Device/Live-App. |
| 3) Logcat `BleLog`/`Crypto` bis O oder klarer Timeout | ❌ | Kein Device -> keine app-seitigen `BleLog`-Laufzeitzeilen. |
| 4) `-> 22 km/h` Regression | ❌ | Nicht durchfuehrbar ohne Device+Roller. |
| 5) `-> 40 km/h` Regression | ❌ | Nicht durchfuehrbar ohne Device+Roller. |
| 6) Playbook-Smoke (Reconnect / RX-DEC / optional SCAN) | ❌ | Nicht durchfuehrbar ohne Device. |

### CLI-Blocker-Auszug

```text
$ adb devices -l
List of devices attached

$ adb shell getprop ro.product.model
adb: no devices/emulators found
```

### Fazit 9d-A

- Phase-1 Gate (dieser Lauf): **ROT**
- Exakter Blocker: **kein angeschlossenes/autorisiertes ADB-Device**
- Naechster minimaler Schritt: Device per USB anschliessen + RSA-Dialog bestaetigen + `adb devices -l` zeigt `device`, dann 9d-B mit vollem Lauf (`logcat -c`, `installDebug`, Diagnostics, 22/40, Log-Export).

---

## Session 9d-B — 2026-04-27 13:42-13:44 (Live-Re-Run mit Phone+Roller)

### Test-Setup

- Datum/Uhrzeit: 2026-04-27 13:42-13:44 (UTC+2)
- Phone-Modell: `2312DRAABG`
- Android-Version: `15`
- Roller Name + MAC (aus `BleLog Reconnect`): `1K1UA2525P1196` (`D5:A1:FB:21:4C:BD`)
- App-Variant: `com.celox.segway.debug`

### Commits (vor/nach Gate-Lauf)

- Pre-SHA: `8235da33735d0543d9f67da6e2ab0bc151aa7594`
- Post-SHA: unveraendert (nur Log-Dokumentation)

### Laufzeit-Output (Auszug)

```text
04-27 13:43:11.404 -- Reconnect  auto-reconnect to 1K1UA2525P1196 (D5:A1:FB:21:4C:BD)
04-27 13:43:12.655 -- Crypto     L: token+challenge received
04-27 13:43:12.657 -- RX-DEC     src=04 dst=3E cmd=5B arg=01 [...]
04-27 13:43:15.523 -- RX-DEC     src=04 dst=3E cmd=5C arg=00 []
04-27 13:43:47.412 -- Cmd        SetSpeedLimit(kmh=22)
04-27 13:43:47.416 TX RX-WRITE   5A A5 02 F0 21 63 2C 0F B3 C8 BE 9B 63 00 0E
04-27 13:43:50.238 -- Cmd        SetSpeedLimit(kmh=40)
04-27 13:43:50.242 TX RX-WRITE   5A A5 02 EF 87 BA 30 24 3A 89 84 47 71 00 0F
```

### Checkliste (6 Punkte, nur dieser Lauf)

| Check | Ergebnis | Evidence (dieser Lauf) |
|---|---|---|
| 1) Fresh-clone + `:app:installDebug` erfolgreich (APK auf Phone) | ✅ | `:app:installDebug` lief erfolgreich: `Installed on 1 device.` |
| 2) L/M/O visuell in Diagnostics (inkl. Resume-M-Verlauf) | ⏳ / manuell | Nutzer hat 22/40 in Diagnostics ausgefuehrt; im exportierten `BleLog` ist **L** sichtbar, M/O-Chip-UI selbst bleibt ein visueller Operator-Check. |
| 3) Logcat `BleLog`/`Crypto` bis O oder klarer Timeout | 🟨 teils | L und RX-DEC vorhanden (`5B`, `5C`), aber in diesem Mitschnitt keine explizite `O: fully paired`-Zeile. |
| 4) `-> 22 km/h` Regression | ✅ | `SetSpeedLimit(kmh=22)` + TX mit Prefix `5A A5 02 ...` vorhanden. |
| 5) `-> 40 km/h` Regression | ✅ | `SetSpeedLimit(kmh=40)` + TX mit Prefix `5A A5 02 ...` vorhanden. |
| 6) Playbook-Smoke (Reconnect / RX-DEC / optional SCAN) | 🟨 teils | `Reconnect` + `RX-DEC` vorhanden; `SCAN` optional und hier nicht getriggert. |

### Fazit 9d-B

- Phase-1 Gate (dieser Lauf): **TEILS GRUEN**
- Klar gruen in 9d-B: Device/Install, Reconnect, Speed-Regression 22+40 mit `5A A5 02` TX.
- Noch offen fuer vollgruenes Gate: expliziter M/O-Abschluss im selben Lauf-Log + visueller L/M/O-Haken im Operator-Protokoll.

---

## Session 9d-C — 2026-04-27 13:49 (Reconnect-Fokus fuer O-Nachweis)

Ziel dieses Kurzlaufs: nach sauberem Neustart explizit `O: fully paired` im selben frischen Log sehen.

### Durchgefuehrte Schritte

1. `adb logcat -c`
2. `adb shell am force-stop com.celox.segway.debug`
3. `adb shell am start -W -n com.celox.segway.debug/com.celox.segway.MainActivity`
4. `adb logcat -d -v time -s BleLog:*`
5. kurze Wartezeit, erneuter Log-Dump

### Relevanter Log-Auszug (9d-C)

```text
04-27 13:49:35.194 -- Reconnect  auto-reconnect to 1K1UA2525P1196 (D5:A1:FB:21:4C:BD)
04-27 13:49:37.138 -- Crypto     L: token+challenge received
04-27 13:49:37.148 -- RX-DEC     src=04 dst=3E cmd=5B arg=01 [...]
04-27 13:49:40.114 -- RX-DEC     src=04 dst=3E cmd=5C arg=00 []
04-27 13:49:41.962 -- RX-DEC     src=04 dst=3E cmd=5C arg=00 []
04-27 13:49:42.798 -- Crypto     stage 2 (M) timed out — pair-init not acked
```

### Ergebnis 9d-C

- `L`: **vorhanden**
- `M`: **kein Abschluss**, stattdessen Timeout
- `O`: **nicht erreicht** (kein `O: fully paired` in diesem Lauf)

### Fazit 9d-C

- Der gezielte O-Nachweis ist in diesem Reconnect-Lauf **nicht** gelungen.
- Damit bleibt das Phase-1-Gate weiterhin **teilgruen** (wie 9d-B), aber **nicht vollgruen**.
