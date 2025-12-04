package com.chris.m3usuite.core.cache

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.prefs.SettingsDataStoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Result of a cache clearing operation.
 *
 * @param success Whether the operation completed successfully
 * @param filesDeleted Number of files deleted
 * @param bytesFreed Number of bytes freed (0 if not calculated)
 * @param errorMessage Error message if operation failed
 */
data class CacheResult(
    val success: Boolean,
    val filesDeleted: Int = 0,
    val bytesFreed: Long = 0L,
    val errorMessage: String? = null,
)

/**
 * Central cache management for the application.
 *
 * Provides functions to clear various subsystem caches:
 * - Log cache: Application logs stored in filesDir/logs
 * - TDLib cache: Telegram database and files in noBackupFilesDir/tdlib-*
 * - Xtream cache: ExoPlayer and RAR caches in cacheDir
 * - Image cache: Coil image cache in cacheDir/image_cache
 * - All caches: Combined operation to clear all above caches
 *
 * All operations run on Dispatchers.IO to avoid blocking the main thread.
 * Operations are safe and handle missing directories gracefully.
 */
class CacheManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "CacheManager"
        private const val LOG_DIR = "logs"
        private const val TDLIB_DB_DIR = "tdlib-db"
        private const val TDLIB_FILES_DIR = "tdlib-files"
        private const val RAR_CACHE_DIR = "rar"
        private const val IMAGE_CACHE_DIR = "image_cache"
        private const val DIAGNOSTICS_EXPORT_DIR = "diagnostics_export"
        private const val DATASTORE_DIR = "datastore"
        private const val OBJECTBOX_DIR = "objectbox"
        private const val SHARED_PREFS_DIR = "shared_prefs"
    }

    /**
     * Clear DataStore (app settings/preferences).
     * This will reset ALL app settings to defaults.
     * Uses edit { clear() } instead of deleting files to prevent multi-DataStore errors.
     */
    suspend fun clearDataStore(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                // Clear DataStore preferences programmatically using singleton instance
                // DO NOT delete the physical file as this causes multi-DataStore errors
                val dataStore = SettingsDataStoreProvider.getInstance(context)
                dataStore.edit { it.clear() }

                val duration = System.currentTimeMillis() - startTime

                UnifiedLog.info(
                    TAG,
                    "Cleared DataStore (app settings)",
                    mapOf(
                        "durationMs" to duration.toString(),
                    ),
                )

                CacheResult(success = true, filesDeleted = 0, bytesFreed = 0L)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLog.error(
                    TAG,
                    "Failed to clear DataStore",
                    mapOf(
                        "error" to e.message.orEmpty(),
                        "durationMs" to duration.toString(),
                    ),
                )
                CacheResult(success = false, errorMessage = e.message ?: "Unknown error")
            }
        }

    /**
     * Clear ObjectBox database (all content: VOD, Series, Live, Episodes, etc.).
     * This will remove all imported content and require a fresh import.
     * The app should be restarted after this operation.
     */
    suspend fun clearObjectBoxDatabase(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                // Close ObjectBox first to release file locks
                try {
                    val store = ObxStore.get(context)
                    store.close()
                } catch (e: Exception) {
                    // Store might not be initialized yet, that's fine
                }

                // Delete ObjectBox directory
                val objectboxDir = File(context.filesDir, OBJECTBOX_DIR)
                var filesDeleted = 0
                var bytesFreed = 0L

                if (objectboxDir.exists()) {
                    val (deleted, bytes) = deleteDirectoryRecursively(objectboxDir)
                    filesDeleted = deleted
                    bytesFreed = bytes
                }

                val duration = System.currentTimeMillis() - startTime

                UnifiedLog.log(
                    level = UnifiedLog.Level.INFO,
                    source = TAG,
                    message = "Cleared ObjectBox database (all content)",
                    details =
                        mapOf(
                            "filesDeleted" to filesDeleted.toString(),
                            "bytesFreed" to bytesFreed.toString(),
                            "durationMs" to duration.toString(),
                        ),
                )

                CacheResult(success = true, filesDeleted = filesDeleted, bytesFreed = bytesFreed)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLog.log(
                    level = UnifiedLog.Level.ERROR,
                    source = TAG,
                    message = "Failed to clear ObjectBox database",
                    details =
                        mapOf(
                            "error" to e.message.orEmpty(),
                            "durationMs" to duration.toString(),
                        ),
                )
                CacheResult(success = false, errorMessage = e.message ?: "Unknown error")
            }
        }

    /**
     * Clear SharedPreferences (legacy preferences if any).
     */
    suspend fun clearSharedPreferences(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val prefsDir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR)
                var filesDeleted = 0
                var bytesFreed = 0L

                if (prefsDir.exists()) {
                    val (deleted, bytes) = deleteDirectoryContents(prefsDir)
                    filesDeleted = deleted
                    bytesFreed = bytes
                }

                val duration = System.currentTimeMillis() - startTime

                UnifiedLog.log(
                    level = UnifiedLog.Level.INFO,
                    source = TAG,
                    message = "Cleared SharedPreferences",
                    details =
                        mapOf(
                            "filesDeleted" to filesDeleted.toString(),
                            "bytesFreed" to bytesFreed.toString(),
                            "durationMs" to duration.toString(),
                        ),
                )

                CacheResult(success = true, filesDeleted = filesDeleted, bytesFreed = bytesFreed)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLog.log(
                    level = UnifiedLog.Level.ERROR,
                    source = TAG,
                    message = "Failed to clear SharedPreferences",
                    details =
                        mapOf(
                            "error" to e.message.orEmpty(),
                            "durationMs" to duration.toString(),
                        ),
                )
                CacheResult(success = false, errorMessage = e.message ?: "Unknown error")
            }
        }

    /**
     * NUCLEAR OPTION: Clear ALL app data (settings, database, caches).
     * This is equivalent to "Clear Data" in Android Settings.
     * The app MUST be restarted after this operation.
     */
    suspend fun clearAllAppData(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                // Clear in order: ObjectBox first (needs closing), then settings, then caches
                val objectboxResult = clearObjectBoxDatabaseInternal()
                val datastoreResult = clearDataStoreInternal()
                val sharedPrefsResult = clearSharedPreferencesInternal()
                val logResult = clearLogCacheInternal()
                val tdlibResult = clearTdlibCacheInternal()
                val xtreamResult = clearXtreamCacheInternal()

                val totalDeleted =
                    objectboxResult.filesDeleted + datastoreResult.filesDeleted +
                        sharedPrefsResult.filesDeleted + logResult.filesDeleted +
                        tdlibResult.filesDeleted + xtreamResult.filesDeleted

                val totalBytes =
                    objectboxResult.bytesFreed + datastoreResult.bytesFreed +
                        sharedPrefsResult.bytesFreed + logResult.bytesFreed +
                        tdlibResult.bytesFreed + xtreamResult.bytesFreed

                val allSuccess =
                    objectboxResult.success &&
                        datastoreResult.success &&
                        sharedPrefsResult.success &&
                        logResult.success &&
                        tdlibResult.success &&
                        xtreamResult.success

                val duration = System.currentTimeMillis() - startTime

                UnifiedLog.log(
                    level = UnifiedLog.Level.INFO,
                    source = TAG,
                    message = "Cleared ALL app data (nuclear reset)",
                    details =
                        mapOf(
                            "filesDeleted" to totalDeleted.toString(),
                            "bytesFreed" to totalBytes.toString(),
                            "durationMs" to duration.toString(),
                            "allSuccess" to allSuccess.toString(),
                        ),
                )

                CacheResult(
                    success = allSuccess,
                    filesDeleted = totalDeleted,
                    bytesFreed = totalBytes,
                    errorMessage = if (!allSuccess) "Some operations failed" else null,
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLog.log(
                    level = UnifiedLog.Level.ERROR,
                    source = TAG,
                    message = "Failed to clear all app data",
                    details =
                        mapOf(
                            "error" to e.message.orEmpty(),
                            "durationMs" to duration.toString(),
                        ),
                )
                CacheResult(success = false, errorMessage = e.message ?: "Unknown error")
            }
        }

    // Internal methods without logging (for combined operations)

    private suspend fun clearDataStoreInternal(): CacheResult =
        try {
            // Use singleton DataStore instance and edit { clear() } only
            val dataStore = SettingsDataStoreProvider.getInstance(context)
            dataStore.edit { it.clear() }
            CacheResult(success = true, filesDeleted = 0, bytesFreed = 0L)
        } catch (e: Exception) {
            CacheResult(success = false, errorMessage = e.message)
        }

    private fun clearObjectBoxDatabaseInternal(): CacheResult =
        try {
            try {
                val store = ObxStore.get(context)
                store.close()
            } catch (e: Exception) {
                // ignore
            }

            val objectboxDir = File(context.filesDir, OBJECTBOX_DIR)
            if (objectboxDir.exists()) {
                val (deleted, bytes) = deleteDirectoryRecursively(objectboxDir)
                CacheResult(success = true, filesDeleted = deleted, bytesFreed = bytes)
            } else {
                CacheResult(success = true, filesDeleted = 0, bytesFreed = 0L)
            }
        } catch (e: Exception) {
            CacheResult(success = false, errorMessage = e.message)
        }

    private fun clearSharedPreferencesInternal(): CacheResult =
        try {
            val prefsDir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR)
            if (prefsDir.exists()) {
                val (deleted, bytes) = deleteDirectoryContents(prefsDir)
                CacheResult(success = true, filesDeleted = deleted, bytesFreed = bytes)
            } else {
                CacheResult(success = true, filesDeleted = 0, bytesFreed = 0L)
            }
        } catch (e: Exception) {
            CacheResult(success = false, errorMessage = e.message)
        }

    /**
     * Clear application log cache.
     * Deletes all files in filesDir/logs directory.
     */
    suspend fun clearLogCache(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val result = clearLogCacheInternal()
                val duration = System.currentTimeMillis() - startTime

                if (result.filesDeleted > 0) {
                    UnifiedLog.log(
                        level = UnifiedLog.Level.INFO,
                        source = TAG,
                        message = "Cleared log cache",
                        details =
                            mapOf(
                                "filesDeleted" to result.filesDeleted.toString(),
                                "bytesFreed" to result.bytesFreed.toString(),
                                "durationMs" to duration.toString(),
                            ),
                    )
                }

                result
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLog.log(
                    level = UnifiedLog.Level.ERROR,
                    source = TAG,
                    message = "Failed to clear log cache",
                    details =
                        mapOf(
                            "error" to e.message.orEmpty(),
                            "durationMs" to duration.toString(),
                        ),
                )
                CacheResult(success = false, errorMessage = e.message ?: "Unknown error")
            }
        }

    /**
     * Internal method to clear log cache without logging.
     * Used by clearAllCaches to avoid logging while clearing the log directory.
     */
    private fun clearLogCacheInternal(): CacheResult {
        val logsDir = File(context.filesDir, LOG_DIR)
        if (!logsDir.exists()) {
            return CacheResult(success = true, filesDeleted = 0)
        }

        val (deleted, bytes) = deleteDirectoryContents(logsDir)
        return CacheResult(success = true, filesDeleted = deleted, bytesFreed = bytes)
    }

    /**
     * Clear TDLib (Telegram) cache.
     * Deletes all files in noBackupFilesDir/tdlib-db and noBackupFilesDir/tdlib-files.
     * Note: TDLib will recreate necessary files on next use.
     */
    suspend fun clearTdlibCache(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val result = clearTdlibCacheInternal()
                val duration = System.currentTimeMillis() - startTime

                UnifiedLog.log(
                    level = UnifiedLog.Level.INFO,
                    source = TAG,
                    message = "Cleared TDLib cache",
                    details =
                        mapOf(
                            "filesDeleted" to result.filesDeleted.toString(),
                            "bytesFreed" to result.bytesFreed.toString(),
                            "durationMs" to duration.toString(),
                        ),
                )

                result
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLog.log(
                    level = UnifiedLog.Level.ERROR,
                    source = TAG,
                    message = "Failed to clear TDLib cache",
                    details =
                        mapOf(
                            "error" to e.message.orEmpty(),
                            "durationMs" to duration.toString(),
                        ),
                )
                CacheResult(success = false, errorMessage = e.message ?: "Unknown error")
            }
        }

    /**
     * Internal method to clear TDLib cache without logging.
     */
    private fun clearTdlibCacheInternal(): CacheResult {
        val tdlibDbDir = File(context.noBackupFilesDir, TDLIB_DB_DIR)
        val tdlibFilesDir = File(context.noBackupFilesDir, TDLIB_FILES_DIR)

        var totalDeleted = 0
        var totalBytes = 0L

        if (tdlibDbDir.exists()) {
            val (deleted, bytes) = deleteDirectoryContents(tdlibDbDir)
            totalDeleted += deleted
            totalBytes += bytes
        }

        if (tdlibFilesDir.exists()) {
            val (deleted, bytes) = deleteDirectoryContents(tdlibFilesDir)
            totalDeleted += deleted
            totalBytes += bytes
        }

        return CacheResult(success = true, filesDeleted = totalDeleted, bytesFreed = totalBytes)
    }

    /**
     * Clear Xtream/ExoPlayer cache.
     * Deletes RAR cache, image cache, and diagnostics export directory in cacheDir.
     * Note: ExoPlayer will rebuild cache as needed during playback.
     */
    suspend fun clearXtreamCache(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val result = clearXtreamCacheInternal()
                val duration = System.currentTimeMillis() - startTime

                UnifiedLog.log(
                    level = UnifiedLog.Level.INFO,
                    source = TAG,
                    message = "Cleared Xtream/ExoPlayer cache",
                    details =
                        mapOf(
                            "filesDeleted" to result.filesDeleted.toString(),
                            "bytesFreed" to result.bytesFreed.toString(),
                            "durationMs" to duration.toString(),
                        ),
                )

                result
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLog.log(
                    level = UnifiedLog.Level.ERROR,
                    source = TAG,
                    message = "Failed to clear Xtream/ExoPlayer cache",
                    details =
                        mapOf(
                            "error" to e.message.orEmpty(),
                            "durationMs" to duration.toString(),
                        ),
                )
                CacheResult(success = false, errorMessage = e.message ?: "Unknown error")
            }
        }

    /**
     * Internal method to clear Xtream cache without logging.
     */
    private fun clearXtreamCacheInternal(): CacheResult {
        val rarCacheDir = File(context.cacheDir, RAR_CACHE_DIR)
        val imageCacheDir = File(context.cacheDir, IMAGE_CACHE_DIR)
        val diagExportDir = File(context.cacheDir, DIAGNOSTICS_EXPORT_DIR)

        var totalDeleted = 0
        var totalBytes = 0L

        if (rarCacheDir.exists()) {
            val (deleted, bytes) = deleteDirectoryContents(rarCacheDir)
            totalDeleted += deleted
            totalBytes += bytes
        }

        if (imageCacheDir.exists()) {
            val (deleted, bytes) = deleteDirectoryContents(imageCacheDir)
            totalDeleted += deleted
            totalBytes += bytes
        }

        if (diagExportDir.exists()) {
            val (deleted, bytes) = deleteDirectoryContents(diagExportDir)
            totalDeleted += deleted
            totalBytes += bytes
        }

        return CacheResult(success = true, filesDeleted = totalDeleted, bytesFreed = totalBytes)
    }

    /**
     * Clear all caches at once.
     * Combines log, TDLib, and Xtream cache clearing operations.
     * Uses internal methods to avoid logging while clearing the log directory.
     */
    suspend fun clearAllCaches(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                // Use internal methods to avoid logging during deletion
                val logResult = clearLogCacheInternal()
                val tdlibResult = clearTdlibCacheInternal()
                val xtreamResult = clearXtreamCacheInternal()

                val totalDeleted = logResult.filesDeleted + tdlibResult.filesDeleted + xtreamResult.filesDeleted
                val totalBytes = logResult.bytesFreed + tdlibResult.bytesFreed + xtreamResult.bytesFreed
                val allSuccess = logResult.success && tdlibResult.success && xtreamResult.success

                val duration = System.currentTimeMillis() - startTime

                // Now log after all deletions are complete
                UnifiedLog.log(
                    level = UnifiedLog.Level.INFO,
                    source = TAG,
                    message = "Cleared all caches",
                    details =
                        mapOf(
                            "filesDeleted" to totalDeleted.toString(),
                            "bytesFreed" to totalBytes.toString(),
                            "durationMs" to duration.toString(),
                            "allSuccess" to allSuccess.toString(),
                        ),
                )

                val errorMessages =
                    listOfNotNull(
                        logResult.errorMessage?.let { "Log: $it" },
                        tdlibResult.errorMessage?.let { "TDLib: $it" },
                        xtreamResult.errorMessage?.let { "Xtream: $it" },
                    )

                CacheResult(
                    success = allSuccess,
                    filesDeleted = totalDeleted,
                    bytesFreed = totalBytes,
                    errorMessage = if (errorMessages.isNotEmpty()) errorMessages.joinToString("; ") else null,
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLog.log(
                    level = UnifiedLog.Level.ERROR,
                    source = TAG,
                    message = "Failed to clear all caches",
                    details =
                        mapOf(
                            "error" to e.message.orEmpty(),
                            "durationMs" to duration.toString(),
                        ),
                )
                CacheResult(success = false, errorMessage = e.message ?: "Unknown error")
            }
        }

    /**
     * Delete all files and subdirectories in a directory, but keep the directory itself.
     *
     * @param directory The directory to clear
     * @return Pair of (number of files deleted, bytes freed)
     */
    private fun deleteDirectoryContents(directory: File): Pair<Int, Long> {
        var deletedCount = 0
        var bytesFreed = 0L

        directory.listFiles()?.forEach { file ->
            try {
                val size = if (file.isFile) file.length() else 0L
                if (file.isDirectory) {
                    val (subDeleted, subBytes) = deleteDirectoryRecursively(file)
                    deletedCount += subDeleted
                    bytesFreed += subBytes
                } else {
                    if (file.delete()) {
                        deletedCount++
                        bytesFreed += size
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.log(
                    level = UnifiedLog.Level.WARN,
                    source = TAG,
                    message = "Failed to delete file",
                    details =
                        mapOf(
                            "file" to file.absolutePath,
                            "error" to e.message.orEmpty(),
                        ),
                )
            }
        }

        return Pair(deletedCount, bytesFreed)
    }

    /**
     * Recursively delete a directory and all its contents.
     *
     * @param directory The directory to delete
     * @return Pair of (number of files deleted, bytes freed)
     */
    private fun deleteDirectoryRecursively(directory: File): Pair<Int, Long> {
        var deletedCount = 0
        var bytesFreed = 0L

        directory.listFiles()?.forEach { file ->
            try {
                val size = if (file.isFile) file.length() else 0L
                if (file.isDirectory) {
                    val (subDeleted, subBytes) = deleteDirectoryRecursively(file)
                    deletedCount += subDeleted
                    bytesFreed += subBytes
                } else {
                    if (file.delete()) {
                        deletedCount++
                        bytesFreed += size
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.log(
                    level = UnifiedLog.Level.WARN,
                    source = TAG,
                    message = "Failed to delete file",
                    details =
                        mapOf(
                            "file" to file.absolutePath,
                            "error" to e.message.orEmpty(),
                        ),
                )
            }
        }

        // Try to delete the directory itself
        try {
            if (directory.delete()) {
                deletedCount++
            }
        } catch (e: Exception) {
            UnifiedLog.log(
                level = UnifiedLog.Level.WARN,
                source = TAG,
                message = "Failed to delete directory",
                details =
                    mapOf(
                        "directory" to directory.absolutePath,
                        "error" to e.message.orEmpty(),
                    ),
            )
        }

        return Pair(deletedCount, bytesFreed)
    }
}
