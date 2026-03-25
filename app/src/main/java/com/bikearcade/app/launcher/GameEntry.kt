package com.bikearcade.app.launcher

import com.bikearcade.emulator.Platform
import com.bikearcade.emulator.PlatformRegistry

/**
 * Represents a ROM file discovered in the game library.
 *
 * @property name Display name derived from the filename (without extension).
 * @property path Absolute path to the ROM file on the filesystem.
 * @property platformId The platform ID (e.g., "nes", "snes") from [PlatformRegistry].
 * @property lastPlayed Timestamp (ms since epoch) when this game was last launched, or 0 if never.
 * @property fileSize File size in bytes.
 * @property artworkPath Local path to cached box art image, or null.
 */
data class GameEntry(
    val name: String,
    val path: String,
    val platformId: String,
    val lastPlayed: Long = 0L,
    val fileSize: Long = 0L,
    val artworkPath: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val publisher: String? = null,
    val developer: String? = null,
    val description: String? = null,
) {
    /** Resolved platform definition, or null if the platform ID is unknown. */
    val platform: Platform? get() = PlatformRegistry.byId(platformId)

    /** Key for metadata cache — platform + name. */
    val cacheKey: String get() = "$platformId:$name"
}
