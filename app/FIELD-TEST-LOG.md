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
