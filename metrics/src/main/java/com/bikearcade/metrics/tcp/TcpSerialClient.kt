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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP client that connects to the Bowflex/Velocore serial bridge on a configurable
 * host and port (default: 127.0.0.1:9999).
 *
 * The serial bridge exposes the bike controller's data stream over TCP. This client
 * reads frames, parses them, and emits [BikeMetrics] via a Flow.
 *
 * Supported frame formats:
 *
 * 1. ASCII CSV: "speed,cadence,power,heartrate,distance,elapsed\n"
 *    Values: speed (km/h * 100), cadence (rpm * 10), power (watts),
 *    heartrate (bpm), distance (meters), elapsed (seconds)
 *
 * 2. Binary frame: [0xF0] [length] [payload...] [checksum]
 *    Payload bytes at fixed offsets contain metric values.
 *
 * Handles automatic reconnection on disconnect with exponential backoff.
 */
class TcpSerialClient(
    override val host: String = "127.0.0.1",
    override val port: Int = 9999
) : MetricsSource.TcpSerial {

    companion object {
        private const val TAG = "TcpSerialClient"

        /** Socket connection timeout in milliseconds. */
        private const val CONNECT_TIMEOUT_MS = 5_000

        /** Socket read timeout in milliseconds. */
        private const val READ_TIMEOUT_MS = 3_000

        /** Initial reconnect delay in milliseconds. */
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L

        /** Maximum reconnect delay in milliseconds. */
        private const val MAX_RECONNECT_DELAY_MS = 30_000L

        /** Binary frame start marker. */
        private const val FRAME_START: Byte = 0xF0.toByte()

        // Binary frame payload offsets for Bowflex/Velocore protocol
        private const val OFFSET_SPEED_HIGH = 0
        private const val OFFSET_SPEED_LOW = 1
        private const val OFFSET_CADENCE_HIGH = 2
        private const val OFFSET_CADENCE_LOW = 3
        private const val OFFSET_POWER_HIGH = 4
        private const val OFFSET_POWER_LOW = 5
        private const val OFFSET_HR = 6
        private const val OFFSET_DISTANCE_HIGH = 7
        private const val OFFSET_DISTANCE_MID = 8
        private const val OFFSET_DISTANCE_LOW = 9
        private const val OFFSET_ELAPSED_HIGH = 10
        private const val OFFSET_ELAPSED_LOW = 11
    }

    override val name: String = "TCP Serial ($host:$port)"

    override var isConnected: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null
    private var socket: Socket? = null
    private var shouldReconnect = false

    private val _metricsFlow = MutableSharedFlow<BikeMetrics>(
        replay = 1,
        extraBufferCapacity = 64
    )
    override val metricsFlow: Flow<BikeMetrics> = _metricsFlow

    /**
     * Connect to the TCP serial bridge and begin reading metrics data.
     * Throws on initial connection failure.
     */
    override suspend fun connect() {
        shouldReconnect = true
        connectInternal()
        startReading()
    }

    /**
     * Disconnect from the TCP serial bridge and stop reading.
     */
    override suspend fun disconnect() {
        shouldReconnect = false
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
                isConnected = true
                Log.i(TAG, "Connected to TCP serial bridge at $host:$port")
            } catch (e: Exception) {
                newSocket.close()
                isConnected = false
                Log.e(TAG, "Failed to connect to $host:$port", e)
                throw e
            }
        }
    }

    private fun startReading() {
        readJob = scope.launch {
            var reconnectDelay = INITIAL_RECONNECT_DELAY_MS

            while (isActive && shouldReconnect) {
                try {
                    readLoop()
                    // readLoop returns normally only if socket closes cleanly
                    reconnectDelay = INITIAL_RECONNECT_DELAY_MS
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Connection lost: ${e.message}")
                    isConnected = false
                    closeSocket()

                    if (!shouldReconnect) break

                    Log.d(TAG, "Reconnecting in ${reconnectDelay}ms...")
                    delay(reconnectDelay)
                    reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)

                    try {
                        connectInternal()
                    } catch (e2: Exception) {
                        Log.w(TAG, "Reconnect failed: ${e2.message}")
                    }
                }
            }
        }
    }

    /**
     * Main read loop. Reads data from the socket and parses frames.
     * Detects whether the data is ASCII or binary and routes accordingly.
     */
    private suspend fun readLoop() {
        withContext(Dispatchers.IO) {
            val currentSocket = socket ?: throw IllegalStateException("Socket not connected")
            val inputStream = currentSocket.getInputStream()

            // Peek at first byte to determine frame format
            val firstByte = inputStream.read()
            if (firstByte == -1) return@withContext

            if (firstByte == (FRAME_START.toInt() and 0xFF)) {
                // Binary frame mode
                readBinaryFrames(inputStream, firstByte.toByte())
            } else {
                // ASCII mode — wrap in BufferedReader
                val reader = BufferedReader(InputStreamReader(inputStream))
                // Process the first line (we already consumed one byte)
                val firstChar = firstByte.toChar()
                readAsciiFrames(reader, firstChar)
            }
        }
    }

    /**
     * Read ASCII CSV frames from the serial bridge.
     * Format: "speed,cadence,power,heartrate,distance,elapsed\n"
     */
    private fun readAsciiFrames(reader: BufferedReader, firstChar: Char) {
        // Read first partial line
        val firstLine = firstChar + (reader.readLine() ?: return)
        parseAsciiFrame(firstLine)?.let { _metricsFlow.tryEmit(it) }

        // Continue reading lines
        while (shouldReconnect) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            parseAsciiFrame(line)?.let { _metricsFlow.tryEmit(it) }
        }
    }

    /**
     * Parse a single ASCII CSV frame.
     *
     * Expected format: "speed,cadence,power,heartrate,distance,elapsed"
     * - speed: km/h * 100 (integer)
     * - cadence: rpm * 10 (integer)
     * - power: watts (integer)
     * - heartrate: bpm (integer)
     * - distance: meters (integer)
     * - elapsed: seconds (integer)
     */
    internal fun parseAsciiFrame(line: String): BikeMetrics? {
        return try {
            val parts = line.trim().split(",")
            if (parts.size < 6) {
                Log.w(TAG, "Incomplete ASCII frame: $line")
                return null
            }

            val speedKph = parts[0].trim().toInt() / 100.0
            val cadenceRpm = parts[1].trim().toInt() / 10.0
            val powerWatts = parts[2].trim().toInt()
            val heartRate = parts[3].trim().toInt()
            val distanceMeters = parts[4].trim().toLong()
            val elapsedSeconds = parts[5].trim().toInt()

            BikeMetrics(
                heartRate = heartRate,
                cadence = cadenceRpm,
                power = powerWatts.coerceAtLeast(0),
                speed = speedKph * BikeMetrics.KMH_TO_MPH,
                distance = (distanceMeters / 1000.0) * BikeMetrics.KM_TO_MILES,
                elapsedTime = elapsedSeconds.toLong() * 1000L
            )
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Failed to parse ASCII frame: $line", e)
            null
        }
    }

    /**
     * Read binary frames from the serial bridge.
     *
     * Frame format: [0xF0] [length] [payload...] [checksum]
     * - Start marker: 0xF0
     * - Length: 1 byte, number of payload bytes
     * - Payload: metric data at fixed offsets
     * - Checksum: XOR of all payload bytes
     */
    private fun readBinaryFrames(inputStream: java.io.InputStream, firstByte: Byte) {
        // We already read 0xF0 as the first byte
        processBinaryStream(inputStream, gotStartMarker = true)
    }

    private fun processBinaryStream(inputStream: java.io.InputStream, gotStartMarker: Boolean) {
        var awaitingStart = !gotStartMarker

        while (shouldReconnect) {
            if (awaitingStart) {
                val b = inputStream.read()
                if (b == -1) return
                if (b != (FRAME_START.toInt() and 0xFF)) continue
            }
            awaitingStart = true

            // Read length byte
            val length = inputStream.read()
            if (length == -1 || length < 1 || length > 64) continue

            // Read payload
            val payload = ByteArray(length)
            var bytesRead = 0
            while (bytesRead < length) {
                val n = inputStream.read(payload, bytesRead, length - bytesRead)
                if (n == -1) return
                bytesRead += n
            }

            // Read checksum
            val expectedChecksum = inputStream.read()
            if (expectedChecksum == -1) return

            // Verify checksum (XOR of all payload bytes)
            var checksum = 0
            for (b in payload) {
                checksum = checksum xor (b.toInt() and 0xFF)
            }

            if (checksum != expectedChecksum) {
                Log.w(TAG, "Binary frame checksum mismatch: expected=$expectedChecksum, got=$checksum")
                continue
            }

            parseBinaryFrame(payload)?.let { _metricsFlow.tryEmit(it) }
        }
    }

    /**
     * Parse a binary frame payload into [BikeMetrics].
     */
    internal fun parseBinaryFrame(payload: ByteArray): BikeMetrics? {
        if (payload.size < 12) {
            Log.w(TAG, "Binary payload too short: ${payload.size} bytes")
            return null
        }

        val speedRaw = ((payload[OFFSET_SPEED_HIGH].toInt() and 0xFF) shl 8) or
                (payload[OFFSET_SPEED_LOW].toInt() and 0xFF)
        val cadenceRaw = ((payload[OFFSET_CADENCE_HIGH].toInt() and 0xFF) shl 8) or
                (payload[OFFSET_CADENCE_LOW].toInt() and 0xFF)
        val powerRaw = ((payload[OFFSET_POWER_HIGH].toInt() and 0xFF) shl 8) or
                (payload[OFFSET_POWER_LOW].toInt() and 0xFF)
        val hrRaw = payload[OFFSET_HR].toInt() and 0xFF
        val distanceRaw = ((payload[OFFSET_DISTANCE_HIGH].toInt() and 0xFF) shl 16) or
                ((payload[OFFSET_DISTANCE_MID].toInt() and 0xFF) shl 8) or
                (payload[OFFSET_DISTANCE_LOW].toInt() and 0xFF)
        val elapsedRaw = ((payload[OFFSET_ELAPSED_HIGH].toInt() and 0xFF) shl 8) or
                (payload[OFFSET_ELAPSED_LOW].toInt() and 0xFF)

        // speedRaw in 0.01 km/h, cadenceRaw in 0.1 rpm
        val speedKph = speedRaw / 100.0
        val cadenceRpm = cadenceRaw / 10.0

        return BikeMetrics(
            heartRate = hrRaw,
            cadence = cadenceRpm,
            power = powerRaw.coerceAtLeast(0),
            speed = speedKph * BikeMetrics.KMH_TO_MPH,
            distance = (distanceRaw / 1000.0) * BikeMetrics.KM_TO_MILES,
            elapsedTime = elapsedRaw.toLong() * 1000L
        )
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        socket = null
        isConnected = false
    }
}
