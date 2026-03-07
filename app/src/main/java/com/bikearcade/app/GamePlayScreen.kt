package com.bikearcade.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikearcade.emulator.EmulatorSurface
import com.bikearcade.overlay.SidebarOverlay
import com.bikearcade.overlay.components.EffortComplianceBar

/**
 * Main game-playing screen: emulator surface (left 2/3) + sidebar HUD (right 1/3).
 *
 * Tapping the emulator surface pauses and shows save/load/reset/exit actions.
 */
@Composable
fun GamePlayScreen(viewModel: MainViewModel) {
    val bikeMetrics by viewModel.bikeMetrics.collectAsStateWithLifecycle()
    val mediaInfo by viewModel.mediaInfo.collectAsStateWithLifecycle()
    val powerHistory by viewModel.powerHistory.collectAsStateWithLifecycle()
    val cadenceHistory by viewModel.cadenceHistory.collectAsStateWithLifecycle()
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val isHudVisible by viewModel.isHudVisible.collectAsStateWithLifecycle()
    val isGameMuted by viewModel.isGameMuted.collectAsStateWithLifecycle()
    val activeWorkout by viewModel.activeWorkout.collectAsStateWithLifecycle()
    val workoutElapsed by viewModel.workoutElapsedSeconds.collectAsStateWithLifecycle()
    val workoutTimeRemaining by viewModel.workoutTimeRemaining.collectAsStateWithLifecycle()
    val currentSegment by viewModel.currentSegmentName.collectAsStateWithLifecycle()
    val resistanceHistory by viewModel.resistanceHistory.collectAsStateWithLifecycle()
    val powerHistoryWorkout by viewModel.powerHistoryWorkout.collectAsStateWithLifecycle()
    val targetPowerPerSegment by viewModel.targetPowerPerSegment.collectAsStateWithLifecycle()
    val difficultyMultiplier by viewModel.difficultyMultiplier.collectAsStateWithLifecycle()
    val gameVolume by viewModel.gameVolume.collectAsStateWithLifecycle()
    val leanAngle by viewModel.leanAngle.collectAsStateWithLifecycle()
    val effortCompliance by viewModel.effortCompliance.collectAsStateWithLifecycle()

    val app = BikeArcadeApplication.instance
    var isPaused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Emulator surface: expands to full width when HUD hidden
            Box(
                modifier = Modifier
                    .weight(if (isHudVisible) 2f else 1f)
                    .fillMaxHeight(),
            ) {
                EmulatorSurface(
                    core = app.container.emulatorCore,
                    modifier = Modifier.fillMaxSize()
                )

                PauseOverlay(
                    isPaused = isPaused,
                    onPause = {
                        viewModel.pauseEmulation()
                        isPaused = true
                    },
                    onResume = {
                        viewModel.resumeEmulation()
                        isPaused = false
                    },
                    onSaveState = { slot -> viewModel.saveState(slot) },
                    onLoadState = { slot -> viewModel.loadState(slot) },
                    hasStateFile = { slot -> viewModel.hasStateFile(slot) },
                    onReset = { viewModel.resetGame() },
                    onExit = {
                        isPaused = false
                        viewModel.stopGame()
                    },
                    isGameMuted = isGameMuted,
                    onToggleMute = viewModel::toggleGameMute,
                    gameVolume = gameVolume,
                    onVolumeChange = viewModel::setGameVolume,
                )

                // Pedal-pause overlay: countdown + paused state
                val isPedalPaused by viewModel.isPedalPaused.collectAsState()
                PedalPauseOverlay(
                    countdown = effortCompliance.pauseCountdown,
                    isPedalPaused = isPedalPaused,
                )
            }

            // Sidebar overlay: right 1/3 (640 of 1920)
            if (isHudVisible) {
                SidebarOverlay(
                    metrics = bikeMetrics,
                    mediaInfo = mediaInfo,
                    powerHistory = powerHistory,
                    cadenceHistory = cadenceHistory,
                    calories = calories,
                    isHudVisible = isHudVisible,
                    onPlayPause = viewModel::onPlayPause,
                    onSkipNext = viewModel::onSkipNext,
                    onSkipPrevious = viewModel::onSkipPrevious,
                    onToggleHud = viewModel::toggleHud,
                    onOpenSettings = viewModel::openSettings,
                    onExitGame = viewModel::stopGame,
                    workoutName = activeWorkout?.name ?: "",
                    workoutSegment = currentSegment,
                    workoutTimeRemaining = workoutTimeRemaining,
                    workoutElapsed = workoutElapsed,
                    workoutTotalSeconds = activeWorkout?.totalDurationSeconds ?: 0,
                    workoutSegments = activeWorkout?.segments ?: emptyList(),
                    resistanceHistory = resistanceHistory,
                    powerHistoryWorkout = powerHistoryWorkout,
                    targetPowerPerSegment = targetPowerPerSegment,
                    difficultyMultiplier = difficultyMultiplier,
                    onAdjustDifficulty = viewModel::adjustDifficulty,
                    leanAngle = leanAngle,
                    effortCompliance = effortCompliance,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        // When HUD hidden: show eye icon + effort compliance bar on the right edge
        if (!isHudVisible) {
            val hasWorkout = (activeWorkout?.segments ?: emptyList()).isNotEmpty()

            // Effort bar stays visible during workouts even with HUD hidden
            if (hasWorkout) {
                EffortComplianceBar(
                    compliance = effortCompliance,
                    leanAngle = leanAngle,
                    heartRate = bikeMetrics.heartRate,
                    showHeartRate = true,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }

            // Show HUD toggle icon in top-right corner
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Show HUD",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (hasWorkout) 60.dp else 12.dp, 12.dp, 12.dp, 12.dp)
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { viewModel.toggleHud(true) }
            )
        }
    }
}

/**
 * Overlay shown on the emulator surface during pedal-pause countdown and paused state.
 * - During countdown (5..1): large number + "SLOWING DOWN..." text
 * - When paused: "PAUSED" + "PEDAL TO RESUME" text
 */
@Composable
private fun PedalPauseOverlay(
    countdown: Int,
    isPedalPaused: Boolean,
) {
    val showCountdown = countdown in 1..5
    val showPaused = isPedalPaused && !showCountdown

    if (!showCountdown && !showPaused) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (showPaused) 0.6f else 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showCountdown) {
                // Large countdown number
                Text(
                    text = "$countdown",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF3B5C),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "KEEP PEDALING",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Paused state
                Text(
                    text = "PAUSED",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "PEDAL TO RESUME",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FFCC),
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
