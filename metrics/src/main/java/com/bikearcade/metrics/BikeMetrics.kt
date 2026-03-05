package com.bikearcade.metrics

/**
 * Data class holding all real-time bike telemetry metrics.
 *
 * @property heartRate Heart rate in beats per minute (bpm).
 * @property cadence Pedal cadence in revolutions per minute (rpm). Resolution 0.5 rpm.
 * @property power Instantaneous power output in watts (W).
 * @property speed Current speed in miles per hour (mph).
 * @property distance Total distance traveled in miles.
 * @property elapsedTime Elapsed workout time in milliseconds.
 * @property timestamp System time when this sample was captured (ms since epoch).
 */
data class BikeMetrics(
    val heartRate: Int = 0,
    val cadence: Double = 0.0,
    val power: Int = 0,
    val speed: Double = 0.0,
    val distance: Double = 0.0,
    val elapsedTime: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /** Empty/default metrics instance with all values zeroed. */
        val EMPTY = BikeMetrics(
            heartRate = 0,
            cadence = 0.0,
            power = 0,
            speed = 0.0,
            distance = 0.0,
            elapsedTime = 0L,
            timestamp = 0L
        )

        /** Conversion factor: 1 km/h = 0.621371 mph */
        const val KMH_TO_MPH = 0.621371

        /** Conversion factor: 1 km = 0.621371 miles */
        const val KM_TO_MILES = 0.621371
    }

    /** Speed in kilometers per hour. */
    val speedKph: Double get() = speed / KMH_TO_MPH

    /** Distance in kilometers. */
    val distanceKm: Double get() = distance / KM_TO_MILES

    /** Whether any meaningful data is present. */
    val hasData: Boolean
        get() = heartRate > 0 || cadence > 0.0 || power > 0 || speed > 0.0
}
