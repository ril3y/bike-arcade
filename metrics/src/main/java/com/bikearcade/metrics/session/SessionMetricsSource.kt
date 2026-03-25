package com.bikearcade.metrics.session

import android.content.Context
import android.util.Log
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.metrics.MetricsSource
import com.bikearcade.metrics.common.SpeedFromPower
import io.freewheel.fit.WorkoutSessionClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Session-based metrics source — wraps [WorkoutSessionClient] to receive
 * real-time sensor data from VeloLauncher's bridge connection.
 *
 * Computes derived metrics (speed, distance, calories) using JRNY
 * empirical formulas via [SpeedFromPower].
 */
class SessionMetricsSource(
    private val context: Context
) : MetricsSource.Session {

    companion object {
        private const val TAG = "SessionMetrics"
    }

    override val name: String = "VeloLauncher Session"

    override var isConnected: Boolean = false
        private set

    private val session = WorkoutSessionClient(context)

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

    // Last known heart rate (arrives via separate callback)
    @Volatile
    private var lastHeartRate: Int = 0

    private val listener = object : WorkoutSessionClient.ListenerAdapter() {
        override fun onSensorData(
            resistance: Int, rpm: Int, tilt: Int, power: Float,
            crankRevCount: Long, crankEventTime: Int
        ) {
            val now = System.currentTimeMillis()
            val powerDouble = power.toDouble().coerceAtLeast(0.0)
            val powerInt = powerDouble.toInt()

            val speedMph = SpeedFromPower.compute(powerDouble)

            // Accumulate distance
            if (prevTimestampMs > 0 && powerDouble > 0) {
                val elapsedMs = (now - prevTimestampMs).coerceIn(0, 5000)
                totalDistanceMiles += (speedMph / 3600000.0) * elapsedMs
            }

            // Accumulate calories (watts * hours * 3.6 kcal/Wh)
            if (prevTimestampMs > 0 && powerDouble > 0) {
                val elapsedMs = (now - prevTimestampMs).coerceIn(0, 5000)
                totalCalories += powerDouble * (elapsedMs / 3600000.0) * 3.6
            }

            // Initialize start time on first pedaling
            if (startTimeMs == 0L && rpm > 0) {
                startTimeMs = now
            }

            prevTimestampMs = now
            val elapsedTimeMs = if (startTimeMs > 0) now - startTimeMs else 0L

            val metrics = BikeMetrics(
                heartRate = lastHeartRate,
                cadence = rpm.toDouble(),
                power = powerInt,
                resistance = resistance,
                speed = speedMph,
                distance = totalDistanceMiles,
                elapsedTime = elapsedTimeMs,
                calories = totalCalories,
                burnRate = if (powerDouble > 0) powerDouble * 3.6 else 0.0,
            )
            _metricsFlow.tryEmit(metrics)
            Log.d(TAG, "Sensor: ${powerInt}W ${rpm}RPM " +
                    "res=$resistance ${String.format("%.1f", speedMph)}mph " +
                    "${String.format("%.2f", totalDistanceMiles)}mi")
        }

        override fun onHeartRate(bpm: Int, deviceName: String?) {
            lastHeartRate = bpm
            Log.d(TAG, "HR: $bpm bpm ($deviceName)")
        }

        override fun onConnectionChanged(connected: Boolean, message: String?) {
            isConnected = connected
            Log.i(TAG, "Connection: $message (connected=$connected)")
        }

        override fun onServiceConnected() {
            isConnected = true
            Log.i(TAG, "Bound to VeloLauncher WorkoutSession service")
        }

        override fun onServiceDisconnected() {
            isConnected = false
            Log.w(TAG, "VeloLauncher WorkoutSession service disconnected")
        }
    }

    override suspend fun connect() {
        startTimeMs = 0L
        prevTimestampMs = 0L
        totalDistanceMiles = 0.0
        totalCalories = 0.0
        lastHeartRate = 0

        session.addListener(listener)
        session.bind()
        Log.i(TAG, "Started WorkoutSessionClient")
    }

    override suspend fun disconnect() {
        session.unbind()
        session.removeListener(listener)
        isConnected = false
        Log.i(TAG, "Stopped WorkoutSessionClient")
    }

    override fun setResistance(level: Int): Boolean = session.setResistance(level)
    override fun requestStart(): Boolean = session.requestStart()
    override fun requestStop(): Boolean = session.requestStop()
}
