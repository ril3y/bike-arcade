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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.ui.components.MetricCard
import com.bikearcade.ui.components.MetricCardDouble
import com.bikearcade.ui.theme.CadenceBlue
import com.bikearcade.ui.theme.HeartRateRed
import com.bikearcade.ui.theme.PowerGreen
import com.bikearcade.ui.theme.ResistanceYellow
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
        // 3-column row: HR, Cadence, Power
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetricCard(
                icon = Icons.Default.Favorite,
                value = metrics.heartRate,
                unit = "BPM",
                label = "HR",
                accentColor = HeartRateRed,
                modifier = Modifier.weight(1f),
                compact = true,
            )
            MetricCard(
                icon = Icons.Default.DirectionsBike,
                value = metrics.cadence.toInt(),
                unit = "RPM",
                label = "Cadence",
                accentColor = CadenceBlue,
                modifier = Modifier.weight(1f),
                compact = true,
            )
            MetricCard(
                icon = Icons.Default.FlashOn,
                value = metrics.power,
                unit = "W",
                label = "Power",
                accentColor = PowerGreen,
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }

        Spacer(Modifier.height(6.dp))

        // 2-column row: Speed, Resistance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetricCardDouble(
                icon = Icons.Default.Speed,
                value = metrics.speed,
                unit = "MPH",
                label = "Speed",
                accentColor = SpeedOrange,
                modifier = Modifier.weight(1f),
                compact = true,
            )
            MetricCard(
                icon = Icons.Default.Tune,
                value = metrics.resistance,
                unit = "",
                label = "Resist",
                accentColor = ResistanceYellow,
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
    }
}
