package com.bikearcade.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bikearcade.app.launcher.GameEntry
import com.bikearcade.emulator.EmulatorThread
import com.bikearcade.emulator.PlatformRegistry
import com.bikearcade.metrics.BikeMetrics
import com.bikearcade.metrics.BikeMetricsService
import com.bikearcade.media.MediaInfo
import com.bikearcade.app.workout.WorkoutLoader
import com.bikearcade.app.workout.WorkoutScaler
import com.bikearcade.overlay.components.EffortComplianceState
import com.bikearcade.overlay.components.EffortZone
import com.bikearcade.settings.MetricsSourceType
import com.bikearcade.settings.UserProfile
import com.bikearcade.settings.WorkoutDefinition
import io.freewheel.fit.RideStats
import io.freewheel.fit.VeloFitnessClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BikeArcadeApplication
    private val container = app.container
    private val fitnessClient = VeloFitnessClient(application)
    private var rideStartTime = 0L

    // Navigation state
    enum class Screen { SPLASH, USER_SETUP, WORKOUT_SELECT, INTENSITY_PICK, LAUNCHER, PLAYING, SETTINGS }

    private val _currentScreen = MutableStateFlow(Screen.SPLASH)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // User profile
    val userProfile: StateFlow<UserProfile> = container.userRepository.activeProfile

    // Active workout
    private val _activeWorkout = MutableStateFlow<WorkoutDefinition?>(null)
    val activeWorkout: StateFlow<WorkoutDefinition?> = _activeWorkout.asStateFlow()

    private val _workoutElapsedSeconds = MutableStateFlow(0)
    val workoutElapsedSeconds: StateFlow<Int> = _workoutElapsedSeconds.asStateFlow()

    private val _workoutTimeRemaining = MutableStateFlow(0)
    val workoutTimeRemaining: StateFlow<Int> = _workoutTimeRemaining.asStateFlow()

    private val _currentSegmentName = MutableStateFlow("")
    val currentSegmentName: StateFlow<String> = _currentSegmentName.asStateFlow()

    private var workoutTimerJob: Job? = null

    // Bike metrics
    private val _bikeMetrics = MutableStateFlow(BikeMetrics.EMPTY)
    val bikeMetrics: StateFlow<BikeMetrics> = _bikeMetrics.asStateFlow()

    // Media info (bridged to overlay's MediaInfo)
    private val _mediaInfo = MutableStateFlow(MediaInfo.EMPTY)
    val mediaInfo: StateFlow<MediaInfo> = _mediaInfo.asStateFlow()

    // Graph history (circular buffers to avoid GC churn at 13 Hz)
    private val powerRingBuffer = ArrayDeque<Float>(64)
    private val _powerHistory = MutableStateFlow<List<Float>>(emptyList())
    val powerHistory: StateFlow<List<Float>> = _powerHistory.asStateFlow()

    private val cadenceRingBuffer = ArrayDeque<Float>(64)
    private val _cadenceHistory = MutableStateFlow<List<Float>>(emptyList())
    val cadenceHistory: StateFlow<List<Float>> = _cadenceHistory.asStateFlow()

    // Calories
    private val _calories = MutableStateFlow(0)
    val calories: StateFlow<Int> = _calories.asStateFlow()

    // HUD visibility
    private val _isHudVisible = MutableStateFlow(true)
    val isHudVisible: StateFlow<Boolean> = _isHudVisible.asStateFlow()

    // Game audio mute + volume
    private val _isGameMuted = MutableStateFlow(false)
    val isGameMuted: StateFlow<Boolean> = _isGameMuted.asStateFlow()

    private val _gameVolume = MutableStateFlow(1.0f)
    val gameVolume: StateFlow<Float> = _gameVolume.asStateFlow()

    // Power history for workout graph (actual power sampled every second)
    private val workoutPowerBuffer = ArrayDeque<Int>()
    private val _powerHistory_workout = MutableStateFlow<List<Int>>(emptyList())
    val powerHistoryWorkout: StateFlow<List<Int>> = _powerHistory_workout.asStateFlow()

    // Legacy resistance history (kept for compatibility)
    private val _resistanceHistory = MutableStateFlow<List<Int>>(emptyList())
    val resistanceHistory: StateFlow<List<Int>> = _resistanceHistory.asStateFlow()

    // Difficulty multiplier (adjustable mid-workout, 1.0 = as designed)
    private val _difficultyMultiplier = MutableStateFlow(1.0f)
    val difficultyMultiplier: StateFlow<Float> = _difficultyMultiplier.asStateFlow()

    // Target power center for each workout segment (computed on workout start)
    private val _targetPowerPerSegment = MutableStateFlow<List<Int>>(emptyList())
    val targetPowerPerSegment: StateFlow<List<Int>> = _targetPowerPerSegment.asStateFlow()

    // Pause game when user drops below target power
    private val _pauseOnStop = MutableStateFlow(true)
    val pauseOnStop: StateFlow<Boolean> = _pauseOnStop.asStateFlow()
    private val _isPedalPaused = MutableStateFlow(false)
    val isPedalPaused: StateFlow<Boolean> = _isPedalPaused.asStateFlow()
    private var pauseCountdownJob: Job? = null
    private val _pauseCountdown = MutableStateFlow(-1)  // -1 = inactive, 5..1 = counting
    private var hasStartedPedaling = false  // don't trigger countdown until user has pedaled

    // Lean angle from tilt sensor
    private val _leanAngle = MutableStateFlow(0f)
    val leanAngle: StateFlow<Float> = _leanAngle.asStateFlow()

    // Effort compliance (power-based workout target tracking)
    private val _effortCompliance = MutableStateFlow(EffortComplianceState.EMPTY)
    val effortCompliance: StateFlow<EffortComplianceState> = _effortCompliance.asStateFlow()

    // Screen saver
    private val _isScreenSaverActive = MutableStateFlow(false)
    val isScreenSaverActive: StateFlow<Boolean> = _isScreenSaverActive.asStateFlow()
    private var idleTimerJob: Job? = null

    // Emulator thread (created when game starts)
    private var emulatorThread: EmulatorThread? = null

    // Settings & services
    val settings = container.settingsRepository.settings
    val gameLibrary = container.gameLibrary

    // Workouts loaded from JSON assets
    val workouts: List<WorkoutDefinition> = WorkoutLoader.loadAll(application)

    // Screens eligible for screen saver activation
    private val screenSaverEligible = setOf(Screen.WORKOUT_SELECT, Screen.LAUNCHER, Screen.INTENSITY_PICK)

    init {
        // Collect bike metrics
        viewModelScope.launch {
            container.bikeMetricsService.metricsFlow.collect { metrics ->
                _bikeMetrics.value = metrics

                // Update graph history (keep last 60 samples via ring buffer)
                powerRingBuffer.addLast(metrics.power.toFloat())
                if (powerRingBuffer.size > 60) powerRingBuffer.removeFirst()
                _powerHistory.value = powerRingBuffer.toList()

                cadenceRingBuffer.addLast(metrics.cadence.toFloat())
                if (cadenceRingBuffer.size > 60) cadenceRingBuffer.removeFirst()
                _cadenceHistory.value = cadenceRingBuffer.toList()

                // Update calories from processor
                _calories.value = container.bikeMetricsService.getProcessor().sessionStats.totalCalories.toInt()

                // Lean angle
                _leanAngle.value = metrics.leanAngle

                // Pedaling resets screen saver idle timer
                if (metrics.cadence > 0) resetIdleTimer()

                // Real-time effort compliance (updates at sensor rate, not 1Hz timer)
                updateEffortCompliance(metrics)
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

        // Arm/disarm screen saver based on current screen
        viewModelScope.launch {
            _currentScreen.collect { screen ->
                if (screen in screenSaverEligible) {
                    resetIdleTimer()
                } else {
                    idleTimerJob?.cancel()
                    _isScreenSaverActive.value = false
                }
            }
        }

        // Start media session monitoring — falls back to polling if listener not granted
        if (!container.mediaSessionMonitor.isListenerEnabled()) {
            android.util.Log.w("MainViewModel", "Notification listener not enabled, using polling fallback")
        }
        container.mediaSessionMonitor.startListening()

        // Connect to bike metrics — ensure serial bridge chain is running,
        // then try configured port, then fallback port
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.value

            val primary = when (settings.metricsSource) {
                MetricsSourceType.SESSION -> BikeMetricsService.SourceType.SESSION
                MetricsSourceType.BLE -> BikeMetricsService.SourceType.BLE_FTMS
            }

            // Start lean sensor (independent of metrics source)
            container.bikeMetricsService.leanSensor.start()

            try {
                container.bikeMetricsService.connect(primary)
                android.util.Log.i("MainViewModel", "Connected to $primary source")
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "$primary source failed: ${e.message}")
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

                val platform = entry.platform
                    ?: throw IllegalArgumentException("Unknown platform: ${entry.platformId}")

                core.loadCore(platform)
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
                startWorkoutTimer()
                _currentScreen.value = Screen.PLAYING
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to launch game", e)
            }
        }
    }

    fun stopGame() {
        container.bikeMetricsService.getSessionSource()?.requestStop()
        logRide()
        stopWorkoutTimer()
        emulatorThread?.stop()
        emulatorThread = null
        container.audioRenderer.stop()
        container.emulatorCore.unloadGame()
        _currentScreen.value = Screen.WORKOUT_SELECT
    }

    private fun logRide() {
        val elapsed = _workoutElapsedSeconds.value
        if (elapsed < 60) return // skip rides shorter than 1 minute

        val powerHistory = workoutPowerBuffer.toList()
        val avgPower = if (powerHistory.isNotEmpty()) powerHistory.average().toInt() else 0
        val maxPower = powerHistory.maxOrNull() ?: 0
        val stats = container.bikeMetricsService.getProcessor().sessionStats
        val currentMetrics = _bikeMetrics.value
        val workout = _activeWorkout.value

        val builder = RideStats.Builder()
            .startTime(rideStartTime)
            .durationSeconds(elapsed)
            .avgPower(avgPower)
            .maxPower(maxPower)
            .avgRpm(stats.avgCadence.toInt())
            .avgResistance(currentMetrics.resistance)
            .calories(stats.totalCalories.toInt())
            .distanceMiles(currentMetrics.distance.toFloat())
            .avgHeartRate(stats.avgHeartRate)
            .source("com.bikearcade.app", "BikeArcade")

        if (workout != null) {
            builder.workout(workout.id, workout.name)
        }

        val uri = fitnessClient.logRide(builder.build())
        android.util.Log.i("MainViewModel", "Ride logged: ${elapsed}s, avgPower=${avgPower}W → $uri")
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

    // --- User & Workout ---

    fun dismissSplash() {
        if (container.userRepository.hasProfile) {
            _currentScreen.value = Screen.WORKOUT_SELECT
            return
        }

        // Try to load profile from VeloLauncher before showing setup screen
        val launcherProfile = container.userRepository.loadFromLauncher()
        if (launcherProfile != null && launcherProfile.isValid) {
            container.userRepository.saveProfile(launcherProfile)
            _currentScreen.value = Screen.WORKOUT_SELECT
        } else {
            _currentScreen.value = Screen.USER_SETUP
        }
    }

    fun saveUserProfile(profile: UserProfile) {
        container.userRepository.saveProfile(profile)
        // Write back to VeloLauncher so profile is shared across apps
        container.userRepository.writeToLauncher(profile)
        fitnessClient.invalidateCache()
        _currentScreen.value = Screen.WORKOUT_SELECT
    }

    fun changeUser() {
        _currentScreen.value = Screen.USER_SETUP
    }

    // Pending workout before intensity scaling
    private val _pendingWorkout = MutableStateFlow<WorkoutDefinition?>(null)
    val pendingWorkout: StateFlow<WorkoutDefinition?> = _pendingWorkout.asStateFlow()

    fun selectWorkout(workout: WorkoutDefinition) {
        _pendingWorkout.value = workout
        _currentScreen.value = Screen.INTENSITY_PICK
    }

    fun confirmWorkoutIntensity(scaledWorkout: WorkoutDefinition) {
        _activeWorkout.value = scaledWorkout
        _workoutElapsedSeconds.value = 0
        _workoutTimeRemaining.value = scaledWorkout.totalDurationSeconds
        _currentSegmentName.value = scaledWorkout.segments.firstOrNull()?.name ?: ""
        _pendingWorkout.value = null

        // Precompute target power center for each segment (used by workout graph)
        _targetPowerPerSegment.value = scaledWorkout.segments.map { seg ->
            val target = fitnessClient.getTargetPower(seg.resistanceLevel)
            target.centerPower
        }

        _currentScreen.value = Screen.LAUNCHER
    }

    fun cancelIntensityPick() {
        _pendingWorkout.value = null
        _currentScreen.value = Screen.WORKOUT_SELECT
    }

    fun openGameLibrary() {
        _currentScreen.value = Screen.LAUNCHER
    }

    fun backToWorkoutSelect() {
        _currentScreen.value = Screen.WORKOUT_SELECT
    }

    private fun startWorkoutTimer() {
        workoutTimerJob?.cancel()
        _resistanceHistory.value = emptyList()
        workoutPowerBuffer.clear()
        _powerHistory_workout.value = emptyList()
        _difficultyMultiplier.value = 1.0f
        _isPedalPaused.value = false
        pauseCountdownJob?.cancel()
        pauseCountdownJob = null
        _pauseCountdown.value = -1
        hasStartedPedaling = false
        rideStartTime = System.currentTimeMillis()
        container.bikeMetricsService.getSessionSource()?.requestStart()
        val workout = _activeWorkout.value ?: return
        workoutTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val elapsed = _workoutElapsedSeconds.value + 1
                _workoutElapsedSeconds.value = elapsed
                _workoutTimeRemaining.value = (workout.totalDurationSeconds - elapsed).coerceAtLeast(0)

                val segment = workout.segmentAt(elapsed)
                _currentSegmentName.value = segment?.name ?: "Complete"

                // Record actual power for the workout graph
                val currentMetrics = _bikeMetrics.value
                workoutPowerBuffer.addLast(currentMetrics.power)
                _powerHistory_workout.value = workoutPowerBuffer.toList()

                // Apply workout resistance via active source (Session or Direct)
                if (segment != null) {
                    container.bikeMetricsService.setResistance(segment.resistanceLevel)
                }

                if (elapsed >= workout.totalDurationSeconds) {
                    android.util.Log.i("MainViewModel", "Workout complete!")
                    break
                }
            }
        }
    }

    private fun stopWorkoutTimer() {
        workoutTimerJob?.cancel()
        workoutTimerJob = null
    }

    private fun updateEffortCompliance(metrics: BikeMetrics) {
        val workout = _activeWorkout.value ?: return
        val elapsed = _workoutElapsedSeconds.value
        val segment = workout.segmentAt(elapsed) ?: return
        val multiplier = _difficultyMultiplier.value

        val target = fitnessClient.getTargetPower(segment.resistanceLevel, multiplier)
        val targetLow = target.targetLow
        val targetHigh = target.targetHigh
        val maxDisplay = target.maxDisplay()
        val actualPower = metrics.power

        val ratio = target.complianceRatio(actualPower)
        val zoneLow = target.zoneLowFraction()
        val zoneHigh = target.zoneHighFraction()

        val effortZone = target.zone(actualPower)
        val zone = when (effortZone) {
            io.freewheel.fit.PowerTarget.EffortZone.IDLE -> EffortZone.IDLE
            io.freewheel.fit.PowerTarget.EffortZone.UNDER -> EffortZone.UNDER
            io.freewheel.fit.PowerTarget.EffortZone.ON_TARGET -> EffortZone.ON_TARGET
            io.freewheel.fit.PowerTarget.EffortZone.OVER -> EffortZone.OVER
        }

        _effortCompliance.value = EffortComplianceState(
            complianceRatio = ratio,
            targetZoneLow = zoneLow,
            targetZoneHigh = zoneHigh,
            zone = zone,
            actualPower = actualPower,
            targetPowerLow = targetLow,
            targetPowerHigh = targetHigh,
            resistance = metrics.resistance,
            pauseCountdown = _pauseCountdown.value,
        )

        // Pause countdown: when power drops below target, start 5s countdown to pause
        // Only activates after user has started pedaling (don't pause before first pedal stroke)
        if (metrics.cadence >= 1.0 || metrics.power > 0) {
            hasStartedPedaling = true
        }
        if (_pauseOnStop.value && emulatorThread?.isRunning == true && emulatorThread?.isPaused == false && hasStartedPedaling) {
            val belowTarget = zone == EffortZone.UNDER || zone == EffortZone.IDLE
            if (belowTarget && !_isPedalPaused.value && pauseCountdownJob == null) {
                // Start countdown
                pauseCountdownJob = viewModelScope.launch {
                    for (i in 5 downTo 1) {
                        _pauseCountdown.value = i
                        // Re-emit compliance with countdown
                        _effortCompliance.value = _effortCompliance.value.copy(pauseCountdown = i)
                        delay(1000)
                    }
                    // Countdown reached 0 — pause
                    _pauseCountdown.value = -1
                    _effortCompliance.value = _effortCompliance.value.copy(pauseCountdown = -1)
                    if (emulatorThread?.isPaused == false) {
                        pauseEmulation()
                        _isPedalPaused.value = true
                    }
                    pauseCountdownJob = null
                }
            } else if (!belowTarget) {
                // Back on target — cancel countdown and resume if paused
                pauseCountdownJob?.cancel()
                pauseCountdownJob = null
                _pauseCountdown.value = -1
                if (_isPedalPaused.value) {
                    resumeEmulation()
                    _isPedalPaused.value = false
                }
            }
        }
    }

    fun adjustDifficulty(delta: Float) {
        _difficultyMultiplier.value = (_difficultyMultiplier.value + delta).coerceIn(0.5f, 2.0f)
    }

    fun togglePauseOnStop() {
        _pauseOnStop.value = !_pauseOnStop.value
        // If turning off, cancel countdown and resume if paused
        if (!_pauseOnStop.value) {
            pauseCountdownJob?.cancel()
            pauseCountdownJob = null
            _pauseCountdown.value = -1
            if (_isPedalPaused.value) {
                resumeEmulation()
                _isPedalPaused.value = false
            }
        }
    }


    fun openSettings() {
        _currentScreen.value = Screen.SETTINGS
    }

    fun closeSettings() {
        _currentScreen.value = when {
            emulatorThread?.isRunning == true -> Screen.PLAYING
            _activeWorkout.value != null -> Screen.LAUNCHER
            else -> Screen.WORKOUT_SELECT
        }
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

    fun setGameVolume(level: Float) {
        _gameVolume.value = level.coerceIn(0f, 1f)
        container.audioRenderer.volume = _gameVolume.value
        // Unmute if volume is being adjusted up
        if (level > 0f && _isGameMuted.value) {
            _isGameMuted.value = false
            container.audioRenderer.isMuted = false
        }
    }

    // --- Screen saver / idle tracking ---

    fun resetIdleTimer() {
        _isScreenSaverActive.value = false
        idleTimerJob?.cancel()
        val screen = _currentScreen.value
        if (screen !in screenSaverEligible) return
        val timeoutMs = settings.value.screenSaverTimeoutMinutes * 60_000L
        idleTimerJob = viewModelScope.launch {
            delay(timeoutMs)
            _isScreenSaverActive.value = true
        }
    }

    fun dismissScreenSaver() {
        _isScreenSaverActive.value = false
        resetIdleTimer()
    }

    fun activateScreenSaver() {
        _isScreenSaverActive.value = true
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
