"""Frame-Analyse-Heuristiken fuer den ZT3-CAN-Bus.

Reine Funktionen ohne I/O, damit sie host-seitig testbar sind
(``python3 -m unittest`` im Verzeichnis ``can-bus/parser``).

Enthalten:
- Sendeperiode / Jitter / DLC-Konstanz
- Byte-Statistik inkl. Pearson-Korrelation zu einem Referenz-Byte
- Rolling-Counter-Erkennung (ganzes Byte und je Nibble, mit Wrap)
- Checksummen-Suche (XOR, Summe, invertierte Summe, gaengige CRC-8)
- Stufenantwort: Latenz zwischen einer Flanke im Eingangssignal und der
  ersten Reaktion eines Antwortsignals

(c) 2026 Martin Pfeffer | celox.io
"""

from __future__ import annotations

import math
import statistics
from collections import Counter
from dataclasses import dataclass, field
from typing import Callable, Iterable, Sequence


# ─────────────────────────────────────────────────────────────────────────────
# Vorfilter
# ─────────────────────────────────────────────────────────────────────────────

def valid_frames(frames: Iterable, can_id: int | None = None) -> list:
    """Nur quittierte Frames, deren Nutzdaten zur DLC passen.

    KingstVIS liefert bei Decoder-Glitches Frames ohne ACK oder mit zu wenig
    Datenbytes. Die verfaelschen jede Statistik, deshalb fliegen sie raus.
    ``ack is None`` (Format ohne ACK-Spalte) gilt als gueltig.
    """
    out = []
    for f in frames:
        if can_id is not None and f.can_id != can_id:
            continue
        if f.ack is False:
            continue
        if len(f.data) != f.dlc:
            continue
        out.append(f)
    return out


# ─────────────────────────────────────────────────────────────────────────────
# Periode
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class PeriodStats:
    count: int
    mean_ms: float
    min_ms: float
    max_ms: float
    stdev_ms: float
    median_ms: float
    gaps: int  # Intervalle > 1,5 x Median (fehlende Frames oder Decoder-Verlust)


def period_stats(times: Sequence[float]) -> PeriodStats | None:
    if len(times) < 3:
        return None
    dt = [(b - a) * 1000.0 for a, b in zip(times, times[1:])]
    med = statistics.median(dt)
    return PeriodStats(
        count=len(times),
        mean_ms=statistics.fmean(dt),
        min_ms=min(dt),
        max_ms=max(dt),
        stdev_ms=statistics.pstdev(dt),
        median_ms=med,
        gaps=sum(1 for d in dt if d > 1.5 * med),
    )


# ─────────────────────────────────────────────────────────────────────────────
# Byte-Statistik
# ─────────────────────────────────────────────────────────────────────────────

def pearson(xs: Sequence[float], ys: Sequence[float]) -> float | None:
    """Pearson-Korrelation. None, wenn eine Reihe konstant ist."""
    n = len(xs)
    if n < 2 or n != len(ys):
        return None
    mx = statistics.fmean(xs)
    my = statistics.fmean(ys)
    sxx = sum((x - mx) ** 2 for x in xs)
    syy = sum((y - my) ** 2 for y in ys)
    if sxx == 0 or syy == 0:
        return None
    sxy = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    return sxy / math.sqrt(sxx * syy)


@dataclass
class ByteStats:
    index: int
    min: int
    max: int
    distinct: int
    corr_ref: float | None


def byte_stats(datas: Sequence[Sequence[int]], ref_index: int = 0) -> list[ByteStats]:
    if not datas:
        return []
    n = min(len(d) for d in datas)
    ref = [d[ref_index] for d in datas] if ref_index < n else None
    out = []
    for i in range(n):
        col = [d[i] for d in datas]
        out.append(ByteStats(
            index=i,
            min=min(col),
            max=max(col),
            distinct=len(set(col)),
            corr_ref=None if (ref is None or i == ref_index) else pearson(col, ref),
        ))
    return out


# ─────────────────────────────────────────────────────────────────────────────
# Rolling Counter
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class CounterCandidate:
    index: int
    field: str        # "byte", "lo", "hi"
    step: int         # haeufigstes Inkrement modulo Feldbreite
    hit_rate: float   # Anteil der Uebergaenge mit genau diesem Inkrement
    wraps: int        # beobachtete Ueberlaeufe


_FIELDS: dict[str, tuple[Callable[[int], int], int]] = {
    "byte": (lambda v: v, 256),
    "lo": (lambda v: v & 0x0F, 16),
    "hi": (lambda v: (v >> 4) & 0x0F, 16),
}


