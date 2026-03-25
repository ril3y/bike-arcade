package com.bikearcade.app.launcher

import android.content.Context
import android.content.SharedPreferences
import com.bikearcade.emulator.PlatformRegistry
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
 * Platforms are discovered from [PlatformRegistry] — adding a new platform
 * there automatically makes its ROMs scannable here.
 *
 * ROM directory: /storage/emulated/0/BikeArcade/roms/
 */
class GameLibrary(
    context: Context,
    private val thumbnailManager: ThumbnailManager? = null,
    private val metadataDB: GameMetadataDB? = null,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _games = MutableStateFlow<List<GameEntry>>(emptyList())
    val games: StateFlow<List<GameEntry>> = _games.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val metadataCache = MetadataCache(
        File(ROM_DIRECTORY).parentFile ?: File("/storage/emulated/0/BikeArcade")
    )

    /**
     * Scan ROMs and apply ALL cached data (thumbnails + metadata) before
     * publishing to the UI. Games appear fully enriched on first frame.
     */
    suspend fun scan() {
        _isScanning.value = true
        try {
            val entries = withContext(Dispatchers.IO) {
                val romDir = File(ROM_DIRECTORY)
                if (!romDir.exists() || !romDir.isDirectory) {
                    return@withContext emptyList<GameEntry>()
                }

                // Load metadata cache once (small JSON, <50KB)
                val cachedMeta = metadataCache.loadAll()

                romDir.listFiles()
                    ?.filter { it.isFile }
                    ?.mapNotNull { file -> parseRomFile(file) }
                    ?.map { entry ->
                        // Apply cached thumbnail
                        val art = thumbnailManager?.getCachedThumbnail(entry)
                        val withArt = if (art != null) entry.copy(artworkPath = art.absolutePath) else entry

                        // Apply cached metadata
                        val meta = cachedMeta[withArt.cacheKey]
                        if (meta != null) {
                            withArt.copy(
                                releaseDate = meta.releaseDate,
                                genre = meta.genre,
                                publisher = meta.publisher,
                                developer = meta.developer,
                                description = meta.description,
                            )
                        } else withArt
                    }
                    ?: emptyList()
            }

            _games.value = entries.sortedWith(
                compareByDescending<GameEntry> { it.lastPlayed }
                    .thenBy { it.name.lowercase() }
            )
        } finally {
            _isScanning.value = false
        }
    }

    /**
     * Fetch thumbnails for games that don't have cached art yet.
     * Batches updates every 3 downloads to reduce recomposition churn.
     */
    suspend fun fetchMissingThumbnails() {
        val tm = thumbnailManager ?: return
        val missing = _games.value.filter { it.artworkPath == null }
        if (missing.isEmpty()) return

        val updates = mutableMapOf<String, String>() // path -> artworkPath
        for (entry in missing) {
            val file = tm.fetchThumbnail(entry)
            if (file != null) {
                updates[entry.path] = file.absolutePath
            }
            // Batch-apply every 3 downloads or at the end
            if (updates.size >= 3 || entry === missing.last()) {
                if (updates.isNotEmpty()) {
                    _games.value = _games.value.map { g ->
                        val art = updates[g.path]
                        if (art != null) g.copy(artworkPath = art) else g
                    }
                    updates.clear()
                }
            }
        }
    }

    /**
     * Enrich games missing metadata. Queries OpenVGDB only for unknowns,
     * then saves results to cache for instant loading next time.
     */
    suspend fun fetchMetadata() {
        val mdb = metadataDB ?: return

        // Only look up games that scan() didn't enrich from cache
        val needsLookup = _games.value.filter { it.releaseDate == null }
        if (needsLookup.isEmpty()) return

        // Download OpenVGDB if needed (42MB, first run only)
        if (!mdb.ensureDownloaded()) return

        // Query only the missing games
        val newEntries = mutableMapOf<String, GameMetadata>()
        val enriched = withContext(Dispatchers.IO) {
            needsLookup.mapNotNull { entry ->
                val meta = mdb.lookup(entry.name, entry.platformId) ?: return@mapNotNull null
                newEntries[entry.cacheKey] = meta
                entry.path to meta
            }.toMap()
        }

        // Single bulk update to the games list
        if (enriched.isNotEmpty()) {
            _games.value = _games.value.map { entry ->
                val meta = enriched[entry.path]
                if (meta != null) {
                    entry.copy(
                        releaseDate = meta.releaseDate,
                        genre = meta.genre,
                        publisher = meta.publisher,
                        developer = meta.developer,
                        description = meta.description,
                    )
                } else entry
            }

            // Merge into cache for next launch
            withContext(Dispatchers.IO) {
                val existing = metadataCache.loadAll().toMutableMap()
                existing.putAll(newEntries)
                metadataCache.saveAll(existing)
            }
        }
    }

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
            "zip" -> parseZipFile(file)
            else -> {
                val platform = PlatformRegistry.fromExtension(extension) ?: return null
                createEntry(file, platform.id)
            }
        }
    }

    private fun parseZipFile(file: File): GameEntry? {
        return try {
            ZipFile(file).use { zip ->
                val romEntry = zip.entries().asSequence().firstOrNull { entry ->
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    ext in PlatformRegistry.ALL_ROM_EXTENSIONS
                }

                if (romEntry != null) {
                    val innerExt = romEntry.name.substringAfterLast('.', "").lowercase()
                    val platform = PlatformRegistry.fromExtension(innerExt) ?: return@use null
                    val displayName = romEntry.name
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                        .cleanRomName()

                    GameEntry(
                        name = displayName,
                        path = file.absolutePath,
                        platformId = platform.id,
                        lastPlayed = getLastPlayed(file.absolutePath),
                        fileSize = file.length()
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createEntry(file: File, platformId: String): GameEntry {
        return GameEntry(
            name = file.nameWithoutExtension.cleanRomName(),
            path = file.absolutePath,
            platformId = platformId,
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

        private fun String.cleanRomName(): String {
            return this
                .replace(Regex("\\[.*?]"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .replace('_', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
