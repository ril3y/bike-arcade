package com.bikearcade.metrics.processing

import com.bikearcade.metrics.BikeMetrics

/**
 * Processes raw bike metrics: applies smoothing and calculates derived/session values.
 *
 * Maintains running averages, totals, and workout session state. Calorie estimation
 * uses MET (Metabolic Equivalent of Task) values derived from power output.
 *
 * Usage:
 * ```
 * val processor = MetricsProcessor()
 * val processed = processor.process(rawMetrics)
 * val stats = processor.sessionStats
 * ```
 */
class MetricsProcessor(
    smoothingAlpha: Double = MetricsSmoother.DEFAULT_ALPHA
) {

    private val smoother = MetricsSmoother(smoothingAlpha)

    /** Running sum of power values for average calculation. */
    private var totalPower: Long = 0L

    /** Running sum of cadence values for average calculation. */
    private var totalCadence: Double = 0.0

    /** Running sum of heart rate values for average calculation. */
    private var totalHeartRate: Long = 0L

    /** Number of samples received. */
    private var sampleCount: Long = 0L

    /** Peak power observed in this session. */
    private var peakPower: Int = 0

    /** Peak heart rate observed in this session. */
    private var peakHeartRate: Int = 0

    /** Peak cadence observed in this session. */
    private var peakCadence: Double = 0.0

    /** Peak speed observed in this session. */
    private var peakSpeed: Double = 0.0

    /** Estimated total calories burned (kcal). */
    private var totalCalories: Double = 0.0

    /** Timestamp of the last processed sample, for calorie time-delta calculation. */
    private var lastTimestamp: Long = 0L

    /** Session start time (millis since epoch). */
    private var sessionStartTime: Long = 0L

    /**
     * Snapshot of cumulative session statistics.
     */
    data class SessionStats(
        val avgPower: Int = 0,
        val avgCadence: Double = 0.0,
        val avgHeartRate: Int = 0,
        val peakPower: Int = 0,
        val peakHeartRate: Int = 0,
        val peakCadence: Double = 0.0,
        val peakSpeed: Double = 0.0,
        val totalCalories: Double = 0.0,
        val sampleCount: Long = 0L,
        val sessionDurationMs: Long = 0L
    )

    /**
     * Current session statistics.
     */
    val sessionStats: SessionStats
        get() {
            if (sampleCount == 0L) return SessionStats()
            val now = System.currentTimeMillis()
            return SessionStats(
                avgPower = (totalPower / sampleCount).toInt(),
                avgCadence = totalCadence / sampleCount,
                avgHeartRate = (totalHeartRate / sampleCount).toInt(),
                peakPower = peakPower,
                peakHeartRate = peakHeartRate,
                peakCadence = peakCadence,
                peakSpeed = peakSpeed,
                totalCalories = totalCalories,
                sampleCount = sampleCount,
                sessionDurationMs = if (sessionStartTime > 0) now - sessionStartTime else 0L
            )
        }

    /**
     * Process a raw metrics sample: apply smoothing, update session stats, estimate calories.
     *
     * @param raw Raw [BikeMetrics] from the data source.
     * @return Smoothed [BikeMetrics].
     */
    fun process(raw: BikeMetrics): BikeMetrics {
        val smoothed = smoother.smooth(raw)

        // Initialize session start
        if (sampleCount == 0L) {
            sessionStartTime = System.currentTimeMillis()
            lastTimestamp = raw.timestamp
        }

        // Update running totals (use raw values for accurate averages)
        sampleCount++
        totalPower += raw.power
        totalCadence += raw.cadence
        totalHeartRate += raw.heartRate

        // Update peaks
        if (raw.power > peakPower) peakPower = raw.power
        if (raw.heartRate > peakHeartRate) peakHeartRate = raw.heartRate
        if (raw.cadence > peakCadence) peakCadence = raw.cadence
        if (raw.speed > peakSpeed) peakSpeed = raw.speed

        // Estimate calories using MET-based approach
        val timeDeltaMs = raw.timestamp - lastTimestamp
        if (timeDeltaMs in 1..5000) {
            val calories = estimateCalories(raw.power, timeDeltaMs)
            totalCalories += calories
        }
        lastTimestamp = raw.timestamp

        return smoothed
    }

    /**
     * Reset all session state. Call when starting a new workout.
     */
    fun resetSession() {
        smoother.reset()
        totalPower = 0L
        totalCadence = 0.0
        totalHeartRate = 0L
        sampleCount = 0L
        peakPower = 0
        peakHeartRate = 0
        peakCadence = 0.0
        peakSpeed = 0.0
        totalCalories = 0.0
        lastTimestamp = 0L
        sessionStartTime = 0L
    }

    /**
     * Estimate calories burned based on power output and time interval.
     *
     * Uses MET estimation from cycling power:
     * - MET ~= (power_watts * 3.5) / (body_weight_kg * 1.163) simplified
     * - For simplicity, we use: kcal/hour ~= power_watts * 3.6 / 0.25 (assuming ~25% efficiency)
     * - More practically: kcal/sec ~= power_watts / 1000 * 4.184 * efficiency_factor
     *
     * Simplified formula: kcal = (power_watts / 1000.0) * (timeDeltaMs / 1000.0) * 4.0
     * This approximates: 1 watt sustained for 1 second ~ 0.004 kcal
     * Which gives ~3.6 kcal/watt/hour at ~25% metabolic efficiency.
     *
     * @param powerWatts Current power output in watts.
     * @param timeDeltaMs Time interval in milliseconds.
     * @return Estimated calories burned in this interval (kcal).
     */
    private fun estimateCalories(powerWatts: Int, timeDeltaMs: Long): Double {
        if (powerWatts <= 0) return 0.0

        // Mechanical work in kilojoules
        val workKj = powerWatts * (timeDeltaMs / 1000.0) / 1000.0

        // Assume ~25% gross metabolic efficiency for cycling
        // Total metabolic energy = mechanical work / efficiency
        // 1 kcal = 4.184 kJ
        val metabolicEnergyKj = workKj / 0.25
        return metabolicEnergyKj / 4.184
    }
}