def find_counters(datas: Sequence[Sequence[int]], min_rate: float = 0.9,
                  min_transitions: int = 8) -> list[CounterCandidate]:
    """Sucht Felder, die pro Frame um ein konstantes Inkrement (ungleich 0)
    weiterzaehlen, modulo Feldbreite. Ein konstantes Feld ist kein Counter.

    Toleriert einzelne Aussetzer (fehlende Frames) ueber ``min_rate``.
    """
    out: list[CounterCandidate] = []
    if len(datas) < min_transitions + 1:
        return out
    n = min(len(d) for d in datas)
    for i in range(n):
        for name, (get, mod) in _FIELDS.items():
            vals = [get(d[i]) for d in datas]
            diffs = [(b - a) % mod for a, b in zip(vals, vals[1:])]
            step, hits = Counter(diffs).most_common(1)[0]
            if step == 0:
                continue
            rate = hits / len(diffs)
            if rate < min_rate:
                continue
            wraps = sum(1 for a, b in zip(vals, vals[1:])
                        if (b - a) % mod == step and b < a)
            out.append(CounterCandidate(i, name, step, rate, wraps))
    # Ein Byte-Counter erzeugt immer auch einen Low-Nibble-Treffer: nur das
    # breiteste Feld je Byte behalten.
    best: dict[int, CounterCandidate] = {}
    for c in out:
        if c.index not in best or (c.field == "byte" and best[c.index].field != "byte"):
            best[c.index] = c
    return sorted(best.values(), key=lambda c: c.index)


# ─────────────────────────────────────────────────────────────────────────────
# Checksummen
# ─────────────────────────────────────────────────────────────────────────────

def _reflect8(v: int) -> int:
    r = 0
    for _ in range(8):
        r = (r << 1) | (v & 1)
        v >>= 1
    return r


def crc8(data: Iterable[int], poly: int, init: int = 0, xorout: int = 0,
         refin: bool = False, refout: bool = False) -> int:
    crc = init & 0xFF
    for b in data:
        if refin:
            b = _reflect8(b)
        crc ^= b & 0xFF
        for _ in range(8):
            crc = ((crc << 1) ^ poly) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    if refout:
        crc = _reflect8(crc)
    return crc ^ xorout


# Parameter nach dem CRC-Katalog von Greg Cook (reveng), Pruefwerte ueber
# b"123456789" stehen in den Tests.
CRC8_PRESETS: dict[str, dict] = {
    "crc8": dict(poly=0x07, init=0x00, xorout=0x00),
    "crc8-itu": dict(poly=0x07, init=0x00, xorout=0x55),
    "crc8-sae-j1850": dict(poly=0x1D, init=0xFF, xorout=0xFF),
    "crc8-gsm-a": dict(poly=0x1D, init=0x00, xorout=0x00),
    "crc8-autosar": dict(poly=0x2F, init=0xFF, xorout=0xFF),
    "crc8-maxim": dict(poly=0x31, init=0x00, xorout=0x00, refin=True, refout=True),
    "crc8-cdma2000": dict(poly=0x9B, init=0xFF, xorout=0x00),
}


def _xor(bs: Sequence[int]) -> int:
    r = 0
    for b in bs:
        r ^= b
    return r


CHECKSUM_ALGOS: dict[str, Callable[[Sequence[int]], int]] = {
    "xor": _xor,
    "sum8": lambda bs: sum(bs) & 0xFF,
    "sum8-inv": lambda bs: (~sum(bs)) & 0xFF,
    "sum8-neg": lambda bs: (-sum(bs)) & 0xFF,
    **{name: (lambda p: lambda bs: crc8(bs, **p))(params)
       for name, params in CRC8_PRESETS.items()},
}


@dataclass
class ChecksumCandidate:
    target: int
    inputs: str          # Beschreibung der Eingangsbytes
    algo: str
    with_id: bool
    hit_rate: float
    distinct_targets: int
    distinct_inputs: int


def _input_sets(n: int, target: int) -> list[tuple[str, list[int]]]:
    sets = []
    if target > 0:
        sets.append((f"0..{target - 1}", list(range(target))))
    others = [i for i in range(n) if i != target]
    if others and (target == 0 or others != list(range(target))):
        sets.append(("alle ausser Ziel", others))
    return sets


def find_checksums(datas: Sequence[Sequence[int]], can_id: int | None = None,
                   min_rate: float = 0.98) -> list[ChecksumCandidate]:
    """Prueft jedes Byte als moegliches Checksummen-Ziel gegen gaengige
    Algorithmen ueber (a) alle vorhergehenden Bytes und (b) alle uebrigen
    Bytes, jeweils ohne und mit vorangestellter CAN-ID (low, high).

    Treffer zaehlen nur, wenn sowohl Eingaenge als auch Ziel variieren:
    konstante Daten erfuellen jede Summe trivial und beweisen nichts.
    """
    out: list[ChecksumCandidate] = []
    if len(datas) < 4:
        return out
    n = min(len(d) for d in datas)
    id_prefix = [] if can_id is None else [can_id & 0xFF, (can_id >> 8) & 0xFF]
    for t in range(n):
        targets = [d[t] for d in datas]
        distinct_t = len(set(targets))
        for label, idxs in _input_sets(n, t):
            rows = [[d[i] for i in idxs] for d in datas]
            distinct_in = len({tuple(r) for r in rows})
            if distinct_t < 2 or distinct_in < 2:
                continue
            for with_id in ([False, True] if id_prefix else [False]):
                pre = id_prefix if with_id else []
                for name, fn in CHECKSUM_ALGOS.items():
                    hits = sum(1 for r, tv in zip(rows, targets) if fn(pre + r) == tv)
                    rate = hits / len(rows)
                    if rate >= min_rate:
                        out.append(ChecksumCandidate(t, label, name, with_id, rate,
                                                     distinct_t, distinct_in))
    return out


