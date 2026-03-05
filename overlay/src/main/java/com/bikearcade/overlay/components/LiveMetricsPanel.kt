package com.bikearcade.overlay.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.ui.components.MetricCard
import com.bikearcade.ui.components.MetricCardDouble
import com.bikearcade.ui.theme.CadenceBlue
import com.bikearcade.ui.theme.HeartRateRed
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.PowerGreen
import com.bikearcade.ui.theme.SpeedOrange

/**
 * Grid of 4 live metric cards: Heart Rate, Cadence, Power, Speed.
 * Arranged in a 2x2 grid for readability on the 640px sidebar.
 */
@Composable
fun LiveMetricsPanel(
    metrics: BikeMetrics,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "LIVE METRICS",
            style = MaterialTheme.typography.titleMedium,
            color = NeonAccent,
        )

        Spacer(Modifier.height(8.dp))

        // Top row: HR + Cadence
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard(
                icon = Icons.Default.Favorite,
                value = metrics.heartRate,
                unit = "BPM",
                label = "Heart Rate",
                accentColor = HeartRateRed,
                modifier = Modifier.weight(1f),
            )

            MetricCard(
                icon = Icons.Default.DirectionsBike,
                value = metrics.cadence.toInt(),
                unit = "RPM",
                label = "Cadence",
                accentColor = CadenceBlue,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Bottom row: Power + Speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard(
                icon = Icons.Default.FlashOn,
                value = metrics.power,
                unit = "W",
                label = "Power",
                accentColor = PowerGreen,
                modifier = Modifier.weight(1f),
            )

            MetricCardDouble(
                icon = Icons.Default.Speed,
                value = metrics.speed,
                unit = "MPH",
                label = "Speed",
                accentColor = SpeedOrange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
