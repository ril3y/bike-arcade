package com.bikearcade.settings

/**
 * User profile for workout tracking and calorie estimation.
 *
 * @property name Display name.
 * @property weightLbs Body weight in pounds.
 * @property heightInches Height in inches.
 * @property bodyFatPercent Optional body fat percentage (0-100).
 * @property targetWeightLbs Optional target weight in pounds.
 */
data class UserProfile(
    val name: String = "",
    val age: Int = 30,
    val weightLbs: Float = 150f,
    val heightInches: Float = 68f,
    val bodyFatPercent: Float? = null,
    val targetWeightLbs: Float? = null,
) {
    val isValid: Boolean get() = name.isNotBlank() && weightLbs > 0f && heightInches > 0f && age > 0

    val weightKg: Float get() = weightLbs * 0.453592f
    val heightCm: Float get() = heightInches * 2.54f

    /** BMI calculation. */
    val bmi: Float get() {
        val heightM = heightCm / 100f
        return if (heightM > 0) weightKg / (heightM * heightM) else 0f
    }

    /** Estimated max heart rate (Tanaka formula — more accurate than 220-age). */
    val maxHeartRate: Int get() = (208 - (0.7f * age)).toInt()

    companion object {
        val EMPTY = UserProfile()
    }
}
