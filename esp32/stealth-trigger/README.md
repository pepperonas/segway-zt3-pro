# stealth-trigger — ESP32 CAN-Sniffer mit Custom-Patterns

Passiver CAN-Sniffer am ZT3-VCU-Bus. Erkennt 3 vom Fahrer ausführbare
Patterns auf Bremse, Throttle und Mode-Knopf und sendet entsprechende
Trigger-Notifications via BLE an die Phone-App.

> **Status:** Sketch ist code-complete, Hardware-Test ausstehend (warten auf ESP32-S3 + SN65HVD230).

## Patterns

| # | Pattern | Trigger | Use-Case |
|---|---------|---------|----------|
| A | 3× Brake innerhalb 2 s | `STEALTH_LOCK` | Lock auch ohne Phone-Reichweite triggern |
| B | Throttle ≥0x80 für 5 s + Brake-Tap | `CRUISE_REQUEST` | Tempomat-Geste, Phone-App entscheidet was tun |
| C | 5 Mode-Wechsel innerhalb 3 s | `PROFILE_SWITCH` | Custom-Profile-Wechsel oder Hidden-Mode |

Schwellwerte und Zeitfenster in [`config.h`](config.h) anpassbar.

Cooldown von 3 s nach jedem Trigger verhindert dass mehrere Patterns
gleichzeitig feuern.

## Hardware

| Bauteil | Modell | Kosten | Zweck |
|---------|--------|--------|-------|
| MCU | **ESP32-S3 DevKit** (z.B. WROOM-1 N16R8) | 8–12 € | TWAI + BLE on-chip, kein Extra-Chip nötig |
| CAN-Transceiver | **SN65HVD230** Modul | 1–3 € | 3.3V-compatible, direkt an ESP32-Pins |
| Optional: Buck-Regler | MP1584 | 2 € | wenn Stromversorgung vom Roller-12V-Bus statt USB |

**Verkabelung am Roller:**

```
ESP32-S3 GPIO 4   →  SN65HVD230 D (TXD)
ESP32-S3 GPIO 5   →  SN65HVD230 R (RXD)
ESP32-S3 3.3V     →  SN65HVD230 Vcc
ESP32-S3 GND      →  SN65HVD230 GND  →  Roller schwarz (GND)
SN65HVD230 CANH   →  Roller gelb     (CAN-H)
SN65HVD230 CANL   →  Roller grün     (CAN-L)
```

**120Ω Termination:** Der Roller-Bus ist bereits terminiert (Display + VCU
haben 120Ω an den Bus-Enden). Auf dem SN65HVD230-Modul entweder Termination
weglassen oder den 120Ω-Widerstand auslöten / R-S-Pin auf GND legen.

**Listen-only-Mode:** Im Sketch ist TWAI auf `LISTEN_ONLY` konfiguriert. Der
ESP32 sendet **keine ACKs** auf den Bus. Damit kann er den Bus auch nicht
versehentlich stören. Wenn später aktive Frames injiziert werden sollen
(Phase 2+ ESP32-Bridge-Plan), Mode auf `NORMAL` ändern.

## Build & Flash

### Mit Arduino IDE
1. Board-Manager: ESP32-Paket installieren (Espressif Systems)
2. Board: „ESP32S3 Dev Module"
3. USB CDC On Boot: „Enabled" (für Serial-Debug über USB)
4. Sketch öffnen, kompilieren, flashen

### Mit arduino-cli
```bash
cd esp32/stealth-trigger

# Erstmal Board-Core (nur einmal nötig):
arduino-cli core install esp32:esp32

# Compile:
arduino-cli compile --fqbn esp32:esp32:esp32s3 .

# Upload (Port via `arduino-cli board list` finden):
arduino-cli upload -p /dev/cu.usbmodem* --fqbn esp32:esp32:esp32s3 .

# Serial Monitor:
arduino-cli monitor -p /dev/cu.usbmodem* -c baudrate=115200
```

## BLE-Protokoll

Custom NUS-Service (eigene UUIDs, kollidiert nicht mit der Roller-NUS):

| Aspekt | Wert |
|--------|------|
| Service UUID | `8e7a0000-7c5e-4dc0-aa1f-7c3c43b1e3b1` |
| TX Char (Notify, ESP32→Phone) | `8e7a0001-…` |
| RX Char (Write, Phone→ESP32) | `8e7a0002-…` (für künftige Config) |
| Device-Name | `ZT3-StealthTrigger` |

### Notify-Messages (TX)

```
TRIGGER:STEALTH_LOCK              ← Pattern A gefeuert
TRIGGER:CRUISE_REQUEST            ← Pattern B gefeuert
TRIGGER:PROFILE_SWITCH            ← Pattern C gefeuert
STATUS:CAN_OK:frames=12345        ← alle 5s Heartbeat während connected
STATUS:CAN_SILENT:frames=0        ← falls keine CAN-Aktivität
```

### Phone-App-Integration

Die existierende ZT3-App ([`app/`](../../app/)) kann zusätzlich zum Roller-NUS-Service
auch den ESP32-NUS-Service connecten. Ein `TRIGGER:STEALTH_LOCK`-Empfang
ruft direkt die existierende `lockNow()`-Funktion auf — unabhängig vom
Vol-Down-3×-Stealth-Trigger der App selbst.

Skizze für `BleEsp32TriggerClient.kt`:

