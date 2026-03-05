package com.bikearcade.metrics.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.metrics.MetricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BLE FTMS (Fitness Machine Service) manager.
 *
 * Connects to a Bluetooth LE fitness machine, subscribes to the Indoor Bike Data
 * characteristic (0x2AD2), and parses metric bytes per the Bluetooth FTMS specification.
 *
 * FTMS Indoor Bike Data characteristic format (per Bluetooth SIG FTMS spec):
 * - Bytes 0-1: Flags (16-bit little-endian bitfield)
 * - Remaining bytes: Optional fields, presence determined by flags
 *
 * Flag bits (0 = present unless noted):
 *   Bit 0: More Data (0 = no more data beyond mandatory fields)
 *   Bit 1: Average Speed present
 *   Bit 2: Instantaneous Cadence present
 *   Bit 3: Average Cadence present
 *   Bit 4: Total Distance present
 *   Bit 5: Resistance Level present
 *   Bit 6: Instantaneous Power present
 *   Bit 7: Average Power present
 *   Bit 8: Expended Energy present
 *   Bit 9: Heart Rate present
 *   Bit 10: Metabolic Equivalent present
 *   Bit 11: Elapsed Time present
 *   Bit 12: Remaining Time present
 */
class FtmsManager(private val context: Context) : MetricsSource.BleFtms {

    companion object {
        private const val TAG = "FtmsManager"

        /** FTMS Service UUID: 0x1826 */
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")

        /** Indoor Bike Data Characteristic UUID: 0x2AD2 */
        val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")

        /** Client Characteristic Configuration Descriptor UUID: 0x2902 */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Connection timeout in milliseconds. */
        private const val CONNECT_TIMEOUT_MS = 15_000L

        /** Scan timeout in milliseconds. */
        private const val SCAN_TIMEOUT_MS = 12_000L

        // FTMS Indoor Bike Data flag bit positions
        private const val FLAG_MORE_DATA = 0x0001
        private const val FLAG_AVG_SPEED = 0x0002
        private const val FLAG_INST_CADENCE = 0x0004
        private const val FLAG_AVG_CADENCE = 0x0008
        private const val FLAG_TOTAL_DISTANCE = 0x0010
        private const val FLAG_RESISTANCE_LEVEL = 0x0020
        private const val FLAG_INST_POWER = 0x0040
        private const val FLAG_AVG_POWER = 0x0080
        private const val FLAG_EXPENDED_ENERGY = 0x0100
        private const val FLAG_HEART_RATE = 0x0200
        private const val FLAG_METABOLIC_EQUIV = 0x0400
        private const val FLAG_ELAPSED_TIME = 0x0800
        private const val FLAG_REMAINING_TIME = 0x1000
    }

    override val name: String = "BLE FTMS"

    override var deviceAddress: String? = null
        private set

    override var isConnected: Boolean = false
        private set

    private val scanner = BleScanner(context)
    private var bluetoothGatt: BluetoothGatt? = null

    private val _metricsFlow = MutableSharedFlow<BikeMetrics>(
        replay = 1,
        extraBufferCapacity = 64
    )
    override val metricsFlow: Flow<BikeMetrics> = _metricsFlow

    /**
     * Scan for FTMS devices and connect to the first found (or the specified address).
     */
    @Suppress("MissingPermission")
    override suspend fun scanAndConnect(targetAddress: String?) {
        val device: BluetoothDevice = if (targetAddress != null) {
            // Connect directly to known address
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter.getRemoteDevice(targetAddress)
        } else {
            // Scan for first FTMS device
            Log.d(TAG, "Scanning for FTMS devices...")
            val ftmsDevice = withTimeout(SCAN_TIMEOUT_MS) {
                scanner.scanForFtmsDevices().first()
            }
            Log.d(TAG, "Found FTMS device: ${ftmsDevice.name} [${ftmsDevice.address}]")
            ftmsDevice.device
        }

        deviceAddress = device.address
        connectToDevice(device)
    }

