"""Frame codec tests — verify the inner-frame layout matches Kotlin."""

from zt3_cli import frame
from zt3_cli.crypto import NinebotCrypto


class TestReadRegister:
    def test_read_register_inner_layout_via_decrypt(self):
        sender = NinebotCrypto("ZT3-XXXX")
        receiver = NinebotCrypto("ZT3-XXXX")

        # Synthesise paired state.
        token = bytes(range(1, 17))
        random = bytes(range(17, 33))
        for c in (sender, receiver):
            c.token[:] = token
            c.app_random[:] = random
            c._derive_key(random, token)
            c.counter = 1

        wire = frame.read_register(sender, dst=frame.DST_VCU, register=0x55, length=2)
        decoded = frame.parse(receiver, wire)

        assert decoded is not None
        assert decoded.src == frame.SRC_PHONE
        assert decoded.dst == frame.DST_VCU
        assert decoded.cmd == frame.CMD_READ
        assert decoded.arg == 0x55
        assert decoded.payload == bytes([0x02])


class TestChallengeResponse:
    def test_challenge_response_carries_full_14_byte_challenge(self):
        sender = NinebotCrypto("ZT3-XXXX")
        receiver = NinebotCrypto("ZT3-XXXX")
        token = bytes(range(1, 17))
        random = bytes(range(17, 33))
        for c in (sender, receiver):
            c.token[:] = token
            c.app_random[:] = random
            c._derive_key(random, token)
            c.counter = 1

        challenge = bytes(range(0x80, 0x80 + 14))
        wire = frame.challenge_response(sender, dst=frame.DST_HANDSHAKE, challenge=challenge)
        decoded = frame.parse(receiver, wire)

        assert decoded is not None
        assert decoded.cmd == frame.CMD_HANDSHAKE_CHALLENGE
        assert decoded.arg == 0x00
        assert decoded.payload == challenge
