package com.bikearcade.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bikearcade.app.launcher.GameEntry
import com.bikearcade.emulator.CoreType
import com.bikearcade.emulator.EmulatorThread
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.metrics.BikeMetricsService
import com.bikearcade.media.MediaInfo
import com.bikearcade.settings.MetricsSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BikeArcadeApplication
    private val container = app.container

    // Navigation state
    enum class Screen { LAUNCHER, PLAYING, SETTINGS }

    private val _currentScreen = MutableStateFlow(Screen.LAUNCHER)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Bike metrics
    private val _bikeMetrics = MutableStateFlow(BikeMetrics.EMPTY)
    val bikeMetrics: StateFlow<BikeMetrics> = _bikeMetrics.asStateFlow()

    // Media info (bridged to overlay's MediaInfo)
    private val _mediaInfo = MutableStateFlow(MediaInfo.EMPTY)
    val mediaInfo: StateFlow<MediaInfo> = _mediaInfo.asStateFlow()

    // Graph history
    private val _powerHistory = MutableStateFlow<List<Float>>(emptyList())
    val powerHistory: StateFlow<List<Float>> = _powerHistory.asStateFlow()

    private val _cadenceHistory = MutableStateFlow<List<Float>>(emptyList())
    val cadenceHistory: StateFlow<List<Float>> = _cadenceHistory.asStateFlow()

    // Calories
    private val _calories = MutableStateFlow(0)
    val calories: StateFlow<Int> = _calories.asStateFlow()

    // HUD visibility
    private val _isHudVisible = MutableStateFlow(true)
    val isHudVisible: StateFlow<Boolean> = _isHudVisible.asStateFlow()

    // Game audio mute
    private val _isGameMuted = MutableStateFlow(false)
    val isGameMuted: StateFlow<Boolean> = _isGameMuted.asStateFlow()

    // Emulator thread (created when game starts)
    private var emulatorThread: EmulatorThread? = null

    // Settings & services
    val settings = container.settingsRepository.settings
    val gameLibrary = container.gameLibrary

    init {
        // Collect bike metrics
        viewModelScope.launch {
            container.bikeMetricsService.metricsFlow.collect { metrics ->
                _bikeMetrics.value = metrics

                // Update graph history (keep last 60 samples)
                val power = _powerHistory.value.toMutableList()
                power.add(metrics.power.toFloat())
                if (power.size > 60) power.removeAt(0)
                _powerHistory.value = power

                val cadence = _cadenceHistory.value.toMutableList()
                cadence.add(metrics.cadence.toFloat())
                if (cadence.size > 60) cadence.removeAt(0)
                _cadenceHistory.value = cadence

                // Update calories from processor
                _calories.value = container.bikeMetricsService.getProcessor().sessionStats.totalCalories.toInt()
            }
        }

        // Collect media info directly from controller
        viewModelScope.launch {
            container.mediaController.mediaInfo.collect { info ->
                _mediaInfo.value = info
            }
        }

        // Auto-attach media controller when session changes
        viewModelScope.launch {
            container.mediaSessionMonitor.activeController.collect { controller ->
                if (controller != null) {
                    container.mediaController.attach(controller)
                }
            }
        }

        // Start media session monitoring — request access if not granted
        if (!container.mediaSessionMonitor.isListenerEnabled()) {
            android.util.Log.w("MainViewModel", "Notification listener not enabled, opening settings")
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                )
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Could not open notification listener settings", e)
            }
        }
        container.mediaSessionMonitor.startListening()

        // Connect to bike metrics — ensure serial bridge chain is running,
        // then try configured port, then fallback port
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.value

            // Force-stop JRNY to release the single-client serial bridge (TCP:9999)
            releaseSerialBridge()

            // Ensure nautiluslauncher is running (provides TCP:9999 serial bridge)
            ensureSerialBridgeRunning()

            val primary = when (settings.metricsSource) {
                MetricsSourceType.BLE -> BikeMetricsService.SourceType.BLE_FTMS
                MetricsSourceType.TCP -> BikeMetricsService.SourceType.TCP_SERIAL
            }

            if (primary == BikeMetricsService.SourceType.TCP_SERIAL) {
                // Try configured port first, then the other serial port
                val ports = if (settings.tcpPort == 9998) intArrayOf(9998, 9999) else intArrayOf(9999, 9998)
                var connected = false
                for (port in ports) {
                    try {
                        container.bikeMetricsService.connect(primary, tcpPort = port)
                        android.util.Log.i("MainViewModel", "Connected to serial bridge on port $port")
                        connected = true
                        break
                    } catch (e: Exception) {
                        android.util.Log.w("MainViewModel", "TCP port $port failed: ${e.message}")
                    }
                }
                if (!connected) {
                    // Try BLE as last resort
                    try {
                        container.bikeMetricsService.connect(BikeMetricsService.SourceType.BLE_FTMS)
                    } catch (e: Exception) {
                        android.util.Log.w("MainViewModel", "BLE fallback also failed: ${e.message}")
                    }
                }
            } else {
                try {
                    container.bikeMetricsService.connect(primary)
                } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "BLE failed, trying TCP", e)
                    try {
                        container.bikeMetricsService.connect(BikeMetricsService.SourceType.TCP_SERIAL, tcpPort = settings.tcpPort)
                    } catch (e2: Exception) {
                        android.util.Log.w("MainViewModel", "TCP fallback also failed: ${e2.message}")
                    }
                }
            }
        }
    }

    fun launchGame(entry: GameEntry) {
        viewModelScope.launch {
            try {
                val core = container.emulatorCore

                // Reset if a previous load left us in a non-IDLE state
                if (core.state != com.bikearcade.emulator.EmulatorCore.State.IDLE) {
                    core.unloadGame()
                }

                val coreType = when (entry.consoleType) {
                    com.bikearcade.app.launcher.ConsoleType.NES -> CoreType.NES
                    com.bikearcade.app.launcher.ConsoleType.SNES -> CoreType.SNES
                }

                core.loadCore(coreType)
                core.loadGame(entry.path)

                // Wire input
                core.setInputCallback(container.inputManager::queryButtonState)
                core.setInputPollCallback { /* Input state is updated via key events */ }

                // Wire audio
                container.audioRenderer.attachTo(core)
                container.audioRenderer.start(core.coreSampleRate.toInt())

                // Start emulator thread
                val thread = EmulatorThread(core)
                emulatorThread = thread
                thread.start()

                container.gameLibrary.recordLaunch(entry)
                _currentScreen.value = Screen.PLAYING
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to launch game", e)
            }
        }
    }

    fun stopGame() {
        emulatorThread?.stop()
        emulatorThread = null
        container.audioRenderer.stop()
        container.emulatorCore.unloadGame()
        _currentScreen.value = Screen.LAUNCHER
    }

    fun pauseEmulation() {
        emulatorThread?.pause()
        container.audioRenderer.stop()
        // Save SRAM on pause in case the process is killed while backgrounded
        container.emulatorCore.saveSram()
    }

    fun resumeEmulation() {
        if (emulatorThread?.isPaused == true) {
            container.audioRenderer.start()
            container.audioRenderer.flush()
            emulatorThread?.resume()
        }
    }

    fun openSettings() {
        _currentScreen.value = Screen.SETTINGS
    }

    fun closeSettings() {
        _currentScreen.value = if (emulatorThread?.isRunning == true) Screen.PLAYING else Screen.LAUNCHER
    }

    fun saveState(slot: Int = 0): Boolean = container.emulatorCore.saveState(slot)
    fun loadState(slot: Int = 0): Boolean = container.emulatorCore.loadState(slot)
    fun hasStateFile(slot: Int = 0): Boolean = container.emulatorCore.hasStateFile(slot)
    fun resetGame() { container.emulatorCore.reset() }

    fun toggleHud(visible: Boolean) {
        _isHudVisible.value = visible
    }

    fun toggleGameMute() {
        val muted = !_isGameMuted.value
        _isGameMuted.value = muted
        container.audioRenderer.isMuted = muted
    }

    /**
     * Ensure nautiluslauncher is running so its SerialPortServer (TCP:9999) is available.
     * Also start the serialbridge proxy (TCP:9998) if installed.
     */
    private suspend fun ensureSerialBridgeRunning() {
        try {
            val ctx = getApplication<BikeArcadeApplication>()
            // Launch nautiluslauncher to un-stop it and trigger SerialPortService
            val launcherIntent = ctx.packageManager.getLaunchIntentForPackage("com.nautilus.nautiluslauncher")
            if (launcherIntent != null) {
                launcherIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launcherIntent)
                android.util.Log.i("MainViewModel", "Started nautiluslauncher for serial bridge")
            }
            // Start serialbridge proxy if available
            try {
                val bridgeIntent = android.content.Intent()
                bridgeIntent.setClassName("com.bowflex.serialbridge", "com.bowflex.serialbridge.MainActivity")
                bridgeIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(bridgeIntent)
                android.util.Log.i("MainViewModel", "Started serialbridge proxy")
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Serialbridge proxy not available: ${e.message}")
            }
            // Give services time to start
            kotlinx.coroutines.delay(2000)
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Could not ensure serial bridge: ${e.message}")
        }
    }

    /**
     * Force-stop JRNY app to release the single-client serial bridge on TCP:9999.
     * The nautiluslauncher's SerialPortServer only accepts one client at a time.
     * JRNY (com.nautilus.bowflex.usb) holds this connection during workouts.
     */
    private fun releaseSerialBridge() {
        try {
            val am = getApplication<BikeArcadeApplication>()
                .getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            // Force-stop JRNY variants that may hold the serial bridge connection
            am.killBackgroundProcesses("com.nautilus.bowflex.usb")
            am.killBackgroundProcesses("com.nautilus.bowflex")
            android.util.Log.i("MainViewModel", "Killed JRNY background processes to release serial bridge")
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Could not kill JRNY processes: ${e.message}")
        }
    }

    // Media controls
    fun onPlayPause() = container.mediaController.togglePlayPause()
    fun onSkipNext() = container.mediaController.skipNext()
    fun onSkipPrevious() = container.mediaController.skipPrevious()

    override fun onCleared() {
        super.onCleared()
        emulatorThread?.stop()
        container.audioRenderer.stop()
        container.emulatorCore.unloadGame()
        container.bikeMetricsService.destroy()
        container.mediaSessionMonitor.stopListening()
        container.mediaController.release()
    }
}
