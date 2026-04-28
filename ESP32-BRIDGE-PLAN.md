# ESP32 als Bridge zwischen ZT3-Stem-Bus und Phone-BLE

Roadmap für die nächste Projekt-Phase: ein ESP32 sitzt **physisch am internen UART-Bus** im ZT3 (Single-wire half-duplex zwischen Dashboard und VCU), liest die rohen Frames im Klartext mit, kann optional eigene BLE-Commands an den Roller senden und hat über einen zweiten ESP32-Knoten via ESP-NOW Zugriff auf Hardware-Erweiterungen (Buzzer-Mute, Sensoren, Aktoren).

> **Architektur-Hinweis**: Pure-BLE-MitM (Phone↔ESP32↔Roller via Bluetooth, mit Re-Encrypt) wäre 10× komplexer — zwei Crypto-Sessions parallel, Adv-Spoof, Dual-Role-NimBLE. Für die realistischen Use-Cases (Brake-Trigger, Beep-Mute, Hardware-Erweiterung) reicht **passives UART-Sniffen + aktiver BLE-Client + Hardware-Cut**. Der UART-Frame ist plain — keine Crypto auf dem internen Bus, der `5A A5 + AES-CTR`-Wrapper ist nur der BLE-Transport-Layer.

## Use-Cases (sauber getrennt!)

### A — Beep-Mute bei Hochgeschwindigkeit

Hardware-Cut: MOSFET in der Buzzer-Leitung, ESP32-GPIO als Gate-Driver, Speed-Trigger aus dem UART-Sniff. Brutal simpel, 100 % zuverlässig, völlig unabhängig vom BLE-Pfad. Bus-Spoofing wäre Overkill — die VCU triggert den Beep autonom auf Basis der echten Wheel-RPM, nicht auf Phone-Befehl.

### B — Brake-Trigger → BLE-Lock

Passives UART-Mitlesen erkennt 3× Bremshebel-Pull innerhalb 2 s (analog zum AirLock-Pairing-Pattern). ESP32 ist parallel **BLE-Central** zum Roller (mit dem aus Android extrahierten `cryptoRandom` als Resume-Key) und sendet bei Trigger einen `SetSpeedLimit(22)`-Frame. Das ist passive Sniffing + aktiver BLE-Client, **kein** MitM.

### C — Hardware-Erweiterung

ESP32 als Hub für Sensoren / Aktoren am Lenker oder im Deck (LED-Strips, Buzzer-Mute-MOSFET, optionale IMU für Sturzerkennung). Bei räumlicher Trennung läuft ein zweiter ESP32-C3 im Deck und kommuniziert via **ESP-NOW** mit dem im Dashboard — kein zusätzliches Kabel durch den Stem nötig.

## Wire-Protokoll am UART-Bus

Single-wire half-duplex, 115200 8n1. Frame-Format (klassisch Ninebot/m365-Familie):

```
55 AA  <bLen>  <bAddr>  <bCmd>  <bArg>  <payload[bLen-2]>  <wChecksumLE>
```

`wChecksum = ~(bLen + bAddr + bCmd + bArg + sum(payload))` als 16-Bit-Inverted-Sum, little-endian.

**Wichtig**: das ist **derselbe Inner-Frame**, den der NinebotCrypto-Wrapper über BLE transportiert (nur ohne `5A A5 …`-Outer und ohne CBC-MAC). Der Bus zwischen Dashboard ↔ VCU ↔ MCU ↔ BMS spricht plain, weil intern keine Verschlüsselung gebraucht wird. ZT3-spezifische Payload-Längen können von G2/G30 abweichen — siehe Recon-Phase.

## Hardware-Stückliste

| Teil | Modell | Zweck |
|---|---|---|
| MCU (Dashboard-Knoten) | **ESP32-C3 Super Mini** (18 × 11 mm) | UART-Sniff + BLE-Central |
| MCU (Deck-Knoten, optional) | ESP32-C3 Super Mini #2 | Buzzer-MOSFET + Deck-Sensoren |
| Buck-Regler | MP1584 (auf 5 V) | Vom Roller-12V-Tap zur ESP32-VCC |
| Logic-Schutz | BAT54 Schottky + 10k Serien-Widerstand | RX-Pin-Schutz am Single-wire-Bus |
| Bus-Tap | JST-XH 8-pin Y-Splitter | reversibel, kein Eingriff am OEM-PCB |
| Buzzer-Cut | AO3400 N-MOSFET | Gate vom ESP32-GPIO |
| Befestigung | 3M VHB Doppelklebeband + Kapton | Vibrationen am Lenker tolerieren |

ESP32-S3 / Dual-Role / NimBLE-Adv-Spoof wird **nicht** gebraucht. C3 reicht für Sniff + BLE-Central.

## Einbau-Position: Dashboard-Gehäuse

Vergleich aller Optionen:

