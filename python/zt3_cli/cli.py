"""argparse-based CLI for the zt3 tool.

Subcommands:
  zt3 connect [--mac MAC]                        — first-time pair
  zt3 read DST OFFSET [LEN] [--mac MAC]          — read register
  zt3 write DST OFFSET VALUE [--mac MAC]         — write u16 register
  zt3 sweep DST LO..HI [--mac MAC]               — one-pass sweep
  zt3 watch DST LO..HI [--ms MS] [--mac MAC]     — live diff
  zt3 hunt DST [--mac MAC]                       — triple-sweep + counter filter
  zt3 scan                                       — list nearby ZT3-class scooters

DST and OFFSET accept hex (`0x16`, `0xC4`) or decimal. Range syntax is
`LO..HI` (inclusive) — `0x80..0xFF` is the full upper VCU range.
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from typing import Optional

from . import commands
from .ble import NINEBOT_MFG_ID, scan as ble_scan, scan_raw
from .session import open_session


def _int(s: str) -> int:
    """Parse `0x..` or decimal integers."""
    s = s.strip()
    if s.lower().startswith("0x"):
        return int(s, 16)
    return int(s)


def _range(s: str) -> tuple[int, int]:
    """Parse `LO..HI` (inclusive)."""
    if ".." not in s:
        raise argparse.ArgumentTypeError(f"range must be 'LO..HI', got {s!r}")
    lo_s, hi_s = s.split("..", 1)
    return _int(lo_s), _int(hi_s)


# ── command dispatch ─────────────────────────────────────────────


async def _cmd_connect(args: argparse.Namespace) -> int:
    async with open_session(mac=args.mac) as session:
        print(f"connected: {session.mac} ({session.name})", file=sys.stderr)
        # Just exit cleanly — handshake is done, random is persisted.
        return 0


async def _cmd_read(args: argparse.Namespace) -> int:
    async with open_session(mac=args.mac) as session:
        return await commands.cmd_read(session, args.dst, args.offset, args.length)


async def _cmd_write(args: argparse.Namespace) -> int:
    async with open_session(mac=args.mac) as session:
        return await commands.cmd_write(session, args.dst, args.offset, args.value)


async def _cmd_sweep(args: argparse.Namespace) -> int:
    lo, hi = args.range
    async with open_session(mac=args.mac) as session:
        return await commands.cmd_sweep(session, args.dst, lo, hi)


async def _cmd_watch(args: argparse.Namespace) -> int:
    lo, hi = args.range
    async with open_session(mac=args.mac) as session:
        return await commands.cmd_watch(session, args.dst, lo, hi, args.ms)


async def _cmd_hunt(args: argparse.Namespace) -> int:
    lo, hi = args.range or (0x00, 0xFF)
    async with open_session(mac=args.mac) as session:
        return await commands.cmd_hunt(session, args.dst, lo, hi)


async def _cmd_import_random(args: argparse.Namespace) -> int:
    """Inject a known-good cryptoRandom (from the Android app) into
    state.json. Bypasses fresh-pair (Stage 2) entirely on next connect.
    """
    import base64
    import binascii
    from .session import _load_state, _save_state, _save_random

    raw = args.random.replace(" ", "").replace(":", "")
    # Accept hex (32 chars) or base64 (24 chars).
    try:
        if len(raw) == 32 and all(c in "0123456789abcdefABCDEF" for c in raw):
            random_bytes = bytes.fromhex(raw)
        else:
            random_bytes = base64.b64decode(raw)
    except (ValueError, binascii.Error) as e:
        print(f"could not decode random: {e}", file=sys.stderr)
        return 1
    if len(random_bytes) != 16:
        print(f"random must be 16 bytes, got {len(random_bytes)}", file=sys.stderr)
        return 1

    mac = args.mac.upper() if args.mac else None
    state = _load_state()
    if mac is None:
        # Use default if set, else error.
        mac = state.get("default")
        if mac is None:
            print("no --mac given and no default in state.json — connect once first", file=sys.stderr)
            return 1
    _save_random(state, mac, args.name, random_bytes)
    _save_state(state)
    print(f"saved cryptoRandom for {mac}: {random_bytes.hex(' ')}")
    print("next `zt3 connect` will resume (skip Stage 2).")
    return 0


async def _cmd_scan(args: argparse.Namespace) -> int:
    if args.all:
        # Diagnostic mode — show every nearby BLE device with its
        # manufacturer-id list. Useful when the filtered scan returns
        # nothing and we need to figure out what's actually advertising.
        print("Scanning ALL nearby BLE devices for 8 s…", file=sys.stderr)
        found = await scan_raw(timeout=8.0)
        if not found:
            print(
                "no BLE devices in range at all — Mac Bluetooth permission?\n"
                "  System Settings → Privacy & Security → Bluetooth → Terminal/iTerm = ON\n"
                "  (you may need to restart the terminal app after granting)",
                file=sys.stderr,
            )
            return 1
        for d in found:
            mfg = ", ".join(f"0x{m:04X}" for m in d.manufacturer_ids) or "—"
            highlight = "  ← ZT3 candidate" if NINEBOT_MFG_ID in d.manufacturer_ids else ""
            print(
                f"{d.address}  RSSI={d.rssi:>4} dBm  "
                f"name={d.name or '?':<24}  mfg=[{mfg}]{highlight}"
            )
        return 0

    found = await ble_scan(timeout=5.0)
    if not found:
        print(
            "no ZT3-class scooters in range (filter: manufacturer 0x434E).\n"
            "  • is the scooter switched on?\n"
            "  • is the Android app currently connected to it? disconnect first.\n"
            "  • try `zt3 scan --all` to see every nearby BLE device.",
            file=sys.stderr,
        )
        return 1
    for s in found:
        print(f"{s.address}  {s.name or '(no name)'}  RSSI={s.rssi} dBm")
    return 0


# ── parser ───────────────────────────────────────────────────────


def _build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="zt3", description="ZT3 Pro D BLE CLI")
    p.add_argument("-v", "--verbose", action="store_true", help="enable debug logging")
    sub = p.add_subparsers(dest="cmd", required=True)

    def _add_mac(sp: argparse.ArgumentParser) -> None:
        sp.add_argument("--mac", help="explicit MAC address (overrides state.json default)")

    p_conn = sub.add_parser("connect", help="pair (first time) or resume")
    _add_mac(p_conn)
    p_conn.set_defaults(func=_cmd_connect)

    p_imp = sub.add_parser(
        "import-random",
        help="paste a known-good cryptoRandom (16 bytes hex/base64) — skips Stage 2 next connect",
    )
    p_imp.add_argument("random", help="16-byte random as hex (32 chars) or base64")
    p_imp.add_argument("--mac", help="scooter MAC (default: state.json default)")
    p_imp.add_argument("--name", help="display name (e.g. ZT3-XXXX serial)")
    p_imp.set_defaults(func=_cmd_import_random)

    p_scan = sub.add_parser("scan", help="discover ZT3-class scooters")
    p_scan.add_argument(
        "--all",
        action="store_true",
        help="show every nearby BLE device (diagnostic — bypasses the 0x434E filter)",
    )
    p_scan.set_defaults(func=_cmd_scan)

    p_read = sub.add_parser("read", help="read a register")
    p_read.add_argument("dst", type=_int)
    p_read.add_argument("offset", type=_int)
    p_read.add_argument("length", type=_int, nargs="?", default=2)
    _add_mac(p_read)
    p_read.set_defaults(func=_cmd_read)

    p_write = sub.add_parser("write", help="write a uint16 register")
    p_write.add_argument("dst", type=_int)
    p_write.add_argument("offset", type=_int)
    p_write.add_argument("value", type=_int)
    _add_mac(p_write)
    p_write.set_defaults(func=_cmd_write)

    p_sweep = sub.add_parser("sweep", help="one-pass register sweep")
    p_sweep.add_argument("dst", type=_int)
    p_sweep.add_argument("range", type=_range, help="LO..HI inclusive (e.g. 0x80..0xFF)")
    _add_mac(p_sweep)
    p_sweep.set_defaults(func=_cmd_sweep)

    p_watch = sub.add_parser("watch", help="live register-change watcher")
    p_watch.add_argument("dst", type=_int)
    p_watch.add_argument("range", type=_range, help="LO..HI inclusive")
    p_watch.add_argument("--ms", type=int, default=100, help="poll interval in ms (default 100)")
    _add_mac(p_watch)
    p_watch.set_defaults(func=_cmd_watch)

    p_hunt = sub.add_parser("hunt", help="triple-sweep + counter filter")
    p_hunt.add_argument("dst", type=_int)
    p_hunt.add_argument(
        "range",
        type=_range,
        nargs="?",
        default=None,
        help="LO..HI inclusive (default 0x00..0xFF)",
    )
    _add_mac(p_hunt)
    p_hunt.set_defaults(func=_cmd_hunt)

    return p


def main(argv: Optional[list[str]] = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)

    logging.basicConfig(
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        level=logging.DEBUG if args.verbose else logging.INFO,
    )
    try:
        return asyncio.run(args.func(args))
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
