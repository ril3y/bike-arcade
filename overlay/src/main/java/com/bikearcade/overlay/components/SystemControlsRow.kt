package com.bikearcade.overlay.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bikearcade.ui.components.ArcadeIconButton
import com.bikearcade.ui.theme.DarkBackground
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.SettingsGear
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.TextSecondary
import com.bikearcade.ui.theme.ToggleOffTrack
import com.bikearcade.ui.theme.ToggleOnTrack

/**
 * Row with HUD visibility toggle and Settings gear icon button.
 */
@Composable
fun SystemControlsRow(
    isHudVisible: Boolean,
    onToggleHud: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // HUD toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcadeIconButton(
                icon = if (isHudVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isHudVisible) "HUD visible" else "HUD hidden",
                onClick = { onToggleHud(!isHudVisible) },
                iconTint = if (isHudVisible) NeonAccent else TextSecondary,
                size = 40.dp,
                iconSize = 20.dp,
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "HUD",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = isHudVisible,
                onCheckedChange = onToggleHud,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonAccent,
                    checkedTrackColor = ToggleOnTrack.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = ToggleOffTrack,
                    uncheckedBorderColor = ToggleOffTrack,
                ),
            )
        }

        // Settings button
        ArcadeIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
            iconTint = SettingsGear,
            backgroundColor = SurfaceBright,
        )
    }
}
