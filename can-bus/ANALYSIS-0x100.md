# Analyse 0x100 (Gas, Bremse, Mode)

Grundlage für das Teilprojekt **Wheelie-Assist**: ein ESP32-Gateway, das `0x100[0]` (Gas) nur verringert, sobald der Roller über einen eingestellten Winkel nach hinten kippt. Dieses Dokument hält fest, was die vorhandenen 18 Captures über `0x100` hergeben, was daraus für das Gateway folgt und welche Messungen noch fehlen.

> **Stand:** 2026-09-24. Ausgewertet mit `can_parser.py --analyze 0x100` und `--step` über alle CSVs in [`../can-data/`](../can-data/). Aussagen, die nur auf einem oder zwei Captures beruhen, sind als Hypothese markiert.

## Reproduzieren

```bash
cd can-bus
python3 parser/can_parser.py ../can-data/*.csv --analyze 0x100
python3 parser/can_parser.py ../can-data/driving-40-beep.csv --step
python3 parser/can_parser.py ../can-data/throttle.csv --step --response 0x211:6:u16le

# Host-Tests der Heuristiken (nur Standardbibliothek)
cd parser && python3 -m unittest -v
```

Vor jeder Statistik werden Frames ohne ACK oder mit zu wenig Datenbytes verworfen. Das betrifft zwei Decoder-Glitches (`throttle.csv` bei 5,35 s, `driving-40-beep.csv` bei 8,19 s).

## Ergebnisse

### Sendeperiode und DLC

| Kennzahl | Wert |
|---|---|
| Periode (Median, alle Captures) | **20,00 ms** (50 Hz) |
| Jitter (Min bis Max ohne Lücken) | 19,7 bis 20,3 ms |
| Standardabweichung ohne Lücken | ≤ 0,03 ms |
| DLC | **konstant 8** in allen 8743 gültigen Frames |
| Lücken (> 1,5 × Median) | 6 Stück, je genau eine pro betroffenem Capture |

Die Lücken teilen sich in zwei Sorten:

- **40 ms** in `throttle.csv` und `driving-40-beep.csv`: genau an der Stelle des verworfenen Glitch-Frames. Der Frame war auf dem Bus, nur der Decoder hat ihn falsch gelesen.
- **45 ms** in `charging-stop.csv`, `light-toggle.csv`, `lock-22.csv`, `unlock-40.csv`: ein Frame fehlt ohne Decoder-Fehler, und der nächste kommt 5 ms verspätet. Alle vier Captures enthalten eine **Zustandsänderung** (Laden, Licht, Speed-Limit). Hypothese: der Sender von `0x100` persistiert dabei eine Einstellung und setzt einen Zyklus aus. `mode-switch.csv` zeigt keine Lücke, die Hypothese ist also unvollständig.

**Folge für das Gateway:** Ein Timeout für ausbleibende `0x100`-Frames muss mindestens 45 ms tolerieren. Vorschlag für den Plan: Fehler erst nach 3 fehlenden Perioden (60 ms), dann Passthrough bzw. Bypass.

### Bytes

| Byte | Beobachtet | Distinct | Korrelation zu Byte 0 | Bedeutung |
|---|---|---|---|---|
| 0 | `00` bis `C8` | 29 | Referenz | Gas, linear |
| 1 | `00` bis `82` | 71 | −0,04 | Bremse, analog |
| 2 | `00`, `04`, `10` | 3 | +0,22 | Bitfeld: `0x04` Eingabe aktiv, `0x10` Lade-Ereignis |
| 3 | `40`, einmal `00` | 2 | 0,00 | `00` nur in einem Ereignis-Frame beim Laden (`00 00 00 00 00 4F 00 32`) |
| 4 | `05 0F 19 23`, `00` im Ereignis-Frame | 5 | +0,22 | Mode-Label |
| 5 | `4F` | 1 | konstant | unbekannt (die Deutung „Battery%“ ist fraglich, der Wert bleibt auch beim Laden 0x4F) |
| 6 | `0F 23 5A 64`, `00` im Ereignis-Frame | 5 | +0,21 | Mode-Wert B |
| 7 | `32`, einmal `36` | 2 | +0,05 | unbekannt, siehe unten |

Die Korrelationen von +0,2 bei Byte 2, 4 und 6 sind ein Artefakt: Gas wurde nur im Sport-Mode gegeben. Einen funktionalen Zusammenhang belegen sie nicht.

