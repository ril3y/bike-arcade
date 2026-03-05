package com.bikearcade.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dark theme colors consistent with the app
private val BackgroundDark = Color(0xFF0D0D1A)
private val SurfaceDark = Color(0xFF1A1A2E)
private val AccentBlue = Color(0xFF4FC3F7)
private val AccentGreen = Color(0xFF66BB6A)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val DividerColor = Color(0xFF2A2A3E)

/**
 * Full settings screen with sliders, toggles, dropdowns, and number inputs.
 *
 * @param repository The [SettingsRepository] providing and persisting settings.
 * @param onClose Callback invoked when the user closes the settings screen.
 */
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onClose: () -> Unit = {}
) {
    val settings by repository.settings.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceDark,
                        contentColor = TextPrimary
                    )
                ) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Display")

            // Game Scale slider
            SettingsSlider(
                label = "Game Scale",
                value = settings.gameScale,
                valueRange = AppSettings.GAME_SCALE_RANGE,
                valueLabel = "%.1fx".format(settings.gameScale),
                onValueChange = { repository.update { copy(gameScale = it) } }
            )

            // Sidebar Width slider
            SettingsSlider(
                label = "Sidebar Width",
                value = settings.sidebarWidth.toFloat(),
                valueRange = AppSettings.SIDEBAR_WIDTH_RANGE.first.toFloat()..
                        AppSettings.SIDEBAR_WIDTH_RANGE.last.toFloat(),
                valueLabel = "${settings.sidebarWidth} px",
                steps = (AppSettings.SIDEBAR_WIDTH_RANGE.last - AppSettings.SIDEBAR_WIDTH_RANGE.first) / 20 - 1,
                onValueChange = { repository.update { copy(sidebarWidth = it.toInt()) } }
            )

            // HUD Opacity slider
            SettingsSlider(
                label = "HUD Opacity",
                value = settings.hudOpacity,
                valueRange = AppSettings.HUD_OPACITY_RANGE,
                valueLabel = "${(settings.hudOpacity * 100).toInt()}%",
                onValueChange = { repository.update { copy(hudOpacity = it) } }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Metrics")

            // Metric/Imperial toggle
            SettingsToggle(
                label = "Use Metric Units",
                description = if (settings.useMetricUnits) "km/h, km" else "mph, miles",
                checked = settings.useMetricUnits,
                onCheckedChange = { repository.update { copy(useMetricUnits = it) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Source dropdown
            MetricsSourceDropdown(
                selected = settings.metricsSource,
                onSelected = { repository.update { copy(metricsSource = it) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // TCP Port (shown only when TCP source is selected)
            if (settings.metricsSource == MetricsSourceType.TCP) {
                TcpPortInput(
                    port = settings.tcpPort,
                    onPortChanged = { port ->
                        if (port in AppSettings.TCP_PORT_RANGE) {
                            repository.update { copy(tcpPort = port) }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Smoothing Alpha slider
            SettingsSlider(
                label = "Smoothing Factor",
                value = settings.smoothingAlpha,
                valueRange = AppSettings.SMOOTHING_ALPHA_RANGE,
                valueLabel = "%.2f".format(settings.smoothingAlpha),
                onValueChange = { repository.update { copy(smoothingAlpha = it) } }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Controller")

            val context = LocalContext.current
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pair Bluetooth Controller")
            }
            Text(
                text = "Opens Android Bluetooth settings to pair an SNES/game controller",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Reset to defaults
            Button(
                onClick = { repository.resetToDefaults() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B0000),
                    contentColor = Color.White
                )
            ) {
                Text("Reset to Defaults")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = AccentBlue,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor)
    )
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = TextPrimary, fontSize = 16.sp)
            Text(text = valueLabel, color = AccentGreen, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = SurfaceDark
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, color = TextPrimary, fontSize = 16.sp)
            Text(text = description, color = TextSecondary, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentBlue,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = SurfaceDark,
                uncheckedThumbColor = TextSecondary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricsSourceDropdown(
    selected: MetricsSourceType,
    onSelected: (MetricsSourceType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Metrics Source",
            color = TextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selected.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DividerColor,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MetricsSourceType.entries.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.displayName, color = TextPrimary) },
                        onClick = {
                            onSelected(source)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TcpPortInput(
    port: Int,
    onPortChanged: (Int) -> Unit
) {
    var text by remember(port) { mutableStateOf(port.toString()) }
    var isError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "TCP Port",
            color = TextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue
                    val parsed = newValue.toIntOrNull()
                    if (parsed != null && parsed in AppSettings.TCP_PORT_RANGE) {
                        isError = false
                        onPortChanged(parsed)
                    } else {
                        isError = newValue.isNotEmpty()
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isError,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DividerColor,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    errorBorderColor = Color(0xFFEF5350)
                ),
                modifier = Modifier.width(160.dp)
            )
            if (isError) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "1024-65535",
                    color = Color(0xFFEF5350),
                    fontSize = 12.sp
                )
            }
        }
    }
}
