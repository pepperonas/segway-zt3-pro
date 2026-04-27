package com.celox.segway.core.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract vehicle. Allows future support of G3 / F3 / M365-classic by
 * supplying alternate implementations without touching the UI layer.
 */
interface Vehicle {
    val id: String
    val displayName: String
    val state: StateFlow<VehicleState>

    suspend fun connect()
    suspend fun disconnect()

    /** High-level command surface. Implementations encode/encrypt as needed. */
    suspend fun execute(command: VehicleCommand): Result<Unit>

    /** Periodic poll for status registers. Implementation may use either polling or push. */
    suspend fun refresh(): Result<Unit>
}

/** Snapshot of the vehicle's runtime state. */
data class VehicleState(
    val isConnected: Boolean = false,
    /**
     * True once the handshake / crypto-pairing is complete and the vehicle is
     * ready to accept register read/write commands. For NinebotCrypto-based
     * vehicles this is set after Stage O or after a successful resume; for
     * plaintext vehicles it follows [isConnected] directly.
     */
    val isReady: Boolean = false,
    val isLocked: Boolean = false,
    val isLightsOn: Boolean = false,
    val isCruiseOn: Boolean = false,
    val mode: RideMode? = null,
    val speedKmh: Float = 0f,
    val batteryPercent: Int = 0,
    val temperatureC: Float = 0f,
    val odometerKm: Float = 0f,
    val tripKm: Float = 0f,
    val firmwareVcu: String = "",
    val firmwareMcu: String = "",
    val firmwareBle: String = "",
    val serialNumber: String = "",
    val regionCode: String = "",
    val errorCode: Int = 0,
    val warnCode: Int = 0,
    /** Estimated remaining range in km, from `VCU_LeftMileage` (reg 0x5F). */
    val rangeRemainingKm: Float = 0f,
    /** Total runtime since manufacture in seconds (`VCU_Runtime`, reg 0x64). */
    val totalRuntimeSeconds: Long = 0L,
    /** Trip ride time in seconds (`VCU_SingleRideTime`, reg 0x6A). */
    val tripDurationSeconds: Long = 0L,
    /** Pack voltage in volts (BMS 0x8C, raw V × 100). */
    val batteryVoltage: Float = 0f,
    /** Pack current in amperes, signed (BMS 0x8D, raw A × 100). Negative = discharge. */
    val batteryCurrentA: Float = 0f,
    /** Battery state-of-health % (BMS 0x8E `BMS_FULL_CAP_PCT`). */
    val batteryHealthPercent: Int = 0,
    /** Lifetime charge cycle count (BMS 0x59). */
    val batteryCycleCount: Int = 0,
    /** Charging state from BMS 0x92: 0=idle, 1=charging, 2=fully charged, ... */
    val chargingState: Int = 0,
    /** Battery pack temperature, °C (BMS 0xF9). */
    val batteryTempC: Float = 0f,
    /** Per-cell voltages in millivolts. Length = number of series cells (typically 12 for ZT3). */
    val cellVoltagesMv: IntArray = intArrayOf(),
    /** Motor controller temperatures (sensor A and B), °C. */
    val motorTempAC: Float = 0f,
    val motorTempBC: Float = 0f,
    /** Last raw register read (offset → bytes), so the diagnostics screen can show arbitrary regs. */
    val lastRegisterRead: Pair<Int, ByteArray>? = null,
    /** Last read black-box (crash-log) entry. Format implementation-defined. */
    val blackBoxRaw: ByteArray? = null,
    /**
     * BMS firmware version (read via VCU 0x19, NOT BMS 0x?? — the VCU mediates this
     * register per SHU's bootstrap zt3.json). uint16-LE → "X.YYY" format.
     */
    val firmwareBms: String = "",
    /**
     * Battery max charge percentage (`charge_threshold`, BMS 0x82, R/W).
     * Range 80–100 in units of percent. Lets the user trade peak range vs
     * cell longevity.
     */
    val chargeThresholdPercent: Int = 0,
)

/** ZT3 has 4 modes shown on the dashboard: Walk, E, D, S. */
enum class RideMode { Walk, Eco, Drive, Sport }

sealed interface VehicleCommand {
    data object Lock : VehicleCommand
    data object Unlock : VehicleCommand
    data class SetMode(val mode: RideMode) : VehicleCommand
    data class SetLights(val on: Boolean) : VehicleCommand
    data class SetCruise(val on: Boolean) : VehicleCommand
    data class SetSpeedLimit(val kmh: Int) : VehicleCommand
    data object Reboot : VehicleCommand
    data class ChangeRegion(val region: String) : VehicleCommand   // "U", "D", "E", ...
    data class WriteSerial(val newSerial: String) : VehicleCommand
    /** Read [length] bytes starting at register [offset] from [dst]. dst defaults to VCU=0x16. */
    data class ReadRegister(val offset: Int, val length: Int, val dst: Byte = 0x16) : VehicleCommand
    /** Read the black-box (crash-log) registers. Convenience over ReadRegister. */
    data object ReadBlackBox : VehicleCommand
    /** Read the firmware version registers (VCU/MCU/BLE). */
    data object ReadFirmware : VehicleCommand
}
