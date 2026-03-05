package com.bikearcade.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bikearcade.app.launcher.GameLauncherScreen
import com.bikearcade.emulator.EmulatorSurface
import com.bikearcade.overlay.SidebarOverlay
import com.bikearcade.settings.SettingsScreen
import com.bikearcade.ui.theme.BikeArcadeTheme

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

    Row(modifier = Modifier.fillMaxSize()) {
        // Emulator surface: left 2/3 (1280 of 1920)
        EmulatorSurface(
            core = app.container.emulatorCore,
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
        )

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
