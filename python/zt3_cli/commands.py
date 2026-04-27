"""High-level command implementations: read, write, sweep, watch, hunt.

Each function takes a connected `Session` and produces side-effects
(prints, writes). The CLI dispatcher in `cli.py` calls these directly.
"""

from __future__ import annotations

import asyncio
import sys
import time
from typing import Optional

from .session import Session


# ── helpers ──────────────────────────────────────────────────────


def _hex(b: bytes) -> str:
    return " ".join(f"{x:02X}" for x in b)


def _decode_uint16_le(b: bytes) -> Optional[int]:
    if len(b) < 2:
        return None
    return b[0] | (b[1] << 8)


def _decode_pretty(dst: int, reg: int, payload: bytes) -> str:
    """Best-effort human-readable decoding for a few well-known registers."""
    u16 = _decode_uint16_le(payload)
    if dst == 0x16 and reg == 0x55 and u16 is not None:
        return f"{payload[0]} %  (battery)"
    if dst == 0x07 and reg == 0x8F and len(payload) >= 1:
        return f"{payload[0]} %  (BMS SOC)"
    if dst == 0x07 and reg == 0x8C and u16 is not None:
        return f"{u16 / 100:.2f} V  (pack)"
    if dst == 0x16 and reg == 0x57 and u16 is not None:
        return f"{u16 / 10:.1f} km/h  (speed/throttle)"
    if dst == 0x02 and reg == 0x86 and u16 is not None:
        return f"{u16 / 10:.1f} km/h  (MCU speed)"
    if u16 is not None:
        return f"u16-LE = {u16}"
    return ""


# ── commands ─────────────────────────────────────────────────────


async def cmd_read(session: Session, dst: int, register: int, length: int = 2) -> int:
    decoded = await session.read_register(dst, register, length)
    if decoded is None:
        print(f"timeout — no response from {dst:#04x}:{register:#04x}", file=sys.stderr)
        return 1
    pretty = _decode_pretty(dst, register, decoded.payload)
    suffix = f"  ({pretty})" if pretty else ""
    print(f"{dst:02X}:{register:02X}  [{_hex(decoded.payload)}]{suffix}")
    return 0


async def cmd_write(session: Session, dst: int, register: int, value: int) -> int:
    """Write a uint16-LE value (encoding the value as 2 bytes)."""
    if value < 0 or value > 0xFFFF:
        print("value must fit in uint16 (0..65535)", file=sys.stderr)
        return 1
    payload = bytes([value & 0xFF, (value >> 8) & 0xFF])
    await session.write_register(dst, register, payload)
    print(f"wrote {dst:02X}:{register:02X} = {value} ([{_hex(payload)}])")
    return 0


async def cmd_sweep(session: Session, dst: int, lo: int, hi: int) -> int:
    """One-shot pass over a register range — prints every successful read."""
    for reg in range(lo, hi + 1):
        decoded = await session.read_register(dst, reg, 2)
        if decoded is None:
            continue
        if decoded.cmd != 0x04:
            continue
        print(f"{dst:02X}:{reg:02X}  [{_hex(decoded.payload)}]")
        await asyncio.sleep(0.01)
    return 0


async def cmd_watch(session: Session, dst: int, lo: int, hi: int, interval_ms: int = 100) -> int:
    """Continuously polls [lo..hi], prints only changes (live diff)."""
    print(
        f"Watching dst={dst:#04x} regs {lo:#04x}..{hi:#04x} every {interval_ms} ms.\n"
        f"Press buttons / blinker / lever on the scooter. Ctrl-C to stop.\n",
        flush=True,
    )
    last: dict[int, bytes] = {}
    try:
        while True:
            for reg in range(lo, hi + 1):
                decoded = await session.read_register(dst, reg, 2)
                if decoded is None:
                    continue
                payload = decoded.payload
                prev = last.get(reg)
                if prev is None:
                    last[reg] = payload
                    continue
                if payload != prev:
                    diff_bits = _bit_diff_summary(prev, payload)
                    ts = time.strftime("%H:%M:%S")
                    print(
                        f"[{ts}] {dst:02X}:{reg:02X}  [{_hex(prev)}] → [{_hex(payload)}]  {diff_bits}",
                        flush=True,
                    )
                    last[reg] = payload
            await asyncio.sleep(interval_ms / 1000.0)
    except KeyboardInterrupt:
        print("\nstopped.", file=sys.stderr)
        return 0


def _bit_diff_summary(before: bytes, after: bytes) -> str:
    if len(before) != len(after):
        return f"(length changed {len(before)}→{len(after)})"
    bits_changed: list[str] = []
    for byte_idx in range(len(before)):
        x = before[byte_idx] ^ after[byte_idx]
        if x == 0:
            continue
        for bit_idx in range(8):
            if (x >> bit_idx) & 1:
                global_bit = byte_idx * 8 + bit_idx
                direction = "+" if (after[byte_idx] >> bit_idx) & 1 else "-"
                bits_changed.append(f"{direction}bit{global_bit}")
    if not bits_changed:
        return ""
    if len(bits_changed) <= 4:
        return f"({', '.join(bits_changed)})"
    return f"({len(bits_changed)} bits changed)"


async def cmd_hunt(session: Session, dst: int, lo: int = 0x00, hi: int = 0xFF) -> int:
    """Triple-sweep with counter filtering: A → A2 (counter baseline) → B
    (after user trigger). Diffs A2→B excluding regs that changed A→A2.
    """
    print("=== triple-sweep starting ===", flush=True)
    a = await _sweep_capture(session, dst, lo, hi)
    print(f"sweep A: {len(a)} regs captured — counter baseline (5 s, do nothing)", flush=True)
    await asyncio.sleep(5.0)

    a2 = await _sweep_capture(session, dst, lo, hi)
    counters = {reg for reg, before in a.items() if (after := a2.get(reg)) is not None and before != after}
    print(
        f"{len(counters)} natural counter(s) detected — TRIGGER NOW (5 s)\n"
        "  → press the button / activate the blinker / pull the brake",
        flush=True,
    )
    await asyncio.sleep(5.0)

    b = await _sweep_capture(session, dst, lo, hi)
    print(f"sweep B: {len(b)} regs captured — diffing (excluding counters)\n", flush=True)

    diffs = 0
    for reg, before in a2.items():
        after = b.get(reg)
        if after is None or before == after:
            continue
        if reg in counters:
            continue
        diffs += 1
        diff_bits = _bit_diff_summary(before, after)
        print(f"DIFF {dst:02X}:{reg:02X}  [{_hex(before)}] → [{_hex(after)}]  {diff_bits}", flush=True)

    print(
        f"\n=== done — {diffs} candidate(s), {len(counters)} counter(s) filtered out ===",
        flush=True,
    )
    return 0


async def _sweep_capture(session: Session, dst: int, lo: int, hi: int) -> dict[int, bytes]:
    """One sweep pass, returns {register: payload} for successful reads."""
    out: dict[int, bytes] = {}
    for reg in range(lo, hi + 1):
        decoded = await session.read_register(dst, reg, 2)
        if decoded is None:
            continue
        if decoded.cmd != 0x04:
            continue
        out[reg] = decoded.payload
        await asyncio.sleep(0.005)
    return out
