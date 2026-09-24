# ZT3 CAN-Bus Frame Reference

Beobachtete Frames auf dem **externen VCU-CAN-Bus** des Segway Ninebot ZT3 Pro D (gelb=CAN-H, grün=CAN-L, schwarz=GND).

> **Status:** Erst-Recon abgeschlossen 2026-05-06. Alle primären Fahrer-Inputs gemappt + Beep-Trigger gefunden + Live-Speed-Limit-Mechanik verstanden.

## Bus-Parameter

- **Bitrate:** 500 kbit/s
- **Format:** Standard CAN 2.0A (11-bit IDs)
- **Verschlüsselung:** keine (plain) — bestätigt die Hypothese aus [`ESP32-BRIDGE-PLAN.md`](../ESP32-BRIDGE-PLAN.md): der interne Bus spricht plain, der `5A A5` + Encryption2-Wrapper ist nur das BLE-Transport-Layer.
- **Frame-Rate:** ~210 Frames/s im Idle, 35 unique CAN-IDs gesehen

## Kompakt-Übersicht aller bestätigten Mappings

| Frame.Byte | Funktion | Range / Werte |
|------------|----------|---------------|
| `0x100[0]` | Throttle | 0 – 0xC8 (0–200) |
| `0x100[1]` | Bremse (vorne+hinten kombiniert) | 0 bis 0x82 beobachtet, in Ruhe exakt 0 |
| `0x100[2]` | User-Input-Active-Flag | 0x04 wenn Throttle/Knopf gedrückt |
| `0x100[4]` | Mode-LABEL (km/h-Bucket, statisch pro Mode) | Walk=05, Eco=0F, Drive=19, Sport=23 |
| `0x100[5]` | unbekannt (Battery% fraglich, bleibt beim Laden 0x4F) | 0x4F = 79 |
| `0x100[6]` | Mode-spezifischer 2. Wert (Power/KERS-Bucket) | Walk=0F, Eco=23, Drive=5A, Sport=64 |
| `0x342[6]` | **★ TATSÄCHLICHER MCU-enforcter Top-Speed in km/h** | live |
| `0x20C[2]` | Top-Speed in 0.5-km/h-Auflösung (= 0x342[6] × 2) | live |
| `0x343[3]` + `0x343[6]` | Hauptlicht-Status (synchron, 1 = an) | bit |
| `0x343[4]` + `0x343[5]` | **★ Brake-Light-Status** (geht beim Bremsen an, [5] mit ~500ms Hold-Verzug) | bit |
| `0x20C[0]` + `0x342[4]` | **★ Turn-Signal-Indikator** (toggled 1.25 Hz) | 0/1=L/2=R |
| `0x211[3]` | „Blinker aktiv"-Flag (set bei links UND rechts) | 0x00 / 0x04 |
| `0x212[2]` | Brake-Pedal-Pressed-Flag, reagiert nicht in allen Captures (siehe [Analyse](ANALYSIS-0x100.md#bremse-byte-1)) | 0 / 1 |
| `0x401[0]+[1]` | Brake-System-Active 16-bit (`00 00` ↔ `FF FF`) | bit-pair |
| `0x212[2]` | **★ Charger-Connected-Flag** (Bit 3, 0x08) | bit |
| `0x20C[1]` + `0x342[5]` | **★ Charging-State**: 0xC4/0xE4=normal, 0x80=Charger an idle, 0x82=Charging aktiv | enum |
| `0x100[2]` Bit 4 | Charge-Status-Change-Event (kurz beim Stecker-Wechsel) | bit |
| `0x21A` (one-shot) | **★ Beep-Event** (Auslöser offen, nicht Speed > 25 km/h) | erscheint nur bei Beep |
| `0x344[7]` | Buzzer-Drive-Pulse (~200ms während Beep) | 0x00 / 0xC0 |
| `0x211[6..7]` = `0x203[6..7]` | Geschwindigkeit, **u16 little-endian**, Hypothese 0,1 km/h/LSB | 0 bis 405 beobachtet |
| `0x420[0..1]` / `[2..3]` | Hypothese: Packspannung 10 mV (u16le) / Packstrom mit Vorzeichen (s16le) | 2 Hz |
| `0x483` + `0x484` (ASCII) | Seriennummer-Broadcast | „1K1UA2551P3965" |

## Bestätigte Frame-Details

### Frame `0x100` — VCU-Status (50 Hz)

```
Byte 0:  00..C8      THROTTLE (analog, 0–200)
Byte 1:  00..FF      BREMSDRUCK (sammelt vorne+hinten, nicht unterscheidbar)
Byte 2:  Bitfeld     Bit 2 (0x04) = USER-INPUT-ACTIVE (Throttle/Mode/Licht-Knopf gedrückt)
                     Bit 4 (0x10) = CHARGE-STATUS-CHANGE-EVENT (kurz beim Stecker-Wechsel)
Byte 3:  40          (konstant in allen Captures, vermutlich globales Mode-Bitfeld)
Byte 4:  Mode-LABEL  km/h-Bucket: Walk=05, Eco=0F, Drive=19, Sport=23 — statisch, NICHT das echte Limit
Byte 5:  4F = 79     (unbekannt, bleibt auch beim Laden 4F)
Byte 6:  Mode-B      Walk=0F, Eco=23, Drive=5A, Sport=64 — vermutlich Power/Torque/KERS-Bucket
Byte 7:  32 = 50     (?, einmalig 36 vor einem Beep)
```

Analyse vom 2026-09-24 (Details und Messvorschläge in [`ANALYSIS-0x100.md`](ANALYSIS-0x100.md)):
- Periode 20,00 ms, Jitter ±0,3 ms, DLC konstant 8. Einzelne Lücken von 45 ms nur in Captures mit Zustandsänderung.
- **Kein Rolling Counter, keine inhaltsabhängige Checksumme.** Byte 0 und 1 variieren, Bytes 3, 5, 7 bleiben dabei konstant.
- Byte 1 (Bremse) ist ohne Betätigung in jedem Frame exakt 0, beobachtetes Maximum 0x82.
- Byte 3 ist einmalig `00` in einem Lade-Ereignis-Frame, Byte 7 einmalig `36` etwa 100 ms vor einem `0x21A`-Beep. Bedeutung offen.
- **Wer 0x100 sendet, ist offen.** Der Name „VCU-Status“ ist eine Annahme. Messmethode in der Analyse.

Verifiziert via:
- [`brake-left.csv`](../can-data/brake-left.csv) (hinterer Bremshebel) und [`brake-right.csv`](../can-data/brake-right.csv) (vorderer): identisches Pattern auf Byte 1, **vorne und hinten auf dem CAN-Bus nicht unterscheidbar**. Die VCU OR'd beide Sensoren zu einem einzigen Brake-Intent-Wert. Für separate Detektion müsste man die analogen Sensor-Leitungen direkt an den Hebeln anzapfen.
- [`throttle.csv`](../can-data/throttle.csv): Byte 0 stieg linear `00 → C8` und zurück, Byte 2 ging in `04` während Throttle gedrückt war.

### Frame `0x342` — Display-Echo + LIVE-Speed-Limit (5 Hz)

```
Byte 0:   Mode-Label  Echo von 0x100[4]
Byte 2:   Mode-B      Echo von 0x100[6]
Byte 5:   C4/E4       wechselt mit Mode (Bedeutung unklar)
Byte 6:   ★ LIVE-LIMIT  TATSÄCHLICH vom MCU enforcter Top-Speed in km/h
Byte 7:   34          konstant
```

**`0x342[6]` ist der wahre Speed-Cap, nicht das Mode-Label.** Er weicht vom Label ab wenn:
- Per-Mode-Limits in der App reduziert wurden (z.B. Drive=20 statt nominal 25)
- Ein Speed-Unlock aktiv ist (z.B. Sport mit 0x342[6]=40 trotz Mode-Label "35")

Verifiziert via:
- [`unlock-40.csv`](../can-data/unlock-40.csv): bei aktivem 40-km/h-Unlock im Sport-Mode wechselt 0x342[6] live von 0x16 (22) auf 0x28 (40), während 0x100[4] auf 0x23 (Sport-Label) bleibt.
- [`lock-22.csv`](../can-data/lock-22.csv): umgekehrt — beim Lock auf 22 fällt 0x342[6] zurück von 0x28 (40) auf 0x16 (22), 0x100[4] unverändert. Damit doppelt bestätigt.

### Frame `0x20C` — Live-Speed-Limit High-Resolution (10 Hz)

```
Byte 2:   LIVE-LIMIT × 2   gleicher Wert wie 0x342[6], aber in 0.5-km/h-Auflösung
```

Beispiel Sport+Unlock-40: 0x20C[2] = 0x50 (80) → 80 × 0.5 = 40 km/h ✓
Beispiel Sport+Lock-22:  0x20C[2] = 0x2C (44) → 44 × 0.5 = 22 km/h ✓

### Frame `0x343` — Lighting-Status komplett (10 Hz)

```
Byte 3:  00/01      HAUPTLICHT-BIT A (Front + Heck synchron)
Byte 4:  00/01      BRAKE-LIGHT-BIT A (geht beim Bremsen an)
Byte 5:  00/01      BRAKE-LIGHT-BIT B (geht beim Bremsen an, Hold ~500ms nach Loslassen)
Byte 6:  00/01      HAUPTLICHT-BIT B (synchron mit Byte 3)
Byte 7:  00..C8     in Fahrt: Throttle-Echo
sonstige: 00        konstant
```

`0x343` ist DAS zentrale Lighting-Status-Frame des Rollers. Bytes paaren sich:
- `[3]+[6]` = Hauptlicht (an wenn beide 1) — verifiziert via [`light-toggle.csv`](../can-data/light-toggle.csv)
- `[4]+[5]` = Brake-Light (an wenn beide 1) — verifiziert via [`brake-light-twice-with-light-on.csv`](../can-data/brake-light-twice-with-light-on.csv): bei jeder der zwei Bremsungen flippten beide Bytes 0→1, beim Loslassen fiel Byte 4 sofort auf 0, Byte 5 erst ~500ms später (klassisches Brake-Light-Hold-Verhalten für die hinter Fahrenden).

Im Fahrt-Modus tauchen zusätzlich „ride active"-Bits in Bytes 3,4,5,6 auf (alle = 0x01) und Byte 7 trackt den Throttle-Wert (siehe [`driving-40-beep.csv`](../can-data/driving-40-beep.csv)).

### Charging-Detection ⭐

Der Roller signalisiert Charging-Status auf 3 verschiedenen Frames gleichzeitig:

```
0x212[2]:  0x00 = kein Charger,   0x08 = Charger angeschlossen
0x20C[1]:  Charging-State (siehe Tabelle unten)
0x342[5]:  Echo von 0x20C[1] ans Display
0x100[2]:  Bit 4 (0x10) Burst beim Stecker-Wechsel als Event-Marker
```

**Charging-State-Werte (0x20C[1] und 0x342[5]):**

| Wert | Bedeutung |
|------|-----------|
| `0xC4` / `0xE4` | normaler Betrieb, kein Charger |
| `0x80` | Charger angeschlossen, idle (lädt nicht aktiv — z.B. fertig oder pausiert) |
| `0x82` | Charger angeschlossen + lädt aktiv |

Verifiziert via:
- [`charging-start.csv`](../can-data/charging-start.csv): bei t=3.56s 0x212[2] springt 0x00→0x08 (Charger erkannt), bei t=3.81s wechselt 0x20C[1] auf 0x80.
- [`charging-stop.csv`](../can-data/charging-stop.csv): startete im Lade-Zustand (0x212[2]=0x08, 0x342[5]=0x82). Bei t=3.61s fiel 0x212[2] auf 0x00 (Charger abgesteckt), bei t=5.13s 0x342[5] auf 0x80, kurz danach 0x100[2] = 0x10 als „Status-Change-Event"-Burst.

**Use-Case App:** der CAN-basierte Charging-Status reagiert in <50ms auf Stecker-Wechsel. Eine Phone-App kann darauf verzichten, BLE alle paar Sekunden zu pollen — und im Dashboard live „lädt gerade / Charger angeschlossen / fährt" anzeigen.

### Bremse — weitere Frames (außer 0x100[1])

Das primäre Brems-Pedal-Signal ist `0x100[1]` (analoge 0–255 Skala, vorne+hinten kombiniert). Zusätzlich wird der Brems-Status redundant in mehreren Frames mitsignalisiert:

| Frame.Byte | Funktion |
|------------|----------|
| `0x100[1]` | Analoge Brems-Stärke (0–255) |
| `0x212[2]` | Brake-Pedal-Pressed-Flag (0/1, einfacher Bit-Flag) |
| `0x343[4]+[5]` | Brake-Light-Aktiv (siehe oben) |
| `0x401[0]+[1]` | „Brake-System-Active" 16-bit Status (`00 00` ↔ `FF FF`) |
| `0x401[3]` | weiteres Brems-Bit |

**Einschränkung (2026-09-24):** Die Flags `0x212[2]`, `0x343[4..5]` und `0x401` reagieren nur in [`brake-light-twice-with-light-on.csv`](../can-data/brake-light-twice-with-light-on.csv), dort schon ab `0x100[1]` = 3. In [`brake-left.csv`](../can-data/brake-left.csv) und [`brake-right.csv`](../can-data/brake-right.csv) bleiben sie trotz `0x100[1]` bis 116 auf 0. Die Bedingung ist offen. Als verlässliches Bremssignal gilt deshalb nur `0x100[1]`.

Die Redundanz erklärt sich über CAN-Architektur: jeder ECU broadcastet was sie über das Bremsen weiß — Display will den Wert für Tacho-Bremspedal-Indikator, Brake-Light-Treiber will den Aktiv-Status, BMS will Bremsen für KERS-Charging-Triggering.

### Frame `0x21A` + `0x344[7]` — Speed-Warning-Beep ⭐

`0x21A`: DLC 4, Payload `00 00 00 00`. **Erscheint nur während eine Über-Speed-Warnung (Beep) gefeuert wird.** In keinem anderen Capture (Idle, Brake, Throttle ohne Beep, Mode-Switch, Light-Toggle, Stand-Unlock, Stand-Lock) gesehen.

`0x344[7]` springt korreliert auf `0xC0` für ~200ms und fällt dann auf `0x00` zurück — vermutlich der eigentliche Buzzer-Drive-Pulse vom VCU.

Verifiziert via [`driving-40-beep.csv`](../can-data/driving-40-beep.csv):
- t=7.89s: 0x21A erscheint
- t=7.91s (+20ms): 0x344[7] = 0xC0
- t=8.11s (+200ms): 0x344[7] = 0x00

Throttle war zu dieser Zeit auf Max (0xC8 seit 150ms). **Korrektur 2026-09-24:** Die Geschwindigkeit in `0x211[6..7]` lag beim Beep bei 44 = 4,4 km/h (Hinterrad vermutlich in der Luft). Die frühere Deutung „über die 25-km/h-Warnschwelle“ passt dazu nicht, der Auslöser des Beeps ist offen.

**Wichtig für ESP32-Anwendung:** Der Beep ist ein autonomes VCU-Hardware-Ereignis. Das CAN-Frame ist nur Broadcast/Info — den Beep durch Suppress des Frames zu verhindern funktioniert NICHT. Der ESP32 kann das CAN-Event aber als Trigger für einen **Hardware-Buzzer-Cut-MOSFET** nutzen (siehe ESP32-Bridge-Plan Use-Case A).

### Frame `0x20C[0]` und `0x342[4]` — Turn-Signal-Indikator (10/5 Hz) ⭐

```
0x20C[0]:  00 = aus, 01 = links, 02 = rechts, vermutlich 03 = Warnblinker
0x342[4]:  Echo desselben Werts ans Display
0x211[3]:  00 / 04  Generelles "Blinker aktiv"-Bit (links UND rechts setzen es)
```

**Real-Time-Lampen-Steuerung** — der Wert toggled alle 400 ms zwischen Aktiv und 00 (= 1.25 Hz Blink-Rate). Verifiziert via [`turn-left.csv`](../can-data/turn-left.csv) und [`turn-right.csv`](../can-data/turn-right.csv).

**Wichtige Korrektur zur BLE-Side-Conclusion:** Die Aussage in `python/README.md` *"turn-signal state not exposed as readable registers"* gilt nur für den **BLE-Register-Sweep** — die VCU exposed den Status nicht über die BLE-Read-Register. Auf dem **CAN-Bus** ist er aber sichtbar. ESP32 mit CAN-Sniff kann Blinker-Status in Echtzeit erkennen.

### Negativ-Befunde — was NICHT auf dem CAN-Bus liegt

- **Custom-Button-Press-Erkennung** — siehe [`custom-button.csv`](../can-data/custom-button.csv): kein eigener „Custom-Button-pressed"-Frame. Nur die ausgelöste Effekt-Aktion ist sichtbar (Mode-Wechsel auf Walk-Werte = Custom-Button war hier auf Walk-Effect konfiguriert). Wenn man wissen will dass der Knopf gedrückt wurde, muss man die Effekt-Konsequenz beobachten.

### Mode-Knopf-Logik (Dual-Funktion)

Der Mode-Knopf am Lenker hat Dual-Funktion:
- **Kurz drücken** → Mode-Wechsel (rotiert 0x100[4] und 0x100[6])
- **Lang drücken** → Licht-Toggle (kippt 0x343[3] und [6])

Long-Press triggert intern beides nacheinander: erst Mode-Switch (~80–560ms vor Light-Toggle), dann Licht. Beide Events sind im Bus sichtbar.

**Mode-Rotation per Short-Press — alle 4 Übergänge dediziert verifiziert:**

| Mode | 0x100[4] (Label km/h) | 0x100[6] | 0x342[6] (echtes Limit) | Capture |
|------|----------------------|----------|-------------------------|---------|
| Walk | 0x05 (5) | 0x0F (15) | 0x05 (5) | sport-to-walk |
| Eco | 0x0F (15) | 0x23 (35) | 0x0F (15) | walk-to-eco / eco-to-drive |
| Drive | 0x19 (25) | 0x5A (90) | 0x14 (20) | eco-to-drive / drive-to-sport |
| Sport | 0x23 (35) | 0x64 (100) | 0x16 (22) | drive-to-sport / sport-to-walk |
| **Sport+Unlock-40** | 0x23 (35) | 0x64 (100) | **0x28 (40)** ★ | unlock-40 |
| **Sport+Lock-22** | 0x23 (35) | 0x64 (100) | **0x16 (22)** | lock-22 |

Rotations-Reihenfolge: **Walk → Eco → Drive → Sport → Walk** (zyklisch).

Bei diesem Roller (SHU-getuned) sind Drive=20 und Sport=22 **app-konfigurierte Reduktionen** vom nominellen Mode-Bucket (25/35). Sport+Unlock-40 hebt es temporär auf 40 km/h.

### Frames `0x211` und `0x203`: Geschwindigkeit (je 10 Hz)

```
0x211 Byte 6..7:  GESCHWINDIGKEIT, u16 little-endian
0x203 Byte 6..7:  derselbe Wert, andere Sendephase im 100-ms-Raster
```

**Korrektur 2026-09-24:** Der Wert ist 16 Bit breit. In [`driving-40-beep.csv`](../can-data/driving-40-beep.csv) läuft er bis `0x0195` = 405 und damit in Byte 7 über. Die frühere Angabe „Byte 6, max 0xE5“ las nur das untere Byte.

**Hypothese 0,1 km/h pro LSB:** Das Plateau bei Vollgas liegt bei 220 bis 229 mit Limit 22 km/h ([`throttle.csv`](../can-data/throttle.csv)) und bei 400 bis 405 mit Limit 40 km/h ([`driving-40-beep.csv`](../can-data/driving-40-beep.csv)). In beiden Captures drehte das Hinterrad sehr wahrscheinlich in der Luft (Anstieg um 36 km/h in 1,2 s). Bestätigung im Fahrbetrieb per GPS steht aus, siehe Messung M5 in [`ANALYSIS-0x100.md`](ANALYSIS-0x100.md).

### Frames `0x483` + `0x484` — Seriennummer (1 Hz, broadcast)

ASCII-Decode der konstanten Bytes:
- `0x483`: `31 4B 31 55 41 32 35 35` = `"1K1UA255"`
- `0x484`: `31 50 33 39 36 35 00 00` = `"1P3965"`

Konkateniert: **`1K1UA2551P3965`** = vermutlich VIN/Seriennummer-Broadcast, alle 1s gesendet.

### Frames `0x501` + `0x502` — vermutlich Crypto-Challenge

Bytes wirken vollständig zufällig, niedrige Frame-Rate (~0.4 Hz). Sehr wahrscheinlich der Encryption2-Handshake-Traffic vom BLE-Modul.

## Vollständige ID-Liste (alle 35 IDs aus 5s-Capture)

| ID | DLC | Rate (Hz) | Bedeutung |
|----|-----|-----------|-----------|
| `0x100` | 8 | 50 | **VCU-Status** (Throttle, Brake, Mode, State-Flag) ✅ |
| `0x203` | 8 | 10 | BMS / Wheel-Speed-Echo (Byte 6) ✅ |
| `0x204` | 8 | 10 | reserved/diag (alle 0) |
| `0x205` | 8 | 10 | reserved/diag (alle 0) |
| `0x209` | 8 | 10 | konstant `9F 27 10 04 90 49 00 00` |
| `0x20A` | 8 | 10 | reserved/diag (alle 0) |
| `0x20B` | 8 | 10 | konstant `BC 02 64 00 01 C8 00 00` |
| `0x20C` | 8 | 10 | **Live-Speed-Limit × 2** (Byte 2) ✅ |
| `0x211` | 8 | 10 | **Wheel-Speed** (Byte 6) ✅ |
| `0x212` | 8 | 10 | konstant `52 01 00 C9 00 00 00 29` |
| `0x21A` | 4 | one-shot | **Beep-Event** ⭐ |
| `0x301` | 8 | 5 | meist 0 |
| `0x302` | 2 | 5 | konstant `00 FF` |
| `0x310` | 8 | 5 | konstant `F1 0E 78 05 1A 02 01 C8` |
| `0x311` | 8 | 5 | konstant `39 1E 07 3F 90 01 00 00` |
| `0x341` | 8 | 5 | konstant `00 00 00 00 02 00 00 00` |
| `0x342` | 8 | 5 | **Display-Echo + Live-Limit** (Byte 6) ✅ |
| `0x343` | 8 | 10 | **Light-Status** + Ride-Aktivität (Bytes 3,6) ✅ |
| `0x344` | 8 | 5 | **Buzzer-Drive** (Byte 7) ⭐ + andere Events |
| `0x401` | 8 | 2 | konstant `00 00 34 00 05 2A 00 00` |
| `0x420` | 8 | 2 | Hypothese: Packspannung [0..1] + Packstrom [2..3] |
| `0x421` | 8 | 2 | konstant `38 36 31 34 FB 04 62 04` |
| `0x422` | 8 | 2 | konstant `00 00 20 00 15 00 02 00` |
| `0x423` | 8 | 2 | konstant `BC 02 64 00 3C 00 01 00` |
| `0x424` | 8 | 1 | Byte 0 = Counter (steigt monoton) |
| `0x425` | 8 | 2 | konstant `01 64 48 58 58 58 01 FF` |
| `0x429` | 8 | 2 | konstant `20 00 00 00 00 00 00 00` |
| `0x480` | 8 | 1 | konstant `00 00 00 00 20 00 00 00` |
| `0x481` | 8 | 1 | wechselt mit Mode (Multi-Byte-Settings) |
| `0x482` | 8 | 1 | konstant `52 01 02 00 00 00 00 00` |
| `0x483` | 8 | 1 | **ASCII-Seriennummer Teil 1** ✅ |
| `0x484` | 8 | 1 | **ASCII-Seriennummer Teil 2** ✅ |
| `0x485` | 8 | 5 | konstant `22 00 00 00 0C 02 48 0C` |
| `0x500` | 8 | 0.6 | konstant `47 48 48 FF 48 48 48 FF` |
| `0x501` | 8 | 0.4 | rauschig — **vermutete Crypto-Challenge** |
| `0x502` | 8 | 0.4 | rauschig — **vermutete Crypto-Challenge** |

## Workflow zur ID-Identifikation

Parser hat keine externen Abhängigkeiten (nur stdlib), läuft direkt mit System-Python:

```bash
cd can-bus

# Übersicht: welche IDs wurden gesehen, wie oft, wie variabel
python3 parser/can_parser.py ../can-data/brake-left.csv

# Eine ID über die Zeit beobachten — Diff-Modus
python3 parser/can_parser.py ../can-data/throttle.csv --watch 0x100

# Periode, Byte-Statistik, Counter- und Checksummen-Suche über mehrere Captures
python3 parser/can_parser.py ../can-data/*.csv --analyze 0x100

# Latenz von Gasflanken zu Antwortsignalen (ID:Byte:Typ)
python3 parser/can_parser.py ../can-data/throttle.csv --step --response 0x211:6:u16le

# Welche Bytes ändern sich überhaupt
python3 parser/can_parser.py ../can-data/throttle.csv --diff

# Zwei Captures vergleichen — was ändert sich bei Bremsen vs Idle
python3 parser/can_parser.py ../can-data/brake-left.csv ../can-data/throttle.csv --compare
```

## Mapping-Strategie

Pro isolierter Aktion am Roller einen Capture machen, dann diffen. Bewährtes Pattern:
- 5–10 s Capture
- 3 s Idle-Vorlauf, 1 Aktion, Rest Idle-Nachlauf
- Auto-Restart-Sampling AUS — sonst überschreibt jeder Capture den vorherigen
- Pro Aktion eigene CSV → `can_parser.py --compare` macht das Diffing trivial

## Cross-Reference zur ECU-Tabelle (BLE-Layer)

Die [`zt3-ble-register-reference.md`](../reverse-engineering/protocol/zt3-ble-register-reference.md) listet ECU-Adressen für BLE/UART:

| BLE-Adresse | ECU | mögliche CAN-ID? |
|-------------|-----|------------------|
| `0x02` | MCU | ? |
| `0x07` | BMS | `0x203` ? |
| `0x16` | VCU | `0x100` ? |
| `0x23` | TFT/Display | `0x342` ? |
| `0x04` | BLE | ? |

→ Mapping zwischen den BLE-ECU-Adressen und den CAN-IDs muss noch ermittelt werden — möglicherweise unabhängige Adressräume.

## Status

### Erledigt (2026-05-06)

- [x] Throttle-Sweep → `0x100[0]`
- [x] Brake (vorne + hinten) → `0x100[1]`, beide nicht unterscheidbar
- [x] Mode-Wechsel pro Mode → `0x100[4]+[6]` und `0x342[6]` alle 4 Modi vermessen
- [x] Mode-Knopf-Dual-Funktion (kurz=Mode, lang=Licht) verifiziert
- [x] Live-Speed-Limit-Mechanik via `unlock-40` + `lock-22` doppelt bestätigt
- [x] Light-Toggle → `0x343[3]+[6]`
- [x] Brake-Light → `0x343[4]+[5]` (~500ms Hold-Verzug zwischen den Bytes)
- [x] Beep-Trigger via `0x21A` + `0x344[7]` gefunden
- [x] Turn-Signal links/rechts → `0x20C[0]` + `0x342[4]` (1.25 Hz Toggle)
- [x] Charging-Detection → `0x212[2]` + `0x20C[1]` + `0x342[5]` + `0x100[2]` Bit 4
- [x] Brems-Status-Redundanz → `0x212[2]`, `0x401[0]+[1]`, `0x401[3]`
- [x] Seriennummer per ASCII-Decode auf `0x483`+`0x484`

### Offene Captures — low-effort (5–10s, kein Extra-Equipment)

| # | Capture-Name | Aktion | Erwartete Erkenntnis |
|---|--------------|--------|---------------------|
| O1 | `multi-beep.csv` | 30s Fahrt mit 3-4 absichtlichen Beep-Events | Bestätigt 1:1 Korrelation `0x21A ↔ Beep`, kritisch für ESP32-Buzzer-Mute-Implementation |
| O2 | `battery-full.csv` + `battery-50.csv` | Baseline bei 100% und nach Fahrt bei ~50% | Verifiziert `0x100[5] = Battery%`-Hypothese (vermutet 0x4F = 79) |
| O3 | `idle-30s.csv` | 30s steady idle (Roller an, nichts machen) | Vollständige ID-Liste, seltene Frames, Heartbeat-Periodizität |
| O4 | `tacho-10kmh.csv`, `tacho-15kmh.csv`, `tacho-20kmh.csv` | jeweils ~10s konstant fahren, GPS parallel | Bestätigt die Hypothese 0,1 km/h/LSB für `0x211[6..7]` im Fahrbetrieb (Messung M5) |
| O5 | `cruise-control.csv` | Tempomat aktivieren (falls vorhanden, vermutlich Throttle-5s-Halten) | Cruise-Active-Bit finden — Throttle-Byte = 0 aber Roller fährt weiter |
| O6 | `walk-active.csv` | Walk-Mode aktiv, Knopf gehalten (Roller schiebt) | Was passiert auf Bus während Walk-Hold-Active |
| O7 | `error-throttle-disconnect.csv` | Throttle-Stecker während Capture kurz ziehen | Error-Code-Frame (vermutlich neue ID oder `0x100[7]` wechselt) |
| O8 | `airlock-active.csv`, `findmy-active.csv` | AirLock / Find My aktivieren | Vermutlich neue Frame-IDs oder Bit-Flags |
| O9 | `odometer-100m.csv` | Vor + nach 100m Fahrt vergleichen | Trip-Counter / Odometer in `0x424` oder `0x480-0x485` finden |
| O10 | `warn-blinker.csv` | Warnblinker (beide gleichzeitig, falls vorhanden) | Bestätigt `0x20C[0] = 0x03`-Hypothese für Warnblinker |

### Offene Captures — medium-effort (Multimeter / Werkbank-Setup)

| # | Test | Setup | Ziel |
|---|------|-------|------|
| O11 | Cell-Voltages identifizieren | Multimeter am Akku-Stecker, Pack-Voltage messen | 16-bit LiIon-Werte (3000–4200 mV) in `0x209` / `0x20B` / `0x310` / `0x311` korrelieren |
| O12 | Pack-Voltage / Pack-Current | Roller im Stand vs Volllast | Frame-Bytes die unter Last steigen (Phasen-Strom) |
| O13 | Temperatur (Motor / MCU / BMS) | Kalt-Baseline + 5min Fahrt | 1-Byte °C-encoded Werte (oft als signed int oder offset-encoded) |
| O14 | Motor-Current / Power | Berg-Fahrt vs Ebene auf Privatgelände | Bytes die mit Last korrelieren |

### Bytes/Frames mit unklarer Bedeutung (passive Spekulation)

| Frame.Byte | Beobachtet | Spekulation |
|------------|------------|-------------|
| `0x100[3]` | konstant 0x40 = 64 | globales Mode-Bitfeld? |
| `0x100[5]` | konstant 0x4F = 79 | Battery%? (zu verifizieren O2) |
| `0x100[6]` | bekannte Wertepaare | Power/Torque/KERS-Bucket pro Mode (Walk=0F, Eco=23, Drive=5A, Sport=64) |
| `0x100[7]` | konstant 0x32 = 50 | ? (testen via Error-Provocation O7) |
| `0x209` | konstant `9F 27 10 04 90 49 00 00` | ECU-Static-Config? Kandidat für BMS-Cell-Voltages O11 |
| `0x20B` | konstant `BC 02 64 00 01 C8 00 00` | `BC 02` = 700 als 16-bit; vermutlich Pack-Voltage in 0.05V (= 35V)? |
| `0x301` | meist 0 | ? |
| `0x302` | konstant `00 FF` (DLC 2!) | Heartbeat? |
| `0x310` | konstant `F1 0E 78 05 1A 02 01 C8` | Motor-Config? Kandidat für BMS O11 |
| `0x311` | konstant `39 1E 07 3F 90 01 00 00` | ? |
| `0x341` | konstant `00 00 00 00 02 00 00 00` | ? |
| `0x344[0..6]` | gelegentlich Multi-Byte-Events | weitere Status-Events neben dem Buzzer-Drive in [7] |
| `0x401[2]` | wechselt 0x34/0x35 bei Charging-Stop | Charging-related (Counter? State-Sub-Code?) |
| `0x420` | Bytes 0,1,2,3 wechseln unter Last | Hypothese Packspannung/Packstrom, siehe [Analyse](ANALYSIS-0x100.md#nebenbefund-0x420-hypothese) |
| `0x421` | konstant `38 36 31 34 FB 04 62 04` | startet mit ASCII "8614" — vermutlich Hardware-/Modell-ID-Teil |
| `0x422` | Byte 4 wechselt 0x15/0x55 bei Charging | Charging-related Settings |
| `0x423` | konstant `BC 02 64 00 3C 00 01 00` | `BC 02` = 700 (Pack-Voltage echo?) |
| `0x424[0]` | monotoner Counter (~1 Hz) | Tick-Counter / Uptime? Kandidat für Odometer O9 |
| `0x425` | konstant `01 64 48 58 58 58 01 FF` | dreimal 0x58 = 88 — Cell-Block-Werte? |
| `0x429` | konstant `20 00 00 00 00 00 00 00` | ? |
| `0x480` | konstant `00 00 00 00 20 00 00 00` | ? |
| `0x481` | wechselt komplett mit Mode (4 Bytes auf einmal) | Per-Mode-Display-Settings (Acceleration-Curve, KERS-Stärke, …) |
| `0x482` | konstant `52 01 02 00 00 00 00 00` | gleiche `52 01` wie `0x212` Anfang — verwandt? |
| `0x485` | konstant `22 00 00 00 0C 02 48 0C` (wechselt mit Charging) | Charging-related Settings |
| `0x500` | konstant `47 48 48 FF 48 48 48 FF` | Pattern-bezogen (vier `48` = ASCII 'H'?) |
| `0x501` + `0x502` | rauschig (~0.4 Hz) | vermutete Crypto-Challenge / Random-Stream |

### Strukturelle Verbesserungen

- [ ] CAN-DBC-Datei generieren sobald genug IDs benannt sind (für `cantools` / `python-can` Standard-Tooling)
- [ ] Glitch-Frame in [`throttle.csv`](../can-data/throttle.csv) bei 5.35s untersuchen (`BC 20 02 20 11 A7 B2 19` mit ID 0x100) — vermutlich Bit-Stuffing-Decoder-Glitch in KingstVIS, sollte mit Re-Capture verifiziert werden
- [ ] BLE↔CAN-ECU-Mapping vervollständigen (welche BLE-Adressen aus [`zt3-ble-register-reference.md`](../reverse-engineering/protocol/zt3-ble-register-reference.md) korrespondieren mit welchen CAN-IDs)
- [ ] Active-Sender-Test: ESP32 als CAN-Node auf den Bus, kann er Frames erfolgreich injizieren ohne dass die VCU zickt? → Phase 2 ESP32-Bridge-Plan
