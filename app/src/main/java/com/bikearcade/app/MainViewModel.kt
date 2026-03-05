package com.bikearcade.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bikearcade.app.launcher.GameEntry
import com.bikearcade.emulator.CoreType
import com.bikearcade.emulator.EmulatorThread
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.metrics.BikeMetricsService
import com.bikearcade.overlay.MediaInfo as OverlayMediaInfo
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
    private val _mediaInfo = MutableStateFlow(OverlayMediaInfo.EMPTY)
    val mediaInfo: StateFlow<OverlayMediaInfo> = _mediaInfo.asStateFlow()

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

        // Collect media info and bridge to overlay MediaInfo type
        viewModelScope.launch {
            container.mediaController.mediaInfo.collect { info ->
                _mediaInfo.value = OverlayMediaInfo(
                    trackName = info.trackName,
                    artist = info.artistName,
                    albumArtUri = null, // Bitmap not directly usable as URI
                    isPlaying = info.isPlaying,
                    durationMs = info.duration,
                    positionMs = info.position
                )
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

        // Start media session monitoring
        container.mediaSessionMonitor.startListening()

        // Connect to bike metrics — try configured source, fallback to the other
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.value
            val primary = when (settings.metricsSource) {
                MetricsSourceType.BLE -> BikeMetricsService.SourceType.BLE_FTMS
                MetricsSourceType.TCP -> BikeMetricsService.SourceType.TCP_SERIAL
            }
            val fallback = when (primary) {
                BikeMetricsService.SourceType.BLE_FTMS -> BikeMetricsService.SourceType.TCP_SERIAL
                BikeMetricsService.SourceType.TCP_SERIAL -> BikeMetricsService.SourceType.BLE_FTMS
            }

            try {
                container.bikeMetricsService.connect(primary, tcpPort = settings.tcpPort)
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Primary metrics ($primary) failed, trying fallback ($fallback)", e)
                try {
                    container.bikeMetricsService.connect(fallback, tcpPort = settings.tcpPort)
                } catch (e2: Exception) {
                    android.util.Log.w("MainViewModel", "Fallback metrics ($fallback) also failed", e2)
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
    }

    fun resumeEmulation() {
        if (emulatorThread?.isPaused == true) {
            container.audioRenderer.start()
            emulatorThread?.resume()
        }
    }

    fun openSettings() {
        _currentScreen.value = Screen.SETTINGS
    }

    fun closeSettings() {
        _currentScreen.value = if (emulatorThread?.isRunning == true) Screen.PLAYING else Screen.LAUNCHER
    }

    fun toggleHud(visible: Boolean) {
        _isHudVisible.value = visible
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
