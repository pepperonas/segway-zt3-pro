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
| `0x343[3]+[6]` | Light-Status (synchron, 1 = an) | bit |
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
| `eco-to-drive.csv` | Mode-Übergang | Eco/Drive-Werte |
| `drive-to-sport.csv` | Mode-Übergang | Drive/Sport-Werte |
| `sport-to-walk.csv` | Mode-Übergang | Sport/Walk-Werte |
| `walk-to-eco.csv` | Mode-Übergang | Walk/Eco-Werte |
| `unlock-40.csv` | Sport-Mode 40-km/h-Unlock | 0x342[6] = 40, 0x20C[2] = 80 |
| `lock-22.csv` | Lock zurück auf 22 km/h | 0x342[6] = 22 (bestätigt Live-Limit-Hypothese) |
| `driving-40-beep.csv` | Fahrt mit Über-Speed-Beep | 0x21A + 0x344[7] |

## Status (Stand 2026-05-06)

- ✅ Bus identifiziert (CAN-Bus statt UART), Bitrate 500 kbit/s, plain bestätigt
- ✅ Alle primären Fahrer-Inputs gemappt (Throttle, Bremsen, Mode-Knopf, Licht-Knopf)
- ✅ Mode-Rotation aller 4 Modi dediziert verifiziert (Walk → Eco → Drive → Sport)
- ✅ Live-Speed-Limit-Mechanik via unlock-40 + lock-22 doppelt bestätigt
- ✅ Beep-Trigger gefunden (0x21A + 0x344[7])
- 🔜 Brake-Light-Test, Multi-Beep-Verifikation, Long-Idle-Capture
- 🔜 BMS-Frames (Cell-Voltages) identifizieren
- 🔜 ESP32-Sniffer-Prototyp (Phase 2 ESP32-Bridge-Plan)