**Byte 7 = `0x36`**: genau ein Frame, sauber quittiert, in `driving-40-beep.csv` bei 7,793 s (`C8 00 04 40 23 4F 64 36`), also etwa 100 ms vor dem Frame `0x21A`. Ein Bitfehler ist bei gültiger CAN-CRC unwahrscheinlich. Die Bedeutung ist **offen**. Für das Gateway ist das unkritisch, weil Byte 7 unverändert durchgereicht wird.

### Rolling Counter und Checksumme

**Keine Kandidaten.** Geprüft wurden:

- Counter: jedes Byte als Ganzes sowie Low- und High-Nibble, Inkrement konstant und ungleich 0 in ≥ 90 % der Übergänge, Wrap erlaubt, einzelne fehlende Frames toleriert.
- Checksumme: jedes Byte als Ziel, Eingänge „alle vorhergehenden Bytes“ und „alle übrigen Bytes“, jeweils mit und ohne vorangestellte CAN-ID, Algorithmen XOR, Summe, invertierte Summe, negierte Summe und sieben CRC-8-Varianten (Parameter nach dem reveng-Katalog, Prüfwerte in den Tests). Treffer zählen nur, wenn Ziel und Eingänge variieren.

Der Befund ist auch ohne Heuristik eindeutig: Byte 0 läuft über 29 Werte, Byte 1 über 71 Werte, und in denselben Frames bleiben die Bytes 3, 5 und 7 konstant. Ein Byte, das sich nicht ändert, kann weder ein Zähler noch eine Prüfsumme über Byte 0 oder 1 sein.

**Folge für das Gateway:** Byte 0 lässt sich ändern, ohne andere Bytes neu zu berechnen. Das gilt **unter Vorbehalt**: Eine Plausibilitätsprüfung des Empfängers, die nicht im Frame selbst steckt (etwa ein Vergleich mit einem analogen Gassignal), lässt sich aus den Captures nicht ausschließen.

### Bremse (Byte 1)

- In allen 15 Captures ohne Bremsbetätigung ist Byte 1 in jedem Frame **exakt 0** (über 7000 Frames). Kleinste gesehene Werte bei leichter Betätigung: 1, 3, 5.
- Höchster gesehener Wert: `0x82` (130). Die Angabe `0xFF` in früheren Tabellen ist nicht belegt. Ob der Sensor bei vollem Hebelweg höher geht, ist offen.
- In keinem Frame waren Gas und Bremse gleichzeitig größer 0. Wie sich der Roller dabei verhält, ist nicht gemessen.
- Die redundanten Brems-Flags `0x212[2]`, `0x343[4..5]` und `0x401` reagieren **nur** in `brake-light-twice-with-light-on.csv`, dort schon ab Byte 1 = 3. In `brake-left.csv` und `brake-right.csv` bleiben sie trotz Byte 1 bis 116 auf 0. Die Bedingung dafür ist **offen** (Licht an, Fahrzustand oder etwas anderes).

**Folge für das Gateway:** Als Bremssignal dient ausschließlich `0x100[1] > 0`. Es sitzt im selben Frame wie das Gas, hat also keinen Zeitversatz, und reagiert in allen Captures. Ob Vibration während der Fahrt im Ruhezustand Werte ungleich 0 erzeugt, ist offen. Die Regel „Bremse > 0 → Passthrough“ ist dann schlimmstenfalls zu vorsichtig, nie zu leichtsinnig.

### Radgeschwindigkeit `0x211` und `0x203`

`0x211[6..7]` ist ein **16-Bit-Wert, little-endian**, nicht nur Byte 6. In `driving-40-beep.csv` läuft er bis `0x0195` (405) und damit über Byte 6 hinaus. `0x203[6..7]` trägt denselben Wert, gesendet mit anderer Phase im 100-ms-Raster.

Das Plateau bei Vollgas passt in beiden Captures mit Bewegung zum aktiven Speed-Limit aus `0x342[6]`:

| Capture | Limit `0x342[6]` | Plateau `0x211[6..7]` | Verhältnis |
|---|---|---|---|
| `throttle.csv` | 22 km/h | 220 bis 229 | 10,2 |
| `driving-40-beep.csv` | 40 km/h | 400 bis 405 | 10,1 |

