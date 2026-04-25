# Ninebot ZT3 Pro D Unlock Research

**Ziel:** Verstehen und ggf. replizieren der bekannten Unlock-Methoden für den Segway Ninebot ZT3 Pro D — kommerziell (NBT/MountainTuning), Open Source (ZT3Tools via ST-Link) oder Hardware-Swap (Global-Controller).

**Kontext:** Schwester-Projekt zur G3-D-Research. Der ZT3 Pro D ist interessant, weil er die **direkte Vorlage** der gesamten G3-Tool-Familie ist — MaxG3Tools ist ein Fork von ZT3Tools, nicht umgekehrt.

**Stand:** 2026-04-22

---

## 1. Situationsüberblick

### Der Scooter
- **Segway Ninebot ZT3 Pro D** — deutsche Version mit StVZO-Zulassung, auf 20 km/h gedrosselt
- 1600 W Motor (Dauerleistung), deutlich stärker als G3 D
- Doppel-Teleskop-Federung vorne + Federung hinten, 152 mm Bodenfreiheit
- 11-Zoll-Geländereifen, 25 % Steigungsfähigkeit, TCS (Traction Control)
- Vehicle Controller auf STM32-Basis mit offenem SWD-Port
- BLE-Modul mit NinebotCrypto (AES + SHA-1)
- Apple Find My integriert, AirLock, Smart BMS 2.0
- Gewicht: knapp 30 kg
- Auf Privatgelände durch Community entsperrt: ~30–40 km/h je nach Methode

### Rechtliche Einordnung
Entdrosselung → sofortiger Verlust der eKFV-Betriebserlaubnis. Im öffentlichen Verkehr: Fahren ohne Versicherungsschutz (§6 PflVG, Straftat) + ohne gültige Zulassung (§21 StVG). **Alle beschriebenen Vorgehensweisen gelten ausschließlich für Privatgelände.**

### Die ZT3-spezifische Besonderheit
Laut NBT-Anbieter ist der ZT3 D **komplexer als G2 D oder F2 D** — er basiere auf einem erweiterten Elektroniksystem mit "individuellen Schutzmechanismen, die klassisches Unlocking verhindern". Deshalb ist auch der NBT-Lizenzpreis höher: **~129 €** (ZT3 D) vs. ~45–70 € (G3 D).

### Vier bekannte Angriffswege
1. **NBT Unlock Key / MountainTuning** — kommerziell, ~129 €, Web-BLE, reversibel
2. **Alternative Lizenzanbieter** (escooter-tuning.de, schneller-machen.de) — ~40 km/h
3. **ZT3Tools (scooterteam)** — Open Source, ST-Link via SWD, MIT-Lizenz
4. **Controller-Swap** — AliExpress 30-km/h-Controller-Mainboard oder China-Dashboard
5. **ScooterBoost** — Tuning-Chip (Plug-and-Play), kommerziell

---

## 2. ZT3Tools — Die Basis aller G3/ZT3-Tools

### Meta-Info
Repo: `https://github.com/scooterteam/ZT3Tools` (MIT, v3 vom 16. Feb 2025)
**Wichtig: Das Repo wurde am 13. Juli 2025 archiviert (read-only).** Stand zum Zeitpunkt der Archivierung ist eingefroren.
Hauptautor: `encryptize` (einziger Contributor)

**Dies ist die Vorlage, auf der MaxG3Tools basiert** — erkennbar an der identischen Dateistruktur:
```
zt3tool.cpp                    (entspricht G3tool.cpp)
change_sn.bat
change_key_to_default.bat
dump_memory.bat
dump_ram.bat
fix_key.bat
flash_memory.bat
flash_memory_modded.bat
flash_memory_og.bat
```

Interessanter Unterschied zu MaxG3Tools: **Kein explizites `verify`-Script im ZT3-Repo**. Die Verify-Logik wurde erst in der G3-Ableitung hinzugefügt.

### Architektur-Analogie zu MaxG3Tools
Basierend auf der bekannten G3tool.cpp-Struktur ist davon auszugehen, dass zt3tool.cpp praktisch identisch arbeitet:

1. **Region-Pattern-Suche** im Memory-Dump (vermutlich analog `"1CG"` + Offset, aber ZT3-spezifische Bytes)
2. **Region-Byte-Flip** (B↔C oder entsprechende Varianten)
3. **Key-Replacement** mit Default-Key (BLE-Pairing-Reset)
4. **Optional: Key-Migration** (alten Key ins neue Dump übernehmen)

