#!/usr/bin/env python3
"""Passive UART-Sniffer für ZT3-Stem-Bus.

Liest den 55-AA-Frame-Stream über einen USB-UART-Adapter (z.B. CH340-basierter
BAITE BTE13-007) und gibt jedes valide Frame als Hex-Dump mit Zeitstempel aus.

Hardware:
    USB-UART RXD → ZT3 gelb (UART-Bus)
    USB-UART GND → ZT3 schwarz / Chassis
    Logic-Switch:  3.3 V
    USB-UART TXD bleibt frei (passive-only)

Frame-Format (Ninebot/m365-Familie):
    55 AA <bLen> <bAddr> <bCmd> <bArg> <payload[bLen-2]> <wChecksumLE>
    wChecksum = ~(bLen + bAddr + bCmd + bArg + sum(payload)) & 0xFFFF

Usage:
    python3 -m pip install pyserial
    python3 uart_sniff.py /dev/tty.wchusbserial-XXX
"""

from __future__ import annotations

import sys
import time
from typing import Optional

try:
    import serial
except ImportError:
    sys.stderr.write("pyserial fehlt: pip install pyserial\n")
    sys.exit(1)


def parse_frames(buf: bytearray) -> tuple[list[bytes], bytearray]:
    """Findet alle vollständigen 55-AA-Frames im Buffer.

    Liefert (frames, restbuffer). Der Restbuffer enthält partielle Daten am Ende,
    die für das nächste read-Event behalten werden.
    """
    frames: list[bytes] = []
    while True:
        idx = buf.find(b"\x55\xaa")
        if idx < 0:
            return frames, bytearray()
        # Brauchen wenigstens magic + len-byte
        if len(buf) - idx < 3:
            return frames, buf[idx:]
        b_len = buf[idx + 2]
        # Frame-Total = 2 (magic) + 1 (len) + 1 (addr) + 1 (cmd) + 1 (arg)
        #               + (len-2) (payload) + 2 (checksum) = len + 6
        frame_total = b_len + 6
        if frame_total > 256 or b_len < 2:
            # Vermutlich Sync-Verlust; ein Byte vor und neu suchen.
            buf = buf[idx + 1 :]
            continue
        if len(buf) - idx < frame_total:
            return frames, buf[idx:]
        frame = bytes(buf[idx : idx + frame_total])
        if validate_checksum(frame):
            frames.append(frame)
            buf = buf[idx + frame_total :]
        else:
            # Ungültige Checksum — wahrscheinlich falsche Magic-Position.
            buf = buf[idx + 1 :]


def validate_checksum(frame: bytes) -> bool:
    """Inverted-Sum-16-Bit über bytes 2..-2 (alles ohne magic + checksum)."""
    if len(frame) < 6:
        return False
    body = frame[2:-2]
    s = sum(body) & 0xFFFF
    expected = (~s) & 0xFFFF
    actual = frame[-2] | (frame[-1] << 8)
    return expected == actual


def annotate(frame: bytes) -> str:
    """Knappe Klartext-Bedeutung der ersten 4 Body-Bytes."""
    if len(frame) < 6:
        return "<short>"
    b_len, b_addr, b_cmd, b_arg = frame[2], frame[3], frame[4], frame[5]
    payload = frame[6:-2]
    return (
        f"len={b_len:02X} addr={b_addr:02X} cmd={b_cmd:02X} arg={b_arg:02X} "
        f"pl=[{payload.hex(' ')}]"
    )


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        sys.stderr.write(
            "Usage: uart_sniff.py <serial-device> [baudrate=115200]\n"
            "Beispiele:\n"
            "  uart_sniff.py /dev/tty.wchusbserial1410\n"
            "  uart_sniff.py /dev/tty.SLAB_USBtoUART 115200\n"
        )
        return 2

    port = argv[1]
    baud = int(argv[2]) if len(argv) > 2 else 115200

    print(f"# opening {port} @ {baud} 8N1, idle-high (passive sniff)")
    print("# columns: timestamp  raw-hex  annotation")
    print("# ctrl-c to stop\n")

    ser = serial.Serial(port, baudrate=baud, bytesize=8, parity="N", stopbits=1, timeout=0.1)
    buf = bytearray()
    started = time.monotonic()
    frame_count = 0

    try:
        while True:
            chunk = ser.read(256)
            if chunk:
                buf.extend(chunk)
                frames, buf = parse_frames(buf)
                for fr in frames:
                    frame_count += 1
                    ts = time.monotonic() - started
                    print(f"[{ts:7.3f}]  {fr.hex(' ')}  ({annotate(fr)})")
    except KeyboardInterrupt:
        elapsed = time.monotonic() - started
        rate = frame_count / elapsed if elapsed > 0 else 0
        sys.stderr.write(
            f"\nstopped after {elapsed:.1f}s — {frame_count} valid frames "
            f"({rate:.1f}/s)\n"
        )
        return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
