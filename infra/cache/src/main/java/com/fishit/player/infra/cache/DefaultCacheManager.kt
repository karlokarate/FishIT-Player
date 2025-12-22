package com.fishit.player.infra.cache

import android.content.Context
import coil3.ImageLoader
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [CacheManager].
 *
 * **Thread Safety:**
 * - All file operations run on Dispatchers.IO
 * - No main-thread blocking
 *
 * **Logging:**
 * - All operations log via UnifiedLog
 * - No sensitive information in log messages
 *
 * **Architecture:**
 * - This is the ONLY place with direct file system access for caches
 * - DebugInfoProvider and Settings delegate to this class
 */
@Singleton
class DefaultCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader
) : CacheManager {

    companion object {
        private const val TAG = "CacheManager"
        
        // TDLib directory names (relative to noBackupFilesDir)
        private const val TDLIB_DB_DIR = "tdlib"
        private const val TDLIB_FILES_DIR = "tdlib-files"
        
        // ObjectBox directory name (relative to filesDir)
        private const val OBJECTBOX_DIR = "objectbox"
    }

    // =========================================================================
    // Size Calculations
    // =========================================================================

    override suspend fun getTelegramCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            val tdlibDir = File(context.noBackupFilesDir, TDLIB_DB_DIR)
            val filesDir = File(context.noBackupFilesDir, TDLIB_FILES_DIR)
            
            var totalSize = 0L
            
            if (tdlibDir.exists()) {
                totalSize += calculateDirectorySize(tdlibDir)
            }
            if (filesDir.exists()) {
                totalSize += calculateDirectorySize(filesDir)
            }
            
            UnifiedLog.d(TAG) { "TDLib cache size: $totalSize bytes" }
            totalSize
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to calculate TDLib cache size" }
            0L
        }
    }

    override suspend fun getImageCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            val diskCache = imageLoader.diskCache
            val size = diskCache?.size ?: 0L
            
            UnifiedLog.d(TAG) { "Image cache size: $size bytes" }
            size
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to calculate image cache size" }
            0L
        }
    }

    override suspend fun getDatabaseSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            val objectboxDir = File(context.filesDir, OBJECTBOX_DIR)
            val size = if (objectboxDir.exists()) {
                calculateDirectorySize(objectboxDir)
            } else {
                0L
            }
            
            UnifiedLog.d(TAG) { "Database size: $size bytes" }
            size
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to calculate database size" }
            0L
        }
    }

    // =========================================================================
    // Cache Clearing
    // =========================================================================

    override suspend fun clearTelegramCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Only clear files directory (downloaded media), preserve database
            val filesDir = File(context.noBackupFilesDir, TDLIB_FILES_DIR)
            
            if (filesDir.exists()) {
                deleteDirectoryContents(filesDir)
                UnifiedLog.i(TAG) { "Cleared TDLib files cache" }
            } else {
                UnifiedLog.d(TAG) { "TDLib files directory does not exist, nothing to clear" }
            }
            true
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to clear TDLib cache" }
            false
        }
    }

    override suspend fun clearImageCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Clear both disk and memory cache
            imageLoader.diskCache?.clear()
            imageLoader.memoryCache?.clear()
            
            UnifiedLog.i(TAG) { "Cleared image cache (disk + memory)" }
            true
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to clear image cache" }
            false
        }
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Calculate total size of a directory recursively.
     * Runs on IO dispatcher (caller's responsibility).
     */
    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Delete all contents of a directory without deleting the directory itself.
     * Runs on IO dispatcher (caller's responsibility).
     */
    private fun deleteDirectoryContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }
}
