package com.bikearcade.media

import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
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
 * Monitors active media sessions on the device using [MediaSessionManager].
 *
 * Requires notification listener access (the user must grant
 * "Notification access" in system settings for this app).
 *
 * Falls back to polling [AudioManager] playback configurations if
 * notification listener access is not granted.
 */
class MediaSessionMonitor(
    private val context: Context,
    parentScope: CoroutineScope? = null
) {
    private val sessionManager: MediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val _activeController = MutableStateFlow<MediaController?>(null)
    val activeController: StateFlow<MediaController?> = _activeController.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<MediaSessionInfo>>(emptyList())
    val activeSessions: StateFlow<List<MediaSessionInfo>> = _activeSessions.asStateFlow()

    private val _hasListenerAccess = MutableStateFlow(false)
    /** Whether the app has notification listener access for media session monitoring. */
    val hasListenerAccess: StateFlow<Boolean> = _hasListenerAccess.asStateFlow()

    private var isListening = false
    private val scope = parentScope?.let { CoroutineScope(it.coroutineContext + Job(it.coroutineContext[Job])) }
        ?: CoroutineScope(Dispatchers.Main + Job())
    private var pollingJob: Job? = null
    private var mediaBrowser: MediaBrowser? = null

    private val listenerComponent = ComponentName(context, BikeArcadeNotificationListener::class.java)

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            onSessionsChanged(controllers)
        }

    /**
     * Check if our notification listener is enabled in system settings.
     */
    fun isListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val enabled = flat?.contains(listenerComponent.flattenToString()) == true
        _hasListenerAccess.value = enabled
        return enabled
    }

    /**
     * Start listening for active media sessions.
     * If notification listener is not granted, falls back to polling.
     */
    fun startListening() {
        if (isListening) return
        isListening = true

        if (isListenerEnabled()) {
            startWithNotificationAccess()
        } else {
            Log.w(TAG, "Notification listener not enabled, using MediaBrowser fallback")
            startMediaBrowserFallback()
        }
    }

    private fun startWithNotificationAccess() {
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionListener, listenerComponent)
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            onSessionsChanged(controllers)
            Log.i(TAG, "Listening with notification access, found ${controllers.size} sessions")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException despite listener being enabled, falling back to polling", e)
            _hasListenerAccess.value = false
            startMediaBrowserFallback()
        }
    }

    /**
     * Fallback: connect to known music apps via [MediaBrowser].
     * This doesn't require notification listener permission — any app can
     * connect to a MediaBrowserService to get a MediaController.
     */
    private fun startMediaBrowserFallback() {
        Log.i(TAG, "Starting MediaBrowser fallback for music apps")
        // Try each preferred package until one connects
        tryConnectMediaBrowser(PREFERRED_PACKAGES.toList(), 0)

        // Also poll periodically to re-check for notification listener or new sessions
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (isListenerEnabled()) {
                    Log.i(TAG, "Notification listener access detected, switching to event-based")
                    mediaBrowser?.disconnect()
                    mediaBrowser = null
                    startWithNotificationAccess()
                    return@launch
                }
            }
        }
    }

    private fun tryConnectMediaBrowser(packages: List<String>, index: Int) {
        if (index >= packages.size) {
            Log.w(TAG, "No music app MediaBrowserService responded")
            return
        }

        val pkg = packages[index]
        val browserServiceName = BROWSER_SERVICES[pkg]
        if (browserServiceName == null) {
            tryConnectMediaBrowser(packages, index + 1)
            return
        }

        val component = ComponentName(pkg, browserServiceName)
        Log.d(TAG, "Trying MediaBrowser connection to $component")

        mediaBrowser?.disconnect()
        mediaBrowser = MediaBrowser(context, component, object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val browser = mediaBrowser ?: return
                val token = browser.sessionToken
                val controller = MediaController(context, token)
                Log.i(TAG, "MediaBrowser connected to ${controller.packageName}")

                _activeController.value = controller
                _activeSessions.value = listOf(
                    MediaSessionInfo(
                        packageName = controller.packageName,
                        label = getAppLabel(controller.packageName),
                        isPlaying = controller.playbackState?.state ==
                                android.media.session.PlaybackState.STATE_PLAYING
                    )
                )
            }

            override fun onConnectionFailed() {
                Log.d(TAG, "MediaBrowser connection failed for $pkg, trying next")
                tryConnectMediaBrowser(packages, index + 1)
            }

            override fun onConnectionSuspended() {
                Log.d(TAG, "MediaBrowser connection suspended for $pkg")
                _activeController.value = null
            }
        }, null)

        mediaBrowser?.connect()
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        pollingJob?.cancel()
        pollingJob = null
        mediaBrowser?.disconnect()
        mediaBrowser = null

        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing session listener", e)
        }

        _activeController.value = null
        _activeSessions.value = emptyList()
    }

    fun selectSession(packageName: String): Boolean {
        try {
            val component = if (isListenerEnabled()) listenerComponent else null
            val sessions = sessionManager.getActiveSessions(component)
            val target = sessions.firstOrNull { it.packageName == packageName }
            if (target != null) {
                _activeController.value = target
                return true
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot select session: $e")
        }
        return false
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        val sessions = controllers.orEmpty()

        _activeSessions.value = sessions.map { controller ->
            MediaSessionInfo(
                packageName = controller.packageName,
                label = getAppLabel(controller.packageName),
                isPlaying = controller.playbackState?.state ==
                        android.media.session.PlaybackState.STATE_PLAYING
            )
        }

        // Auto-select: prefer a playing session, prioritizing known music apps
        val preferred = sessions.firstOrNull { it.packageName in PREFERRED_PACKAGES &&
                it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull {
                it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            }
            ?: sessions.firstOrNull { it.packageName in PREFERRED_PACKAGES }
            ?: sessions.firstOrNull()

        _activeController.value = preferred

        Log.d(TAG, "Active sessions: ${sessions.size}, " +
                "selected: ${preferred?.packageName ?: "none"}")
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    companion object {
        private const val TAG = "MediaSessionMonitor"
        private const val POLL_INTERVAL_MS = 5000L

        private val PREFERRED_PACKAGES = setOf(
            "com.google.android.apps.youtube.music",
            "com.spotify.music",
            "com.google.android.music",
            "com.amazon.mp3",
            "com.apple.android.music"
        )

        /** Known MediaBrowserService class names for music apps. */
        private val BROWSER_SERVICES = mapOf(
            "com.google.android.apps.youtube.music" to
                "com.google.android.apps.youtube.music.mediabrowser.MusicBrowserService",
            "com.spotify.music" to
                "com.spotify.music.internal.net.mdns.MdnsService", // Spotify uses a different name
            "com.google.android.music" to
                "com.google.android.music.playback.MediaPlaybackService",
        )
    }
}

data class MediaSessionInfo(
    val packageName: String,
    val label: String,
    val isPlaying: Boolean
)

/**
 * Minimal [NotificationListenerService] required for [MediaSessionManager]
 * to grant access to active media sessions.
 */
class BikeArcadeNotificationListener : NotificationListenerService()
