package com.chris.m3usuite.telegram.core

import android.content.Context
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Download progress information for a file.
 */
data class DownloadProgress(
    val fileId: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean,
) {
    val progressPercent: Int
        get() =
            if (totalBytes > 0) {
                ((downloadedBytes * 100) / totalBytes).toInt()
            } else {
                0
            }
}

/**
 * Handles file downloads from Telegram using TDLib.
 *
 * This class DOES NOT create its own TdlClient - it receives an injected session
 * from T_TelegramServiceClient. All operations use the client from session.
 *
 * Key responsibilities:
 * - File download management with priority support
 * - Real-time download progress tracking via fileUpdates
 * - File chunk reading for streaming (Zero-Copy preparation)
 * - Download cancellation
 * - File info caching
 * - Storage optimization and cleanup
 *
 * This implementation is designed to support the Streaming cluster's DataSource
 * with efficient chunk-based access and in-memory buffer support.
 *
 * Following TDLib coroutines documentation:
 * - Uses downloadFile API with priority and offset support
 * - Implements proper caching to prevent bloat
 * - Manages concurrent downloads efficiently
 * - Provides real-time download progress tracking via fileUpdates flow
 */
class T_TelegramFileDownloader(
    private val context: Context,
    private val session: T_TelegramSession,
) {
    private val client get() = session.client

    // Cache for file info to avoid repeated TDLib calls - thread-safe
    private val fileInfoCache = ConcurrentHashMap<String, File>()

    // Active downloads tracker - thread-safe
    private val activeDownloads = ConcurrentHashMap.newKeySet<Int>()

    /**
     * Get file size from TDLib.
     * Returns -1 if size is unknown.
     *
     * @param fileId TDLib file ID
     * @return File size in bytes, or -1 if unknown
     */
    suspend fun getFileSize(fileId: String): Long =
        withContext(Dispatchers.IO) {
            val fileInfo = getFileInfo(fileId)
            fileInfo.expectedSize?.toLong() ?: -1L
        }

    /**
     * Read a chunk of data from a Telegram file.
     * Downloads the required portion if not cached.
     *
     * This method is designed for streaming use cases where the DataSource
     * needs random access to file chunks.
     *
     * @param fileId TDLib file ID
     * @param position Offset in bytes
     * @param buffer Destination buffer
     * @param offset Offset in buffer to start writing
     * @param length Number of bytes to read
     * @return Number of bytes actually read, or -1 on EOF
     */
    suspend fun readFileChunk(
        fileId: String,
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        withContext(Dispatchers.IO) {
            val fileInfo = getFileInfo(fileId)
            val fileIdInt = fileInfo.id

            // Calculate which part we need
            val endPosition = position + length

            // Check if we need to download more data
            val downloadedSize = fileInfo.local?.downloadedSize?.toLong() ?: 0L

            if (downloadedSize < endPosition) {
                // Need to download more data
                // Download with higher priority for streaming
                val result =
                    client.downloadFile(
                        fileId = fileIdInt,
                        priority = 16, // High priority for streaming
                        offset = position.coerceAtLeast(0L),
                        limit = 0L, // 0 means download from offset to end
                        synchronous = true, // Wait for completion
                    )

                when (result) {
                    is dev.g000sha256.tdl.TdlResult.Success -> {
                        // Update cached file info
                        fileInfoCache[fileId] = result.result
                    }
                    is dev.g000sha256.tdl.TdlResult.Failure -> {
                        throw Exception("TDLib download failed: ${result.code} - ${result.message}")
                    }
                }
            }

            // After potential download, get the latest file info
            val updatedFileInfo = getFileInfo(fileId)
            val localPath = updatedFileInfo.local?.path
            if (localPath.isNullOrBlank()) {
                throw Exception("File not downloaded yet: $fileId")
            }

            val file = java.io.File(localPath)
            if (!file.exists()) {
                throw Exception("Downloaded file not found: $localPath")
            }

            RandomAccessFile(file, "r").use { raf ->
                if (position >= raf.length()) {
                    return@withContext -1 // EOF
                }

                raf.seek(position)
                val bytesToRead = min(length, (raf.length() - position).toInt())
                return@withContext raf.read(buffer, offset, bytesToRead)
            }
        }

    /**
     * Start downloading a file and return immediately.
     * Use observeDownloadProgress() to track progress.
     *
     * @param fileId TDLib file ID (integer)
     * @param priority Download priority (1-32, higher = more important, default 16)
     * @return true if download started successfully
     */
    suspend fun startDownload(
        fileId: Int,
        priority: Int = 16,
    ): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                activeDownloads.add(fileId)

                val result =
                    client.downloadFile(
                        fileId = fileId,
                        priority = priority,
                        offset = 0L,
                        limit = 0L, // Download entire file
                        synchronous = false, // Async download
                    )

                when (result) {
                    is dev.g000sha256.tdl.TdlResult.Success -> {
                        fileInfoCache[fileId.toString()] = result.result
                        true
                    }
                    is dev.g000sha256.tdl.TdlResult.Failure -> {
                        activeDownloads.remove(fileId)
                        println("[T_TelegramFileDownloader] Download start failed: ${result.message}")
                        false
                    }
                }
            } catch (e: Exception) {
                activeDownloads.remove(fileId)
                println("[T_TelegramFileDownloader] Download start error: ${e.message}")
                false
            }
        }

    /**
     * Cancel an ongoing download.
     *
     * @param fileId TDLib file ID
     */
    suspend fun cancelDownload(fileId: String) =
        withContext(Dispatchers.IO) {
            val fileInfo = fileInfoCache[fileId] ?: return@withContext
            val fileIdInt = fileInfo.id

            if (activeDownloads.contains(fileIdInt)) {
                runCatching {
                    client.cancelDownloadFile(
                        fileId = fileIdInt,
                        onlyIfPending = false,
                    )
                }
                activeDownloads.remove(fileIdInt)
                println("[T_TelegramFileDownloader] Cancelled download for file $fileId")
            }
        }

    /**
     * Cancel an ongoing download by integer file ID.
     *
     * @param fileId TDLib file ID (integer)
     */
    suspend fun cancelDownload(fileId: Int) =
        withContext(Dispatchers.IO) {
            if (activeDownloads.contains(fileId)) {
                runCatching {
                    client.cancelDownloadFile(
                        fileId = fileId,
                        onlyIfPending = false,
                    )
                }
                activeDownloads.remove(fileId)
                println("[T_TelegramFileDownloader] Cancelled download for file $fileId")
            }
        }

    /**
     * Observe download progress for a specific file.
     * Returns a Flow that emits progress updates.
     *
     * Based on tdlib-coroutines documentation for file updates.
     *
     * @param fileId TDLib file ID (integer)
     * @return Flow of download progress
     */
    fun observeDownloadProgress(fileId: Int): Flow<DownloadProgress> =
        client.fileUpdates
            .filter { update -> update.file.id == fileId }
            .map { update ->
                val file = update.file
                val downloaded = file.local?.downloadedSize?.toLong() ?: 0L
                val total = file.expectedSize?.toLong() ?: 0L
                val isComplete = file.local?.isDownloadingCompleted ?: false

                DownloadProgress(
                    fileId = fileId,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    isComplete = isComplete,
                )
            }

    /**
     * Get file information from TDLib.
     * Uses cache to avoid repeated API calls.
     *
     * @param fileId TDLib file ID (string or integer)
     * @return File object
     */
    private suspend fun getFileInfo(fileId: String): File =
        withContext(Dispatchers.IO) {
            // Check cache first
            fileInfoCache[fileId]?.let { return@withContext it }

            // Get from TDLib
            val fileIdInt =
                try {
                    fileId.toInt()
                } catch (e: NumberFormatException) {
                    throw Exception("Invalid file ID format: $fileId")
                }

            val result = client.getFile(fileIdInt)

            when (result) {
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    val file = result.result
                    fileInfoCache[fileId] = file
                    return@withContext file
                }
                is dev.g000sha256.tdl.TdlResult.Failure -> {
                    throw Exception("Failed to get file info: ${result.code} - ${result.message}")
                }
            }
        }

    /**
     * Get file information by integer file ID.
     *
     * @param fileId TDLib file ID (integer)
     * @return File object or null if not found
     */
    suspend fun getFileInfo(fileId: Int): File? =
        withContext(Dispatchers.IO) {
            // Check cache first
            val cacheKey = fileId.toString()
            fileInfoCache[cacheKey]?.let { return@withContext it }

            // Get from TDLib
            val result = client.getFile(fileId)

            when (result) {
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    val file = result.result
                    fileInfoCache[cacheKey] = file
                    return@withContext file
                }
                is dev.g000sha256.tdl.TdlResult.Failure -> {
                    println("[T_TelegramFileDownloader] Failed to get file info: ${result.message}")
                    return@withContext null
                }
            }
        }

    /**
     * Clear old cached files to prevent bloat.
     * Should be called periodically or when cache size limit is reached.
     *
     * @param maxCacheSizeMb Maximum cache size in megabytes (default 500 MB)
     */
    suspend fun cleanupCache(maxCacheSizeMb: Long = 500) =
        withContext(Dispatchers.IO) {
            println("[T_TelegramFileDownloader] Checking cache size...")

            // Get cache statistics from TDLib
            val statsResult = client.getStorageStatistics(chatLimit = 1)

            when (statsResult) {
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    val stats = statsResult.result
                    val currentSizeMb = stats.size / (1024 * 1024)

                    println("[T_TelegramFileDownloader] Current cache size: $currentSizeMb MB")

                    if (currentSizeMb > maxCacheSizeMb) {
                        println("[T_TelegramFileDownloader] Cache size exceeds limit, optimizing...")

                        // Optimize storage to remove old files
                        val optimizeResult =
                            client.optimizeStorage(
                                size = ((currentSizeMb - maxCacheSizeMb) * 1024 * 1024).toLong(),
                                ttl = 7 * 24 * 60 * 60, // Files older than 7 days
                                count = Int.MAX_VALUE,
                                immunityDelay = 24 * 60 * 60, // Keep files from last 24h
                                fileTypes = emptyArray(), // All file types
                                chatIds = longArrayOf(), // All chats
                                excludeChatIds = longArrayOf(),
                                returnDeletedFileStatistics = false,
                                chatLimit = 100,
                            )

                        when (optimizeResult) {
                            is dev.g000sha256.tdl.TdlResult.Success -> {
                                // Clear our cache as well
                                fileInfoCache.clear()
                                println("[T_TelegramFileDownloader] Cache optimized successfully")
                            }
                            is dev.g000sha256.tdl.TdlResult.Failure -> {
                                println("[T_TelegramFileDownloader] Storage optimization failed: ${optimizeResult.message}")
                            }
                        }
                    }
                }
                is dev.g000sha256.tdl.TdlResult.Failure -> {
                    println("[T_TelegramFileDownloader] Storage stats query failed: ${statsResult.message}")
                }
            }
        }

    /**
     * Get count of active downloads.
     *
     * @return Number of active downloads
     */
    fun getActiveDownloadCount(): Int = activeDownloads.size

    /**
     * Clear the file info cache.
     * Useful when you want to force fresh data from TDLib.
     */
    fun clearFileCache() {
        fileInfoCache.clear()
        println("[T_TelegramFileDownloader] File info cache cleared")
    }
}
