package com.bikearcade.emulator

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/**
 * Thrown when a libretro core .so file is not found on disk.
 * The UI should catch this and prompt the user to download the core
 * via [CoreDownloader].
 *
 * @property platform The [Platform] whose core is missing, or null if loaded by raw path.
 * @property coreLibrary The expected .so filename.
 */
class CoreNotInstalledException(
    val platform: Platform? = null,
    val coreLibrary: String,
    message: String = "Core not installed: $coreLibrary",
) : Exception(message)

/**
 * High-level manager for the libretro emulator lifecycle.
 *
 * Wraps [LibretroBridge] with state tracking, callback wiring, and
 * safety checks so callers do not need to worry about call ordering.
 *
 * Cores are copied from external storage to the app's private directory
 * before loading, because Android's linker namespace restrictions prevent
 * dlopen from loading .so files outside the app's permitted paths.
 */
class EmulatorCore(private val context: Context) {

    companion object {
        private const val TAG = "EmulatorCore"
    }

    private val coresDir: File by lazy {
        File(context.filesDir, "cores").also { it.mkdirs() }
    }

    private val savesDir: File by lazy {
        File("/storage/emulated/0/BikeArcade/saves").also { it.mkdirs() }
    }

    /** The ROM path of the currently loaded game, used to derive the save file name. */
    private var currentRomPath: String? = null

    // ── State ───────────────────────────────────────────────────────────

    enum class State { IDLE, CORE_LOADED, GAME_LOADED }

    @Volatile
    var state: State = State.IDLE
        private set

    /** System info populated after [loadCore]. */
    var libraryName: String = ""
        private set
    var libraryVersion: String = ""
        private set

    /** AV info populated after [loadGame]. */
    var coreFps: Double = 60.0
        private set
    var coreSampleRate: Double = 44100.0
        private set

    // ── Callbacks ───────────────────────────────────────────────────────

    /**
     * Set the video frame callback.
     * @param callback receives (pixelData, width, height, pitch, pixelFormat)
     */
    fun setVideoCallback(callback: (ByteBuffer, Int, Int, Int, Int) -> Unit) {
        LibretroBridge.videoCallback = callback
    }

    /**
     * Set the audio sample batch callback.
     * @param callback receives (interleavedSamples, frameCount)
     */
    fun setAudioCallback(callback: (ShortArray, Int) -> Unit) {
        LibretroBridge.audioBatchCallback = callback
    }

    /**
     * Set single-sample audio callback (used by some cores).
     */
    fun setAudioSampleCallback(callback: (Short, Short) -> Unit) {
        LibretroBridge.audioSampleCallback = callback
    }

    /**
     * Set the input state query callback.
     * @param callback (port, device, index, id) -> pressed (non-zero = pressed)
     */
    fun setInputCallback(callback: (port: Int, device: Int, index: Int, id: Int) -> Int) {
        LibretroBridge.inputStateCallback = callback
    }

    /**
     * Set the input poll callback (called once per frame before state queries).
     */
    fun setInputPollCallback(callback: () -> Unit) {
        LibretroBridge.inputPollCallback = callback
    }

    // ── Core lifecycle ──────────────────────────────────────────────────

    /**
     * Load a libretro core from the given .so path.
     * @throws IllegalStateException if a core is already loaded
     * @throws IllegalArgumentException if the core file does not exist
     * @throws RuntimeException if the native load fails
     */
    fun loadCore(corePath: String) {
        check(state == State.IDLE) { "Cannot load core in state $state; call unloadGame() first." }

        val coreFile = File(corePath)
        require(coreFile.exists()) { "Core file not found: $corePath" }

        Log.i(TAG, "Loading core: $corePath")

        if (!LibretroBridge.nativeLoadCore(corePath)) {
            throw RuntimeException("Failed to load libretro core: $corePath")
        }

        // Register all callbacks before init
        LibretroBridge.retroSetEnvironment()
        LibretroBridge.retroSetVideoRefresh()
        LibretroBridge.retroSetAudioSample()
        LibretroBridge.retroSetAudioSampleBatch()
        LibretroBridge.retroSetInputPoll()
        LibretroBridge.retroSetInputState()

        LibretroBridge.retroInit()

        // Read system info
        val info = LibretroBridge.retroGetSystemInfo()
        libraryName = info[0]
        libraryVersion = info[1]

        val apiVersion = LibretroBridge.retroApiVersion()
        Log.i(TAG, "Core loaded: $libraryName v$libraryVersion (API v$apiVersion)")

        state = State.CORE_LOADED
    }

