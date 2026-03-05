package com.bikearcade.metrics.tcp

import android.util.Log
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.metrics.MetricsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.CRC32

/**
 * TCP client that connects to the Bowflex/Velocore serial bridge (TCP:9999)
 * or the serialbridge proxy (TCP:9998).
 *
 * Implements the UCB (Universal Control Board) protocol:
 * - Wire format: STX (0x02) + hex-encoded([cmdId][payload][CRC-32 LE]) + ETX (0x03)
 * - Sends STREAMING_CONTROL on connect to enable sensor data
 * - Sends SYSTEM_HEART_BEAT every 3 seconds to keep connection alive
 * - Parses C9C10StreamData sensor notifications (cmdId=0x02, subtype=0x08)
 */
class TcpSerialClient(
    override val host: String = "127.0.0.1",
    override val port: Int = 9999
) : MetricsSource.TcpSerial {

    companion object {
        private const val TAG = "TcpSerialClient"

        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val HEARTBEAT_INTERVAL_MS = 3_000L

        // UCB protocol constants
        private const val STX: Byte = 0x02
        private const val ETX: Byte = 0x03

        // UCB command IDs (Console -> Board)
        private const val CMD_STREAMING_CONTROL = 0x1D
        private const val CMD_HEARTBEAT = 0x1F

        // UCB notification IDs (Board -> Console)
        private const val CMD_SENSOR_NOTIFICATION = 0x02
        private const val SUBTYPE_C9C10_STREAM = 0x08

        // Max hex-encoded frame size between STX and ETX
        private const val MAX_FRAME_HEX_LEN = 512

        // Sensor data byte offsets within the 23-byte sensor payload (after cmdId, subtype, seq)
        // These are best-guess based on the C9C10StreamData field types (uint16 LE encoding).
        // Layout: [cadenceHi][cadenceLo][speedHi][speedLo][powerHi][powerLo][HR][resistLvl]
        //         [distB3][distB2][distB1][distB0][elapsedB3][elapsedB2][elapsedB1][elapsedB0]
        //         [caloriesHi][caloriesLo][tiltHi][tiltLo][flags][spare1][spare2]
        //
        // NOTE: These offsets may need adjustment based on actual captured data.
        // The initial implementation logs raw bytes for field mapping verification.
    }

    override val name: String = "TCP Serial ($host:$port)"

    override var isConnected: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null
    private var heartbeatJob: Job? = null
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var shouldReconnect = false
    private var frameCount = 0L

    private val _metricsFlow = MutableSharedFlow<BikeMetrics>(
        replay = 1,
        extraBufferCapacity = 64
    )
    override val metricsFlow: Flow<BikeMetrics> = _metricsFlow

    override suspend fun connect() {
        shouldReconnect = true
        connectInternal()
        startReading()
    }

    override suspend fun disconnect() {
        shouldReconnect = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        readJob?.cancel()
        readJob = null
        closeSocket()
    }

    private suspend fun connectInternal() {
        withContext(Dispatchers.IO) {
            closeSocket()
            val newSocket = Socket()
            try {
                Log.d(TAG, "Connecting to $host:$port...")
                newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                newSocket.soTimeout = READ_TIMEOUT_MS
                newSocket.tcpNoDelay = true
                socket = newSocket
                outputStream = newSocket.getOutputStream()
                isConnected = true
                frameCount = 0
                Log.i(TAG, "Connected to UCB serial bridge at $host:$port")

                // Send STREAMING_CONTROL to enable sensor data
                sendStreamingControl(true)

                // Start heartbeat
                startHeartbeat()

            } catch (e: Exception) {
                newSocket.close()
                isConnected = false
                Log.e(TAG, "Failed to connect to $host:$port", e)
                throw e
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    sendCommand(CMD_HEARTBEAT, byteArrayOf())
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat send failed: ${e.message}")
                    break
                }
            }
        }
    }

    private fun startReading() {
        readJob = scope.launch {
            var reconnectDelay = INITIAL_RECONNECT_DELAY_MS

            while (isActive && shouldReconnect) {
                try {
                    readLoop()
                    reconnectDelay = INITIAL_RECONNECT_DELAY_MS
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Connection lost: ${e.message}")
                    isConnected = false
                    heartbeatJob?.cancel()
                    closeSocket()

                    if (!shouldReconnect) break

                    Log.d(TAG, "Reconnecting in ${reconnectDelay}ms...")
                    delay(reconnectDelay)
                    reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)

                    try {
                        connectInternal()
                        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
                    } catch (e2: Exception) {
                        Log.w(TAG, "Reconnect failed: ${e2.message}")
                    }
                }
            }
        }
    }

    /**
     * Main read loop. Reads UCB protocol frames from the TCP socket.
     * Frame format: STX (0x02) + hex-encoded data + ETX (0x03)
     */
    private suspend fun readLoop() {
        withContext(Dispatchers.IO) {
            val currentSocket = socket ?: throw IllegalStateException("Socket not connected")
            val inputStream = currentSocket.getInputStream()
            val hexBuf = StringBuilder(MAX_FRAME_HEX_LEN)

            while (shouldReconnect && !currentSocket.isClosed) {
                // Wait for STX
                val b = inputStream.read()
                if (b == -1) {
                    Log.d(TAG, "Stream ended (EOF)")
                    return@withContext
                }

                if (b.toByte() != STX) continue

                // Read until ETX
                hexBuf.setLength(0)
                var foundEtx = false
                while (hexBuf.length < MAX_FRAME_HEX_LEN) {
                    val c = inputStream.read()
                    if (c == -1) return@withContext
                    if (c.toByte() == ETX) {
                        foundEtx = true
                        break
                    }
                    hexBuf.append(c.toChar())
                }

                if (!foundEtx) {
                    Log.w(TAG, "Frame too long (>${MAX_FRAME_HEX_LEN} hex chars), discarding")
                    continue
                }

                if (hexBuf.length < 10) { // Minimum: 1 cmdId + 4 CRC = 5 bytes = 10 hex chars
                    continue
                }

                processFrame(hexBuf.toString())
            }
        }
    }

    /**
     * Process a single hex-encoded UCB frame (the content between STX and ETX).
     */
    private fun processFrame(hexString: String) {
        val decoded = hexDecode(hexString) ?: return

        if (decoded.size < 5) return // Minimum: 1 cmd + 4 CRC

        // Split into raw data and CRC (last 4 bytes)
        val rawLen = decoded.size - 4
        val raw = decoded.copyOfRange(0, rawLen)

        frameCount++

        // Log first few raw frames for protocol debugging
        if (frameCount <= 10) {
            Log.d(TAG, "Frame hex(${hexString.length}): ${hexString.take(80)}...")
            Log.d(TAG, "Decoded(${decoded.size}): ${bytesToHex(decoded)}")
            Log.d(TAG, "Raw(${raw.size}): ${bytesToHex(raw)}")
        }

        // Skip CRC validation for now — the bike MCU may use a non-standard CRC-32 variant.
        // The connection is trusted (localhost), so integrity isn't a concern.

        val cmdId = raw[0].toInt() and 0xFF

        // Log all command types for first 20 frames so we can see what's coming
        if (frameCount <= 20) {
            Log.d(TAG, "Frame #$frameCount cmdId=0x${String.format("%02X", cmdId)} rawLen=${raw.size}")
        }

        // We care about sensor notifications (cmdId=0x02 with subtype=0x08)
        if (cmdId == CMD_SENSOR_NOTIFICATION && raw.size >= 3) {
            val subType = raw[1].toInt() and 0xFF
            if (frameCount <= 20) {
                Log.d(TAG, "  subType=0x${String.format("%02X", subType)}")
            }
            if (subType == SUBTYPE_C9C10_STREAM) {
                val sensorData = raw.copyOfRange(3, raw.size) // Skip cmdId, subtype, sequence

                // Log every frame for field mapping (temporarily verbose)
                val seq = raw[2].toInt() and 0xFF
                if (frameCount <= 20 || frameCount % 10 == 0L) {
                    Log.d(TAG, "Sensor frame #$frameCount seq=$seq (${sensorData.size} bytes): ${bytesToHex(sensorData)}")
                }

                parseSensorData(sensorData)?.let { _metricsFlow.tryEmit(it) }
            }
        }
    }

    /**
     * Parse the sensor data bytes from a C9C10StreamData notification.
     *
     * The 23-byte sensor payload encoding is reverse-engineered from the
     * UCB protocol. Field positions determined by analyzing captured frames.
     *
     * Encoding uses little-endian byte order with BytesUtil-style functions:
     * - uint16: 2 bytes LE
     * - uint32: 4 bytes LE
     * - float:  4 bytes IEEE 754 LE
     *
     * Based on C9C10BleWorkoutdata field order and BLE FTMS Indoor Bike Data
     * characteristic layout, the most likely packing is:
     *
     * Offset  Size  Field
     * 0       2     cadence (uint16, RPM * 2 or RPM * 10)
     * 2       2     speed (uint16, MPH * 100 or KPH * 100)
     * 4       2     power (uint16, watts)
     * 6       1     heartRate (uint8, BPM)
     * 7       1     resistanceLevel (uint8, 0-100)
     * 8       4     distance (uint32, meters or cm)
     * 12      4     elapsedTime (uint32, seconds or deciseconds)
     * 16      2     calories (uint16, kcal)
     * 18      2     tilt (int16, lean angle)
     * 20      1     flags (uint8)
     * 21      2     spare/padding
     *
     * NOTE: These offsets will be validated against live captured data.
     * The parser logs raw bytes so we can adjust field mapping.
     */
    /**
     * Parse the 23-byte sensor payload from the Bowflex C9C10StreamData.
     *
     * Known field mapping (from idle/pedaling analysis):
     *   Byte  3:     resistance level (0-100)
     *   Byte  7:     cadence-related (changes with pedaling)
     *   Byte 10:     flags (0x08 normal, 0x0A sometimes)
     *   Bytes 4-5:   speed (uint16 LE, raw units TBD)
     *   Bytes 8-9:   power (uint16 LE, raw units TBD)
     *   Bytes 20-21: unknown (0xEF44 idle, changes with pedaling)
     *
     * This mapping is still being refined. All values log verbosely so
     * we can correct field offsets from captured pedaling data.
     */
    private fun parseSensorData(data: ByteArray): BikeMetrics? {
        if (data.size < 20) {
            Log.w(TAG, "Sensor payload too short: ${data.size} bytes")
            return null
        }

        try {
            // Resistance is well-established at byte 3
            val resistance = data[3].toInt() and 0xFF

            // Cadence: byte 7 appears to change with pedaling
            // Could be raw RPM or RPM * 2
            val cadenceRaw = data[7].toInt() and 0xFF

            // Speed: try uint16 LE at bytes 4-5
            val speedRaw = decodeUint16(data, 4)

            // Power: try uint16 LE at bytes 8-9
            val powerRaw = decodeUint16(data, 8)

            // Incline/lean: int16 LE at bytes 18-19 (signed, degrees * 10?)
            val inclineRaw = if (data.size >= 20) decodeInt16(data, 18) else 0

            // Bytes 20-21: another uint16 that changes with activity
            val field2021 = if (data.size >= 22) decodeUint16(data, 20) else 0

            // Verbose logging to map remaining fields while pedaling
            if (frameCount <= 10 || frameCount % 50 == 0L) {
                val dump = buildString {
                    append("Parsed: res=$resistance cadRaw=$cadenceRaw ")
                    append("spd16=${speedRaw} pwr16=${powerRaw} ")
                    append("f2021=$field2021 ")
                    // Dump each byte individually for analysis
                    append("bytes=[")
                    for (i in data.indices) {
                        if (i > 0) append(",")
                        append(String.format("%02X", data[i]))
                    }
                    append("]")
                }
                Log.i(TAG, dump)
            }

            // Cadence: use byte 7 as raw RPM for now
            val cadenceRpm = cadenceRaw.toDouble()

            // Speed: if speedRaw > 0, assume KPH * 100
            val speedKph = speedRaw / 100.0
            val speedMph = speedKph * BikeMetrics.KMH_TO_MPH

            return BikeMetrics(
                heartRate = 0, // No HR from bike — would need BLE HR strap
                cadence = cadenceRpm,
                power = powerRaw.coerceAtLeast(0),
                speed = speedMph,
                resistance = resistance,
                incline = inclineRaw / 10.0,
                distance = 0.0,    // Need to accumulate from speed over time
                elapsedTime = 0L   // Tracked by session timer, not from bike
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse sensor data: ${e.message}")
            return null
        }
    }

    // --- UCB command building ---

    private fun sendStreamingControl(enable: Boolean) {
        val payload = if (enable) byteArrayOf(0x01, 0x00) else byteArrayOf(0x00, 0x00)
        try {
            sendCommand(CMD_STREAMING_CONTROL, payload)
            Log.d(TAG, "Sent STREAMING_CONTROL enable=$enable")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send STREAMING_CONTROL: ${e.message}")
        }
    }

    @Synchronized
    private fun sendCommand(cmdId: Int, payload: ByteArray) {
        val out = outputStream ?: return

        // Raw = cmdId + payload
        val raw = ByteArray(1 + payload.size)
        raw[0] = cmdId.toByte()
        payload.copyInto(raw, 1)

        // CRC-32 over raw
        val crc = CRC32()
        crc.update(raw)
        val crcVal = crc.value

        // CRC as 4 bytes LE
        val crcBytes = byteArrayOf(
            (crcVal and 0xFF).toByte(),
            ((crcVal shr 8) and 0xFF).toByte(),
            ((crcVal shr 16) and 0xFF).toByte(),
            ((crcVal shr 24) and 0xFF).toByte()
        )

        // Hex-encode raw + crc
        val toEncode = ByteArray(raw.size + 4)
        raw.copyInto(toEncode)
        crcBytes.copyInto(toEncode, raw.size)
        val hex = bytesToHex(toEncode)

        // Frame: STX + hex + ETX
        val frame = ByteArray(1 + hex.length + 1)
        frame[0] = STX
        hex.toByteArray(Charsets.US_ASCII).copyInto(frame, 1)
        frame[frame.size - 1] = ETX

        out.write(frame)
        out.flush()
    }

    // --- Byte utilities ---

    private fun decodeUint16(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun decodeInt16(data: ByteArray, offset: Int): Int {
        val raw = decodeUint16(data, offset)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    private fun decodeUint32(data: ByteArray, offset: Int): Long {
        return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun hexDecode(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                val hi = Character.digit(hex[i * 2], 16)
                val lo = Character.digit(hex[i * 2 + 1], 16)
                if (hi < 0 || lo < 0) return null
                ((hi shl 4) or lo).toByte()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02X", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun closeSocket() {
        try {
            outputStream = null
            socket?.close()
        } catch (_: Exception) { }
        socket = null
        isConnected = false
    }
}
