package com.bikearcade.metrics.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * BLE scanner for discovering Fitness Machine Service (FTMS) devices.
 *
 * Scans for BLE peripherals advertising the FTMS service UUID (0x1826).
 * Designed for Android 9 (API 28), which requires BLUETOOTH, BLUETOOTH_ADMIN,
 * and ACCESS_FINE_LOCATION permissions (not the newer Android 12+ BLUETOOTH_SCAN).
 */
class BleScanner(private val context: Context) {

    companion object {
        private const val TAG = "BleScanner"

        /** FTMS (Fitness Machine Service) UUID: 0x1826 */
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")

        /** Default scan duration in milliseconds. */
        const val DEFAULT_SCAN_DURATION_MS = 10_000L
    }

    /** Represents a discovered FTMS device. */
    data class FtmsDevice(
        val device: BluetoothDevice,
        val name: String?,
        val address: String,
        val rssi: Int
    )

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    /**
     * Check if Bluetooth is available and enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Scan for FTMS devices, emitting each discovered device as a Flow.
     * The flow completes when the caller cancels collection.
     *
     * Filters for devices advertising the FTMS service UUID (0x1826).
     */
    @Suppress("MissingPermission")
    fun scanForFtmsDevices(): Flow<FtmsDevice> = callbackFlow {
        val scanner = bleScanner
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            close(IllegalStateException("Bluetooth LE scanner not available"))
            return@callbackFlow
        }

        val seenAddresses = mutableSetOf<String>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val address = device.address

                // Deduplicate by address
                if (address in seenAddresses) return
                seenAddresses.add(address)

                val ftmsDevice = FtmsDevice(
                    device = device,
                    name = device.name,
                    address = address,
                    rssi = result.rssi
                )
                Log.d(TAG, "Found FTMS device: ${ftmsDevice.name ?: "Unknown"} [$address] RSSI=${ftmsDevice.rssi}")
                trySend(ftmsDevice)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
                close(RuntimeException("BLE scan failed: error $errorCode"))
            }
        }

        // Build scan filter for FTMS service UUID
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        Log.d(TAG, "Starting BLE scan for FTMS devices...")
        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)

        awaitClose {
            Log.d(TAG, "Stopping BLE scan")
            scanner.stopScan(scanCallback)
        }
    }

    /**
     * Scan for FTMS devices for a limited duration and return all discovered devices.
     *
     * @param durationMs How long to scan in milliseconds.
     * @return List of discovered FTMS devices, sorted by signal strength (strongest first).
     */
    @Suppress("MissingPermission")
    suspend fun scanForDevices(
        durationMs: Long = DEFAULT_SCAN_DURATION_MS
    ): List<FtmsDevice> = suspendCancellableCoroutine { continuation ->
        val scanner = bleScanner
        if (scanner == null) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        val devices = mutableListOf<FtmsDevice>()
        val seenAddresses = mutableSetOf<String>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val address = device.address
                if (address in seenAddresses) return
                seenAddresses.add(address)

                devices.add(
                    FtmsDevice(
                        device = device,
                        name = device.name,
                        address = address,
                        rssi = result.rssi
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                scanner.stopScan(this)
                continuation.resume(devices.sortedByDescending { it.rssi })
            }
        }

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)

        // Stop scan after duration
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            scanner.stopScan(scanCallback)
            if (continuation.isActive) {
                continuation.resume(devices.sortedByDescending { it.rssi })
            }
        }, durationMs)

        continuation.invokeOnCancellation {
            scanner.stopScan(scanCallback)
        }
    }
}
