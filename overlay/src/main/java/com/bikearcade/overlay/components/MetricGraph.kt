package com.bikearcade.overlay.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bikearcade.ui.theme.DarkBackground
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.SurfaceBorder
import com.bikearcade.ui.theme.TextMuted
import com.bikearcade.ui.theme.TextSecondary

/**
 * Real-time scrolling line graph using Canvas. Renders the last 60 seconds
 * of data points as a smooth line chart.
 *
 * @param dataPoints List of values (newest last). Should contain up to 60 entries (one per second).
 * @param label Display label for the graph (e.g., "Power" or "Cadence").
 * @param unit Unit string (e.g., "W" or "RPM").
 * @param lineColor Color of the data line.
 * @param yMin Minimum Y-axis value.
 * @param yMax Maximum Y-axis value.
 */
@Composable
fun MetricGraph(
    dataPoints: List<Float>,
    label: String,
    unit: String,
    lineColor: Color,
    yMin: Float = 0f,
    yMax: Float = 400f,
    modifier: Modifier = Modifier,
) {
    val yRange = remember(yMin, yMax) { (yMax - yMin).coerceAtLeast(1f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = DarkBackground,
                shape = MaterialTheme.shapes.medium,
            )
            .border(
                width = 1.dp,
                color = SurfaceBorder,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
    ) {
        // Header with label and current value
        val currentValue = dataPoints.lastOrNull() ?: 0f
        Text(
            text = "$label: ${currentValue.toInt()} $unit",
            style = MaterialTheme.typography.labelMedium,
            color = lineColor,
        )

        Spacer(Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
        ) {
            drawGraphBackground(yMin, yMax, yRange)
            if (dataPoints.size >= 2) {
                drawDataLine(dataPoints, lineColor, yMin, yRange)
            }
        }
    }
}

/**
 * Draws horizontal grid lines and Y-axis labels.
 */
private fun DrawScope.drawGraphBackground(
    yMin: Float,
    yMax: Float,
    yRange: Float,
) {
    val gridLines = 4
    for (i in 0..gridLines) {
        val fraction = i.toFloat() / gridLines
        val y = size.height * (1f - fraction)
        drawLine(
            color = SurfaceBorder,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

/**
 * Draws the data line path across the canvas.
 */
private fun DrawScope.drawDataLine(
    dataPoints: List<Float>,
    lineColor: Color,
    yMin: Float,
    yRange: Float,
) {
    val maxPoints = 60
    val points = if (dataPoints.size > maxPoints) {
        dataPoints.takeLast(maxPoints)
    } else {
        dataPoints
    }

    val path = Path()
    val stepX = size.width / (maxPoints - 1).toFloat()

    // Offset so most recent data is at the right edge
    val startOffset = maxPoints - points.size

    points.forEachIndexed { index, value ->
        val x = (startOffset + index) * stepX
        val normalized = ((value - yMin) / yRange).coerceIn(0f, 1f)
        val y = size.height * (1f - normalized)

        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    // Draw glow (wider, semi-transparent)
    drawPath(
        path = path,
        color = lineColor.copy(alpha = 0.2f),
        style = Stroke(
            width = 6f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // Draw main line
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(
            width = 2.5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/**
 * Stacked pair of power and cadence graphs, commonly used together.
 */
@Composable
fun PowerCadenceGraphs(
    powerData: List<Float>,
    cadenceData: List<Float>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "GRAPHS",
            style = MaterialTheme.typography.titleMedium,
            color = NeonAccent,
        )

        Spacer(Modifier.height(8.dp))

        MetricGraph(
            dataPoints = powerData,
            label = "Power",
            unit = "W",
            lineColor = com.bikearcade.ui.theme.PowerGreen,
            yMin = 0f,
            yMax = 400f,
        )

        Spacer(Modifier.height(6.dp))

        MetricGraph(
            dataPoints = cadenceData,
            label = "Cadence",
            unit = "RPM",
            lineColor = com.bikearcade.ui.theme.CadenceBlue,
            yMin = 0f,
            yMax = 130f,
        )
    }
}
