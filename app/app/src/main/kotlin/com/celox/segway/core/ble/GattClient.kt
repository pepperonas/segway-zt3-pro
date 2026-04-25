package com.celox.segway.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** GATT-level connection state. */
enum class GattState { Disconnected, Connecting, ServicesDiscovered, Ready, Error }

/**
 * Thin GATT wrapper around the Nordic UART service exposed by Ninebot scooters.
 *
 * Concerns:
 *   • Connect to a [BluetoothDevice] by MAC
 *   • Discover services, set CCCD on TX, request a higher MTU
 *   • Expose incoming notifications as a [SharedFlow] of byte arrays
 *   • Send frames via write-without-response on RX
 *
 * It is intentionally *frame-agnostic*: encryption / pairing logic lives one
 * layer above (see [EllipticPairing]).
 */
class GattClient(private val context: Context) {

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null

    private val _state = MutableStateFlow(GattState.Disconnected)
    val state: StateFlow<GattState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    private val _mtu = MutableStateFlow(23)
    val mtu: StateFlow<Int> = _mtu.asStateFlow()

    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        disconnect()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = manager.adapter?.getRemoteDevice(macAddress)
            ?: error("Bluetooth not available")
        _state.value = GattState.Connecting
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        rxChar = null
        txChar = null
        _state.value = GattState.Disconnected
    }

    @SuppressLint("MissingPermission")
    fun send(frame: ByteArray): Boolean {
        val ch = rxChar ?: return false
        val g = gatt ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(
                ch, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            ch.value = frame
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.i("GATT connected, requesting service discovery")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.i("GATT disconnected (status=$status)")
                    _state.value = if (status == 0) GattState.Disconnected else GattState.Error
                    g.close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = GattState.Error
                return
            }
            val nus = g.getService(BleUuids.NUS_SERVICE)
            if (nus == null) {
                Timber.w("Nordic UART Service not found on device")
                _state.value = GattState.Error
                return
            }
            rxChar = nus.getCharacteristic(BleUuids.NUS_RX)
            txChar = nus.getCharacteristic(BleUuids.NUS_TX)
            _state.value = GattState.ServicesDiscovered

            // Enable notifications on TX
            txChar?.let { tx ->
                g.setCharacteristicNotification(tx, true)
                tx.getDescriptor(BleUuids.CCCD)?.let { d ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(d)
                    }
                }
            }
            // Bigger MTU for faster pairing
            g.requestMtu(247)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            _mtu.value = mtu
            _state.value = GattState.Ready
            Timber.i("MTU negotiated: $mtu")
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == BleUuids.NUS_TX) _incoming.tryEmit(value)
        }

        @Deprecated("Pre-Tiramisu callback")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { _incoming.tryEmit(it.copyOf()) }
        }
    }
}
