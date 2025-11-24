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
import java.util.concurrent.ConcurrentHashMap

/**
 * Download progress information for a file.
 */
data class DownloadProgress(
    val fileId: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isComplete: Boolean,
)

/**
 * Handles file downloads from Telegram using TDLib with a zero-copy friendly strategy.
 *
 * This class DOES NOT create its own TdlClient - it receives an injected session
 * from T_TelegramServiceClient. All operations use the client from that session.
 *
 * Zero-copy strategy:
 * - TDLib caches media files on disk.
 * - `ensureFileReady()` asks TDLib to download at least a required prefix/range.
 * - ExoPlayer / Media3 use FileDataSource to read directly from the TDLib cache path.
 * - No in-memory ringbuffers or custom window buffering are implemented here.
 *
 * Key responsibilities:
 * - Start and cancel file downloads with priorities.
 * - Track download progress via the `fileUpdates` flow from tdlib-coroutines.
 * - Poll fresh file state from TDLib when waiting for a prefix to complete.
 * - Maintain a small in-memory cache of File descriptors to reduce API calls.
 * - Trigger TDLib's file database optimization when needed.
 *
 * Following tdlib-coroutines documentation:
 * - Uses downloadFile API with offset/limit support.
 * - Respects TDLib's internal file database and caching.
 * - Keeps application-side state minimal and stateless where possible.
 */
