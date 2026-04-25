# SHU Backend & Update-Mechanismus

Im Gegensatz zur Ninebot-App nutzt SHU eine eigene, kompakte Backend-Infrastruktur unter der Domain **`cfw.sh`** ("CFW" = Custom Firmware).

## Endpoints

| URL | Zweck | Aufrufer |
|---|---|---|
| `https://apps-data.cfw.sh/utility/update` | Update-Manifest abfragen (POST mit Install-ID + Track) | `UpdateCheckerActivity` |
| `https://apps-data.cfw.sh/utility/download_apk` | Self-Update-APK-Download | `UpdateCheckerActivity` |
| `https://apps-data.cfw.sh/utility/<file>` | Beliebige Konfig-Dateien (POST) | `UpdateCheckerActivity` |
| `https://apps-content.cfw.sh/repo/v4/<name>.zip` | Scooter-DB / Asset-Bundles (z. B. `bootstrap.zip`) | `c0.java:168` |
| `https://apps-data.cfw.sh/shfw/v8/config` | SHFW-Config-Endpunkt | (SHFW-Modul) |
| `https://apps-data.cfw.sh/shfw/v8/fetch` | SHFW-Fetch-Endpunkt | (SHFW-Modul) |
| `https://apps-data.cfw.sh/shfw/v8/releases` | SHFW-Release-Liste | (SHFW-Modul) |
| `https://utility.cfw.sh/` | Web-Landingpage | im Disclaimer-Dialog |
| `https://utility.cfw.sh/gplay` | Google-Play-Variante-Landing | im Disclaimer-Dialog |
| `https://cfw.sh/eula` | EULA-HTML | im Disclaimer-Dialog |
| `https://scooterhack.in/bugreport` | Bug-Tracker | "Report Bug"-Dialog |
| `https://scooterhack.in/shutprivacy` | Privacy-Policy | im Disclaimer-Dialog |
| `mailto:scamwatch@scooterhacking.org` | Scam-Meldung | `j.java:138` |

Alle Calls laufen über HTTPS, kein Cleartext zugelassen.

## Repo-Schema (`apps-content.cfw.sh/repo/v4/`)

Die App lädt zwei ZIPs in den App-internen Storage:

| Name | Quelle | Zweck |
|---|---|---|
| `bootstrap.zip` | `repo/v4/bootstrap.zip` | Liste der unterstützten Scooter (`beacons.json`), Vehicle-Bilder, Default-Configs |
| `data.zip` | `repo/v4/data.zip` (vermutet) | Erweiterte Daten (SHFW-Profile, Firmware-Pakete) |

Das ZIP wird von `i0.b(context, "bootstrap")` ausgelesen:

```java
ZipInputStream zis = new ZipInputStream(...);
while ((entry = zis.getNextEntry()) != null) {
    map.put(entry.getName(), readEntireStream(zis));
}
```

Pro Eintrag wird der Name auf einen Byte-Array gemappt – das schließt sowohl JSON-Listen (`beacons.json`) als auch Bilder (`g30.png`, `f40.png`, etc.) ein.

`i0.getBeaconsArray()` liefert dann das `JSONArray`, gegen das der `BeaconParser` jedes BLE-Adv-Frame matcht.

## Update-Mechanismus

`UpdateCheckerActivity.onCreate()` ruft beim App-Start `https://apps-data.cfw.sh/utility/update` mit folgendem JSON-Body (rekonstruiert aus dem Code):

```json
{
  "track": "pre_release.open_beta",
  "current_version": 5,
  "install_id": "<UUID>",
  "package_name": "sh.cfw.utility.pre_release.open_beta"
}
```

Antwort enthält (mutmaßlich):
- `latest_version` – Build-Nummer
- `download_url` – wenn Update verfügbar
- `disabled_message` – wenn Track abgeschaltet wurde

Bei aktivem Beta-Track sind Self-Updates möglich (Permission `REQUEST_INSTALL_PACKAGES`!).

## Einer Disclaimer-Texte (Auszug)

Aus `j.java:170` werden bei erstem App-Start drei Links präsentiert:

> ScooterHacking Utility · [https://utility.cfw.sh/](https://utility.cfw.sh/)
> EULA: [https://cfw.sh/eula](https://cfw.sh/eula)
> Privacy: [https://scooterhack.in/shutprivacy](https://scooterhack.in/shutprivacy)

In der Google-Play-Variante (Detection via `u(context)`) wird stattdessen `https://utility.cfw.sh/gplay` als Hauptlink eingeblendet.

## Beta-Enrollment (`EnrollActivity`)

`EnrollActivity` zeigt eine "Install ID" und Anweisungen, sich für den Pre-Release-Track einzuschreiben. Die Install-ID wird vermutlich an `scooterhacking.org` gepostet (aus dem App-Code nicht direkt sichtbar, vermutlich nur als sichtbare ID, die der User selbst irgendwo einträgt).

## SHFW (Scooter Hacking Firmware)

Der Modul-Name "SHFW" zieht sich durch das gesamte Code-Layout (`sh.cfw.utility.classes.shfw`, `sh.cfw.utility.models.shfw`, `sh.cfw.utility.ui.shfw`). Das bezieht sich auf die Open-Source-Custom-Firmware für Ninebot/Xiaomi-Scooter, die über die App auf den Scooter geflasht wird.

Die SHFW-Backend-Routen (`apps-data.cfw.sh/shfw/v8/...`) liefern:
- `config` – Profile-Definitionen
- `fetch` – Profile-Inhalt (binär)
- `releases` – Liste verfügbarer SHFW-Versionen

Entscheidend: **der ZT3 Pro hat einen anderen Hauptcontroller als die alten G30/F-Series** (laut Community-Berichten ein eigener STM32-basierter ESC). Ob SHFW für den ZT3 Pro existiert, ist Backend-abhängig und ändert sich häufig – aktueller Stand auf `https://utility.cfw.sh/` checken.

## Zusammenfassung für die ZT3-Pro-Analyse

- Backend ist überschaubar (4 Domains, alle HTTPS)
- Keine API-Keys oder Secrets im Code
- Modell-DB wird **dynamisch nachgeladen** (`bootstrap.zip`) – die App selbst weiß zur Build-Zeit nicht, ob der ZT3 Pro unterstützt ist
- Um zu prüfen, ob SHU einen ZT3 Pro erkennt: `bootstrap.zip` herunterladen (`https://apps-content.cfw.sh/repo/v4/bootstrap.zip`) und in der enthaltenen `beacons.json` nach Modellen suchen, die "ZT3" / "Z3T" / "ZTPRO" o. ä. matchen.
