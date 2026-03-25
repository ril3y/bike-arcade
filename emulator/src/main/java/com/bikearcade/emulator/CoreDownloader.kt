package com.bikearcade.emulator

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Metadata for a downloadable libretro core.
 *
 * @property id Short identifier matching [Platform.id] (e.g., "nes", "snes").
 * @property name Human-readable label for the UI (e.g., "NES (FCEUmm)").
 * @property platform Platform badge / short name (e.g., "NES", "SNES").
 * @property fileName The .so filename expected by [EmulatorCore] (e.g., "fceumm_libretro_android.so").
 * @property downloadUrl Full URL to the buildbot .so.zip archive.
 */
data class CoreInfo(
    val id: String,
    val name: String,
    val platform: String,
    val fileName: String,
    val downloadUrl: String,
)

/**
 * Downloads libretro cores on demand from the official buildbot.
 *
 * Libretro cores are GPL-licensed and cannot be bundled with the app.
 * This class fetches them at runtime into [Context.getFilesDir]/cores/,
 * which is the same directory [EmulatorCore] loads from.
 *
 * Usage:
 * ```
 * val downloader = CoreDownloader(context)
 * if (!downloader.isInstalled(CoreDownloader.coreForPlatform(platform)!!)) {
 *     val success = downloader.downloadCore(core)
 * }
 * ```
 */
class CoreDownloader(private val context: Context) {

    companion object {
        private const val TAG = "CoreDownloader"
        private const val BUILDBOT_BASE =
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"

        val AVAILABLE_CORES = listOf(
            CoreInfo(
                id = "nes",
                name = "NES (FCEUmm)",
                platform = "NES",
                fileName = "fceumm_libretro_android.so",
                downloadUrl = "$BUILDBOT_BASE/fceumm_libretro_android.so.zip",
            ),
            CoreInfo(
                id = "snes",
                name = "SNES (Snes9x)",
                platform = "SNES",
                fileName = "snes9x_libretro_android.so",
                downloadUrl = "$BUILDBOT_BASE/snes9x_libretro_android.so.zip",
            ),
            CoreInfo(
                id = "gba",
                name = "GBA (mGBA)",
                platform = "GBA",
                fileName = "mgba_libretro_android.so",
                downloadUrl = "$BUILDBOT_BASE/mgba_libretro_android.so.zip",
            ),
            CoreInfo(
                id = "gb",
                name = "Game Boy (Gambatte)",
                platform = "GB",
                fileName = "gambatte_libretro_android.so",
                downloadUrl = "$BUILDBOT_BASE/gambatte_libretro_android.so.zip",
            ),
            CoreInfo(
                id = "genesis",
                name = "Genesis (Genesis Plus GX)",
                platform = "GEN",
                fileName = "genesis_plus_gx_libretro_android.so",
                downloadUrl = "$BUILDBOT_BASE/genesis_plus_gx_libretro_android.so.zip",
            ),
        )

        /**
         * Find the [CoreInfo] that corresponds to a [Platform], matched by core library filename.
         * Returns null if the platform's core is not in the available list.
         */
        fun coreForPlatform(platform: Platform): CoreInfo? =
            AVAILABLE_CORES.firstOrNull { it.fileName == platform.coreLibrary }
    }

    private val coresDir get() = File(context.filesDir, "cores").also { it.mkdirs() }

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _downloading = MutableStateFlow<String?>(null)
    val downloading: StateFlow<String?> = _downloading

    /** Check whether a core's .so file exists in the private cores directory. */
    fun isInstalled(core: CoreInfo): Boolean =
        File(coresDir, core.fileName).exists()

    /** Return all cores that have been downloaded. */
    fun getInstalledCores(): List<CoreInfo> =
        AVAILABLE_CORES.filter { isInstalled(it) }

    /** Return the .so [File] if it exists, or null. */
    fun getCoreFile(core: CoreInfo): File? {
        val file = File(coresDir, core.fileName)
        return if (file.exists()) file else null
    }

    /**
     * Download and extract a core from the libretro buildbot.
     *
     * The buildbot serves `.so.zip` archives containing a single `.so` file.
     * This method streams the zip, extracts the .so to a temp file, then
     * atomically renames it to the final location.
     *
     * Observe [downloadProgress] (0f..1f) and [downloading] (core id or null)
     * to drive a progress UI.
     *
     * @return true on success, false on failure (error is logged).
     */
    suspend fun downloadCore(core: CoreInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            _downloading.value = core.id
            _downloadProgress.value = 0f

            val url = URL(core.downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000

            val totalSize = conn.contentLength.toLong()
            val tempFile = File(coresDir, "${core.fileName}.tmp")

            if (core.downloadUrl.endsWith(".zip")) {
                ZipInputStream(conn.inputStream).use { zis ->
                    val entry = zis.nextEntry
                    if (entry != null) {
                        FileOutputStream(tempFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var totalRead = 0L
                            while (true) {
                                val read = zis.read(buffer)
                                if (read == -1) break
                                fos.write(buffer, 0, read)
                                totalRead += read
                                if (totalSize > 0) {
                                    _downloadProgress.value =
                                        (totalRead.toFloat() / totalSize).coerceAtMost(1f)
                                }
                            }
                        }
                    }
                }
            } else {
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            totalRead += read
                            if (totalSize > 0) {
                                _downloadProgress.value =
                                    (totalRead.toFloat() / totalSize).coerceAtMost(1f)
                            }
                        }
                    }
                }
            }
            conn.disconnect()

            // Atomic rename from temp to final destination
            val finalFile = File(coresDir, core.fileName)
            if (!tempFile.renameTo(finalFile)) {
                // renameTo can fail across filesystems; fall back to copy+delete
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            finalFile.setExecutable(true)

            _downloadProgress.value = 1f
            Log.i(TAG, "Downloaded core: ${core.name} (${finalFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download ${core.name}", e)
            // Clean up partial temp file
            File(coresDir, "${core.fileName}.tmp").delete()
            false
        } finally {
            _downloading.value = null
        }
    }

    /** Delete a downloaded core. */
    fun deleteCore(core: CoreInfo) {
        val file = File(coresDir, core.fileName)
        if (file.delete()) {
            Log.i(TAG, "Deleted core: ${core.name}")
        }
    }
}
