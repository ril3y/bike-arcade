package com.bikearcade.app

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikearcade.settings.ScreenSaverStyle
import kotlin.math.sin
import kotlin.random.Random

private val NEON_COLORS = listOf(
    Color(0xFF00FFFF), // cyan
    Color(0xFF00FF66), // green
    Color(0xFF4488FF), // blue
    Color(0xFFFF3366), // red
    Color(0xFFFF8800), // orange
    Color(0xFFFF00FF), // magenta
)

// --- Dispatcher ---

@Composable
fun ScreenSaver(
    style: ScreenSaverStyle,
    lastRideStats: LastRideStats? = null,
    onDismiss: () -> Unit,
) {
    when (style) {
        ScreenSaverStyle.ARCADE -> ArcadeAttractMode(onDismiss)
        ScreenSaverStyle.BOUNCING_LOGO -> BouncingLogoMode(onDismiss)
        ScreenSaverStyle.STARFIELD -> StarfieldMode(onDismiss)
        ScreenSaverStyle.BLACKOUT -> BlackoutMode(onDismiss)
    }
}

// --- 1. Arcade Cabinet floating attract mode ---

@Composable
private fun ArcadeAttractMode(onDismiss: () -> Unit) {
    // Cabinet is ~300px wide x ~500px tall, drifts around screen
    val cabinetW = 340f
    val cabinetH = 560f
    val screenW = 1920f
    val screenH = 1080f

    var cabX by remember { mutableFloatStateOf(screenW / 2f - cabinetW / 2f) }
    var cabY by remember { mutableFloatStateOf(screenH / 2f - cabinetH / 2f) }
    var velX by remember { mutableFloatStateOf(40f) }
    var velY by remember { mutableFloatStateOf(28f) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    var colorIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var lastFrameMs = 0L
        withInfiniteAnimationFrameMillis { lastFrameMs = it }
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val dt = ((frameMs - lastFrameMs).coerceIn(0, 100)) / 1000f
                lastFrameMs = frameMs
                timeSeconds += dt

                cabX += velX * dt
                cabY += velY * dt

                var bounced = false
                if (cabX < 20f) { cabX = 20f; velX = -velX; bounced = true }
                if (cabX > screenW - cabinetW - 20f) { cabX = screenW - cabinetW - 20f; velX = -velX; bounced = true }
                if (cabY < 20f) { cabY = 20f; velY = -velY; bounced = true }
                if (cabY > screenH - cabinetH - 20f) { cabY = screenH - cabinetH - 20f; velY = -velY; bounced = true }
                if (bounced) colorIndex = (colorIndex + 1) % NEON_COLORS.size
            }
        }
    }

    val neonColor = NEON_COLORS[colorIndex]
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .dismissOnClick(onDismiss),
    ) {
        // Subtle scanlines over the whole screen
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScanlines((timeSeconds * 60f) % 4f)
        }

        // The cabinet
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArcadeCabinet(cabX, cabY, cabinetW, cabinetH, neonColor, timeSeconds, textMeasurer)
        }

        // "TOUCH TO PLAY" at bottom
        val touchAlpha = (0.2f + 0.3f * ((sin(timeSeconds * 2f) + 1f) / 2f))
        Text(
            text = "TOUCH TO PLAY",
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = touchAlpha),
            letterSpacing = 4.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

