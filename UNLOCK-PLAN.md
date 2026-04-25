# ZT3 Pro D – Entsperr-Ablaufplan

Stand: April 2026. Konkret für den **Segway-Ninebot ZT3 Pro D** (deutsche Variante, Werks-Drossel auf 20 km/h).

---

## ⚠ Wichtige Vorab-Hinweise

| Punkt | Konsequenz |
|---|---|
| **StVZO/eKFV-Zulassung** | Bei Tuning **erlischt** die Allgemeinbetriebserlaubnis. Auf öffentlichen Straßen → Fahren ohne Versicherung + Pflichtversicherungsgesetz §6. |
| **Versicherung** | Versicherungsschutz **erlischt** sofort. Bei Unfall → Selbst-Haftung (auch zivilrechtlich, kann existenzbedrohend sein). |
| **Garantie** | Bei jeder der Methoden weg. Segway erkennt Tuning-Spuren. |
| **Privatgelände & Reverse-Engineering** | In Deutschland legal: § 69e UrhG (Dekompilieren zur Interoperabilität), Privat-Use auf Privatgrund. |

Die Methoden sind nach **Aufwand & Reversibilität** sortiert. Erst die einfachste probieren.

## Dein Scooter prüfen – BEVOR du startest

Notiere dir zwingend (Foto vom Setting-Screen reicht):

1. **Seriennummer (SN)** – auf der Lenkstange unter dem Display oder in der Ninebot-App unter „Geräteinfo". Format: `1K1xNNNNxxxxx`. Die **4. Stelle** kodiert die Region:
   - `D` = Deutschland (20 km/h)
   - `E` = EU-Rest (25 km/h)
   - `U` = USA (40 km/h, Zielzustand für volle Leistung)
   - `Z` / `A` = China / Asien (32 km/h)
2. **VCU-Firmware-Version** (Vehicle Control Unit / DRV) – in Ninebot-App unter „Firmware-Information". Bekannt funktionierend für ST-Link-Hack: **VCU 1.4.8 und 1.4.10**, höhere Versionen funktionieren ebenfalls (laut Forum-Berichten).
3. **Aktuelle km/h-Anzeige im Eco/Drive/Sport-Modus** – als Baseline.

---

## Methoden-Übersicht

