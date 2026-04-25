# ZT3 Pro D – Entsperr-Ablaufplan

Stand: April 2026. Konkret für den **Segway-Ninebot ZT3 Pro D** (deutsche Variante, Werks-Drossel auf 20 km/h).

---

## ⚠ Wichtige Vorab-Hinweise

| Punkt | Konsequenz |
|---|---|
| **StVZO/eKFV-Zulassung** | Bei Tuning **erlischt** die Allgemeinbetriebserlaubnis. Auf öffentlichen Straßen → Fahren ohne Versicherung + Pflichtversicherungsgesetz §6. |
| **Versicherung** | Versicherungsschutz **erlischt** sofort. Bei Unfall → Selbst-Haftung (auch zivilrechtlich). |
| **Garantie** | Bei jeder der Methoden weg. Segway erkennt Tuning-Spuren. |
| **Privatgelände & Reverse-Engineering** | In Deutschland legal: § 69e UrhG (Dekompilieren zur Interoperabilität), Privat-Use auf Privatgrund. |

---

## Dein Scooter prüfen – BEVOR du startest

Notiere dir zwingend (Foto vom Setting-Screen reicht):

1. **Seriennummer (SN)** – auf der Lenkstange unter dem Display oder in der Ninebot-App. Format: `1K1xNNNNxxxxx`. Die **4. Stelle** kodiert die Region:
   - `D` = Deutschland (20 km/h) ← deine Ausgangslage
   - `E` = EU-Rest (25 km/h)
   - `U` = USA (40 km/h) ← Zielzustand
   - `Z` / `A` = China / Asien (32 km/h)
2. **VCU- und MCU-Firmware-Version** (Vehicle / Motor Control Unit) – in Ninebot-App unter „Firmware-Information"
3. **Aktuelle km/h-Anzeige im Eco/Drive/Sport-Modus** als Baseline

---

## Methoden-Übersicht

| # | Methode | Kosten | Reversibel | Endgeschw. | Schwierigkeit | Risiko |
|---|---------|--------|------------|-----------|---------------|--------|
| **1** ⭐ | **SHU v3 Beta + Region-Change** | **kostenlos** | ✅ ja (re-flash original) | **40 km/h** | ⭐ trivial (Software) | 🟢 niedrig |
| 2 | NBT Unlock Key (Lizenzcode) | ~129 € | ✅ ja | 38–40 km/h | ⭐ trivial | 🟢 niedrig |
| 3 | Dashboard-Tausch (China/US-Display) | ~50–60 € | ✅ ja | 31–40 km/h | ⭐⭐⭐ Schraubarbeit | 🟡 mittel |
| 4 | ZT3Tools + ST-Link V2 (DIY-Hack) | ~15–25 € | ⚠ Memory-Backup nötig | 40 km/h | ⭐⭐⭐⭐ STM32-Erfahrung | 🔴 hoch (Brick-Risiko) |
| 5 | XiaoDash Custom Firmware | Lizenz pro Controller | ⚠ teils | 40 km/h + Features | ⭐⭐⭐⭐ ST-Link nötig | 🟡 mittel |

> 🎯 **Empfehlung**: Methode 1 ist die klar beste – kostenlos, Open Source, ohne Werkzeug, mit Community-Support. Inzwischen unterstützt SHU v3 Beta die "x3-Reihe" (G3, ZT3, F3) vollständig.

