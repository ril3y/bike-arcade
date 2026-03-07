package com.bikearcade.metrics.processing

import com.bikearcade.metrics.BikeMetrics

/**
 * Exponential Moving Average (EMA) filter for bike metrics.
 *
 * Smooths noisy sensor data to prevent UI flicker. Each metric field is
 * filtered independently using the formula:
 *
 *   smoothed = alpha * newValue + (1 - alpha) * previousSmoothed
 *
 * A higher alpha (closer to 1.0) makes the filter more responsive but less smooth.
 * A lower alpha (closer to 0.0) makes it smoother but more laggy.
 *
 * @property alpha Smoothing factor in range (0.0, 1.0]. Default 0.3.
 */
class MetricsSmoother(val alpha: Double = DEFAULT_ALPHA) {

    companion object {
        /** Default smoothing factor. Responsive at 13 Hz sensor rate. */
        const val DEFAULT_ALPHA = 0.7

        /** Alpha for very smooth output (more lag). */
        const val ALPHA_SMOOTH = 0.15

        /** Alpha for responsive output (less smoothing). */
        const val ALPHA_RESPONSIVE = 0.5

        /** Alpha for near-raw output (minimal smoothing). */
        const val ALPHA_RAW = 0.8
    }

    init {
        require(alpha > 0.0 && alpha <= 1.0) {
            "Alpha must be in range (0.0, 1.0], got $alpha"
        }
    }

    private var smoothedHeartRate: Double = 0.0
    private var smoothedCadence: Double = 0.0
    private var smoothedPower: Double = 0.0
    private var smoothedSpeed: Double = 0.0
    private var isInitialized = false

    /**
     * Apply EMA smoothing to the given metrics.
     *
     * On the first call, the raw values are used directly (no smoothing).
     * Distance and elapsed time are NOT smoothed — they are cumulative/monotonic values.
     *
     * @param raw The raw (unsmoothed) metrics from the sensor.
     * @return Smoothed [BikeMetrics] with filtered instantaneous values.
     */
    fun smooth(raw: BikeMetrics): BikeMetrics {
        if (!isInitialized) {
            // Seed with first values
            smoothedHeartRate = raw.heartRate.toDouble()
            smoothedCadence = raw.cadence
            smoothedPower = raw.power.toDouble()
            smoothedSpeed = raw.speed
            isInitialized = true

            return raw
        }

        smoothedHeartRate = ema(raw.heartRate.toDouble(), smoothedHeartRate)
        smoothedCadence = ema(raw.cadence, smoothedCadence)
        smoothedPower = ema(raw.power.toDouble(), smoothedPower)
        smoothedSpeed = ema(raw.speed, smoothedSpeed)

        return raw.copy(
            heartRate = smoothedHeartRate.toInt(),
            cadence = roundToHalf(smoothedCadence),
            power = smoothedPower.toInt(),
            speed = roundToTenth(smoothedSpeed)
        )
    }

    /**
     * Reset the smoother state. Call when starting a new workout session.
     */
    fun reset() {
        smoothedHeartRate = 0.0
        smoothedCadence = 0.0
        smoothedPower = 0.0
        smoothedSpeed = 0.0
        isInitialized = false
    }

    /** Apply exponential moving average. */
    private fun ema(newValue: Double, previousSmoothed: Double): Double {
        return alpha * newValue + (1.0 - alpha) * previousSmoothed
    }

    /** Round to nearest 0.5. */
    private fun roundToHalf(value: Double): Double {
        return Math.round(value * 2.0) / 2.0
    }

    /** Round to nearest 0.1. */
    private fun roundToTenth(value: Double): Double {
        return Math.round(value * 10.0) / 10.0
    }
}
