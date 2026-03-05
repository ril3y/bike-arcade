package com.bikearcade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.TextPrimary

/**
 * Styled icon button for media controls and system actions.
 * Circular background with a ripple effect, sized for touch targets
 * that remain usable while cycling.
 */
@Composable
fun ArcadeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = TextPrimary,
    backgroundColor: Color = SurfaceBright,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.4f),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Larger variant for primary media actions (Play/Pause).
 */
@Composable
fun ArcadeIconButtonLarge(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = TextPrimary,
    backgroundColor: Color = SurfaceBright,
    enabled: Boolean = true,
) {
    ArcadeIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        iconTint = iconTint,
        backgroundColor = backgroundColor,
        size = 56.dp,
        iconSize = 32.dp,
        enabled = enabled,
    )
}
