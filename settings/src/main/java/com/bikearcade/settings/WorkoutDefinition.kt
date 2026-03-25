package com.bikearcade.settings

/**
 * A segment within a workout — a period of time at a specific resistance.
 */
data class WorkoutSegment(
    val durationSeconds: Int,
    val resistanceLevel: Int,
    val name: String = "",
)

/**
 * A complete workout definition with one or more segments.
 */
data class WorkoutDefinition(
    val id: String,
    val name: String,
    val description: String,
    val segments: List<WorkoutSegment>,
    val isCustom: Boolean = false,
) {
    val totalDurationSeconds: Int get() = segments.sumOf { it.durationSeconds }
    val totalDurationMinutes: Int get() = totalDurationSeconds / 60

    /** Get the segment active at a given elapsed time (seconds). */
    fun segmentAt(elapsedSeconds: Int): WorkoutSegment? {
        var acc = 0
        for (seg in segments) {
            acc += seg.durationSeconds
            if (elapsedSeconds < acc) return seg
        }
        return null
    }

    /** Get the resistance level at a given elapsed time. */
    fun resistanceAt(elapsedSeconds: Int): Int =
        segmentAt(elapsedSeconds)?.resistanceLevel ?: segments.lastOrNull()?.resistanceLevel ?: 1
}

/**
 * Built-in workout presets.
 */
object WorkoutPresets {

    val FREE_RIDE = WorkoutDefinition(
        id = "free_ride",
        name = "Free Ride",
        description = "30 min at your own pace. Low resistance.",
        segments = listOf(
            WorkoutSegment(1800, 5, "Free Ride"),
        )
    )

    val FAT_BURN = WorkoutDefinition(
        id = "fat_burn",
        name = "Fat Burn",
        description = "20 min moderate steady-state for fat burning.",
        segments = listOf(
            WorkoutSegment(180, 3, "Warm Up"),
            WorkoutSegment(900, 12, "Fat Burn Zone"),
            WorkoutSegment(120, 8, "Recovery"),
        )
    )

    val HILL_CLIMB = WorkoutDefinition(
        id = "hill_climb",
        name = "Hill Climb",
        description = "25 min of increasing resistance. Climb the mountain!",
        segments = listOf(
            WorkoutSegment(180, 5, "Warm Up"),
            WorkoutSegment(240, 10, "Base"),
            WorkoutSegment(240, 15, "Climb 1"),
            WorkoutSegment(120, 8, "Recovery"),
            WorkoutSegment(240, 20, "Climb 2"),
            WorkoutSegment(120, 8, "Recovery"),
            WorkoutSegment(240, 25, "Summit Push"),
            WorkoutSegment(120, 5, "Cool Down"),
        )
    )

    val INTERVALS = WorkoutDefinition(
        id = "intervals",
        name = "Intervals",
        description = "20 min high/low alternating for max calorie burn.",
        segments = buildList {
            add(WorkoutSegment(180, 5, "Warm Up"))
            repeat(6) { i ->
                add(WorkoutSegment(60, 25, "Sprint ${i + 1}"))
                add(WorkoutSegment(90, 5, "Recover"))
            }
            add(WorkoutSegment(120, 3, "Cool Down"))
        }
    )

    val ENDURANCE = WorkoutDefinition(
        id = "endurance",
        name = "Endurance",
        description = "45 min moderate pace. Build stamina.",
        segments = listOf(
            WorkoutSegment(300, 5, "Warm Up"),
            WorkoutSegment(2100, 10, "Endurance Zone"),
            WorkoutSegment(300, 5, "Cool Down"),
        )
    )

    val ALL = listOf(FREE_RIDE, FAT_BURN, HILL_CLIMB, INTERVALS, ENDURANCE)
}
