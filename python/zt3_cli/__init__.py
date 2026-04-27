"""zt3_cli — Python CLI for direct BLE access to a Segway-Ninebot ZT3 Pro D.

Implements the same NinebotCrypto + Stage-1/2/3 handshake the Android app
uses, so register reads/writes/sweeps run from a Mac terminal at ~50 ms
iteration time instead of the Android app's ~10 s build cycle.
"""

__version__ = "0.1.0"
