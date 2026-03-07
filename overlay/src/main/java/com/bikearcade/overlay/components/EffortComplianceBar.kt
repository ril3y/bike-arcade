package com.bikearcade.overlay.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikearcade.ui.theme.EffortOver
import com.bikearcade.ui.theme.EffortUnder
import com.bikearcade.ui.theme.HeartRateRed
import com.bikearcade.ui.theme.PowerGreen
import com.bikearcade.ui.theme.SurfaceBorder
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.TextSecondary

enum class EffortZone { UNDER, ON_TARGET, OVER, IDLE }

data class EffortComplianceState(
    val complianceRatio: Float = 0f,       // 0..1 position on the bar (0=bottom, 1=top)
    val targetZoneLow: Float = 0.35f,      // 0..1 position of target zone lower bound
    val targetZoneHigh: Float = 0.65f,     // 0..1 position of target zone upper bound
    val zone: EffortZone = EffortZone.IDLE,
    val actualPower: Int = 0,
    val targetPowerLow: Int = 0,
    val targetPowerHigh: Int = 0,
    val resistance: Int = 0,
    val pauseCountdown: Int = -1,          // -1 = no countdown, 5..1 = seconds until pause
) {
    companion object {
        val EMPTY = EffortComplianceState()
    }
}

private val BarTrack = Color(0xFF1A1A2E)
private val TargetZoneGreen = PowerGreen.copy(alpha = 0.25f)
private val TargetBorderGreen = PowerGreen.copy(alpha = 0.6f)
private val MarkerColor = Color.White

/**
 * Continuous effort compliance bar. Shows a vertical gauge with a green target zone band
 * and an animated marker for current power output. Color-coded: blue (under), green (on target),
 * orange-red (over).
 */
@Composable
fun EffortComplianceBar(
    compliance: EffortComplianceState,
    leanAngle: Float = 0f,
    heartRate: Int = 0,
    showHeartRate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val animatedRatio by animateFloatAsState(
        targetValue = compliance.complianceRatio,
        animationSpec = tween(durationMillis = 100, easing = androidx.compose.animation.core.LinearEasing),
        label = "effort_marker",
    )

    val textMeasurer = rememberTextMeasurer()

    val zoneColor = when (compliance.zone) {
        EffortZone.UNDER -> EffortUnder
        EffortZone.ON_TARGET -> PowerGreen
        EffortZone.OVER -> EffortOver
        EffortZone.IDLE -> TextSecondary
    }

    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(SurfaceBright, MaterialTheme.shapes.medium)
            .border(1.dp, SurfaceBorder, MaterialTheme.shapes.medium)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Heart rate (shown when HUD hidden)
        if (showHeartRate && heartRate > 0) {
            Text(
                text = "$heartRate",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HeartRateRed,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "BPM",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = HeartRateRed.copy(alpha = 0.7f),
                letterSpacing = 1.sp,
            )
        }

        // Target label
        Text(
            text = "TARGET",
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
        )
        if (compliance.targetPowerHigh > 0) {
            Text(
                text = "${compliance.targetPowerLow}-${compliance.targetPowerHigh}W",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = PowerGreen.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        // Continuous vertical gauge
        Canvas(
            modifier = Modifier
                .width(28.dp)
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            val w = size.width
            val h = size.height
            val cornerR = CornerRadius(4.dp.toPx())

            // Track background
            drawRoundRect(
                color = BarTrack,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = cornerR,
            )

            // Target zone band (green rectangle spanning targetZoneLow..targetZoneHigh)
            val zoneTopY = h * (1f - compliance.targetZoneHigh)
            val zoneBottomY = h * (1f - compliance.targetZoneLow)
            val zoneHeight = zoneBottomY - zoneTopY
            if (zoneHeight > 0f) {
                drawRect(
                    color = TargetZoneGreen,
                    topLeft = Offset(0f, zoneTopY),
                    size = Size(w, zoneHeight),
                )
                // Top boundary line
                drawLine(
                    color = TargetBorderGreen,
                    start = Offset(0f, zoneTopY),
                    end = Offset(w, zoneTopY),
                    strokeWidth = 1.5.dp.toPx(),
                )
                // Bottom boundary line
                drawLine(
                    color = TargetBorderGreen,
                    start = Offset(0f, zoneBottomY),
                    end = Offset(w, zoneBottomY),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            // Gradient fill from bottom to marker position
            if (animatedRatio > 0f) {
                val fillTop = h * (1f - animatedRatio)
                val fillHeight = h - fillTop

                val gradientColors = when (compliance.zone) {
                    EffortZone.UNDER -> listOf(EffortUnder.copy(alpha = 0.2f), EffortUnder.copy(alpha = 0.5f))
                    EffortZone.ON_TARGET -> listOf(PowerGreen.copy(alpha = 0.15f), PowerGreen.copy(alpha = 0.4f))
                    EffortZone.OVER -> listOf(PowerGreen.copy(alpha = 0.15f), EffortOver.copy(alpha = 0.5f))
                    EffortZone.IDLE -> listOf(TextSecondary.copy(alpha = 0.1f), TextSecondary.copy(alpha = 0.2f))
                }

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = fillTop,
                        endY = h,
                    ),
                    topLeft = Offset(0f, fillTop),
                    size = Size(w, fillHeight),
                )
            }

            // Current power marker — bright horizontal line
            if (compliance.zone != EffortZone.IDLE) {
                val markerY = h * (1f - animatedRatio)
                val markerThickness = 3.dp.toPx()

                drawLine(
                    color = zoneColor,
                    start = Offset(0f, markerY),
                    end = Offset(w, markerY),
                    strokeWidth = markerThickness,
                )
                // White core line for visibility
                drawLine(
                    color = MarkerColor,
                    start = Offset(2.dp.toPx(), markerY),
                    end = Offset(w - 2.dp.toPx(), markerY),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            // Outer border
            drawRoundRect(
                color = SurfaceBorder,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = cornerR,
                style = Stroke(width = 1.dp.toPx()),
            )

            // Pause countdown overlay
            if (compliance.pauseCountdown in 1..5) {
                // Dim background
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset.Zero,
                    size = Size(w, h),
                )
                // Large countdown number
                val countdownStyle = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = HeartRateRed,
                )
                val measured = textMeasurer.measure(
                    compliance.pauseCountdown.toString(),
                    countdownStyle,
                )
                drawText(
                    measured,
                    topLeft = Offset(
                        (w - measured.size.width) / 2f,
                        (h - measured.size.height) / 2f,
                    ),
                )
            }
        }

        // Actual power (large)
        Text(
            text = "${compliance.actualPower}W",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = zoneColor,
            textAlign = TextAlign.Center,
        )

        // Resistance context (small)
        if (compliance.resistance > 0) {
            Text(
                text = "R: ${compliance.resistance}",
                fontSize = 9.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        // Lean indicator
        if (leanAngle != 0f) {
            val absLean = kotlin.math.abs(leanAngle)
            val leanText = when {
                leanAngle < -10f -> "\u25C0\u25C0 L"
                leanAngle < -2f -> "\u25C0 L"
                leanAngle > 10f -> "R \u25B6\u25B6"
                leanAngle > 2f -> "R \u25B6"
                else -> "\u25CF"
            }
            val leanColor = when {
                absLean > 20f -> Color(0xFFFF6B6B)
                absLean > 10f -> com.bikearcade.ui.theme.ResistanceYellow
                else -> TextSecondary
            }
            Text(
                text = leanText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = leanColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
