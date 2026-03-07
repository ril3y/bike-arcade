package com.bikearcade.metrics.aidl

import android.content.Context
import android.util.Log
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.metrics.MetricsSource
import io.battlewithbytes.ucb.BikeServiceClient
import io.battlewithbytes.ucb.SensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * AIDL-based metrics source -- uses ucblib's [BikeServiceClient] to bind to
 * SerialBridge's IBikeService via AIDL and receive sensor data callbacks.
 *
 * Computes derived metrics (speed, distance, calories) from raw sensor data
 * using JRNY's empirical formulas for VeloCore C9/C10 bikes.
 */
class AidlMetricsSource(
    private val context: Context,
) : MetricsSource.Direct {

    companion object {
        private const val TAG = "AidlMetrics"
    }

    override val name: String = "UCB AIDL"

    override var isConnected: Boolean = false
        private set

    private val client = BikeServiceClient(context)

    private val _metricsFlow = MutableSharedFlow<BikeMetrics>(
        replay = 1,
        extraBufferCapacity = 64
    )
    override val metricsFlow: Flow<BikeMetrics> = _metricsFlow

    // Accumulated ride stats
    private var startTimeMs: Long = 0L
    private var prevTimestampMs: Long = 0L
    private var totalDistanceMiles: Double = 0.0
    private var totalCalories: Double = 0.0

    private val listener = object : BikeServiceClient.ListenerAdapter() {
        override fun onSensorData(data: SensorData) {
            val now = System.currentTimeMillis()
            val power = data.power.toDouble().coerceAtLeast(0.0)
            val powerInt = power.toInt()

            // Speed from power (JRNY empirical curve for VeloCore)
            val speedMph = if (power > 0) {
                (13.26 * power) / (18.1 + power) + (38.88 * power) / (power + 586.6)
            } else 0.0

            // Accumulate distance
            if (prevTimestampMs > 0 && power > 0) {
                val elapsedMs = (now - prevTimestampMs).coerceIn(0, 5000)
                val deltaMiles = (speedMph / 3600000.0) * elapsedMs
                totalDistanceMiles += deltaMiles
            }

            // Accumulate calories (rough: watts * hours * 3.6 kcal/Wh)
            if (prevTimestampMs > 0 && power > 0) {
                val elapsedMs = (now - prevTimestampMs).coerceIn(0, 5000)
                val hours = elapsedMs / 3600000.0
                totalCalories += power * hours * 3.6
            }

            // Initialize start time on first data with pedaling
            if (startTimeMs == 0L && data.rpm > 0) {
                startTimeMs = now
            }

            prevTimestampMs = now

            val elapsedTimeMs = if (startTimeMs > 0) now - startTimeMs else 0L

            val metrics = BikeMetrics(
                cadence = data.rpm.toDouble(),
                power = powerInt,
                resistance = data.resistanceLevel,
                speed = speedMph,
                distance = totalDistanceMiles,
                elapsedTime = elapsedTimeMs,
                calories = totalCalories,
                burnRate = if (power > 0) power * 3.6 else 0.0,
            )
            _metricsFlow.tryEmit(metrics)
            Log.d(TAG, "Sensor: ${powerInt}W ${data.rpm}RPM " +
                    "res=${data.resistanceLevel} ${String.format("%.1f", speedMph)}mph " +
                    "${String.format("%.2f", totalDistanceMiles)}mi")
        }

        override fun onFirmwareStateChanged(state: Int, stateName: String) {
            Log.d(TAG, "Firmware state: $stateName ($state)")
        }

        override fun onConnectionChanged(connected: Boolean, message: String) {
            isConnected = connected
            Log.i(TAG, "Connection: $message (connected=$connected)")
        }

        override fun onServiceConnected() {
            Log.i(TAG, "Service connected, starting workout")
            client.startWorkout()
        }

        override fun onServiceDisconnected() {
            isConnected = false
            Log.w(TAG, "Service disconnected")
        }
    }

    override suspend fun connect() {
        // Reset accumulated stats on new connection
        startTimeMs = 0L
        prevTimestampMs = 0L
        totalDistanceMiles = 0.0
        totalCalories = 0.0

        client.addListener(listener)
        client.bind()
        Log.i(TAG, "Binding to BikeService via AIDL")
    }

    override suspend fun disconnect() {
        client.stopWorkout()
        client.unbind()
        client.removeListener(listener)
        isConnected = false
        Log.i(TAG, "Unbound from BikeService")
    }

    override fun setResistance(level: Int): Boolean = client.setResistance(level)

    /** Re-claim session and restart workout after returning from background. */
    fun reconnect() {
        if (client.isBound) {
            Log.i(TAG, "Reconnecting: re-starting workout")
            client.startWorkout()
        } else {
            Log.i(TAG, "Reconnecting: re-binding to service")
            client.bind()
        }
    }
}
