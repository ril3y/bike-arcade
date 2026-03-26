package com.bikearcade.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikearcade.emulator.CoreDownloader
import com.bikearcade.emulator.CoreInfo
import kotlinx.coroutines.launch

// Dark theme colors
private val BackgroundDark = Color(0xFF0D0D1A)
private val SurfaceDark = Color(0xFF1A1A2E)
private val SurfaceCard = Color(0xFF16162A)
private val AccentCyan = Color(0xFF4FC3F7)
private val AccentGreen = Color(0xFF66BB6A)
private val AccentOrange = Color(0xFFFFB74D)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val DividerColor = Color(0xFF2A2A3E)
private val CardBorder = Color(0xFF2A2A40)
private val DangerRed = Color(0xFF8B0000)

private val CardShape = RoundedCornerShape(12.dp)

/**
 * Redesigned settings screen with two-column layout and organized sections.
 */
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onClose: () -> Unit = {},
    onTestScreenSaver: () -> Unit = {},
) {
    val settings by repository.settings.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SETTINGS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentCyan,
                    letterSpacing = 3.sp,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Two-column layout
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Left column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Display section
                    SettingsCard(icon = Icons.Default.Tv, title = "DISPLAY") {
                        SettingsSlider(
                            label = "Game Scale",
                            value = settings.gameScale,
                            valueRange = AppSettings.GAME_SCALE_RANGE,
                            valueLabel = "%.1fx".format(settings.gameScale),
                            onValueChange = { repository.update { copy(gameScale = it) } },
                        )
                        SettingsSlider(
                            label = "Sidebar Width",
                            value = settings.sidebarWidth.toFloat(),
                            valueRange = AppSettings.SIDEBAR_WIDTH_RANGE.first.toFloat()..
                                    AppSettings.SIDEBAR_WIDTH_RANGE.last.toFloat(),
                            valueLabel = "${settings.sidebarWidth} px",
                            steps = (AppSettings.SIDEBAR_WIDTH_RANGE.last - AppSettings.SIDEBAR_WIDTH_RANGE.first) / 20 - 1,
                            onValueChange = { repository.update { copy(sidebarWidth = it.toInt()) } },
                        )
                        SettingsSlider(
                            label = "HUD Opacity",
                            value = settings.hudOpacity,
                            valueRange = AppSettings.HUD_OPACITY_RANGE,
                            valueLabel = "${(settings.hudOpacity * 100).toInt()}%",
                            onValueChange = { repository.update { copy(hudOpacity = it) } },
                        )
                    }

                    // Screen saver section
                    SettingsCard(icon = Icons.Default.DarkMode, title = "SCREEN SAVER") {
                        Text(
                            text = "Style",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        ScreenSaverStylePicker(
                            selected = settings.screenSaverStyle,
                            onSelected = { repository.update { copy(screenSaverStyle = it) } },
                        )
                        Spacer(Modifier.height(8.dp))
                        SettingsSlider(
                            label = "Idle Timeout",
                            value = settings.screenSaverTimeoutMinutes.toFloat(),
                            valueRange = AppSettings.SCREEN_SAVER_TIMEOUT_RANGE.first.toFloat()..
                                    AppSettings.SCREEN_SAVER_TIMEOUT_RANGE.last.toFloat(),
                            valueLabel = "${settings.screenSaverTimeoutMinutes} min",
                            steps = AppSettings.SCREEN_SAVER_TIMEOUT_RANGE.last - AppSettings.SCREEN_SAVER_TIMEOUT_RANGE.first - 1,
                            onValueChange = { repository.update { copy(screenSaverTimeoutMinutes = it.toInt()) } },
                        )
                        Text(
                            text = "Activates on menu screens after idle. Touch or pedal to dismiss.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        OutlinedButton(
                            onClick = onTestScreenSaver,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Preview")
                        }
                    }

                    // Reset
                    OutlinedButton(
                        onClick = { repository.resetToDefaults() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("Reset All to Defaults")
                    }
                }

                // Right column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Metrics section
                    SettingsCard(icon = Icons.Default.Speed, title = "BIKE METRICS") {
                        SettingsToggle(
                            label = "Metric Units",
                            description = if (settings.useMetricUnits) "km/h, km" else "mph, miles",
                            checked = settings.useMetricUnits,
                            onCheckedChange = { repository.update { copy(useMetricUnits = it) } },
                        )
                        Spacer(Modifier.height(4.dp))
                        SettingsSlider(
                            label = "Smoothing",
                            value = settings.smoothingAlpha,
                            valueRange = AppSettings.SMOOTHING_ALPHA_RANGE,
                            valueLabel = "%.2f".format(settings.smoothingAlpha),
                            onValueChange = { repository.update { copy(smoothingAlpha = it) } },
                        )
                        Text(
                            text = "Connection: UCB Direct (kills JRNY, full hardware control)",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    // Games section
                    SettingsCard(icon = Icons.Default.Gamepad, title = "GAME LIBRARY") {
                        Text(
                            text = "Artwork Server",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        ServerSelector()
                        Spacer(Modifier.height(8.dp))
                        SettingsToggle(
                            label = "Auto-download Artwork",
                            description = "Fetch box art from server on scan",
                            checked = true,
                            onCheckedChange = { /* TODO persist */ },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Metadata Database",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = "OpenVGDB — genre, publisher, release info",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "Downloaded on first scan, cached locally (~42 MB)",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    // Emulator cores section
                    CoreManagerSection()

                    // Controller section
                    SettingsCard(icon = Icons.Default.Bluetooth, title = "BLUETOOTH") {
                        BluetoothPairingPanel()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceCard)
            .border(1.dp, CardBorder, CardShape)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = AccentCyan,
                letterSpacing = 1.5.sp,
            )
        }
        content()
    }
}

@Composable
private fun ScreenSaverStylePicker(
    selected: ScreenSaverStyle,
    onSelected: (ScreenSaverStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScreenSaverStyle.entries.forEach { style ->
            val isSelected = style == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) AccentCyan.copy(alpha = 0.1f) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) AccentCyan.copy(alpha = 0.4f) else CardBorder,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelected(style) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) AccentCyan else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) AccentCyan else TextSecondary,
                            shape = RoundedCornerShape(7.dp),
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = style.displayName,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                    Text(
                        text = style.description,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSelector() {
    val servers = listOf("Libretro Thumbnails (default)")
    var selectedIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        servers.forEachIndexed { index, server ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (index == selectedIndex) AccentCyan.copy(alpha = 0.1f) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (index == selectedIndex) AccentCyan.copy(alpha = 0.4f) else CardBorder,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { selectedIndex = index }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (index == selectedIndex) AccentCyan else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (index == selectedIndex) AccentCyan else TextSecondary,
                            shape = RoundedCornerShape(7.dp),
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = server,
                    color = if (index == selectedIndex) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = TextPrimary, fontSize = 14.sp)
            Text(text = valueLabel, color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan,
                activeTrackColor = AccentCyan,
                inactiveTrackColor = SurfaceDark,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = TextPrimary, fontSize = 14.sp)
            Text(text = description, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentCyan,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = SurfaceDark,
                uncheckedThumbColor = TextSecondary,
            ),
        )
    }
}

@Composable
private fun CoreManagerSection() {
    val context = LocalContext.current
    val downloader = remember { CoreDownloader(context) }
    val scope = rememberCoroutineScope()

    val downloadingId by downloader.downloading.collectAsState()
    val progress by downloader.downloadProgress.collectAsState()

    // Track installed state; incremented after download/delete to force recomposition
    var refreshKey by remember { mutableIntStateOf(0) }

    SettingsCard(icon = Icons.Default.Memory, title = "MANAGE CORES") {
        Text(
            text = "Libretro emulator cores (GPL, downloaded on demand)",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        CoreDownloader.AVAILABLE_CORES.forEach { core ->
            @Suppress("UNUSED_EXPRESSION")
            refreshKey // read to trigger recomposition

            val installed = downloader.isInstalled(core)
            val isDownloading = downloadingId == core.id

            CoreRow(
                core = core,
                installed = installed,
                isDownloading = isDownloading,
                progress = if (isDownloading) progress else 0f,
                onDownload = {
                    scope.launch {
                        downloader.downloadCore(core)
                        refreshKey++
                    }
                },
                onDelete = {
                    downloader.deleteCore(core)
                    refreshKey++
                },
            )

            if (core != CoreDownloader.AVAILABLE_CORES.last()) {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CoreRow(
    core: CoreInfo,
    installed: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (installed) AccentGreen.copy(alpha = 0.05f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (installed) AccentGreen.copy(alpha = 0.3f) else CardBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = core.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (installed) "Installed" else "Not installed",
                    color = if (installed) AccentGreen else TextSecondary,
                    fontSize = 11.sp,
                )
            }

            if (isDownloading) {
                // Show nothing — progress bar below handles it
            } else if (installed) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${core.name}",
                        tint = DangerRed,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download ${core.name}",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (isDownloading) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentCyan,
                trackColor = SurfaceDark,
            )
            Text(
                text = "Downloading… ${(progress * 100).toInt()}%",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