def varying_bytes(datas: Sequence[Sequence[int]]) -> list[int]:
    if not datas:
        return []
    n = min(len(d) for d in datas)
    return [i for i in range(n) if len({d[i] for d in datas}) > 1]


# ─────────────────────────────────────────────────────────────────────────────
# Signal-Extraktion + Stufenantwort
# ─────────────────────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class SignalSpec:
    """``0x211:6:u16le`` = ID 0x211, ab Byte 6, 16 Bit little-endian."""
    can_id: int
    offset: int
    kind: str = "u8"   # u8, s8, u16le, s16le, u16be, s16be

    @staticmethod
    def parse(text: str) -> "SignalSpec":
        parts = text.split(":")
        if len(parts) not in (2, 3):
            raise ValueError(f"Signal-Spec '{text}' erwartet ID:Byte[:Typ]")
        kind = parts[2].lower() if len(parts) == 3 else "u8"
        if kind not in ("u8", "s8", "u16le", "s16le", "u16be", "s16be"):
            raise ValueError(f"unbekannter Typ '{kind}'")
        return SignalSpec(int(parts[0], 0), int(parts[1], 0), kind)

    def width(self) -> int:
        return 1 if self.kind in ("u8", "s8") else 2

    def decode(self, data: Sequence[int]) -> int | None:
        o = self.offset
        if o + self.width() > len(data):
            return None
        if self.kind in ("u8", "s8"):
            v = data[o]
            return v - 256 if self.kind == "s8" and v >= 128 else v
        lo, hi = (data[o], data[o + 1]) if self.kind.endswith("le") else (data[o + 1], data[o])
        v = lo | (hi << 8)
        return v - 65536 if self.kind.startswith("s") and v >= 32768 else v

    def __str__(self) -> str:
        return f"0x{self.can_id:03X}:{self.offset}:{self.kind}"


def extract(frames: Iterable, spec: SignalSpec) -> list[tuple[float, int]]:
    out = []
    for f in valid_frames(frames, spec.can_id):
        v = spec.decode(f.data)
        if v is not None:
            out.append((f.time_s, v))
    return out


@dataclass
class Edge:
    time_s: float
    before: int
    after: int

    @property
    def falling(self) -> bool:
        return self.after < self.before


def find_edges(series: Sequence[tuple[float, int]], min_delta: int) -> list[Edge]:
    """Spruenge zwischen zwei aufeinanderfolgenden Samples mit |Delta| >= min_delta."""
    return [Edge(t1, v0, v1) for (t0, v0), (t1, v1) in zip(series, series[1:])
            if abs(v1 - v0) >= min_delta]


@dataclass
class StepResponse:
    edge: Edge
    signal: str
    latency_ms: float | None   # Zeit bis zur ersten Aenderung in Flankenrichtung
    resolution_ms: float | None  # Sample-Abstand des Antwortsignals = Messunsicherheit
    note: str = ""


def step_response(edge: Edge, response: Sequence[tuple[float, int]], signal_name: str,
                  window_s: float = 1.0, direction: int | None = None) -> StepResponse:
    """Latenz von ``edge`` bis zur ersten Aenderung von ``response`` in
    erwarteter Richtung.

    ``direction``: +1 = Antwort steigt, -1 = faellt, None = gleiche Richtung
    wie die Flanke. Die Aufloesung ist nach unten durch die Sendeperiode des
    Antwort-Frames begrenzt und wird mit ausgegeben.
    """
    if direction is None:
        direction = -1 if edge.falling else 1
    before = [(t, v) for t, v in response if t <= edge.time_s]
    after = [(t, v) for t, v in response if edge.time_s < t <= edge.time_s + window_s]
    if not before or not after:
        return StepResponse(edge, signal_name, None, None, "keine Samples um die Flanke")
    ts = [t for t, _ in response]
    res = statistics.median([b - a for a, b in zip(ts, ts[1:])]) * 1000.0 if len(ts) > 1 else None
    ref = before[-1][1]
    for t, v in after:
        if (v - ref) * direction > 0:
            return StepResponse(edge, signal_name, (t - edge.time_s) * 1000.0, res)
        ref = v
    return StepResponse(edge, signal_name, None, res, f"keine Reaktion binnen {window_s:.1f}s")
