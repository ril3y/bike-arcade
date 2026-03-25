package com.bikearcade.app.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Full-screen intensity picker shown before starting a workout.
 * Shows workout info, user-specific targets, and intensity options.
 */
@Composable
fun IntensityPickerDialog(
    workout: WorkoutDefinition,
    userProfile: UserProfile,
    onConfirm: (WorkoutScaler.Intensity) -> Unit,
    onCancel: () -> Unit,
) {
    val suggested = WorkoutScaler.suggestedIntensity(workout)
    var selected by remember { mutableStateOf(suggested) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(700.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceBright)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Text(
                text = workout.name.uppercase(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = NeonAccent,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${workout.totalDurationMinutes} min  •  ${workout.segments.size} segments",
                fontSize = 14.sp,
                color = TextSecondary,
            )

            Spacer(Modifier.height(16.dp))

            // User stats bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                UserStatChip("Max HR", "${userProfile.maxHeartRate} bpm", HeartRateRed)
                UserStatChip("Weight", "${userProfile.weightLbs.toInt()} lbs", CadenceBlue)
                UserStatChip("BMI", "%.1f".format(userProfile.bmi), SpeedOrange)
                val bf = userProfile.bodyFatPercent
                if (bf != null) {
                    UserStatChip("Body Fat", "%.0f%%".format(bf), PowerGreen)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "SELECT INTENSITY",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(12.dp))

            // Intensity options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (intensity in WorkoutScaler.Intensity.entries) {
                    val isSelected = intensity == selected
                    val color = colorForIntensity(intensity)
                    val hrRange = WorkoutScaler.targetHeartRate(userProfile, intensity)
                    val powerRange = WorkoutScaler.targetPower(userProfile, intensity)
                    val isSuggested = intensity == suggested

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) color.copy(alpha = 0.15f) else SurfaceDark)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) color else SurfaceBorder,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { selected = intensity }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (isSuggested) {
                            Text(
                                text = "RECOMMENDED",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                letterSpacing = 1.sp,
                            )
                        } else {
                            Spacer(Modifier.height(10.dp))
                        }

                        Text(
                            text = intensity.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) color else TextPrimary,
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = intensity.subtitle,
                            fontSize = 9.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )

                        Spacer(Modifier.height(8.dp))

                        // Target HR
                        Text(
                            text = "${hrRange.first}–${hrRange.last}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeartRateRed.copy(alpha = if (isSelected) 1f else 0.6f),
                        )
                        Text(
                            text = "bpm",
                            fontSize = 8.sp,
                            color = TextSecondary,
                        )

                        Spacer(Modifier.height(4.dp))

                        // Target Power
                        Text(
                            text = "${powerRange.first}–${powerRange.last}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PowerGreen.copy(alpha = if (isSelected) 1f else 0.6f),
                        )
                        Text(
                            text = "watts",
                            fontSize = 8.sp,
                            color = TextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Confirm / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onCancel) {
                    Text("CANCEL", color = TextSecondary)
                }
                TextButton(onClick = { onConfirm(selected) }) {
                    Text(
                        text = "START ${selected.label.uppercase()}",
                        color = NeonAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserStatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 9.sp, color = TextSecondary)
    }
}

private fun colorForIntensity(intensity: WorkoutScaler.Intensity): Color = when (intensity) {
    WorkoutScaler.Intensity.RECOVERY -> CadenceBlue
    WorkoutScaler.Intensity.LIGHT -> PowerGreen
    WorkoutScaler.Intensity.MODERATE -> ResistanceYellow
    WorkoutScaler.Intensity.VIGOROUS -> SpeedOrange
    WorkoutScaler.Intensity.MAX -> HeartRateRed
}
