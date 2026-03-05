package com.bikearcade.app.launcher

/**
 * Represents a ROM file discovered in the game library.
 *
 * @property name Display name derived from the filename (without extension).
 * @property path Absolute path to the ROM file on the filesystem.
 * @property consoleType The detected console type (NES or SNES).
 * @property lastPlayed Timestamp (ms since epoch) when this game was last launched, or 0 if never.
 * @property fileSize File size in bytes.
 */
data class GameEntry(
    val name: String,
    val path: String,
    val consoleType: ConsoleType,
    val lastPlayed: Long = 0L,
    val fileSize: Long = 0L,
    val artworkPath: String? = null,
)

/**
 * Supported console types, detected from ROM file extension.
 */
enum class ConsoleType(val displayName: String, val badge: String) {
    NES("Nintendo Entertainment System", "NES"),
    SNES("Super Nintendo", "SNES");
}