**Hypothese: 0,1 km/h pro LSB.** Zwei unabhängige Punkte passen auf 1 bis 2 % genau zusammen. Die Regressionstests pinnen das Verhältnis auf ±5 %.

**Einschränkung:** In beiden Captures steigt der Wert in etwa 1,2 s von rund 4 auf 40 km/h und fällt nach dem Loslassen in 0,3 s um 24 km/h. Für einen Roller mit Fahrer ist das physikalisch nicht erreichbar. Sehr wahrscheinlich drehte das **Hinterrad in der Luft**, auch in `driving-40-beep.csv`. Der Wert ist damit die Geschwindigkeitsschätzung des Controllers aus der Motordrehzahl. Ob sie im Fahrbetrieb mit der Geschwindigkeit über Grund übereinstimmt, klärt Messung M5.

Daraus folgt auch: `driving-40-beep.csv` zeigt den Beep (`0x21A`) bei einem Wert von `0x2C` = 4,4 km/h. Die bisherige Deutung „Speed-Warning ab etwa 25 km/h“ passt dazu nicht. Die Ursache des Beeps ist **offen**.

### Stufenantwort Gas → Motor

`--step` misst die Zeit von einer Gasflanke in `0x100[0]` bis zur ersten Änderung eines Antwortsignals. Mit den vorhandenen Captures ist die Reaktionszeit der Stock-Elektronik **nicht bestimmbar**:

- `0x211` und `0x203` kommen nur mit 10 Hz. Fast alle gemessenen Latenzen (11 bis 170 ms) liegen unter oder knapp über dieser Auflösung und sind Phasenlage, keine Reaktionszeit.
- `0x420` kommt nur mit 2 Hz.
- `0x343[7]` ist ein Echo des Gaswerts und sagt nichts über den Motor.

Belastbar ist nur eine grobe Obergrenze: Nach einem Sprung von Vollgas auf 0 fällt `0x211` spätestens mit dem übernächsten Sample, also nach höchstens etwa 200 ms. Für den Wheelie-Assist ist das zu ungenau, siehe Messung M3.

Eine Beobachtung für die Rekuperation: Nach dem Loslassen fällt die Radgeschwindigkeit in der Luft von 40,5 auf 15,8 km/h in 0,3 s. Ein frei drehendes Rad würde deutlich länger auslaufen. Zusammen mit dem positiven Wert in `0x420[2..3]` nach dem Loslassen spricht das dafür, dass der Motor bei Gas 0 **aktiv bremst**. Das ist eine Hypothese und für den Wheelie-Assist der kritischste offene Punkt, siehe Messung M4.

### Nebenbefund `0x420` (Hypothese)

| Bytes | Beobachtet | Vermutung |
|---|---|---|
| 0..1 u16le | `0x14A3` im Stand, `0x1489` unter Last | Packspannung in 10 mV (52,83 V → 52,57 V) |
| 2..3 s16le | −12 im Stand, −116 unter Last, +546 nach dem Loslassen | Packstrom mit Vorzeichen, positiv = Rekuperation |

Bei 2 Hz eignet sich `0x420` nicht als Regelgröße, wohl aber als Plausibilitätshinweis im Log. Verifikation per Multimeter an Pack-Spannung und Zangenamperemeter steht aus.

## Offene Frage: Wer sendet 0x100?

Das lässt sich mit dem Logic Analyzer am ungeteilten Bus nicht klären: Alle Knoten treiben dieselben zwei Adern, und ein Frame trägt keine Absenderkennung. Die Antwort entscheidet über die ganze Architektur:

- **Fall A: Der Knoten am Lenker (Dashboard) sendet `0x100` an die VCU.** Dann kann ein Gateway zwischen Dashboard-Seite und VCU-Seite Byte 0 verringern. Das ist die Zielarchitektur.
- **Fall B: Die VCU liest das Gas analog ein und sendet `0x100` nur als Statusmeldung.** Dann hat eine Änderung von `0x100[0]` keinerlei Wirkung auf den Motor, und der Eingriff muss am analogen Gassignal oder an einem Frame VCU → MCU erfolgen, der auf diesem Bus womöglich gar nicht sichtbar ist.

### Messmethode

