package com.bikearcade.settings

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists user profiles via SharedPreferences.
 * Supports multiple profiles with one "active" profile.
 * Can also sync with VeloLauncher's ContentProvider.
 */
class UserRepository(private val context: Context) {

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

    /**
     * Try to load a user profile from VeloLauncher's ContentProvider.
     * Uses the /profile URI which has displayName, weightLbs, heightInches, age, etc.
     * Returns a valid UserProfile if the launcher has one, null otherwise.
     */
    fun loadFromLauncher(): UserProfile? {
        try {
            val uri = Uri.parse("content://io.freewheel.launcher.provider/profile")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("displayName")) ?: ""
                val weightLbs = cursor.getInt(cursor.getColumnIndexOrThrow("weightLbs"))
                val heightInches = cursor.getInt(cursor.getColumnIndexOrThrow("heightInches"))
                val age = cursor.getInt(cursor.getColumnIndexOrThrow("age"))
                cursor.close()

                // Only return if there's meaningful data
                if (name.isNotBlank() || weightLbs > 0 || age > 0) {
                    val profile = UserProfile(
                        name = name.ifBlank { "Rider" },
                        age = if (age > 0) age else 30,
                        weightLbs = if (weightLbs > 0) weightLbs.toFloat() else 150f,
                        heightInches = if (heightInches > 0) heightInches.toFloat() else 68f,
                    )
                    Log.i(TAG, "Loaded profile from launcher: $name, ${weightLbs}lbs, age=$age")
                    return profile
                }
                return null
            }
            cursor?.close()
        } catch (e: Exception) {
            Log.w(TAG, "VeloLauncher profile unavailable: ${e.message}")
        }
        return null
    }

    /**
     * Write the user profile to VeloLauncher's ContentProvider so it's shared
     * across apps. Fails silently if the launcher is not installed.
     */
    fun writeToLauncher(profile: UserProfile) {
        try {
            val uri = Uri.parse("content://io.freewheel.launcher.provider/profile")
            val values = ContentValues().apply {
                put("displayName", profile.name)
                put("weightLbs", profile.weightLbs.toInt())
                put("heightInches", profile.heightInches.toInt())
                put("age", profile.age)
                put("maxHeartRate", profile.maxHeartRate)
            }
            context.contentResolver.update(uri, values, null, null)
            Log.i(TAG, "Wrote profile to launcher: ${profile.name}, ${profile.weightLbs.toInt()}lbs")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write profile to launcher: ${e.message}")
        }
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

    companion object {
        private const val TAG = "UserRepository"
    }
}
