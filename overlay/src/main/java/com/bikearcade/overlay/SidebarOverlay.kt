package com.bikearcade.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikearcade.media.MediaInfo
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.overlay.components.EffortComplianceState
import com.bikearcade.overlay.components.LiveMetricsPanel
import com.bikearcade.overlay.components.NowPlayingCard
import com.bikearcade.overlay.components.RideStatsBar
import com.bikearcade.overlay.components.SystemControlsRow
import com.bikearcade.overlay.components.EffortComplianceBar
import com.bikearcade.overlay.components.WorkoutGraph
import com.bikearcade.settings.WorkoutSegment
import com.bikearcade.ui.theme.DarkBackground
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.SurfaceBorder
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.TextPrimary
import com.bikearcade.ui.theme.TextSecondary

@Composable
fun SidebarOverlay(
    metrics: BikeMetrics,
    mediaInfo: MediaInfo,
    powerHistory: List<Float>,
    cadenceHistory: List<Float>,
    calories: Int,
    isHudVisible: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleHud: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onExitGame: () -> Unit = {},
    workoutName: String = "",
    workoutSegment: String = "",
    workoutTimeRemaining: Int = 0,
    workoutElapsed: Int = 0,
    workoutTotalSeconds: Int = 0,
    workoutSegments: List<WorkoutSegment> = emptyList(),
    resistanceHistory: List<Int> = emptyList(),
    powerHistoryWorkout: List<Int> = emptyList(),
    targetPowerPerSegment: List<Int> = emptyList(),
    difficultyMultiplier: Float = 1.0f,
    onAdjustDifficulty: (Float) -> Unit = {},
    leanAngle: Float = 0f,
    effortCompliance: EffortComplianceState = EffortComplianceState.EMPTY,
    modifier: Modifier = Modifier,
) {
    val hasWorkout = workoutName.isNotBlank() && workoutSegments.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(DarkBackground),
    ) {
        // Main content column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 0. Workout Status (if active)
            if (hasWorkout) {
                WorkoutStatusBar(
                    workoutName = workoutName,
                    segmentName = workoutSegment,
                    timeRemaining = workoutTimeRemaining,
                    elapsed = workoutElapsed,
                    totalSeconds = workoutTotalSeconds,
                    difficultyMultiplier = difficultyMultiplier,
                    onAdjustDifficulty = onAdjustDifficulty,
                )
                Spacer(Modifier.height(4.dp))
            }

            // 1. Now Playing (with album art)
            NowPlayingCard(
                mediaInfo = mediaInfo,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
            )

            Spacer(Modifier.height(4.dp))

            // 2. Live Metrics
            LiveMetricsPanel(metrics = metrics)

            Spacer(Modifier.height(4.dp))

            // 3. Workout Graph (expected vs actual power)
            if (hasWorkout) {
                WorkoutGraph(
                    segments = workoutSegments,
                    totalSeconds = workoutTotalSeconds,
                    elapsedSeconds = workoutElapsed,
                    actualPower = powerHistoryWorkout,
                    targetPowerPerSegment = targetPowerPerSegment,
                    difficultyMultiplier = difficultyMultiplier,
                )
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.weight(1f))

            // 4. Ride Stats
            RideStatsBar(
                distanceMiles = metrics.distance,
                elapsedTimeMs = metrics.elapsedTime,
                calories = calories,
            )

            Spacer(Modifier.height(4.dp))

            // 5. System Controls
            SystemControlsRow(
                isHudVisible = isHudVisible,
                onToggleHud = onToggleHud,
                onOpenSettings = onOpenSettings,
                onExitGame = onExitGame,
            )
        }

        // Effort compliance bar on far right (only during workout)
        if (hasWorkout) {
            EffortComplianceBar(
                compliance = effortCompliance,
                leanAngle = leanAngle,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
    }
}

/** Find the segment resistance level at a given elapsed time (used by workout graph). */
fun findSegmentResistance(segments: List<WorkoutSegment>, elapsed: Int): Int {
    var acc = 0
    for (seg in segments) {
        acc += seg.durationSeconds
        if (elapsed < acc) return seg.resistanceLevel
    }
    return segments.lastOrNull()?.resistanceLevel ?: 1
}

@Composable
private fun WorkoutStatusBar(
    workoutName: String,
    segmentName: String,
    timeRemaining: Int,
    elapsed: Int,
    totalSeconds: Int,
    difficultyMultiplier: Float = 1.0f,
    onAdjustDifficulty: (Float) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBright, MaterialTheme.shapes.medium)
            .border(1.dp, SurfaceBorder, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = workoutName.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = NeonAccent,
            )
            if (segmentName.isNotBlank()) {
                Text(
                    text = segmentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            // Difficulty adjustment: - [multiplier] +
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\u25BC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonAccent,
                    modifier = Modifier
                        .size(24.dp)
                        .background(SurfaceBorder, CircleShape)
                        .padding(2.dp)
                        .clickable { onAdjustDifficulty(-0.1f) },
                )
                Text(
                    text = "${(difficultyMultiplier * 100).toInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (difficultyMultiplier != 1.0f) NeonAccent else TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    text = "\u25B2",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonAccent,
                    modifier = Modifier
                        .size(24.dp)
                        .background(SurfaceBorder, CircleShape)
                        .padding(2.dp)
                        .clickable { onAdjustDifficulty(0.1f) },
                )
            }
            Text(
                text = formatTime(timeRemaining),
                style = MaterialTheme.typography.labelLarge,
                color = if (timeRemaining < 60) TextPrimary else NeonAccent,
            )
        }

        if (totalSeconds > 0) {
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { (elapsed.toFloat() / totalSeconds).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = NeonAccent,
                trackColor = SurfaceBorder,
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
