# LA1010 Workflow für ZT3-Bus-Recon

Hands-on-Anleitung für den **InnoMaker LA1010** (16-Kanal, 100 MHz, Threshold -4..+4V, KingstVIS-Software). Geschrieben für Phase 1 des [`ESP32-BRIDGE-PLAN.md`](../ESP32-BRIDGE-PLAN.md).

## Setup (5 min)

1. **KingstVIS installieren** — von <https://www.qdkingst.com/en/download> (Mac/Linux/Win)
2. **Treiber** — auf macOS automatisch via libusb, kein Extra-Step
3. **Erst-Verbindung** — laut Reviews: andere USB-UART-Adapter beim ersten Start abstecken, sonst findet KingstVIS das LA1010 nicht. Danach kann alles wieder ran.
4. **Probes** — 16 weibliche Pin-Probes + GND-Krokodilklemmen sind im Lieferumfang. Fragile Federklemmen, vorsichtig handhaben.

## Probe-Setup nach Bus-Typ

### A) Externer CAN-Bus am VCU-Stecker

**Verkabelung:**
```
LA1010 GND       → schwarz (GND)
LA1010 CH0       → gelb (CAN-H)    Threshold +3.0 V
LA1010 CH1       → grün (CAN-L)    Threshold +2.0 V
```

**Software-Setting:**
- Sample-Rate: **2 MHz** (CAN ist max 1 Mbit/s, Faktor-2-Oversample reicht)
- Channels: nur CH0+CH1 aktivieren (mehr Kanäle = niedrigere Sample-Rate)
- Trigger: `CH0 falling edge` (CAN-Frame-Start = SOF dominant)
- Decoder: **Channels → Add Decoder → CAN** auf CH0, Bitrate auto-detect oder typisch 500 kbit/s testen

**Was du sehen solltest:**
```
ID 0x123  [DLC 8]  AA BB CC DD EE FF 00 11
ID 0x456  [DLC 4]  12 34 56 78
```

Wenn KingstVIS sauber CAN-Frames decoded → Plain-CAN, **wir können sniffen ohne Crypto**. 
Wenn die Daten chaotisch aussehen oder die App Encryption2 erwartet → siehe Abschnitt "Falls verschlüsselt".

**Kandidaten-Bitraten** (in Reihenfolge testen): 500 kbit/s, 250 kbit/s, 125 kbit/s, 1 Mbit/s.

### B) Interner Stem-UART (Phase 1 ESP32-Plan)

**Vorbereitung:** Dashboard öffnen, Stem-Kabel offenlegen. 8-Pin JST-XH-Stecker freilegen ohne den Roller-internen Stecker zu lösen (die VCU muss aktiv bleiben damit Daten fließen).

**Verkabelung (alle 8 Adern parallel):**
```
LA1010 GND   → schwarz (GND-Ader im Stem-Kabel, vorher per Multimeter verifizieren)
CH0..CH7     → die 8 Stem-Adern (Reihenfolge egal, später am Decoder zuordnen)
Threshold    → +1.65 V  (= halbe 3.3-V-Logik)
```

**Software-Setting:**
- Sample-Rate: **8 MHz** auf 8 Kanälen (16-MHz-Limit bei 16 Kanälen → 8 Kanäle gehen entspannt)
- Trigger: `Falling edge` auf irgendeinem Kanal — UART-Idle ist HIGH, ein Start-Bit zieht LOW
- Decoder: für jeden CHx einen UART-Decoder, alle auf 115200 8N1
- Capture-Dauer: 1 sec reicht — VCU pollt schnell

**Was du suchst:**
- **UART-Bus-Kandidat:** sauberes UART-Signal, periodisch, `5A A5` Magic im Decode
- **Power-Pins:** dauerhaft HIGH (12 V) → Threshold tatsächlich überschreitet sie nicht; setze Threshold dafür höher, z.B. +6 V → siehst du dann konstant LOW = Indiz für 12V-Power
- **Brake/Throttle (analog):** kein sauberes UART-Pattern, eher PWM oder langsam wechselnd
- **GND/Reserve:** konstant LOW

