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
 * Thumbnails are cached locally in /storage/emulated/0/BikeArcade/thumbnails/{platformId}/
 */
class ThumbnailManager(private val context: Context) {

    private val cacheDir = File("/storage/emulated/0/BikeArcade/thumbnails").also { it.mkdirs() }

    companion object {
        private const val TAG = "ThumbnailManager"
        private const val BASE_URL = "https://thumbnails.libretro.com"
        private const val CONNECT_TIMEOUT = 5000
        private const val READ_TIMEOUT = 10000
    }

    fun getCachedThumbnail(entry: GameEntry): File? {
        val file = thumbnailFileFor(entry)
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun fetchThumbnail(entry: GameEntry): File? = withContext(Dispatchers.IO) {
        val localFile = thumbnailFileFor(entry)
        if (localFile.exists() && localFile.length() > 0) {
            return@withContext localFile
        }

        val markerFile = File(localFile.parentFile, "${localFile.nameWithoutExtension}.notfound")
        if (markerFile.exists()) return@withContext null

        val systemName = entry.platform?.thumbnailSystem ?: return@withContext null

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

        markerFile.parentFile?.mkdirs()
        markerFile.createNewFile()
        Log.d(TAG, "No thumbnail found for ${entry.name}")
        null
    }

    private fun buildNameVariants(cleanName: String): List<String> {
        val baseNames = mutableListOf(cleanName)

        // Try Roman numeral → Arabic numeral conversion (II→2, III→3, IV→4, etc.)
        val romanConverted = convertRomanNumerals(cleanName)
        if (romanConverted != cleanName) baseNames.add(romanConverted)

        // Try & ↔ and swaps
        if ("&" in cleanName) baseNames.add(cleanName.replace("&", "and"))
        if (" and " in cleanName.lowercase()) {
            baseNames.add(cleanName.replace(Regex("\\band\\b", RegexOption.IGNORE_CASE), "&"))
        }

        val regions = listOf("(USA)", "(USA, Europe)", "(Europe)", "(Japan, USA)", "(World)")
        val variants = mutableListOf<String>()
        for (base in baseNames.distinct()) {
            variants.add(base)
            for (region in regions) {
                variants.add("$base $region")
            }
        }

        return variants.distinct()
    }

    private val ROMAN_MAP = listOf(
        "VIII" to "8", "VII" to "7", "VI" to "6",
        "IV" to "4", "IX" to "9",
        "XIII" to "13", "XII" to "12", "XI" to "11",
        "III" to "3", "II" to "2",
        "X" to "10", "V" to "5",
    )

    private fun convertRomanNumerals(name: String): String {
        var result = name
        for ((roman, arabic) in ROMAN_MAP) {
            // Match Roman numeral as a whole word (word boundary)
            result = result.replace(Regex("\\b$roman\\b"), arabic)
        }
        return result
    }

    private fun thumbnailFileFor(entry: GameEntry): File {
        val safeName = entry.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return File(File(cacheDir, entry.platformId), "$safeName.png")
    }
}
