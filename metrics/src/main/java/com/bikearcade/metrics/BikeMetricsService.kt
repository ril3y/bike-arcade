package com.bikearcade.metrics

import android.content.Context
import android.util.Log
import com.bikearcade.metrics.ble.FtmsManager
import com.bikearcade.metrics.processing.MetricsProcessor
import com.bikearcade.metrics.tcp.TcpSerialClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Main entry point for bike metrics telemetry.
 *
 * Manages the connection lifecycle to either a BLE FTMS device or a TCP serial bridge.
 * Exposes a [SharedFlow] of processed (smoothed) [BikeMetrics] for UI consumption.
 *
 * Usage:
 * ```
 * val service = BikeMetricsService(context)
 * service.connect(SourceType.TCP_SERIAL)
 * service.metricsFlow.collect { metrics -> updateUi(metrics) }
 * ```
 */
class BikeMetricsService(private val context: Context) {

    companion object {
        private const val TAG = "BikeMetricsService"
    }

    enum class SourceType {
        BLE_FTMS,
        TCP_SERIAL
    }

    /** Connection state. */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectionJob: Job? = null

    private var activeSource: MetricsSource? = null
    private val processor = MetricsProcessor()

    private val _metricsFlow = MutableSharedFlow<BikeMetrics>(
        replay = 1,
        extraBufferCapacity = 64
    )
    /** Flow of processed bike metrics. Replays the last emitted value to new collectors. */
    val metricsFlow: SharedFlow<BikeMetrics> = _metricsFlow.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>(
        replay = 1,
        extraBufferCapacity = 8
    )
    /** Flow of connection state changes. */
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    /** The currently active source type, or null if disconnected. */
    var currentSourceType: SourceType? = null
        private set

    /**
     * Connect to a bike metrics source.
     *
     * @param sourceType The type of source to connect to.
     * @param bleAddress Optional BLE device address for FTMS. If null, will scan for devices.
     * @param tcpHost Host for TCP serial bridge (default: "127.0.0.1").
     * @param tcpPort Port for TCP serial bridge (default: 9999).
     */
    suspend fun connect(
        sourceType: SourceType,
        bleAddress: String? = null,
        tcpHost: String = "127.0.0.1",
        tcpPort: Int = 9999
    ) {
        // Disconnect existing source if any
        disconnect()

        _connectionState.emit(ConnectionState.CONNECTING)
        currentSourceType = sourceType

        try {
            val source: MetricsSource = when (sourceType) {
                SourceType.BLE_FTMS -> {
                    FtmsManager(context).also { ftms ->
                        ftms.scanAndConnect(bleAddress)
                    }
                }
                SourceType.TCP_SERIAL -> {
                    TcpSerialClient(tcpHost, tcpPort).also { tcp ->
                        tcp.connect()
                    }
                }
            }

            activeSource = source
            processor.resetSession()

            // Collect raw metrics, process them, and emit
            collectionJob = source.metricsFlow
                .onEach { raw ->
                    val processed = processor.process(raw)
                    _metricsFlow.emit(processed)
                }
                .catch { e ->
                    Log.e(TAG, "Metrics collection error", e)
                    _connectionState.emit(ConnectionState.ERROR)
                }
                .launchIn(scope)

            _connectionState.emit(ConnectionState.CONNECTED)
            Log.i(TAG, "Connected to $sourceType source: ${source.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $sourceType", e)
            _connectionState.emit(ConnectionState.ERROR)
            currentSourceType = null
            throw e
        }
    }

    /**
     * Disconnect from the current metrics source and release resources.
     */
    suspend fun disconnect() {
        collectionJob?.cancel()
        collectionJob = null

        activeSource?.let { source ->
            try {
                source.disconnect()
                Log.i(TAG, "Disconnected from ${source.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Error during disconnect", e)
            }
        }
        activeSource = null
        currentSourceType = null
        _connectionState.emit(ConnectionState.DISCONNECTED)
    }

    /** Get the current processed metrics processor for session stats. */
    fun getProcessor(): MetricsProcessor = processor

    /**
     * Destroy this service, cancelling all coroutines and releasing all resources.
     * After calling this, the service instance should not be reused.
     */
    fun destroy() {
        collectionJob?.cancel()
        scope.cancel()
        activeSource = null
    }
}