**Offener Punkt für echte Verifikation:** Die konkreten String-Anker und Flash-Offsets im zt3tool.cpp müssen vor Einsatz ausgelesen werden. Aus Erfahrung mit MaxG3Tools dürfte die Struktur sein:
- `SCOOTER_VCU_xxZT3` (oder ähnlich) als Key-Anker
- Modell-spezifischer SN-Präfix als Region-Anker
- Offset `0x1F000` oder ähnlich für den Data-Bereich

### Hardware-Requirements (identisch zu G3)
- ST-Link V2 (~5 €, AliExpress oder Segor Berlin)
- Dupont-Kabel female/male
- Torx + Philips Set
- **Vermutlich auch ein C45-Äquivalent als Short** — muss am ZT3-PCB lokalisiert werden

### SWD-Pinbelegung
Analog G3:
- SWDIO
- SWCLK
- GND
- 3.3V (Pflicht — Chip-Power-Requirement)

### Workflow (analog G3)
```
1. Unbind Scooter in Segway-App
2. Dashboard abschrauben, Kabel lösen
3. Trittbrett öffnen, VCU freilegen
4. SWD + ggf. Read-Protect-Bypass-Short verkabeln
5. dump_ram.bat       → RAM-Dump
6. dump_memory.bat    → MEMORY.bin (Backup!)
7. change_sn.bat      → Region wählen
                        → MEMORY_modded.bin
8. flash_memory_modded.bat → schreibt Modifikation
9. Zusammenbauen, App rebind
```

Restore: `flash_memory_og.bat` setzt auf Werk zurück.

### Archivierungs-Implikation
Da das Repo seit Juli 2025 archiviert ist: **Kein aktiver Support**. Neue Firmware-Versionen von Ninebot werden nicht mehr abgedeckt. Wenn dein ZT3 Pro D mit einer Firmware >Februar 2025 ausgeliefert wurde, musst du ggf. selbst die Offsets anpassen. Das Fork-Prinzip (wie bei MaxG3Tools) zeigt aber: Das Anpassen auf neue FW-Versionen ist machbar, wenn man ein Baseline-Dump hat.

---

## 3. Kommerzielle Lösungen

### NBT Unlock Key (MountainTuning, EBikeTuningShop.com)
- **Preis: ~129 €** für ZT3 D (vs. 45–70 € für G3 D)
- Browser-basiert, Web BLE API
- iOS: Bluefy Browser, Android: Chrome/WebBLE
- **"German Manöver"**: Nach jedem Neustart ist der Scooter wieder im Werksmodus (20 km/h). Erst durch Tastenkombination wird der Tuning-Modus aktiviert (typisch: 5× voll bremsen + 5× voll Gas, oder 5× links/rechts Bremshebel)
- Reversibel via Webportal
- Features: Dauerhaftes Entsperren (kein Reset nach Neustart), Separate Profile (getunt vs. original), individuelle Freischaltekombination, Lizenzcode-Speicherung

**Voraussetzungen:**
- Original-Controller + Original-Dashboard
- Nie zuvor getunt
- Firmware muss kompatibel sein (Vorsicht bei Updates!)

### Alternative Anbieter
- **escooter-tuning.de** — G3/GT3D/ZT3/G30D2/G2/F3 via Lizenzcode, Custom-Tastenkombi konfigurierbar, Gen2-Support
- **schneller-machen.de** — bis zu 40 km/h, bewirbt WebApp-Aktivierung, Made in Germany
- **scooterwerkstatt.de** — ähnliches Produkt-Angebot, Tuning-Sperre-kompatibel beworben

### ScooterBoost (Tuning-Chip)
- Hardware-basiert, Plug-and-Play
- Hebt Geschwindigkeitsbegrenzung auf, ohne Motorleistung zu verändern
- Eigene Firmware, reversibel
- Thematisiert im escooter.blog ZT3-Pro-D-Testbericht

### Wichtige Einschränkung aller Lizenz-Produkte
> "Im Gegensatz zu einfacheren F2D oder G2D Modellen basiert der ZT3D auf einem fortgeschrittenen Elektronik-System mit individuellen Schutzmechanismen, die klassisches Unlocking verhindern. NBT Unlock Key für ZT3D wurde umfangreich angepasst." — EBikeTuningShop.com

Das erklärt den höheren Preis und auch, warum der ZT3 Pro D erst später als andere Modelle kompatibel wurde (Kalenderwoche 10, Anfang 2025).

---

## 4. Controller-Swap (Hardware-Weg)

