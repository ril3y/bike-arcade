package com.bikearcade.metrics.lean

import android.util.Log
import io.freewheel.ucb.LeanSensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between ucblib's [LeanSensor] and Kotlin Flows.
 *
 * Reads the VeloCore gsensor at /dev/input/event2 (world-readable) and exposes
 * filtered lean angle as a [StateFlow] for UI and game input.
 */
class LeanSensorBridge {

    companion object {
        private const val TAG = "LeanSensorBridge"
    }

    private val sensor = LeanSensor()

    private val _leanAngle = MutableStateFlow(0f)
    /** Current filtered lean angle in degrees. Positive = right, negative = left. */
    val leanAngle: StateFlow<Float> = _leanAngle.asStateFlow()

    private val _rawAccel = MutableStateFlow(Triple(0, 0, 0))
    /** Raw accelerometer X, Y, Z values. */
    val rawAccel: StateFlow<Triple<Int, Int, Int>> = _rawAccel.asStateFlow()

    /** Whether the sensor is actively producing data. */
    val isActive: Boolean get() = sensor.isActive

    /** Set EMA smoothing factor. 0.05=very smooth, 0.3=responsive. Default: 0.15 */
    fun setSmoothingFactor(alpha: Float) = sensor.setSmoothingFactor(alpha)

    /** Set deadzone in degrees. Lean angles smaller than this report as 0. Default: 2.0 */
    fun setDeadzone(degrees: Float) = sensor.setDeadzone(degrees)

    fun start() {
        // Widen deadzone and increase smoothing — the VeloCore gsensor is very sensitive
        // and small physical movements produce huge angle swings with atan2(x, z).
        sensor.setDeadzone(8.0f)       // ignore lean under 8° (was 2°)
        sensor.setSmoothingFactor(0.06f) // heavy smoothing (was 0.15)
        sensor.start { x, y, z, leanDegrees ->
            _leanAngle.value = leanDegrees
            _rawAccel.value = Triple(x, y, z)
        }
        Log.i(TAG, "Lean sensor started (deadzone=8°, alpha=0.06)")
    }

    fun stop() {
        sensor.stop()
        _leanAngle.value = 0f
        Log.i(TAG, "Lean sensor stopped")
    }
}
