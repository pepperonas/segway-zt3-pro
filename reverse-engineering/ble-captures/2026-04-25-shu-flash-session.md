# BLE-Capture: SHU-Beta Multi-Component-Flash

**Datum:** 2026-04-25
**App:** ScooterHacking Utility (SHU) Beta v3.0 `pre_release.open_beta-5`
**Roller:** Segway-Ninebot ZT3 Pro D (anonymisierte S/N `1K1xA****P****`)
**Capture:** `bt_capture-v2.pcap`, 7.5 MB, 49 438 HCI-Frames, **18 min 56 s** Aufzeichnungsdauer

> **TL;DR** – Der Mitschnitt zeigt einen **kompletten Multi-Komponenten-Firmware-Flash** über die SHU-Beta: ~420 KB Payload in 9 zeitlich getrennten Bursts, unterbrochen von Chip-Reboot-Disconnects und einer ~9-min-Konfigurationsphase mit Klein-Frames. Konsistent mit dem SHU-Workflow "BLE-FW → Dashboard-FW → ESC-FW" (oder ähnlicher Reihenfolge), inkl. der erwarteten 3 Reboots.

---

## 1. Geräte-Identifikation

| Feld | Wert | Quelle |
|---|---|---|
| Peer-MAC (anonymisiert) | `XX:XX:XX:XX:XX:XX` | LE Enhanced Connection Complete |
| Adv-Name #1 | `1K1Dx****P****` | Scan Response, Type 0x09 |
| Adv-Name #2 | `1K1Ux****P****` | Scan Response, Type 0x09 |
| Manufacturer Company-ID | `0x434E` (= ASCII `"NC"` little-endian) | EIR/AD, Type 0xFF |
| Connection Role | Phone = Central, Roller = Peripheral | LE Meta 0x0a |

**Beobachtung:** Es werden **zwei separate Adv-Namen** vom gleichen physischen Roller gesendet — `…DA…` und `…UA…`. Das passt zum Doppel-Radio-Design des ZT3 (Dashboard-BLE + IoT/Cellular-Modul, beide advertising-fähig). Die `0x434E`-Manufacturer-ID = `"NC"` markiert die **Crypto-Variante** des NinebotCrypto-Stacks (siehe [SHU-Analyse → BLE-Protokoll](../apps/shu/ANALYSIS.md)).

## 2. GATT-Layer

Service-Discovery liefert **zwei Services**:

### 2.1 Nordic UART Service (Haupt-RPC-Channel)
| Handle | UUID | Properties | Rolle |
|---|---|---|---|
| `0x002F` | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | Write + WriteWithoutResponse | **App → Roller** |
| `0x0031` | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | Notify | **Roller → App** |
| `0x0032` | `2902` (CCCD) | Read + Write | Notify-Aktivierung |

Über diese zwei Charakteristiken läuft **der gesamte Datenverkehr** (10 228 von 10 356 ATT-Frames).

### 2.2 Custom-Service mit "ninebot"-Marker
| Handle | UUID-Suffix (LE) | Properties |
|---|---|---|
| `0x0035` | `…0002…6e696e65626f74` | Write |
| `0x0037` | `…0003…6e696e65626f74` | Write |
| `0x0039` | `…0004…6e696e65626f74` | Notify |
| `0x003C` | `…0005…6e696e65626f74` | Write |
| `0x003E` | `…0006…6e696e65626f74` | Notify |

Die letzten 7 Bytes **`6E 69 6E 65 62 6F 74`** = ASCII `"ninebot"` — der Roller signiert seinen Custom-Service mit dem Hersteller-String. Wird in dieser Session **nicht aktiv genutzt** (keine ATT-Reads/Writes auf 0x0035–0x003E im Daten-Strom), nur via Discovery angefasst.

## 3. Frame-Format auf dem Wire

Jeder ATT-Payload folgt strikt dem Schema:

```
┌───────┬─────┬───────────────────────┬───────┐
│ 5A A5 │ FT  │ encrypted body (var)  │ 03 NN │
└───────┴─────┴───────────────────────┴───────┘
   Magic  Flag       AES-CCM ciphertext        Trailer
```

| Byte(s) | Bedeutung |
|---|---|
| `5A A5` | Frame-Magic (klassischer NinebotCrypto-Pfad — der modernere ECDH-Pfad würde `55 AB` zeigen) |
| `FT` | Frame-Type-Flag: **`0x80`** = große App→Roller-Writes (Flash/Bulk), **`0x01`** = Roller→App-Notifications |
| Body | AES-128/CCM-verschlüsselt (24-Bit-MAC nach SHU-Code) |
| `03 NN` | Trailer mit aufsteigendem 8-Bit-Sequence-Counter `NN` (`0x9F → 0xA0 → 0xA1 → …`, beide Richtungen teilen sich den Counter) |

**Validierung:** Alle 3142 ATT-Payloads in dieser Session beginnen mit `5A A5`. Der Trailer-Counter zählt monoton von `0x9F` bis 8-Bit-Wrap konsistent durch — keine Lücken, keine Reordering-Indikatoren.