## Empirische Trigger-Aktionen während Capture

Bei laufender Aufzeichnung folgende Aktionen ausführen, um Frame-IDs zu mappen:

| Aktion | Erwartete Bus-Aktivität |
|--------|------------------------|
| Power-On | Init-Burst, BMS-Self-Test, Display-Wake |
| Throttle drücken (Stand) | Throttle-Wert-Frame steigt periodisch |
| Bremshebel ziehen | Brake-Status-Frame ändert sich |
| Mode-Knopf 1× | Mode-Wechsel-Frame (Eco/Drive/Sport) |
| Mode-Knopf lang | Beleuchtung an/aus |
| Custom-Button doubletap | Walk/KERS/Park je nach Profil-Setting |
| Power-Off | Shutdown-Frame |

Jede Aktion in einer separaten Capture-Session aufnehmen, dann diff'en um die ID des verantwortlichen Frames zu finden.

## Falls CAN verschlüsselt

Plan-Doku sagt für den **internen UART**-Bus explizit *plain* (kein Crypto). Beim externen CAN ist unklar — könnte sein, dass die `5A A5`-Frames mit Encryption2-Layer drüber gehen (wie auf BLE).

**Erkennung verschlüsselt:**
- Daten-Bytes wirken zufällig/uniform-verteilt
- Kein wiederkehrender Header (`5A A5` o.ä.) sichtbar
- Counter-Felder ändern sich frame-über-frame um konstante Inkremente

**Falls ja → fallback auf Path B** (interner Stem-UART), das ist gemäß ESP32-Plan ohnehin die bessere Stelle weil plain-Text.

## Datenformat zum Mitarbeiten

Captures als `.kvis` (KingstVIS native) ODER als CSV exportieren via:
**File → Export → CSV**

Zur Versionskontrolle in `/can-data/` ablegen — kurze, aktionsbezogene Dateinamen:
```
brake-left.csv          (hinten)
brake-right.csv         (vorne)
throttle.csv
mode-switch.csv
light-toggle.csv
idle-30s.csv
```

Anschließend per [`parser/can_parser.py`](parser/can_parser.py) analysieren — siehe Workflow-Beispiele in [`FRAMES.md`](FRAMES.md#workflow-zur-id-identifikation).

## Stolperfallen

1. **Gemeinsamer GND auf allen Probes** — laut Review wichtig: keine Messungen zwischen verschieden-referenzierten Schaltungen. Hier unkritisch weil ZT3 ein einziges System ist, aber LA-GND **muss** an Roller-GND.
2. **USB-Bandbreite** — bei vielen Kanälen + hoher Sample-Rate verliert das LA1010 Daten (kein internes Memory). Wenn KingstVIS "drop"-Warnungen wirft → Sample-Rate halbieren oder Kanäle reduzieren.
3. **Stem-Kabel nicht trennen während des Captures** — VCU zickt sonst (Watchdog-Reset, Error-Code 24/14 wie bei G2/G30 berichtet).
4. **Threshold zu konservativ** — bei CAN-H mit 2.5V wird recessive nicht sicher als LOW erkannt. Lieber +3.0V oder noch höher (+3.2V) probieren.
5. **Anti-Static** — beim Anschluss am offenen Dashboard ESD-tauglich arbeiten (nicht über Teppich, Hand kurz an Erdung legen).

## Nach Phase 1

Wenn du genug Frames gesampled hast, Doku aktualisieren in:
- [`FRAMES.md`](FRAMES.md) — Frame-Reference mit IDs, Bytes und ihrer Bedeutung
- (`STEM-PINOUT.md` neu anzulegen, falls Pfad B mit internem Stem-UART verfolgt wird)

Dann Phase 2 (ESP32-C3 Sniffer-Prototyp) starten gemäß [`ESP32-BRIDGE-PLAN.md`](../ESP32-BRIDGE-PLAN.md).
