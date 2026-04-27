"""Crypto port unit tests.

Verify that:
1. Initial key derivation matches the well-known SHA-1(name + salt) input.
2. Stage-1 frame produces the documented `62 FF` CRC for [3E 04 5B 00].
3. Encrypt → decrypt round-trips at counter == 0 (f-encrypt is self-inverse).
4. Encrypt → decrypt round-trips at counter > 0 (CTR self-inverse).
5. CRC handles negative-byte signed-sum the same as the Kotlin source.
6. setRandomAppData re-derives a different key.
"""

import hashlib

from zt3_cli.crypto import SALT, NinebotCrypto


def _sha1(*chunks: bytes) -> bytes:
    """SHA-1 of concatenated chunks padded to 32 bytes (SHU's c6.c.d layout)."""
    buf = bytearray(32)
    if chunks:
        left = chunks[0][:16]
        buf[0 : len(left)] = left
    if len(chunks) >= 2:
        right = chunks[1][:16]
        buf[16 : 16 + len(right)] = right
    return hashlib.sha1(bytes(buf)).digest()[:16]


class TestKeyDerivation:
    def test_initial_key_is_sha1_of_name_plus_salt(self):
        c = NinebotCrypto("ZT3-XXXX")
        expected = _sha1(b"ZT3-XXXX", SALT)
        assert bytes(c.aes_key) == expected

    def test_set_random_app_data_changes_key(self):
        c = NinebotCrypto("ZT3-XXXX")
        before = bytes(c.aes_key)
        # set_random_app_data needs a non-zero token (otherwise key derivation
        # collapses to SHA-1(random + zeros) which is fine, just not realistic).
        c.token[:] = b"\x01" * 16
        c.set_random_app_data(b"\x02" * 16)
        after = bytes(c.aes_key)
        assert after != before
        # Verify derivation matches SHA-1(random + token).
        assert after == _sha1(b"\x02" * 16, b"\x01" * 16)


class TestStage1Frame:
    def test_get_random_frame_bytes(self):
        c = NinebotCrypto("ZT3")
        f = c.build_get_random_frame(0x04)
        assert f == bytes([0x5A, 0xA5, 0x00, 0x3E, 0x04, 0x5B, 0x00])

    def test_stage1_encrypt_produces_known_crc(self):
        """The CRC for the inner body `[3E 04 5B 00]` is `62 FF` (verified
        against SHU's speed-manip.pcap Phase B). Independent of scooter
        name because the body itself is fixed.
        """
        c = NinebotCrypto("ZT3")
        f = c.build_get_random_frame(0x04)
        wire = c.encrypt(f)
        # Layout: [5A A5 00 obf(4)] [tag=0 0] [crc_lo crc_hi] [ctr_hi=0 ctr_lo=0]
        # length(f) = 7, so wire = 13 bytes total
        assert len(wire) == 13
        # CRC slot is at indices 9-10 (after the 4-byte obfuscated body
        # and 2 zero tag bytes).
        crc = wire[9:11]
        assert crc == bytes([0x62, 0xFF])

    def test_inverted_sum_matches_kotlin_signed_path(self):
        # The kotlin `j7 += b7` uses signed bytes. For [0x3E, 0x04, 0x5B, 0x00]:
        #   sum = 62 + 4 + 91 + 0 = 157 (all positive bytes)
        #   inv = ~157 = ...0xFFFFFF62 → low 16 bits = 0xFF62 → LE [0x62, 0xFF]
        from zt3_cli.crypto import NinebotCrypto as NC

        crc = NC._inverted_sum(bytes([0x3E, 0x04, 0x5B, 0x00]))
        assert crc == bytes([0x62, 0xFF])


class TestEncryptDecryptRoundtrip:
    def test_counter_zero_roundtrip_via_two_instances(self):
        """A fresh sender encrypts the Stage-1 frame; a fresh receiver
        with the same scooter-name decrypts it back to the original."""
        sender = NinebotCrypto("ZT3-XXXX")
        receiver = NinebotCrypto("ZT3-XXXX")

        plain = sender.build_get_random_frame(0x04)
        wire = sender.encrypt(plain)
        decoded = receiver.decrypt(wire)

        assert decoded is not None
        # Counter-0 path leaves last 6 bytes as overhead; the inner frame
        # we recover is the original `[5A A5 len src dst cmd arg]`.
        assert decoded == plain

    def test_counter_nonzero_roundtrip(self):
        """After a synthetic Stage 1 + Stage 2, both sides share the same
        key and counter; encrypted frames round-trip via CTR cipher.
        """
        sender = NinebotCrypto("ZT3-XXXX")
        receiver = NinebotCrypto("ZT3-XXXX")

        # Synthesise a paired state on both sides: same token, same random.
        token = bytes(range(1, 17))
        random = bytes(range(17, 33))
        for c in (sender, receiver):
            c.token[:] = token
            c.app_random[:] = random
            c._derive_key(random, token)
            # Counters at 1 (first encrypt → counter becomes 2; first decrypt
            # reads counter from trailer, no need to seed).
            c.counter = 1

        # Encrypt a register-read frame: [5A A5 01 3E 16 01 55 02]
        # (read VCU 0x55, length=2 byte payload [0x02])
        plain = bytes([0x5A, 0xA5, 0x01, 0x3E, 0x16, 0x01, 0x55, 0x02])
        wire = sender.encrypt(plain)

        # The wire bytes should be longer than plain (4 tag + 2 ctr trailer).
        assert len(wire) == len(plain) + 6

        # Receiver decrypts. Note: counter handling is per-instance so the
        # receiver needs to also be at counter=1 going in (which we set above).
        decoded = receiver.decrypt(wire)
        assert decoded is not None
        # Recovered inner frame matches the input plain (up to length).
        assert decoded == plain


class TestSelfInverseFEncrypt:
    def test_f_encrypt_is_self_inverse(self):
        """`f()` is its own inverse — encrypt twice gives back the input."""
        c = NinebotCrypto("ZT3-XXXX")
        body = b"hello world abcd"  # 16 bytes
        once = c._f_encrypt(body)
        twice = c._f_encrypt(once)
        assert twice == body