### AliExpress Global-Controller
Community-Forum (eScooter-Stammtisch.de) beschreibt dokumentiert:
- **"Original 30 km/h Controller Mainboard Für Segway Ninebot ZT3/ZT3 Pro"** auf AliExpress verfügbar
- Alternative: China-Dashboard einbauen (Seriennummer ist dort hinterlegt)
- **Einschränkung China-Dashboard:** Blinker funktionieren nicht
- Vorteil: Zero Start + 31 km/h Endgeschwindigkeit ab Werk

### Strategischer Tip aus dem Forum
> "Es wäre allerdings wichtig, dass ihr eine neue Seriennummer mit dem Controller bekommt, damit die Segway APP Euren ZT3D dann auch als original ZT3 annimmt."

Das ist die Kernarchitektur: Die Region/Modell-Identität **hängt an der Seriennummer im Controller oder Dashboard**, nicht am Gehäuse. Controller oder Dashboard mit anderer SN einbauen = anderes Modell.

### Software-Update-Warnung
> "Solange macht besser kein offizielles Firmware-Update von Segway Ninebot, wenn ihr dazu aufgefordert werdet!"

Wichtig für alle Wege: **Updates können Tuning-Kompatibilität brechen**. Das gilt für alle Unlock-Methoden.

---

## 5. Ninebot BLE-Protokoll — gleiche Basis wie G3

Der ZT3 Pro D spricht denselben Protokoll-Stack wie der G3 D und alle anderen neueren Ninebot-Modelle:

```
┌─────────────────────────────────────────┐
│  4. Commands (Read/Write Reg, IAP, SN)  │  ninebot-docs Wiki
├─────────────────────────────────────────┤
│  3. Packet Framing (5A A5 ... Checksum) │  py9b.transport
├─────────────────────────────────────────┤
│  2. Crypto (AES + SHA-1, session-based) │  NinebotCrypto
├─────────────────────────────────────────┤
│  1. BLE Transport (Nordic UART Service) │  Web Bluetooth / Bleak
└─────────────────────────────────────────┘
```

Siehe die G3-D-README für die Details. Die gesamte Protokoll-Infrastruktur ist identisch. **Unterschied:** Der ZT3 D hat vermutlich zusätzliche Schutzmechanismen (wie von NBT explizit erwähnt) — welche genau, ist nicht öffentlich dokumentiert.

### Pairing-Flow (Standard Ninebot 2020+)
```
1. Phone → Scooter: 3E 21 5B 00                    (Session-Start)
2. Scooter → Phone: 21 3E 5B 01 + BLE-Key + SN
3. Phone → Scooter: 3E 21 5C 00 + 16 random bytes
4. USER DRÜCKT POWER-BUTTON AM SCOOTER             (Out-of-band-Auth)
5. Scooter → Phone: 21 3E 5C 00, dann 21 3E 5C 01
6. Phone → Scooter: 3E 21 5D 00 + Serial Number
7. Scooter → Phone: 21 3E 5D 01                    → Paired!
```

### Command-Tabelle (relevante für ZT3)
| Cmd | Arg | Payload | Wirkung |
|-----|-----|---------|---------|
| `01` | ofs | `[len]` | Read registers |
| `02` | ofs | `data[]` | Write registers |
| `07` | any | `updateSize` | Start Firmware Update (IAP) |
| `08` | idx | `data[]` | Write Firmware Chunk |
| `09` | any | `checksum` | Finish Update |
| `0A` | any | — | Reboot |
| `18` | `10` | `newSN[]`, `dAuth` | **Program Serial Number** — der reale Unlock-Command |
| `57` | any | `dAuth0, dAuth1, dReg69` | Activate mit Limit |
| `58` | any | `dAuth0, dAuth1, wUnused` | Factory Reset |
| `59` | any | — | "Activate ohne Limit" — **funktioniert nicht in der Praxis** (siehe Command-59-Research zum G3) |
| `5C` | any | `dAuth, dOdometer, wUnused` | Set Odometer |

**Wichtiger Hinweis aus der G3-Research:** Command `0x59` ist ein Protokoll-Relikt. Die Community nutzt stattdessen CFW + Command `0x18 10` (Program Serial Number) für das Region-Unlock. Gleiches gilt vermutlich für ZT3.

---

## 6. Die drei radikal unterschiedlichen Angriffswege

### Weg A: ST-Link via SWD (ZT3Tools) — permanent, open source
- Aufschrauben erforderlich
- Dauerhaft gültig (bis zum nächsten Firmware-Update)
- Kein Abo, einmalig 5 € Hardware
- Community-Toolchain **archiviert** (Juli 2025) — ggf. selbst pflegen

