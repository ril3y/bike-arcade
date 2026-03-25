package com.bikearcade.overlay.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bikearcade.settings.WorkoutSegment
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.PowerGreen
import com.bikearcade.ui.theme.SurfaceBorder
import com.bikearcade.ui.theme.SurfaceBright

private val ExpectedColor = NeonAccent
private val ActualColor = PowerGreen
private val ActualOverColor = Color(0xFFFF6B3B)
private val ProgressLine = Color(0xFFFFFFFF)

/**
 * Workout graph showing expected target power profile and actual power overlay.
 * The expected profile is derived from segment resistance levels mapped to relative power.
 * A vertical white line shows current position in the workout.
 *
 * @param targetPowerPerSegment target power (center) for each segment, used for Y-axis scaling
 * @param actualPower list of actual power readings (one per second)
 * @param difficultyMultiplier current difficulty multiplier (shifts target line)
 */
@Composable
fun WorkoutGraph(
    segments: List<WorkoutSegment>,
    totalSeconds: Int,
    elapsedSeconds: Int,
    actualPower: List<Int>,
    targetPowerPerSegment: List<Int> = emptyList(),
    difficultyMultiplier: Float = 1.0f,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty() || totalSeconds <= 0) return

    // Compute max power for Y-axis: max of target powers and actual powers
    val maxTargetPower = if (targetPowerPerSegment.isNotEmpty()) {
        (targetPowerPerSegment.maxOrNull() ?: 1)
    } else {
        // Fallback: use resistance levels as relative scale
        segments.maxOf { it.resistanceLevel }.coerceAtLeast(1)
    }
    val maxActualPower = if (actualPower.isNotEmpty()) actualPower.max() else 0
    val maxPower = ((maxOf(maxTargetPower, maxActualPower) * difficultyMultiplier) * 1.3f).toInt().coerceAtLeast(1)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(SurfaceBright, MaterialTheme.shapes.medium)
            .border(1.dp, SurfaceBorder, MaterialTheme.shapes.medium)
            .padding(4.dp)
    ) {
        val w = size.width
        val h = size.height
        val pad = 2f

        fun powerToY(power: Float): Float =
            h - pad - ((power / maxPower) * (h - pad * 2))

        // Draw expected target power profile (filled area)
        val expectedPath = Path()
        expectedPath.moveTo(0f, h)
        var xOff = 0f
        for ((i, seg) in segments.withIndex()) {
            val segW = (seg.durationSeconds.toFloat() / totalSeconds) * w
            val targetP = if (i < targetPowerPerSegment.size) {
                targetPowerPerSegment[i] * difficultyMultiplier
            } else {
                seg.resistanceLevel.toFloat() // fallback
            }
            val y = powerToY(targetP)
            expectedPath.lineTo(xOff, y)
            expectedPath.lineTo(xOff + segW, y)
            xOff += segW
        }
        expectedPath.lineTo(w, h)
        expectedPath.close()

        drawPath(
            expectedPath,
            brush = Brush.verticalGradient(
                listOf(ExpectedColor.copy(alpha = 0.25f), ExpectedColor.copy(alpha = 0.05f))
            ),
            style = Fill,
        )

        // Draw expected outline
        xOff = 0f
        for ((i, seg) in segments.withIndex()) {
            val segW = (seg.durationSeconds.toFloat() / totalSeconds) * w
            val targetP = if (i < targetPowerPerSegment.size) {
                targetPowerPerSegment[i] * difficultyMultiplier
            } else {
                seg.resistanceLevel.toFloat()
            }
            val y = powerToY(targetP)
            if (i > 0) {
                val prevP = if (i - 1 < targetPowerPerSegment.size) {
                    targetPowerPerSegment[i - 1] * difficultyMultiplier
                } else {
                    segments[i - 1].resistanceLevel.toFloat()
                }
                drawLine(ExpectedColor.copy(alpha = 0.3f), Offset(xOff, powerToY(prevP)), Offset(xOff, y), 1f)
            }
            drawLine(ExpectedColor.copy(alpha = 0.6f), Offset(xOff, y), Offset(xOff + segW, y), 2f)
            xOff += segW
        }

        // Draw actual power line
        if (actualPower.size >= 2) {
            val actualPath = Path()
            val step = w / totalSeconds.toFloat()
            for ((i, power) in actualPower.withIndex()) {
                val x = i * step
                val y = powerToY(power.toFloat())
                if (i == 0) actualPath.moveTo(x, y) else actualPath.lineTo(x, y)
            }
            drawPath(actualPath, ActualColor.copy(alpha = 0.8f), style = Stroke(width = 2f))
        }

        // Draw progress line
        if (elapsedSeconds > 0) {
            val px = (elapsedSeconds.toFloat() / totalSeconds) * w
            drawLine(
                ProgressLine.copy(alpha = 0.5f),
                Offset(px, 0f),
                Offset(px, h),
                strokeWidth = 1.5f,
            )
        }
    }
}