private fun DrawScope.drawArcadeCabinet(
    x: Float, y: Float, w: Float, h: Float,
    neonColor: Color, time: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    // Cabinet body proportions
    val marqueeH = h * 0.14f       // top marquee
    val screenTop = y + marqueeH + 8f
    val screenH = h * 0.38f         // CRT screen area
    val controlH = h * 0.12f        // control panel
    val controlTop = screenTop + screenH + 10f
    val baseTop = controlTop + controlH + 6f
    val baseH = h - (baseTop - y)

    // --- Cabinet shell (dark body) ---
    val bodyPath = Path().apply {
        // Slightly tapered shape
        val taper = 12f
        moveTo(x + taper, y)
        lineTo(x + w - taper, y)
        lineTo(x + w, y + marqueeH)
        lineTo(x + w + 5f, y + h)
        lineTo(x - 5f, y + h)
        lineTo(x, y + marqueeH)
        close()
    }
    drawPath(bodyPath, Color(0xFF1A1A1A), style = Fill)
    drawPath(bodyPath, Color(0xFF333333), style = Stroke(width = 2f))

    // --- Marquee (lit header with title) ---
    val marqueeGlow = (sin(time * 2f) + 1f) / 2f * 0.3f + 0.7f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                neonColor.copy(alpha = marqueeGlow),
                neonColor.copy(alpha = marqueeGlow * 0.5f),
            ),
        ),
        topLeft = Offset(x + 10f, y + 4f),
        size = Size(w - 20f, marqueeH - 4f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    // Marquee border
    drawRoundRect(
        color = Color.White.copy(alpha = 0.3f),
        topLeft = Offset(x + 10f, y + 4f),
        size = Size(w - 20f, marqueeH - 4f),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 1.5f),
    )

    // "BIKE ARCADE" on the marquee
    val titleStyle = TextStyle(
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 4.sp,
    )
    val titleLayout = textMeasurer.measure("BIKE ARCADE", titleStyle)
    drawText(
        titleLayout,
        topLeft = Offset(
            x + (w - titleLayout.size.width) / 2f,
            y + (marqueeH - titleLayout.size.height) / 2f + 2f,
        ),
    )

    // --- Screen (CRT area) ---
    // Screen bezel
    drawRoundRect(
        color = Color(0xFF0A0A0A),
        topLeft = Offset(x + 20f, screenTop),
        size = Size(w - 40f, screenH),
        cornerRadius = CornerRadius(4f, 4f),
    )
    // Screen glass with scanline tint
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF001122),
                Color(0xFF000A15),
            ),
        ),
        topLeft = Offset(x + 25f, screenTop + 5f),
        size = Size(w - 50f, screenH - 10f),
        cornerRadius = CornerRadius(2f, 2f),
    )
    // Screen reflection glare
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.06f),
                Color.Transparent,
            ),
            start = Offset(x + 25f, screenTop + 5f),
            end = Offset(x + w / 2f, screenTop + screenH / 2f),
        ),
        topLeft = Offset(x + 25f, screenTop + 5f),
        size = Size((w - 50f) * 0.6f, (screenH - 10f) * 0.4f),
        cornerRadius = CornerRadius(2f, 2f),
    )

    // "INSERT COIN" blinking on the CRT screen
    val blinkOn = (time * 1.5f) % 1f < 0.65f
    if (blinkOn) {
        val coinStyle = TextStyle(
            color = Color(0xFFFFDD00),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 3.sp,
        )
        val coinLayout = textMeasurer.measure("INSERT COIN", coinStyle)
        drawText(
            coinLayout,
            topLeft = Offset(
                x + (w - coinLayout.size.width) / 2f,
                screenTop + (screenH - coinLayout.size.height) / 2f,
            ),
        )
    }

    // Neon glow around screen
    val screenGlow = (sin(time * 3f) + 1f) / 2f * 0.25f + 0.1f
    drawRoundRect(
        color = neonColor.copy(alpha = screenGlow),
        topLeft = Offset(x + 18f, screenTop - 2f),
        size = Size(w - 36f, screenH + 4f),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 2f),
    )

    // --- Control panel ---
    drawRoundRect(
        color = Color(0xFF222222),
        topLeft = Offset(x + 10f, controlTop),
        size = Size(w - 20f, controlH),
        cornerRadius = CornerRadius(3f, 3f),
    )
    // Joystick
    val joyX = x + w * 0.32f
    val joyY = controlTop + controlH / 2f
    drawCircle(Color(0xFF333333), 16f, Offset(joyX, joyY))
    drawCircle(Color(0xFF111111), 7f, Offset(joyX, joyY))
    drawLine(Color(0xFF444444), Offset(joyX, joyY - 5f), Offset(joyX - 2f, joyY - 18f), strokeWidth = 4f)
    drawCircle(Color(0xFF222222), 6f, Offset(joyX - 2f, joyY - 18f))

    // Buttons (3 colored)
    val btnColors = listOf(Color(0xFFCC2222), Color(0xFF2222CC), Color(0xFFCCCC22))
    for (i in 0..2) {
        val bx = x + w * 0.55f + i * 28f
        val by = joyY + (if (i == 1) -6f else 2f)
        drawCircle(btnColors[i].copy(alpha = 0.8f), 10f, Offset(bx, by))
        drawCircle(Color.White.copy(alpha = 0.15f), 6f, Offset(bx - 2f, by - 2f))
    }

    // --- Coin slot area (on the base) ---
    val coinSlotX = x + w / 2f
    val coinSlotY = baseTop + baseH * 0.3f
    val slotPlateW = 60f
    val slotPlateH = 44f

    // Coin slot plate
    drawRoundRect(
        color = Color(0xFF2A2A2A),
        topLeft = Offset(coinSlotX - slotPlateW / 2f, coinSlotY),
        size = Size(slotPlateW, slotPlateH),
        cornerRadius = CornerRadius(4f, 4f),
    )
    drawRoundRect(
        color = Color(0xFF555555),
        topLeft = Offset(coinSlotX - slotPlateW / 2f, coinSlotY),
        size = Size(slotPlateW, slotPlateH),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(width = 1.5f),
    )
    // Coin slit
    drawRoundRect(
        color = Color(0xFF0A0A0A),
        topLeft = Offset(coinSlotX - 18f, coinSlotY + 12f),
        size = Size(36f, 5f),
        cornerRadius = CornerRadius(2f, 2f),
    )
    drawRoundRect(
        color = Color(0xFF444444),
        topLeft = Offset(coinSlotX - 18f, coinSlotY + 11f),
        size = Size(36f, 1.5f),
        cornerRadius = CornerRadius(1f, 1f),
    )

    // Animated coin dropping
    val coinCycle = (time * 0.8f) % 3f
    if (coinCycle < 1.5f) {
        val progress = (coinCycle / 1.5f).coerceIn(0f, 1f)
        val coinY2 = coinSlotY - 30f + (progress * 30f)
        val coinAlpha = if (progress > 0.85f) (1f - (progress - 0.85f) * 6.67f).coerceAtLeast(0f) else 1f
        drawCircle(Color(0xFF886600).copy(alpha = coinAlpha * 0.4f), 10f, Offset(coinSlotX, coinY2 + 2f))
        drawCircle(Color(0xFFFFCC00).copy(alpha = coinAlpha), 8f, Offset(coinSlotX, coinY2))
        drawCircle(Color(0xFFFFEE88).copy(alpha = coinAlpha * 0.5f), 4f, Offset(coinSlotX - 2f, coinY2 - 2f))
    }

    // Pulsing neon glow around coin slot
    val coinGlow = (sin(time * 4f) + 1f) / 2f * 0.5f
    drawRoundRect(
        color = neonColor.copy(alpha = coinGlow),
        topLeft = Offset(coinSlotX - slotPlateW / 2f - 3f, coinSlotY - 3f),
        size = Size(slotPlateW + 6f, slotPlateH + 6f),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 1.5f),
    )

    // --- Neon glow around entire cabinet ---
    val cabinetGlow = (sin(time * 1.5f) + 1f) / 2f * 0.15f + 0.05f
    drawPath(bodyPath, neonColor.copy(alpha = cabinetGlow), style = Stroke(width = 3f))

    // --- Floor shadow ---
    drawOval(
        color = Color.White.copy(alpha = 0.02f),
        topLeft = Offset(x - 10f, y + h - 5f),
        size = Size(w + 20f, 20f),
    )
}

