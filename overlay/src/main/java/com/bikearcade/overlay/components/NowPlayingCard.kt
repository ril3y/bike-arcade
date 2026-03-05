package com.bikearcade.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bikearcade.media.MediaInfo
import com.bikearcade.ui.components.ArcadeIconButton
import com.bikearcade.ui.components.ArcadeIconButtonLarge
import com.bikearcade.ui.theme.MediaActive
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.SurfaceBorder
import com.bikearcade.ui.theme.TextPrimary
import com.bikearcade.ui.theme.TextSecondary

/**
 * Now Playing card showing current track info with media controls.
 * Track name uses marquee scrolling if too long.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingCard(
    mediaInfo: MediaInfo,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = SurfaceBright,
                shape = MaterialTheme.shapes.large,
            )
            .border(
                width = 1.dp,
                color = SurfaceBorder,
                shape = MaterialTheme.shapes.large,
            )
            .padding(16.dp),
    ) {
        // Section header
        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.titleMedium,
            color = NeonAccent,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Album art placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(SurfaceBorder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = "Album art",
                    tint = TextSecondary,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaInfo.trackName.ifBlank { "No track" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (mediaInfo.trackName.length > 25) {
                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    } else {
                        Modifier
                    },
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = mediaInfo.artistName.ifBlank { "Unknown artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Media controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArcadeIconButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                onClick = onSkipPrevious,
                iconTint = MediaActive,
            )

            Spacer(Modifier.width(16.dp))

            ArcadeIconButtonLarge(
                icon = if (mediaInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (mediaInfo.isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
                iconTint = NeonAccent,
            )

            Spacer(Modifier.width(16.dp))

            ArcadeIconButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next track",
                onClick = onSkipNext,
                iconTint = MediaActive,
            )
        }
    }
}
