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
    val isLocked: Boolean = false,
    val isLightsOn: Boolean = false,
    val isCruiseOn: Boolean = false,
    val mode: RideMode = RideMode.Drive,
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
    /** Last read black-box (crash-log) entry. Format implementation-defined. */
    val blackBoxRaw: ByteArray? = null,
    /** Last raw register read (offset → bytes), so the diagnostics screen can show arbitrary regs. */
    val lastRegisterRead: Pair<Int, ByteArray>? = null,
)

enum class RideMode { Eco, Drive, Sport }

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
    /** Read [length] bytes starting at register [offset]. Result lands in [VehicleState]. */
    data class ReadRegister(val offset: Int, val length: Int) : VehicleCommand
    /** Read the black-box (crash-log) registers. Convenience over ReadRegister. */
    data object ReadBlackBox : VehicleCommand
    /** Read the firmware version registers (VCU/MCU/BLE). */
    data object ReadFirmware : VehicleCommand
}
