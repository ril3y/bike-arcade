package com.bikearcade.emulator

/**
 * Supported libretro cores with their library filenames and ROM extensions.
 */
enum class CoreType(
    val coreLibrary: String,
    val displayName: String,
    val supportedExtensions: Set<String>
) {
    NES(
        coreLibrary = "fceumm_libretro_android.so",
        displayName = "FCEUmm (NES)",
        supportedExtensions = setOf("nes", "zip")
    ),
    SNES(
        coreLibrary = "snes9x_libretro_android.so",
        displayName = "Snes9x (SNES)",
        supportedExtensions = setOf("smc", "sfc", "zip")
    );

    companion object {
        /** Base directory for ROM files. */
        const val ROM_BASE_PATH = "/storage/emulated/0/BikeArcade/roms"

        /** Base directory for core .so files. */
        const val CORE_BASE_PATH = "/storage/emulated/0/BikeArcade/cores"

        /**
         * Detect the appropriate core type from a ROM file extension.
         * Returns null if the extension is not supported by any core.
         */
        fun fromExtension(extension: String): CoreType? {
            val ext = extension.lowercase().removePrefix(".")
            return entries.firstOrNull { ext in it.supportedExtensions }
        }

        /**
         * Detect core type from a full ROM file path.
         */
        fun fromRomPath(path: String): CoreType? {
            val ext = path.substringAfterLast('.', "").lowercase()
            // For .zip files, we cannot determine core from extension alone;
            // caller should specify explicitly. For unambiguous extensions, resolve directly.
            return when (ext) {
                "nes" -> NES
                "smc", "sfc" -> SNES
                else -> null
            }
        }

        /**
         * Returns the full path to the core .so file.
         */
        fun corePathFor(type: CoreType): String {
            return "$CORE_BASE_PATH/${type.coreLibrary}"
        }
    }
}
