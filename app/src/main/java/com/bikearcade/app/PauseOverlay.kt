package com.bikearcade.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SlotEmpty = Color(0xFF2A2A3E)
private val SlotFilled = Color(0xFF4FC3F7)
private val SlotSelected = Color(0xFFBB86FC)
private val PanelBg = Color(0xFF1A1A2E)

/**
 * Touch-to-pause overlay for the emulator surface.
 *
 * Tap to pause — shows a dimmed overlay with save slots and action buttons.
 * Tap anywhere on the dimmed background to resume.
 */
@Composable
fun PauseOverlay(
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSaveState: (Int) -> Boolean,
    onLoadState: (Int) -> Boolean,
    hasStateFile: (Int) -> Boolean,
    onReset: () -> Unit,
    onExit: () -> Unit,
    isGameMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    gameVolume: Float = 1f,
    onVolumeChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showPlayIcon by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var selectedSlot by remember { mutableIntStateOf(0) }
    var showExitConfirm by remember { mutableStateOf(false) }
    // Track which slots have files — refreshed each time pause is triggered
    var slotStates by remember { mutableStateOf(BooleanArray(5) { false }) }

    // Refresh slot states when paused
    LaunchedEffect(isPaused) {
        if (isPaused) {
            slotStates = BooleanArray(5) { hasStateFile(it) }
        }
    }

    // Auto-fade the play icon after resuming
    LaunchedEffect(showPlayIcon) {
        if (showPlayIcon) {
            delay(600)
            showPlayIcon = false
        }
    }

    // Auto-fade status messages
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(1500)
            statusMessage = null
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Touch layer — tap to pause when playing
        if (!isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onPause() },
            )
        }

        // Play icon animation on resume
        val playAlpha by animateFloatAsState(
            targetValue = if (showPlayIcon) 0.7f else 0f,
            animationSpec = tween(durationMillis = if (showPlayIcon) 100 else 400),
            label = "playAlpha",
        )
        if (playAlpha > 0f) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Resumed",
                tint = Color.White.copy(alpha = playAlpha),
                modifier = Modifier.size(96.dp),
            )
        }

        // Pause overlay
        val pauseAlpha by animateFloatAsState(
            targetValue = if (isPaused) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "pauseAlpha",
        )
        if (pauseAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * pauseAlpha))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        onResume()
                        showPlayIcon = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelBg.copy(alpha = 0.95f * pauseAlpha))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* Consume clicks so they don't resume */ }
                        .padding(28.dp),
                ) {
                    // Pause icon + title
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Paused",
                        tint = Color.White.copy(alpha = 0.8f * pauseAlpha),
                        modifier = Modifier.size(48.dp),
                    )

                    // Save slot selector
                    Text(
                        text = "SAVE SLOTS",
                        color = Color.White.copy(alpha = 0.6f * pauseAlpha),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (slot in 0 until 5) {
                            val isFilled = slotStates[slot]
                            val isSelected = slot == selectedSlot
                            SaveSlotChip(
                                slot = slot + 1,
                                isFilled = isFilled,
                                isSelected = isSelected,
                                alpha = pauseAlpha,
                                onClick = { selectedSlot = slot },
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PauseActionButton(
                            icon = Icons.Default.SaveAlt,
                            label = "Save",
                            alpha = pauseAlpha,
                            onClick = {
                                val ok = onSaveState(selectedSlot)
                                if (ok) {
                                    slotStates = BooleanArray(5) { hasStateFile(it) }
                                    statusMessage = "Saved to slot ${selectedSlot + 1}"
                                } else {
                                    statusMessage = "Save failed"
                                }
                            },
                        )
                        PauseActionButton(
                            icon = Icons.Default.Upload,
                            label = "Load",
                            alpha = pauseAlpha,
                            enabled = slotStates.getOrElse(selectedSlot) { false },
                            onClick = {
                                val ok = onLoadState(selectedSlot)
                                statusMessage = if (ok) "Loaded slot ${selectedSlot + 1}" else "Load failed"
                            },
                        )
                        PauseActionButton(
                            icon = Icons.Default.Refresh,
                            label = "Reset",
                            alpha = pauseAlpha,
                            onClick = {
                                onReset()
                                statusMessage = "Game reset"
                            },
                        )
                        PauseActionButton(
                            icon = if (isGameMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            label = if (isGameMuted) "Unmute" else "Mute",
                            tint = if (isGameMuted) Color(0xFFE53935) else Color.White,
                            alpha = pauseAlpha,
                            onClick = onToggleMute,
                        )
                        PauseActionButton(
                            icon = Icons.Default.PlayArrow,
                            label = "Resume",
                            tint = Color(0xFF4FC3F7),
                            alpha = pauseAlpha,
                            onClick = {
                                onResume()
                                showPlayIcon = true
                            },
                        )
                        PauseActionButton(
                            icon = Icons.Default.ExitToApp,
                            label = "Exit",
                            tint = Color(0xFFE53935),
                            alpha = pauseAlpha,
                            onClick = { showExitConfirm = true },
                        )
                    }

                    // Volume slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.widthIn(max = 280.dp),
                    ) {
                        Icon(
                            imageVector = if (isGameMuted || gameVolume < 0.01f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White.copy(alpha = 0.6f * pauseAlpha),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onToggleMute),
                        )
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = if (isGameMuted) 0f else gameVolume,
                            onValueChange = onVolumeChange,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4FC3F7).copy(alpha = pauseAlpha),
                                activeTrackColor = Color(0xFF4FC3F7).copy(alpha = 0.6f * pauseAlpha),
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f * pauseAlpha),
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(gameVolume * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.5f * pauseAlpha),
                            fontSize = 11.sp,
                        )
                    }

                    // Status message
                    val msgAlpha by animateFloatAsState(
                        targetValue = if (statusMessage != null) 1f else 0f,
                        animationSpec = tween(200),
                        label = "msgAlpha",
                    )
                    if (msgAlpha > 0f) {
                        Text(
                            text = statusMessage ?: "",
                            color = Color.White.copy(alpha = msgAlpha),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        // Reserve space so layout doesn't jump
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }

        // Exit confirmation dialog
        if (showExitConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                containerColor = Color(0xFF1A1A2E),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFB0B0B0),
                title = { Text("Exit Game?", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your workout stats will be saved if you've been riding for at least 1 minute.")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tip: Save your game state first so you can continue later!",
                            color = Color(0xFF4FC3F7),
                            fontSize = 13.sp,
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Save & Exit button
                        androidx.compose.material3.TextButton(onClick = {
                            // Save to the currently selected slot, then exit
                            onSaveState(selectedSlot)
                            showExitConfirm = false
                            onExit()
                        }) {
                            Text("Save & Exit", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
                        }
                        // Exit without saving
                        androidx.compose.material3.TextButton(onClick = {
                            showExitConfirm = false
                            onExit()
                        }) {
                            Text("Exit", color = Color(0xFFE53935))
                        }
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showExitConfirm = false }) {
                        Text("Cancel", color = Color(0xFF9E9E9E))
                    }
                },
            )
        }
    }
}

