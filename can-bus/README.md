# ZT3 CAN-Bus Reverse-Engineering

Eigenständiges Sub-Projekt: **passives Mitlesen + Mappen des externen VCU-CAN-Busses** des Segway Ninebot ZT3 Pro D, mit dem Ziel, später per ESP32 aktive Frames zu injizieren (siehe [`../ESP32-BRIDGE-PLAN.md`](../ESP32-BRIDGE-PLAN.md)).

> Diese Arbeit ist **getrennt** vom BLE-Reverse-Engineering im [`../reverse-engineering/`](../reverse-engineering/)-Verzeichnis. Die BLE-Seite ist verschlüsselt (Encryption2 / NinebotCrypto), der CAN-Bus ist **plain**.

## Hardware-Setup

| Komponente | Modell | Anschluss |
|------------|--------|-----------|
| Logic Analyzer | InnoMaker LA1010 (16ch / 100MHz) | USB an Mac |
| Probe Channel 0 | gelb (CAN-H) | Threshold +3.0 V |
| Probe Channel 1 | grün (CAN-L) | Threshold +1.5 V |
| Probe GND | schwarz | Roller-GND |
| Software | KingstVIS 3.6.5 | macOS |

Bus-Parameter: **500 kbit/s, Standard CAN 2.0A (11-bit IDs), plain**.

## Verzeichnis-Struktur

```
can-bus/
├── README.md           ← du bist hier
├── WORKFLOW.md         Setup-Anleitung Logic Analyzer + Capture-Workflow
├── FRAMES.md           Frame-Reference: alle bekannten CAN-IDs mit Byte-Mapping
└── parser/
    └── can_parser.py   Python-Parser für KingstVIS-CSV-Exports

../can-data/            CSV-Captures (außerhalb dieses Verzeichnisses)
```

## Schnell-Workflow

1. Capture in KingstVIS, Decoded Results CSV-Export → `../can-data/<aktion>.csv`
2. Parse + Übersicht:
   ```bash
   python3 parser/can_parser.py ../can-data/<aktion>.csv
   ```
3. Tieferer Watch eines Frames:
   ```bash
   python3 parser/can_parser.py ../can-data/<aktion>.csv --watch 0x100
   ```
4. Erkenntnis in [`FRAMES.md`](FRAMES.md) eintragen

Volle Details in [`WORKFLOW.md`](WORKFLOW.md) und [`FRAMES.md`](FRAMES.md).

## Bisher bestätigte Mappings

| Frame.Byte | Funktion | Range |
|------------|----------|-------|
| `0x100[0]` | Throttle | 0–0xC8 |
| `0x100[1]` | Bremse (vorne+hinten kombiniert) | 0–0xFF |
| `0x100[2]` | User-Input-Active-Flag | 0x04 / 0x00 |
| `0x100[4]` | Mode-LABEL (statischer km/h-Bucket) | Walk=5, Eco=15, Drive=25, Sport=35 |
| `0x342[6]` | **★ TATSÄCHLICHER Top-Speed-Cap in km/h** (live, app-konfiguriert oder Unlock) | live |
| `0x20C[2]` | derselbe Cap in 0.5-km/h-Auflösung (= 0x342[6] × 2) | live |
| `0x343[3]+[6]` | Hauptlicht-Status (synchron, 1 = an) | bit |
| `0x343[4]+[5]` | **Brake-Light** (an beim Bremsen, [5] mit ~500ms Hold) | bit |
| `0x20C[0]` + `0x342[4]` | **Turn-Signal-Indikator** (toggled 1.25 Hz) | 0=aus, 1=L, 2=R |
| `0x212[2]` Bit 3 | **Charger-Connected-Flag** | 0x08 wenn an |
| `0x20C[1]` + `0x342[5]` | **Charging-State** | 0x80=idle, 0x82=lädt aktiv |
| `0x21A` (one-shot) + `0x344[7]` | **★ Speed-Warning-Beep-Trigger** | event + 200ms-Burst |
| `0x211[6]` = `0x203[6]` | Wheel-Speed (Echo auf 2 IDs) | analog |
| `0x483` + `0x484` | Seriennummer-Broadcast (ASCII) | „1K1UA2551P3965" |