## 4. Volumen-Profil

| Richtung | Frames | Dominante Größe | Σ Bytes |
|---|---|---|---|
| App → Roller (Write Cmd, Op `0x52`) | 5 142 | 3 299× **141 B** + 1 656× 14 B | ~488 KB |
| Roller → App (Notify, Op `0x1B`) | 5 086 | 1 938× 14 B + 1 418× 13 B + 1 416× 15 B | ~71 KB |

**Asymmetrie:** Outbound (App→Roller) ≈ **7× mehr Bytes** als inbound. Konsistent mit einem **Schreib-/Upload-Szenario**, nicht mit einem Logfile-Dump (der zeigt umgekehrte Asymmetrie).

Die **141-Byte-Frames** sind das Maximum bei BLE 5.x-MTU (typisch 247 B ATT-MTU minus Header/Padding/Encryption-Overhead) und entsprechen der Bulk-Transfer-Größe, die SHU für Firmware-Chunks verwendet.

## 5. Aktivitäts-Timeline (Bursts & Reboots)

Verteilung der 141-B-Writes ("Big-Bursts") über die Zeit, kombiniert mit HCI-Disconnect-/Reconnect-Events:

| # | Phase | Zeit (mm:ss) | Dauer | 141-B-Frames | Geschätzte Payload* |
|---|---|---|---|---|---|
| **A1** | Big-Burst | `00:00 – 00:10` | 10 s | 102 | ~13 KB |
| **A2** | Big-Burst | `00:12 – 00:34` | 22 s | 462 | ~58 KB |
| **A3** | Big-Burst | `00:35 – 00:56` | 21 s | 457 | ~58 KB |
| ⚡ | **Disconnect** | `03:06` | – | – | Reboot Komponente A |
| ⚡ | **Reconnect** | `03:14` | – | – | – |
| **K1** | Klein-Frame-Phase | `03:14 – 12:20` | ~9 min | 0 (nur 14 B) | Konfiguration / Telemetrie |
| ⚡ | **Disconnect** | `12:20` | – | – | – |
| ⚡ | **Reconnect** | `12:30` | – | – | – |
| **B1** | Big-Burst (Header) | `12:55` | <1 s | 1 | OTA-Init |
| **B2** | Big-Burst | `13:59 – 14:19` | 20 s | 445 | ~57 KB |
| **B3** | Big-Burst | `14:28 – 14:49` | 21 s | 455 | ~58 KB |
| ⚡ | **Disconnect (×4)** | `15:31 – 15:34` | – | – | Reboot Komponente B |
| ⚡ | **Reconnect** | `16:33` | – | – | – |
| **C1** | Big-Burst | `16:45 – 17:07` | 22 s | 455 | ~58 KB |
| **C2** | Big-Burst | `17:31 – 17:54` | 23 s | 460 | ~59 KB |
| **C3** | Big-Burst | `18:04 – 18:31` | 27 s | 462 | ~59 KB |
| ⚡ | **Final-Disconnect** | `19:00` | – | – | Reboot Komponente C |

\* Payload-Schätzung = Frames × 128 B (141 B ATT-Payload abzüglich `5A A5 80` + `03 NN` + AES-CCM-MAC + IV/Counter). Tatsächliche Plain-Bytes je nach Padding ggf. ±10 %.

**Σ Big-Burst-Volumen:** 3 299 Frames × ~128 B ≈ **422 KB** Klartext-Payload, verteilt in **drei Flash-Sessions** (A/B/C) mit je einem Reboot-Zyklus dazwischen.

## 6. Interpretation

Das Muster — drei separate Bulk-Upload-Phasen mit dazwischenliegenden BLE-Reconnects und einer langen Konfigurations-Pause — ist charakteristisch für die **SHU-Multi-Component-Flash-Sequenz**. SHU flasht Komponenten sequenziell, und nach jedem erfolgreichen Schreiben startet das jeweilige Modul neu, wodurch die BLE-Verbindung abbricht.

### Komponenten-Zuordnung (Hypothese)

| Session | Volumen | Plausible Komponente | Begründung |
|---|---|---|---|
| **A** (3 Bursts, ~130 KB) | klein, früh | **BLE-Modul-FW** | Wird typischerweise zuerst geflasht, weil SHU dann mit der neuen FW weiterspricht; Größe passt zum BLE-Chip (typ. 100–130 KB) |
| **B** (1 Header + 2 Bursts, ~115 KB) | mittel | **Dashboard-FW** | Dashboard-Reboot kappt BLE-Verbindung mehrfach (passt zu den 4 Disconnects um 15:31–15:34) |
| **C** (3 Bursts, ~175 KB) | groß, segmentiert | **ESC-FW** (Motor-Controller) | ESC ist die größte Einheit, wird in Sektoren übertragen, finaler Reboot beendet die Session |

