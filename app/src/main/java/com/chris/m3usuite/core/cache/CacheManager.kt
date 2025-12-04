package com.chris.m3usuite.core.cache

import android.content.Context
import com.chris.m3usuite.core.logging.UnifiedLogRepository
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
    }

    /**
     * Clear application log cache.
     * Deletes all files in filesDir/logs directory.
     */
    suspend fun clearLogCache(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val logsDir = File(context.filesDir, LOG_DIR)
                if (!logsDir.exists()) {
                    UnifiedLogRepository.log(
                        level = UnifiedLogRepository.Level.INFO,
                        category = "diagnostics",
                        source = TAG,
                        message = "Log cache directory does not exist, nothing to clear",
                    )
                    return@withContext CacheResult(success = true, filesDeleted = 0)
                }

                val (deleted, bytes) = deleteDirectoryContents(logsDir)
                val duration = System.currentTimeMillis() - startTime

                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.INFO,
                    category = "diagnostics",
                    source = TAG,
                    message = "Cleared log cache",
                    details =
                        mapOf(
                            "filesDeleted" to deleted.toString(),
                            "bytesFreed" to bytes.toString(),
                            "durationMs" to duration.toString(),
                        ),
                )

                CacheResult(success = true, filesDeleted = deleted, bytesFreed = bytes)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.ERROR,
                    category = "diagnostics",
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
     * Clear TDLib (Telegram) cache.
     * Deletes all files in noBackupFilesDir/tdlib-db and noBackupFilesDir/tdlib-files.
     * Note: TDLib will recreate necessary files on next use.
     */
    suspend fun clearTdlibCache(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
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

                val duration = System.currentTimeMillis() - startTime

                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.INFO,
                    category = "telegram",
                    source = TAG,
                    message = "Cleared TDLib cache",
                    details =
                        mapOf(
                            "filesDeleted" to totalDeleted.toString(),
                            "bytesFreed" to totalBytes.toString(),
                            "durationMs" to duration.toString(),
                        ),
                )

                if (totalDeleted == 0) {
                    return@withContext CacheResult(success = true, filesDeleted = 0, bytesFreed = 0)
                }

                CacheResult(success = true, filesDeleted = totalDeleted, bytesFreed = totalBytes)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.ERROR,
                    category = "telegram",
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
     * Clear Xtream/ExoPlayer cache.
     * Deletes RAR cache, image cache, and diagnostics export directory in cacheDir.
     * Note: ExoPlayer will rebuild cache as needed during playback.
     */
    suspend fun clearXtreamCache(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
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

                val duration = System.currentTimeMillis() - startTime

                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.INFO,
                    category = "xtream",
                    source = TAG,
                    message = "Cleared Xtream/ExoPlayer cache",
                    details =
                        mapOf(
                            "filesDeleted" to totalDeleted.toString(),
                            "bytesFreed" to totalBytes.toString(),
                            "durationMs" to duration.toString(),
                        ),
                )

                if (totalDeleted == 0) {
                    return@withContext CacheResult(success = true, filesDeleted = 0, bytesFreed = 0)
                }

                CacheResult(success = true, filesDeleted = totalDeleted, bytesFreed = totalBytes)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.ERROR,
                    category = "xtream",
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
     * Clear all caches at once.
     * Combines log, TDLib, and Xtream cache clearing operations.
     */
    suspend fun clearAllCaches(): CacheResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val logResult = clearLogCache()
                val tdlibResult = clearTdlibCache()
                val xtreamResult = clearXtreamCache()

                val totalDeleted = logResult.filesDeleted + tdlibResult.filesDeleted + xtreamResult.filesDeleted
                val totalBytes = logResult.bytesFreed + tdlibResult.bytesFreed + xtreamResult.bytesFreed
                val allSuccess = logResult.success && tdlibResult.success && xtreamResult.success

                val duration = System.currentTimeMillis() - startTime

                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.INFO,
                    category = "diagnostics",
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
                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.ERROR,
                    category = "diagnostics",
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
                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.WARN,
                    category = "diagnostics",
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
                UnifiedLogRepository.log(
                    level = UnifiedLogRepository.Level.WARN,
                    category = "diagnostics",
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
            UnifiedLogRepository.log(
                level = UnifiedLogRepository.Level.WARN,
                category = "diagnostics",
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
