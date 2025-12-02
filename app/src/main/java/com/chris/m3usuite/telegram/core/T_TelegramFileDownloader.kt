package com.chris.m3usuite.telegram.core

import android.content.Context
import android.os.SystemClock
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
 * Handles file downloads from Telegram using TDLib with **Zero-Copy Streaming**.
 *
 * This class DOES NOT create its own TdlClient - it receives an injected session
 * from T_TelegramServiceClient. All operations use the client from session.
 *
 * **Zero-Copy Streaming Architecture (Updated):**
 * - TDLib caches media files on disk (unavoidable)
 * - `ensureFileReady()` ensures TDLib has downloaded required prefix/range
 * - TelegramFileDataSource delegates I/O to Media3's FileDataSource
 * - No in-memory ringbuffer - direct file access via FileDataSource
 * - ExoPlayer/FileDataSource handles seeking and position tracking
 * - Applies to all media types: MOVIE, EPISODE, CLIP, AUDIO
 * - RAR_ARCHIVE uses different extraction path
 *
 * **Legacy Windowed Streaming (Deprecated):**
 * - Old implementation used ensureWindow() with in-memory ChunkRingBuffer
 * - readFileChunk() wrote directly to player buffers from TDLib cache
 * - This has been replaced by TelegramFileDataSource + FileDataSource
 *
 * Key responsibilities:
 * - File download management with priority support
 * - Real-time download progress tracking via fileUpdates
 * - Zero-Copy file chunk reading for legacy code paths
 * - Window state management for legacy streaming
 * - Download cancellation and cleanup
 * - File info and handle caching
 * - Storage optimization and cleanup
 * - ensureFileReady() for new TelegramFileDataSource architecture
 *
 * Following TDLib coroutines documentation:
 * - Uses downloadFile API with offset/limit support
 * - Implements proper caching to prevent bloat
 * - Manages concurrent downloads efficiently
 * - Provides real-time download progress tracking via fileUpdates flow
 */
