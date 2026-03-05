package com.bikearcade.emulator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer

/**
 * Compose-compatible surface that renders libretro video frames.
 *
 * Video frames are double-buffered: the emulator thread writes into a back
 * buffer while a dedicated render thread draws the front buffer to the
 * SurfaceView. This prevents lockCanvas() from blocking the emulator loop.
 */
@Composable
fun EmulatorSurface(
    core: EmulatorCore,
    modifier: Modifier = Modifier
) {
    val renderer = remember { FrameRenderer() }

    DisposableEffect(core) {
        core.setVideoCallback { data, width, height, pitch, pixelFormat ->
            renderer.submitFrame(data, width, height, pitch, pixelFormat)
        }
        onDispose {
            renderer.stop()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            renderer.surfaceHolder = holder
                            renderer.start()
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            renderer.onSurfaceChanged(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            renderer.stop()
                            renderer.surfaceHolder = null
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Double-buffered frame renderer. The emulator thread copies pixels into a
 * back-buffer bitmap, then a render thread swaps and draws to the surface.
 * This keeps lockCanvas() off the emulator thread entirely.
 */
internal class FrameRenderer {

    @Volatile
    var surfaceHolder: SurfaceHolder? = null

    private var bitmapA: Bitmap? = null
    private var bitmapB: Bitmap? = null
    private val lock = Object()
    @Volatile
    private var frontBitmap: Bitmap? = null
    private var frameReady = false

    private val paint = Paint(0) // No flags — fastest draw
    private var destRect = Rect()
    private var surfaceWidth = 1280
    private var surfaceHeight = 960
    private var needsClear = true
    private var lastFrameWidth = 0
    private var lastFrameHeight = 0

    @Volatile
    private var running = false
    private var renderThread: Thread? = null

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        lastFrameWidth = 0  // Force destRect recompute
        lastFrameHeight = 0
        needsClear = true
    }

    fun start() {
        if (running) return
        running = true
        renderThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY)
            renderLoop()
        }, "FrameRenderer").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        renderThread?.join(1000)
        renderThread = null
    }

    /**
     * Called from the emulator thread. Copies pixel data into the back bitmap
     * and signals the render thread. Never blocks on canvas operations.
     */
    fun submitFrame(data: ByteBuffer, width: Int, height: Int, pitch: Int, pixelFormat: Int) {
        try {
            val back = getOrCreateBackBitmap(width, height)
            copyPixelsToBitmap(data, back, width, height, pitch)
            if (width != lastFrameWidth || height != lastFrameHeight) {
                computeDestRect(width, height)
                lastFrameWidth = width
                lastFrameHeight = height
            }

            synchronized(lock) {
                frontBitmap = back
                frameReady = true
                lock.notify()
            }
        } catch (e: Exception) {
            android.util.Log.w("FrameRenderer", "submitFrame error: ${e.message}")
        }
    }

    private fun renderLoop() {
        while (running) {
            val bitmap: Bitmap?
            synchronized(lock) {
                while (!frameReady && running) {
                    try { lock.wait(16) } catch (_: InterruptedException) { return }
                }
                bitmap = frontBitmap
                frameReady = false
            }

            val holder = surfaceHolder
            if (bitmap == null || holder == null) continue

            var canvas: Canvas? = null
            try {
                // Use hardware-accelerated canvas (GPU) on API 26+
                canvas = if (android.os.Build.VERSION.SDK_INT >= 26) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    if (needsClear) {
                        canvas.drawColor(Color.BLACK)
                        needsClear = false
                    }
                    canvas.drawBitmap(bitmap, null, destRect, paint)
                }
            } catch (_: Exception) {
                // Surface may have been destroyed
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun getOrCreateBackBitmap(width: Int, height: Int): Bitmap {
        val a = bitmapA
        val b = bitmapB

        val back = when {
            a != null && a !== frontBitmap && a.width == width && a.height == height -> a
            b != null && b !== frontBitmap && b.width == width && b.height == height -> b
            else -> {
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                if (bitmapA == null || bitmapA === frontBitmap) {
                    bitmapA?.let { if (it !== frontBitmap) it.recycle() }
                    bitmapA = bmp
                } else {
                    bitmapB?.let { if (it !== frontBitmap) it.recycle() }
                    bitmapB = bmp
                }
                bmp
            }
        }
        return back
    }

    /**
     * All frames arrive as XRGB8888 (native code does RGB565 conversion).
     * Pitch is always width*4, so copyPixelsFromBuffer works directly.
     */
    private fun copyPixelsToBitmap(
        data: ByteBuffer,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        pitch: Int
    ) {
        data.rewind()
        bitmap.copyPixelsFromBuffer(data)
    }

    private fun computeDestRect(frameWidth: Int, frameHeight: Int) {
        val frameAspect = frameWidth.toFloat() / frameHeight.toFloat()
        val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()

        val scaledWidth: Int
        val scaledHeight: Int

        if (frameAspect > surfaceAspect) {
            scaledWidth = surfaceWidth
            scaledHeight = (surfaceWidth / frameAspect).toInt()
        } else {
            scaledHeight = surfaceHeight
            scaledWidth = (surfaceHeight * frameAspect).toInt()
        }

        val left = (surfaceWidth - scaledWidth) / 2
        val top = (surfaceHeight - scaledHeight) / 2
        destRect.set(left, top, left + scaledWidth, top + scaledHeight)
        needsClear = true  // Clear once on dimension change
    }
}
