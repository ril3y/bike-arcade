package com.bikearcade.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bikearcade.app.workout.IntensityPickerDialog
import com.bikearcade.app.workout.UserSetupScreen
import com.bikearcade.app.workout.WorkoutScaler
import com.bikearcade.app.workout.WorkoutSelectionScreen
import com.bikearcade.settings.ScreenSaverStyle
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
        mainViewModel?.reconnectMetrics()
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            mainViewModel?.resetIdleTimer()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (inputManager.onGenericMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
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

    LaunchedEffect(viewModel) {
        onViewModelReady(viewModel)
    }

    Crossfade(targetState = screen, label = "screen") { currentScreen ->
        when (currentScreen) {
            MainViewModel.Screen.SPLASH -> {
                SplashScreen(onFinished = { viewModel.dismissSplash() })
            }
            MainViewModel.Screen.USER_SETUP -> {
                val profile by viewModel.userProfile.collectAsState()
                UserSetupScreen(
                    existingProfile = profile,
                    onSave = { viewModel.saveUserProfile(it) },
                )
            }
            MainViewModel.Screen.WORKOUT_SELECT -> {
                val profile by viewModel.userProfile.collectAsState()
                WorkoutSelectionScreen(
                    userProfile = profile,
                    workouts = viewModel.workouts,
                    onSelectWorkout = { viewModel.selectWorkout(it) },
                    onChangeUser = { viewModel.changeUser() },
                    onExitApp = { activity?.finish() },
                    onManageGames = { viewModel.openGameLibrary() },
                    onSettings = { viewModel.openSettings() },
                )
            }
            MainViewModel.Screen.INTENSITY_PICK -> {
                val profile by viewModel.userProfile.collectAsState()
                val pending by viewModel.pendingWorkout.collectAsState()
                pending?.let { workout ->
                    IntensityPickerDialog(
                        workout = workout,
                        userProfile = profile,
                        onConfirm = { intensity ->
                            val scaled = WorkoutScaler.scale(workout, profile, intensity)
                            viewModel.confirmWorkoutIntensity(scaled)
                        },
                        onCancel = { viewModel.cancelIntensityPick() },
                    )
                }
            }
            MainViewModel.Screen.LAUNCHER -> {
                GameLauncherScreen(
                    gameLibrary = viewModel.gameLibrary,
                    onGameSelected = { entry -> viewModel.launchGame(entry) },
                    onExit = { viewModel.backToWorkoutSelect() },
                )
            }
            MainViewModel.Screen.PLAYING -> {
                GamePlayScreen(viewModel = viewModel)
            }
            MainViewModel.Screen.SETTINGS -> {
                val app = BikeArcadeApplication.instance
                SettingsScreen(
                    repository = app.container.settingsRepository,
                    onClose = { viewModel.closeSettings() },
                    onTestScreenSaver = {
                        viewModel.activateScreenSaver()
                    },
                )
            }
        }
    }

    // Screen saver overlay (rendered on top via z-ordering)
    val isScreenSaverActive by viewModel.isScreenSaverActive.collectAsState()
    val currentSettings by viewModel.settings.collectAsState()
    if (isScreenSaverActive) {
        ScreenSaver(
            style = currentSettings.screenSaverStyle,
            onDismiss = { viewModel.dismissScreenSaver() },
        )
    }
}
