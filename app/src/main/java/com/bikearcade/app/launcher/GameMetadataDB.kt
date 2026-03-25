package com.bikearcade.app.launcher

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads and queries the OpenVGDB SQLite database for game metadata
 * (release date, genre, publisher, developer, description).
 *
 * The database is downloaded once on first use and cached locally.
 */
class GameMetadataDB(private val cacheDir: File) {

    companion object {
        private const val TAG = "GameMetadataDB"
        private const val DB_FILENAME = "openvgdb.sqlite"
        private const val DOWNLOAD_URL =
            "https://github.com/OpenVGDB/OpenVGDB/releases/latest/download/openvgdb.zip"
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 30000

        // OpenVGDB system IDs for our platforms
        private val SYSTEM_MAP = mapOf(
            "nes" to 25,
            "snes" to 26,
            "gb" to 19,
            "gba" to 20,
            "genesis" to 33,
        )
    }

    private val dbFile = File(cacheDir, DB_FILENAME)
    private var db: SQLiteDatabase? = null

    val isAvailable: Boolean get() = dbFile.exists() && dbFile.length() > 0

    /**
     * Download the OpenVGDB database if not already cached.
     * Returns true if the database is ready to use.
     */
    suspend fun ensureDownloaded(): Boolean = withContext(Dispatchers.IO) {
        if (isAvailable) {
            openDb()
            return@withContext true
        }

        try {
            Log.i(TAG, "Downloading OpenVGDB...")
            cacheDir.mkdirs()
            val conn = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.instanceFollowRedirects = true

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Download failed: HTTP ${conn.responseCode}")
                return@withContext false
            }

            // The download is a zip — extract the sqlite file
            val tempFile = File(cacheDir, "$DB_FILENAME.tmp")
            ZipInputStream(conn.inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".sqlite")) {
                        tempFile.outputStream().use { out ->
                            zis.copyTo(out)
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
            conn.disconnect()

            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.renameTo(dbFile)
                Log.i(TAG, "OpenVGDB downloaded: ${dbFile.length() / 1024}KB")
                openDb()
                return@withContext true
            } else {
                tempFile.delete()
                Log.w(TAG, "Download produced no valid sqlite file")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download OpenVGDB: ${e.message}")
            return@withContext false
        }
    }

    private fun openDb() {
        if (db?.isOpen == true) return
        try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open database: ${e.message}")
        }
    }

    /**
     * Look up metadata for a game by name and platform.
     * Returns a [GameMetadata] if found, null otherwise.
     */
    fun lookup(gameName: String, platformId: String): GameMetadata? {
        val database = db ?: return null
        val systemId = SYSTEM_MAP[platformId] ?: return null

        // Try exact match on releaseTitleName first
        val query = """
            SELECT DISTINCT r.releaseDate, r.releaseGenre, r.releasePublisher,
                   r.releaseDeveloper, r.releaseDescription
            FROM RELEASES r
            JOIN ROMs rom ON r.romID = rom.romID
            WHERE rom.systemID = ?
              AND r.releaseTitleName = ?
              AND r.regionLocalizedID = 21
            LIMIT 1
        """.trimIndent()

        try {
            database.rawQuery(query, arrayOf(systemId.toString(), gameName)).use { cursor ->
                if (cursor.moveToFirst()) {
                    return GameMetadata(
                        releaseDate = cursor.getString(0)?.takeIf { it.isNotBlank() },
                        genre = cursor.getString(1)?.takeIf { it.isNotBlank() },
                        publisher = cursor.getString(2)?.takeIf { it.isNotBlank() },
                        developer = cursor.getString(3)?.takeIf { it.isNotBlank() },
                        description = cursor.getString(4)?.takeIf { it.isNotBlank() },
                    )
                }
            }

            // Fallback: fuzzy match with LIKE
            val fuzzyQuery = """
                SELECT DISTINCT r.releaseDate, r.releaseGenre, r.releasePublisher,
                       r.releaseDeveloper, r.releaseDescription
                FROM RELEASES r
                JOIN ROMs rom ON r.romID = rom.romID
                WHERE rom.systemID = ?
                  AND r.releaseTitleName LIKE ?
                LIMIT 1
            """.trimIndent()

            database.rawQuery(fuzzyQuery, arrayOf(systemId.toString(), "%$gameName%")).use { cursor ->
                if (cursor.moveToFirst()) {
                    return GameMetadata(
                        releaseDate = cursor.getString(0)?.takeIf { it.isNotBlank() },
                        genre = cursor.getString(1)?.takeIf { it.isNotBlank() },
                        publisher = cursor.getString(2)?.takeIf { it.isNotBlank() },
                        developer = cursor.getString(3)?.takeIf { it.isNotBlank() },
                        description = cursor.getString(4)?.takeIf { it.isNotBlank() },
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lookup failed for '$gameName': ${e.message}")
        }

        return null
    }

    fun close() {
        db?.close()
        db = null
    }
}

data class GameMetadata(
    val releaseDate: String? = null,
    val genre: String? = null,
    val publisher: String? = null,
    val developer: String? = null,
    val description: String? = null,
)
