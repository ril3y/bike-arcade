package com.bikearcade.app.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Downloads and caches game box art from the libretro thumbnail server.
 *
 * URL format: https://thumbnails.libretro.com/{System}/Named_Boxarts/{GameName}.png
 *
 * Thumbnails are cached locally in /storage/emulated/0/BikeArcade/thumbnails/{system}/
 */
class ThumbnailManager(private val context: Context) {

    private val cacheDir = File("/storage/emulated/0/BikeArcade/thumbnails").also { it.mkdirs() }

    companion object {
        private const val TAG = "ThumbnailManager"
        private const val BASE_URL = "https://thumbnails.libretro.com"
        private const val CONNECT_TIMEOUT = 5000
        private const val READ_TIMEOUT = 10000

        private val SYSTEM_NAMES = mapOf(
            ConsoleType.NES to "Nintendo - Nintendo Entertainment System",
            ConsoleType.SNES to "Nintendo - Super Nintendo Entertainment System",
        )
    }

    /**
     * Get the local cached thumbnail path for a game, or null if not cached.
     */
    fun getCachedThumbnail(entry: GameEntry): File? {
        val file = thumbnailFileFor(entry)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Download the thumbnail for a game entry if not already cached.
     * Returns the local file path on success, null on failure.
     */
    suspend fun fetchThumbnail(entry: GameEntry): File? = withContext(Dispatchers.IO) {
        val localFile = thumbnailFileFor(entry)
        if (localFile.exists() && localFile.length() > 0) {
            return@withContext localFile
        }

        // Mark as attempted even if it fails (create empty marker to avoid retrying)
        val markerFile = File(localFile.parentFile, "${localFile.nameWithoutExtension}.notfound")

        // Skip if we already know it's not found
        if (markerFile.exists()) return@withContext null

        val systemName = SYSTEM_NAMES[entry.consoleType] ?: return@withContext null

        // Try the ROM name as-is first, then with common No-Intro naming patterns
        val namesToTry = buildNameVariants(entry.name)

        for (name in namesToTry) {
            val encodedName = URLEncoder.encode(name, "UTF-8")
                .replace("+", "%20")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%2C", ",")
                .replace("%27", "'")
                .replace("%21", "!")

            val url = "$BASE_URL/${URLEncoder.encode(systemName, "UTF-8").replace("+", "%20")}/Named_Boxarts/$encodedName.png"

            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    localFile.parentFile?.mkdirs()
                    connection.inputStream.use { input ->
                        localFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Downloaded thumbnail for ${entry.name}: $url")
                    return@withContext localFile
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.d(TAG, "Failed to fetch thumbnail: $url (${e.message})")
            }
        }

        // Mark as not found so we don't retry
        markerFile.parentFile?.mkdirs()
        markerFile.createNewFile()
        Log.d(TAG, "No thumbnail found for ${entry.name}")
        null
    }

    /**
     * Build name variants to try against the libretro thumbnail server.
     * The server uses No-Intro naming convention which includes region tags.
     */
    private fun buildNameVariants(cleanName: String): List<String> {
        val variants = mutableListOf<String>()

        // The clean name as-is (already stripped of tags by GameLibrary)
        variants.add(cleanName)

        // With common region tags added back
        variants.add("$cleanName (USA)")
        variants.add("$cleanName (USA, Europe)")
        variants.add("$cleanName (Europe)")
        variants.add("$cleanName (Japan, USA)")
        variants.add("$cleanName (World)")

        // Replace ampersand variants
        if ("&" in cleanName) {
            val altName = cleanName.replace("&", "and")
            variants.add("$altName (USA)")
        }
        if (" and " in cleanName.lowercase()) {
            val altName = cleanName.replace(Regex("\\band\\b", RegexOption.IGNORE_CASE), "&")
            variants.add("$altName (USA)")
        }

        return variants
    }

    private fun thumbnailFileFor(entry: GameEntry): File {
        val systemDir = when (entry.consoleType) {
            ConsoleType.NES -> "nes"
            ConsoleType.SNES -> "snes"
        }
        // Sanitize name for filesystem
        val safeName = entry.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return File(File(cacheDir, systemDir), "$safeName.png")
    }
}
