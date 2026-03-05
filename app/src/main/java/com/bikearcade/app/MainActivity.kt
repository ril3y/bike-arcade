package com.bikearcade.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bikearcade.app.launcher.GameLauncherScreen
import com.bikearcade.emulator.EmulatorSurface
import com.bikearcade.overlay.SidebarOverlay
import com.bikearcade.settings.SettingsScreen
import com.bikearcade.ui.theme.BikeArcadeTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val inputManager by lazy {
        (application as BikeArcadeApplication).container.inputManager
    }

    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            BikeArcadeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    BikeArcadeApp(onViewModelReady = { mainViewModel = it })
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mainViewModel?.pauseEmulation()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel?.resumeEmulation()
        // Re-hide system bars
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (inputManager.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (inputManager.onKeyUp(keyCode, event)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (inputManager.onGenericMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        val vm = (application as BikeArcadeApplication)
        // Let the ViewModel handle back navigation via compose
        super.onBackPressed()
    }
}

@Composable
fun BikeArcadeApp(
    viewModel: MainViewModel = viewModel(),
    onViewModelReady: (MainViewModel) -> Unit = {}
) {
    val screen by viewModel.currentScreen.collectAsState()
    val activity = LocalContext.current as? ComponentActivity

    // Expose ViewModel to Activity for lifecycle handling
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        onViewModelReady(viewModel)
    }

    Crossfade(targetState = screen, label = "screen") { currentScreen ->
        when (currentScreen) {
            MainViewModel.Screen.LAUNCHER -> {
                GameLauncherScreen(
                    gameLibrary = viewModel.gameLibrary,
                    onGameSelected = { entry -> viewModel.launchGame(entry) },
                    onExit = { activity?.finish() }
                )
            }
            MainViewModel.Screen.PLAYING -> {
                GamePlayScreen(viewModel = viewModel)
            }
            MainViewModel.Screen.SETTINGS -> {
                val app = BikeArcadeApplication.instance
                SettingsScreen(
                    repository = app.container.settingsRepository,
                    onClose = { viewModel.closeSettings() }
                )
            }
        }
    }
}

@Composable
fun GamePlayScreen(viewModel: MainViewModel) {
    val bikeMetrics by viewModel.bikeMetrics.collectAsState()
    val mediaInfo by viewModel.mediaInfo.collectAsState()
    val powerHistory by viewModel.powerHistory.collectAsState()
    val cadenceHistory by viewModel.cadenceHistory.collectAsState()
    val calories by viewModel.calories.collectAsState()
    val isHudVisible by viewModel.isHudVisible.collectAsState()

    val app = BikeArcadeApplication.instance
    var isPaused by remember { mutableStateOf(false) }
    var showIcon by remember { mutableStateOf(false) }

    // Auto-fade the icon after showing
    LaunchedEffect(showIcon, isPaused) {
        if (showIcon && !isPaused) {
            delay(800)
            showIcon = false
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Emulator surface: left 2/3 (1280 of 1920)
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            EmulatorSurface(
                core = app.container.emulatorCore,
                modifier = Modifier.fillMaxSize()
            )

            // Touch overlay for pause/resume
            val iconAlpha by animateFloatAsState(
                targetValue = if (showIcon) 0.7f else 0f,
                animationSpec = tween(durationMillis = if (showIcon) 150 else 500),
                label = "pauseIconAlpha",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        if (isPaused) {
                            viewModel.resumeEmulation()
                            isPaused = false
                            showIcon = true // Show play briefly then fade
                        } else {
                            viewModel.pauseEmulation()
                            isPaused = true
                            showIcon = true // Show pause, stays visible
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (iconAlpha > 0f) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPaused) "Paused" else "Playing",
                        tint = Color.White.copy(alpha = iconAlpha),
                        modifier = Modifier.size(96.dp),
                    )
                }
            }
        }

        // Sidebar overlay: right 1/3 (640 of 1920)
        if (isHudVisible) {
            SidebarOverlay(
                metrics = bikeMetrics,
                mediaInfo = mediaInfo,
                powerHistory = powerHistory,
                cadenceHistory = cadenceHistory,
                calories = calories,
                isHudVisible = isHudVisible,
                onPlayPause = viewModel::onPlayPause,
                onSkipNext = viewModel::onSkipNext,
                onSkipPrevious = viewModel::onSkipPrevious,
                onToggleHud = viewModel::toggleHud,
                onOpenSettings = viewModel::openSettings,
                onExitGame = viewModel::stopGame,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        } else {
            // Minimal HUD toggle when hidden
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
            )
        }
    }
}
