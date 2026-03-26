package com.bikearcade.app

import android.app.Application
import android.content.Context
import com.bikearcade.app.launcher.GameLibrary
import com.bikearcade.app.launcher.GameMetadataDB
import com.bikearcade.app.launcher.ThumbnailManager
import com.bikearcade.emulator.AudioRenderer
import com.bikearcade.emulator.CoreDownloader
import com.bikearcade.emulator.EmulatorCore
import com.bikearcade.emulator.InputManager
import com.bikearcade.media.BikeArcadeMediaController
import com.bikearcade.media.MediaSessionMonitor
import com.bikearcade.metrics.BikeMetricsService
import com.bikearcade.settings.SettingsRepository
import com.bikearcade.settings.UserRepository

class BikeArcadeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: BikeArcadeApplication
            private set
    }
}

class AppContainer(context: Context) {
    val userRepository = UserRepository(context)
    val settingsRepository = SettingsRepository(context)
    val thumbnailManager = ThumbnailManager(context)
    val metadataDB = GameMetadataDB(java.io.File("/storage/emulated/0/BikeArcade/metadata"))
    val gameLibrary = GameLibrary(context, thumbnailManager, metadataDB)
    val bikeMetricsService = BikeMetricsService(context)
    val mediaSessionMonitor = MediaSessionMonitor(context)
    val mediaController = BikeArcadeMediaController(context)
    val emulatorCore = EmulatorCore(context)
    val coreDownloader = CoreDownloader(context)
    val inputManager = InputManager()
    val audioRenderer = AudioRenderer()
}
