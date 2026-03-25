package com.bikearcade.app.launcher

import android.util.Log
import com.bikearcade.emulator.PlatformRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Scans mounted USB drives for ROM files and copies them to the BikeArcade ROM folder.
 * Recursively searches all directories on the USB drive.
 *
 * Deduplication: skips files that already exist in the ROM directory by checking
 * both filename match and MD5 hash match against existing files of the same size.
 *
 * On Android 9 (Rockchip), USB drives mount at:
 *   /mnt/media_rw/<UUID>/
 *   /storage/<UUID>/
 */
object UsbImporter {

    private const val TAG = "UsbImporter"
    private const val MAX_DEPTH = 5

    private val ROM_EXTENSIONS = PlatformRegistry.ALL_ROM_EXTENSIONS + "zip"

    /** Common parent directories where USB volumes appear. */
    private val USB_MOUNT_PARENTS = listOf(
        File("/mnt/media_rw"),
        File("/mnt/usb_storage"),
        File("/storage"),
    )

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<String>,
    )

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importStatus = MutableStateFlow("")
    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    /**
     * Check if any USB volumes are currently mounted.
     */
    fun hasUsbDrive(): Boolean = findUsbVolumes().isNotEmpty()

    /**
     * Find all mounted USB volumes (directories that aren't internal storage).
     */
    fun findUsbVolumes(): List<File> {
        val volumes = mutableListOf<File>()
        for (parent in USB_MOUNT_PARENTS) {
            if (!parent.exists() || !parent.isDirectory) continue
            val children = parent.listFiles() ?: continue
            for (child in children) {
                if (!child.isDirectory) continue
                if (child.name == "emulated" || child.name == "self") continue
                val contents = child.listFiles()
                if (contents != null && contents.isNotEmpty()) {
                    volumes.add(child)
                    Log.d(TAG, "Found USB volume: ${child.absolutePath}")
                }
            }
        }
        return volumes
    }

    /**
     * Recursively scan a directory for ROM files.
     */
    private fun findRomsRecursive(dir: File, depth: Int = 0): List<File> {
        if (depth > MAX_DEPTH) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        val roms = mutableListOf<File>()
        for (file in files) {
            if (file.isFile && file.extension.lowercase() in ROM_EXTENSIONS) {
                roms.add(file)
            } else if (file.isDirectory && !file.name.startsWith(".")) {
                roms.addAll(findRomsRecursive(file, depth + 1))
            }
        }
        return roms
    }

    /**
     * Import all ROM files from all detected USB volumes into the ROM directory.
     * Skips files that already exist (by name or by content hash).
     */
    suspend fun importFromUsb(): ImportResult = withContext(Dispatchers.IO) {
        _isImporting.value = true
        _importStatus.value = "Scanning for USB drives..."

        try {
            val romDir = File(GameLibrary.ROM_DIRECTORY)
            romDir.mkdirs()

            val volumes = findUsbVolumes()
            if (volumes.isEmpty()) {
                _importStatus.value = "No USB drives found"
                return@withContext ImportResult(0, 0, 0, listOf("No USB drives detected"))
            }

            _importStatus.value = "Scanning USB for ROMs..."
            val allRoms = volumes.flatMap { vol ->
                Log.d(TAG, "Scanning ${vol.absolutePath}")
                findRomsRecursive(vol)
            }

            if (allRoms.isEmpty()) {
                _importStatus.value = "No ROM files found on USB"
                return@withContext ImportResult(0, 0, 0, listOf("No ROM files found on USB"))
            }

            // Build hash index of existing ROMs for dedup
            _importStatus.value = "Checking existing library..."
            val existingFiles = romDir.listFiles()?.filter { it.isFile } ?: emptyList()
            val existingNames = existingFiles.map { it.name.lowercase() }.toSet()
            val existingHashes = mutableMapOf<Long, MutableSet<String>>()
            for (file in existingFiles) {
                existingHashes.getOrPut(file.length()) { mutableSetOf() }
            }

            var imported = 0
            var skipped = 0
            var failed = 0
            val errors = mutableListOf<String>()

            for ((index, romFile) in allRoms.withIndex()) {
                _importStatus.value = "Importing ${romFile.name} (${index + 1}/${allRoms.size})"
                val dest = File(romDir, romFile.name)

                // Check 1: exact filename match
                if (dest.name.lowercase() in existingNames) {
                    Log.d(TAG, "Skipping ${romFile.name} (name already exists)")
                    skipped++
                    continue
                }

                // Check 2: same-size files → compare MD5 hash
                val sameSize = existingFiles.filter { it.length() == romFile.length() }
                if (sameSize.isNotEmpty()) {
                    val srcHash = md5(romFile)
                    val isDuplicate = sameSize.any { md5(it) == srcHash }
                    if (isDuplicate) {
                        Log.d(TAG, "Skipping ${romFile.name} (content duplicate)")
                        skipped++
                        continue
                    }
                }

                try {
                    romFile.copyTo(dest)
                    imported++
                    Log.i(TAG, "Imported ${romFile.name} (${romFile.length() / 1024}KB)")
                } catch (e: Exception) {
                    failed++
                    errors.add("${romFile.name}: ${e.message}")
                    Log.e(TAG, "Failed to import ${romFile.name}", e)
                }
            }

            val msg = buildString {
                append("Done: $imported imported")
                if (skipped > 0) append(", $skipped skipped")
                if (failed > 0) append(", $failed failed")
            }
            _importStatus.value = msg
            Log.i(TAG, msg)

            ImportResult(imported, skipped, failed, errors)
        } finally {
            _isImporting.value = false
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
