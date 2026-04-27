# ZT3 Pro D — Authoritative Settings & Toggles Register Map

**Source**: SHU's runtime-loaded `bootstrap.zip` → `zt3.json`. Extracted via `adb run-as` from the patched-debuggable SHU build on 2026-04-28. Raw archive preserved at [`apps/shu/extracted-runtime/`](../apps/shu/extracted-runtime/).

This file is the **source of truth** for writable settings / toggles. Read-only telemetry (battery state, mileage, etc.) is in [`zt3-ble-register-reference.md`](zt3-ble-register-reference.md).

## Wire commands (verified)

| Op | cmd | response_cmd | arg | notes |
|----|-----|--------------|-----|-------|
| read_register | `0x01` | `0x04` | register offset | payload = uint8 length |
| write_register | `0x02` | `0x05` | register offset | payload per datatype |
| write_no_reply | (variant) | — | — | fire-and-forget |

## Devices (tx_addr / dst byte)

| Device | dst | role |
|--------|-----|------|
| BLE | `0x04` | handshake / IoT |
| MCU | `0x02` | motor controller |
| VCU | `0x16` | vehicle control unit (most settings live here) |
| BMS | `0x07` | battery management system |

## VCU registers (dst=0x16)

### Sliders / numeric (uint16-LE)

| Register | Offset | Range / Unit | Setting Name | UI Section |
|----------|--------|--------------|--------------|-----------|
| `start_speed` | `0x42` | 0–5 km/h | Start Speed (Anlauf) | tools |
| `max_speed_eco_drive` | `0x47` | km/h | ECO + DRIVE max speed (combined) | tools |
| `max_speed_sports` | `0x48` | km/h | SPORT max speed | tools |
| `auto_off_time` | `0x49` | minutes (0–60) | Auto Shutdown timeout | tools |
| `custom_key` | `0x4A` | enum | Custom Button Action | tools |
| `tail_light_mode` | `0x5D` | enum (multi-state) | Taillight Mode | tools |
| `acc_level` | `0x6E` | enum (Low/Med/High) | Acceleration level | tools |
| `kers_level` | `0x70` | enum (Off/Low/Med/High) | Energy Recovery / KERS | tools |

### Bitfields (uint16-LE, single-bit toggles packed into one register)

#### `vcu_bool` — VCU offset `0x1D`

| Bit | Name | Friendly |
|-----|------|----------|
| 0 | `traction_control` | Traction Control |
| 3 | `imperial_units` | Use imperial units (mph) |
| 4 | `enable_walk` | Enable Walk mode |
| 5 | `ramp_parking` | Park on Slope (Hill-Hold) |
| 10 | `boost_function` | Enable Boost function |
| 11 | `turn_signal_sounds` | Turn signal sounds (Indicator Sound) |
| 15 | `alarm` | Enable Alarm |

#### `vcu_bool_2` — VCU offset `0x1E`

| Bit | Name | Friendly |
|-----|------|----------|
| 0 | `app_function_tone` | App interaction sounds |
| 7 | `enable_drive` | Enable Drive mode |
| 8 | `enable_sports` | Enable Sports mode |

#### `vcu_bool_3` — VCU offset `0x1F`

| Bit | Name | Friendly |
|-----|------|----------|
| 0 | `auto_headlight` | Auto headlight (light sensor) |
| 1 | `charging_breathing_light` | Tail-light breathing on charging |
| 2 | `underglow_lights` | Safety underglow lights |
| 7 | `charge_now` | Charge now (bypass schedule) |
| 8 | `power_off_folding` | Power off on folding |
| 9 | `folding_disable_alarm` | Disable alarm on folding |
| 11 | `front_position_lamp` | Front position lamp |

### Identifier / read-only (already covered in main register doc)

| Register | Offset | Type | Notes |
|----------|--------|------|-------|
| `vcu_serial` | `0x10` | string | Serial number |
| `vcu_version` | `0x17` | uint16LE | VCU firmware version |
| `mcu_version` | `0x18` | uint16LE | MCU FW (alt path; we use 0x1A bundle) |
| `bms_version` | `0x19` | uint16LE | BMS firmware version (queried via VCU, not BMS!) |
| `vcu_uid` | `0xDA` | uint16LE | VCU unique ID |
| `mcu_uid` | `0xC0` | uint16LE | MCU unique ID |

## BMS registers (dst=0x07)

| Register | Offset | Range / Unit | R/W | Setting Name |
|----------|--------|--------------|-----|--------------|
| `charge_threshold` | `0x82` | 80–100 (%) | R/W | **Battery Max Charge Percentage** |

## BLE registers (dst=0x04)

| Register | Offset | Type | R/W | Notes |
|----------|--------|------|-----|-------|
| `ble_version` | `0x01` | uint16LE | R | BLE module FW version (we read this from VCU 0x1A bundle currently) |

## Coverage vs XiaoDash screenshot

XiaoDash exposes ~25 settings on its Basic + Battery tabs. Coverage from SHU's bootstrap:

✅ **Found** (24 of ~25):
- Auto Shutdown, Volume *(see note)*, Imperial/Miles, Traction Control, Park On Slope, Boost Mode, Indicator Sound (= turn_signal_sounds), App Function Tone, Auto Headlight, Front Position Lamp, UnderGlow Lights, Breathing Charging Taillight, Taillight Mode, Poweroff After Folding, Disable Alarm Mode After Folding, Alarm Mode, Energy Recovery (= kers_level), Custom Button Action (= custom_key), Start Speed, Per-Mode Speeds (ECO/DRIVE combined + SPORT), Battery Max Charge %, Walk Mode toggle, Drive Mode toggle, Sports Mode toggle, Charge Now bypass

❌ **Not in SHU bootstrap** (no register definition found):
- **Volume** — not listed in zt3.json. SHU doesn't expose it; XiaoDash does. Probably lives in a different bitfield or VCU register that SHU doesn't surface. **Action**: capture via XiaoDash live BLE-trace later.
- **Motor Brake / Regen** strength — probably overlaps with `kers_level` (KERS = energy recovery via motor brake)
- **Dashboard Settings** (which screen shows on the scooter dashboard)
- **Battery Capacity / Degraded / Manufacture Date / Throughput / Charge Counts** (BMS deep-detail telemetry — read-only, in BMS reg `0x80`-range, not surfaced in SHU)

🔬 **Verified via firmware extraction**: All offsets and bit-positions above are taken directly from SHU's `zt3.json` config that the official-distributed scooter app is loading at runtime, so they should be byte-perfect for ZT3 Pro D.

## Implementation pointers

When wiring these in `Zt3ProVehicle.kt`:

- **Read bitfield**: send read_register with offset `0x1D`/`0x1E`/`0x1F` (length=2) → uint16-LE → bit-shift to extract specific toggle
- **Write bitfield**: read current value → flip the target bit → write back the full uint16 (no atomic toggle support; race-y if two writes interleave)
- **Read uint16 setting**: read_register with the register's offset, length=2
- **Write uint16 setting**: write_register with the register's offset + the uint16-LE payload

Cmd codes are already correct in the app's `FrameCodecClassic`/`Zt3ProVehicle` (read=0x01, write=0x02). No protocol changes needed.