→ siehe [`FRAMES.md`](FRAMES.md) für die vollständige Liste aller 35 bekannten IDs + Details zu Frame-Layout.

## Captures-Inventar (Stand 2026-05-06)

| CSV | Aktion | Erkenntnis |
|-----|--------|-----------|
| `brake-left.csv` | Hinterer Bremshebel | 0x100 Byte 1 |
| `brake-right.csv` | Vorderer Bremshebel | gleicher Wert wie hinten — keine Discrimination |
| `throttle.csv` | Throttle-Sweep (aufgebockt) | 0x100 Byte 0 |
| `mode-switch.csv` | Mode-Knopf 1× | 0x100[4]+[6] |
| `light-toggle.csv` | Licht an + aus | 0x343[3]+[6] |
| `brake-light-twice-with-light-on.csv` | 2× Bremsen mit Licht an | 0x343[4]+[5] = Brake-Light, plus 0x212[2] / 0x401[0]+[1] redundant |
| `charging-start.csv`, `charging-stop.csv` | Charger anstecken / abziehen | 0x212[2]=0x08 connected, 0x20C[1]/0x342[5] = Charging-State |
| `turn-left.csv`, `turn-right.csv` | Blinker links/rechts | 0x20C[0]=01/02, 0x342[4] echo, 1.25 Hz Toggle |
| `custom-button.csv` | Custom-Button-Doppel-Tap | nur Effekt sichtbar (Mode-Wechsel auf Walk) |
| `eco-to-drive.csv` | Mode-Übergang | Eco/Drive-Werte |
| `drive-to-sport.csv` | Mode-Übergang | Drive/Sport-Werte |
| `sport-to-walk.csv` | Mode-Übergang | Sport/Walk-Werte |
| `walk-to-eco.csv` | Mode-Übergang | Walk/Eco-Werte |
| `unlock-40.csv` | Sport-Mode 40-km/h-Unlock | 0x342[6] = 40, 0x20C[2] = 80 |
| `lock-22.csv` | Lock zurück auf 22 km/h | 0x342[6] = 22 (bestätigt Live-Limit-Hypothese) |
| `driving-40-beep.csv` | Fahrt mit Über-Speed-Beep | 0x21A + 0x344[7] |

## Status (Stand 2026-05-06)

**Erste Recon-Phase abgeschlossen — alle Kern-Inputs des Rollers sind gemappt:**

- ✅ Bus identifiziert (CAN-Bus statt UART), Bitrate 500 kbit/s, plain bestätigt
- ✅ Alle primären Fahrer-Inputs (Throttle, Bremsen vorne+hinten, Mode-Knopf, Licht-Knopf, Blinker)
- ✅ Lighting komplett (Hauptlicht + Brake-Light + Turn-Signals)
- ✅ Mode-Rotation aller 4 Modi (Walk/Eco/Drive/Sport) dediziert verifiziert
- ✅ Live-Speed-Limit-Mechanik via unlock-40 + lock-22 doppelt bestätigt
- ✅ Beep-Trigger gefunden (0x21A + 0x344[7]) — direkt nutzbar für ESP32-Buzzer-Mute
- ✅ Charging-Detection (Connected-Flag + Charging-State + Status-Change-Event)

**Was noch offen ist** — ausführliche Liste aller geplanten Captures + ungeklärten Bytes/Frames in [`FRAMES.md`](FRAMES.md#status). Hauptthemen:

- 🔜 BMS-Frames (Cell-Voltages, Pack-Spannung, Pack-Strom) — vermutlich in 0x209/0x20B/0x310/0x311 versteckt, brauchen Multimeter zur Verifikation
- 🔜 Tacho-km/h-Skalierungsfaktor für `0x211[6]` Wheel-Speed-Counter
- 🔜 Battery%-Verifikation für `0x100[5]`
- 🔜 Long-Idle-Capture für seltene Frames
- 🔜 Multi-Beep-Verifikation (1:1 Korrelation 0x21A ↔ Beep)
- 🔜 ESP32-Sniffer-Prototyp (Phase 2 ESP32-Bridge-Plan)
