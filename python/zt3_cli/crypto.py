"""NinebotCrypto port from `NinebotCrypto.kt` (Kotlin).

Direct port of SHU's `c6.c` class. Re-implements the custom
AES-CBC-MAC + AES-CTR construction used by Segway-Ninebot scooters that
advertise with manufacturer-id `0x434E` ("NC") — i.e. the ZT3 Pro D.

Wire format (`5A A5` magic, Case 2 in `c6/b.java`):

    [5A A5] [len] [src dst cmd arg ENC(payload)] [tag(4)] [ctrHi ctrLo]

Counter is shared between TX and RX (both increment on every frame). The
very first TX uses the `f()`-obfuscation path (counter 0); the RX
response carries a 16-byte token that re-keys the cipher.

Verified byte-for-byte against the Kotlin original via shared unit
tests; see `tests/test_crypto.py`.
"""

import hashlib
import secrets
from typing import Optional

from Crypto.Cipher import AES


SALT = bytes([
    0x97, 0xCF, 0xB8, 0x02,
    0x84, 0x41, 0x43, 0xDE,
    0x56, 0x00, 0x2B, 0x3B,
    0x34, 0x78, 0x0A, 0x5D,
])


def _xor(a: bytes, b: bytes) -> bytes:
    """XOR two byte sequences up to len(min)."""
    n = min(len(a), len(b))
    return bytes(a[i] ^ b[i] for i in range(n))


def _aes_ecb(input_block: bytes, key: bytes) -> bytes:
    """Single-block AES-128-ECB encrypt (16-byte input, 16-byte key)."""
    return AES.new(key, AES.MODE_ECB).encrypt(input_block)