1. **Topologie stromlos vermessen.** Akku ab. Widerstand zwischen CAN-H und CAN-L an jedem zugänglichen Stecker messen, einmal gesteckt und einmal getrennt. Etwa 60 Ω am gesteckten Bus heißt: zwei Abschlusswiderstände à 120 Ω. Aus den getrennten Messungen ergibt sich, auf welcher Seite des Steckers welcher Abschluss sitzt.
2. **Bus am Stecker zwischen Lenker-Seite und VCU-Seite auftrennen,** über ein selbstgebautes Adapterkabel mit getrennten CAN-Adern und durchgeschleiftem GND und Versorgung. Jede Seite bekommt einen Abschluss von 120 Ω, falls ihr der Abschluss der anderen Seite fehlt (Schritt 1).
3. **Beide Segmente gleichzeitig mitschneiden:** CH0/CH1 auf Segment Lenker, CH2/CH3 auf Segment VCU, gemeinsame Masse.
4. **Auswerten:** `0x100` erscheint nur auf dem Segment seines Senders. Hat dieses Segment keinen weiteren Empfänger, bleibt das ACK aus, und der Sender wiederholt den Frame dauerhaft mit ACK-Fehler. Auch das identifiziert ihn eindeutig.
5. **Gegenprobe:** Gas betätigen. Ändert sich `0x100[0]` auf dem Segment, ist dort auch die Gas-Auswertung.

Vorsichtsmaßnahmen: Hinterrad in der Luft, kein Fahrer. Die VCU wird ohne `0x100` sehr wahrscheinlich einen Fehler melden. Den Fehlercode zu notieren ist für das Fail-Safe-Konzept ausdrücklich erwünscht. Nach dem Test Roller aus- und einschalten, bevor er wieder bewegt wird.

Alternative ohne Auftrennen: Ein Oszilloskop mit Differenzmessung zeigt für jeden Knoten einen leicht anderen Dominant-Pegel. Ordnet man die Pegel den IDs zu, erkennt man, welche IDs vom selben Knoten stammen. Mit dem LA1010 geht das nicht, er misst nur digital.

## Nötige Messungen für den Wheelie-Assist

| # | Messung | Aufbau | Liefert |
|---|---|---|---|
| M1 | Sender von `0x100` | Bus auftrennen, siehe oben | Fall A oder B, Einbauort des Gateways |
| M2 | Topologie und Abschlüsse | Widerstandsmessung stromlos | Abschluss je Segment nach dem Auftrennen |
| M3 | Reaktionszeit Gas → Motor | Aufgebockt. Die Hall-Sensor-Leitungen des Nabenmotors (digital, vermutlich 5 V) auf freie LA-Kanäle, dazu CAN auf CH0/CH1. Sprünge Vollgas → Teilgas und Teilgas → Vollgas. | Zeit von der Flanke in `0x100[0]` bis zur Änderung der Hall-Periode, Auflösung im ms-Bereich. Alternativ Stromzange auf eine Motorphase am Oszilloskop. |
| M4 | Rekuperationsschwelle | Aufgebockt, Gas langsam von oben nach 0 zurücknehmen, Stromzange auf die Akkuleitung (Richtung beachten), Hall-Kanäle wie M3 | Gaswert, unterhalb dessen der Motor bremst. Ohne diesen Wert darf der Regler das Gas nie unter diese Schwelle ziehen, solange das Vorderrad in der Luft ist. |
| M5 | Skalierung `0x211` im Fahrbetrieb | Auf dem Gelände je 10 s konstant bei etwa 10, 15 und 20 km/h, GPS-Log der App parallel | Bestätigung oder Korrektur der Hypothese 0,1 km/h/LSB |
| M6 | Verhalten bei fehlendem oder verzögertem `0x100` | Erst mit Gateway-Aufbau aus M1, aufgebockt: `0x100` gezielt 20, 50, 100, 500 ms zurückhalten | Timeout der VCU, Fehlercode, Wiederanlauf. Grundlage des Fail-Safe-Konzepts. |
| M7 | Wheelie ohne Regler, passiv | ESP32 mit IMU und CAN im Listen-Only-Modus, Wheelie-Versuche auf dem Gelände | Pitch, Pitch-Rate, Gas und Geschwindigkeit im echten Wheelie. Daraus Sollwinkel und Reglerverstärkung. |

M3 und M4 lassen sich in einer Sitzung erledigen, vor jedem Bau. Sie beantworten die Frage, ob die Stock-Elektronik schnell genug und ohne Bremsruck auf eine Gasreduktion reagiert.

---

© 2026 Martin Pfeffer | celox.io
