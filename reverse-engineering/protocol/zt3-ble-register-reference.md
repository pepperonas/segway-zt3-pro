# Segway Ninebot ZT3 — BLE Register & Protocol Reference

> Reverse-engineered BLE-Protokoll-Referenz für den Segway Ninebot ZT3
> (gehört zur "x3-Serie": F3, G3, GT3, ZT3)

**Stand:** April 2026
**Hauptquellen:**
- [segMod Wiki](https://github.com/MacintoshKeyboardHacking/segMod/wiki) (MacintoshKeyboardHacking)
- [x3regs.h](https://github.com/MacintoshKeyboardHacking/segMod/blob/main/myBLE4/x3regs.h)
- [NootNooot Ninebot BLE Documentation](https://nootnooot.codeberg.page/segway-ninebot-ble/)
- [etransport/ninebot-docs](https://github.com/etransport/ninebot-docs/wiki/protocol)
- **SHU `bootstrap.zip` / `zt3.json`** (extracted 2026-04-28 from a patched-debuggable SHU build) — full settings/toggle/bitfield map authoritatively in [`zt3-settings-registers.md`](zt3-settings-registers.md).

---

## ⚠️ Hinweis vorweg

Der ZT3 gehört zur Segway **"x3-Serie"** (F3, G3, GT3, ZT3). Das wesentliche Reverse Engineering wurde am GT3 Pro und F3 mit ESP32 + Raspberry Pi durchgeführt und sollte auch für G3 und ZT3 gelten. Die ZT3-Hardware ist laut segMod-Wiki noch nicht vollständig verifiziert — insbesondere fehlt beim ZT3-VCU offenbar der SPI-Flash-Chip, den GT3/G3/F3 haben. Auf Protokoll-/Register-Ebene sind die Befehle aber kompatibel.

---

## 1. Architektur

Die App spricht nicht direkt mit der VCU, sondern fragt 4 ECUs über UART/CAN ab, die im Scooter verbunden sind. BLE ist nur die Transport-Schicht.

```
Phone ──BLE (Nordic UART Service)──▶ ECU 0x04/0x23 (BLE/Display)
                                      │ UART 115200 8N1
                                      ▼
                                     ECU 0x16 (VCU)  ◀── ECU 0x02 (MCU)
                                                     ◀── ECU 0x07 (BMS)
```

### ECU-Adressen

| Adresse | ECU | Funktion |
|---------|-----|----------|
| `0x02` | MCU | Motor Controller (früher DRV/ESC) |
| `0x07` | BMS | Battery Management System |
| `0x16` | **VCU** | **Vehicle Control Unit** — Licht, Mode, Settings |
| `0x23` | TFT/DIS | Display |
| `0x04` | BLE | BLE-Modul |
| `0x3E` | App | Sender (Write) |
| `0x3F` | App | Sender (Read) |

---

## 2. Frame-Format (5A A5 Protokoll)

```
5A A5 │ bLen │ bSrc │ bDst │ bCmd │ bArg │ payload[bLen] │ wChecksumLE
```

- `bLen` — Länge der Payload
- `wChecksumLE` — Little-Endian, `0xFFFF XOR (16-bit-Summe der Bytes von bSrc bis Ende der Payload)`

### Command-Bytes

| Byte | Bedeutung |
|------|-----------|
| `0x01` | READ |
| `0x02` | WRITE |
| `0x03` | WRITE_NR (no response) |
| `0x04` | READ_RESP |
| `0x05` | WRITE_RESP |
| `0x06` | WR_BIT / CLEARERROR |
| `0x07` | IAP_BEGIN (Firmware-Download Start) |
| `0x08` | IAP_WR (Firmware-Daten schreiben) |
| `0x09` | IAP_CRC (Firmware-Checksumme) |
| `0x0A` | IAP_RESET |
| `0x0B` | IAP_ACK |
| `0x57` | ACTIVATE |
| `0x5B` | PRE_COMM (Handshake) |
| `0x5C` | SET_PWD (Handshake) |
| `0x5D` | AUTH (Handshake) |
| `0x76` | AUDIO_BEGIN |
| `0x77` | AUDIO_WR |
| `0x78` | AUDIO_CRC |

### Encryption2 Handshake

Auf aktuellen Firmwares läuft vor jedem Write ein Authentifizierungs-Handshake:

```
App                    Vehicle
 │                       │
 │── PRE_COMM (challenge)─▶│
 │◀── auth_param + serial──│
 │                       │
 │── SET_PWD (session pw)─▶│
 │◀── accepted ────────────│
```

Ohne gültige Session werden Writes verworfen. Die Krypto wird über `libnbcrypto.so` (Native Library der Segway-Ninebot Android App) gemacht.

---

## 3. VCU-Register (ECU 0x16) — Hauptkapitel

Adressierung: `Src=0x3E (App) → Dst=0x16 (VCU), CMD=0x02 (Write)`, gefolgt vom Register-Byte.

### 3.1 Power / Mode

| Reg | Name | Schreibwert | Wirkung |
|-----|------|-------------|---------|
| `0x79` | `VCU_EGear` | `01 00` | **Scooter einschalten** |
| `0x79` | `VCU_EGear` | `02 00` | **Scooter ausschalten** |
| `0x7A` | `VCU_DGear` | `00 01` | Deep Sleep (an BMS 0x07) |
| `0x5A` | `VCU_GearMode` / `VCU_DRIVE_MODE` | `01` / `02` / `03` / `04` | Walk / Eco / Sport / Race |
| `0x70` | `VCU_DecMode` (KERS) | `00` / `01` / `02` | Rekuperation aus / schwach / standard |

**Beispielframes:**

```
5a a5 02 3e 16 79 00 01 00 2f ff       # ON
5a a5 02 3e 16 79 00 02 00 2e ff       # OFF
5a a5 02 3e 07 7a 00 00 01 3d ff       # Deep Sleep
5a a5 00 07 3e fa 00 c0 fe             # Deep Sleep Bestätigung
```

### 3.2 Licht (Headlight, Taillight, Turn)

| Reg | Name | Werte | Wirkung |
|-----|------|-------|---------|
| `0x5B` | `VCU_LedMode` | (Mode) | Hauptscheinwerfer-Modus |
| `0x5D` | `VCU_TailLightMode` | `00 00` / `01 00` | Heller bei Bremsen / Blinkt bei Bremsen |
| `0x5C` | `VCU_ProjectionLightMode` | (Mode) | Projektionslicht (modellabhängig) |

**Headlight Status (gepusht via Subscription):**
`SUB_HEADLIGHT (0xa2bc1d75)` — 4-bit Wert:

| Wert | Bedeutung |
|------|-----------|
| `0` | off |
| `1` | low beam |
| `2` | high beam |
| `3` | auto low |
| `4` | auto high |
| `5` | auto mode (off) |

**Auto-Headlight + Brems-Modus** als Bits in `0x1F` und `0x5D`:
```
3e:16 1f bit ...1     auto headlight on
3e:16 1f bit ...0     auto headlight off
3e:16 1f bit ..1.     breathing taillight on
3e:16 5d = 00 00      brighter when braking
3e:16 5d = 01 00      flash when braking
```

### 3.3 Blinker (gehen an Display 0x23)

```
16:23 60 = 00 00     off
16:23 60 = 01 00     left turn
16:23 60 = 02 00     right turn
```

Frame an Display: `Src=0x16 → Dst=0x23, Reg=0x60 (DIS_VCU_TURN)`

### 3.4 Geschwindigkeits-Limits

| Reg | Name | Bedeutung |
|-----|------|-----------|
| `0x42` | `VCU_StartSpeed` | Anfahrgeschwindigkeit (km/h) |
| `0x43` | `VCU_GearEDMin` | Eco/Drive Minimum |
| `0x44` | `VCU_GearSRMin` | Sport/Race Minimum |
| `0x45` | `VCU_GearEDMax` | Eco/Drive Maximum |
| `0x46` | `VCU_GearSRMax` | Sport/Race Maximum |
| `0x47` | `VCU_GearED` | Eco-Modus aktueller Speed |
| `0x48` | `VCU_GearSR` | Sport+Race aktueller Speed (2 Bytes) |

**Beispiele:**
```
3e:16 47 = 19 00     eco = 25 km/h (0x19=25)
3e:16 48 = 50 32     sport=80, race=50
3e:16 42 = 05 00     start speed 5 km/h
3e:16 42 = 00 00     start speed 0
```

### 3.5 Function-Bits (Bitfelder)

Diese Register sind **Bitfelder** — die App liest erst, modifiziert ein Bit, schreibt zurück.

#### Register `0x1D` (`VCU_FunBool`) — 16-bit

```
Bit-Layout: AAAA BBBB CCCC DDDD

A-Block:  ?... = abnormality alert
          .?.. = direction indicator sound
          ..?. = (reserved)
          ...? = GT3 turbo (per SHU)

B-Block:  ?... = digital code lock active
          .?.. = (reserved)
          ..?. = park on slope
          ...? = walk mode enable

C-Block:  ?... = imperial / metric
          .?.. = locking function
          ..?. = (reserved)
          ...? = TCS (Traction Control)
```

#### Register `0x1E` (`VCU_FunBool2`) — 16-bit

```
.... ..1. .... ....   race mode enabled
.... ...1 .... ....   sport mode enabled
.... .... ..1. ....   S-ABS on
.... .... .... ...1   app function tone on
```

#### Register `0x1F` (`VCU_FunBool3` / `VCU_InfoBool2`) — 16-bit

```
.... ..1. .... ....   disable abn-alert after fold
.... ...1 .... ....   poweroff after folding
.... .... ..1. ....   continue charging after time
.... .... ...1 ....   scheduled charging
.... .... .... ..1.   breathing taillight
.... .... .... ...1   auto headlight
```

### 3.6 Sicherheit / Lock

| Reg | Name | Wirkung |
|-----|------|---------|
| `0x61` | `VCU_Pwd` | Digital Code Lock (4-stellig) |
| `0x71` | `VCU_KeyPwd` | Pattern Lock (z.B. `86 68 00 00` = LRRL) |
| `0x74` | `VCU_AlarmLevel` | Alarm-Sensitivity (`00`=low, `01`=std, `02`=high) |
| `0x49` | `VCU_AutoOffTime` | Auto-Power-Off Minuten |
| `0x4B` | `VCU_ChargeStartTime` | Geplantes Laden Startzeit |
| `0x4C` | `VCU_ChargeEndTime` | Geplantes Laden Endzeit |
| `0x75` | `VCU_BumpyRoad` | Anti-Bump Sensor |
| `0x76` | `VCU_VoiceVolume` | Sprachausgabe-Lautstärke |
| `0x77` | `VCU_playSound` | Sound abspielen |

### 3.7 Charge-Limit (geht an BMS 0x07, nicht VCU)

```
3e:07 82 = 50 00     charge limit 80%   (0x50 = 80 dezimal)
3e:07 82 = 64 00     charge limit 100%  (0x64 = 100)
```

`BMS_MaxPower = 0x82`

### 3.8 Lese-Register (Telemetrie)

| Reg | Name | Inhalt |
|-----|------|--------|
| `0x10` | `VCU_SN` | Seriennummer (kodiert auch die Region — siehe ⓘ unten) |
| `0x17` | `VCU_CtrlV` | Controller-Version |
| `0x18` | `VCU_MCUV` | MCU-Version |
| `0x19` | `VCU_BmsV` | BMS-Version |
| `0x1A` | `VCU_Bms2V` | BMS2-Version |
| `0x55` | `VCU_Battery` / `VCU_BATTPCT` | Battery % |
| `0x57` | `VCU_Speed` | Throttle (0x00 – 0x01b4) |
| `0x58` | `VCU_ErrorCode` | Fehlercode |
| `0x59` | `VCU_WarnCode` | Warncode |
| `0x5A` | `VCU_GearMode` | Aktueller Drive Mode |
| `0x5E` | `VCU_PreciseMileage` | Präzise Mileage |
| `0x5F` | `VCU_LeftMileage` | Restreichweite |
| `0x62` | `VCU_Mileage` | Gesamt-Mileage |
| `0x64` | `VCU_Runtime` | 32-bit Runtime |
| `0x66` | `VCU_RideTime` | 32-bit Ride Time |
| `0x68` | `VCU_SingleMileage` | Trip-Mileage |
| `0x69` | `VCU_RunningTime` | 32-bit Running Time |
| `0x6A` | `VCU_SingleRideTime` | Trip-Time |
| `0x6B` | `VCU_BodyTemp` / `VCU_TEMP` | Body-Temp (°C × 10) |
| `0x6E` | `VCU_SGear` | S-Gear |
| `0xC0` | `VCU_MCUCPUId` | MCU CPU-ID |
| `0xD2` | `VCU_LIGHT` | Ambient Light Level (vom BLE → VCU) |
| `0xDA` | `VCU_CPUId` | VCU CPU-ID |

---

## 4. Display-Register (ECU 0x23)

| Reg | Name | Bedeutung |
|-----|------|-----------|
| `0x25` | `DIS_SHOWPAGE` | Aktive Seite anzeigen |
| `0x3D` | `DIS_VCU_TIME` | Aktuelle Uhrzeit setzen |
| `0x60` | `DIS_VCU_TURN` | Blinker (s.o.) |
| `0x62` | `DIS_VCU_WHY0` | unbekannt, meist `00 00` |
| `0x64` | `DIS_VCU_WHY1` | Countdown |
| `0xD3` | `DIS_COMBPASS` | Combination Pass |
| `0xD5` | `DIS_VCU_MESG` | Display-Message |

### Display-Message Codes (`0x25`)

```
16:23 25 = 02 00     dashboard: booting
16:23 25 = 01 00     dashboard: booted, unlocked
16:23 25 = 00 00     dashboard: shutdown
16:23 25 = 05 00     warning display
16:23 25 = 11 04     pattern unlock
16:23 25 = 13 00     scheduled charging off
```

### Cruise / Throttle Status (`0xD5`)

```
16:23 d5 = 00 00     brake — ready to drive
16:23 d5 = 01 00     throttle when autopark
16:23 d5 = 04 00     (status)
16:23 d5 = 08 00     "not allowed"
16:23 d5 = 0c 00     cruise
```

---

## 5. BMS-Register (ECU 0x07)

| Reg | Name | Inhalt |
|-----|------|--------|
| `0x02` | `BMS_BatterySN` | Battery Serial Number |
| `0x0A` | `BMS_ManufactureDateLT` | Manufacture Date |
| `0x0E` | `BMS_Ver` | BMS Version |
| `0x10` | `BMS_SERIES_CELLS` | Anzahl Zellen in Serie |
| `0x11` | `BMS_RATED_VOLTAGE` | Nennspannung × 10 |
| `0x13` | `BMS_Capacity` | Kapazität |
| `0x59` | `BMS_CycleCountLT` | Lebenszeit Cycle Count |
| `0x82` | `BMS_MaxPower` | **Charge Limit** |
| `0x8C` | `BMS_VOLTAGE` | Spannung |
| `0x8D` | `BMS_CURRENT` | Strom |
| `0x8E` | `BMS_FULL_CAP_PCT` | Full Cap % |
| `0x8F` | `BMS_CHARGE_PCT` / `BMS_SOC` | Aktueller Ladezustand |
| `0x92` | `BMS_ChargeStatus` | Ladestatus |
| `0x94` | `BMS_TimeFull` | Restzeit bis voll (Minuten) |
| `0x96` | `BMS_Temps` | Temperaturen |
| `0xA0` | `BMS_CellVolts` | Einzelzellspannungen |
| `0xCE` | `BMS_CHARGE_PCT_ALT` | Instant Charge Value |
| `0xF9` | `BMS_TEMP` | BMS Temperatur |

---

## 6. MCU-Register (ECU 0x02)

| Reg | Name | Inhalt |
|-----|------|--------|
| `0x10` | `MCU_PN` | Part Number |
| `0x3E` | `MCU_TEMP` | MCU Temperatur |
| `0x40` | `MCU_TEMP_A_LASTMAX` | Letzte Maximaltemp A |
| `0x41` | `MCU_TEMP_B_LASTMAX` | Letzte Maximaltemp B |
| `0x48` | `MCU_TEMP_A` | Aktuelle Temp A (°C × 10) |
| `0x49` | `MCU_TEMP_B` | Aktuelle Temp B |
| `0x83` | `MCU_MODE` | MCU Mode |
| `0x86` | `MCU_SPEED` | Aktueller Speed |
| `0x8F` | `MCU_VOLTS` | Spannung |

---

## 7. Subscribed Parameters (Push-Telemetrie)

Die App abonniert beim Connect ein Set von Parametern, die der Scooter dann periodisch pusht. Jeder Parameter ist über einen 32-bit Hash adressiert.

### 16-bit Werte

| Hash | Bedeutung |
|------|-----------|
| `0x7e765289` | Acceleration intensity? |
| `0xddaa7a89` | `SUB_MILEAGE` — miles to zero in current mode |
| `0xac440ac5` | `SUB_TRIP` — trip distance |
| `0x6fe56c44` | `SUB_SPEED` — MPH |
| `0x9c9b65e4` | `SUB_BOOST` — Boost-Gauge (HH=0..200, LL=0..6) |

### 8-bit Werte

| Hash | Bedeutung |
|------|-----------|
| `0xa4f6d064` | `SUB_BATTERY` — battery percent bar |
| `0x616a4512` | `SUB_ECOBATTERY` — battery background gauge |
| `0xb3e2e070` | battery percent |

### 4-bit Werte

| Hash | Bedeutung |
|------|-----------|
| `0x8689b2da` | `SUB_CHARGING` — 1=charging, 2=flash charging |
| `0x7e0124a1` | `SUB_DRIVEMODE` — 1=walk, 2=eco, 3=sport, 4=race |
| `0xa2bc1d75` | `SUB_HEADLIGHT` — 0..5 (siehe oben) |

### Flags (1-bit Icons)

| Hash | Bedeutung |
|------|-----------|
| `0x42416f9f` | `SUB_2WD` — 2WD-Icon |
| `0x17b28d98` | `SUB_SABS` — S-ABS-Icon |
| `0xcfbc0a29` | `SUB_SDTC_DIS` — SDTC disabled Icon |
| `0x88e7bacc` | `SUB_ODO_TRIP` — trip vs odo |
| `0x0c99e9e5` | `SUB_IMPERIAL` — imperial set |
| `0x1841e961` | `SUB_TCS_DIS` — TCS disabled Icon |
| `0x16b5a88a` | `SUB_PARKED` — vehicle parked |
| `0x70efd7b3` | `SUB_RBS` — RBS active |
| `0x751a2603` | `SUB_IMPACTW` — "caution brake now" |
| `0x06bfb039` | `SUB_ANTIBUMP` — anti bumping on |
| `0x20e8263b` | `SUB_DOWNASST` — downhill assist on |
| `0x7f1a2555` | `SUB_UPPUSH` — uphill pushing on |
| `0xf739712b` | `SUB_HILLPARK` — hill park mode |
| `0x0086bd19` | `SUB_CRUISEC` — cruise control on |
| `0x0c7d931a` | `SUB_EXT_BAT` — external battery installed |

---

## 8. Konkrete Frame-Beispiele

### Beispiel 1: Scooter einschalten

```
5a a5 02 3e 16 79 00 01 00 2f ff
│  │  │  │  │  │  │  ─┬─── ──┬──
│  │  │  │  │  │  │   │       └── Checksum LE (0xff2f)
│  │  │  │  │  │  │   └────────── Payload: 01 00 = "ON"
│  │  │  │  │  │  └────────────── Arg: 0x00
│  │  │  │  │  └───────────────── Reg: 0x79 (VCU_EGear)
│  │  │  │  └──────────────────── Cmd: 0x16... wait, Dst!
│  │  │  └─────────────────────── Dst: 0x16 (VCU)
│  │  └────────────────────────── Src: 0x3E (App, write)
│  └───────────────────────────── bLen: 0x02
└──────────────────────────────── 5a a5 magic
```

### Beispiel 2: Drive Mode auf Sport setzen

```
5a a5 02 3e 16 5a 00 03 00 [chk]
                    │  │
                    │  └── Mode 3 = Sport
                    └───── Reg 0x5A (Drive Mode)
```

### Beispiel 3: Charge Limit auf 80%

```
5a a5 02 3e 07 82 00 50 00 [chk]
            │  │     │
            │  │     └── 0x50 = 80 dezimal
            │  └───────── Reg 0x82 (BMS_MaxPower)
            └──────────── Dst: 0x07 (BMS)
```

### Beispiel 4: Linker Blinker

```
5a a5 02 16 23 60 00 01 00 [chk]
         │  │  │     │
         │  │  │     └── Wert: 1 = links
         │  │  └──────── Reg 0x60 (DIS_VCU_TURN)
         │  └─────────── Dst: 0x23 (Display)
         └────────────── Src: 0x16 (VCU)
```

---

## 9. Checksumme berechnen (Python)

```python
def calc_checksum(payload: bytes) -> bytes:
    """
    Ninebot 5AA5 Checksum:
    16-bit Summe von bSrc bis Ende Payload, dann 0xFFFF XOR.
    Little-Endian zurückgeben.
    """
    s = sum(payload) & 0xFFFF
    chk = s ^ 0xFFFF
    return chk.to_bytes(2, 'little')


def build_frame(src: int, dst: int, cmd: int,
                arg: int, payload: bytes) -> bytes:
    """
    Baut einen kompletten 5AA5-Frame.
    bLen = len(payload).
    """
    body = bytes([len(payload), src, dst, cmd, arg]) + payload
    chk = calc_checksum(body)
    return b'\x5a\xa5' + body + chk


# Beispiel: Scooter einschalten
frame = build_frame(
    src=0x3E,          # App write
    dst=0x16,          # VCU
    cmd=0x02,          # WRITE
    arg=0x79,          # VCU_EGear
    payload=b'\x01\x00'  # ON
)
print(frame.hex())
# Erwartet: 5aa5023e1602790100[chk]
```

---

## 10. Praktischer Einstieg

### a) Sniffing der echten App (empfohlen zum Start)

Die NootNooot-Doku stellt einen funktionierenden Python-BLE-Client mit Encryption2-Handshake bereit:

```bash
git clone https://codeberg.org/NootNooot/segway-ninebot-ble.git
cd segway-ninebot-ble/ble-client
pip install bleak pycryptodome

# Scan für Scooter
python segway_ble_client.py scan

# Verbinden und Speed lesen
python segway_ble_client.py -a AA:BB:CC:DD:EE:FF read-speed
```

Damit sniffst du echte Frames, **bevor** du selbst sendest.

### b) MITM zwischen BLE-Modul und VCU (segMod-Ansatz)

ESP32-S3 hängt sich zwischen Stock-BLE und VCU. App-Funktionalität bleibt erhalten, du kannst parallel mitlesen und eingreifen.

**Hardware-Verkabelung am VCU-Stecker:**

| Farbe | Pin | Funktion |
|-------|-----|----------|
| schwarz | G | GND |
| blau | 12D | Permanent-Power (auch im Standby) |
| weiß | KEY | Power-Button (3.2V on, 76V off, 16V deep-sleep) |
| rot | 12C | Switched Power (off im Standby) |
| grün | CAN-L | CAN Low |
| gelb | CAN-H | CAN High |

### c) Encryption-Schicht beachten

Auf aktuellen Firmwares wirst du **ohne den Encryption2-Handshake** (PRE_COMM/SET_PWD/AUTH mit Challenge-Response gegen `libnbcrypto.so`) nichts geschrieben bekommen. Die meisten alten m365-orientierten Tools funktionieren auf dem ZT3 nicht mehr.

---

## 11. Nordic UART Service (BLE-Layer)

Der Scooter wirft das 5AA5-Protokoll in den **Nordic UART Service** (NUS):

- Service UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- TX Characteristic: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` (Write)
- RX Characteristic: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (Notify)

**Fragmentierung:** BLE-Pakete sind auf ~20 Bytes (oder MTU-1) limitiert. Längere 5AA5-Frames müssen über mehrere BLE-Writes fragmentiert werden, der Empfänger reassembliert.

**Modell-Identifikation** über BLE Advertising:
- 16-bit Service-ID `0xFE??` (Service Data)
- 128-bit Nordic UART Service ID
- Manufacturing Data → enthält Modell-Hint

---

## ⓘ Region-Codierung (kein dedizierter Register)

Die x3-Plattform (ZT3/F3/G3/GT3) hat **keinen separaten Region-Register**. Die Region ist in der Seriennummer (`0x10 VCU_SN`) enkodiert, und die App leitet sie client-seitig per Pattern-Matching ab.

Verifiziert via SHU-Decompile (`apps/shu/decompiled/jadx/sources/j6/p.java` + `g6/p1.java`):

```java
// j6/p.java::c() — pattern-matching
private static String c(String sn, h hVar) {
    JSONObject regionMap = device.getRegionMap();      // sn_region_map JSON
    Iterator<String> keys = regionMap.keys();
    while (keys.hasNext()) {
        String next = keys.next();
        JSONObject entry = regionMap.optJSONObject(next);
        if (entry != null && b(sn, entry.optString("pattern", ""))) {
            return entry.optString("label", next);     // z.B. "US"
        }
    }
    return "unknown";
}
```

`b(...)` macht Wildcard-Match mit `?` als Beliebig-Zeichen — z.B. `pattern = "1K1U?????????"` matcht alle SNs mit Prefix `1K1U`.

### ZT3 Pro D Region-Letter (4. Zeichen der SN, nach `1K1`-Modellprefix)

| Letter | Region | Speed-Limit |
|---|---|---|
| `U` | US | unbegrenzt / 40 km/h |
| `D` | DE / Germany | 20 km/h (Mofa-Klasse) |
| `E` | EU generic | 20 km/h |
| `G` | GB / UK | 25 km/h |
| `F` | FR / France | 25 km/h |
| `C` | CN / China | 25 km/h |
| `K` | KR / Korea | regional |
| `J` | JP / Japan | regional |

Beispiel: `1K1UA2551P3965` → Position 3 = `U` → **US-Region** (40 km/h Hardware-Limit weg, kompatibel mit SHU-„Change Region to US"-Workflow aus [`UNLOCK-PLAN.md`](../../UNLOCK-PLAN.md)).

### Region ändern (= neuen SN schreiben)

Region-Wechsel ist **kein eigener Befehl**, sondern ein `change_sn`-Write auf Reg `0x10`:

```
5A A5 0E 3E 16 02 10 [14 ASCII-bytes der neuen SN] [chk]
```

Die SHU-App nutzt diesen Mechanismus für „Change Region" (siehe `g6/p1.java::Q1()` + `V1()`).

---

## 12. Bekannte Lücken / TODOs

- **ZT3-spezifische Hardware**: VCU-Pinbelegung und Flash-Layout noch unbekannt
- **`0x5C VCU_ProjectionLightMode`**: Werte für Projektionslicht-Modi nicht vollständig dokumentiert
- **`0x49 VCU_AutoOffTime`**: Funktioniert mit `01-00` (1 min) bis `1e-00` (30 min), darüber unklar
- **Audio-Upload (`0x76/0x77/0x78`)**: Format und Verschlüsselung des Audio-Streams noch nicht reverse-engineered

---

## Footer

© 2026 Martin Pfeffer | celox.io
