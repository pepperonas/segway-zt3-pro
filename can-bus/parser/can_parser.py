#!/usr/bin/env python3
"""KingstVIS CAN-CSV-Export → Frame-Statistik + Diff-Analyse.

KingstVIS exports CAN-decoded results in a few different CSV layouts
depending on version. This parser auto-detects common variants and
collapses them into a unified Frame model:

    Frame(time_s, can_id, dlc, data: list[int], crc, ack)

Usage:
    python can_parser.py path/to/capture.csv                    # summary
    python can_parser.py path/to/capture.csv --watch 0x100      # show one ID over time
    python can_parser.py path/to/capture.csv --diff             # byte-by-byte change report
    python can_parser.py a.csv b.csv --compare                  # two-capture diff (which IDs differ)
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Frame:
    time_s: float
    can_id: int
    dlc: int
    data: list[int]
    crc: int | None = None
    ack: bool | None = None


def _parse_int(s: str) -> int | None:
    s = (s or "").strip().replace("0x", "").replace("0X", "")
    if not s:
        return None
    try:
        return int(s, 16)
    except ValueError:
        try:
            return int(s)
        except ValueError:
            return None


def parse_kingst_csv(path: Path) -> list[Frame]:
    """Auto-detect KingstVIS CAN export layout and yield Frames."""
    rows = list(csv.reader(path.open()))
    if not rows:
        return []

    # Strategy 1 — "wide" format with one row per frame:
    # Time [s], Type, Identifier, RTR, IDE, DLC, Data 0..7, CRC, ACK
    header = [c.strip().lower() for c in rows[0]]
    if any("identifier" in h or "id" == h or h.startswith("id") for h in header):
        return _parse_wide(rows)

    # Strategy 2 — "long" format with one row per decoded element:
    # Time [s], Value
    # Frame is reassembled from sequential rows: ID:0xNN, DLC:0xN, Data:0xNN..., CRC:0xN, ACK
    return _parse_long(rows)


def _parse_wide(rows: list[list[str]]) -> list[Frame]:
    header = [c.strip().lower() for c in rows[0]]
    idx = {name: i for i, name in enumerate(header)}

    def col(row: list[str], *names: str) -> str | None:
        for n in names:
            if n in idx and idx[n] < len(row):
                return row[idx[n]]
        return None

    out: list[Frame] = []
    for row in rows[1:]:
        if not row or all(not c.strip() for c in row):
            continue
        t = col(row, "time [s]", "time(s)", "time")
        ident = col(row, "identifier", "id")
        dlc = col(row, "dlc")
        if not ident:
            continue
        # KingstVIS only emits Frames for Type=Data (skip "Error", "Remote", etc.)
        ftype = (col(row, "type") or "").strip().lower()
        if ftype and ftype not in ("data", "data frame", "frame"):
            continue
        try:
            tf = float(t) if t else 0.0
        except ValueError:
            tf = 0.0
        data: list[int] = []
        # Strategy A: single "Data" column with space-separated hex bytes
        combined = col(row, "data")
        if combined and (" " in combined or len(combined.replace("0x", "").strip()) > 4):
            for tok in combined.split():
                iv = _parse_int(tok)
                if iv is not None:
                    data.append(iv)
        # Strategy B: separate "Data 0".."Data 7" columns
        if not data:
            for i in range(8):
                v = col(row, f"data {i}", f"d{i}", f"data{i}")
                iv = _parse_int(v) if v else None
                if iv is not None:
                    data.append(iv)
        crc = _parse_int(col(row, "crc") or "")
        ack_raw = (col(row, "ack") or "").strip().lower()
        ack = ack_raw in ("ack", "1", "true", "yes")
        out.append(
            Frame(
                time_s=tf,
                can_id=_parse_int(ident) or 0,
                dlc=_parse_int(dlc) or len(data),
                data=data,
                crc=crc,
                ack=ack,
            )
        )
    return out


_LONG_PAT = re.compile(r"^(ID|DLC|Data|CRC|ACK)\s*[:=]?\s*(.*)$", re.IGNORECASE)


def _parse_long(rows: list[list[str]]) -> list[Frame]:
    out: list[Frame] = []
    cur_time = 0.0
    cur_id: int | None = None
    cur_dlc: int | None = None
    cur_data: list[int] = []
    cur_crc: int | None = None
    cur_ack: bool | None = None

    def flush():
        nonlocal cur_id, cur_dlc, cur_data, cur_crc, cur_ack
        if cur_id is not None:
            out.append(
                Frame(
                    time_s=cur_time,
                    can_id=cur_id,
                    dlc=cur_dlc if cur_dlc is not None else len(cur_data),
                    data=list(cur_data),
                    crc=cur_crc,
                    ack=cur_ack,
                )
            )
        cur_id = None
        cur_dlc = None
        cur_data = []
        cur_crc = None
        cur_ack = None

    # Skip optional header
    start = 0
    if rows and rows[0] and any(c.strip().lower() in ("time [s]", "time(s)", "value") for c in rows[0]):
        start = 1

    for row in rows[start:]:
        if not row:
            continue
        # Two-column "Time, Value"
        if len(row) >= 2:
            try:
                t = float(row[0])
            except ValueError:
                t = cur_time
            value = row[1]
        else:
            t = cur_time
            value = row[0]

        m = _LONG_PAT.match(value.strip())
        if not m:
            continue
        kind = m.group(1).upper()
        payload = m.group(2).strip()

        if kind == "ID":
            flush()
            cur_time = t
            cur_id = _parse_int(payload)
        elif kind == "DLC":
            cur_dlc = _parse_int(payload)
        elif kind == "DATA":
            v = _parse_int(payload)
            if v is not None:
                cur_data.append(v)
        elif kind == "CRC":
            cur_crc = _parse_int(payload)
        elif kind == "ACK":
            cur_ack = True
            flush()  # ACK marks end-of-frame in KingstVIS long-format

    flush()
    return out


def summarize(frames: list[Frame]) -> None:
    if not frames:
        print("# no frames parsed — check CSV format")
        return
    by_id: dict[int, list[Frame]] = defaultdict(list)
    for f in frames:
        by_id[f.can_id].append(f)

    duration = (frames[-1].time_s - frames[0].time_s) if len(frames) > 1 else 0.0
    print(f"# {len(frames)} frames over {duration:.3f}s ({len(by_id)} unique IDs)")
    print(f"{'ID':>6}  {'count':>6}  {'rate':>6}  {'dlc':>3}  {'first data':<24}  {'variability'}")

    for cid in sorted(by_id):
        frs = by_id[cid]
        rate = len(frs) / duration if duration > 0 else 0.0
        dlc = frs[0].dlc
        first = " ".join(f"{b:02X}" for b in frs[0].data)
        # Per-byte variability: HSet of distinct values
        per_byte = []
        for i in range(dlc):
            vals = {f.data[i] for f in frs if i < len(f.data)}
            per_byte.append(len(vals))
        var_str = " ".join(f"{n:>2}" for n in per_byte)
        print(f"  0x{cid:03X}  {len(frs):>6}  {rate:>5.1f}/s  {dlc:>3}  {first:<24}  [{var_str}]")


def watch(frames: list[Frame], target: int) -> None:
    matched = [f for f in frames if f.can_id == target]
    if not matched:
        print(f"# no frames with ID 0x{target:X}")
        return
    last_data: list[int] | None = None
    print(f"# {len(matched)} frames for 0x{target:X} — printing only changes")
    for f in matched:
        if f.data != last_data:
            cur = " ".join(f"{b:02X}" for b in f.data)
            if last_data is None:
                print(f"  [{f.time_s:8.4f}s]  {cur}")
            else:
                prev = " ".join(f"{b:02X}" for b in last_data)
                diff = []
                for i, (a, b) in enumerate(zip(last_data, f.data)):
                    diff.append(" .." if a == b else f"{b:02X}")
                diff_str = " ".join(diff)
                print(f"  [{f.time_s:8.4f}s]  {cur}    Δ {diff_str}")
            last_data = f.data


def diff(frames: list[Frame]) -> None:
    by_id: dict[int, list[Frame]] = defaultdict(list)
    for f in frames:
        by_id[f.can_id].append(f)
    for cid in sorted(by_id):
        frs = by_id[cid]
        if len(frs) < 2:
            continue
        # find bytes that EVER changed
        n = max(f.dlc for f in frs)
        changed_bytes = []
        for i in range(n):
            vals = {f.data[i] for f in frs if i < len(f.data)}
            if len(vals) > 1:
                changed_bytes.append((i, sorted(vals)))
        if changed_bytes:
            print(f"# 0x{cid:03X} — {len(frs)} frames, changing bytes:")
            for i, vals in changed_bytes:
                vstr = " ".join(f"{v:02X}" for v in vals[:16])
                more = f" (+{len(vals)-16})" if len(vals) > 16 else ""
                print(f"   byte {i}: {len(vals)} distinct values: {vstr}{more}")


def compare(frames_a: list[Frame], frames_b: list[Frame]) -> None:
    by_a: dict[int, set[tuple[int, ...]]] = defaultdict(set)
    by_b: dict[int, set[tuple[int, ...]]] = defaultdict(set)
    for f in frames_a:
        by_a[f.can_id].add(tuple(f.data))
    for f in frames_b:
        by_b[f.can_id].add(tuple(f.data))
    all_ids = sorted(set(by_a) | set(by_b))
    print(f"# IDs in A only: {sorted(set(by_a) - set(by_b))}")
    print(f"# IDs in B only: {sorted(set(by_b) - set(by_a))}")
    print(f"# IDs with different value-sets between A and B:")
    for cid in all_ids:
        if cid not in by_a or cid not in by_b:
            continue
        only_a = by_a[cid] - by_b[cid]
        only_b = by_b[cid] - by_a[cid]
        if only_a or only_b:
            print(f"   0x{cid:03X}: {len(only_a)} A-only, {len(only_b)} B-only")


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv", nargs="+", type=Path)
    ap.add_argument("--watch", type=lambda s: int(s, 0), help="show all frames of one CAN ID")
    ap.add_argument("--diff", action="store_true", help="report which bytes change per ID")
    ap.add_argument("--compare", action="store_true", help="compare two captures")
    args = ap.parse_args(argv[1:])

    if args.compare and len(args.csv) == 2:
        a = parse_kingst_csv(args.csv[0])
        b = parse_kingst_csv(args.csv[1])
        print(f"# A: {args.csv[0].name} — {len(a)} frames")
        print(f"# B: {args.csv[1].name} — {len(b)} frames")
        compare(a, b)
        return 0

    frames = parse_kingst_csv(args.csv[0])
    if args.watch is not None:
        watch(frames, args.watch)
    elif args.diff:
        diff(frames)
    else:
        summarize(frames)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
