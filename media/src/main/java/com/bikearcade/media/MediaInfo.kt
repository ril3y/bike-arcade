package com.bikearcade.media

import android.graphics.Bitmap

/**
 * Snapshot of the currently active media session state.
 *
 * @property trackName Display name of the current track.
 * @property artistName Artist or channel name.
 * @property albumArt Album artwork bitmap, or null if unavailable.
 * @property isPlaying Whether audio is currently playing.
 * @property duration Total track duration in milliseconds.
 * @property position Current playback position in milliseconds.
 */
data class MediaInfo(
    val trackName: String = "",
    val artistName: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val position: Long = 0L
) {
    companion object {
        /** Empty state with no track information. */
        val EMPTY = MediaInfo()
    }

    /** Whether any meaningful track info is present. */
    val hasTrack: Boolean
        get() = trackName.isNotBlank()

    /** Formatted position string (mm:ss). */
    val positionFormatted: String
        get() = formatTime(position)

    /** Formatted duration string (mm:ss). */
    val durationFormatted: String
        get() = formatTime(duration)

    /** Progress as a fraction in [0, 1]. */
    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