class NinebotCrypto:
    """Port of NinebotCrypto.kt with public methods preserved.

    - `encrypt(data)` and `decrypt(data)` operate on inner frames
      (`[5A A5 len src dst cmd arg payload]`) and produce/consume wire
      bytes (which add a 4-byte tag and 2-byte counter trailer).
    - State machine flags `stage_received_token` (L), `stage_paired_key`
      (M), `stage_fully_paired` (O) mirror SHU's ScooterActivity.
    - `set_random_app_data(random)` is the resume path: re-derives the
      session key as SHA-1(random + token) without sending an o1 frame.
    """

    def __init__(self, scooter_name: str):
        self._scooter_name = scooter_name.encode("utf-8")
        self.token = bytearray(16)
        self.app_random = bytearray(16)
        self.challenge = bytearray(14)
        self.aes_key = bytearray(16)
        self.counter: int = 0
        self.stage_received_token = False
        self.stage_paired_key = False
        self.stage_fully_paired = False
        self._derive_key(self._scooter_name, SALT)

    # ── public state ─────────────────────────────────────────────

    def snapshot_random(self) -> bytes:
        return bytes(self.app_random)

    def snapshot_token(self) -> bytes:
        return bytes(self.token)

    def snapshot_challenge(self) -> bytes:
        return bytes(self.challenge)

    def is_handshake_complete(self) -> bool:
        return self.counter > 0 and any(b != 0 for b in self.token)

    def reset(self) -> None:
        """Reset to fresh-connect state (called on disconnect)."""
        self.counter = 0
        self.stage_received_token = False
        self.stage_paired_key = False
        self.stage_fully_paired = False
        self.token[:] = bytes(16)
        self.app_random[:] = bytes(16)
        self.challenge[:] = bytes(14)
        self._derive_key(self._scooter_name, SALT)

    def reset_pairing_state(self) -> None:
        """Wipe stage-2 (M) and stage-3 (O) state but KEEP the token from
        Stage 1. Re-derives `SHA-1(name + token)` so the next o1 frame
        goes out under the correct cipher. Used as fallback when a
        resume (persisted random) fails at Stage 3.
        """
        self.stage_paired_key = False
        self.stage_fully_paired = False
        self.app_random[:] = bytes(16)
        self._derive_key(self._scooter_name, bytes(self.token))

    def set_random_app_data(self, data: bytes) -> None:
        """Mirror of SHU's `setRandomAppData([B)V`: sets the app-random
        AND re-derives the session key as `SHA-1(appRandom + token)`.
        """
        if len(data) != 16:
            raise ValueError("appRandom must be 16 bytes")
        self.app_random[:] = data
        self._derive_key(bytes(self.app_random), bytes(self.token))

    # ── Stage-N frame builders ───────────────────────────────────

    def build_get_random_frame(self, tx_addr: int) -> bytes:
        """Stage-1 frame — `getBleRandom`. Body `[3E txAddr 5B 00]`,
        plen=0. Triggers the scooter to respond with a token + challenge.
        """
        return bytes([0x5A, 0xA5, 0x00, 0x3E, tx_addr & 0xFF, 0x5B, 0x00])

    def build_pair_init_frame(self, tx_addr: int) -> bytes:
        """Stage-2 frame — `o1(R)`. Body `[3E txAddr 5C 00] + 16 random
        bytes`, plen=0x10. After the scooter ACKs, both sides re-key to
        `SHA-1(appRandom + token)`.
        """
        return bytes([0x5A, 0xA5, 0x10, 0x3E, tx_addr & 0xFF, 0x5C, 0x00]) + secrets.token_bytes(16)

    # ── encrypt / decrypt ────────────────────────────────────────

    def encrypt(self, data: bytes) -> bytes:
        """Encrypt a complete inner frame `[5A A5 len src dst cmd arg
        payload]` to wire bytes (which add a 4-byte tag and 2-byte
        counter trailer).
        """
        length = len(data)
        out = bytearray(length + 6)
        out[0:3] = data[0:3]  # 5A A5 len

        body = bytes(data[3:])

        current = self.counter
        if current == 0:
            # First-message path: f()-obfuscation + 16-bit invsum CRC.
            crc = self._inverted_sum(body)
            obf = self._f_encrypt(body)
            out[3 : 3 + len(obf)] = obf
            out[length] = 0
            out[length + 1] = 0
            out[length + 2] = crc[0]
            out[length + 3] = crc[1]
            out[length + 4] = 0
            out[length + 5] = 0
            self.counter = 1
        else:
            self.counter = current + 1
            tag = self._compute_tag(data, self.counter)
            enc = self._ctr_cipher(body, self.counter)
            out[3 : 3 + len(enc)] = enc
            out[length] = tag[0]
            out[length + 1] = tag[1]
            out[length + 2] = tag[2]
            out[length + 3] = tag[3]
            out[length + 4] = (self.counter >> 8) & 0xFF
            out[length + 5] = self.counter & 0xFF

            # Mirror SHU's i() lines 301-303: capture our own random echo.
            if (
                len(data) >= 23
                and data[0] == 0x5A
                and data[1] == 0xA5
                and data[2] == 0x10
                and data[3] == 0x3E
                and data[5] == 0x5C
                and data[6] == 0x00
            ):
                self.app_random[:] = data[7:23]
        return bytes(out)

    def decrypt(self, data: bytes) -> Optional[bytes]:
        """Decrypt a wire frame back to its inner-frame bytes. Returns
        None if the size is invalid. Tag verification is not enforced —
        SHU also doesn't (it warns on mismatch but proceeds).
        """
        if len(data) < 9:
            return None
        out = bytearray(len(data) - 6)
        out[0:3] = data[0:3]

        # 16-bit BE counter from trailer (last two bytes), stitched with our high-16.
        ctr_incoming = ((data[len(data) - 2] & 0xFF) << 8) | (data[len(data) - 1] & 0xFF)
        effective_counter = (self.counter & ~0xFFFF) + ctr_incoming

        body_len = len(data) - 9
        body = bytes(data[3 : 3 + body_len])

        if effective_counter == 0:
            plain = self._f_encrypt(body)  # self-inverse
        else:
            plain = self._ctr_cipher(body, effective_counter)

        out[3 : 3 + len(plain)] = plain

        # Stage 1 (L flag): cmd=0x5B token+challenge response at counter=0.
        # Inner: [5A A5 1E rxAddr 3E 5B arg token(16) challenge(14)]
        if (
            effective_counter == 0
            and len(out) >= 37
            and out[0] == 0x5A
            and out[1] == 0xA5
            and out[2] == 0x1E
            and out[4] == 0x3E
            and out[5] == 0x5B
        ):
            self.token[:] = out[7:23]
            self.challenge[:] = out[23:37]
            self._derive_key(self._scooter_name, bytes(self.token))
            self.stage_received_token = True

        # Stage 2 (M flag): cmd=0x5C arg=0x01 paired-key confirmation.
        if (
            effective_counter > 0
            and len(out) >= 7
            and out[0] == 0x5A
            and out[1] == 0xA5
            and out[4] == 0x3E
            and out[5] == 0x5C
            and out[6] == 0x01
        ):
            self._derive_key(bytes(self.app_random), bytes(self.token))
            self.stage_paired_key = True

        # Stage 3 (O flag): cmd=0x5D arg=0x01 fully-paired confirmation.
        if (
            effective_counter > 0
            and len(out) >= 7
            and out[0] == 0x5A
            and out[1] == 0xA5
            and out[4] == 0x3E
            and out[5] == 0x5D
            and out[6] == 0x01
        ):
            self.stage_fully_paired = True
            self.stage_paired_key = True  # SHU also sets M=true here

        self.counter = effective_counter + 1
        return bytes(out)

    # ── internals ────────────────────────────────────────────────

    def _derive_key(self, left: bytes, right: bytes) -> None:
        """SHU's `c6.c.d()`: SHA-1 over [left(<=16) | right(<=16)] padded
        to 32 bytes; first 16 bytes of the digest become the AES key.
        """
        buf = bytearray(32)
        buf[0 : min(len(left), 16)] = left[: min(len(left), 16)]
        buf[16 : 16 + min(len(right), 16)] = right[: min(len(right), 16)]
        digest = hashlib.sha1(bytes(buf)).digest()
        self.aes_key[:] = digest[:16]

    def _f_encrypt(self, input_data: bytes) -> bytes:
        """`f()` from `c6.c.f()` — XOR each 16-byte block with
        AES_ECB(salt, aes_key) (a fixed keystream). Self-inverse.
        Used only on the first frame (counter == 0).
        """
        out = bytearray(len(input_data))
        keystream = _aes_ecb(SALT, bytes(self.aes_key))
        i = 0
        while i < len(input_data):
            n = min(16, len(input_data) - i)
            for j in range(n):
                out[i + j] = input_data[i + j] ^ keystream[j]
            i += n
        return bytes(out)

    def _ctr_cipher(self, input_data: bytes, ctr: int) -> bytes:
        """`g()` from `c6.c.g()` — AES-CTR with a 16-byte counter block:
            [01][counter32-BE][token[0..8]][...zeros...][block_index]
        where block_index (the last byte) increments per 16-byte block.
        """
        out = bytearray(len(input_data))
        ctr_block = bytearray(16)
        ctr_block[0] = 0x01
        ctr_block[1] = (ctr >> 24) & 0xFF
        ctr_block[2] = (ctr >> 16) & 0xFF
        ctr_block[3] = (ctr >> 8) & 0xFF
        ctr_block[4] = ctr & 0xFF
        ctr_block[5:13] = self.token[:8]
        ctr_block[15] = 0
        i = 0
        while i < len(input_data):
            ctr_block[15] = (ctr_block[15] + 1) & 0xFF
            keystream = _aes_ecb(bytes(ctr_block), bytes(self.aes_key))
            n = min(16, len(input_data) - i)
            for j in range(n):
                out[i + j] = input_data[i + j] ^ keystream[j]
            i += n
        return bytes(out)

    def _compute_tag(self, data: bytes, ctr: int) -> bytes:
        """`c()` from `c6.c.c()` — custom CBC-MAC over the full frame,
        returning a 4-byte tag. The B0 block layout is non-standard CCM:
        flag=0x59, length-byte at idx 15.
        """
        length = len(data) - 3
        b0 = bytearray(16)
        b0[0] = 0x59
        b0[1] = (ctr >> 24) & 0xFF
        b0[2] = (ctr >> 16) & 0xFF
        b0[3] = (ctr >> 8) & 0xFF
        b0[4] = ctr & 0xFF
        b0[5:13] = self.token[:8]
        b0[15] = length & 0xFF

        x = _aes_ecb(bytes(b0), bytes(self.aes_key))

        # First absorbed block: data[0..3] padded into 16 bytes, only first 3 used.
        first = bytearray(16)
        first[0:3] = data[0:3]
        x = _aes_ecb(_xor(bytes(first), x), bytes(self.aes_key))

        idx = 3
        remaining = length
        while remaining > 0:
            n = min(16, remaining)
            block = bytearray(16)
            block[0:n] = data[idx : idx + n]
            x = _aes_ecb(_xor(bytes(block), x), bytes(self.aes_key))
            remaining -= n
            idx += n

        # S0 block: [01][counter32][token[0..8]][...] with byte 15 = 0.
        s0 = bytearray(16)
        s0[0] = 0x01
        s0[1] = b0[1]
        s0[2] = b0[2]
        s0[3] = b0[3]
        s0[4] = b0[4]
        s0[5:13] = self.token[:8]
        s0[15] = 0
        s_enc = _aes_ecb(bytes(s0), bytes(self.aes_key))

        return bytes(s_enc[i] ^ x[i] for i in range(4))

    @staticmethod
    def _inverted_sum(input_data: bytes) -> bytes:
        """16-bit inverted-sum CRC over the body, returned little-endian.
        Matches SHU's `j7 += b7` (signed sum).
        """
        sum_signed = 0
        for b in input_data:
            sum_signed += b if b < 128 else b - 256  # signed byte
        sum_signed &= 0xFFFFFFFFFFFFFFFF  # like Long
        inv = (~sum_signed) & 0xFFFF
        return bytes([inv & 0xFF, (inv >> 8) & 0xFF])
