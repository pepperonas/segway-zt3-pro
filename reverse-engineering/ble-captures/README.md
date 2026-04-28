# BLE-Captures — Methodik

HCI-Snoop-Logs sind unser primäres Wire-Verifikations-Tool: Live-Mitschnitt der BLE-Kommunikation zwischen Phone und Roller, beim Bauen / Debuggen der App und beim ESP32-MitM.

## Capture auf Android

```bash
# Einmalig: Developer-Options → "Bluetooth-HCI-Snoop-Log aktivieren"
adb pull /sdcard/btsnoop_hci.log capture.pcap

# Auswertung
tshark -r capture.pcap -V                                  # full detail
tshark -r capture.pcap -Y 'btatt' -T fields \
       -e btatt.opcode -e btatt.handle -e btatt.value      # ATT-Frames als TSV
```

Wireshark dekodiert HCI-H4 + ATT/GATT nativ.

## Was sich ohne Session-Key ablesen lässt

| Artefakt | Aussage |
|---|---|
| Service-/Char-UUIDs | Nordic UART (`6e400001-…`) bestätigt |
| Adv-Manufacturer-Bytes | `FF 4E 43` = NinebotCrypto-Variante |
| Write- vs. Notify-Volumen | Daten-Richtung (Upload = Flash, Download = Read-Sweep) |
| Frame-Größenverteilung | Big-Frame-Bursts (~141 B) ⇒ Bulk / OTA |
| Disconnect/Reconnect-Muster | Modul-Reboots nach Teil-Flash |
| Sequenz-Counter im Trailer | Frame-Verlust-Erkennung |

## Mit Session-Key (= unser Setup)

Da wir die NinebotCrypto-Implementierung in der App haben, können wir Frames live decodieren. Drei Pfade:

1. **Patched-SHU**: `Log.d`-Injection in SHU-Smali → `adb logcat -s CRYPTO_DUMP` zeigt jeden TX in plain. War unser Bug-Debug-Tool. Details: [`../apps/shu/ANALYSIS.md`](../apps/shu/ANALYSIS.md).
2. **App-eigenes Diagnostics-Log** (Ring-Buffer 512 Frames, monospace, Share-as-Text)
3. **ESP32-MitM** (geplant) — Real-Time-Decode aller Frames in beide Richtungen via UART/WiFi-Console.

Für ESP32 wird HCI-Snoop wieder zentral: Verifizieren, dass die ESP32-Adv und der Crypto-Output 1:1 mit dem echten Roller übereinstimmen.

## `.pcap`-Files

Captures liegen **nicht** im Repo (potenziell identifizierende BLE-Adressen aus der Umgebung). Bei Bedarf lokal mit `adb pull` ziehen.
