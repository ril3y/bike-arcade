package com.bikearcade.overlay

/**
 * Data class representing the currently playing media track.
 */
data class MediaInfo(
    val trackName: String = "",
    val artist: String = "",
    val albumArtUri: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
) {
    companion object {
        val EMPTY = MediaInfo()
    }

    val hasTrack: Boolean get() = trackName.isNotBlank()
}
