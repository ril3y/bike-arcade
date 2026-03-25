package com.bikearcade.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists [AppSettings] using [SharedPreferences].
 *
 * Exposes a [StateFlow] of the current settings and provides methods to
 * update individual fields or replace the entire settings object.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init { migrate() }

    private val _settings = MutableStateFlow(load())
    /** Current settings, updated on every save. */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Run one-time migrations for settings changes between versions. */
    private fun migrate() {
        val version = prefs.getInt(KEY_SETTINGS_VERSION, 0)
        if (version < 4) {
            // v4: Removed DIRECT source. New ordinals: SESSION=0, BLE=1.
            //   v3 ordinals: SESSION=0, BLE=1, DIRECT=2.
            //   Pre-v3 ordinals: BLE=0, TCP=1, LOGCAT=2, DIRECT=3, AIDL=4.
            //   Map BLE to BLE(1), everything else to SESSION(0).
            val source = prefs.getInt(KEY_METRICS_SOURCE, -1)
            val newSource = when {
                version >= 3 && source == 1 -> 1  // v3 BLE(1) → BLE(1)
                version < 3 && source == 0 -> 1   // pre-v3 BLE(0) → BLE(1)
                else -> 0                          // everything else → SESSION(0)
            }
            prefs.edit()
                .putInt(KEY_METRICS_SOURCE, newSource)
                .putInt(KEY_SETTINGS_VERSION, 4)
                .apply()
        }
    }

    /** Load all settings from SharedPreferences. */
    fun load(): AppSettings {
        return AppSettings(
            gameScale = prefs.getFloat(KEY_GAME_SCALE, AppSettings.DEFAULT_GAME_SCALE),
            sidebarWidth = prefs.getInt(KEY_SIDEBAR_WIDTH, AppSettings.DEFAULT_SIDEBAR_WIDTH),
            hudOpacity = prefs.getFloat(KEY_HUD_OPACITY, AppSettings.DEFAULT_HUD_OPACITY),
            useMetricUnits = prefs.getBoolean(KEY_USE_METRIC, AppSettings.DEFAULT_USE_METRIC),
            metricsSource = MetricsSourceType.fromOrdinal(
                prefs.getInt(KEY_METRICS_SOURCE, AppSettings.DEFAULT_METRICS_SOURCE.ordinal)
            ),
            tcpPort = prefs.getInt(KEY_TCP_PORT, AppSettings.DEFAULT_TCP_PORT),
            smoothingAlpha = prefs.getFloat(KEY_SMOOTHING_ALPHA, AppSettings.DEFAULT_SMOOTHING_ALPHA),
            screenSaverTimeoutMinutes = prefs.getInt(KEY_SCREEN_SAVER_TIMEOUT, AppSettings.DEFAULT_SCREEN_SAVER_TIMEOUT),
            screenSaverStyle = ScreenSaverStyle.fromOrdinal(
                prefs.getInt(KEY_SCREEN_SAVER_STYLE, AppSettings.DEFAULT_SCREEN_SAVER_STYLE.ordinal)
            )
        )
    }

    /** Persist the full [AppSettings] object. */
    fun save(settings: AppSettings) {
        prefs.edit()
            .putFloat(KEY_GAME_SCALE, settings.gameScale)
            .putInt(KEY_SIDEBAR_WIDTH, settings.sidebarWidth)
            .putFloat(KEY_HUD_OPACITY, settings.hudOpacity)
            .putBoolean(KEY_USE_METRIC, settings.useMetricUnits)
            .putInt(KEY_METRICS_SOURCE, settings.metricsSource.ordinal)
            .putInt(KEY_TCP_PORT, settings.tcpPort)
            .putFloat(KEY_SMOOTHING_ALPHA, settings.smoothingAlpha)
            .putInt(KEY_SCREEN_SAVER_TIMEOUT, settings.screenSaverTimeoutMinutes)
            .putInt(KEY_SCREEN_SAVER_STYLE, settings.screenSaverStyle.ordinal)
            .apply()

        _settings.value = settings
    }

    /** Update settings by applying a transform to the current value. */
    fun update(transform: AppSettings.() -> AppSettings) {
        save(_settings.value.transform())
    }

    /** Reset all settings to defaults. */
    fun resetToDefaults() {
        save(AppSettings())
    }

    companion object {
        private const val PREFS_NAME = "bikearcade_settings"
        private const val KEY_GAME_SCALE = "game_scale"
        private const val KEY_SIDEBAR_WIDTH = "sidebar_width"
        private const val KEY_HUD_OPACITY = "hud_opacity"
        private const val KEY_USE_METRIC = "use_metric_units"
        private const val KEY_METRICS_SOURCE = "metrics_source"
        private const val KEY_TCP_PORT = "tcp_port"
        private const val KEY_SMOOTHING_ALPHA = "smoothing_alpha"
        private const val KEY_SCREEN_SAVER_TIMEOUT = "screen_saver_timeout"
        private const val KEY_SCREEN_SAVER_STYLE = "screen_saver_style"
        private const val KEY_SETTINGS_VERSION = "settings_version"
    }
}
