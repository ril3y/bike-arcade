package com.bikearcade.app.launcher

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Scans the ROM directory for supported game files and maintains
 * a reactive list of discovered [GameEntry] items.
 *
 * ROM directory: /storage/emulated/0/BikeArcade/roms/
 * Supported formats: .nes, .smc, .sfc, .zip (containing supported ROMs)
 */
class GameLibrary(context: Context, private val thumbnailManager: ThumbnailManager? = null) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _games = MutableStateFlow<List<GameEntry>>(emptyList())
    /** Reactive list of discovered games, sorted by last played then alphabetically. */
    val games: StateFlow<List<GameEntry>> = _games.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    /** Whether a scan is currently in progress. */
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * Scan the ROM directory for supported files.
     * Updates [games] with the results, sorted by last played (descending) then name.
     */
    suspend fun scan() {
        _isScanning.value = true
        try {
            val entries = withContext(Dispatchers.IO) {
                val romDir = File(ROM_DIRECTORY)
                if (!romDir.exists() || !romDir.isDirectory) {
                    return@withContext emptyList<GameEntry>()
                }

                romDir.listFiles()
                    ?.filter { it.isFile }
                    ?.mapNotNull { file -> parseRomFile(file) }
                    ?: emptyList()
            }

            // Attach cached thumbnails
            val entriesWithArt = entries.map { entry ->
                val cached = thumbnailManager?.getCachedThumbnail(entry)
                if (cached != null) entry.copy(artworkPath = cached.absolutePath) else entry
            }

            _games.value = entriesWithArt.sortedWith(
                compareByDescending<GameEntry> { it.lastPlayed }
                    .thenBy { it.name.lowercase() }
            )
        } finally {
            _isScanning.value = false
        }
    }

    /**
     * Download missing thumbnails in the background and update entries as they arrive.
     */
    suspend fun fetchMissingThumbnails() {
        val tm = thumbnailManager ?: return
        val current = _games.value
        for (entry in current) {
            if (entry.artworkPath != null) continue
            val file = tm.fetchThumbnail(entry)
            if (file != null) {
                _games.value = _games.value.map { g ->
                    if (g.path == entry.path) g.copy(artworkPath = file.absolutePath) else g
                }
            }
        }
    }

    /**
     * Record that a game was launched, updating its lastPlayed timestamp.
     */
    fun recordLaunch(entry: GameEntry) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(lastPlayedKey(entry.path), now).apply()

        _games.value = _games.value.map { game ->
            if (game.path == entry.path) game.copy(lastPlayed = now) else game
        }.sortedWith(
            compareByDescending<GameEntry> { it.lastPlayed }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun parseRomFile(file: File): GameEntry? {
        val extension = file.extension.lowercase()

        return when (extension) {
            "nes" -> createEntry(file, ConsoleType.NES)
            "smc", "sfc" -> createEntry(file, ConsoleType.SNES)
            "zip" -> parseZipFile(file)
            else -> null
        }
    }

    /**
     * Peek inside a .zip file to determine the console type from the
     * first supported ROM found within.
     */
    private fun parseZipFile(file: File): GameEntry? {
        return try {
            ZipFile(file).use { zip ->
                val romEntry = zip.entries().asSequence().firstOrNull { entry ->
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    ext in SUPPORTED_EXTENSIONS
                }

                if (romEntry != null) {
                    val innerExt = romEntry.name.substringAfterLast('.', "").lowercase()
                    val consoleType = extensionToConsole(innerExt) ?: return@use null
                    // Use the inner ROM name for display, but the zip path for launching
                    val displayName = romEntry.name
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                        .cleanRomName()

                    GameEntry(
                        name = displayName,
                        path = file.absolutePath,
                        consoleType = consoleType,
                        lastPlayed = getLastPlayed(file.absolutePath),
                        fileSize = file.length()
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            // Corrupt or unreadable zip
            null
        }
    }

    private fun createEntry(file: File, consoleType: ConsoleType): GameEntry {
        return GameEntry(
            name = file.nameWithoutExtension.cleanRomName(),
            path = file.absolutePath,
            consoleType = consoleType,
            lastPlayed = getLastPlayed(file.absolutePath),
            fileSize = file.length()
        )
    }

    private fun getLastPlayed(path: String): Long {
        return prefs.getLong(lastPlayedKey(path), 0L)
    }

    private fun lastPlayedKey(path: String): String = "last_played_$path"

    companion object {
        private const val PREFS_NAME = "game_library"
        const val ROM_DIRECTORY = "/storage/emulated/0/BikeArcade/roms"

        /** File extensions supported directly (not inside zips). */
        private val SUPPORTED_EXTENSIONS = setOf("nes", "smc", "sfc")

        private fun extensionToConsole(ext: String): ConsoleType? = when (ext) {
            "nes" -> ConsoleType.NES
            "smc", "sfc" -> ConsoleType.SNES
            else -> null
        }

        /**
         * Clean up ROM filenames for display:
         * - Remove common tags like [!], (U), (USA), (Europe), etc.
         * - Replace underscores with spaces.
         * - Trim extra whitespace.
         */
        private fun String.cleanRomName(): String {
            return this
                .replace(Regex("\\[.*?]"), "")     // Remove [tags]
                .replace(Regex("\\(.*?\\)"), "")   // Remove (tags)
                .replace('_', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
