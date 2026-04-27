"""Three-stage NinebotCrypto handshake — port of `Zt3ProVehicle.sendHandshake`.

  L: Stage 1 — `getBleRandom` (cmd=0x5B) → token + challenge captured
  M: Stage 2 — fresh `o1(R)` (cmd=0x5C) OR resume via `setRandomAppData`
  O: Stage 3 — `D0(challenge)` (cmd=0x5D) → fully paired

After Stage 1 the wire-key transitions to `SHA-1(name + token)`; after
Stage 2 it settles on `SHA-1(appRandom + token)` which is the session
key.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Optional

from . import frame
from .ble import BleSession
from .crypto import NinebotCrypto


log = logging.getLogger(__name__)


class HandshakeError(RuntimeError):
    pass


async def _wait_for(predicate, timeout_ms: int = 600, poll_ms: int = 20) -> bool:
    """Poll `predicate` (a no-arg callable returning bool) up to timeout."""
    deadline = asyncio.get_event_loop().time() + timeout_ms / 1000.0
    while asyncio.get_event_loop().time() < deadline:
        if predicate():
            return True
        await asyncio.sleep(poll_ms / 1000.0)
    return predicate()


async def _drain_into_crypto(ble: BleSession, crypto: NinebotCrypto, briefly_ms: int = 50) -> None:
    """Pull any queued frames and feed them through crypto.parse so flags update."""
    while True:
        f = await ble.recv(timeout=briefly_ms / 1000.0)
        if f is None:
            return
        frame.parse(crypto, f)


async def _stage1_get_random(ble: BleSession, crypto: NinebotCrypto) -> bool:
    """Send getBleRandom until L flag (token+challenge captured)."""
    s1 = crypto.build_get_random_frame(frame.DST_HANDSHAKE)
    for _ in range(6):
        if crypto.stage_received_token:
            break
        # First TX: counter is 0, encrypt fresh.
        # We pass the bytes but let crypto.encrypt do the work; counter
        # advances inside encrypt.
        wire = crypto.encrypt(bytes(s1))
        await ble.send(wire)

        # Wait briefly for the response and feed it back through decrypt.
        for _ in range(30):  # 30 × 20 ms = 600 ms
            f = await ble.recv(timeout=0.02)
            if f is not None:
                frame.parse(crypto, f)
            if crypto.stage_received_token:
                break
        if crypto.stage_received_token:
            break
        await asyncio.sleep(0.3)
    return crypto.stage_received_token


async def _stage2_fresh(ble: BleSession, crypto: NinebotCrypto) -> bool:
    """Send o1 until M flag (paired-key)."""
    pair_init = crypto.build_pair_init_frame(frame.DST_HANDSHAKE)
    for _ in range(6):
        if crypto.stage_paired_key:
            break
        wire = crypto.encrypt(bytes(pair_init))
        await ble.send(wire)
        for _ in range(30):
            f = await ble.recv(timeout=0.02)
            if f is not None:
                frame.parse(crypto, f)
            if crypto.stage_paired_key:
                break
        if crypto.stage_paired_key:
            break
        await asyncio.sleep(0.3)
    return crypto.stage_paired_key


async def _stage3_challenge_echo(ble: BleSession, crypto: NinebotCrypto) -> bool:
    """Send D0(challenge) until O flag (fully paired)."""
    challenge = crypto.snapshot_challenge()
    for _ in range(4):
        if crypto.stage_fully_paired:
            break
        wire = frame.challenge_response(crypto, frame.DST_HANDSHAKE, challenge)
        await ble.send(wire)
        for _ in range(30):
            f = await ble.recv(timeout=0.02)
            if f is not None:
                frame.parse(crypto, f)
            if crypto.stage_fully_paired:
                break
        if crypto.stage_fully_paired:
            break
        await asyncio.sleep(0.3)
    return crypto.stage_fully_paired


async def perform_handshake(
    ble: BleSession,
    crypto: NinebotCrypto,
    persisted_random: Optional[bytes],
) -> bool:
    """Run the full three-stage handshake. If `persisted_random` is
    provided, take the resume path (skip o1, inject random directly).
    Falls back to fresh-pair if Stage 3 times out on resume.

    Returns True if the cipher reached at least Stage 2 (M flag) — at
    that point the session key is correct and write-register frames are
    accepted by the scooter, so we let it through even if O didn't ack.
    """
    log.info("handshake: stage 1 — get random")
    if not await _stage1_get_random(ble, crypto):
        raise HandshakeError("stage 1 timed out — no token from scooter")

    resumed_from_persisted = False
    if persisted_random is not None and len(persisted_random) == 16 and any(persisted_random):
        log.info("handshake: stage 2 — resume via persisted random")
        crypto.set_random_app_data(persisted_random)
        resumed_from_persisted = True
    else:
        log.info("handshake: stage 2 — fresh o1 (press the power button on your scooter NOW)")
        if not await _stage2_fresh(ble, crypto):
            raise HandshakeError("stage 2 timed out — pair-init not acked")

    log.info("handshake: stage 3 — challenge echo")
    if not await _stage3_challenge_echo(ble, crypto):
        if resumed_from_persisted:
            log.warning("stage 3 failed on resume — falling back to fresh pair")
            crypto.reset_pairing_state()
            if not await _stage2_fresh(ble, crypto):
                raise HandshakeError("stage 2 fallback failed")
            await _stage3_challenge_echo(ble, crypto)
        # If still not fully paired, M is probably enough — log and proceed.
        if not crypto.stage_fully_paired:
            log.warning("stage 3 (O) didn't ack — proceeding with M-level cipher")

    return crypto.stage_paired_key