| # | Methode | Kosten | Reversibel | Endgeschw. | Schwierigkeit | Risiko |
|---|---------|--------|------------|-----------|---------------|--------|
| 1 | **NBT Unlock Key** (Lizenzcode) | ~70 € | ✅ ja | 38–40 km/h | ⭐ trivial | 🟢 niedrig |
| 2 | **ScooterBoost Chip** (Hardware) | ~50–100 € | ⚠ teilweise | bis 32 km/h | ⭐⭐ Mittel (löten/stecken) | 🟡 mittel |
| 3 | **Dashboard-Tausch** (China-/US-Display) | ~50–60 € | ✅ ja (Original aufheben) | 31–32 km/h (China) / 40 km/h (US-SN) | ⭐⭐⭐ Schraubarbeit | 🟡 mittel |
| 4 | **ZT3Tools + ST-Link V2** (DIY-Hack) | ~15–25 € | ⚠ Memory-Backup nötig | 40 km/h | ⭐⭐⭐⭐ Erfahrung mit STM32 | 🔴 hoch (Brick-Risiko) |
| 5 | **XiaoDash Custom Firmware** | Lizenz pro Controller | ⚠ teils | bis 40 km/h + Features | ⭐⭐⭐⭐ ST-Link nötig | 🟡 mittel |
| 6 | **SHU / SHFW** (Open-Source-CFW) | kostenlos | ✅ | – | ⭐⭐⭐ | 🔴 **funktioniert beim ZT3 Pro NICHT direkt** ([Wiki](https://wiki.bastelpichi.de/compatibility.html)) |

> 💡 **Hinweis zu SHU/SHFW**: Die Open-Source-Tools von scooterhacking.org listen den ZT3 Pro **explizit als nicht unterstützt** (laut [bastelpichi-Wiki](https://wiki.bastelpichi.de/compatibility.html)). Es gibt einige YouTube-Videos die das Gegenteil behaupten – diese beziehen sich aber meist auf **andere Modelle** (G3, F3) oder sind veraltet. Verlasse dich NICHT auf SHU als alleinige Lösung beim ZT3 Pro D.

---

## Empfohlener Ablauf (Pragmatik vor Maximum)

### Phase 1 — Versuch ohne Hardware-Eingriff (1 Stunde)

**Ziel**: 38–40 km/h, voll reversibel, ohne Werkzeug.

**Methode 1 – NBT Unlock Key** (kommerziell, am einfachsten):

1. **Roller in Ninebot-App entkoppeln** (Vorbereitung).
2. **Code kaufen** bei einem dieser Anbieter:
   - [ebiketuningshop.com – NBT Unlock Key ZT3D](https://en.ebiketuningshop.com/products/nbt-unlock-key-1-zt3-tuning-lizenzcode-fuer-ninebot)
   - [schneller-machen.de – Tuning-Lizenzcode](https://schneller-machen.de/products/ninebot-zt3-zt3e-zt3d-zt3-pro-tuning-lizenzcode-bis-zu-40-km-h-via-webapp)
3. **Anleitung des Anbieters** befolgen – meist:
   - WebApp aufrufen, **Code eingeben**
   - **Bluetooth-Kopplung** mit dem Scooter direkt im Browser (Web Bluetooth, Chrome/Edge nötig)
   - Skript läuft 5 Min
   - Reboot Scooter
4. **Test**: max-Speed im Sport-Modus prüfen. Sollte 38–40 km/h erreichen.

✅ Wenn das funktioniert → fertig. Du kannst rückgängig machen (Anbieter unterstützen "Restore"-Mode meist).

### Phase 2 — Dashboard-Tausch (wenn Phase 1 nicht reicht oder zu teuer)

**Ziel**: Region permanent auf US umflashen über Hardware-Swap.

**Aufwand**: 1–2h Schraubarbeit. **Voll reversibel** wenn man Original-Display aufhebt.

1. **Display kaufen** – International / US-Variante:
   - [mikrofahrzeuge.com – Internationale Display Einheit](https://mikrofahrzeuge.com/product/ninebot-zt3-pro-internationale-display-einheit-komplett/) (~52 €)
   - oder AliExpress: Suche „ZT3 Pro dashboard US"
2. **Display ausbauen** – Anleitung [offizielles Segway-Disassembly-Video (Service-Site)](https://service.segway.com/us-en/selfRepair/videoDetail?id=8161&subCategoryCode=H1&subSeriesCode=K139A&seriesCode=K139&seriesName=Segway+ZT3+Series&subSeriesName=Segway+ZT3+Pro)
3. **Neues Display einbauen** – Steckverbindungen 1:1 übernehmen.
4. **Roller einschalten** und mit Ninebot-App neu pairen. Falls Fehlercode 35 (SN-Mismatch) erscheint → Phase 4 ergänzen (SN per ST-Link auf US ändern).
5. Original-Display sicher aufheben für Rückrüstung (z. B. Garantie-Fall).

⚠ China-Dashboards aktivieren **Tempomat (Cruise Control)**, aber **deaktivieren die Blinker** – das ist erwähnenswert weil der ZT3 Pro D Blinker hat.

### Phase 3 — ST-Link-Memory-Hack mit ZT3Tools (Maximum)

**Ziel**: SN-Region-Code permanent auf `U` ändern → 40 km/h, alle Features, kein zusätzliches Display nötig.

**Vorausgesetzt**: VCU-Version aus `1.4.8 / 1.4.10` oder neuer (laut [RollerPlausch-Thread](https://rollerplausch.com/threads/zt3-pro-unlock-40-kmh-dashboard-tausch-oder-st-link-vcu-1-4-8-1-4-10-max-tempomat-zt3scripts.12501/)).

**Hardware (~25 €):**

| Teil | Preis | Bezugsquelle |
|---|---|---|
| ST-Link V2 USB-Programmer (Mini) | 8–15 € | Amazon, AliExpress |
| Dupont-Kabel (m/m, m/f, 4 Stück) | 2 € | beigelegt zum ST-Link oder Amazon |
| TX1-Bit + Innensechskant 2/2.5/3/4 mm | im Werkzeugkasten | – |
| (optional) USB-Verlängerung | 3 € | – |

**Software (Windows-PC):**

| Software | Zweck | Quelle |
|---|---|---|
| **ST-Link USB-Treiber** | erkennt den Programmer | [st.com STSW-LINK009](https://www.st.com/en/development-tools/stsw-link009.html) |
| **STM32CubeProgrammer** | offizielle ST-GUI/CLI für Flash/Memory | [st.com CubeProg](https://www.st.com/en/development-tools/stm32cubeprog.html) |
| **ZT3Tools** (archiviert) | C++-Tool + Batch-Skripte | [github.com/scooterteam/ZT3Tools](https://github.com/scooterteam/ZT3Tools/) (oder Forks) |
| (Alternative) **OpenOCD** + **ScooterFlasher** | Open-Source-Flasher | [github.com/scooter-flasher/ScooterFlasher](https://github.com/scooter-flasher/ScooterFlasher) |

**Schritte:**

1. **Backup zuerst!** Ninebot-Account & App-Zustand mit Screenshot dokumentieren. Roller in der App **entkoppeln**.

2. **Roller öffnen**:
   - Lenkstange / Display abschrauben (offizielles Segway-Tutorial folgen)
   - Bei einigen Versionen muss die VCU im Trittbrett geöffnet werden (häufiger Fall beim ZT3)
   - Die SWD-Pins der STM32 lokalisieren – meist als 4-Pin-Test-Pad markiert mit `SWDIO`, `SWCLK`, `GND`, `3V3`

3. **ST-Link verbinden** (wichtig: Roller AUS, ST-Link aus PC ZIEHEN beim Anstecken am Roller):
   ```
   ST-Link V2 Pin    →    STM32 (auf VCU/Display)
   Pin 1 (SWCLK)     →    SWCLK
   Pin 7 (SWDIO)     →    SWDIO (auch SWIO genannt)
   Pin 9 (GND)       →    GND
   Pin 19 (3V3)      →    3V3       (NUR wenn Roller aus ist!)
   ```
   Je nach ZT3Tools-Variante GND und SWCLK Belegung an Roller-Stecker prüfen – Repo-Doku konsultieren.

4. **Vollständiges Memory-Image dumpen** (Backup!):
   ```cmd
   dump_memory.bat       :: dumpt komplettes Flash → memory_dump.bin
   dump_ram.bat          :: dumpt RAM → ram_dump.bin
   ```
   Beide `.bin`-Files **mehrfach sichern** (USB-Stick + Cloud) – das ist deine Rettung wenn was schiefgeht.

5. **Seriennummer zu US ändern**:
   ```cmd
   change_sn.bat                 :: interaktiv, fragt neue SN ab
   :: oder direkt:
   change_sn_to_us.bat           :: ändert Region-Code auf U (40 km/h)
   ```
   Diese Skripte rufen intern `zt3tool.exe` auf, modifizieren die SN im RAM/Flash und schreiben zurück.

6. **Modifizierte Firmware flashen**:
   ```cmd
   flash_memory_modded.bat       :: flasht modifizierte Firmware
   ```
   Falls etwas schiefgeht:
   ```cmd
   flash_memory_og.bat           :: stellt Original-Firmware wieder her
   ```

7. **Roller wieder zusammenbauen**, einschalten, mit Ninebot-App **neu pairen**.

8. **Test**: Sport-Modus → 40 km/h sollten gehen.

⚠ **Niemals Firmware-Update über die Ninebot-App akzeptieren** nach dem Hack – das überschreibt die SN-Modifikation. Auto-Update in der App **deaktivieren**.

### Phase 4 — XiaoDash CFW (Pro-Mod mit Features)

**Wenn du nicht nur Speed willst, sondern Custom-Profile, Tempomat-Tuning, Field-Weakening etc.**

1. ST-Link-Setup wie Phase 3
2. Android-Smartphone mit OTG-Support
3. [XiaoDash-App](https://www.xiaodash.app/zt3) installieren (APK-Download von der Seite)
4. ST-Link via OTG-Kabel an Smartphone anschließen
5. ST-Link-Pins am STM32 verkabeln (wie Phase 3)
6. In der App **„Flash Custom Firmware" → ZT3** wählen
7. **Lizenz kaufen** (in-App, pro Controller). Preis wird in der App angezeigt – typisch ~30–50 €.
8. Profile konfigurieren (Speed-Limits pro Modus, Cruise-Control etc.)

XiaoDash ist die einzige etablierte Custom-Firmware speziell für ZT3, da SHFW den Roller offiziell nicht unterstützt.

---

## Entscheidungsbaum

```
Willst du nur 40 km/h ohne Werkzeug?          → Methode 1 (NBT Key)
                                                ✅ Wenn du nur fahren willst
Möchtest du es kostenlos und reversibel?      → Methode 3 (Display-Swap)
                                                ✅ Wenn Schraubarbeit OK
Willst du das Maximum + Features + bist Bastler? → Methode 4 (ST-Link + XiaoDash)
                                                ✅ Wenn STM32-Erfahrung
Du suchst Custom Profiles + experimentell?    → ZT3Tools nackt + eigenes Modding
                                                ✅ Wenn du Code lesen kannst
```

---

## Software-Liste (komplett, kondensiert)

### Für alle Methoden
- **Aktuelle Ninebot-App** (Segway Mobility) aus Play Store oder die Analyse aus `apps/ninebot-segway/` weiterverwenden

### Phase 1 (NBT)
- Browser mit **Web Bluetooth**: Chrome ≥ 89 oder Edge auf Android, **Desktop-Chrome funktioniert nicht zuverlässig**
- Optional: VPN (manche Anbieter geo-fencen)

### Phase 3 (ST-Link DIY)
- Windows 10/11 PC
- [STM32CubeProgrammer](https://www.st.com/en/development-tools/stm32cubeprog.html) (offiziell, gratis, registrierungspflichtig)
- [ST-Link USB Driver](https://www.st.com/en/development-tools/stsw-link009.html)
- [ZT3Tools](https://github.com/scooterteam/ZT3Tools/) (archiviert, daher git clone empfohlen während noch verfügbar)
- Alternativ: [stlink-org/stlink](https://github.com/stlink-org/stlink) (Open-Source-CLI auf Linux/macOS)

### Phase 4 (XiaoDash)
- Android-Phone mit USB-OTG
- [XiaoDash APK](https://www.xiaodash.app/zt3) – Anweisungen direkt von der Seite

---

## Hardware-Liste

| Item | Preis | Verwendung | Bezug |
|---|---|---|---|
| ST-Link V2 USB-Programmer | 8–15 € | Phase 3, 4 | Amazon: „ST-Link V2 mini" / AliExpress |
| Dupont-Kabel-Set m/m + m/f (40er Pack) | 4 € | SWD-Verkabelung | Amazon |
| Innensechskant-Set 2–6 mm | ggf. vorhanden | Roller-Demontage | – |
| TX-Schraubendreher (TX10/TX15) | – | Display-Schrauben | – |
| Antistatik-Armband | 3 € | STM32 nicht killen | – |
| (Phase 3) Lab-Power-Supply 3.3V | optional 20 € | Falls Roller-Akku problematisch | – |
| (Phase 4) USB-OTG-Kabel | 5 € | ST-Link an Android | – |

---

## Sicherheits-Checkliste vor jedem Schritt

- [ ] Original-SN fotografieren (auf Roller + in Ninebot-App)
- [ ] VCU-FW-Version notiert (Screenshot)
- [ ] Akku auf > 50 % geladen, NICHT laden während des Flashs
- [ ] Privatgelände gefunden für Geschwindigkeits-Tests (StVO!)
- [ ] (Phase 3+) Memory-Dump auf 2 verschiedenen Speichern gesichert
- [ ] Auto-Update in Ninebot-App deaktiviert (nach Tuning)
- [ ] Versicherung & Zulassung-Bewusstsein (nochmal lesen, oben)

---

## Quellen / weiterführend

### Forum / Community (auf Deutsch)
- [RollerPlausch – ZT3 Pro Unlock-Thread (60+ Seiten, sehr detailliert)](https://rollerplausch.com/threads/zt3-pro-unlock-40-kmh-dashboard-tausch-oder-st-link-vcu-1-4-8-1-4-10-max-tempomat-zt3scripts.12501/)
- [eScooter-Stammtisch – ZT3 Pro D Tuning-Optionen](https://www.escooter-stammtisch.de/forum/index.php?thread/1437-segway-zt3-pro-d-tuning-optionen/=)
- [RollerPlausch – Lösung zum Entsperren des ZT3 Pro](https://rollerplausch.com/threads/loesung-zum-entsperren-des-zt3-pro.12275/)
- [eScooter-Treff – Tuning ZT3 Pro 40 km/h Diskussion](https://www.escooter-treff.de/threads/tuning-zt3-pro-bis-40-km-h-schon-wieder-am-ende.22905/)

### Tools / Repos
- [scooterteam/ZT3Tools (GitHub, archiviert 13.07.2025)](https://github.com/scooterteam/ZT3Tools/)
- [lekrsu/shfw-walkthrough (Open-Source-Walkthrough generischer)](https://github.com/lekrsu/shfw-walkthrough)
- [bastelpichi.de Kompatibilitäts-Wiki](https://wiki.bastelpichi.de/compatibility.html)
- [ScooterHacking Utility offiziell](https://utility.cfw.sh/)
- [XiaoDash für ZT3](https://www.xiaodash.app/zt3)

### Kommerzielle Anbieter
- [E-Bike-Tuning-Shop NBT Unlock Key](https://en.ebiketuningshop.com/products/nbt-unlock-key-1-zt3-tuning-lizenzcode-fuer-ninebot)
- [schneller-machen.de Tuning-Lizenzcode](https://schneller-machen.de/products/ninebot-zt3-zt3e-zt3d-zt3-pro-tuning-lizenzcode-bis-zu-40-km-h-via-webapp)
- [E-Bike-Tuning-Shop ScooterBoost Chip](https://en.ebiketuningshop.com/products/scooterboost-fuer-ninebot-ztprod)
- [Mikrofahrzeuge International Display](https://mikrofahrzeuge.com/product/ninebot-zt3-pro-internationale-display-einheit-komplett/)

### Videos
- [Ninebot 32 km/h Unlock — F3, F3 Pro, ZT3 Pro, G3](https://www.youtube.com/watch?v=CLms_1JeUao)
- [Scooterhacking for noobies – G3 / ZT3 / F3 / GT3 unlock](https://www.youtube.com/watch?v=-y7gnOwjuaw)
- [Akku-Alle.de – Tuning-Übersicht 2026](https://akku-alle.de/blog/segway-zt3-pro-tuning-2026-40-km-h-speed-hack-deutsche-gesetze)
- [Ninebot ZT3 40 km/h Speed Hack](https://www.youtube.com/watch?v=ItDkBcP9wDU)
- [Offizielle Segway-Reparatur-Videos zum ZT3 Pro D](https://www.escooter-stammtisch.de/forum/index.php?thread%2F1473-reparatur-einstellungen-offizielle-anleitungs-videos-zum-segway-ninebot-zt3-pro%2F=)
- [Ninebot ZT3 Pro Tuning per Dashboard-Tausch (DE)](https://www.youtube.com/watch?v=-7p1l98s9-Y)

---

## Anhang – Was wir aus der Decompile-Analyse wissen, das hier hilft

Aus `reverse-engineering/apps/shu/docs/02-BLE-PROTOCOL.md`:

- BLE-Service ist **Nordic UART** (`6e400001-b5a3-f393-e0a9-e50e24dcca9e`) – das nutzen NBT-Key-WebApp und alle Tuning-Tools
- Pairing nutzt **ECDH (secp256r1) + AES-CCM** – falls beim NBT-Key-Vorgang Probleme auftreten, ist das BLE-Pairing-Stack die wahrscheinlichste Ursache
- Manufacturer-Specific-Bytes im Adv-Frame sind `FF 4E 43` ("NC" = Ninebot Crypto) – Diagnose mit nRF Connect

Aus `reverse-engineering/apps/ninebot-segway/docs/03-NETWORK-ENDPOINTS.md`:

- Wenn die Ninebot-App nach einem Tuning-Vorgang Login-Probleme hat: das Backend ist `eu-oms-gateway.ninebot.com` für die Overseas-Variante. Region-Mismatch in der SN kann zu API-Fehlern führen, die teils mit App-Cache-Clear oder Re-Login lösbar sind.