> 📝 **Hinweis zu meiner früheren Recherche**: Das [bastelpichi-Wiki](https://wiki.bastelpichi.de/compatibility.html) listet ZT3 Pro noch als "nicht unterstützt" – das ist **veraltet**. Mit der Beta-Variante von `utility-beta.cfw.sh` und neuen VCU/MCU-Repo-Builds geht es seit 2025/2026.

---

## 🥇 Phase 1 — SHU v3 Beta + Region-Change (empfohlen)

**Quelle**: WhatsApp-Anleitung Daniel Hensen (Stand 2026-04). Verwendet die Beta-APK aus dem Repo (`reverse-engineering/apps/shu/ScooterHackingUtility-pre_release.open_beta-5.apk`, identisch zum Stand auf [utility-beta.cfw.sh](https://utility-beta.cfw.sh/)).

### Voraussetzungen

- ✅ **Android-Handy** (zwingend – iOS funktioniert nicht, weil SHU iOS-only über Luna läuft, das beim ZT3 nicht funktioniert)
- ✅ Bluetooth + Standortdienste an
- ✅ ZT3 Pro D zu mindestens 50 % geladen, nicht am Ladegerät
- ✅ **VPN-App** mit Server **außerhalb der EU** (Deutschland-User-Pflicht – das Backend `apps-data.cfw.sh` blockt EU-IPs für CFW-Downloads)

### Empfohlene VPN-Apps (Play Store)

| App | Kostenlos? | Empfehlung |
|---|---|---|
| ProtonVPN | ✅ Free-Plan ausreichend (US/JP/NL-Server) | sehr empfehlenswert |
| Cloudflare WARP | ✅ kostenlos | einfach |
| Mullvad | ❌ ca. 5 €/Monat | datenschutz-stark |
| Windscribe | ✅ 10 GB/Monat free | OK |

→ **Server-Land wählen**: USA, Schweiz, UK, Japan, Singapur o. ä. – nur **nicht** EU.

### Schritt-für-Schritt

1. **Roller in Ninebot-App entkoppeln** (oder Ninebot-App vorerst nicht benutzen).

2. **VPN aktivieren** mit Non-EU-Server (vor App-Start!).

3. **SHU APK installieren** – aus diesem Repo:
   ```
   reverse-engineering/apps/shu/ScooterHackingUtility-pre_release.open_beta-5.apk
   ```
   (Auf Android per Datei-Manager öffnen → "Installation aus unbekannten Quellen erlauben" → Installieren)

   Alternative: direkt von [utility-beta.cfw.sh](https://utility-beta.cfw.sh/) (gleiche Datei, SHA-256: `0a163989e98c1245e983134347f01ca6e5076a76d5e3d0d6e71db3b2cc6275df`).

4. **SHU öffnen → Disclaimer akzeptieren → Continue**.

5. **Bluetooth-Scan** → ZT3 Pro auswählen. Roller pingt sich kurzzeitig (Vibration / LED).

6. **VCU flashen**:
   - Tab **FLASH**
   - **Load from Repo** (lädt Bootstrap aus `apps-content.cfw.sh/repo/v4/` – hier braucht es das VPN!)
   - **VCU** auswählen
   - **Letzte verfügbare Version** flashen
   - Vorgang dauert 2–5 Min, Roller darf nicht ausgehen

7. **MCU flashen** (analog):
   - Tab **FLASH** → Load from Repo → **MCU** → letzte Version
   - Wieder warten

8. **Region ändern**:
   - Tab **TOOLS**
   - **Change Region** → **US** wählen
   - Bestätigen

9. **Roller neustarten**.

10. **Test**: Sport-Modus → Anzeige sollte jetzt 40 km/h Maximum zeigen.

### Häufige Fehler

| Fehler | Ursache | Fix |
|---|---|---|
| "Repo not reachable" / Spinner endlos | EU-IP, kein VPN | VPN auf Non-EU prüfen, App neu starten |
| "Connection lost" beim Flashen | BT abgebrochen / Roller zu weit | Phone näher legen, Schritt wiederholen |
| Ninebot-App zeigt Fehler 35 | SN-/Region-Mismatch nach Region-Change | normal direkt nach Change Region – Ninebot-App neu pairen oder erstmal nicht benutzen |
| MCU-Flash hängt | Selten Firmware-Inkompatibilität | Neuesten VCU-Build vor MCU einspielen |

### Restore (falls du zurück willst)

In SHU: **TOOLS → change region → DE** (oder Original-Region) → wieder ältere VCU/MCU flashen falls gewünscht. Vollständig reversibel solange du keinen Hardware-Eingriff gemacht hast.

---

## 🥈 Phase 2 — NBT Unlock Key (kommerziell, falls Phase 1 nicht klappt)

Software-only ohne Werkzeug, aber kostenpflichtig. ~129 € pro Roller.

**Alternative wenn**: SHU-Repo lädt selbst mit VPN nicht, oder du willst keine VPN nutzen.

1. **Roller in Ninebot-App entkoppeln**
2. **Code kaufen** bei einem dieser Anbieter:
   - [ebiketuningshop.com – NBT Unlock Key ZT3D](https://en.ebiketuningshop.com/products/nbt-unlock-key-1-zt3-tuning-lizenzcode-fuer-ninebot) (~129 €)
   - [schneller-machen.de – Tuning-Lizenzcode](https://schneller-machen.de/products/ninebot-zt3-zt3e-zt3d-zt3-pro-tuning-lizenzcode-bis-zu-40-km-h-via-webapp)
   - [escooter-tuning.de](https://www.escooter-tuning.de/) – ZT3-fähig
3. **WebApp** des Anbieters aufrufen, **Code eingeben**, **Bluetooth-Kopplung** im Browser (Web Bluetooth – Chrome ≥ 89 oder Edge auf Android, Desktop nicht zuverlässig)
4. Skript läuft 5 Min, danach Roller rebooten
5. **Test** im Sport-Modus → 38–40 km/h

> ℹ️ **„German Manöver"**: NBT-Anbieter aktivieren das Tuning oft erst nach einer Tastenkombination (z. B. 5× voll bremsen + 5× voll Gas). Das ist Absicht – schützt vor versehentlichem Tuning, falls Polizei kontrolliert.

---

## 🥉 Phase 3 — Dashboard-Tausch (Hardware-Swap)

Mechanische Lösung ohne Software-Eingriff. ~52 € + 1–2 h Schraubarbeit.

1. **Display kaufen** – International / US-Variante:
   - [mikrofahrzeuge.com – Internationale Display Einheit](https://mikrofahrzeuge.com/product/ninebot-zt3-pro-internationale-display-einheit-komplett/) (~52 €)
   - oder AliExpress: Suche „ZT3 Pro dashboard US"
2. **Display ausbauen** – [offizielles Segway-Disassembly-Video](https://service.segway.com/us-en/selfRepair/videoDetail?id=8161&subCategoryCode=H1&subSeriesCode=K139A&seriesCode=K139&seriesName=Segway+ZT3+Series&subSeriesName=Segway+ZT3+Pro)
3. **Neues Display einbauen** – Steckverbindungen 1:1 übernehmen
4. **Roller einschalten** + Ninebot-App neu pairen. Bei Fehlercode 35 (SN-Mismatch) → Phase 4 ergänzen
5. Original-Display aufheben für Rückrüstung (Garantie-Fall)

⚠ **China-Dashboards**: Tempomat ✅, Blinker ❌. **US-Dashboards**: beides ✅, aber teurer.

---

## 🏗️ Phase 4 — ZT3Tools + ST-Link V2 (DIY-Hack)

Wenn Phase 1–3 nicht reichen oder du es selbst verstehen willst.

**Vorausgesetzt**: VCU `1.4.8 / 1.4.10` oder neuer.

### Hardware (~25 €)

| Teil | Preis | Bezugsquelle |
|---|---|---|
| ST-Link V2 USB-Programmer (Mini) | 8–15 € | Amazon, AliExpress |
| Dupont-Kabel (m/m, m/f, 4 Stück) | 2 € | beigelegt zum ST-Link |
| Innensechskant + TX | im Werkzeugkasten | – |

### Software (Windows-PC)

| Software | Zweck | Quelle |
|---|---|---|
| ST-Link USB-Treiber | erkennt Programmer | [st.com STSW-LINK009](https://www.st.com/en/development-tools/stsw-link009.html) |
| STM32CubeProgrammer | offizielle GUI/CLI | [st.com CubeProg](https://www.st.com/en/development-tools/stm32cubeprog.html) |
| **ZT3Tools** (archiviert) | C++ + Batch-Skripte | [github.com/scooterteam/ZT3Tools](https://github.com/scooterteam/ZT3Tools/) |
| (Alternative) ScooterFlasher | Open-Source-Flasher | [github.com/scooter-flasher/ScooterFlasher](https://github.com/scooter-flasher/ScooterFlasher) |

### Schritte

1. **Backup zuerst**: SN, VCU/MCU-Version, Ninebot-Account-Stand. Roller in App entkoppeln.

2. **Roller öffnen**: Lenkstange/Display abschrauben (offizielles Segway-Tutorial). VCU im Trittbrett oft nötig. SWD-Pins (`SWDIO`, `SWCLK`, `GND`, `3V3`) lokalisieren.

3. **ST-Link verbinden** (Roller AUS, ST-Link aus PC ZIEHEN beim Anstecken):
   ```
   ST-Link V2 Pin    →    STM32 (auf VCU)
   Pin 1 (SWCLK)     →    SWCLK
   Pin 7 (SWDIO)     →    SWDIO (auch SWIO genannt)
   Pin 9 (GND)       →    GND
   Pin 19 (3V3)      →    3V3
   ```

4. **Memory-Image dumpen** (Backup!):
   ```cmd
   dump_memory.bat       :: → memory_dump.bin
   dump_ram.bat          :: → ram_dump.bin
   ```
   Beide `.bin` mehrfach sichern (USB-Stick + Cloud).

5. **SN auf US ändern**:
   ```cmd
   change_sn.bat                 :: interaktiv
   :: oder direkt:
   change_sn_to_us.bat
   ```

6. **Modifizierte Firmware flashen**:
   ```cmd
   flash_memory_modded.bat       :: schreibt Mod
   ```
   Notfall-Restore:
   ```cmd
   flash_memory_og.bat           :: stellt Original wieder her
   ```

7. **Roller zusammenbauen**, einschalten, mit Ninebot-App neu pairen.

8. **Test**: Sport-Modus → 40 km/h.

⚠ **Niemals Firmware-Update über Ninebot-App akzeptieren** nach dem Hack – das überschreibt die SN-Modifikation. Auto-Update in der App **deaktivieren**.

---

## 🛠️ Phase 5 — XiaoDash Custom Firmware (Pro-Mod mit Features)

Für Custom-Profile, Tempomat-Tuning, Field-Weakening etc. Erfordert ST-Link wie Phase 4.

1. ST-Link-Setup wie oben
2. Android-Smartphone mit OTG-Support
3. [XiaoDash-App](https://www.xiaodash.app/zt3) installieren (APK von der Seite)
4. ST-Link via OTG an Smartphone
5. ST-Link-Pins am STM32 wie Phase 4
6. App: **Flash Custom Firmware → ZT3** wählen
7. **Lizenz kaufen** (in-App, pro Controller, ~30–50 €)
8. Profile konfigurieren

---

## Entscheidungsbaum

```
Willst du nur 40 km/h schnell und kostenlos?       → Phase 1 (SHU v3 Beta)  ✅ EMPFEHLUNG
                                                     Aber: VPN + Android nötig
Du hast iOS oder magst keinen VPN?                  → Phase 2 (NBT Unlock Key, ~129 €)
Du willst es ohne jegliche Software-Spuren?         → Phase 3 (Display-Swap)
Du willst es selbst verstehen, mit STM32-Skills?    → Phase 4 (ZT3Tools + ST-Link)
Du suchst Custom-Profile/Field-Weakening?           → Phase 5 (XiaoDash CFW)
```

---

## Sicherheits-Checkliste vor jedem Schritt

- [ ] Original-SN fotografieren (auf Roller + in Ninebot-App)
- [ ] VCU- + MCU-Firmware-Version notiert (Screenshot)
- [ ] Akku auf > 50 % geladen, NICHT laden während des Flashs
- [ ] Privatgelände gefunden für Geschwindigkeits-Tests (StVO!)
- [ ] (Phase 4+) Memory-Dump auf 2 verschiedenen Speichern gesichert
- [ ] Auto-Update in Ninebot-App deaktiviert (nach Tuning)
- [ ] Versicherung & Zulassungs-Bewusstsein

---

## Quellen / weiterführend

### Tools / Repos
- [scooterteam/ZT3Tools (GitHub, archiviert 13.07.2025)](https://github.com/scooterteam/ZT3Tools/)
- [lekrsu/shfw-walkthrough](https://github.com/lekrsu/shfw-walkthrough)
- [ScooterHacking Utility (Stable)](https://utility.cfw.sh/)
- [**ScooterHacking Utility (Beta)** – für ZT3](https://utility-beta.cfw.sh/) ← Phase 1
- [XiaoDash für ZT3](https://www.xiaodash.app/zt3)
- [bastelpichi.de Kompatibilitäts-Wiki](https://wiki.bastelpichi.de/compatibility.html) (veraltet zu ZT3)

### Forum / Community (DE)
- [RollerPlausch – ZT3 Pro Unlock-Thread (60+ Seiten)](https://rollerplausch.com/threads/zt3-pro-unlock-40-kmh-dashboard-tausch-oder-st-link-vcu-1-4-8-1-4-10-max-tempomat-zt3scripts.12501/)
- [eScooter-Stammtisch – ZT3 Pro D Tuning](https://www.escooter-stammtisch.de/forum/index.php?thread/1437-segway-zt3-pro-d-tuning-optionen/=)
- [RollerPlausch – Lösung zum Entsperren des ZT3 Pro](https://rollerplausch.com/threads/loesung-zum-entsperren-des-zt3-pro.12275/)

### Kommerziell
- [E-Bike-Tuning-Shop NBT Unlock Key](https://en.ebiketuningshop.com/products/nbt-unlock-key-1-zt3-tuning-lizenzcode-fuer-ninebot)
- [schneller-machen.de Tuning-Lizenzcode](https://schneller-machen.de/products/ninebot-zt3-zt3e-zt3d-zt3-pro-tuning-lizenzcode-bis-zu-40-km-h-via-webapp)
- [Mikrofahrzeuge.com Internationales Display](https://mikrofahrzeuge.com/product/ninebot-zt3-pro-internationale-display-einheit-komplett/)

### Videos
- [Ninebot 32 km/h Unlock — F3, F3 Pro, ZT3 Pro, G3](https://www.youtube.com/watch?v=CLms_1JeUao)
- [Scooterhacking for noobies – G3 / ZT3 / F3 / GT3 unlock](https://www.youtube.com/watch?v=-y7gnOwjuaw)
- [akku-alle.de – Tuning-Übersicht 2026](https://akku-alle.de/blog/segway-zt3-pro-tuning-2026-40-km-h-speed-hack-deutsche-gesetze)

---

## Anhang – Was wir aus der Decompile-Analyse wissen, das hier hilft

Aus [`reverse-engineering/apps/shu/ANALYSIS.md`](reverse-engineering/apps/shu/ANALYSIS.md) (Abschnitt "BLE-Protokoll"):

- BLE-Service ist **Nordic UART** (`6e400001-b5a3-f393-e0a9-e50e24dcca9e`) – das nutzen NBT-Key-WebApp und SHU
- Pairing nutzt **ECDH (secp256r1) + AES-CCM** – falls beim NBT-Key-Vorgang Probleme auftreten, ist der BLE-Pairing-Stack die wahrscheinlichste Ursache
- Manufacturer-Specific-Bytes im Adv-Frame sind `FF 4E 43` ("NC" = Ninebot Crypto) – Diagnose mit nRF Connect
- Backend-Routen `apps-data.cfw.sh/shfw/v8/{config, fetch, releases}` werden in Phase 1 angesprochen → daher VPN-Pflicht

Aus [`reverse-engineering/apps/ninebot-segway/ANALYSIS.md`](reverse-engineering/apps/ninebot-segway/ANALYSIS.md) (Abschnitt "Network-Endpoints"):

- Wenn Ninebot-App nach Tuning Login-Probleme hat: Backend ist `eu-oms-gateway.ninebot.com` (Overseas-Variante). Region-Mismatch in der SN kann zu API-Fehlern führen, teils mit App-Cache-Clear oder Re-Login lösbar.