class T_TelegramFileDownloader(
    private val context: Context,
    private val session: T_TelegramSession,
) {
    private val client get() = session.client

    companion object {
        // Streaming-friendly constants (Phase D+ ~50MB window policy)
        private const val TELEGRAM_STREAM_WINDOW_BYTES: Long = 50L * 1024L * 1024L // 50 MB max per video
        private const val TELEGRAM_MIN_PREFIX_BYTES: Long = 256L * 1024L // 256 KB minimum for playback start
        private const val STREAMING_MAX_TIMEOUT_MS: Long = 10_000L // 10 seconds for initial window
        private const val POLL_INTERVAL_MS: Long = 150L // 150ms polling for progress updates

        // Legacy constants (kept for backward compatibility)
        private const val MIN_START_BYTES = 64 * 1024L // 64KB minimum for early success
        private const val STALL_TIMEOUT_MS = 5_000L // 5 seconds without progress = stalled
        private const val PROGRESS_RESET_TIMEOUT_MS = 60_000L // Max 60s timeout when actively progressing
        
        // Seek margin for SEEK mode (1 MB buffer)
        private const val SEEK_MARGIN_BYTES: Long = 1L * 1024L * 1024L // 1 MB margin for seeks
    }

    /**
     * Mode for ensureFileReady to distinguish initial playback from seeks.
     */
    enum class EnsureFileReadyMode {
        /**
         * Initial start of playback (dataSpecPosition == 0).
         * Requires only a small fixed prefix (256 KB or less), independent of any future positions.
         */
        INITIAL_START,

        /**
         * Seek operation (dataSpecPosition > 0).
         * Requires only startPosition + margin (e.g., +1 MB) instead of startPosition + 256 KB up to totalSize.
         */
        SEEK,
    }

    // Cache for file info to avoid repeated TDLib calls - thread-safe
    private val fileInfoCache = ConcurrentHashMap<String, File>()

    // Active downloads tracker - thread-safe
    private val activeDownloads = ConcurrentHashMap.newKeySet<Int>()

    // Window state per file for streaming - thread-safe
    private val windowStates = ConcurrentHashMap<Int, WindowState>()

    // File handle cache for Zero-Copy reads - thread-safe
    private val fileHandleCache = ConcurrentHashMap<Int, RandomAccessFile>()

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
     * Ensure TDLib has downloaded a file with a sliding window for streaming.
     * This is used by TelegramFileDataSource for zero-copy streaming.
     *
     * **Phase D+ Sliding Window Strategy:**
     * 1. Get current file info from TDLib to determine total size
     * 2. Compute streaming window based on mode:
     *    - INITIAL_START: Small fixed prefix (256 KB or less), capped at fileSizeBytes
     *    - SEEK: startPosition + margin (1 MB), avoiding large end-of-file windows
     * 3. Request only the window via downloadFile(offset=windowStart, limit=windowSize)
     * 4. Poll TDLib state until enough prefix is available for playback
     * 5. Return local file path
     *
     * **Key behaviors:**
     * - Never downloads full file for streaming (uses limit parameter)
     * - INITIAL_START: Quick startup with minimal prefix
     * - SEEK: Avoids runaway downloads near end-of-file
     * - Progress-aware timeout: resets when download is actively progressing
     * - Early success fallback: allows playback when minimum data available
     *
     * @param fileId TDLib file ID (integer)
     * @param startPosition Starting byte offset for the streaming window
     * @param minBytes Hint for minimum bytes needed (used for INITIAL_START)
     * @param mode INITIAL_START for initial playback, SEEK for seek operations
     * @param fileSizeBytes Optional known file size from TDLib DTOs (avoids extra queries)
     * @param timeoutMs Maximum time to wait in milliseconds (default 30 seconds)
     * @return Local file path from TDLib cache
     * @throws Exception if download fails or times out
     */
    suspend fun ensureFileReady(
        fileId: Int,
        startPosition: Long,
        minBytes: Long,
        mode: EnsureFileReadyMode = EnsureFileReadyMode.INITIAL_START,
        fileSizeBytes: Long? = null,
        timeoutMs: Long = 30_000L,
    ): String {
        return withContext(Dispatchers.IO) {
            // 1. Get current file status from TDLib (fresh, no cache)
            var file = getFreshFileState(fileId)

            // Use provided fileSizeBytes if available, otherwise use TDLib's expectedSize
            val totalSize = fileSizeBytes ?: file.expectedSize?.toLong() ?: Long.MAX_VALUE
            val localPath = file.local?.path

            // 2. Compute streaming window based on mode
            val windowStart = startPosition.coerceAtLeast(0L)
            val windowEnd: Long
            val windowSize: Long
            val requiredPrefixFromStart: Long

            when (mode) {
                EnsureFileReadyMode.INITIAL_START -> {
                    // INITIAL_START: Small fixed prefix for quick startup
                    // Cap at totalSize to avoid requesting beyond file size
                    windowEnd = (windowStart + TELEGRAM_MIN_PREFIX_BYTES).coerceAtMost(totalSize)
                    windowSize = windowEnd - windowStart
                    requiredPrefixFromStart = windowEnd
                }
                EnsureFileReadyMode.SEEK -> {
                    // SEEK: Request only startPosition + margin (1 MB)
                    // This avoids "download almost the whole file" when seeking near the end
                    windowEnd = (windowStart + SEEK_MARGIN_BYTES).coerceAtMost(totalSize)
                    windowSize = windowEnd - windowStart
                    requiredPrefixFromStart = windowEnd
                }
            }

            val initialPrefix = file.local?.downloadedPrefixSize?.toLong() ?: 0L

            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "ensureFileReady: starting with mode=$mode",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "mode" to mode.name,
                        "startPosition" to startPosition.toString(),
                        "windowStart" to windowStart.toString(),
                        "windowEnd" to windowEnd.toString(),
                        "windowSize" to windowSize.toString(),
                        "totalSize" to totalSize.toString(),
                        "fileSizeBytes" to (fileSizeBytes?.toString() ?: "unknown"),
                        "requiredPrefixFromStart" to requiredPrefixFromStart.toString(),
                        "initialPrefix" to initialPrefix.toString(),
                    ),
            )

            // 3. Check if already satisfied
            if (!localPath.isNullOrBlank() && initialPrefix >= requiredPrefixFromStart) {
                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "ensureFileReady: already satisfied",
                    details =
                        mapOf(
                            "fileId" to fileId.toString(),
                            "downloadedPrefixSize" to initialPrefix.toString(),
                            "requiredPrefixFromStart" to requiredPrefixFromStart.toString(),
                            "path" to localPath,
                        ),
                )
                return@withContext localPath
            }

            // 4. Need to download - start windowed download
            // Download from windowStart with limit=windowSize (capped at 50MB)
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "ensureFileReady: starting windowed download",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "windowStart" to windowStart.toString(),
                        "windowSize" to windowSize.toString(),
                        "currentPrefix" to initialPrefix.toString(),
                        "requiredPrefix" to requiredPrefixFromStart.toString(),
                        "totalSize" to totalSize.toString(),
                    ),
            )

            val downloadResult =
                client.downloadFile(
                    fileId = fileId,
                    priority = 32, // High priority for streaming
                    offset = windowStart,
                    limit = windowSize, // Download only the window (max 50MB)
                    synchronous = false, // Async download
                )

            if (downloadResult is dev.g000sha256.tdl.TdlResult.Failure) {
                val errorMsg = "Download failed: ${downloadResult.message}"
                TelegramLogRepository.error(
                    source = "T_TelegramFileDownloader",
                    message = errorMsg,
                    details =
                        mapOf(
                            "fileId" to fileId.toString(),
                            "windowStart" to windowStart.toString(),
                            "windowSize" to windowSize.toString(),
                            "totalSize" to totalSize.toString(),
                            "code" to downloadResult.code.toString(),
                        ),
                )
                throw Exception(errorMsg)
            }

            // 5. Progress-aware polling loop
            // Poll TDLib state with POLL_INTERVAL_MS until enough prefix is available
            val startTime = SystemClock.elapsedRealtime()
            var lastProgressTime = startTime
            var lastDownloaded = initialPrefix

            var result: String? = null
            while (result == null) {
                delay(POLL_INTERVAL_MS) // 150ms polling interval

                // Get fresh file state from TDLib (bypassing cache)
                file = getFreshFileState(fileId)
                val prefix = file.local?.downloadedPrefixSize?.toLong() ?: 0L
                val pathNow = file.local?.path

                // Check if we have enough data for playback
                if (!pathNow.isNullOrBlank() && prefix >= requiredPrefixFromStart) {
                    TelegramLogRepository.debug(
                        source = "T_TelegramFileDownloader",
                        message = "ensureFileReady: streaming window ready",
                        details =
                            mapOf(
                                "fileId" to fileId.toString(),
                                "downloadedPrefixSize" to prefix.toString(),
                                "requiredPrefixFromStart" to requiredPrefixFromStart.toString(),
                                "windowSize" to windowSize.toString(),
                                "path" to pathNow,
                            ),
                    )
                    result = pathNow
                } else {
                    // Track progress
                    if (prefix > lastDownloaded) {
                        lastDownloaded = prefix
                        lastProgressTime = SystemClock.elapsedRealtime()
                    }

                    // Check timeout - use timeSinceProgress when download is actively progressing
                    val elapsed = SystemClock.elapsedRealtime() - startTime
                    val timeSinceProgress = SystemClock.elapsedRealtime() - lastProgressTime

                    // Streaming-friendly fallback: if we have minimum data and download stalled, allow playback
                    val hasMinimumDataFromWindow = prefix >= (windowStart + TELEGRAM_MIN_PREFIX_BYTES).coerceAtMost(totalSize)

                    if (!pathNow.isNullOrBlank() &&
                        hasMinimumDataFromWindow &&
                        timeSinceProgress > STALL_TIMEOUT_MS
                    ) {
                        TelegramLogRepository.info(
                            source = "T_TelegramFileDownloader",
                            message = "ensureFileReady: streaming fallback (stalled with sufficient data)",
                            details =
                                mapOf(
                                    "fileId" to fileId.toString(),
                                    "downloadedPrefixSize" to prefix.toString(),
                                    "requiredPrefixFromStart" to requiredPrefixFromStart.toString(),
                                    "windowStart" to windowStart.toString(),
                                    "minPrefixBytes" to TELEGRAM_MIN_PREFIX_BYTES.toString(),
                                    "totalSize" to totalSize.toString(),
                                    "path" to pathNow,
                                ),
                        )
                        result = pathNow
                    } else if (timeSinceProgress > timeoutMs) {
                        // Use timeSinceProgress for timeout when download has stalled
                        val errorMsg =
                            "Timeout waiting for streaming window (no progress): fileId=$fileId, downloaded=$prefix, " +
                                "required=$requiredPrefixFromStart, totalSize=$totalSize, stallTime=${timeSinceProgress}ms"
                        TelegramLogRepository.error(
                            source = "T_TelegramFileDownloader",
                            message = errorMsg,
                            details =
                                mapOf(
                                    "fileId" to fileId.toString(),
                                    "downloaded" to prefix.toString(),
                                    "required" to requiredPrefixFromStart.toString(),
                                    "windowStart" to windowStart.toString(),
                                    "windowSize" to windowSize.toString(),
                                    "totalSize" to totalSize.toString(),
                                    "stallTime" to timeSinceProgress.toString(),
                                ),
                        )
                        throw Exception(errorMsg)
                    } else if (elapsed > PROGRESS_RESET_TIMEOUT_MS && lastDownloaded == initialPrefix) {
                        // Absolute timeout if no progress at all after extended period
                        val errorMsg =
                            "Timeout waiting for streaming window (no initial progress): fileId=$fileId, " +
                                "downloaded=$prefix, required=$requiredPrefixFromStart, totalSize=$totalSize"
                        TelegramLogRepository.error(
                            source = "T_TelegramFileDownloader",
                            message = errorMsg,
                            details =
                                mapOf(
                                    "fileId" to fileId.toString(),
                                    "downloaded" to prefix.toString(),
                                    "required" to requiredPrefixFromStart.toString(),
                                    "windowStart" to windowStart.toString(),
                                    "windowSize" to windowSize.toString(),
                                    "totalSize" to totalSize.toString(),
                                    "elapsed" to elapsed.toString(),
                                ),
                        )
                        throw Exception(errorMsg)
                    }
                }
            }
            result
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
                // If position is at EOF and download is complete, allow EOF handling
                if (file.length() > position) {
                    return@withContext true
                }
                if (file.length() == position && fileInfo.local?.isDownloadingCompleted == true) {
                    return@withContext true
                }
                return@withContext false
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
                        "Timeout: Data not available at position $position after $retryAttempts attempts (fileId=$fileId)",
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
            val updatedFileInfo = getFileInfo(fileId)
            val localPath = updatedFileInfo.local?.path

            if (localPath.isNullOrBlank()) {
                // This should not happen after retry loop, but handle gracefully
                throw Exception("File path not available yet: $fileId (after retry)")
            }

            val file = java.io.File(localPath)
            if (!file.exists()) {
                throw Exception("Downloaded file not found: $localPath")
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
                                "ioRetryCount" to ioRetryCount.toString(),
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
     * Get a file from TDLib or throw an exception on failure.
     * This is a shared helper to avoid duplicating error handling.
     *
     * @param fileId TDLib file ID (integer)
     * @return File object from TDLib
     * @throws Exception if the request fails
     */
    private suspend fun getFileOrThrow(fileId: Int): File =
        withContext(Dispatchers.IO) {
            when (val result = client.getFile(fileId)) {
                is dev.g000sha256.tdl.TdlResult.Success -> result.result
                is dev.g000sha256.tdl.TdlResult.Failure ->
                    throw Exception("Failed to get file $fileId: ${result.code} - ${result.message}")
            }
        }

    /**
     * Get fresh file state from TDLib without using cache.
     * Used by ensureFileReady to poll actual download progress.
     *
     * @param fileId TDLib file ID (integer)
     * @return Fresh File object from TDLib
     */
    private suspend fun getFreshFileState(fileId: Int): File = getFileOrThrow(fileId)

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

            // Use getFileOrThrow helper to fetch the file
            val file = getFileOrThrow(fileIdInt)
            fileInfoCache[fileId] = file
            file
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

            // Get from TDLib using getFileOrThrow helper
            try {
                val file = getFileOrThrow(fileId)
                fileInfoCache[cacheKey] = file
                file
            } catch (e: Exception) {
                println("[T_TelegramFileDownloader] Failed to get file info: ${e.message}")
                null
            }
        }

    /**
     * Resolve a remote file ID to a local file ID.
     *
     * Phase D+: This is the key method for remoteId-first playback wiring.
     * Uses TDLib's getRemoteFile API to resolve a stable remoteId to a volatile fileId.
     *
     * The remoteId is stable across sessions and devices, while fileId is only valid
     * for the current TDLib instance and may become stale.
     *
     * @param remoteId Stable remote file identifier (from TelegramMediaRef.remoteId)
     * @return TDLib local file ID, or null if resolution fails
     */
    suspend fun resolveRemoteFileId(remoteId: String): Int? =
        withContext(Dispatchers.IO) {
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Resolving remoteId to fileId",
                details = mapOf("remoteId" to remoteId),
            )

            try {
                val result =
                    client.getRemoteFile(
                        remoteFileId = remoteId,
                        fileType = null, // Let TDLib determine the file type
                    )

                when (result) {
                    is dev.g000sha256.tdl.TdlResult.Success -> {
                        val file = result.result
                        val fileId = file.id

                        // Cache the resolved file info
                        fileInfoCache[fileId.toString()] = file

                        TelegramLogRepository.debug(
                            source = "T_TelegramFileDownloader",
                            message = "Resolved remoteId to fileId",
                            details =
                                mapOf(
                                    "remoteId" to remoteId,
                                    "fileId" to fileId.toString(),
                                ),
                        )
                        fileId
                    }
                    is dev.g000sha256.tdl.TdlResult.Failure -> {
                        TelegramLogRepository.error(
                            source = "T_TelegramFileDownloader",
                            message = "Failed to resolve remoteId",
                            details =
                                mapOf(
                                    "remoteId" to remoteId,
                                    "error" to result.message,
                                    "code" to result.code.toString(),
                                ),
                        )
                        null
                    }
                }
            } catch (e: Exception) {
                TelegramLogRepository.error(
                    source = "T_TelegramFileDownloader",
                    message = "Exception resolving remoteId",
                    exception = e,
                    details = mapOf("remoteId" to remoteId),
                )
                null
            }
        }

    /**
     * Clear old cached files to prevent bloat.
     * Should be called periodically or when cache size limit is reached.
     *
     * **Phase D+ Global Storage Management (500 MB cap):**
     * - Uses getStorageStatisticsFast() for quick stats check
     * - Calls optimizeStorage() when threshold exceeded to free space
     * - Designed as background maintenance operation
     *
     * @param maxCacheSizeMb Maximum cache size in megabytes (default 500 MB)
     */
    suspend fun cleanupCache(maxCacheSizeMb: Long = 500) =
        withContext(Dispatchers.IO) {
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Checking cache size for cleanup...",
            )

            // Get fast cache statistics from TDLib
            val statsResult = client.getStorageStatisticsFast()

            when (statsResult) {
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    val stats = statsResult.result
                    val currentSizeMb = stats.filesSize / (1024 * 1024)

                    TelegramLogRepository.info(
                        source = "T_TelegramFileDownloader",
                        message = "Current TDLib cache size",
                        details =
                            mapOf(
                                "sizeMB" to currentSizeMb.toString(),
                                "maxMB" to maxCacheSizeMb.toString(),
                            ),
                    )

                    if (currentSizeMb > maxCacheSizeMb) {
                        TelegramLogRepository.info(
                            source = "T_TelegramFileDownloader",
                            message = "Cache size exceeds limit, optimizing storage...",
                            details =
                                mapOf(
                                    "currentMB" to currentSizeMb.toString(),
                                    "maxMB" to maxCacheSizeMb.toString(),
                                ),
                        )

                        // Optimize storage to remove old files
                        val targetSize = ((currentSizeMb - maxCacheSizeMb) * 1024 * 1024).toLong()
                        val optimizeResult =
                            client.optimizeStorage(
                                size = targetSize,
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
                                TelegramLogRepository.info(
                                    source = "T_TelegramFileDownloader",
                                    message = "Cache optimized successfully",
                                )
                            }
                            is dev.g000sha256.tdl.TdlResult.Failure -> {
                                TelegramLogRepository.error(
                                    source = "T_TelegramFileDownloader",
                                    message = "Storage optimization failed",
                                    details = mapOf("error" to optimizeResult.message),
                                )
                            }
                        }
                    }
                }
                is dev.g000sha256.tdl.TdlResult.Failure -> {
                    TelegramLogRepository.error(
                        source = "T_TelegramFileDownloader",
                        message = "Storage stats query failed",
                        details = mapOf("error" to statsResult.message),
                    )
                }
            }
        }

    /**
     * Cancel download for a file when playback ends (Phase D+ cache management).
     * This helps keep cache size under control by stopping downloads for inactive files.
     *
     * @param fileId TDLib file ID
     * @param onlyIfPending If true, only cancel if download hasn't started yet
     */
    suspend fun cancelDownloadOnPlaybackEnd(
        fileId: Int,
        onlyIfPending: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        try {
            client.cancelDownloadFile(
                fileId = fileId,
                onlyIfPending = onlyIfPending,
            )
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Cancelled download on playback end",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "onlyIfPending" to onlyIfPending.toString(),
                    ),
            )
        } catch (e: Exception) {
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Failed to cancel download (non-critical)",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "error" to (e.message ?: "unknown"),
                    ),
            )
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
