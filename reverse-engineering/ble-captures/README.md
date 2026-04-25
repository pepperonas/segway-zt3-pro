# BLE-Captures – ZT3 Pro D

Dynamische Analyse von HCI-Snoop-Logs (BLE-Mitschnitte zwischen Android-Phone und Roller). Ergänzt die statische APK-Analyse unter [`../apps/`](../apps/) um echtes Wire-Verhalten.

## Captures

| Datum | Datei | App | Kontext | Doku |
|---|---|---|---|---|
| 2026-04-25 | `bt_capture-v2.pcap`* | **SHU Beta** v3.0 open_beta-5 | vermuteter Multi-Komponenten-Firmware-Flash | [`2026-04-25-shu-flash-session.md`](2026-04-25-shu-flash-session.md) |

\* `.pcap`-Files sind **nicht im Repo** — sie enthalten potenziell identifizierende BLE-Adressen aus der Umgebung. Analyse-Outputs sind anonymisiert.

## Methodik

```bash
# Mitschnitt auf Android (Developer-Options → "Bluetooth-HCI-Snoop-Log aktivieren")
adb pull /sdcard/btsnoop_hci.log

# Auswertung mit tshark
tshark -r capture.pcap -q -z io,phs                              # Protokoll-Hierarchie
tshark -r capture.pcap -V                                        # vollständiger Detail-Dump
tshark -r capture.pcap -Y 'btatt' -T fields -e btatt.opcode -e btatt.handle -e btatt.value
```

Wireshark dekodiert HCI-H4 inkl. ATT/GATT nativ. Verschlüsselte Payloads (AES-CCM des NinebotCrypto-Layers) bleiben ohne Session-Key opak — über **Frame-Struktur, Timing und Längen-Verteilung** lässt sich aber trotzdem rekonstruieren, was passiert ist.

## Was sich aus einem Capture ablesen lässt (auch ohne Key)

| Artefakt | Aussage |
|---|---|
| Service-/Char-UUIDs | Protokoll-Variante (NUS vs. Custom-"ninebot") |
| Adv-Name + Manufacturer-ID | Geräte-Identifikation, Region-Variante |
| Write- vs. Notify-Volumen | Daten-Richtung (Upload = Flash, Download = Dump) |
| Frame-Größenverteilung | "Big-Frame-Bursts" (141 B = max-MTU) ⇒ Bulk-Transfer |
| Bursts vs. Klein-Traffic | Flash-Phasen vs. Parameter-/Telemetrie-Phasen |
| Disconnect/Reconnect-Muster | Komponenten-Reboots nach Teil-Flash |
| Sequenz-Counter im Trailer | Frame-Verlust-Erkennung, Reihenfolge |

## Was sich **nicht** ablesen lässt

- Konkreter Befehl/Parameter (verschlüsselt, ECDH-pro-Session-Key)
- Geflashte Firmware-Datei
- App-interne UI-Aktionen (HCI sieht nur, was raus auf den Wire geht)

Für entschlüsselte Captures müsste der ECDH-Session-Key aus der App extrahiert werden (Frida-Hook auf `crypto/elliptic/d.java#deriveKey` o.ä.) — siehe [`../apps/shu/ANALYSIS.md`](../apps/shu/ANALYSIS.md).
