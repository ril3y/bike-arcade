package com.bikearcade.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists user profiles via SharedPreferences.
 * Supports multiple profiles with one "active" profile.
 */
class UserRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bikearcade_users", Context.MODE_PRIVATE)

    private val _activeProfile = MutableStateFlow(loadActiveProfile())
    val activeProfile: StateFlow<UserProfile> = _activeProfile.asStateFlow()

    /** Whether a valid profile has been set up. */
    val hasProfile: Boolean get() = _activeProfile.value.isValid

    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putString("active_name", profile.name)
            .putInt("active_age", profile.age)
            .putFloat("active_weight", profile.weightLbs)
            .putFloat("active_height", profile.heightInches)
            .apply {
                if (profile.bodyFatPercent != null) {
                    putFloat("active_bodyfat", profile.bodyFatPercent)
                } else {
                    remove("active_bodyfat")
                }
                if (profile.targetWeightLbs != null) {
                    putFloat("active_target_weight", profile.targetWeightLbs)
                } else {
                    remove("active_target_weight")
                }
            }
            .apply()
        _activeProfile.value = profile
    }

    fun clearProfile() {
        prefs.edit().clear().apply()
        _activeProfile.value = UserProfile.EMPTY
    }

    private fun loadActiveProfile(): UserProfile {
        val name = prefs.getString("active_name", null) ?: return UserProfile.EMPTY
        return UserProfile(
            name = name,
            age = prefs.getInt("active_age", 30),
            weightLbs = prefs.getFloat("active_weight", 150f),
            heightInches = prefs.getFloat("active_height", 68f),
            bodyFatPercent = if (prefs.contains("active_bodyfat")) prefs.getFloat("active_bodyfat", 0f) else null,
            targetWeightLbs = if (prefs.contains("active_target_weight")) prefs.getFloat("active_target_weight", 0f) else null,
        )
    }
}
