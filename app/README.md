# Segway Mobility (Reborn) – Android App

[![Min SDK](https://img.shields.io/badge/minSdk-26-blue)](#)
[![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose)](#)
[![Architecture](https://img.shields.io/badge/architecture-MVVM%20%2B%20Hilt-success)](#)
[![Maps](https://img.shields.io/badge/maps-OpenStreetMap-7EBC6F?logo=openstreetmap)](#)
[![BLE](https://img.shields.io/badge/BLE-Nordic%20UART-blue)](#)
[![Crypto](https://img.shields.io/badge/Crypto-ECDH%20P--256%20%2B%20AES--CCM-yellow)](#)

Native, open-source rebuild of the official Segway-Ninebot **Segway Mobility** companion app, focused on the **ZT3 Pro D**. Built with Jetpack Compose + Material 3, using OpenStreetMap (no Google Play Services dependency), and the reverse-engineered Ninebot 2nd-gen ECDH pairing protocol.

> ⚠ For private use / private property only. Tuning a StVZO-registered scooter voids warranty, insurance and street-legality.

## Build & install

```bash
cd app

# 1) Bootstrap the Gradle wrapper (only needed once on a fresh checkout)
gradle wrapper --gradle-version 8.10.2

# 2) Open in Android Studio (Hedgehog or newer)
#    – or build from CLI:
./gradlew :app:installDebug
```

The app uses the `com.celox.segway` package id (`.debug` suffix on debug builds, so it can coexist with the official Ninebot app).

## Module layout

```
app/
├── core/
│   ├── ble/          BLE scan, GATT, frame codec, ECDH pairing
│   ├── crypto/       ECDH/AES-CCM/HKDF/HMAC primitives (BouncyCastle)
│   ├── vehicle/      Vehicle interface + ZT3 Pro implementation
│   └── data/         DataStore, Room DB, PairingPrefs
├── di/               Hilt modules
├── ui/
│   ├── theme/        Material 3 theme + dynamic color
│   ├── components/   Speedometer, StatTile, etc.
│   └── SegwayApp.kt  NavHost + bottom nav
└── feature/
    ├── home/         Vehicle dashboard
    ├── pair/         BLE scan + pairing UI
    ├── track/        OSM map + GPS recording
    ├── discover/     Community feed (stub)
    ├── mine/         Profile / settings entry
    ├── settings/     Theme, units, dynamic color
    └── about/        Disclaimer
```

## Implementation status

| Feature | Status |
|---|---|
| Material 3 theme + dynamic color | ✅ |
| Bottom-nav skeleton (4 tabs) | ✅ |
| BLE scanner with NB/NC beacon parsing | ✅ |
| GATT client (Nordic UART) | ✅ |
| ECDH pairing on secp256r1 | ✅ (handshake state machine, **not yet field-verified on a real ZT3 Pro**) |
| AES-CCM frame codec (`0x55 0xAB`) | ✅ |
| Inverted-sum checksum | ✅ |
| Pairing-state persistence (DataStore) | ✅ |
| Vehicle abstraction + ZT3 Pro impl. | ✅ (lock/unlock, mode, lights, cruise, speed-limit, region, serial) |
| Status polling (battery, speed, temp, trip) | ✅ (basic register layout; verify offsets!) |
| Foreground service for BLE keep-alive | ✅ |
| OSM map view (osmdroid) | ✅ |
| GPS track recording (LocationManager) | ✅ |
| Track persistence to Room | ⚠ schema in place, write-side TODO |
| Region-change command (D → U) | ⚠ encoded; wiring + confirmation dialog TODO |
| OTA firmware update | ⚠ commands modeled, flash flow TODO |
| Cloud account / login | ❌ offline-first |

## Open items / "first ride" checklist

1. **Verify the ECDH handshake against a real ZT3 Pro D.** The exact HKDF salt/info strings are not 100 % confirmed from the (incomplete-decompiled) SHU source. Capture an HCI snoop of a successful SHU pairing and compare.
2. **Confirm register layout (0xB0)** – the current decoder reads battery / speed / odometer / trip / temperature from a 32-byte block. Cross-check against Ninebot 2nd-gen register tables.
3. **Region-change command 0x18 0x10** – currently passes only the region byte; should serialize the full new SN.
4. **Wire `TrackRecordingService` → Room.** Service exposes a `StateFlow<List<Location>>`; needs a binder + persistence on Stop.
5. **Add a proper launcher icon set** (currently a placeholder vector "S"). Stock Segway has a glyph asset – swap in your own.

## How to extend for other scooters

The `Vehicle` interface is the extension point:

```kotlin
class G3Vehicle(
    override val id: String,
    override val displayName: String,
    private val gatt: GattClient,
    private val pairing: EllipticPairing,
) : Vehicle {
    /* override execute() with the G3-specific register map */
}
```

…and bind it instead of `Zt3ProVehicle` in `ActiveVehicleHolder`. The crypto / frame layers are model-agnostic.

## Credits

This app builds on top of work from:

- [ScooterHacking Utility (SHU)](https://utility.cfw.sh/) – source of the ECDH pairing protocol details
- [scooterteam/ZT3Tools](https://github.com/scooterteam/ZT3Tools/) – ST-Link memory layout reference
- [Nordic Semiconductor's Android-BLE-Library](https://github.com/NordicSemiconductor/Android-BLE-Library)
- [osmdroid](https://github.com/osmdroid/osmdroid)
