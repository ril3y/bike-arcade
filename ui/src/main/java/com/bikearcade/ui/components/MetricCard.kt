package com.bikearcade.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.SurfaceBorder
import com.bikearcade.ui.theme.TextSecondary

/**
 * Reusable metric display card showing an icon, large animated integer value,
 * and a unit label. The accent color is applied to the icon and value text.
 *
 * Designed for cycling readability at arm's length on a 1920x1080 screen.
 */
@Composable
fun MetricCard(
    icon: ImageVector,
    value: Int,
    unit: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 75),
        label = "metricValue",
    )

    Box(
        modifier = modifier
            .background(
                color = SurfaceBright,
                shape = MaterialTheme.shapes.medium,
            )
            .border(
                width = 1.dp,
                color = SurfaceBorder,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 6.dp else 10.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(if (compact) 14.dp else 20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label.uppercase(),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = animatedValue.toString(),
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
                color = accentColor,
                textAlign = TextAlign.Center,
            )

            if (!compact) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Variant for displaying a Double metric value with one decimal place.
 */
@Composable
fun MetricCardDouble(
    icon: ImageVector,
    value: Double,
    unit: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 75),
        label = "metricValueFloat",
    )
    val displayText = "%.1f".format(animatedValue)

    Box(
        modifier = modifier
            .background(
                color = SurfaceBright,
                shape = MaterialTheme.shapes.medium,
            )
            .border(
                width = 1.dp,
                color = SurfaceBorder,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 6.dp else 10.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(if (compact) 14.dp else 20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label.uppercase(),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = displayText,
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
                color = accentColor,
                textAlign = TextAlign.Center,
            )

            if (!compact) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
