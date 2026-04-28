# Reverse-Engineering

Wire-Format-Doku + Crypto-Analyse rund um den Segway-Ninebot **ZT3 Pro D**. Stand: 2026-04-28.

## Inhalt

```
reverse-engineering/
├── protocol/
│   ├── zt3-ble-register-reference.md     # alle bekannten VCU/MCU/BMS-Register
│   └── zt3-settings-registers.md         # XiaoDash-equivalent settings (aus SHU bootstrap.zip)
├── apps/shu/
│   ├── ScooterHackingUtility-pre_release.open_beta-5.apk  # via Git LFS
│   └── ANALYSIS.md                       # Crypto + Pairing-Flow im Detail
└── ble-captures/
    └── README.md                         # HCI-Snoop-Methodik (tshark)
```

Die offizielle Ninebot-App (`com.ninebot.segway`) ist via NetEase NIS gepackt — Code statisch nicht extrahierbar. Wir verlassen uns vollständig auf SHU als Open-Source-Referenz für das BLE-Protokoll.

## Quick-Reference — BLE-Stack

| Aspekt | Wert |
|---|---|
| GATT-Service | Nordic UART `6e400001-b5a3-f393-e0a9-e50e24dcca9e` |
| RX (App→Roller) | `…002` write-no-resp |
| TX (Roller→App) | `…003` notify |
| Adv-Manufacturer | `FF 4E 43` ("NC", NinebotCrypto) |
| Frame-Magic | `5A A5` (klassisch, **bestätigt für ZT3 Pro D**) |
| Crypto | AES-128 CBC-MAC + AES-CTR, 16-byte SHA-1-derived session key |
| Handshake | 3-stage (cmd `0x5B`/`0x5C`/`0x5D`), Resume via `setRandomAppData` |

Code-Implementierung: [`app/.../core/crypto/NinebotCrypto.kt`](../app/app/src/main/kotlin/com/celox/segway/core/crypto/NinebotCrypto.kt) (Kotlin) und [`python/zt3_cli/crypto.py`](../python/zt3_cli/crypto.py) (Python). Beide sind byte-genau gegen SHU-Wire-Output verifiziert.

## Reproduktion (SHU-Decompile)

```bash
brew install apktool jadx
cd apps/shu
apktool d -f -o decompiled/apktool ScooterHackingUtility-pre_release.open_beta-5.apk
jadx -d decompiled/jadx --no-res ScooterHackingUtility-pre_release.open_beta-5.apk
```

Outputs (`decompiled/`) sind gitignored — ~50 MB.
