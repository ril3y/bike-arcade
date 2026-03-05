package com.bikearcade.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bikearcade.media.MediaInfo
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.overlay.components.LiveMetricsPanel
import com.bikearcade.overlay.components.NowPlayingCard
import com.bikearcade.overlay.components.PowerCadenceGraphs
import com.bikearcade.overlay.components.RideStatsBar
import com.bikearcade.overlay.components.SystemControlsRow
import com.bikearcade.ui.theme.DarkBackground

/**
 * Main sidebar Composable occupying the right 640x1080 area of the display.
 *
 * Arranges all HUD sections vertically:
 *  1. Now Playing (media controls)
 *  2. Live Metrics (HR, Cadence, Power, Speed)
 *  3. Graphs (Power + Cadence line charts)
 *  4. Ride Stats (Distance, Time, Calories)
 *  5. System Controls (HUD toggle, Settings)
 *
 * @param metrics Current bike telemetry data.
 * @param mediaInfo Currently playing media track information.
 * @param powerHistory Last 60 seconds of power readings.
 * @param cadenceHistory Last 60 seconds of cadence readings.
 * @param calories Estimated calories burned.
 * @param isHudVisible Whether the HUD overlay is currently visible.
 * @param onPlayPause Callback for play/pause media action.
 * @param onSkipNext Callback for skip to next track.
 * @param onSkipPrevious Callback for skip to previous track.
 * @param onToggleHud Callback when HUD visibility is toggled.
 * @param onOpenSettings Callback to open settings screen.
 */
@Composable
fun SidebarOverlay(
    metrics: BikeMetrics,
    mediaInfo: MediaInfo,
    powerHistory: List<Float>,
    cadenceHistory: List<Float>,
    calories: Int,
    isHudVisible: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleHud: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onExitGame: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(640.dp)
            .fillMaxHeight()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // 1. Now Playing
        NowPlayingCard(
            mediaInfo = mediaInfo,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
        )

        Spacer(Modifier.height(12.dp))

        // 2. Live Metrics
        LiveMetricsPanel(metrics = metrics)

        Spacer(Modifier.height(12.dp))

        // 3. Graphs
        PowerCadenceGraphs(
            powerData = powerHistory,
            cadenceData = cadenceHistory,
        )

        Spacer(Modifier.height(12.dp))

        // 4. Ride Stats
        RideStatsBar(
            distanceMiles = metrics.distance,
            elapsedTimeMs = metrics.elapsedTime,
            calories = calories,
        )

        Spacer(Modifier.height(12.dp))

        // 5. System Controls
        SystemControlsRow(
            isHudVisible = isHudVisible,
            onToggleHud = onToggleHud,
            onOpenSettings = onOpenSettings,
            onExitGame = onExitGame,
        )

        Spacer(Modifier.height(8.dp))
    }
}
