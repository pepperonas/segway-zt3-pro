# Feature Parity vs XiaoDash

Tracks which XiaoDash-exposed settings/telemetry are implemented in our app, with register-source verification.

**Legend**:
- ✅ Implemented + verified on real ZT3
- 🔧 Register address known (from SHU bootstrap), wiring TODO
- ❓ Setting label seen in XiaoDash strings, no register address yet
- ⛔ Skipped (already in app via different mechanism)

## Battery Detail (XiaoDash "Battery" tab)

| XiaoDash field | Status | Source / register | Notes |
|----------------|--------|-------------------|-------|
| Battery % | ✅ | BMS 0x8F | `BMS_SOC` |
| Battery Voltage | ✅ | BMS 0x8C | uint16-LE × 0.01 V |
| Battery Current | ✅ | BMS 0x8D | int16-LE × 0.01 A |
| Battery Power (W) | 🔧 derived | V × I | UI display only |
| BMS Version | 🔧 | VCU 0x19 (uint16LE) | bootstrap-confirmed |
| Battery Capacity (mAh) | ❓ | unknown | not in SHU bootstrap |
| Degraded Capacity (mAh) | ❓ | unknown | derivable from designed × health% if designed known |
| Temperatures | ✅ | BMS 0x96 (4B), 0xF9 | pack temp sensors |
| Max Battery Percentage | 🔧 | BMS 0x82 (`charge_threshold`) R/W, uint16LE 80–100 | **slider 80–100%** |
| Estimated Charging Time | 🔧 derived | charge state + current % + amperage | UI display |
| Manufacture Date | ❓ | unknown | not in bootstrap |
| Full Charge Count | ❓ | unknown | not in bootstrap |
| Deep Discharge Count | ❓ | unknown | not in bootstrap |
| Extreme Use Time / Charge Time | ❓ | unknown | not in bootstrap |
| Total Energy Throughput (kWh) | ❓ | unknown | not in bootstrap |
| Total Capacity Throughput (mAh) | ❓ | unknown | not in bootstrap |
| Efficiency (km/kWh) | 🔧 derived | total_mileage / energy_throughput | needs throughput |
| Range with Full Battery | 🔧 derived | range_remaining / SOC × 100 | UI display |
| Min/Max Cell Voltage Diff | 🔧 derived | from existing `cellVoltagesMv[]` | UI display |
| Cycle Count | ✅ | BMS 0x59 | already polled |

## Scooter Info (XiaoDash "Basic" tab — top section)

| XiaoDash field | Status | Source / register | Notes |
|----------------|--------|-------------------|-------|
| Current Speed | ✅ | MCU 0x86 | actual from MCU |
| Start Speed (= Anlauf) | 🔧 | VCU 0x42 (`start_speed`) R/W uint16-LE | slider 0–5 km/h |
| Mileage / Trip | ✅ | VCU 0x68 | trip mileage |
| RideTime (Trip) | ✅ | VCU 0x6A | trip seconds |
| Uptime / Total RideTime / Total Uptime | ✅/❓ | VCU 0x64 (Total Runtime) — Uptime≠RideTime distinction unclear |
| Total Mileage | ✅ | VCU 0x62 | total km |
| Serial Number | ✅ | VCU 0x10 | string read |
| Scooter ID | ❓ | unknown | "0-0-0" string in XiaoDash; possibly derived |
| Body Temperature | ✅ | VCU 0x6B | °C × 10 |
| Left Mileage (Range) | ✅ | VCU 0x5F | range_remaining km |
| VCU Version | ✅ | VCU 0x17 | already shown |
| MCU Version | ✅ | VCU 0x18 (or 0x1A bundle offset 2-3) | already shown |
| BMS Version | 🔧 | VCU 0x19 | new |
| BLE Version | ✅ | VCU 0x1A bundle offset 4-5 | already shown |
| Battery Level | ✅ | BMS 0x8F | already shown |
| Drive Mode | ✅ | VCU 0x5A | already shown |
| Error Code | ✅ | VCU 0x58 | already shown |

## Settings (XiaoDash "Basic" tab — toggle/slider section)

### Toggles (VCU bitfields, found in SHU bootstrap)

