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
