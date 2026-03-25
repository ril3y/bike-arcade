package com.bikearcade.settings

/**
 * Generates workouts of a requested duration from templates.
 * Each template defines a warm-up, a repeatable core pattern, and a cool-down.
 * The core pattern repeats to fill the requested duration.
 */
object WorkoutGenerator {

    /** Available duration options in minutes */
    val DURATION_OPTIONS = listOf(10, 15, 20, 30, 45, 60)

    /**
     * Generate a workout of the specified type scaled to the requested duration.
     * Always includes warm-up and cool-down; the middle section repeats/stretches to fill.
     */
    fun generate(type: WorkoutType, durationMinutes: Int): WorkoutDefinition {
        val totalSeconds = durationMinutes * 60
        return when (type) {
            WorkoutType.FREE_RIDE -> generateFreeRide(totalSeconds, durationMinutes)
            WorkoutType.FAT_BURN -> generateSteadyState(totalSeconds, durationMinutes, "Fat Burn", resistance = 12)
            WorkoutType.ENDURANCE -> generateSteadyState(totalSeconds, durationMinutes, "Endurance", resistance = 10)
            WorkoutType.INTERVALS -> generateIntervals(totalSeconds, durationMinutes)
            WorkoutType.HILL_CLIMB -> generateHillClimb(totalSeconds, durationMinutes)
            WorkoutType.POWER_PYRAMID -> generatePyramid(totalSeconds, durationMinutes)
        }
    }

    // --- Templates ---

    private fun generateFreeRide(totalSec: Int, mins: Int) = WorkoutDefinition(
        id = "free_ride_${mins}m",
        name = "Free Ride",
        description = "$mins min at your own pace.",
        segments = listOf(WorkoutSegment(totalSec, 5, "Free Ride")),
    )

    private fun generateSteadyState(totalSec: Int, mins: Int, name: String, resistance: Int): WorkoutDefinition {
        val warmUp = 180.coerceAtMost(totalSec / 6)       // ~1/6 of total, max 3 min
        val coolDown = 120.coerceAtMost(totalSec / 6)     // ~1/6 of total, max 2 min
        val mainZone = totalSec - warmUp - coolDown

        return WorkoutDefinition(
            id = "${name.lowercase().replace(' ', '_')}_${mins}m",
            name = name,
            description = "$mins min steady-state $name zone.",
            segments = listOf(
                WorkoutSegment(warmUp, 5, "Warm Up"),
                WorkoutSegment(mainZone, resistance, "$name Zone"),
                WorkoutSegment(coolDown, 5, "Cool Down"),
            ),
        )
    }

    private fun generateIntervals(totalSec: Int, mins: Int): WorkoutDefinition {
        val warmUp = 180.coerceAtMost(totalSec / 6)
        val coolDown = 120.coerceAtMost(totalSec / 6)
        val coreTime = totalSec - warmUp - coolDown

        // Each cycle: 60s sprint + 90s recover = 150s
        val cycleSeconds = 150
        val cycles = (coreTime / cycleSeconds).coerceAtLeast(1)
        val remaining = coreTime - (cycles * cycleSeconds)

        return WorkoutDefinition(
            id = "intervals_${mins}m",
            name = "Intervals",
            description = "$mins min — $cycles sprint cycles.",
            segments = buildList {
                add(WorkoutSegment(warmUp, 5, "Warm Up"))
                repeat(cycles) { i ->
                    add(WorkoutSegment(60, 25, "Sprint ${i + 1}"))
                    add(WorkoutSegment(90, 5, "Recover"))
                }
                if (remaining > 0) {
                    add(WorkoutSegment(remaining, 5, "Recovery"))
                }
                add(WorkoutSegment(coolDown, 3, "Cool Down"))
            },
        )
    }

    private fun generateHillClimb(totalSec: Int, mins: Int): WorkoutDefinition {
        val warmUp = 180.coerceAtMost(totalSec / 6)
        val coolDown = 120.coerceAtMost(totalSec / 6)
        val coreTime = totalSec - warmUp - coolDown

        // Each hill cycle: climb (240s) + recovery (120s) = 360s
        // Resistance escalates: 10, 15, 20, 25, repeat
        val cycleSeconds = 360
        val cycles = (coreTime / cycleSeconds).coerceAtLeast(1)
        val resistanceLevels = listOf(10, 15, 20, 25)

        return WorkoutDefinition(
            id = "hill_climb_${mins}m",
            name = "Hill Climb",
            description = "$mins min — $cycles hills to conquer.",
            segments = buildList {
                add(WorkoutSegment(warmUp, 5, "Warm Up"))
                repeat(cycles) { i ->
                    val res = resistanceLevels[i % resistanceLevels.size]
                    add(WorkoutSegment(240, res, "Climb ${i + 1}"))
                    if (i < cycles - 1) {
                        add(WorkoutSegment(120, 8, "Recovery"))
                    }
                }
                // Distribute any remaining time as a final push
                val used = warmUp + coolDown + (cycles * 240) + ((cycles - 1).coerceAtLeast(0) * 120)
                val remaining = totalSec - used
                if (remaining > 30) {
                    add(WorkoutSegment(remaining, 25, "Summit Push"))
                }
                add(WorkoutSegment(coolDown, 5, "Cool Down"))
            },
        )
    }

    private fun generatePyramid(totalSec: Int, mins: Int): WorkoutDefinition {
        val warmUp = 180.coerceAtMost(totalSec / 6)
        val coolDown = 180.coerceAtMost(totalSec / 6)
        val coreTime = totalSec - warmUp - coolDown

        // Pyramid: up (10,15,20,25) + down (20,15,10) = 7 steps
        // Each step = coreTime / (pyramids * 7)
        val stepsPerPyramid = 7
        val pyramids = if (coreTime >= 1200) 2 else 1 // 2 pyramids if >= 20 min core
        val stepDuration = coreTime / (pyramids * stepsPerPyramid)
        val upLevels = listOf(10, 15, 20, 25)
        val downLevels = listOf(20, 15, 10)

        return WorkoutDefinition(
            id = "power_pyramid_${mins}m",
            name = "Power Pyramid",
            description = "$mins min — $pyramids pyramid${if (pyramids > 1) "s" else ""}.",
            segments = buildList {
                add(WorkoutSegment(warmUp, 5, "Warm Up"))
                repeat(pyramids) { p ->
                    for ((i, res) in upLevels.withIndex()) {
                        val dur = if (res == 25) (stepDuration * 1.5).toInt() else stepDuration // longer peak
                        add(WorkoutSegment(dur, res, if (res == 25) "Peak" else "Level ${i + 1}"))
                    }
                    for ((i, res) in downLevels.withIndex()) {
                        add(WorkoutSegment(stepDuration, res, "Level ${downLevels.size - i}"))
                    }
                    if (p < pyramids - 1) {
                        add(WorkoutSegment(60, 5, "Recovery"))
                    }
                }
                add(WorkoutSegment(coolDown, 3, "Cool Down"))
            },
        )
    }
}

enum class WorkoutType(val label: String, val description: String) {
    FREE_RIDE("Free Ride", "Ride at your own pace"),
    FAT_BURN("Fat Burn", "Steady-state fat burning zone"),
    ENDURANCE("Endurance", "Moderate pace, build stamina"),
    INTERVALS("Intervals", "Sprint/recover cycles"),
    HILL_CLIMB("Hill Climb", "Escalating resistance climbs"),
    POWER_PYRAMID("Power Pyramid", "Build up, come down, repeat"),
}
