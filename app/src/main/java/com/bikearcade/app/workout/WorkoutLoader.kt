package com.bikearcade.app.workout

import android.content.Context
import android.util.Log
import com.bikearcade.settings.WorkoutDefinition
import com.bikearcade.settings.WorkoutSegment
import org.json.JSONObject

/**
 * Loads workout definitions from local JSON files in assets/workouts/.
 */
object WorkoutLoader {

    private const val TAG = "WorkoutLoader"
    private const val WORKOUTS_DIR = "workouts"

    fun loadAll(context: Context): List<WorkoutDefinition> {
        return loadFromAssets(context)
    }

    private fun loadFromAssets(context: Context): List<WorkoutDefinition> {
        val assetManager = context.assets
        val files = try {
            assetManager.list(WORKOUTS_DIR) ?: emptyArray()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot list workout assets: ${e.message}")
            emptyArray()
        }

        return files.filter { it.endsWith(".json") }
            .mapNotNull { filename ->
                try {
                    val json = assetManager.open("$WORKOUTS_DIR/$filename")
                        .bufferedReader().use { it.readText() }
                    parseWorkout(json)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse $filename: ${e.message}")
                    null
                }
            }
            .sortedBy { SORT_ORDER[it.id] ?: 99 }
    }

    private fun parseWorkout(jsonStr: String): WorkoutDefinition {
        val obj = JSONObject(jsonStr)
        val segArr = obj.getJSONArray("segments")
        val segments = (0 until segArr.length()).map { i ->
            val seg = segArr.getJSONObject(i)
            WorkoutSegment(
                durationSeconds = seg.getInt("durationSeconds"),
                resistanceLevel = seg.getInt("resistanceLevel"),
                name = seg.optString("name", ""),
            )
        }
        return WorkoutDefinition(
            id = obj.getString("id"),
            name = obj.getString("name"),
            description = obj.getString("description"),
            segments = segments,
        )
    }

    // Display order for built-in workouts
    private val SORT_ORDER = mapOf(
        "free_ride" to 0,
        "fat_burn" to 1,
        "hill_climb" to 2,
        "intervals" to 3,
        "endurance" to 4,
        "power_pyramid" to 5,
    )
}