### Weg B: BLE-OTA via Lizenz-Anbieter (NBT/MountainTuning) — kein Aufschrauben
- 129 € Lizenz
- Wireless, Browser-basiert
- Reversibel
- "German Manöver" nach jedem Einschalten — bewusste Aktivierung nötig
- Anbieter pflegt Firmware-Kompatibilität

### Weg C: Hardware-Swap (Global-Controller / China-Dashboard) — Bauteil-Tausch
- Global-Controller von AliExpress: ~30-50 €
- China-Dashboard als Alternative (aber: Blinker-Verlust)
- Dauerhaft
- Kein Software-Eingriff — Firmware ist original, nur Hardware anders

### Weg D: Eigener BLE-Patcher (1dragon-Klon) — Research-Projekt
Siehe G3-D-Research `COMMAND_59_RESEARCH.md` — analog auf ZT3 übertragbar. Schätzaufwand: **4-6 Wochen konzentrierter Arbeit**, plus zusätzliche Komplexität durch ZT3-spezifische Schutzmechanismen.

---

## 7. Vergleich G3 D vs. ZT3 Pro D

| Aspekt | G3 D | ZT3 Pro D |
|--------|------|-----------|
| Motor | 2000W Peak | 1600W Dauer |
| Gewicht | 24,6 kg | ~30 kg |
| Federung | Doppelte hydraulische | Doppel-Teleskop + hinten |
| Reifen | 11" tubeless | 11" All-Terrain |
| Community-Toolchain | aktiv (MaxG3Tools v1.2 Mai 2025) | **archiviert** (ZT3Tools v3 Feb 2025) |
| Schutzmechanismen | Standard Ninebot | "erweitert" (NBT-Aussage) |
| NBT-Lizenz-Preis | ~45-70 € | ~129 € |
| ST-Link-Weg | MaxG3Tools | ZT3Tools |
| Controller-Swap AliExpress | Ja | Ja, auch China-Dashboard |
| Empfehlungspriorität | G3 D einfacher | ZT3 komplexer |

Der ZT3 Pro D ist ein Schritt härter als der G3 D. Die Community-Tools sind da, aber weniger gepflegt (Archivierung). Die kommerziellen Anbieter mussten mehr Arbeit investieren (Preis + später verfügbar).

---

## 8. Hardware-Umfeld ZT3 Pro D

### Leistung / Technik
- 1600 W Dauerleistung (Motor), Peak-Leistung im Produkt nicht spezifiziert
- 48V-System
- Traction Control System (TCS)
- BMS 2.0 mit Lebenszyklus-Überwachung
- FlashCharge: ~4h Ladezeit

### Sicherheit / Bremsen
- Vordere + hintere Doppelscheibenbremsen
- Federung vorne (Teleskop) + hinten

### App-Integration
- Segway Mobility App (Registrierungszwang)
- Apple Find My
- AirLock
- 3" LCD-Display
- Zero Start verfügbar (muss aktiviert werden)

### App-Einschränkungen (Stand Feb 2025, aus Forum)
> "in der Segway App, das X-Frontlicht und Regelung der Höchstgeschwindigkeits-Stufen deaktiviert wurde"

Segway-Ninebot hat nach Release Features per App-Update deaktiviert. Das ist Grund genug, App-Updates mit Vorsicht zu behandeln — nicht nur wegen Tuning, sondern auch wegen Feature-Erosion.

---

## 9. Relevante Repositories

### Primäre Referenzen
- `scooterteam/ZT3Tools` — ST-Link-Weg für ZT3, **archiviert Juli 2025**, MIT
- `amirabasalinaghi/MaxG3Tools` — ZT3Tools-Fork für G3, aktiv
- `scooterteam/scooterflasher` — OpenOCD-Wrapper für ST-Link-basiertes Flashen vieler Scooter
- `scooterhacking/firmware` — Firmware-Mirror
- `scooterhacking/NinebotCrypto` — Crypto-Library

### Sekundäre Referenzen
- `etransport/ninebot-docs` — Protokoll-Wiki (2018-2020, Basis)
- `etransport/py9b` — Python-BLE-Library (frozen Nov 2019)
- `ArchGryphon9362/asu-app` — Moderne Swift-App
- `lekrsu/shfw-walkthrough` — SHFW-Kompatibilitätsmatrix für Ninebot

### ScooterFlasher CLI-Features
```
python -m scooterflasher \
  --device {max,esx,e,f,t15,g2,4pro,f2,m365,pro,pro2,1s,lite,mi3} \
  --target {BLE,ESC} \
  [--sn SN] \
  [--km KM] \
  [--fake-chip] \
  [--activate-ecu] \
  [--custom-fw CUSTOM_FW]
```