| XiaoDash setting | Status | Register | Bit |
|------------------|--------|----------|-----|
| Imperial/Miles Mode | 🔧 | VCU 0x1D `vcu_bool` | bit 3 |
| Traction Control | 🔧 | VCU 0x1D | bit 0 |
| Park On Slope (Hill-Hold) | 🔧 | VCU 0x1D | bit 5 |
| Boost Mode | 🔧 | VCU 0x1D | bit 10 |
| Indicator Sound (Turn signal) | 🔧 | VCU 0x1D | bit 11 |
| App Function Tone | 🔧 | VCU 0x1E | bit 0 |
| Hold Descent Control | ⛔ same as Park On Slope | VCU 0x1D bit 5 (`ramp_parking`) | confirmed by user 2026-04-28 — XiaoDash labels the same toggle two ways |
| Motor Brake | ⛔ same as Energy Recovery | VCU 0x70 (`kers_level`) | confirmed by user 2026-04-28 — KERS regen *is* the motor-brake strength |
| Sport Mode (toggle, enable mode) | 🔧 | VCU 0x1E | bit 8 (`enable_sports`) |
| Drive Mode (toggle, enable mode) | 🔧 | VCU 0x1E | bit 7 (`enable_drive`) |
| Walk Mode (toggle, enable mode) | 🔧 | VCU 0x1D | bit 4 (`enable_walk`) |
| Alarm Mode | 🔧 | VCU 0x1D | bit 15 (`alarm` — on/off) |
| Energy Recovery (multi-state) | 🔧 | VCU 0x70 (`kers_level`) | uint16-LE enum |
| Auto Headlight | 🔧 | VCU 0x1F | bit 0 |
| Front Position Lamp | 🔧 | VCU 0x1F | bit 11 |
| UnderGlow Lights | 🔧 | VCU 0x1F | bit 2 |
| Breathing Charging Taillight | 🔧 | VCU 0x1F | bit 1 |
| Taillight Mode (multi-state) | 🔧 | VCU 0x5D (`tail_light_mode`) | uint16-LE enum |
| Poweroff After Folding | 🔧 | VCU 0x1F | bit 8 |
| Disable Alarm Mode After Folding | 🔧 | VCU 0x1F | bit 9 |

### Sliders / numeric (VCU uint16, found in SHU bootstrap)

| XiaoDash setting | Status | Register | Range |
|------------------|--------|----------|-------|
| Auto Shutdown After X Min. | 🔧 | VCU 0x49 (`auto_off_time`) | 0–60 min |
| Volume | ❓ | unknown | not in SHU bootstrap |
| Set Start Speed | 🔧 | VCU 0x42 (`start_speed`) | 0–5 km/h |
| ECO Speed Limit | ⛔ already | VCU 0x47 | combined with DRIVE |
| DRIVE Speed Limit | ⛔ already | VCU 0x47 | combined with ECO |
| SPORT Speed Limit | ⛔ already | VCU 0x48 | |
| RACE Speed Limit | ❓ | unknown | not in bootstrap (race may be SHFW-only) |
| Custom Button Action | 🔧 | VCU 0x4A (`custom_key`) | enum |
| Dashboard Settings | ❓ | unknown | not in bootstrap |

## Implementation Plan

**Commit 2 — Read-only telemetry** (no risk):
- Add VehicleState fields: `bmsVersion`, `chargeThreshold` (R), `motorPower`, `cellVoltageDiffMv`, `rangeAtFullKm` (derived)
- Extend pollPlan with VCU 0x19, BMS 0x82
- New BatteryDetailScreen accessible from Home (tap battery card)

**Commit 3 — Writable settings** (with care):
- Add VehicleCommand surface: `SetVcuBitfieldBit`, `SetVcuRegister`, `SetBmsRegister`
- Implement bitfield read-modify-write atomically (mutex around the read→flip→write cycle for each bitfield register)
- New SettingsScreen as 5th BottomNav tab with sections: General, Lights, Folding, Battery, Modes
- Each toggle / slider reads current value on screen-open, writes throttled on user change

## Open items (deferred — needs Frida/HCI snoop)

- All BMS deep-detail telemetry (capacity, manufacture date, throughput, charge counts)
- Volume register
- Dashboard Settings register
- Motor Brake setting (might just be kers_level)
- "Hold Descent Control" — disambiguate vs ramp_parking
- Scooter ID format ("0-0-0")
