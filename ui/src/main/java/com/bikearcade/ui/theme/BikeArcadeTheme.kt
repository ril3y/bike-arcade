package com.bikearcade.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val BikeArcadeColorScheme = darkColorScheme(
    primary = NeonAccent,
    onPrimary = DarkBackground,
    secondary = CadenceBlue,
    onSecondary = TextPrimary,
    tertiary = PowerGreen,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceBright,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = HeartRateRed,
    onError = TextPrimary,
)

/** Monospace family for metric values — retro/pixel feel */
val MetricFontFamily = FontFamily.Monospace

/** Clean sans-serif for labels and general text */
val LabelFontFamily = FontFamily.SansSerif

private val BikeArcadeTypography = Typography(
    // Large metric values (e.g., "142" for HR)
    displayLarge = TextStyle(
        fontFamily = MetricFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = 2.sp,
    ),
    // Medium metric values
    displayMedium = TextStyle(
        fontFamily = MetricFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 1.sp,
    ),
    // Small metric values
    displaySmall = TextStyle(
        fontFamily = MetricFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 1.sp,
    ),
    // Section headers (e.g., "LIVE METRICS")
    titleLarge = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 3.sp,
    ),
    // Card titles / labels
    titleMedium = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.sp,
    ),
    // Unit labels (e.g., "BPM", "RPM")
    labelLarge = TextStyle(
        fontFamily = MetricFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 2.sp,
    ),
    // Small labels
    labelMedium = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
    ),
    // Body text (track info, etc.)
    bodyLarge = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val BikeArcadeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun BikeArcadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BikeArcadeColorScheme,
        typography = BikeArcadeTypography,
        shapes = BikeArcadeShapes,
        content = content,
    )
}