| Position | Strom | BLE-Reichweite | Platz | Wartungs-Zugang |
|---|---|---|---|---|
| Stem-Tubus (Lenkstange) | nur via Bus-Tap | gut (Plastik) | sehr eng | schlecht |
| Deck / VCU-Nähe | Battery-Tap direkt | **schlecht** (Aluminium) | viel | Bodenplatte |
| **Dashboard-Gehäuse** ⭐ | Stem-Bus VCC → MP1584 | **top** (Plastik, oben) | knapp aber OK | Display öffnen |
| Hybrid (ESP im Deck, Antenne im Stem) | flexibel | OK | viel | komplex (uFL-Mod nötig) |

**Empfehlung Dashboard-Gehäuse**: BLE-Reichweite ist top (Plastikgehäuse, oben am Lenker), Bus-Kabel kommt direkt rein, Stromversorgung via MP1584 vom Stem-VCC ist sauber. Single-Knoten-Setup falls Buzzer-Mute erstmal nicht nötig.

**2-Knoten-Setup** (final): zwei ESP32-C3 verteilt — einer im Dashboard (Bus-Sniff + BLE-Lock), einer im Deck (Buzzer-MOSFET + Deck-Sensoren). Sie reden via ESP-NOW (~3 ms Latenz, kein WiFi-Pairing, sehr robust). Vorteil: nur ein Bus-Tap im Dashboard, Buzzer-Cut ohne dünnes Kabel durch den Stem.

## Stem-Kabel-Pinout (TBD — Phase 1 aufnehmen)

ZT3-Stem-Kabel führt typisch 8 Pins. Geschätzt (vor Verifikation):

| Pin | Funktion |
|---|---|
| 1–2 | VCU-Power (12 V geregelt oder 36–48 V Akku — vor Tap mit Multimeter messen) |
| 3 | GND |
| 4 | UART-Bus (Single-wire, 115200 8n1) |
| 5–6 | Brake-Lever-Sensor (analog, optional separat) |
| 7–8 | Throttle / Hall / Reserve |

→ **Phase 1 verifiziert das per Logic-Analyzer**.

## Anschluss-Schema

```
[Stem-Kabel JST-XH 8-pin]
    ├─ VCC (12 V) ──► MP1584 Buck (5 V) ──► ESP32-C3 VBUS
    ├─ GND ─────────────────────────────► ESP32-C3 GND
    └─ UART-Bus ──[10k]──┬──► ESP32-C3 GPIO20 (RX, sniff-only Phase 1)
                         │
                         └──[BAT54]──► 3.3V  (klemmt Spikes)
```

10k Serien-Widerstand begrenzt Strom in den ESP32-Pin, BAT54 Schottky in Sperrrichtung schützt gegen Über-3.3V-Pulse vom Bus. RX-only in Phase 1; TX (`GPIO21`) erst aktivieren wenn Bus-Arbitration steht.

## Phasen

### Phase 1 — Recon (1–2 Tage)

1. Dashboard ausbauen, Stem-Kabel offenlegen
2. Logic-Analyzer (Saleae/Cheap) auf alle 8 Pins → Pin-Mapping
3. Pollen im Stand: Throttle, Bremse, Mode-Wechsel, Reichweite-Anzeige
4. Frame-Stream mit `9-analyzer` (rascafr/9-analyzer) parsen → ZT3-cmd/arg-Tabelle aufbauen
5. Hochgeschwindigkeitslauf auf Privatgelände → Beep-Trigger-Frame identifizieren

Erfolgs-Kriterium: Brake-Event und Speed-Stream sind reproduzierbar im Capture sichtbar.

### Phase 2 — Sniffer-Prototyp (1 Wochenende)

ESP32-C3 nur RX am Bus, FreeRTOS-Task parst `55 AA <len>...` Frames mit Magic-Search + Length-Validate + Inverted-Sum-Checksum. Brake/Speed-Events über USB-Serial loggen.

Erfolgs-Kriterium: Live-Decode sämtlicher Frames mit < 1 % Drop-Rate.

### Phase 3 — Brake-Trigger → BLE-Lock (2–3 Tage)

ESP32 als BLE-Central pairen (Resume-Path mit `cryptoRandom` aus Android-DataStore, identisch zum existing `python/zt3_cli`-Workflow). Trigger-Pattern (3× Brake in 2 s) erkennen → `SetSpeedLimit(22)`-Frame wrap'en + senden.

NinebotCrypto-Port von Python nach C/C++ via mbedTLS: ~300 LOC. Referenz: [`python/zt3_cli/crypto.py`](python/zt3_cli/crypto.py) und [`python/zt3_cli/handshake.py`](python/zt3_cli/handshake.py).

> **App-Pairing-Konflikt**: solange ESP32 paired ist, kann die Segway/Ninebot-App sich querschießen (Auth-Token-Konflikt). Während Test-Phase Roller aus App-Account entkoppeln; produktiv beide via separate `cryptoRandom`-Slots koexistieren lassen.

