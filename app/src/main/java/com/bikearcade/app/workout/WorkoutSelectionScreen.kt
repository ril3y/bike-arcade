package com.bikearcade.app.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bikearcade.app.R
import com.bikearcade.settings.UserProfile
import com.bikearcade.settings.WorkoutDefinition
import com.bikearcade.ui.theme.CadenceBlue
import com.bikearcade.ui.theme.HeartRateRed
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.PowerGreen
import com.bikearcade.ui.theme.ResistanceYellow
import com.bikearcade.ui.theme.SpeedOrange
import com.bikearcade.ui.theme.SurfaceBorder
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.SurfaceDark
import com.bikearcade.ui.theme.TextPrimary
import com.bikearcade.ui.theme.TextSecondary

@Composable
fun WorkoutSelectionScreen(
    userProfile: UserProfile,
    workouts: List<WorkoutDefinition>,
    onSelectWorkout: (WorkoutDefinition) -> Unit,
    onChangeUser: () -> Unit,
    onExitApp: () -> Unit = {},
    onManageGames: () -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .paint(
                painter = painterResource(R.drawable.gamewallpaper),
                contentScale = ContentScale.Crop,
            )
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        // Header row with user info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "SELECT WORKOUT",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonAccent,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose a workout, then pick your game",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onManageGames) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = "Game Library",
                        tint = CadenceBlue,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "GAMES",
                        color = CadenceBlue,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SETTINGS",
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onChangeUser) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = NeonAccent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = userProfile.name,
                        color = NeonAccent,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onExitApp) {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = "Exit App",
                        tint = HeartRateRed,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "EXIT",
                        color = HeartRateRed,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Workout cards grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(workouts) { workout ->
                WorkoutCard(
                    workout = workout,
                    icon = iconForWorkout(workout.id),
                    accentColor = colorForWorkout(workout.id),
                    onClick = { onSelectWorkout(workout) },
                )
            }
        }
    }
    }
}

@Composable
private fun WorkoutCard(
    workout: WorkoutDefinition,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBright, MaterialTheme.shapes.large)
            .border(1.dp, SurfaceBorder, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = workout.name,
            tint = accentColor,
            modifier = Modifier.size(48.dp),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = workout.name,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${workout.totalDurationMinutes} min",
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
        )

        Spacer(Modifier.height(8.dp))

        // Resistance profile graph
        ResistanceProfileGraph(
            workout = workout,
            accentColor = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = workout.description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Draws a step-based resistance profile graph showing the workout shape.
 * Each segment is a horizontal bar at its resistance level, filled with gradient.
 */
@Composable
private fun ResistanceProfileGraph(
    workout: WorkoutDefinition,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val segments = workout.segments
    val totalSeconds = workout.totalDurationSeconds
    val maxRes = segments.maxOfOrNull { it.resistanceLevel } ?: 1

    Canvas(modifier = modifier) {
        if (segments.isEmpty() || totalSeconds <= 0) return@Canvas

        val w = size.width
        val h = size.height
        val padding = 2f

        // Build the profile path as a filled area chart
        val path = Path()
        var xOffset = 0f
        path.moveTo(0f, h) // bottom-left

        for (seg in segments) {
            val segWidth = (seg.durationSeconds.toFloat() / totalSeconds) * w
            val barHeight = ((seg.resistanceLevel.toFloat() / maxRes) * (h - padding * 2))
            val y = h - padding - barHeight

            path.lineTo(xOffset, y)
            path.lineTo(xOffset + segWidth, y)
            xOffset += segWidth
        }

        path.lineTo(w, h) // bottom-right
        path.close()

        // Fill with gradient
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.6f),
                    accentColor.copy(alpha = 0.1f),
                ),
            ),
            style = Fill,
        )

        // Draw the top edge line
        var lineX = 0f
        for ((i, seg) in segments.withIndex()) {
            val segWidth = (seg.durationSeconds.toFloat() / totalSeconds) * w
            val barHeight = ((seg.resistanceLevel.toFloat() / maxRes) * (h - padding * 2))
            val y = h - padding - barHeight

            if (i == 0) {
                drawLine(accentColor, Offset(lineX, y), Offset(lineX + segWidth, y), strokeWidth = 2f)
            } else {
                // Vertical step
                val prevSeg = segments[i - 1]
                val prevBarH = ((prevSeg.resistanceLevel.toFloat() / maxRes) * (h - padding * 2))
                val prevY = h - padding - prevBarH
                drawLine(accentColor.copy(alpha = 0.4f), Offset(lineX, prevY), Offset(lineX, y), strokeWidth = 1f)
                // Horizontal bar
                drawLine(accentColor, Offset(lineX, y), Offset(lineX + segWidth, y), strokeWidth = 2f)
            }
            lineX += segWidth
        }
    }
}

private fun iconForWorkout(id: String): ImageVector = when (id) {
    "free_ride" -> Icons.Default.DirectionsBike
    "fat_burn" -> Icons.Default.LocalFireDepartment
    "hill_climb" -> Icons.Default.Terrain
    "intervals" -> Icons.Default.Speed
    "endurance" -> Icons.Default.Timer
    "power_pyramid" -> Icons.Default.FitnessCenter
    else -> Icons.Default.FitnessCenter
}

private fun colorForWorkout(id: String): Color = when (id) {
    "free_ride" -> CadenceBlue
    "fat_burn" -> HeartRateRed
    "hill_climb" -> PowerGreen
    "intervals" -> SpeedOrange
    "endurance" -> ResistanceYellow
    "power_pyramid" -> NeonAccent
    else -> NeonAccent
}