    /**
     * Connect to the currently targeted device (requires prior scanAndConnect or known address).
     */
    override suspend fun connect() {
        val address = deviceAddress
            ?: throw IllegalStateException("No device address set. Call scanAndConnect first.")
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = bluetoothManager.adapter.getRemoteDevice(address)
        connectToDevice(device)
    }

    @Suppress("MissingPermission")
    private suspend fun connectToDevice(device: BluetoothDevice) {
        withTimeout(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : BluetoothGattCallback() {

                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Log.i(TAG, "Connected to GATT server, discovering services...")
                                isConnected = true
                                gatt.discoverServices()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.i(TAG, "Disconnected from GATT server")
                                isConnected = false
                                gatt.close()
                                bluetoothGatt = null
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        RuntimeException("Disconnected during setup (status=$status)")
                                    )
                                }
                            }
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.e(TAG, "Service discovery failed: status=$status")
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    RuntimeException("Service discovery failed: $status")
                                )
                            }
                            return
                        }

                        val ftmsService = gatt.getService(FTMS_SERVICE_UUID)
                        if (ftmsService == null) {
                            Log.e(TAG, "FTMS service not found on device")
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    RuntimeException("FTMS service (0x1826) not found")
                                )
                            }
                            return
                        }

                        val bikeDataChar = ftmsService.getCharacteristic(INDOOR_BIKE_DATA_UUID)
                        if (bikeDataChar == null) {
                            Log.e(TAG, "Indoor Bike Data characteristic not found")
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    RuntimeException("Indoor Bike Data characteristic (0x2AD2) not found")
                                )
                            }
                            return
                        }

                        // Enable notifications
                        gatt.setCharacteristicNotification(bikeDataChar, true)
                        val descriptor = bikeDataChar.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }

                        Log.i(TAG, "Subscribed to Indoor Bike Data notifications")
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in API 33, but required for API 28")
                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic
                    ) {
                        if (characteristic.uuid == INDOOR_BIKE_DATA_UUID) {
                            val data = characteristic.value
                            if (data != null && data.size >= 2) {
                                val metrics = parseIndoorBikeData(data)
                                _metricsFlow.tryEmit(metrics)
                            }
                        }
                    }
                }

                bluetoothGatt = device.connectGatt(context, false, callback)

                continuation.invokeOnCancellation {
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    isConnected = false
                }
            }
        }
    }

    /**
     * Disconnect from the FTMS device and release GATT resources.
     */
    @Suppress("MissingPermission")
    override suspend fun disconnect() {
        withContext(Dispatchers.Main) {
            bluetoothGatt?.let { gatt ->
                gatt.disconnect()
                gatt.close()
            }
            bluetoothGatt = null
            isConnected = false
            deviceAddress = null
        }
    }

    /**
     * Parse the FTMS Indoor Bike Data characteristic value.
     *
     * Per the Bluetooth FTMS spec, the data format is:
     * - Bytes 0-1: Flags (UINT16, little-endian)
     * - Then optional fields in order, each present only if its flag bit is set.
     *
     * Note on the speed field: Instantaneous Speed is ALWAYS present (it's mandatory
     * per the FTMS spec) unless bit 0 (More Data) indicates otherwise. The speed
     * is UINT16 in units of 0.01 km/h.
     *
     * @param data Raw byte array from the BLE characteristic notification.
     * @return Parsed [BikeMetrics] with values converted to imperial units.
     */
    internal fun parseIndoorBikeData(data: ByteArray): BikeMetrics {
        if (data.size < 2) return BikeMetrics.EMPTY

        var offset = 0
        val flags = readUint16(data, offset)
        offset += 2

        var speedKph = 0.0
        var cadenceRpm = 0.0
        var powerWatts = 0
        var heartRateBpm = 0
        var distanceMeters = 0L
        var elapsedTimeSec = 0

        // Instantaneous Speed (mandatory, UINT16, resolution 0.01 km/h)
        // Present when bit 0 (More Data) is 0
        if (flags and FLAG_MORE_DATA == 0) {
            if (offset + 2 <= data.size) {
                speedKph = readUint16(data, offset) * 0.01
                offset += 2
            }
        }

        // Average Speed (UINT16, resolution 0.01 km/h)
        if (flags and FLAG_AVG_SPEED != 0) {
            if (offset + 2 <= data.size) {
                // Skip average speed, we use instantaneous
                offset += 2
            }
        }

        // Instantaneous Cadence (UINT16, resolution 0.5 rpm)
        if (flags and FLAG_INST_CADENCE != 0) {
            if (offset + 2 <= data.size) {
                cadenceRpm = readUint16(data, offset) * 0.5
                offset += 2
            }
        }

        // Average Cadence (UINT16, resolution 0.5 rpm)
        if (flags and FLAG_AVG_CADENCE != 0) {
            if (offset + 2 <= data.size) {
                // Skip average cadence
                offset += 2
            }
        }

        // Total Distance (UINT24, resolution 1 meter)
        if (flags and FLAG_TOTAL_DISTANCE != 0) {
            if (offset + 3 <= data.size) {
                distanceMeters = readUint24(data, offset)
                offset += 3
            }
        }

        // Resistance Level (SINT16)
        if (flags and FLAG_RESISTANCE_LEVEL != 0) {
            if (offset + 2 <= data.size) {
                // Skip resistance level
                offset += 2
            }
        }

        // Instantaneous Power (SINT16, resolution 1 watt)
        if (flags and FLAG_INST_POWER != 0) {
            if (offset + 2 <= data.size) {
                powerWatts = readSint16(data, offset)
                offset += 2
            }
        }

        // Average Power (SINT16, resolution 1 watt)
        if (flags and FLAG_AVG_POWER != 0) {
            if (offset + 2 <= data.size) {
                // Skip average power
                offset += 2
            }
        }

        // Expended Energy (Total: UINT16 kcal, Per Hour: UINT16 kcal/h, Per Minute: UINT8 kcal/min)
        if (flags and FLAG_EXPENDED_ENERGY != 0) {
            if (offset + 5 <= data.size) {
                // Skip: total energy (2) + energy per hour (2) + energy per minute (1)
                offset += 5
            }
        }

        // Heart Rate (UINT8, resolution 1 bpm)
        if (flags and FLAG_HEART_RATE != 0) {
            if (offset + 1 <= data.size) {
                heartRateBpm = data[offset].toInt() and 0xFF
                offset += 1
            }
        }

        // Metabolic Equivalent (UINT8, resolution 0.1)
        if (flags and FLAG_METABOLIC_EQUIV != 0) {
            if (offset + 1 <= data.size) {
                // Skip MET
                offset += 1
            }
        }

        // Elapsed Time (UINT16, resolution 1 second)
        if (flags and FLAG_ELAPSED_TIME != 0) {
            if (offset + 2 <= data.size) {
                elapsedTimeSec = readUint16(data, offset)
                offset += 2
            }
        }

        // Remaining Time (UINT16, resolution 1 second)
        // Skipped — not needed for our metrics

        // Convert to imperial units
        val speedMph = speedKph * BikeMetrics.KMH_TO_MPH
        val distanceMiles = (distanceMeters / 1000.0) * BikeMetrics.KM_TO_MILES

        return BikeMetrics(
            heartRate = heartRateBpm,
            cadence = cadenceRpm,
            power = powerWatts.coerceAtLeast(0),
            speed = speedMph,
            distance = distanceMiles,
            elapsedTime = elapsedTimeSec.toLong() * 1000L
        )
    }

    /** Read an unsigned 16-bit integer (little-endian) from the byte array. */
    private fun readUint16(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    /** Read a signed 16-bit integer (little-endian) from the byte array. */
    private fun readSint16(data: ByteArray, offset: Int): Int {
        val value = readUint16(data, offset)
        return if (value >= 0x8000) value - 0x10000 else value
    }

    /** Read an unsigned 24-bit integer (little-endian) from the byte array. */
    private fun readUint24(data: ByteArray, offset: Int): Long {
        return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16)
    }
}
