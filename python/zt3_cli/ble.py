"""bleak-based BLE transport for the Nordic UART Service.

Single-device async wrapper:
- Connect by MAC address
- Write to RX char (`6e400002…`) without response
- Receive notifications from TX char (`6e400003…`) into a queue
- Discover scooters by scanning for manufacturer-id `0x434E` ("NC")
"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from typing import AsyncIterator, Optional

from bleak import BleakClient, BleakScanner
from bleak.backends.device import BLEDevice
from bleak.backends.scanner import AdvertisementData


NUS_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
NUS_RX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  # phone → scooter (write)
NUS_TX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  # scooter → phone (notify)

# Manufacturer-id 0x434E ("NC") in the BLE advertisement marks Ninebot
# crypto-variant scooters (ZT3 Pro D, GT3, etc).
NINEBOT_MFG_ID = 0x434E

log = logging.getLogger(__name__)


@dataclass
class DiscoveredScooter:
    address: str
    name: Optional[str]
    rssi: int


async def scan(timeout: float = 5.0) -> list[DiscoveredScooter]:
    """Scan for ZT3-class scooters (manufacturer-id 0x434E)."""
    found: dict[str, DiscoveredScooter] = {}

    def _detected(device: BLEDevice, adv: AdvertisementData) -> None:
        if NINEBOT_MFG_ID in adv.manufacturer_data:
            if device.address not in found:
                found[device.address] = DiscoveredScooter(
                    address=device.address,
                    name=device.name,
                    rssi=adv.rssi,
                )

    async with BleakScanner(detection_callback=_detected):
        await asyncio.sleep(timeout)

    return sorted(found.values(), key=lambda s: -s.rssi)


class BleSession:
    """Async context manager wrapping a single BleakClient.

    Usage:
        async with BleSession(mac) as ble:
            await ble.send(frame_bytes)
            async for notify in ble.notifications():
                ...
    """

    def __init__(self, address: str):
        self.address = address
        self._client: Optional[BleakClient] = None
        self._queue: asyncio.Queue[bytes] = asyncio.Queue(maxsize=64)

    async def __aenter__(self) -> "BleSession":
        self._client = BleakClient(self.address)
        await self._client.connect()
        await self._client.start_notify(NUS_TX, self._on_notify)
        return self

    async def __aexit__(self, exc_type, exc, tb) -> None:
        if self._client is not None:
            try:
                await self._client.stop_notify(NUS_TX)
            except Exception:
                pass
            await self._client.disconnect()
            self._client = None

    def _on_notify(self, _char, data: bytearray) -> None:
        try:
            self._queue.put_nowait(bytes(data))
        except asyncio.QueueFull:
            log.warning("BLE notify queue full — dropping oldest")
            try:
                self._queue.get_nowait()
                self._queue.put_nowait(bytes(data))
            except Exception:
                pass

    async def send(self, frame: bytes) -> None:
        """Write a frame to the RX characteristic (no-response)."""
        if self._client is None:
            raise RuntimeError("not connected")
        await self._client.write_gatt_char(NUS_RX, frame, response=False)

    async def recv(self, timeout: float = 1.0) -> Optional[bytes]:
        """Pop the next received frame, waiting up to `timeout` seconds."""
        try:
            return await asyncio.wait_for(self._queue.get(), timeout=timeout)
        except asyncio.TimeoutError:
            return None

    async def drain(self) -> int:
        """Discard all queued frames (used between phases). Returns count dropped."""
        count = 0
        while not self._queue.empty():
            try:
                self._queue.get_nowait()
                count += 1
            except asyncio.QueueEmpty:
                break
        return count
