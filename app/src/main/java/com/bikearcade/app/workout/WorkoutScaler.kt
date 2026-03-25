package com.bikearcade.app.workout

import com.bikearcade.settings.UserProfile
import com.bikearcade.settings.WorkoutDefinition
import com.bikearcade.settings.WorkoutSegment

/**
 * Scales workout resistance levels based on user profile and selected intensity.
 *
 * Uses exercise science principles:
 * - Tanaka max HR: 208 - 0.7 × age (more accurate than 220-age)
 * - Karvonen method for HR zone targeting
 * - Body composition adjustment: lower BF% = better power-to-weight
 * - BMI-based fitness estimation when BF% unavailable
 *
 * VeloCore resistance range: 1–25
 */
object WorkoutScaler {

    /**
     * Intensity level chosen by user before starting a workout.
     * Each maps to a percentage of the user's estimated capacity.
     */
    enum class Intensity(
        val label: String,
        val subtitle: String,
        val hrZone: String,
        val multiplier: Float,  // base resistance multiplier
    ) {
        RECOVERY(
            label = "Recovery",
            subtitle = "Easy pace, active recovery day",
            hrZone = "Zone 1–2 (50-65% MHR)",
            multiplier = 0.55f,
        ),
        LIGHT(
            label = "Light",
            subtitle = "Comfortable effort, can hold a conversation",
            hrZone = "Zone 2 (60-70% MHR)",
            multiplier = 0.70f,
        ),
        MODERATE(
            label = "Moderate",
            subtitle = "Steady effort, breathing harder",
            hrZone = "Zone 3 (70-80% MHR)",
            multiplier = 0.85f,
        ),
        VIGOROUS(
            label = "Vigorous",
            subtitle = "Challenging, short phrases only",
            hrZone = "Zone 4 (80-90% MHR)",
            multiplier = 1.00f,
        ),
        MAX(
            label = "All Out",
            subtitle = "Maximum effort, unsustainable pace",
            hrZone = "Zone 5 (90-100% MHR)",
            multiplier = 1.20f,
        );
    }

    /**
     * Scale a workout definition based on user profile and selected intensity.
     * Returns a new WorkoutDefinition with adjusted resistance levels.
     */
    fun scale(
        workout: WorkoutDefinition,
        profile: UserProfile,
        intensity: Intensity,
    ): WorkoutDefinition {
        val fitnessMultiplier = estimateFitnessMultiplier(profile)
        val combinedMultiplier = intensity.multiplier * fitnessMultiplier

        val scaledSegments = workout.segments.map { segment ->
            val scaledResistance = (segment.resistanceLevel * combinedMultiplier)
                .toInt()
                .coerceIn(1, 25) // VeloCore max resistance
            segment.copy(resistanceLevel = scaledResistance)
        }

        return workout.copy(segments = scaledSegments)
    }

    /**
     * Estimate target HR for the given intensity level.
     * Uses Karvonen formula with estimated resting HR.
     */
    fun targetHeartRate(profile: UserProfile, intensity: Intensity): IntRange {
        val maxHr = profile.maxHeartRate
        val restingHr = estimateRestingHr(profile)

        val (loPercent, hiPercent) = when (intensity) {
            Intensity.RECOVERY -> 0.50f to 0.65f
            Intensity.LIGHT -> 0.60f to 0.70f
            Intensity.MODERATE -> 0.70f to 0.80f
            Intensity.VIGOROUS -> 0.80f to 0.90f
            Intensity.MAX -> 0.90f to 1.00f
        }

        val lo = (restingHr + loPercent * (maxHr - restingHr)).toInt()
        val hi = (restingHr + hiPercent * (maxHr - restingHr)).toInt()
        return lo..hi
    }

    /**
     * Estimate target power range (watts) for the given intensity.
     */
    fun targetPower(profile: UserProfile, intensity: Intensity): IntRange {
        val ftp = estimateFtp(profile) // watts
        val (loPercent, hiPercent) = when (intensity) {
            Intensity.RECOVERY -> 0.30f to 0.50f
            Intensity.LIGHT -> 0.50f to 0.65f
            Intensity.MODERATE -> 0.65f to 0.85f
            Intensity.VIGOROUS -> 0.85f to 1.05f
            Intensity.MAX -> 1.05f to 1.30f
        }
        return (ftp * loPercent).toInt()..(ftp * hiPercent).toInt()
    }

    /**
     * Suggest the best intensity for a given workout type and user profile.
     */
    fun suggestedIntensity(workout: WorkoutDefinition): Intensity {
        return when (workout.id) {
            "free_ride" -> Intensity.LIGHT
            "fat_burn" -> Intensity.MODERATE
            "hill_climb" -> Intensity.VIGOROUS
            "intervals" -> Intensity.VIGOROUS
            "endurance" -> Intensity.MODERATE
            "power_pyramid" -> Intensity.VIGOROUS
            else -> Intensity.MODERATE
        }
    }

    // --- Private estimation methods ---

    /**
     * Fitness multiplier based on body composition.
     * Higher fitness = can handle higher resistance for the same perceived effort.
     *
     * Range: 0.7 (deconditioned) to 1.3 (very fit)
     */
    private fun estimateFitnessMultiplier(profile: UserProfile): Float {
        val bf = profile.bodyFatPercent
        return if (bf != null) {
            // Body fat based: lower BF% = more muscle = higher capacity
            when {
                bf < 12f -> 1.25f  // Athletic
                bf < 18f -> 1.10f  // Fit
                bf < 25f -> 1.00f  // Average
                bf < 32f -> 0.90f  // Above average
                else -> 0.80f      // Deconditioned
            }
        } else {
            // BMI fallback (less accurate but usable)
            val bmi = profile.bmi
            when {
                bmi < 20f -> 1.05f
                bmi < 25f -> 1.00f
                bmi < 30f -> 0.90f
                bmi < 35f -> 0.80f
                else -> 0.75f
            }
        }
    }

    /**
     * Estimate resting heart rate from body composition.
     * Fitter individuals have lower resting HR.
     */
    private fun estimateRestingHr(profile: UserProfile): Int {
        val bf = profile.bodyFatPercent
        return if (bf != null) {
            when {
                bf < 12f -> 55
                bf < 18f -> 60
                bf < 25f -> 65
                bf < 32f -> 70
                else -> 75
            }
        } else {
            when {
                profile.bmi < 25f -> 65
                profile.bmi < 30f -> 70
                else -> 75
            }
        }
    }

    /**
     * Compute the target power range for a given workout segment based on user profile.
     * Maps resistance 1-25 to a fraction of FTP with ±15% tolerance band.
     */
    fun segmentTargetPower(profile: UserProfile, resistanceLevel: Int): IntRange {
        val ftp = estimateFtp(profile)
        val effortFraction = 0.10f + (resistanceLevel - 1) * (0.90f / 24f)
        val centerPower = ftp * effortFraction
        val lo = (centerPower * 0.85f).toInt()
        val hi = (centerPower * 1.15f).toInt()
        return lo..hi
    }

    /**
     * Estimate Functional Threshold Power (FTP) in watts.
     * Based on weight and fitness level.
     *
     * Reference: untrained ~1.5 W/kg, recreational ~2.5 W/kg, trained ~3.5 W/kg
     */
    fun estimateFtp(profile: UserProfile): Float {
        val wpkg = estimateFitnessMultiplier(profile) * 2.0f // base ~2 W/kg scaled by fitness
        return wpkg * profile.weightKg
    }
}