**Wichtig: ZT3 ist in der `--device`-Liste aktuell NICHT explizit gelistet.** Entweder unter einem Alias (z.B. über `--device max`) oder nicht offiziell supportet. Das ist ein offener Research-Punkt.

### Community-Foren
- `eScooter-Stammtisch.de` — Deutsche Tuning-Community, Thread #1437 für ZT3 Pro D
- `rollerplausch.com` — Thread #13708 zu Ninebot-Lizenzcode-Freischaltung
- `scooterhacking.org/forum` — Hauptforum der Community

---

## 10. Strategische Einordnung

### Bei ZT3 Pro D empfohlener Pfad

**Für "einmalig entsperren, dann ruhe" — Weg C (Controller-Swap):**
AliExpress Global-Controller bestellen, einbauen, neue SN registrieren. ~30-50 €. Keine Software-Fummelei, keine Abo-Gebühr, dauerhaft. Einziger Nachteil: ~2-4h Arbeit.

**Für "jetzt schnell, minimaler Aufwand" — Weg B (NBT-Lizenz):**
129 €, keine Schraube lösen, German Manöver wird bewusste Aktivierung erzwingen (eigentlich sinnvoll als Schutz vor Versehen). Anbieter pflegt Kompatibilität mit Firmware-Updates.

**Für "selbst verstehen und dokumentieren" — Weg A (ZT3Tools + SWD):**
5 € Hardware, MIT-lizenziertes Repo, aber archiviert. Wenn die Community-Arbeit veraltet: selbst nachpflegen nötig. Gutes Lern-Projekt, aber Wartungslast bei FW-Updates trägt jetzt du selbst.

**Für "Eigenes Tool bauen" — Weg D:**
Ähnlich aufwendig wie bei G3, plus ZT3-spezifische Schutzmechanismen. Erwartbar: 4-8 Wochen. Für ein celox.io-Blog-Projekt machbar, für kommerzielle Nutzung nicht rentabel gegen NBT.

### Content-Potenzial für celox.io
Die ZT3-Research ist **weniger attraktiv als G3** für einen Blog-Post, weil:
- Die Community-Tools sind **archiviert** (weniger Aktualität)
- Die Lizenz-Anbieter haben den Markt bereits besetzt
- Der Aufwand zum Dokumentieren ist hoch, der Informationsgewinn gegenüber MaxG3Tools gering

**Aber:** Ein Vergleichs-Artikel "Warum der ZT3 schwerer ist als der G3 D" mit technischem Deep-Dive in die Schutzmechanismen wäre technisch interessant. Voraussetzung: eigene SWD-Analyse + Diff gegen G3-Dump.

---

## 11. Offene Research-Fragen (nächste Schritte)

1. Welche konkreten Byte-Patterns nutzt zt3tool.cpp? (Raw-File aus GitHub holen)
2. Welcher Kondensator am ZT3-PCB entspricht dem C45 beim G3?
3. Was sind die "individuellen Schutzmechanismen" des ZT3 im Unterschied zum G3?
4. Gibt es für den ZT3 eine `--device`-Option in scooterflasher?
5. Läuft NBT beim ZT3 technisch anders als beim G3? (IAP-Chain vs. Memory-Write?)
6. AliExpress Global-Controller: Welche Listings sind verifiziert und haben passende Firmware?
7. China-Dashboard: Woher, welche Features funktionieren, welche nicht?
8. Welche Firmware-Version wird aktuell mit neuen ZT3 Pro D ausgeliefert (April 2026)?

---

## 12. Quellen

- scooterteam/ZT3Tools Repo (Status: archiviert Juli 2025)
- amirabasalinaghi/MaxG3Tools Repo (Referenz-Architektur)
- scooterteam/scooterflasher Repo
- EBikeTuningShop.com Produktseite NBT Unlock Key ZT3D
- escooter.blog "Ninebot ZT3D Tuning" Artikel
- escooter.blog "Ninebot ZT3 Pro D Testbericht"
- emobility-insider.de "ZT3 Pro D Tuning MountainTuning"
- escooter-stammtisch.de Thread #1437
- rollerplausch.com Thread #13708
- mein-escooter.de Produktseite ZT3 Pro D
- schneller-machen.de / scooterwerkstatt.de / escooter-tuning.de
- Siehe auch die parallele G3-D-Research (README.md + COMMAND_59_RESEARCH.md)

---

© 2026 Martin Pfeffer | celox.io