    /**
     * Convenience overload that resolves the core path from a [CoreType].
     * Copies the core .so from external storage to the app's private directory
     * to satisfy Android's linker namespace restrictions.
     */
    fun loadCore(coreType: CoreType) {
        val externalCore = File(CoreType.corePathFor(coreType))
        val privateCore = File(coresDir, coreType.coreLibrary)

        if (externalCore.exists()) {
            if (!privateCore.exists() || externalCore.lastModified() > privateCore.lastModified()) {
                Log.i(TAG, "Copying core to private dir: ${externalCore.name}")
                externalCore.copyTo(privateCore, overwrite = true)
            }
        }

        if (!privateCore.exists() && !externalCore.exists()) {
            throw CoreNotInstalledException(
                coreLibrary = coreType.coreLibrary,
            )
        }

        val corePath = if (privateCore.exists()) privateCore.absolutePath else CoreType.corePathFor(coreType)
        loadCore(corePath)
    }

    /**
     * Load a core from a [Platform] definition.
     * Copies the core .so from external storage to the app's private directory.
     */
    fun loadCore(platform: Platform) {
        val externalCore = File(PlatformRegistry.corePathFor(platform))
        val privateCore = File(coresDir, platform.coreLibrary)

        if (externalCore.exists()) {
            if (!privateCore.exists() || externalCore.lastModified() > privateCore.lastModified()) {
                Log.i(TAG, "Copying core to private dir: ${externalCore.name}")
                externalCore.copyTo(privateCore, overwrite = true)
            }
        }

        if (!privateCore.exists() && !externalCore.exists()) {
            throw CoreNotInstalledException(
                platform = platform,
                coreLibrary = platform.coreLibrary,
            )
        }

        val corePath = if (privateCore.exists()) privateCore.absolutePath else PlatformRegistry.corePathFor(platform)
        loadCore(corePath)
    }

    /**
     * Load a ROM file into the currently loaded core.
     * If the file is a .zip, extracts the first supported ROM to a temp file first,
     * since libretro cores with need_fullpath=false expect raw ROM data.
     *
     * @throws IllegalStateException if no core is loaded
     * @throws IllegalArgumentException if the ROM file does not exist
     * @throws RuntimeException if the native load fails
     */
    fun loadGame(romPath: String) {
        check(state == State.CORE_LOADED) {
            "Cannot load game in state $state; load a core first."
        }

        val romFile = File(romPath)
        require(romFile.exists()) { "ROM file not found: $romPath" }

        val actualPath = if (romPath.endsWith(".zip", ignoreCase = true)) {
            extractRomFromZip(romFile)?.absolutePath
                ?: throw RuntimeException("No supported ROM found inside: $romPath")
        } else {
            romPath
        }

        Log.i(TAG, "Loading ROM: $actualPath")

        if (!LibretroBridge.retroLoadGame(actualPath)) {
            throw RuntimeException("Failed to load ROM: $actualPath")
        }

        // Read AV info for frame pacing and audio sample rate
        val avInfo = LibretroBridge.retroGetSystemAvInfo()
        coreFps = if (avInfo[0] > 0) avInfo[0] else 60.0
        coreSampleRate = if (avInfo[1] > 0) avInfo[1] else 44100.0
        Log.i(TAG, "AV info: fps=$coreFps, sampleRate=$coreSampleRate")

        currentRomPath = actualPath
        state = State.GAME_LOADED
        Log.i(TAG, "Game loaded successfully")

        // Load SRAM save data if it exists
        loadSram()
    }

    private val romExtensions = setOf("nes", "smc", "sfc")

    private val extractDir: File by lazy {
        File(context.cacheDir, "rom_extract").also { it.mkdirs() }
    }

    /**
     * Extract the first supported ROM file from a zip archive to the cache directory.
     */
    private fun extractRomFromZip(zipFile: File): File? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { e ->
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    ext in romExtensions
                } ?: return null

