package com.celox.segway.core.ble

import java.util.UUID

/**
 * GATT UUIDs for modern Ninebot scooters. Values reverse-engineered from the
 * SHU decompile (`sh.cfw.utility.services.g`) and confirmed on G30/F-series
 * devices in the wild — ZT3 Pro is expected to follow the same scheme.
 */
object BleUuids {
    /** Nordic UART Service – the host service. */
    val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    /** Phone → Scooter (write without response). */
    val NUS_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    /** Scooter → Phone (notify). */
    val NUS_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    /** Standard Client-Characteristic-Configuration descriptor. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