```kotlin
class BleEsp32TriggerClient(private val context: Context) {
    private val SERVICE_UUID = UUID.fromString("8e7a0000-7c5e-4dc0-aa1f-7c3c43b1e3b1")
    private val TX_CHAR_UUID = UUID.fromString("8e7a0001-7c5e-4dc0-aa1f-7c3c43b1e3b1")

    fun connect(onTrigger: (String) -> Unit) {
        // Scan for "ZT3-StealthTrigger", connect, subscribe TX char
        // onCharacteristicChanged: parse "TRIGGER:..." and dispatch
    }
}
```

Implementation als Follow-Up wenn Hardware-Test der ESP32-Seite durch ist.

## Test-Checkliste (nach erstem Flash)

1. **CAN-Init OK?** — Serial-Output sollte `[twai] running @ 500 kbit/s, listen-only` zeigen
2. **Frames empfangen?** — Heartbeat alle 5s sollte `frames=…` mit steigendem Counter zeigen, bei stehendem Roller ~210 frames/s = ~1050 frames pro 5s-Heartbeat
3. **BLE sichtbar?** — Phone-Bluetooth-Settings sollten `ZT3-StealthTrigger` sehen
4. **Pattern A** — 3× kurz bremsen innerhalb 2 s → Serial: `[trigger] STEALTH_LOCK`
5. **Pattern B** — Throttle voll halten 5+ s, dann kurz bremsen → `[trigger] CRUISE_REQUEST`
6. **Pattern C** — Mode-Knopf 5× kurz drücken innerhalb 3 s → `[trigger] PROFILE_SWITCH`

## Known Limits

- **Pattern C zählt Mode-Wechsel, nicht Knopf-Drücke.** Der Mode-Knopf hat Dual-Funktion (kurz=Mode, lang=Licht). Long-Press triggert Mode-Wechsel + Light-Toggle, das ist ein einzelner Mode-Wechsel. Wenn du also 5× lang drückst, wird Pattern C trotzdem feuern. Aber kurze Mode-Taps sind die saubere Trigger-Methode.
- **Pattern B braucht aufgebockten Roller** zum Testen — Throttle 5s gehalten = der Roller fährt los. Privatgelände only.
- **Cooldown ist global**, nicht per-Pattern. Nach Pattern-A-Feuer kann B oder C 3 s lang nicht feuern. Das verhindert Ketten-Trigger ist aber bei Bedarf in `triggers.h` per-Pattern aufzubrechen.

## Active-Mode: Speed-Limit-Override und Cruise-Control

⚠ **Default ist OFF** wegen Risiko VCU-Konflikt. Aktivieren via `config.h`:

```cpp
constexpr bool ENABLE_ACTIVE_MODE = true;
```

Dann kann der ESP32:

### Speed-Limit live override (one-shot)

Sendet 0x342 + 0x20C-Frames für 1 Sekunde mit eigenem km/h-Wert. Stock-Display sendet 0x342 nur 5 Hz — wir feuern mit 20 Hz, der MCU enforced den letzten gesehenen Wert (= unseren).

```cpp
actuator.set_speed_limit(40, 1000);  // 40 km/h für 1s
```

**Nicht persistent** — nach Senden-Ende fällt der Cap nach <1s zurück auf den Stock-Wert. Für persistent: BLE-Write an die VCU (nur Phone-App kann das wegen Crypto-Handshake).

### Cruise-Control

Pattern B (5s Throttle-Hold + Brake-Tap) löst dann **CRUISE_ENGAGE** statt nur CRUISE_REQUEST aus. Eine FreeRTOS-Task sendet ab dann mit 100 Hz `0x100`-Frames mit lockedem Throttle-Wert.

**Disengage** = jeder Brake-Press (≥0x20). Universal-Standard, vor dem Pattern-Cooldown geprüft.

**Safety-Watchdog:** Cruise stoppt automatisch nach 60 Sekunden auch ohne Brake (verhindert Run-away wenn ESP32 hängt).

### Risiken (lies das bevor du flashst!)

- VCU/MCU könnten Watchdog-Fault auslösen wenn doppelte ID detektiert → Roller spontaner Reset während Fahrt
- Bei Cruise: wenn ESP32 abstürzt → Roller fährt unkontrolliert weiter bis 60s-Watchdog greift oder du bremst
- Beide Szenarien: **erst auf aufgebocktem Roller testen** (Hinterrad in der Luft)
- StVZO/ABE: erlischt
- Versicherung: greift nicht mehr bei Schaden mit aktivem Tuning

## Folge-Schritte (nach erfolgreichem Test)

- **Phone-App-Integration** in [`app/`](../../app/) — `BleEsp32TriggerClient` bauen, Trigger-Notifications in existierende Lock-/Profile-Logic feeden
- **BLE-RX-Handler** für Phone→ESP32 Commands (SET_LIMIT 40, CRUISE_OFF, etc.) — Stub im Sketch markiert
- **Buzzer-Mute-MOSFET** an einem GPIO ergänzen (siehe `ESP32-BRIDGE-PLAN.md` Use-Case A)
- **OTA-Update via WiFi-AP** für In-Field-Reflash ohne Dashboard-Öffnen
- **Frame-Stats-Logging** auf SD-Karte oder LittleFS für Trip-Recording
- **Active-Mode-Tests:** verifiziere dass VCU keinen Fault wirft bei doppelten 0x342/0x100 IDs (kontrollierte Werkbank-Tests vor live-Fahrt)
