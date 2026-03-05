package com.bikearcade.emulator

import java.nio.ByteBuffer

/**
 * JNI bridge to the libretro C API. All native methods map 1:1 to libretro
 * core functions, with the native library dynamically loading core .so files
 * via dlopen/dlsym.
 *
 * This object is a singleton so callback pointers remain stable across the
 * JNI boundary.
 */
object LibretroBridge {

    init {
        System.loadLibrary("retro_jni")
    }

    // ── Core lifecycle ──────────────────────────────────────────────────

    /** Load a libretro core shared library from the given absolute path. */
    external fun nativeLoadCore(corePath: String): Boolean

    /** Unload the currently loaded core library. */
    external fun nativeUnloadCore()

    /** retro_api_version() - should return 1 for API v1 cores. */
    external fun retroApiVersion(): Int

    /** retro_init() */
    external fun retroInit()

    /** retro_deinit() */
    external fun retroDeinit()

    // ── System info ─────────────────────────────────────────────────────

    /**
     * retro_get_system_info() - populates and returns system info.
     * Returns an array: [libraryName, libraryVersion, validExtensions, needFullpath(0/1), blockExtract(0/1)]
     */
    external fun retroGetSystemInfo(): Array<String>

    /**
     * retro_get_system_av_info() - returns timing and geometry info.
     * Returns a double array: [fps, sampleRate, baseWidth, baseHeight]
     * Call after retroLoadGame().
     */
    external fun retroGetSystemAvInfo(): DoubleArray

    // ── Game lifecycle ──────────────────────────────────────────────────

    /**
     * retro_load_game() with a retro_game_info struct built from the given path.
     * Returns true on success.
     */
    external fun retroLoadGame(romPath: String): Boolean

    /** retro_unload_game() */
    external fun retroUnloadGame()

    // ── Run / Reset ─────────────────────────────────────────────────────

    /** retro_run() - advance emulation by one frame. */
    external fun retroRun()

    /** retro_reset() - soft-reset the loaded game. */
    external fun retroReset()

    // ── Memory (SRAM) ────────────────────────────────────────────────────

    /** RETRO_MEMORY_SAVE_RAM */
    const val MEMORY_SAVE_RAM = 0

    /** Read the core's SRAM as a byte array. Returns null if unavailable. */
    external fun retroGetMemoryData(memoryId: Int): ByteArray?

    /** Write data into the core's SRAM buffer. Returns true on success. */
    external fun retroSetMemoryData(memoryId: Int, data: ByteArray): Boolean

    // ── Callback registration ───────────────────────────────────────────

    /**
     * retro_set_environment() - the native side implements the environment
     * callback internally and forwards relevant queries to this bridge.
     */
    external fun retroSetEnvironment()

    /** retro_set_video_refresh() - register the video callback on the native side. */
    external fun retroSetVideoRefresh()

    /** retro_set_audio_sample() */
    external fun retroSetAudioSample()

    /** retro_set_audio_sample_batch() */
    external fun retroSetAudioSampleBatch()

    /** retro_set_input_poll() */
    external fun retroSetInputPoll()

    /** retro_set_input_state() */
    external fun retroSetInputState()

    // ── Java-side callback targets (called FROM native) ─────────────────

    // Pixel format constants matching libretro
    const val PIXEL_FORMAT_0RGB1555 = 0
    const val PIXEL_FORMAT_XRGB8888 = 1
    const val PIXEL_FORMAT_RGB565 = 2

    /** Receives a rendered video frame from native. */
    @JvmStatic
    var videoCallback: ((ByteBuffer, Int, Int, Int, Int) -> Unit)? = null

    /**
     * Called from JNI with the raw video frame data.
     * @param data        Direct ByteBuffer pointing to the pixel data
     * @param width       Frame width in pixels
     * @param height      Frame height in pixels
     * @param pitch       Row stride in bytes
     * @param pixelFormat Pixel format (0=0RGB1555, 1=XRGB8888, 2=RGB565)
     */
    @JvmStatic
    fun onVideoRefresh(data: ByteBuffer, width: Int, height: Int, pitch: Int, pixelFormat: Int) {
        videoCallback?.invoke(data, width, height, pitch, pixelFormat)
    }

    /** Receives a single audio sample pair from native. */
    @JvmStatic
    var audioSampleCallback: ((Short, Short) -> Unit)? = null

    @JvmStatic
    fun onAudioSample(left: Short, right: Short) {
        audioSampleCallback?.invoke(left, right)
    }

    /** Receives a batch of interleaved stereo audio samples. */
    @JvmStatic
    var audioBatchCallback: ((ShortArray, Int) -> Unit)? = null

    @JvmStatic
    fun onAudioSampleBatch(data: ShortArray, frames: Int) {
        audioBatchCallback?.invoke(data, frames)
    }

    /** Called when the core polls for input. */
    @JvmStatic
    var inputPollCallback: (() -> Unit)? = null

    @JvmStatic
    fun onInputPoll() {
        inputPollCallback?.invoke()
    }

    /**
     * Called when the core queries the state of a specific button.
     * @return non-zero if button is pressed
     */
    @JvmStatic
    var inputStateCallback: ((Int, Int, Int, Int) -> Int)? = null

    @JvmStatic
    fun onInputState(port: Int, device: Int, index: Int, id: Int): Int {
        return inputStateCallback?.invoke(port, device, index, id) ?: 0
    }
}
