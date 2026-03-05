package com.bikearcade.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.SurfaceBorder
import com.bikearcade.ui.theme.TextPrimary
import com.bikearcade.ui.theme.TextSecondary

/**
 * Compact horizontal row showing Distance, Time, and Calories.
 */
@Composable
fun RideStatsBar(
    distanceMiles: Double,
    elapsedTimeMs: Long,
    calories: Int,
    modifier: Modifier = Modifier,
) {
    val formattedTime by remember(elapsedTimeMs) {
        derivedStateOf {
            val totalSeconds = elapsedTimeMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    val formattedDistance by remember(distanceMiles) {
        derivedStateOf {
            String.format("%.2f", distanceMiles)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = SurfaceBright,
                shape = MaterialTheme.shapes.medium,
            )
            .border(
                width = 1.dp,
                color = SurfaceBorder,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem(
            label = "DISTANCE",
            value = formattedDistance,
            unit = "mi",
            modifier = Modifier.weight(1f),
        )

        StatItem(
            label = "TIME",
            value = formattedTime,
            unit = "",
            modifier = Modifier.weight(1f),
        )

        StatItem(
            label = "CALORIES",
            value = calories.toString(),
            unit = "kcal",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        if (unit.isNotBlank()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = NeonAccent.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
