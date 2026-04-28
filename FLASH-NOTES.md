# ZT3 Pro D — SHU-Beta Flash-Workflow

So habe ich den Roller von DE (20 km/h) auf US (40 km/h) entsperrt. Open Source, kostenlos, reversibel. Methoden mit ST-Link, NBT-Lizenz oder Dashboard-Tausch sind hier bewusst NICHT dokumentiert — siehe Forum-Quellen unten falls relevant.

> ⚠ Verwirft StVZO-Betriebserlaubnis + Versicherung. Privatgelände only.

## Voraussetzungen

- Android-Handy (iOS funktioniert nicht — SHU iOS läuft auf Luna, das ZT3 nicht supportet)
- Bluetooth + Standortdienste an
- ZT3 Pro D auf > 50 % geladen, **nicht am Ladegerät**
- VPN-App mit **Server außerhalb der EU** — `apps-data.cfw.sh` blockt EU-IPs für CFW-Downloads
  - ProtonVPN Free (US/JP/NL) reicht. Server-Land: USA, Schweiz, UK, JP, SG — nur nicht EU.

## Schritte

1. **Roller in offizieller Ninebot-App entkoppeln** (oder Ninebot-App nicht öffnen).
2. **VPN aktivieren** mit Non-EU-Server (vor App-Start!).
3. **SHU-Beta-APK installieren** aus diesem Repo:
   ```
   reverse-engineering/apps/shu/ScooterHackingUtility-pre_release.open_beta-5.apk
   ```
   Identisch zum Stand auf [utility-beta.cfw.sh](https://utility-beta.cfw.sh/).
4. **SHU öffnen** → Disclaimer akzeptieren → **Bluetooth-Scan** → ZT3 Pro auswählen (Roller pingt mit Vibration / LED).
5. **Tab FLASH** → **Load from Repo** (lädt Bootstrap aus `apps-content.cfw.sh/repo/v4/` — VPN nötig!) → **VCU** → letzte verfügbare Version flashen. 2–5 min, Roller darf nicht ausgehen.
6. **Tab FLASH** → **MCU** → letzte Version (analog).
7. **Tab TOOLS** → **Change Region** → **US** → bestätigen.
8. Roller neu starten. Sport-Modus → Anzeige sollte 40 km/h Maximum zeigen.

## Häufige Fehler

| Fehler | Ursache | Fix |
|---|---|---|
| "Repo not reachable" | EU-IP, VPN inaktiv | VPN auf Non-EU prüfen, App neu starten |
| Connection lost beim Flashen | BT-Abriss / Roller zu weit | Phone näher legen, Schritt wiederholen |
| Ninebot-App: Fehler 35 | SN-/Region-Mismatch nach Region-Change | normal — Ninebot-App neu pairen oder erstmal nicht benutzen |
| MCU-Flash hängt | Firmware-Inkompatibilität | Erst neuesten VCU-Build, dann MCU |

## Restore (zurück auf DE-Stock)

In SHU: **TOOLS → Change Region → DE** + ältere VCU/MCU flashen. Vollständig reversibel.

## Quellen

- [utility-beta.cfw.sh](https://utility-beta.cfw.sh/) — SHU-Beta (offiziell)
- [RollerPlausch ZT3 Unlock-Thread](https://rollerplausch.com/threads/zt3-pro-unlock-40-kmh-dashboard-tausch-oder-st-link-vcu-1-4-8-1-4-10-max-tempomat-zt3scripts.12501/) — Forum-Diskussion zu allen Methoden
