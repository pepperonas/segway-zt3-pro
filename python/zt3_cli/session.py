"""High-level session: BLE + crypto + handshake + state persistence.

Persists a `state.json` under `~/.zt3-cli/`:

    {
      "scooters": {
        "C1:6B:5E:D0:C5:96": {
          "name": "ZT3-XXXX",
          "cryptoRandom": "<base64>",
          "lastSeen": "2026-04-28T18:42:00Z"
        }
      },
      "default": "C1:6B:5E:D0:C5:96"
    }
"""

from __future__ import annotations

import asyncio
import base64
import json
import logging
import os
from contextlib import asynccontextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import AsyncIterator, Optional

from . import frame
from .ble import BleSession, scan
from .crypto import NinebotCrypto
from .handshake import perform_handshake


log = logging.getLogger(__name__)


STATE_DIR = Path.home() / ".zt3-cli"
STATE_FILE = STATE_DIR / "state.json"


def _load_state() -> dict:
    if not STATE_FILE.exists():
        return {"scooters": {}, "default": None}
    try:
        return json.loads(STATE_FILE.read_text())
    except Exception:
        log.warning("state.json corrupt — starting fresh")
        return {"scooters": {}, "default": None}


def _save_state(state: dict) -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, indent=2, sort_keys=True))


def _persisted_random(state: dict, mac: str) -> Optional[bytes]:
    entry = state.get("scooters", {}).get(mac.upper())
    if entry is None:
        return None
    raw = entry.get("cryptoRandom")
    if raw is None:
        return None
    try:
        b = base64.b64decode(raw)
        if len(b) != 16 or not any(b):
            return None
        return b
    except Exception:
        return None


def _save_random(state: dict, mac: str, name: Optional[str], random: bytes) -> None:
    state.setdefault("scooters", {})
    entry = state["scooters"].setdefault(mac.upper(), {})
    entry["cryptoRandom"] = base64.b64encode(random).decode("ascii")
    if name and not entry.get("name"):
        entry["name"] = name
    entry["lastSeen"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    if state.get("default") is None:
        state["default"] = mac.upper()


@dataclass
class Session:
    """Active connection — direct send/recv API for command code."""

    ble: BleSession
    crypto: NinebotCrypto
    mac: str
    name: Optional[str]

    async def read_register(self, dst: int, register: int, length: int = 2) -> Optional[frame.Decoded]:
        """Send a read and wait for the matching response, up to ~600 ms."""
        wire = frame.read_register(self.crypto, dst, register, length)
        await self.ble.send(wire)
        # Drain replies briefly; pick the first that matches.
        deadline = asyncio.get_event_loop().time() + 0.6
        while asyncio.get_event_loop().time() < deadline:
            f = await self.ble.recv(timeout=0.05)
            if f is None:
                continue
            decoded = frame.parse(self.crypto, f)
            if decoded is None:
                continue
            if decoded.cmd == frame.CMD_RESP_READ and decoded.arg == (register & 0xFF) and decoded.src == (dst & 0xFF):
                return decoded
        return None

    async def write_register(self, dst: int, register: int, payload: bytes) -> None:
        wire = frame.write_register(self.crypto, dst, register, payload)
        await self.ble.send(wire)
        # Don't wait for the ack — fire-and-forget like the Android app.

    async def send_raw(self, wire: bytes) -> None:
        await self.ble.send(wire)


async def _resolve_mac(requested_mac: Optional[str], state: dict) -> tuple[str, Optional[str]]:
    """Pick a target MAC: explicit arg → state default → scan."""
    if requested_mac is not None:
        mac = requested_mac.upper()
        name = state.get("scooters", {}).get(mac, {}).get("name")
        return mac, name
    default = state.get("default")
    if default is not None:
        return default.upper(), state.get("scooters", {}).get(default, {}).get("name")
    log.info("no saved scooter — scanning…")
    found = await scan(timeout=5.0)
    if not found:
        raise RuntimeError("no ZT3 found on scan (manufacturer id 0x434E)")
    log.info(f"found {found[0].address} ({found[0].name}) RSSI={found[0].rssi} dBm")
    return found[0].address.upper(), found[0].name


@asynccontextmanager
async def open_session(mac: Optional[str] = None, name: Optional[str] = None) -> AsyncIterator[Session]:
    """Connect, handshake, yield an active Session.

    Resumes silently if `~/.zt3-cli/state.json` has a cryptoRandom for
    this MAC. Falls back to fresh pair (user must hold power button on
    the scooter when prompted).
    """
    state = _load_state()
    target_mac, resolved_name = await _resolve_mac(mac, state)
    final_name = name or resolved_name or "ZT3"

    crypto = NinebotCrypto(scooter_name=final_name)
    persisted = _persisted_random(state, target_mac)

    async with BleSession(target_mac) as ble:
        # Tiny pause so notify subscription is fully wired before Stage 1.
        await asyncio.sleep(0.2)
        await perform_handshake(ble, crypto, persisted)

        # Persist the (possibly newly-generated) random so next run resumes silently.
        _save_random(state, target_mac, final_name, crypto.snapshot_random())
        _save_state(state)

        yield Session(ble=ble, crypto=crypto, mac=target_mac, name=final_name)
