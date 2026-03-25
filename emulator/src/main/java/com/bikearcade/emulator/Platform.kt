package com.bikearcade.emulator

/**
 * Defines a supported emulation platform with its core library, ROM extensions,
 * and metadata for UI display and thumbnail fetching.
 *
 * Each platform maps to a single libretro core and a set of file extensions.
 * To add a new platform, register it in [PlatformRegistry].
 *
 * @property id Unique stable identifier (e.g., "nes", "snes", "gba").
 * @property displayName Human-readable name for settings/UI.
 * @property badge Short tag shown on game cards (e.g., "NES", "SNES").
 * @property coreLibrary Filename of the libretro core .so file.
 * @property romExtensions File extensions this platform handles (without dots, lowercase).
 * @property thumbnailSystem The libretro thumbnail server system name, or null if none.
 * @property accentColorArgb ARGB accent color for UI badges (e.g., 0xFFE53935 for red).
 */
data class Platform(
    val id: String,
    val displayName: String,
    val badge: String,
    val coreLibrary: String,
    val romExtensions: Set<String>,
    val thumbnailSystem: String? = null,
    val accentColorArgb: Long = 0xFF4FC3F7,
)

/**
 * Central registry of all supported emulation platforms.
 *
 * To add a new platform:
 *   1. Define it here with its core library and extensions.
 *   2. Place the .so core file in /storage/emulated/0/BikeArcade/cores/
 *   3. That's it — the game library, launcher, and thumbnail manager
 *      will automatically pick up ROMs for the new platform.
 */
object PlatformRegistry {

    val NES = Platform(
        id = "nes",
        displayName = "Nintendo Entertainment System",
        badge = "NES",
        coreLibrary = "fceumm_libretro_android.so",
        romExtensions = setOf("nes"),
        thumbnailSystem = "Nintendo - Nintendo Entertainment System",
        accentColorArgb = 0xFFE53935,
    )

    val SNES = Platform(
        id = "snes",
        displayName = "Super Nintendo",
        badge = "SNES",
        coreLibrary = "snes9x_libretro_android.so",
        romExtensions = setOf("smc", "sfc"),
        thumbnailSystem = "Nintendo - Super Nintendo Entertainment System",
        accentColorArgb = 0xFF7E57C2,
    )

    val GBA = Platform(
        id = "gba",
        displayName = "Game Boy Advance",
        badge = "GBA",
        coreLibrary = "mgba_libretro_android.so",
        romExtensions = setOf("gba"),
        thumbnailSystem = "Nintendo - Game Boy Advance",
        accentColorArgb = 0xFF5C6BC0,
    )

    val GENESIS = Platform(
        id = "genesis",
        displayName = "Sega Genesis",
        badge = "GEN",
        coreLibrary = "genesis_plus_gx_libretro_android.so",
        romExtensions = setOf("md", "gen", "bin", "smd"),
        thumbnailSystem = "Sega - Mega Drive - Genesis",
        accentColorArgb = 0xFF2196F3,
    )

    val GB = Platform(
        id = "gb",
        displayName = "Game Boy / Color",
        badge = "GB",
        coreLibrary = "gambatte_libretro_android.so",
        romExtensions = setOf("gb", "gbc"),
        thumbnailSystem = "Nintendo - Game Boy",
        accentColorArgb = 0xFF8BC34A,
    )

    /**
     * All registered platforms. The order here determines display order in filter chips.
     */
    val ALL: List<Platform> = listOf(NES, SNES, GBA, GB, GENESIS)

    /** All ROM extensions across all platforms (for file scanning). */
    val ALL_ROM_EXTENSIONS: Set<String> = ALL.flatMap { it.romExtensions }.toSet()

    /** Look up a platform by its unique ID. */
    fun byId(id: String): Platform? = ALL.firstOrNull { it.id == id }

    /**
     * Detect the platform from a ROM file extension.
     * Returns null for ambiguous extensions like "zip" or "bin" that could be multiple platforms.
     * For .zip files, callers should peek inside and match the inner extension.
     */
    fun fromExtension(ext: String): Platform? {
        val lower = ext.lowercase().removePrefix(".")
        // Find platforms that uniquely own this extension
        val matches = ALL.filter { lower in it.romExtensions }
        return if (matches.size == 1) matches.first() else null
    }

    /** Base directory for core .so files on external storage. */
    const val CORE_BASE_PATH = "/storage/emulated/0/BikeArcade/cores"

    /** Path to the core .so file on external storage. */
    fun corePathFor(platform: Platform): String =
        "$CORE_BASE_PATH/${platform.coreLibrary}"
}
