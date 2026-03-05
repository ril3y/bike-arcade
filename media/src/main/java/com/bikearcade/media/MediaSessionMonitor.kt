package com.bikearcade.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors active media sessions on the device using [MediaSessionManager].
 *
 * Requires notification listener access (the user must grant
 * "Notification access" in system settings for this app).
 *
 * Detects sessions from Spotify, YouTube Music, and other media apps,
 * then exposes the active [MediaController] for use by [BikeArcadeMediaController].
 */
class MediaSessionMonitor(
    private val context: Context
) {
    private val sessionManager: MediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val _activeController = MutableStateFlow<MediaController?>(null)
    /** The currently active media controller, or null if none found. */
    val activeController: StateFlow<MediaController?> = _activeController.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<MediaSessionInfo>>(emptyList())
    /** List of all detected active media sessions. */
    val activeSessions: StateFlow<List<MediaSessionInfo>> = _activeSessions.asStateFlow()

    private var isListening = false

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            onSessionsChanged(controllers)
        }

    /**
     * Start listening for active media sessions.
     *
     * This requires that the app has been granted notification listener
     * access. The [NotificationListenerService] component name must match
     * the one declared in the app manifest.
     */
    fun startListening() {
        if (isListening) return
        isListening = true

        val listenerComponent = ComponentName(context, BikeArcadeNotificationListener::class.java)

        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionListener, listenerComponent)

            // Immediately check for existing sessions
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            onSessionsChanged(controllers)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification listener permission not granted. " +
                    "Media session monitoring requires notification access.", e)
        }
    }

    /** Stop listening for session changes. */
    fun stopListening() {
        if (!isListening) return
        isListening = false

        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing session listener", e)
        }

        _activeController.value = null
        _activeSessions.value = emptyList()
    }

    /**
     * Select a specific session by package name.
     * Returns true if the session was found and selected.
     */
    fun selectSession(packageName: String): Boolean {
        val sessions = sessionManager.getActiveSessions(
            ComponentName(context, BikeArcadeNotificationListener::class.java)
        )
        val target = sessions.firstOrNull { it.packageName == packageName }
        if (target != null) {
            _activeController.value = target
            return true
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

        /** Package names of preferred music apps, in priority order. */
        private val PREFERRED_PACKAGES = setOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.music",
            "com.amazon.mp3",
            "com.apple.android.music"
        )
    }
}

/**
 * Summary info for an active media session.
 */
data class MediaSessionInfo(
    val packageName: String,
    val label: String,
    val isPlaying: Boolean
)

/**
 * Minimal [NotificationListenerService] required for [MediaSessionManager]
 * to grant access to active media sessions.
 *
 * Declare in AndroidManifest.xml:
 * ```xml
 * <service
 *     android:name="com.bikearcade.media.BikeArcadeNotificationListener"
 *     android:label="BikeArcade Media Monitor"
 *     android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="android.service.notification.NotificationListenerService" />
 *     </intent-filter>
 * </service>
 * ```
 */
class BikeArcadeNotificationListener : NotificationListenerService() {
    // No custom behavior needed. This service's existence is required
    // by MediaSessionManager to authorize session access.
}
