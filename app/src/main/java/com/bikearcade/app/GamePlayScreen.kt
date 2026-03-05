package com.bikearcade.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bikearcade.emulator.EmulatorSurface
import com.bikearcade.overlay.SidebarOverlay

/**
 * Main game-playing screen: emulator surface (left 2/3) + sidebar HUD (right 1/3).
 *
 * Tapping the emulator surface pauses and shows save/load/reset/exit actions.
 */
@Composable
fun GamePlayScreen(viewModel: MainViewModel) {
    val bikeMetrics by viewModel.bikeMetrics.collectAsState()
    val mediaInfo by viewModel.mediaInfo.collectAsState()
    val powerHistory by viewModel.powerHistory.collectAsState()
    val cadenceHistory by viewModel.cadenceHistory.collectAsState()
    val calories by viewModel.calories.collectAsState()
    val isHudVisible by viewModel.isHudVisible.collectAsState()
    val isGameMuted by viewModel.isGameMuted.collectAsState()

    val app = BikeArcadeApplication.instance
    var isPaused by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Emulator surface: left 2/3 (1280 of 1920)
        Box(
            modifier = Modifier
                .weight(2f)
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
            )
        }
    }
}
