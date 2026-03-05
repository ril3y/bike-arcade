package com.bikearcade.emulator

import android.os.Process
import android.util.Log

/**
 * Dedicated thread that drives the emulator at a steady 60 fps.
 *
 * The thread uses a precise sleep/spin-wait loop to maintain the target
 * frame interval of 16,666,667 ns (~16.67 ms). It calls [EmulatorCore.run]
 * on every tick, which in turn calls retro_run() through the JNI bridge.
 */
class EmulatorThread(
    private val core: EmulatorCore
) {

    companion object {
        private const val TAG = "EmulatorThread"
    }

    /** Target nanoseconds per frame, derived from the core's actual FPS. */
    private val frameNs: Long = (1_000_000_000.0 / core.coreFps).toLong()

    // ── State ───────────────────────────────────────────────────────────

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    private var thread: Thread? = null

    private val pauseLock = Object()

    /** True if the emulator loop is currently active. */
    val isRunning: Boolean get() = running

    /** True if the emulator loop is paused. */
    val isPaused: Boolean get() = paused

    // ── Control ─────────────────────────────────────────────────────────

    /**
     * Start the emulator loop on a new thread. The core must have a game
     * loaded before calling this.
     *
     * @throws IllegalStateException if the thread is already running
     */
    fun start() {
        check(!running) { "Emulator thread is already running." }
        check(core.state == EmulatorCore.State.GAME_LOADED) {
            "Cannot start emulator thread: no game loaded."
        }

        running = true
        paused = false

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            Log.i(TAG, "Emulator thread started")
            emulatorLoop()
            Log.i(TAG, "Emulator thread stopped")
        }, "EmulatorThread").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Stop the emulator loop and wait for the thread to join.
     */
    fun stop() {
        if (!running) return
        running = false

        // Wake up if paused
        synchronized(pauseLock) {
            paused = false
            pauseLock.notifyAll()
        }

        thread?.join(2000)
        thread = null
    }

    /**
     * Pause the emulator loop. Frames stop being generated but the
     * thread stays alive so resume is instant.
     */
    fun pause() {
        if (!running || paused) return
        paused = true
        Log.d(TAG, "Paused")
    }

    /**
     * Resume the emulator loop after a pause.
     */
    fun resume() {
        if (!running || !paused) return
        synchronized(pauseLock) {
            paused = false
            pauseLock.notifyAll()
        }
        Log.d(TAG, "Resumed")
    }

    // ── Loop ────────────────────────────────────────────────────────────

    private fun emulatorLoop() {
        var nextFrameTime = System.nanoTime()

        while (running) {
            // Handle pause
            if (paused) {
                synchronized(pauseLock) {
                    while (paused && running) {
                        try {
                            pauseLock.wait()
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
                nextFrameTime = System.nanoTime()
                continue
            }

            // Run one frame
            try {
                core.run()
            } catch (e: Exception) {
                Log.e(TAG, "Error in emulator frame", e)
                running = false
                break
            }

            // Frame pacing: sleep then spin-wait for precision
            nextFrameTime += frameNs
            val now = System.nanoTime()
            val sleepNs = nextFrameTime - now

            if (sleepNs > 2_000_000L) {
                try {
                    Thread.sleep((sleepNs - 1_000_000L) / 1_000_000)
                } catch (_: InterruptedException) {
                    break
                }
            }
            // Spin-wait for the last ~1ms for precise timing
            while (System.nanoTime() < nextFrameTime) { /* spin */ }

            if (System.nanoTime() - nextFrameTime > frameNs * 3) {
                // Fell too far behind — reset clock to prevent burst
                nextFrameTime = System.nanoTime()
            }
        }
    }
}