Eine alternative Zuordnung (BMS statt Dashboard, oder umgekehrte Reihenfolge) lässt sich ohne Key nicht ausschließen — das beobachtete Reboot-Verhalten passt aber 1:1 zum dokumentierten SHU-Workflow.

### Die K1-Phase (~9 min Klein-Traffic)

Zwischen Session A und B: 9 min lang ausschließlich 14-B-Frames in beiden Richtungen, im Sekunden-Takt. Das ist **kein Idle** — die Frequenz und Bidirektionalität deuten auf:

- Status-Polling (Battery, Speed, Mode)
- Parameter-Reads/-Writes (Speedlimit, KERS, DRR, Region-Setting)
- Verifikation des A-Flashs (Versions-Read)
- Eventuell User-Aktion in der App (Region-Change auf US, was laut [`UNLOCK-PLAN.md`](../../UNLOCK-PLAN.md) Teil des SHU-Workflows ist)

Ohne Decryption nicht weiter aufschlüsselbar.

## 7. Was lässt sich daraus für die statische Analyse ableiten?

| Erkenntnis aus Capture | Konsequenz für [`apps/shu/ANALYSIS.md`](../apps/shu/ANALYSIS.md) |
|---|---|
| Roller benutzt den **NinebotCrypto-Pfad mit `5A A5`-Magic** (nicht den `55 AB`-ECDH-Pfad) | ZT3 Pro D → klassischer Pfad bestätigt; ECDH-Code in `crypto/elliptic/d.java` ist für andere Modelle |
| Manufacturer-ID `0x434E` ("NC") | Match zur Crypto-Variante in `classes/k.java` ✅ |
| GATT läuft **ausschließlich** über NUS-Handles `0x002F`/`0x0031` | Custom-"ninebot"-Service ist Legacy/Reserve, nicht aktiv |
| 8-Bit-Sequenz-Counter im Trailer | Vermutlich Teil des AES-CCM-Nonce — Hinweis für Decryption-Implementierung |
| Outbound-Asymmetrie 7:1 + 3-fach-Reboot-Pattern | Bestätigt SHU als **Schreib-Werkzeug** (Flash), nicht nur Read-Only-Inspector |

## 8. Reproduktion

```bash
# Voraussetzungen
brew install wireshark   # tshark CLI

# Mitschnitt-Stats
tshark -r bt_capture-v2.pcap -q -z io,phs
tshark -r bt_capture-v2.pcap -q -z conv,bluetooth

# Frame-Größenverteilung pro Richtung
tshark -r bt_capture-v2.pcap -Y 'btatt.opcode == 0x52' \
       -T fields -e btatt.value | awk '{print length($1)/2}' | sort | uniq -c

# Burst-Erkennung (Big-Frames in 60-s-Bins)
tshark -r bt_capture-v2.pcap -Y 'btatt.opcode == 0x52' \
       -T fields -e frame.time_relative -e btatt.value | awk '{
  size=length($2)/2; bin=int($1/60)
  if (size==141) big[bin]++; else small[bin]++
} END {for (b=0;b<=20;b++) printf "%3d-%3ds: big=%5d small=%5d\n", b*60,(b+1)*60,big[b]+0,small[b]+0}'

# Connect/Disconnect-Events
tshark -r bt_capture-v2.pcap -Y 'bthci_evt.code == 0x05 or bthci_evt.code == 0x3e' \
       -T fields -e frame.time_relative -e bthci_evt.le_meta_subevent
```

## 9. Limitationen & nächste Schritte

**Was diese Analyse nicht beantwortet:**
- Welche **konkrete Firmware-Version/Datei** wurde aufgespielt?
- Wurde von Stock auf Custom geflasht — oder zurück (Restore)?
- Welche **Parameter-Werte** wurden in der K1-Phase gesetzt (Speedlimit, Region)?
- Wurde der Flash **erfolgreich** vom Roller verifiziert (kein ACK-Inhalt sichtbar)?

**Wege zur vollständigen Decryption:**
1. **Frida-Hook** auf SHU's `crypto/elliptic/d.java` → Session-Key extrahieren während Live-Session
2. **Memory-Dump** der SHU-App nach Pairing → Key aus Heap rekonstruieren
3. **Eigene App** auf Basis des SHU-Source ([`apps/shu/ANALYSIS.md`](../apps/shu/ANALYSIS.md)) — dann ist man selbst im Crypto-Pfad und sieht alles im Klartext

**Empfohlene Erweiterung:** Bei der nächsten SHU-Session den BLE-Snoop **parallel mit `logcat -s BluetoothGatt:* SHUtility:*`** mitschneiden — dann lässt sich jede Wire-Transaktion einer App-Action zuordnen, ohne das Crypto-Layer aufbrechen zu müssen.

---

**Tooling:** tshark 4.x, Python (für Trailer-Counter-Validierung)
**Anonymisierung:** MAC, S/N und Adressen Dritter aus dem Umgebungs-Scan entfernt.