// --- 2. Bouncing Logo (DVD-style) ---

@Composable
private fun BouncingLogoMode(onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val logoWidthPx = with(density) { 200.dp.toPx() }
    val logoHeightPx = with(density) { 240.dp.toPx() }
    val screenW = 1920f
    val screenH = 1080f

    var logoX by remember { mutableFloatStateOf(screenW * 0.3f) }
    var logoY by remember { mutableFloatStateOf(screenH * 0.3f) }
    var velX by remember { mutableFloatStateOf(65f) }
    var velY by remember { mutableFloatStateOf(45f) }
    var colorIndex by remember { mutableIntStateOf(0) }
    var pulsePhase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameMs = 0L
        withInfiniteAnimationFrameMillis { lastFrameMs = it }
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val dt = ((frameMs - lastFrameMs).coerceIn(0, 100)) / 1000f
                lastFrameMs = frameMs
                logoX += velX * dt
                logoY += velY * dt
                var bounced = false
                if (logoX < 0f) { logoX = 0f; velX = -velX; bounced = true }
                if (logoX > screenW - logoWidthPx) { logoX = screenW - logoWidthPx; velX = -velX; bounced = true }
                if (logoY < 0f) { logoY = 0f; velY = -velY; bounced = true }
                if (logoY > screenH - logoHeightPx) { logoY = screenH - logoHeightPx; velY = -velY; bounced = true }
                if (bounced) colorIndex = (colorIndex + 1) % NEON_COLORS.size
                pulsePhase += dt * 3.14159f
            }
        }
    }

    val currentColor = NEON_COLORS[colorIndex]
    val pulseAlpha = (0.3f + 0.7f * ((sin(pulsePhase) + 1f) / 2f)).coerceIn(0.3f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .dismissOnClick(onDismiss),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset { IntOffset(logoX.toInt(), logoY.toInt()) },
        ) {
            Image(
                painter = painterResource(id = R.drawable.bikearcade_logo),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )
            Text(
                text = "BIKE ARCADE",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = currentColor,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = "TOUCH TO PLAY",
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = pulseAlpha),
            letterSpacing = 4.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
        )
    }
}

