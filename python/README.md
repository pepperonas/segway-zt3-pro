# zt3-cli

Mac/Linux Python CLI for direct BLE access to a Segway-Ninebot **ZT3 Pro D** scooter.
Same NinebotCrypto wire protocol the Android app uses, exposed as a fast iteration
tool for register discovery.

## Why

- iteration-time on the Android app: ~10 s per build-install cycle
- iteration-time here: ~50 ms per command
- live `watch` mode prints register changes as you press buttons on the scooter — the killer feature for finding unmapped state-bits

## Install (macOS)

Requires Python ≥ 3.10. Recommended via [`uv`](https://github.com/astral-sh/uv) but
plain pip works too:

```bash
cd python
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
```

**macOS Bluetooth permission**: System Settings → Privacy & Security → Bluetooth → enable
your terminal app (Terminal.app, iTerm, …). Without this, `bleak` fails silently with
"no devices found".

## Quick start

```bash
# Discover scooters in range
zt3 scan

# First time: pair (you'll be asked to hold the power button on the scooter)
zt3 connect

# After that, every command resumes silently:
zt3 read 0x16 0x55                          # battery % (VCU 0x55)
zt3 read 0x07 0xA0 26                       # cell voltages (BMS 0xA0, 26 bytes)
zt3 write 0x16 0x49 60                      # auto-shutdown = 60 minutes
zt3 sweep 0x16 0x80..0xFF                   # one-shot range sweep
zt3 watch 0x16 0xC0..0xFF                   # live diff — print only changes
zt3 hunt 0x16 0x00..0xFF                    # triple-sweep + counter filter
```

## Output format

- `read` / `sweep`: `DD:OO  [HH HH ...]  (decoded)`
  e.g. `16:55  [4F 00]  (79 %  (battery))`
- `watch`: prints only when a value changes. Includes which bits flipped:
  ```
  [18:42:11] 16:E2  [00 00] → [02 00]  (+bit1)
  ```
- `hunt`: emits `DIFF` lines (after counter filtering):
  ```
  DIFF 16:E2  [00 00] → [02 00]  (+bit1)
  ```

## Persistent state

`~/.zt3-cli/state.json` holds the per-MAC `cryptoRandom` so resume works without
re-pairing. Delete the file (or that scooter's entry) to force a fresh pair.

## Architecture

```
crypto.py     — NinebotCrypto port (AES-CBC-MAC + AES-CTR, 5A A5 wire format)
frame.py      — inner-frame layout, wrap()/parse() helpers
ble.py        — bleak/CoreBluetooth wrapper for Nordic UART Service
handshake.py  — Stage 1/2/3 (getRandom → o1/resume → challenge-echo)
session.py    — context manager: connect, handshake, persist random
commands.py   — read/write/sweep/watch/hunt implementations
cli.py        — argparse entry point, exposes `zt3` console script
```

## Tests

```bash
pip install -e ".[dev]"
pytest
```

Crypto-port tests are deterministic (no scooter required). They verify
SHA-1-of-name+salt key derivation, the Stage-1 CRC `62 FF` from SHU's pcap,
and encrypt/decrypt round-trips at counter 0 and counter > 0.

End-to-end (handshake against the real scooter) is manual: `zt3 connect`,
expect "Stage 3: ✓".

## Status

Working end-to-end on macOS against a real ZT3 Pro D — handshake (Stage 1+2+3)
verified byte-perfect via the resume path. The CLI was used to:

- Sweep VCU 0x00..0xFF, MCU 0x00..0xFF, BMS 0x00..0xFF, and rapid-poll the named
  bitfield registers under controlled blinker / brake toggling. Outcome:
  **turn-signal and brake-pedal state are not exposed as readable registers
  on this firmware.** Every diff was either a noisy counter or natural battery
  drift — no clean state bit anywhere. This unblocked an Android feature decision
  (drop the blinker trigger, keep the custom-button double-tap).
- Verify that the **raw button-press is not exposed** either: with `custom_key=0`
  (Off) and the button held, no register changed. Only the action-effect of the
  button (Walk → 0x5A, KERS → 0x70, Park/Hill-Hold → 0x5A) is observable.

### Important workflow caveat

**Do not fresh-pair from the Mac CLI.** Stage 2 fresh-pair (`o1`) does NOT get ACK'd
by this firmware — the scooter accepts the random into its store but disconnects
without confirming, which scrambles the Android app's saved random. Always pair
via the Android app first, then `adb shell run-as ... cat pairing.preferences_pb`
to extract the new `cryptoRandom`, then `zt3 import-random "<base64>" --mac
<UUID>`. Resume path (Stage 3 only) works flawlessly.

Out of scope (for now): subscribed-push parameters, OTA flash, Linux quirks.
The Android app remains the canonical user interface.
