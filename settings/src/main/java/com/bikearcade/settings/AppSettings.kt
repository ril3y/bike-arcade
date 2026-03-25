package com.bikearcade.settings

/**
 * All user-configurable settings for BikeArcade.
 *
 * @property gameScale Scale factor for the emulator display (0.5 to 2.0).
 * @property sidebarWidth Width of the sidebar overlay in pixels (200 to 800).
 * @property hudOpacity Opacity of the HUD overlay elements (0.0 to 1.0).
 * @property useMetricUnits Whether to display speed/distance in metric (km) vs imperial (miles).
 * @property metricsSource Data source for bike metrics: BLE FTMS or TCP serial bridge.
 * @property tcpPort TCP port for the serial bridge connection (1024 to 65535).
 * @property smoothingAlpha Exponential moving average alpha for metric smoothing (0.0 to 1.0).
 */
data class AppSettings(
    val gameScale: Float = DEFAULT_GAME_SCALE,
    val sidebarWidth: Int = DEFAULT_SIDEBAR_WIDTH,
    val hudOpacity: Float = DEFAULT_HUD_OPACITY,
    val useMetricUnits: Boolean = DEFAULT_USE_METRIC,
    val metricsSource: MetricsSourceType = DEFAULT_METRICS_SOURCE,
    val tcpPort: Int = DEFAULT_TCP_PORT,
    val smoothingAlpha: Float = DEFAULT_SMOOTHING_ALPHA,
    val screenSaverTimeoutMinutes: Int = DEFAULT_SCREEN_SAVER_TIMEOUT,
    val screenSaverStyle: ScreenSaverStyle = DEFAULT_SCREEN_SAVER_STYLE
) {
    companion object {
        const val DEFAULT_GAME_SCALE = 1.0f
        const val DEFAULT_SIDEBAR_WIDTH = 640
        const val DEFAULT_HUD_OPACITY = 0.85f
        const val DEFAULT_USE_METRIC = true
        val DEFAULT_METRICS_SOURCE = MetricsSourceType.SESSION
        const val DEFAULT_TCP_PORT = 9999
        const val DEFAULT_SMOOTHING_ALPHA = 0.3f
        const val DEFAULT_SCREEN_SAVER_TIMEOUT = 3
        val DEFAULT_SCREEN_SAVER_STYLE = ScreenSaverStyle.ARCADE

        // Range constraints
        val GAME_SCALE_RANGE = 0.5f..2.0f
        val SIDEBAR_WIDTH_RANGE = 200..800
        val HUD_OPACITY_RANGE = 0.0f..1.0f
        val TCP_PORT_RANGE = 1024..65535
        val SMOOTHING_ALPHA_RANGE = 0.0f..1.0f
        val SCREEN_SAVER_TIMEOUT_RANGE = 1..10
    }
}

/**
 * Available screen saver styles.
 */
enum class ScreenSaverStyle(val displayName: String, val description: String) {
    ARCADE("Arcade", "INSERT COIN attract mode with coin slot"),
    BOUNCING_LOGO("Bouncing Logo", "DVD-style bouncing BikeArcade logo"),
    STARFIELD("Starfield", "Flying through stars with title"),
    BLACKOUT("Blackout", "Pure black screen (max burn-in protection)");

    companion object {
        fun fromOrdinal(ordinal: Int): ScreenSaverStyle =
            entries.getOrElse(ordinal) { ARCADE }
    }
}

/**
 * Supported sources for bike telemetry data.
 */
enum class MetricsSourceType(val displayName: String) {
    SESSION("VeloLauncher"),
    BLE("Bluetooth (FTMS)");

    companion object {
        fun fromOrdinal(ordinal: Int): MetricsSourceType =
            entries.getOrElse(ordinal) { SESSION }
    }
}
