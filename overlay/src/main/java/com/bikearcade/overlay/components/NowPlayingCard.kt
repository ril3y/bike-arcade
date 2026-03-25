package com.bikearcade.overlay.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
 * Now Playing card with album art, track info, progress bar, and media controls.
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
            .background(SurfaceBright, MaterialTheme.shapes.large)
            .border(1.dp, SurfaceBorder, MaterialTheme.shapes.large)
            .padding(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Album art — show real bitmap if available
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(SurfaceBorder),
                contentAlignment = Alignment.Center,
            ) {
                val art = mediaInfo.albumArt
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = "Album art",
                        tint = TextSecondary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Track info + controls
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaInfo.trackName.ifBlank { "No track" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (mediaInfo.trackName.length > 25) {
                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    } else {
                        Modifier
                    },
                )
                Text(
                    text = mediaInfo.artistName.ifBlank { "Unknown artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Progress bar
                if (mediaInfo.duration > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = mediaInfo.positionFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        LinearProgressIndicator(
                            progress = { mediaInfo.progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .padding(horizontal = 6.dp),
                            color = NeonAccent,
                            trackColor = SurfaceBorder,
                        )
                        Text(
                            text = mediaInfo.durationFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // Media controls
            ArcadeIconButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                onClick = onSkipPrevious,
                iconTint = MediaActive,
                size = 32.dp,
                iconSize = 18.dp,
            )
            ArcadeIconButtonLarge(
                icon = if (mediaInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (mediaInfo.isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
                iconTint = NeonAccent,
            )
            ArcadeIconButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next",
                onClick = onSkipNext,
                iconTint = MediaActive,
                size = 32.dp,
                iconSize = 18.dp,
            )
        }
    }
}