@Composable
private fun SaveSlotChip(
    slot: Int,
    isFilled: Boolean,
    isSelected: Boolean,
    alpha: Float,
    onClick: () -> Unit,
) {
    val bgColor = when {
        isSelected -> SlotSelected.copy(alpha = 0.3f * alpha)
        isFilled -> SlotFilled.copy(alpha = 0.15f * alpha)
        else -> SlotEmpty.copy(alpha = 0.5f * alpha)
    }
    val borderColor = when {
        isSelected -> SlotSelected.copy(alpha = 0.8f * alpha)
        isFilled -> SlotFilled.copy(alpha = 0.4f * alpha)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> Color.White.copy(alpha = alpha)
        isFilled -> SlotFilled.copy(alpha = 0.9f * alpha)
        else -> Color.White.copy(alpha = 0.4f * alpha)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "$slot",
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (isFilled) "used" else "empty",
            color = textColor.copy(alpha = 0.6f),
            fontSize = 9.sp,
        )
    }
}

@Composable
fun PauseActionButton(
    icon: ImageVector,
    label: String,
    alpha: Float = 1f,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.3f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = effectiveTint.copy(alpha = alpha * effectiveTint.alpha),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = label,
            color = effectiveTint.copy(alpha = 0.8f * alpha * effectiveTint.alpha),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
