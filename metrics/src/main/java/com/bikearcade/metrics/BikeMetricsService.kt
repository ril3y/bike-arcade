package com.bikearcade.metrics

import android.content.Context
import android.util.Log
import com.bikearcade.metrics.ble.FtmsManager
import com.bikearcade.metrics.lean.LeanSensorBridge
import com.bikearcade.metrics.aidl.AidlMetricsSource
import com.bikearcade.metrics.direct.DirectMetricsSource
import com.bikearcade.metrics.logcat.LogcatMetricsSource
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Main entry point for bike metrics telemetry.
 *
 * Supports three data sources:
 * - **TCP_SERIAL**: Direct connection to UCB serial bridge (raw sensor data at high frequency)
 * - **LOGCAT**: Non-invasive, reads JRNY's workout data from logcat (1 Hz, richer data)
 * - **BLE_FTMS**: Bluetooth FTMS profile for standard fitness machines
 *
 * Also manages the [LeanSensorBridge] for VeloCore lean angle data.
 */
class BikeMetricsService(
    private val context: Context,
    parentScope: CoroutineScope? = null
) {

    companion object {
        private const val TAG = "BikeMetricsService"
    }

    enum class SourceType {
        BLE_FTMS,
        TCP_SERIAL,
        LOGCAT,
        DIRECT,
        AIDL
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val scope = parentScope?.let {
        CoroutineScope(it.coroutineContext + SupervisorJob(it.coroutineContext[Job]))
    } ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectionJob: Job? = null

    private var activeSource: MetricsSource? = null
    private val processor = MetricsProcessor()

    /** Lean sensor bridge — manages the VeloCore gsensor independently of the metrics source. */
    val leanSensor = LeanSensorBridge()

    private val _metricsFlow = MutableSharedFlow<BikeMetrics>(
        replay = 1,
        extraBufferCapacity = 64
    )
    val metricsFlow: SharedFlow<BikeMetrics> = _metricsFlow.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>(
        replay = 1,
        extraBufferCapacity = 8
    )
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    var currentSourceType: SourceType? = null
        private set

    /**
     * Connect to a bike metrics source.
     *
     * @param sourceType The type of source to connect to.
     * @param bleAddress Optional BLE device address for FTMS.
     * @param tcpHost Host for TCP serial bridge (default: "127.0.0.1").
     * @param tcpPort Port for TCP serial bridge (default: 9999).
     */
    suspend fun connect(
        sourceType: SourceType,
        bleAddress: String? = null,
        tcpHost: String = "127.0.0.1",
        tcpPort: Int = 9999,
    ) {
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
                    TcpSerialClient(tcpHost, tcpPort, parentScope = scope).also { tcp ->
                        tcp.connect()
                    }
                }
                SourceType.LOGCAT -> {
                    LogcatMetricsSource(context).also { logcat ->
                        logcat.connect()
                    }
                }
                SourceType.DIRECT -> {
                    DirectMetricsSource(tcpHost, tcpPort, context).also { direct ->
                        direct.connect()
                    }
                }
                SourceType.AIDL -> {
                    AidlMetricsSource(context).also { aidl ->
                        aidl.connect()
                    }
                }
            }

            activeSource = source
            processor.resetSession()

            collectionJob = source.metricsFlow
                .conflate()
                .onEach { raw ->
                    // Merge lean angle into metrics
                    val withLean = raw.copy(leanAngle = leanSensor.leanAngle.value)
                    val processed = processor.process(withLean)
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

    fun getProcessor(): MetricsProcessor = processor

    /** Returns the active direct source if connected, for resistance control. */
    fun getDirectSource(): MetricsSource.Direct? = activeSource as? MetricsSource.Direct

    /** Re-establish AIDL connection after app returns from background. */
    fun reconnect() {
        (activeSource as? AidlMetricsSource)?.reconnect()
    }

    fun destroy() {
        collectionJob?.cancel()
        collectionJob = null
        activeSource?.let { source ->
            try { kotlinx.coroutines.runBlocking { source.disconnect() } }
            catch (e: Exception) { Log.w(TAG, "Error during destroy disconnect", e) }
        }
        activeSource = null
        currentSourceType = null
        leanSensor.stop()
        scope.cancel()
    }
}
