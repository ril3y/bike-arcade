package com.bikearcade.metrics

/**
 * Data class holding all real-time bike telemetry metrics.
 *
 * @property heartRate Heart rate in beats per minute (bpm).
 * @property cadence Pedal cadence in revolutions per minute (rpm).
 * @property power Instantaneous power output in watts (W).
 * @property speed Current speed in miles per hour (mph).
 * @property resistance Resistance level (1-100).
 * @property distance Total distance traveled in miles.
 * @property elapsedTime Elapsed workout time in milliseconds.
 * @property calories Total calories burned.
 * @property avgPower Average power in watts.
 * @property avgCadence Average cadence in RPM.
 * @property avgSpeed Average speed in mph.
 * @property burnRate Current burn rate in cal/hr.
 * @property leanAngle Lean angle in degrees (positive = right, negative = left).
 * @property timestamp System time when this sample was captured (ms since epoch).
 */
data class BikeMetrics(
    val heartRate: Int = 0,
    val cadence: Double = 0.0,
    val power: Int = 0,
    val speed: Double = 0.0,
    val resistance: Int = 0,
    val distance: Double = 0.0,
    val elapsedTime: Long = 0L,
    val calories: Double = 0.0,
    val avgPower: Int = 0,
    val avgCadence: Double = 0.0,
    val avgSpeed: Double = 0.0,
    val burnRate: Double = 0.0,
    val leanAngle: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        val EMPTY = BikeMetrics(timestamp = 0L)

        const val KMH_TO_MPH = 0.621371
        const val KM_TO_MILES = 0.621371
    }

    val speedKph: Double get() = speed / KMH_TO_MPH
    val distanceKm: Double get() = distance / KM_TO_MILES

    val hasData: Boolean
        get() = heartRate > 0 || cadence > 0.0 || power > 0 || speed > 0.0
}