class T_TelegramFileDownloader(
    private val context: Context,
    private val session: T_TelegramSession,
) {

    private val client get() = session.client

    // File info cache keyed by TDLib file ID (string)
    private val fileInfoCache = ConcurrentHashMap<String, File>()

    // Active downloads tracker - thread-safe
    private val activeDownloads = ConcurrentHashMap.newKeySet<Int>()

    /**
     * Start download of a file with optional priority and range.
     *
     * If download is already in progress, this will be a no-op.
     *
     * @param fileId TDLib file ID (integer)
     * @param priority Download priority (default 1)
     * @param offset Optional offset in bytes
     * @param limit Optional limit in bytes
     * @param synchronous If true, blocks until download completes (not recommended for streaming)
     * @return true if download started or already in progress, false if failed
     */
    suspend fun startDownload(
        fileId: Int,
        priority: Int = 1,
        offset: Long = 0L,
        limit: Long = 0L,
        synchronous: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (activeDownloads.contains(fileId)) {
                // Already downloading
                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "startDownload: already in progress",
                    details = mapOf("fileId" to fileId.toString()),
                )
                return@withContext true
            }

            activeDownloads.add(fileId)

            val result =
                client.downloadFile(
                    fileId = fileId,
                    priority = priority,
                    offset = offset.coerceAtLeast(0L),
                    limit = limit,
                    synchronous = synchronous,
                )

            when (result) {
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    val file = result.result
                    fileInfoCache[fileId.toString()] = file

                    TelegramLogRepository.logFileDownload(
                        fileId = fileId,
                        progress = file.local?.downloadedSize ?: 0,
                        total = file.expectedSize ?: 0,
                        status = "started",
                    )

                    true
                }

                is dev.g000sha256.tdl.TdlResult.Failure -> {
                    activeDownloads.remove(fileId)
                    TelegramLogRepository.error(
                        source = "T_TelegramFileDownloader",
                        message = "startDownload failed",
                        details =
                            mapOf(
                                "fileId" to fileId.toString(),
                                "error" to result.message,
                            ),
                    )
                    false
                }
            }
        }

    /**
     * Ensure TDLib has downloaded a file up to the specified position + minBytes.
     * This is used by TelegramFileDataSource for zero-copy streaming.
     *
     * **Flow:**
     * 1. Get current file info from TDLib and check downloadedPrefixSize
     * 2. If already sufficient, return immediately
     * 3. If not, start download with offset/limit for the required range
     * 4. Poll TDLib with fresh getFile() calls until the prefix is ready or timeout occurs
     *
     * @param fileId TDLib file ID (integer)
     * @param startPosition Starting byte offset for the range
     * @param minBytes Minimum number of bytes needed from startPosition
     * @param timeoutMs Maximum time to wait in milliseconds (default 30 seconds)
     * @return Local file path from TDLib cache
     * @throws Exception if download fails or times out
     */
    suspend fun ensureFileReady(
        fileId: Int,
        startPosition: Long,
        minBytes: Long,
        timeoutMs: Long = 30_000L,
    ): String {
        return withContext(Dispatchers.IO) {
            // 1. Get current file status from TDLib (fresh, no cache)
            var file = getFreshFileState(fileId)

            val requiredPrefixSize = startPosition + minBytes
            val initialPrefix = file.local?.downloadedPrefixSize?.toLong() ?: 0L
            val localPath = file.local?.path

            // 2. Check if already satisfied
            if (!localPath.isNullOrBlank() && initialPrefix >= requiredPrefixSize) {
                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "ensureFileReady: already satisfied",
                    details =
                        mapOf(
                            "fileId" to fileId.toString(),
                            "downloadedPrefixSize" to initialPrefix.toString(),
                            "requiredPrefixSize" to requiredPrefixSize.toString(),
                            "path" to localPath,
                        ),
                )
                return@withContext localPath
            }

            // 3. Start/continue download of required range
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "ensureFileReady: starting download",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "startPosition" to startPosition.toString(),
                        "minBytes" to minBytes.toString(),
                        "currentPrefix" to initialPrefix.toString(),
                        "requiredPrefix" to requiredPrefixSize.toString(),
                    ),
            )

            val downloadResult =
                client.downloadFile(
                    fileId = fileId,
                    priority = 32, // High priority for streaming
                    offset = initialPrefix, // Start from where we left off
                    limit = requiredPrefixSize - initialPrefix, // Download what we need
                    synchronous = false, // Async download
                )

            if (downloadResult is dev.g000sha256.tdl.TdlResult.Failure) {
                throw Exception("Download failed: ${downloadResult.message}")
            }

            // 4. Wait loop - poll TDLib state with fresh requests
            val startTime = SystemClock.elapsedRealtime()
            var result: String? = null
            while (result == null) {
                delay(100) // 100ms polling interval

                // Get fresh file state from TDLib (bypassing cache)
                file = getFreshFileState(fileId)
                val prefix = file.local?.downloadedPrefixSize?.toLong() ?: 0L
                val pathNow = file.local?.path

                if (!pathNow.isNullOrBlank() && prefix >= requiredPrefixSize) {
                    TelegramLogRepository.debug(
                        source = "T_TelegramFileDownloader",
                        message = "ensureFileReady: ready",
                        details =
                            mapOf(
                                "fileId" to fileId.toString(),
                                "downloadedPrefixSize" to prefix.toString(),
                                "requiredPrefixSize" to requiredPrefixSize.toString(),
                                "path" to pathNow,
                            ),
                    )
                    result = pathNow
                } else {
                    val elapsed = SystemClock.elapsedRealtime() - startTime
                    if (elapsed > timeoutMs) {
                        throw Exception(
                            "Timeout waiting for file ready: fileId=$fileId, downloaded=$prefix, required=$requiredPrefixSize",
                        )
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
     * @param position Offset in bytes
     * @return true if data is available, false otherwise
     */
    suspend fun isDownloadedAt(fileId: String, position: Long): Boolean =
        withContext(Dispatchers.IO) {
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

            false
        }

    /**
     * Wait until data is available at the specified position, or timeout occurs.
     *
     * This is a helper for legacy callers that want a simple "wait until position ready"
     * semantic, instead of explicitly calling ensureFileReady().
     *
     * @param fileId TDLib file ID (string)
     * @param position Offset in bytes
     * @param timeoutMs Maximum time to wait in milliseconds (default 30 seconds)
     * @param pollIntervalMs Polling interval in milliseconds (default 100ms)
     * @throws Exception if data is not available within timeout
     */
    suspend fun waitForDataAt(
        fileId: String,
        position: Long,
        timeoutMs: Long = 30_000L,
        pollIntervalMs: Long = 100L,
    ) =
        withContext(Dispatchers.IO) {
            val startTime = SystemClock.elapsedRealtime()

            while (!isDownloadedAt(fileId, position)) {
                delay(pollIntervalMs)

                val elapsed = SystemClock.elapsedRealtime() - startTime
                if (elapsed > timeoutMs) {
                    TelegramLogRepository.error(
                        source = "T_TelegramFileDownloader",
                        message = "waitForDataAt(): timeout waiting for data",
                        details =
                            mapOf(
                                "fileId" to fileId,
                                "position" to position.toString(),
                                "timeoutMs" to timeoutMs.toString(),
                            ),
                    )
                    throw Exception(
                        "Timeout: Data not available at position $position after $timeoutMs ms (fileId=$fileId)",
                    )
                }
            }
        }

    /**
     * Cancel an ongoing download for a file by string file ID.
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

                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "Cancelled download",
                    details = mapOf("fileId" to fileId),
                )
            }
        }

    /**
     * Cancel an ongoing download for a file by integer file ID.
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

                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "Cancelled download",
                    details = mapOf("fileId" to fileId.toString()),
                )
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
     * Get current download progress for a file.
     *
     * @param fileId TDLib file ID (integer)
     * @return DownloadProgress or null if file not known
     */
    suspend fun getCurrentProgress(fileId: Int): DownloadProgress? =
        withContext(Dispatchers.IO) {
            val fileInfo = getFileInfo(fileId.toString())
            val downloaded = fileInfo.local?.downloadedSize?.toLong() ?: 0L
            val total = fileInfo.expectedSize?.toLong() ?: 0L
            val isComplete = fileInfo.local?.isDownloadingCompleted ?: false

            DownloadProgress(
                fileId = fileId,
                downloadedBytes = downloaded,
                totalBytes = total,
                isComplete = isComplete,
            )
        }

    /**
     * Optimize TDLib's file database and clear local cache.
     *
     * This is a helper to keep storage usage under control.
     * It triggers TDLib's `optimizeStorage` and clears the local fileInfo cache.
     */
    suspend fun optimizeStorage() =
        withContext(Dispatchers.IO) {
            runCatching {
                val optimizeResult =
                    client.optimizeStorage(
                        size = 0,
                        ttl = 0,
                        count = 0,
                        immunityDelay = 0,
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
                        println(
                            "[T_TelegramFileDownloader] Cache optimization failed: ${optimizeResult.message}",
                        )
                    }
                }
            }.onFailure { throwable ->
                println("[T_TelegramFileDownloader] optimizeStorage failed: $throwable")
            }
        }

    /**
     * Get the number of active downloads.
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

    /**
     * Internal helper to get a File object from TDLib via getFile().
     *
     * @param fileId TDLib file ID (integer)
     * @return File object from TDLib
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

            // If not in cache, fetch from TDLib
            val fileIdInt =
                fileId.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid fileId: $fileId")

            val fileInfo =
                when (val result = client.getFile(fileIdInt)) {
                    is dev.g000sha256.tdl.TdlResult.Success -> result.result
                    is dev.g000sha256.tdl.TdlResult.Failure ->
                        throw Exception(
                            "Failed to get file info for ID $fileId: ${result.code} - ${result.message}",
                        )
                }

            // Cache the result
            fileInfoCache[fileId] = fileInfo
            fileInfo
        }
}