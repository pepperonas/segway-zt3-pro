"""Frame codec for NinebotCrypto wire format.

Mirrors the existing Kotlin `FrameCodecCrypto` + `FrameCodecClassic`.
Inner frame is `[5A A5 (n-4) src dst cmd arg payload]`; the
[`NinebotCrypto`][zt3_cli.crypto.NinebotCrypto] adds a 6-byte trailer
(4 tag + 2 counter) to produce the on-the-wire bytes.
"""

from dataclasses import dataclass
from typing import Optional

from .crypto import NinebotCrypto


# ── Wire constants (byte values) ─────────────────────────────────

MAGIC_HI = 0x5A
MAGIC_LO = 0xA5

SRC_PHONE = 0x3E

DST_HANDSHAKE = 0x04  # crypto handshake (cmd 0x5B/0x5C/0x5D)
DST_VCU = 0x16
DST_MCU = 0x02
DST_BMS = 0x07
DST_BLE = 0x04

CMD_READ = 0x01
CMD_WRITE = 0x02
CMD_RESP_READ = 0x04
CMD_RESP_WRITE = 0x05
CMD_HANDSHAKE_RANDOM = 0x5B
CMD_HANDSHAKE_PAIR = 0x5C
CMD_HANDSHAKE_CHALLENGE = 0x5D


@dataclass(frozen=True)
class Decoded:
    src: int
    dst: int
    cmd: int
    arg: int
    payload: bytes


def wrap(crypto: NinebotCrypto, src: int, dst: int, cmd: int, arg: int, payload: bytes) -> bytes:
    """Build an inner frame and run it through `NinebotCrypto.encrypt`."""
    inner = bytes([src & 0xFF, dst & 0xFF, cmd & 0xFF, arg & 0xFF]) + payload
    plain = bytes([MAGIC_HI, MAGIC_LO, (len(inner) - 4) & 0xFF]) + inner
    return crypto.encrypt(plain)


def parse(crypto: NinebotCrypto, frame: bytes) -> Optional[Decoded]:
    """Decrypt and parse a wire frame. Returns None on bad magic / size."""
    if len(frame) < 9:
        return None
    if frame[0] != MAGIC_HI or frame[1] != MAGIC_LO:
        return None
    plain = crypto.decrypt(frame)
    if plain is None or len(plain) < 7:
        return None
    length = plain[2] & 0xFF
    if len(plain) < length + 7:
        return None
    return Decoded(
        src=plain[3] & 0xFF,
        dst=plain[4] & 0xFF,
        cmd=plain[5] & 0xFF,
        arg=plain[6] & 0xFF,
        payload=bytes(plain[7 : length + 7]),
    )


def read_register(crypto: NinebotCrypto, dst: int, register: int, length: int) -> bytes:
    """Build a read-register frame (cmd=0x01)."""
    return wrap(crypto, SRC_PHONE, dst, CMD_READ, register, bytes([length & 0xFF]))


def write_register(crypto: NinebotCrypto, dst: int, register: int, payload: bytes) -> bytes:
    """Build a write-register frame (cmd=0x02)."""
    return wrap(crypto, SRC_PHONE, dst, CMD_WRITE, register, payload)


def challenge_response(crypto: NinebotCrypto, dst: int, challenge: bytes) -> bytes:
    """Stage-3 handshake frame (cmd=0x5D) echoing the 14-byte challenge."""
    return wrap(crypto, SRC_PHONE, dst, CMD_HANDSHAKE_CHALLENGE, 0x00, challenge)
