package com.bikearcade.media

import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wraps the Android MediaSession/MediaController APIs to provide a reactive
 * interface for controlling and monitoring external media playback (Spotify,
 * YouTube Music, local players, etc.).
 *
 * Requires an active [MediaController] obtained from [MediaSessionMonitor].
 */
class BikeArcadeMediaController(
    @Suppress("unused") private val context: Context
) {
    private val _mediaInfo = MutableStateFlow(MediaInfo.EMPTY)
    /** Current media session state, updated in real-time. */
    val mediaInfo: StateFlow<MediaInfo> = _mediaInfo.asStateFlow()

    private var activeController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var positionPollingJob: Job? = null

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateFromController()
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            updateFromController()
        }

        override fun onSessionDestroyed() {
            detach()
        }
    }

    /**
     * Attach to an active [MediaController] to start receiving updates.
     * Replaces any previously attached controller.
     */
    fun attach(controller: MediaController) {
        detach()
        activeController = controller
        controller.registerCallback(mediaCallback, mainHandler)
        updateFromController()
        startPositionPolling()
    }

    /**
     * Detach from the current controller and stop updates.
     */
    fun detach() {
        positionPollingJob?.cancel()
        positionPollingJob = null
        try {
            activeController?.unregisterCallback(mediaCallback)
        } catch (_: IllegalArgumentException) {
            // Callback was not registered; safe to ignore.
        }
        activeController = null
        _mediaInfo.value = MediaInfo.EMPTY
    }

    /** Resume or start playback. */
    fun play() {
        activeController?.transportControls?.play()
    }

    /** Pause playback. */
    fun pause() {
        activeController?.transportControls?.pause()
    }

    /** Toggle play/pause based on current state. */
    fun togglePlayPause() {
        if (_mediaInfo.value.isPlaying) pause() else play()
    }

    /** Skip to the next track. */
    fun skipNext() {
        activeController?.transportControls?.skipToNext()
    }

    /** Skip to the previous track. */
    fun skipPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    /** Seek to a specific position in milliseconds. */
    fun seekTo(positionMs: Long) {
        activeController?.transportControls?.seekTo(positionMs)
    }

    /** Release all resources. Call when the controller is no longer needed. */
    fun release() {
        detach()
    }

    private fun updateFromController() {
        val controller = activeController ?: return
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val trackName = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artistName = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val albumArt = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
        val duration = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        _mediaInfo.value = MediaInfo(
            trackName = trackName,
            artistName = artistName,
            albumArt = albumArt,
            isPlaying = isPlaying,
            duration = duration,
            position = position
        )
    }

    /**
     * Polls playback position periodically while playing, since Android
     * MediaSession does not push position updates continuously.
     */
    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                delay(POSITION_POLL_INTERVAL_MS)
                val controller = activeController ?: break
                val playbackState = controller.playbackState
                if (playbackState != null && playbackState.state == PlaybackState.STATE_PLAYING) {
                    val elapsed = android.os.SystemClock.elapsedRealtime() -
                            playbackState.lastPositionUpdateTime
                    val estimatedPosition =
                        playbackState.position + (elapsed * playbackState.playbackSpeed).toLong()
                    _mediaInfo.value = _mediaInfo.value.copy(
                        position = estimatedPosition.coerceAtLeast(0L)
                    )
                }
            }
        }
    }

    companion object {
        /** How often to poll for playback position updates (ms). */
        private const val POSITION_POLL_INTERVAL_MS = 1000L
    }
}
