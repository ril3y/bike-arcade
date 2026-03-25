package com.bikearcade.overlay.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bikearcade.ui.components.ArcadeIconButton
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.SettingsGear
import com.bikearcade.ui.theme.SurfaceBright

/**
 * Row with Hide HUD (eye), Settings gear, and Exit button.
 */
@Composable
fun SystemControlsRow(
    isHudVisible: Boolean,
    onToggleHud: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onExitGame: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hide HUD — just the eye icon
        ArcadeIconButton(
            icon = Icons.Default.VisibilityOff,
            contentDescription = "Hide HUD",
            onClick = { onToggleHud(false) },
            iconTint = NeonAccent,
            size = 40.dp,
            iconSize = 20.dp,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Exit game button
            ArcadeIconButton(
                icon = Icons.Default.ExitToApp,
                contentDescription = "Exit Game",
                onClick = onExitGame,
                iconTint = com.bikearcade.ui.theme.HeartRateRed,
                backgroundColor = SurfaceBright,
            )

            Spacer(Modifier.width(8.dp))

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
}
