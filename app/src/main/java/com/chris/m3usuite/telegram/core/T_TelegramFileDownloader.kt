package com.chris.m3usuite.telegram.core

import android.content.Context
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * Represents the current window state for a file being streamed.
 *
 * @property fileId TDLib file ID
 * @property windowStart Starting byte offset of the current window
 * @property windowSize Size of the current window in bytes
 * @property localSize Number of bytes already downloaded within this window
 * @property isComplete Whether the window download is complete
 */
data class WindowState(
    val fileId: Int,
    var windowStart: Long,
    var windowSize: Long,
    var localSize: Long = 0,
    var isComplete: Boolean = false,
)

/**
 * Handles file downloads from Telegram using TDLib with **Windowed Zero-Copy Streaming**.
 *
 * This class DOES NOT create its own TdlClient - it receives an injected session
 * from T_TelegramServiceClient. All operations use the client from session.
 *
 * **Windowed Zero-Copy Streaming:**
 * - TDLib cached Mediendateien weiterhin auf Disk (unvermeidbar)
 * - Es wird nur ein Fenster (z.B. 16MB) der Datei rund um die aktuelle Abspielposition geladen
 * - Beim Spulen werden alte Fenster verworfen und neue Fenster an der Zielposition geöffnet
 * - `readFileChunk()` schreibt **direkt** aus dem TDLib-Cache in den Player-Buffer ohne zusätzliche Kopien
 * - Gilt nur für direkt abspielbare Medien: MOVIE, EPISODE, CLIP, AUDIO
 * - RAR_ARCHIVE und andere Container nutzen weiterhin Voll-Download
 *
 * Key responsibilities:
 * - Windowed file download management with priority support
 * - Real-time download progress tracking via fileUpdates
 * - Zero-Copy file chunk reading for streaming (direct buffer writes)
 * - Window state management and automatic window transitions
 * - Download cancellation and cleanup
 * - File info and handle caching
 * - Storage optimization and cleanup
 *
 * This implementation is designed to support the Streaming cluster's DataSource
 * with efficient windowed access and Zero-Copy buffer handling.
 *
 * Following TDLib coroutines documentation:
 * - Uses downloadFile API with offset/limit support for windowing
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

    // Window state per file for streaming - thread-safe
    private val windowStates = ConcurrentHashMap<Int, WindowState>()

    // File handle cache for Zero-Copy reads - thread-safe
    private val fileHandleCache = ConcurrentHashMap<Int, RandomAccessFile>()

    // In-Memory Ringbuffer pro File für Windowed Streaming
    private val streamingBuffers = ConcurrentHashMap<Int, ChunkRingBuffer>()

    private fun getRingBuffer(fileId: Int): ChunkRingBuffer =
        streamingBuffers.computeIfAbsent(fileId) {
            ChunkRingBuffer(
                chunkSize = StreamingConfig.RINGBUFFER_CHUNK_SIZE_BYTES,
                maxChunks = StreamingConfig.RINGBUFFER_MAX_CHUNKS,
            )
        }

    /**
     * Ensure a download window is active for the specified file and position.
     *
     * This method implements the windowing logic for Zero-Copy streaming:
     * - Checks if current window covers the requested position
     * - If not, cancels old downloads and starts a new windowed download
     * - Updates window state for progress tracking
     *
     * **Window Management:**
     * - Window starts at `windowStart` and spans `windowSize` bytes
     * - Downloads with high priority for streaming
     * - Asynchronous download continues in background
     * - Progress can be tracked via `observeDownloadProgress()`
     *
     * @param fileIdInt TDLib file ID (integer)
     * @param windowStart Starting byte offset for the window
     * @param windowSize Size of the window in bytes
     * @return true if window is active, false if setup failed
     */
    suspend fun ensureWindow(
        fileIdInt: Int,
        windowStart: Long,
        windowSize: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "ensureWindow start",
                details =
                    mapOf(
                        "fileId" to fileIdInt.toString(),
                        "windowStart" to windowStart.toString(),
                        "windowSize" to windowSize.toString(),
                    ),
            )

            val existingWindow = windowStates[fileIdInt]

            // Check if existing window covers the requested range
            if (existingWindow != null) {
                val windowEnd = existingWindow.windowStart + existingWindow.windowSize
                if (windowStart >= existingWindow.windowStart && (windowStart + windowSize) <= windowEnd) {
                    // Current window fully covers the requested range
                    return@withContext true
                }

                // Need new window - cancel old download
                TelegramLogRepository.logStreamingActivity(
                    fileId = fileIdInt,
                    action = "window_switch",
                    details =
                        mapOf(
                            "old_start" to existingWindow.windowStart.toString(),
                            "new_start" to windowStart.toString(),
                            "window_size" to windowSize.toString(),
                        ),
                )

                // Cancel old download if active
                if (activeDownloads.contains(fileIdInt)) {
                    runCatching {
                        client.cancelDownloadFile(
                            fileId = fileIdInt,
                            onlyIfPending = false,
                        )
                    }
                    activeDownloads.remove(fileIdInt)
                }

                // Close old file handle
                fileHandleCache.remove(fileIdInt)?.close()
            } else {
                TelegramLogRepository.logStreamingActivity(
                    fileId = fileIdInt,
                    action = "window_open",
                    details =
                        mapOf(
                            "start" to windowStart.toString(),
                            "size" to windowSize.toString(),
                        ),
                )
            }

            // Create new window state
            val newWindowState =
                WindowState(
                    fileId = fileIdInt,
                    windowStart = windowStart,
                    windowSize = windowSize,
                    localSize = 0,
                    isComplete = false,
                )
            windowStates[fileIdInt] = newWindowState

            // Neues Fenster => alten In-Memory-Buffer verwerfen
            getRingBuffer(fileIdInt).clear()

            // Start windowed download
            activeDownloads.add(fileIdInt)

            val result =
                client.downloadFile(
                    fileId = fileIdInt,
                    priority = 32, // High priority for streaming
                    offset = windowStart.coerceAtLeast(0L),
                    limit = windowSize, // Download only the window
                    synchronous = false, // Async download
                )

            when (result) {
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    val file = result.result
                    fileInfoCache[fileIdInt.toString()] = file

                    // Update window state with initial progress
                    val downloadedInWindow = file.local?.downloadedSize?.toLong() ?: 0L
                    newWindowState.localSize = downloadedInWindow.coerceAtLeast(0)
                    newWindowState.isComplete = file.local?.isDownloadingCompleted ?: false

                    TelegramLogRepository.logFileDownload(
                        fileId = fileIdInt,
                        progress = downloadedInWindow.toInt(),
                        total = windowSize.toInt(),
                        status = "window_started",
                    )

                    TelegramLogRepository.debug(
                        source = "T_TelegramFileDownloader",
                        message = "ensureWindow complete",
                        details =
                            mapOf(
                                "fileId" to fileIdInt.toString(),
                                "windowStart" to windowStart.toString(),
                                "windowSize" to windowSize.toString(),
                            ),
                    )
                    true
                }
                is dev.g000sha256.tdl.TdlResult.Failure -> {
                    activeDownloads.remove(fileIdInt)
                    windowStates.remove(fileIdInt)
                    TelegramLogRepository.error(
                        source = "T_TelegramFileDownloader",
                        message = "Window download failed",
                        details =
                            mapOf(
                                "fileId" to fileIdInt.toString(),
                                "error" to result.message,
                            ),
                    )
                    TelegramLogRepository.debug(
                        source = "T_TelegramFileDownloader",
                        message = "ensureWindow failed",
                        details =
                            mapOf(
                                "fileId" to fileIdInt.toString(),
                                "windowStart" to windowStart.toString(),
                                "windowSize" to windowSize.toString(),
                                "error" to result.message,
                            ),
                    )
                    false
                }
            }
        }

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
     * Check if file data is downloaded at the specified position.
     * 
     * This checks:
     * 1. If the file has a local path
     * 2. If the local file exists
     * 3. If the local file is large enough to contain data at the requested position
     *
     * @param fileId TDLib file ID (string)
     * @param position Byte offset to check
     * @return true if data is available at position, false otherwise
     */
    private suspend fun isDownloadedAt(
        fileId: String,
        position: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val fileInfo = getFileInfo(fileId)
                val localPath = fileInfo.local?.path
                
                // Check if file has a local path
                if (localPath.isNullOrBlank()) {
                    return@withContext false
                }
                
                // Check if file exists and is large enough
                val file = java.io.File(localPath)
                if (!file.exists()) {
                    return@withContext false
                }
                
                // Check if file size is sufficient for requested position
                // Need at least position + 1 byte to read from position
                return@withContext file.length() > position
            } catch (e: Exception) {
                // Any error means data is not available
                return@withContext false
            }
        }

    /**
     * Read a chunk of data from a Telegram file with **Zero-Copy** optimization.
     *
     * This method implements Zero-Copy streaming by:
     * - Reusing cached RandomAccessFile handles
     * - Writing directly from TDLib cache file into the provided buffer
     * - Avoiding intermediate ByteArray allocations
     * - Handling race conditions with retry logic for closed streams
     *
     * For windowed streaming, ensure `ensureWindow()` is called before reading.
     *
     * @param fileId TDLib file ID (string)
     * @param position Offset in bytes
     * @param buffer Destination buffer (provided by ExoPlayer/Media3)
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
            // Get file info once to get fileIdInt
            val fileInfo = getFileInfo(fileId)
            val fileIdInt = fileInfo.id

            // Blocking retry: wait for TDLib to download the first bytes at position
            var retryAttempts = 0
            val maxRetryAttempts = StreamingConfig.READ_RETRY_MAX_ATTEMPTS

            while (!isDownloadedAt(fileId, position)) {
                if (retryAttempts >= maxRetryAttempts) {
                    TelegramLogRepository.error(
                        source = "T_TelegramFileDownloader",
                        message = "read(): timeout waiting for chunk",
                        details =
                            mapOf(
                                "fileId" to fileId,
                                "position" to position.toString(),
                                "attempts" to retryAttempts.toString(),
                            ),
                    )
                    throw Exception(
                        "Timeout: Telegram file not downloaded at position $position after $retryAttempts attempts (fileId=$fileId)",
                    )
                }

                // Log retry attempt
                if (retryAttempts % 20 == 0) {
                    // Log every 20th attempt to avoid log spam (~300ms intervals)
                    TelegramLogRepository.debug(
                        source = "T_TelegramFileDownloader",
                        message = "read(): waiting for chunk",
                        details =
                            mapOf(
                                "fileId" to fileId,
                                "position" to position.toString(),
                                "attempt" to retryAttempts.toString(),
                            ),
                    )
                }

                retryAttempts++
                delay(StreamingConfig.READ_RETRY_DELAY_MS)

                // Re-trigger window download to ensure it's still active
                if (retryAttempts % 50 == 0) {
                    // Every 50 attempts (~750ms), re-ensure window
                    val windowState = windowStates[fileIdInt]
                    if (windowState != null) {
                        runCatching {
                            ensureWindow(fileIdInt, windowState.windowStart, windowState.windowSize)
                        }
                    }
                }
            }

            // Log successful chunk availability
            if (retryAttempts > 0) {
                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "read(): chunk available, reading...",
                    details =
                        mapOf(
                            "fileId" to fileId,
                            "position" to position.toString(),
                            "attempts" to retryAttempts.toString(),
                        ),
                )
            }

            // Now proceed with actual file reading
            val localPath = fileInfo.local?.path
            
            if (localPath.isNullOrBlank()) {
                // This should not happen after retry loop, but handle gracefully
                throw Exception("File not downloaded yet: $fileId (after retry)")
            }

            val file = java.io.File(localPath)
            if (!file.exists()) {
                throw Exception("Downloaded file not found: $localPath")
            }

            val ringBuffer = getRingBuffer(fileIdInt)

            // Falls der Ringbuffer den gesamten Bereich schon im RAM hat, direkt von dort lesen
            if (ringBuffer.containsRange(position, length)) {
                val fromCache = ringBuffer.read(position, buffer, offset, length)
                if (fromCache > 0) {
                    return@withContext fromCache
                }
                // Wenn aus irgendeinem Grund 0 zurückkommt, normal weiter unten über File lesen
            }

            // Retry logic to handle race condition where file handle is closed by another thread
            var ioRetryCount = 0
            val maxIoRetries = StreamingConfig.MAX_READ_ATTEMPTS

            while (ioRetryCount < maxIoRetries) {
                ioRetryCount++

                try {
                    // Get or create cached file handle for Zero-Copy reads
                    val raf =
                        fileHandleCache.computeIfAbsent(fileIdInt) {
                            RandomAccessFile(file, "r")
                        }

                    if (position >= raf.length()) {
                        return@withContext -1 // EOF
                    }

                    raf.seek(position)
                    val bytesToRead = min(length, (raf.length() - position).toInt())

                    // Zero-Copy: write directly into buffer
                    val bytesRead = raf.read(buffer, offset, bytesToRead)

                    if (bytesRead > 0) {
                        // Gelesene Daten zusätzlich im Ringbuffer cachen
                        ringBuffer.write(position, buffer, offset, bytesRead)
                    }

                    return@withContext bytesRead
                } catch (e: java.io.IOException) {
                    // Handle closed stream or stale handle - remove from cache and retry
                    fileHandleCache.remove(fileIdInt)?.runCatching { close() }

                    if (ioRetryCount >= maxIoRetries) {
                        // Max attempts reached, rethrow exception
                        throw Exception("Failed to read file chunk after $maxIoRetries attempts", e)
                    }

                    // Log retry for debugging
                    TelegramLogRepository.debug(
                        source = "T_TelegramFileDownloader",
                        message = "Retrying read after closed stream",
                        details =
                            mapOf(
                                "fileId" to fileId,
                                "position" to position.toString(),
                                "retryCount" to ioRetryCount.toString(),
                            ),
                    )
                } catch (e: Exception) {
                    // For other exceptions, remove stale handle and rethrow
                    fileHandleCache.remove(fileIdInt)?.runCatching { close() }
                    throw e
                }
            }

            throw Exception("Failed to read file chunk after $maxIoRetries attempts")
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

                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "Starting download",
                    details = mapOf("fileId" to fileId.toString(), "priority" to priority.toString()),
                )

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
                        TelegramLogRepository.logFileDownload(
                            fileId = fileId,
                            progress = 0,
                            total = (result.result.expectedSize ?: 0).toInt(),
                            status = "started",
                        )
                        true
                    }
                    is dev.g000sha256.tdl.TdlResult.Failure -> {
                        activeDownloads.remove(fileId)
                        TelegramLogRepository.error(
                            source = "T_TelegramFileDownloader",
                            message = "Download start failed",
                            details = mapOf("fileId" to fileId.toString(), "error" to result.message),
                        )
                        false
                    }
                }
            } catch (e: Exception) {
                activeDownloads.remove(fileId)
                TelegramLogRepository.error(
                    source = "T_TelegramFileDownloader",
                    message = "Download start error",
                    exception = e,
                    details = mapOf("fileId" to fileId.toString()),
                )
                false
            }
        }

    /**
     * Cancel an ongoing download and clean up associated resources.
     *
     * @param fileId TDLib file ID (string)
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

                // Clean up window state and file handle
                windowStates.remove(fileIdInt)
                fileHandleCache.remove(fileIdInt)?.runCatching { close() }
                streamingBuffers.remove(fileIdInt)?.clear()

                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "Cancelled download",
                    details = mapOf("fileId" to fileId),
                )
            }
        }

    /**
     * Cancel an ongoing download by integer file ID and clean up associated resources.
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

                // Clean up window state and file handle
                windowStates.remove(fileId)
                fileHandleCache.remove(fileId)?.runCatching { close() }
                streamingBuffers.remove(fileId)?.clear()

                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "Cancelled download",
                    details = mapOf("fileId" to fileId.toString()),
                )
            }
        }

    /**
     * Explicitly cleanup file handle for a given file ID.
     * This ensures file handles are closed even if download cancellation fails.
     * Safe to call multiple times - idempotent.
     *
     * @param fileId TDLib file ID (integer)
     */
    suspend fun cleanupFileHandle(fileId: Int) =
        withContext(Dispatchers.IO) {
            fileHandleCache.remove(fileId)?.runCatching { close() }
            streamingBuffers.remove(fileId)?.clear()
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Cleaned up file handle",
                details = mapOf("fileId" to fileId.toString()),
            )
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