// --- 3. Starfield ---

private data class Star(var x: Float, var y: Float, var z: Float, val maxZ: Float)

@Composable
private fun StarfieldMode(onDismiss: () -> Unit) {
    val stars = remember {
        List(200) {
            Star(
                x = Random.nextFloat() * 2f - 1f,
                y = Random.nextFloat() * 2f - 1f,
                z = Random.nextFloat() * 1000f,
                maxZ = 1000f,
            )
        }
    }
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    var colorIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var lastFrameMs = 0L
        withInfiniteAnimationFrameMillis { lastFrameMs = it }
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val dt = ((frameMs - lastFrameMs).coerceIn(0, 100)) / 1000f
                lastFrameMs = frameMs
                timeSeconds += dt
                colorIndex = ((timeSeconds / 4f).toInt()) % NEON_COLORS.size
                for (star in stars) {
                    star.z -= 200f * dt
                    if (star.z <= 1f) {
                        star.x = Random.nextFloat() * 2f - 1f
                        star.y = Random.nextFloat() * 2f - 1f
                        star.z = star.maxZ
                    }
                }
            }
        }
    }

    val neonColor = NEON_COLORS[colorIndex]
    val pulseAlpha = (0.3f + 0.4f * ((sin(timeSeconds * 2f) + 1f) / 2f))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .dismissOnClick(onDismiss),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (star in stars) {
                val sx = cx + (star.x * 600f) / (star.z * 0.003f + 0.1f)
                val sy = cy + (star.y * 600f) / (star.z * 0.003f + 0.1f)
                if (sx < 0 || sx > size.width || sy < 0 || sy > size.height) continue
                val brightness = (1f - star.z / star.maxZ).coerceIn(0f, 1f)
                val radius = (1f + brightness * 2.5f)
                drawCircle(Color.White.copy(alpha = brightness * 0.9f), radius, Offset(sx, sy))
                val trailLen = brightness * 8f
                val dx = (star.x * 600f)
                val dy = (star.y * 600f)
                val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                drawLine(
                    neonColor.copy(alpha = brightness * 0.3f),
                    Offset(sx, sy),
                    Offset(sx - (dx / dist) * trailLen, sy - (dy / dist) * trailLen),
                    strokeWidth = radius * 0.5f,
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "BIKE ARCADE",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = neonColor.copy(alpha = 0.8f),
                letterSpacing = 6.sp,
            )
        }
        Text(
            text = "TOUCH TO PLAY",
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = pulseAlpha),
            letterSpacing = 4.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
        )
    }
}

// --- 4. Blackout ---

@Composable
private fun BlackoutMode(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .dismissOnClick(onDismiss),
    )
}

// --- Shared helpers ---

private fun Modifier.dismissOnClick(onDismiss: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
    ) { onDismiss() }

private fun DrawScope.drawScanlines(offset: Float) {
    val spacing = 4f
    var y = offset % spacing
    while (y < size.height) {
        drawLine(
            Color.White.copy(alpha = 0.03f),
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += spacing
    }
}

data class LastRideStats(
    val avgPower: Int,
    val distance: String,
    val duration: String,
)