### Phase 4 — Buzzer-Mute (separat, optional)

Erst wenn Phase 2+3 stehen. Buzzer auf der VCU mit Multimeter (Continuity-Mode) lokalisieren während Roller piepst. AO3400 in-line. ESP32 schaltet Gate auf Speed > X km/h. Bei 2-Knoten-Setup läuft das im Deck-ESP, getriggert per ESP-NOW vom Dashboard-Knoten.

### Phase 5 — Hardware-Erweiterung (use-case-driven)

Custom-Sensoren / Aktoren am Deck-ESP. UART-Injection ins Roller-Bus erst wenn nötig — dann Bus-Arbitration (TX nur wenn Bus idle, sonst Frame-Kollision) implementieren.

## OTA + Wartung

ESP32-C3 unterstützt ArduinoOTA / WiFi-AP-Mode. **Initial-Flash via USB**, danach alles per WiFi-AP. So muss das Dashboard-Gehäuse nach dem Einbau nie wieder geöffnet werden — IPX5-Rating bleibt erhalten.

## Stolperfallen

1. **AirLock + Find My**: vor Tinkering aus Find My-Account nehmen, sonst Sichtbarkeit für andere AppleIDs nach Lock-Events.
2. **App-Pairing-Konflikt**: ESP32 als BLE-Central + offizielle App auf Phone können um den Auth-Token kämpfen. Ein-Slot-Modell der ZT3-Firmware testen.
3. **Single-wire Bus-Arbitration**: TX nur bei idle Bus, sonst Frame-Kollision + Watchdog-Reset im Dashboard (Error 24/14 wurde bei G2/G30 berichtet).
4. **Dashboard-Heartbeat**: VCU erwartet periodische Frames vom Dashboard. In-line MitM ist riskanter als parallel-Tap (passiv).
5. **MP1584 vor Anschluss kalibrieren**: auf 5 V einstellen *bevor* an ESP32, sonst Magic Smoke. Multimeter pflicht.
6. **Watchdog beim BLE-Lock**: SetSpeedLimit-Frame muss innerhalb des Phone-App-Heartbeat-Fensters durchgehen, sonst overrided die Phone-App den Wert beim nächsten Cycle.
7. **Rechtlich**: Beep ist Type-Approval-Feature (eKFV §1 Abs.1 Nr.4 + Anlage). Modifikation = Erlöschen ABE + Versicherung. Privatgelände only.

## Schlüssel-Dateien als Referenz

Lese-Reihenfolge vor Code-Start:

1. [`python/zt3_cli/crypto.py`](python/zt3_cli/crypto.py) — sauberster NinebotCrypto-Port (für Phase 3 nach C++ portieren)
2. [`python/zt3_cli/handshake.py`](python/zt3_cli/handshake.py) — Stage 1/2/3 Handshake
3. [`reverse-engineering/protocol/zt3-ble-register-reference.md`](reverse-engineering/protocol/zt3-ble-register-reference.md) — alle bekannten Register
4. [`reverse-engineering/apps/shu/ANALYSIS.md`](reverse-engineering/apps/shu/ANALYSIS.md) — Crypto + Handshake-Hintergrund

Externe Repos:
- **etransport/ninebot-docs** — Wiki mit Protokoll-Tabellen
- **rascafr/9-analyzer** — Node.js Parser + Arduino-Realtime-Sniff
- **TriWrite/VESC-Ninebot-Max-G2** — ESP32 Dash-Communicator (am nächsten zu unserem Use-Case)
- **ub4raf/Ninebot-PROTOCOL** — UART-RE inkl. BLE↔ESC Hack-Code

## Aufwands-Schätzung

| Phase | Aufwand |
|---|---|
| 1 — Recon | 1–2 Tage |
| 2 — Sniffer | 1 Wochenende |
| 3 — BLE-Lock | 2–3 Tage |
| 4 — Buzzer-Mute | 1 Tag |
| 5 — Hardware-Erweiterung | use-case-abhängig, 2–7 Tage |

Gesamt: ~2–3 Wochen Wochenend-Hacking für Sniff + Lock + Mute.

## Offene Punkte / Recherche-TODOs

- [ ] ZT3-Stem-Kabel-Pinout aus Forum-Threads (RollerPlausch, eScooter-Stammtisch) verifizieren bevor Bus-Tap
- [ ] Bestätigen ob ZT3-Firmware nur **eine** BLE-Connection gleichzeitig zulässt (impliziert App-vs-ESP32-Konflikt) oder zwei
- [ ] Frame-Format-Abweichungen ZT3 vs G2/G30 dokumentieren (Payload-Längen, neue cmd/args)
- [ ] Brake-Sensor: separate Analog-Leitung im Stem oder via UART-Frame? Wenn Letzteres → Frame-ID identifizieren in Phase 1
