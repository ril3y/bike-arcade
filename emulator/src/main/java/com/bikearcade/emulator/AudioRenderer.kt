package com.bikearcade.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Plays emulator audio using Android [AudioTrack].
 *
 * Writes are blocking (MODE_STREAM) which naturally paces the emulator.
 * The buffer is sized generously to absorb timing jitter.
 *
 * Configuration: 44100 Hz, 16-bit signed, stereo (interleaved L/R).
 */
class AudioRenderer {

    companion object {
        private const val TAG = "AudioRenderer"
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_MULTIPLIER = 2
    }

    private var audioTrack: AudioTrack? = null
    private var sampleRate: Int = 44100

    @Volatile
    private var started = false

    fun start(requestedSampleRate: Int = 44100) {
        if (started) return
        sampleRate = requestedSampleRate

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT
        )

        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid min buffer size: $minBufferSize")
            return
        }

        val bufferSize = minBufferSize * BUFFER_MULTIPLIER

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        started = true
        Log.i(TAG, "AudioTrack started: ${sampleRate}Hz stereo, buffer=$bufferSize bytes")
    }

    fun stop() {
        if (!started) return
        started = false

        audioTrack?.let { track ->
            try {
                track.stop()
            } catch (_: IllegalStateException) { }
            track.release()
        }
        audioTrack = null
        Log.i(TAG, "AudioTrack stopped")
    }

    fun writeSample(left: Short, right: Short) {
        if (!started) return
        audioTrack?.write(shortArrayOf(left, right), 0, 2)
    }

    fun writeBatch(samples: ShortArray, frames: Int) {
        if (!started) return
        val count = frames * 2
        val track = audioTrack ?: return
        // Write all samples, blocking if needed to maintain sync
        var offset = 0
        while (offset < count) {
            val written = track.write(samples, offset, count - offset)
            if (written < 0) break
            offset += written
        }
    }

    fun flush() {
        if (!started) return
        audioTrack?.flush()
    }

    fun attachTo(core: EmulatorCore) {
        core.setAudioCallback { samples, frames ->
            writeBatch(samples, frames)
        }
        // Single-sample callback is handled by C++ batching layer;
        // no need to wire the Kotlin side (avoids per-sample allocations)
    }
}