                val outFile = File(extractDir, entry.name.substringAfterLast('/'))
                zip.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Extracted ROM: ${outFile.name} (${outFile.length()} bytes)")
                outFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ROM from zip", e)
            null
        }
    }

    /**
     * Advance emulation by one frame. Call this at 60 fps from [EmulatorThread].
     * @throws IllegalStateException if no game is loaded
     */
    fun run() {
        check(state == State.GAME_LOADED) { "No game loaded; cannot run." }
        LibretroBridge.retroRun()
    }

    /**
     * Soft-reset the currently running game.
     */
    fun reset() {
        check(state == State.GAME_LOADED) { "No game loaded; cannot reset." }
        Log.i(TAG, "Resetting game")
        LibretroBridge.retroReset()
    }

    /**
     * Unload the current game and core, returning to IDLE state.
     * Safe to call from any state.
     */
    fun unloadGame() {
        when (state) {
            State.GAME_LOADED -> {
                Log.i(TAG, "Unloading game")
                saveSram()
                LibretroBridge.retroUnloadGame()
                LibretroBridge.retroDeinit()
                LibretroBridge.nativeUnloadCore()
                clearCallbacks()
                state = State.IDLE
            }
            State.CORE_LOADED -> {
                Log.i(TAG, "Unloading core (no game was loaded)")
                LibretroBridge.retroDeinit()
                LibretroBridge.nativeUnloadCore()
                clearCallbacks()
                state = State.IDLE
            }
            State.IDLE -> {
                Log.d(TAG, "Already idle; nothing to unload")
            }
        }
    }

    /**
     * Save the core's SRAM to disk. Called automatically when unloading a game.
     */
    fun saveSram() {
        val romPath = currentRomPath ?: return
        try {
            val sramData = LibretroBridge.retroGetMemoryData(LibretroBridge.MEMORY_SAVE_RAM)
            if (sramData == null || sramData.isEmpty()) {
                Log.d(TAG, "No SRAM data to save")
                return
            }
            val saveFile = sramFileFor(romPath)
            saveFile.parentFile?.mkdirs()
            saveFile.writeBytes(sramData)
            Log.i(TAG, "Saved SRAM: ${saveFile.name} (${sramData.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save SRAM", e)
        }
    }

    private fun loadSram() {
        val romPath = currentRomPath ?: return
        val saveFile = sramFileFor(romPath)
        if (!saveFile.exists()) {
            Log.d(TAG, "No SRAM save file found: ${saveFile.name}")
            return
        }
        try {
            val sramData = saveFile.readBytes()
            if (LibretroBridge.retroSetMemoryData(LibretroBridge.MEMORY_SAVE_RAM, sramData)) {
                Log.i(TAG, "Loaded SRAM: ${saveFile.name} (${sramData.size} bytes)")
            } else {
                Log.w(TAG, "Core rejected SRAM data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SRAM", e)
        }
    }

    // ── Save states ──────────────────────────────────────────────────────

    private val statesDir: File by lazy {
        File("/storage/emulated/0/BikeArcade/states").also { it.mkdirs() }
    }

    /**
     * Save an emulator snapshot (save state) to slot [slot].
     * Returns true on success.
     */
    fun saveState(slot: Int = 0): Boolean {
        val romPath = currentRomPath ?: run {
            Log.w(TAG, "saveState: no ROM loaded")
            return false
        }
        if (state != State.GAME_LOADED) {
            Log.w(TAG, "saveState: wrong state $state")
            return false
        }
        return try {
            val data = LibretroBridge.retroSerialize()
            if (data == null || data.isEmpty()) {
                Log.w(TAG, "Serialize returned empty/null data")
                return false
            }
            val file = stateFileFor(romPath, slot)
            file.parentFile?.mkdirs()
            // Delete existing file first to ensure clean overwrite
            if (file.exists()) file.delete()
            file.writeBytes(data)
            Log.i(TAG, "Saved state slot $slot: ${file.name} (${data.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state slot $slot", e)
            false
        }
    }

    /**
     * Load an emulator snapshot (save state) from slot [slot].
     * Returns true on success.
     */
    fun loadState(slot: Int = 0): Boolean {
        val romPath = currentRomPath ?: return false
        check(state == State.GAME_LOADED) { "No game loaded; cannot load state." }
        val file = stateFileFor(romPath, slot)
        if (!file.exists()) {
            Log.d(TAG, "No save state in slot $slot: ${file.name}")
            return false
        }
        return try {
            val data = file.readBytes()
            if (LibretroBridge.retroUnserialize(data)) {
                Log.i(TAG, "Loaded state slot $slot: ${file.name} (${data.size} bytes)")
                true
            } else {
                Log.w(TAG, "Core rejected state data from slot $slot")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load state", e)
            false
        }
    }

    /**
     * Check if a save state exists for the given slot.
     */
    fun hasStateFile(slot: Int = 0): Boolean {
        val romPath = currentRomPath ?: return false
        return stateFileFor(romPath, slot).exists()
    }

    private fun stateFileFor(romPath: String, slot: Int): File {
        val romName = File(romPath).nameWithoutExtension
        val suffix = if (slot == 0) ".state" else ".state$slot"
        return File(statesDir, "$romName$suffix")
    }

    private fun sramFileFor(romPath: String): File {
        val romName = File(romPath).nameWithoutExtension
        return File(savesDir, "$romName.srm")
    }

    private fun clearCallbacks() {
        LibretroBridge.videoCallback = null
        LibretroBridge.audioSampleCallback = null
        LibretroBridge.audioBatchCallback = null
        LibretroBridge.inputPollCallback = null
        LibretroBridge.inputStateCallback = null
    }
}
