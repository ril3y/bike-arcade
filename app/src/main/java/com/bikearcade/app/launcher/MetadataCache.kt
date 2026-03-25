package com.bikearcade.app.launcher

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Simple JSON file cache for game metadata so we don't need to
 * query the 42MB OpenVGDB SQLite on every app start.
 *
 * Format: { "platformId:gameName": { "releaseDate": "...", "genre": "...", ... }, ... }
 */
class MetadataCache(private val dir: File) {

    companion object {
        private const val TAG = "MetadataCache"
        private const val CACHE_FILENAME = "metadata_cache.json"
    }

    private val cacheFile = File(dir, CACHE_FILENAME)

    fun loadAll(): Map<String, GameMetadata> {
        if (!cacheFile.exists()) return emptyMap()
        return try {
            val json = JSONObject(cacheFile.readText())
            val result = mutableMapOf<String, GameMetadata>()
            for (key in json.keys()) {
                val obj = json.getJSONObject(key)
                result[key] = GameMetadata(
                    releaseDate = obj.optString("releaseDate").takeIf { it.isNotBlank() },
                    genre = obj.optString("genre").takeIf { it.isNotBlank() },
                    publisher = obj.optString("publisher").takeIf { it.isNotBlank() },
                    developer = obj.optString("developer").takeIf { it.isNotBlank() },
                    description = obj.optString("description").takeIf { it.isNotBlank() },
                )
            }
            Log.d(TAG, "Loaded ${result.size} cached metadata entries")
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load metadata cache: ${e.message}")
            emptyMap()
        }
    }

    fun saveAll(data: Map<String, GameMetadata>) {
        try {
            dir.mkdirs()
            val json = JSONObject()
            for ((key, meta) in data) {
                val obj = JSONObject()
                meta.releaseDate?.let { obj.put("releaseDate", it) }
                meta.genre?.let { obj.put("genre", it) }
                meta.publisher?.let { obj.put("publisher", it) }
                meta.developer?.let { obj.put("developer", it) }
                meta.description?.let { obj.put("description", it) }
                json.put(key, obj)
            }
            cacheFile.writeText(json.toString())
            Log.d(TAG, "Saved ${data.size} metadata entries to cache")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save metadata cache: ${e.message}")
        }
    }
}
