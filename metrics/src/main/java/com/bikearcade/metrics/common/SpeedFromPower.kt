package com.bikearcade.metrics.common

/**
 * JRNY empirical speed-from-power curve for VeloCore C9/C10 bikes.
 * Used by SessionMetricsSource.
 */
object SpeedFromPower {
    /** Returns estimated speed in MPH for the given power output in watts. */
    fun compute(watts: Double): Double {
        if (watts <= 0) return 0.0
        return (13.26 * watts) / (18.1 + watts) + (38.88 * watts) / (watts + 586.6)
    }
}
