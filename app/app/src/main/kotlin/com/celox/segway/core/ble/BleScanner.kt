package com.celox.segway.core.ble

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber

/** A Ninebot scooter spotted via BLE advertising. */
data class DiscoveredScooter(
    val address: String,
    val name: String?,
    val rssi: Int,
    val rawAdvertising: ByteArray?,
    val beacon: BeaconParser.BeaconInfo?,
) {
    val isCrypto: Boolean get() = beacon?.isCrypto == true
}

/**
 * Continuously scans for Ninebot/Segway BLE advertisements. Filters in software
 * via [BeaconParser]; we don't restrict to a service UUID because not all firmware
 * versions include the NUS UUID in the ad frame.
 */
class BleScanner(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun scan(): Flow<DiscoveredScooter> = callbackFlow {
        val scanner = BluetoothLeScannerCompat.getScanner()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareBatchingIfSupported(false)
            .build()
        val filters = listOf(ScanFilter.Builder().build())

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handle(result)?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handle(it)?.let { d -> trySend(d) } }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.w("BLE scan failed: $errorCode")
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }

        scanner.startScan(filters, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    private fun handle(result: ScanResult): DiscoveredScooter? {
        val raw = result.scanRecord?.bytes ?: return null
        val beacon = BeaconParser.parse(raw) ?: return null
        // Only surface Ninebot/Segway scooters. Headphones, smart-bulbs and TVs
        // never have a NB/NC manufacturer prefix.
        return DiscoveredScooter(
            address = result.device.address,
            name = result.scanRecord?.deviceName ?: result.device.name,
            rssi = result.rssi,
            rawAdvertising = raw,
            beacon = beacon,
        )
    }
}
