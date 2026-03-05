package com.bikearcade.emulator

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

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
            // Copy or update if source is newer
            if (!privateCore.exists() || externalCore.lastModified() > privateCore.lastModified()) {
                Log.i(TAG, "Copying core to private dir: ${externalCore.name}")
                externalCore.copyTo(privateCore, overwrite = true)
            }
        }

        val corePath = if (privateCore.exists()) privateCore.absolutePath else CoreType.corePathFor(coreType)
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

        state = State.GAME_LOADED
        Log.i(TAG, "Game loaded successfully")
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

    private fun clearCallbacks() {
        LibretroBridge.videoCallback = null
        LibretroBridge.audioSampleCallback = null
        LibretroBridge.audioBatchCallback = null
        LibretroBridge.inputPollCallback = null
        LibretroBridge.inputStateCallback = null
    }
}
