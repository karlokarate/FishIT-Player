package com.chris.m3usuite.telegram.core

import android.content.Context
import android.os.SystemClock
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.util.Mp4HeaderParser
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Download progress information for a file. */
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
 * Window state for legacy windowed streaming.
 *
 * @deprecated Removed as part of windowing removal (2025-12-03).
 * Use standard TDLib download behavior with offset=0, limit=0 instead.
 *
 * Note: localSize and isComplete are mutable (var) because they are updated
 * in-place during download progress tracking to avoid creating new instances
 * and updating the ConcurrentHashMap on every progress update.
 */
@Deprecated(
    message = "Custom windowing removed. Use TDLib native download with offset=0, limit=0",
    level = DeprecationLevel.WARNING,
)
data class WindowState(
    val fileId: Int,
    val windowStart: Long,
    val windowSize: Long,
    var localSize: Long,
    var isComplete: Boolean,
)

/** Phase 2: Classification of download types for concurrency enforcement. */
sealed class DownloadKind {
    /** Video/media file download (VOD, Episode, etc.) */
    object VIDEO : DownloadKind()

    /** Thumbnail/poster image download */
    object THUMB : DownloadKind()
}

/**
 * Phase 2: Represents a pending download job in the queue.
 *
 * @property fileId TDLib file ID
 * @property kind Classification of download (VIDEO or THUMB)
 * @property priority Download priority (1-32, higher = more important)
 * @property offset Starting byte offset
 * @property limit Number of bytes to download (0 = entire file)
 * @property queuedAtMs Timestamp when job was enqueued
 * @property completion Completion deferred - type erased to avoid KAPT issues with TdlResult generics
 */
internal data class PendingDownloadJob(
    val fileId: Int,
    val kind: DownloadKind,
    val priority: Int,
    val offset: Long,
    val limit: Long,
    val queuedAtMs: Long = SystemClock.elapsedRealtime(),
    val completion: CompletableDeferred<Any> = CompletableDeferred(),
)

/**
 * Handles file downloads from Telegram using TDLib with **Zero-Copy Streaming**.
 *
 * This class DOES NOT create its own TdlClient - it receives an injected session from
 * T_TelegramServiceClient. All operations use the client from session.
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
 *
 * **Phase 2: Download Concurrency Enforcement:**
 * - Runtime-configurable download limits (maxGlobalDownloads, maxVideoDownloads, maxThumbDownloads)
 * - FIFO queues for pending downloads when limits are exceeded
 * - Automatic queue processing on download completion
 * - Structured logging for queue operations
 */
class T_TelegramFileDownloader(
    private val context: Context,
    private val session: T_TelegramSession,
    private val settingsProvider: com.chris.m3usuite.telegram.domain.TelegramStreamingSettingsProvider,
) {
    private val client
        get() = session.client
    private val downloaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        downloaderScope.launch {
            try {
                client.fileUpdates.collect { update -> handleFileUpdate(update.file) }
            } catch (e: Exception) {
                TelegramLogRepository.warn(
                    source = "T_TelegramFileDownloader",
                    message = "File update collector stopped: ${e.message}",
                )
            }
        }
    }

    companion object {
        // Phase 3: Legacy constants - DEPRECATED - No longer configurable
        @Deprecated(
            message =
                "Unused after mode-based window refactor. Custom windowing removed - use TDLib native download.",
            level = DeprecationLevel.WARNING,
        )
        private const val TELEGRAM_STREAM_WINDOW_BYTES: Long =
            50L * 1024L * 1024L // 50 MB max per video (legacy)

        @Deprecated(
            message = "Phase 3: No longer configurable - removed with streaming limit sliders",
            level = DeprecationLevel.WARNING,
        )
        private const val TELEGRAM_MIN_PREFIX_BYTES: Long =
            256L * 1024L // 256 KB minimum for playback start

        @Deprecated(
            message = "Phase 3: No longer used - withTimeout wrapper handles all timeouts",
            level = DeprecationLevel.WARNING,
        )
        private const val STREAMING_MAX_TIMEOUT_MS: Long = 10_000L // 10 seconds for initial window

        private const val POLL_INTERVAL_MS: Long = 150L // 150ms polling for progress updates

        // Legacy constants (kept for backward compatibility in fallback logic)
        private const val MIN_START_BYTES = 64 * 1024L // 64KB minimum for early success
        private const val STALL_TIMEOUT_MS = 5_000L // 5 seconds without progress = stalled

        @Deprecated(
            message = "Phase 3: No longer used - withTimeout wrapper handles all timeouts",
            level = DeprecationLevel.WARNING,
        )
        private const val PROGRESS_RESET_TIMEOUT_MS =
            60_000L // Max 60s timeout when actively progressing

        @Deprecated(
            message = "Phase 3: No longer configurable - removed with streaming limit sliders",
            level = DeprecationLevel.WARNING,
        )
        private const val SEEK_MARGIN_BYTES: Long = 1L * 1024L * 1024L // 1 MB margin for seeks
    }

    /**
     * Mode for ensureFileReady to distinguish initial playback from seeks.
     *
     * @deprecated Removed as part of windowing removal (2025-12-03).
     * Use ensureFileReadyWithMp4Validation() which doesn't require mode distinction.
     */
    @Deprecated(
        message = "Custom windowing modes removed. Use ensureFileReadyWithMp4Validation() instead",
        level = DeprecationLevel.WARNING,
    )
    enum class EnsureFileReadyMode {
        /**
         * Initial start of playback (dataSpecPosition == 0). Requires only a small fixed prefix
         * (256 KB or less), independent of any future positions.
         */
        INITIAL_START,

        /**
         * Seek operation (dataSpecPosition > 0). Requires only startPosition + margin (e.g., +1 MB)
         * instead of startPosition + 256 KB up to totalSize.
         */
        SEEK,
    }

    // Cache for file info to avoid repeated TDLib calls - thread-safe
    private val fileInfoCache = ConcurrentHashMap<String, File>()

    // Active downloads tracker - thread-safe
    private val activeDownloads = ConcurrentHashMap.newKeySet<Int>()
    private val downloadKindByFileId = ConcurrentHashMap<Int, DownloadKind>()

    // ════════════════════════════════════════════════════════════════════════════════════════
    // Phase 2: Download Concurrency Enforcement
    // ════════════════════════════════════════════════════════════════════════════════════════

    // Counters for active downloads by type
    @Volatile private var activeGlobalDownloads = 0

    @Volatile private var activeVideoDownloads = 0

    @Volatile private var activeThumbDownloads = 0

    // FIFO queues for pending download jobs
    private val globalQueue = java.util.concurrent.ConcurrentLinkedQueue<PendingDownloadJob>()
    private val videoQueue = java.util.concurrent.ConcurrentLinkedQueue<PendingDownloadJob>()
    private val thumbQueue = java.util.concurrent.ConcurrentLinkedQueue<PendingDownloadJob>()

    // Lock for queue operations to ensure atomicity
    private val queueLock = Any()

    // ════════════════════════════════════════════════════════════════════════════════════════
    // Phase 2: Download Concurrency Helper Methods
    // ════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Phase 2: Classify a download as VIDEO or THUMB based on context.
     *
     * Heuristic: If priority >= 32 (high priority), it's a VIDEO download. Lower priorities are
     * assumed to be THUMB downloads. This can be refined with additional context in the future.
     */
    private fun classifyDownload(priority: Int): DownloadKind =
        if (priority >= 32) {
            DownloadKind.VIDEO
        } else {
            DownloadKind.THUMB
        }

    /**
     * Phase 2: Check if download can start based on runtime concurrency limits. Returns true if
     * limits allow, false if job should be queued.
     */
    private fun canStartDownload(kind: DownloadKind): Boolean {
        val settings = settingsProvider.currentSettings

        // Check global limit
        if (activeGlobalDownloads >= settings.maxGlobalDownloads) {
            return false
        }

        // Check type-specific limits
        return when (kind) {
            is DownloadKind.VIDEO -> activeVideoDownloads < settings.maxVideoDownloads
            is DownloadKind.THUMB -> activeThumbDownloads < settings.maxThumbDownloads
        }
    }

    /** Phase 2: Enqueue a download job into appropriate queues. */
    private fun enqueueDownload(job: PendingDownloadJob) {
        synchronized(queueLock) {
            globalQueue.offer(job)
            when (job.kind) {
                is DownloadKind.VIDEO -> videoQueue.offer(job)
                is DownloadKind.THUMB -> thumbQueue.offer(job)
            }

            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Download job enqueued",
                details =
                    mapOf(
                        "fileId" to job.fileId.toString(),
                        "kind" to job.kind::class.simpleName.orEmpty(),
                        "priority" to job.priority.toString(),
                        "globalQueueSize" to globalQueue.size.toString(),
                        "videoQueueSize" to videoQueue.size.toString(),
                        "thumbQueueSize" to thumbQueue.size.toString(),
                    ),
            )
        }
    }

    /** Phase 2: Increment download counters when starting a download. */
    private fun incrementDownloadCounters(kind: DownloadKind) {
        synchronized(queueLock) {
            activeGlobalDownloads++
            when (kind) {
                is DownloadKind.VIDEO -> activeVideoDownloads++
                is DownloadKind.THUMB -> activeThumbDownloads++
            }

            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Download started",
                details =
                    mapOf(
                        "activeGlobal" to activeGlobalDownloads.toString(),
                        "activeVideo" to activeVideoDownloads.toString(),
                        "activeThumb" to activeThumbDownloads.toString(),
                        "kind" to kind::class.simpleName.orEmpty(),
                    ),
            )
        }
    }

    /** Phase 2: Decrement download counters when download completes. */
    private fun decrementDownloadCounters(kind: DownloadKind) {
        synchronized(queueLock) {
            activeGlobalDownloads = (activeGlobalDownloads - 1).coerceAtLeast(0)
            when (kind) {
                is DownloadKind.VIDEO ->
                    activeVideoDownloads = (activeVideoDownloads - 1).coerceAtLeast(0)
                is DownloadKind.THUMB ->
                    activeThumbDownloads = (activeThumbDownloads - 1).coerceAtLeast(0)
            }

            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Download completed",
                details =
                    mapOf(
                        "activeGlobal" to activeGlobalDownloads.toString(),
                        "activeVideo" to activeVideoDownloads.toString(),
                        "activeThumb" to activeThumbDownloads.toString(),
                        "kind" to kind::class.simpleName.orEmpty(),
                    ),
            )
        }
    }

    private fun markDownloadStartLocked(job: PendingDownloadJob) {
        incrementDownloadCounters(job.kind)
        activeDownloads.add(job.fileId)
    }

    private fun releaseDownloadSlot(
        fileId: Int,
        explicitKind: DownloadKind? = null,
        reason: String,
    ) {
        val kind = explicitKind ?: downloadKindByFileId[fileId] ?: return
        downloadKindByFileId.remove(fileId)

        var released = false
        synchronized(queueLock) {
            if (activeDownloads.remove(fileId)) {
                decrementDownloadCounters(kind)
                released = true
            }
        }

        if (released) {
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "Download slot released",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "kind" to kind::class.simpleName.orEmpty(),
                        "reason" to reason,
                    ),
            )
            scheduleQueueProcessing()
        }
    }

    private fun scheduleQueueProcessing() {
        downloaderScope.launch { processQueuedDownloads() }
    }

    private fun handleFileUpdate(file: File) {
        val isActive = file.local?.isDownloadingActive ?: false
        if (!isActive) {
            val kind = downloadKindByFileId[file.id] ?: return
            val reason = if (file.local?.isDownloadingCompleted == true) "completed" else "inactive"
            releaseDownloadSlot(file.id, kind, reason)
        }
    }

    /** Phase 2: Process queued downloads after a download completes. */
    private suspend fun processQueuedDownloads() {
        while (true) {
            val nextJob =
                synchronized(queueLock) {
                    val candidate = findNextStartableJob() ?: return
                    globalQueue.remove(candidate)
                    when (candidate.kind) {
                        is DownloadKind.VIDEO -> videoQueue.remove(candidate)
                        is DownloadKind.THUMB -> thumbQueue.remove(candidate)
                    }
                    markDownloadStartLocked(candidate)
                    candidate
                }

            runDownloadJob(nextJob)
        }
    }

    /**
     * Phase 2: Find the next job that can start based on current limits. Returns null if no job can
     * start yet.
     */
    private fun findNextStartableJob(): PendingDownloadJob? {
        // Iterate through global queue (FIFO order)
        for (job in globalQueue) {
            if (canStartDownload(job.kind)) {
                return job
            }
        }
        return null
    }

    /**
     * Internal wrapper to avoid KAPT issues with TdlResult generics in method signatures.
     * Returns Any to prevent KAPT from generating generic TdlResult stubs.
     */
    private suspend fun startOrQueueDownload(
        fileId: Int,
        priority: Int,
        offset: Long,
        limit: Long,
    ): Any {
        val job =
            PendingDownloadJob(
                fileId = fileId,
                kind = classifyDownload(priority),
                priority = priority,
                offset = offset,
                limit = limit,
            )

        val startImmediately =
            synchronized(queueLock) {
                if (canStartDownload(job.kind)) {
                    markDownloadStartLocked(job)
                    true
                } else {
                    enqueueDownload(job)
                    false
                }
            }

        return if (startImmediately) {
            runDownloadJob(job)
        } else {
            job.completion.await()
        }
    }

    /**
     * Internal wrapper to avoid KAPT issues with TdlResult generics in method signatures.
     * Returns Any to prevent KAPT from generating generic TdlResult stubs.
     */
    private suspend fun runDownloadJob(job: PendingDownloadJob): Any {
        val queuedDuration = SystemClock.elapsedRealtime() - job.queuedAtMs

        TelegramLogRepository.info(
            source = "T_TelegramFileDownloader",
            message = "Starting download",
            details =
                mapOf(
                    "fileId" to job.fileId.toString(),
                    "kind" to job.kind::class.simpleName.orEmpty(),
                    "priority" to job.priority.toString(),
                    "queuedMs" to queuedDuration.toString(),
                ),
        )

        val result =
            try {
                client.downloadFile(
                    fileId = job.fileId,
                    priority = job.priority,
                    offset = job.offset,
                    limit = job.limit,
                    synchronous = false,
                )
            } catch (e: Exception) {
                releaseDownloadSlot(job.fileId, job.kind, "exception:${e.message}")
                if (!job.completion.isCompleted) {
                    job.completion.completeExceptionally(e)
                }
                throw e
            }

        when (result) {
            is TdlResult.Success -> {
                fileInfoCache[job.fileId.toString()] = result.result
                val local = result.result.local
                val isActive = local?.isDownloadingActive ?: false
                if (isActive) {
                    downloadKindByFileId[job.fileId] = job.kind
                } else {
                    val reason =
                        if (local?.isDownloadingCompleted ==
                            true
                        ) {
                            "complete_immediate"
                        } else {
                            "inactive_immediate"
                        }
                    releaseDownloadSlot(job.fileId, job.kind, reason)
                }

                TelegramLogRepository.logFileDownload(
                    fileId = job.fileId,
                    progress = 0,
                    total = (result.result.expectedSize ?: 0).toInt(),
                    status = "scheduled",
                )
            }
            is TdlResult.Failure -> {
                releaseDownloadSlot(job.fileId, job.kind, "start_failed")
                TelegramLogRepository.error(
                    source = "T_TelegramFileDownloader",
                    message = "Download failed to start",
                    details =
                        mapOf(
                            "fileId" to job.fileId.toString(),
                            "error" to result.message,
                        ),
                )
            }
        }

        if (!job.completion.isCompleted) {
            job.completion.complete(result)
        }

        return result
    }

    /**
     * Ensure a download window is active for the specified file and position.
     *
     * @deprecated Custom windowing removed (2025-12-03). Use ensureFileReadyWithMp4Validation() instead.
     * This method is kept for backward compatibility but no longer implements windowing logic.
     * It simply ensures the file is being downloaded from offset=0.
     *
     * @param fileIdInt TDLib file ID (integer)
     * @param windowStart Starting byte offset for the window (ignored)
     * @param windowSize Size of the window in bytes (ignored)
     * @return true if download started successfully
     */
    @Deprecated(
        message = "Custom windowing removed. Use ensureFileReadyWithMp4Validation() instead",
        replaceWith = ReplaceWith("startDownload(fileIdInt, priority = 32)"),
        level = DeprecationLevel.WARNING,
    )
    suspend fun ensureWindow(
        fileIdInt: Int,
        windowStart: Long,
        windowSize: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            TelegramLogRepository.warn(
                source = "T_TelegramFileDownloader",
                message = "ensureWindow is deprecated - use ensureFileReadyWithMp4Validation instead",
                details =
                    mapOf(
                        "fileId" to fileIdInt.toString(),
                        "windowStart" to windowStart.toString(),
                        "windowSize" to windowSize.toString(),
                    ),
            )
            // Fallback: Start standard TDLib download (offset=0, limit=0)
            startDownload(fileIdInt, priority = 32)
        }

    /**
     * Ensure TDLib has downloaded a file for streaming.
     *
     * @deprecated Use ensureFileReadyWithMp4Validation() instead (2025-12-03).
     * This legacy method is kept for backward compatibility but now delegates to standard TDLib download.
     *
     * **Standard TDLib Behavior:**
     * - Calls downloadFile(offset=0, limit=0, priority=32) for progressive download
     * - Polls until minimum prefix is available for playback start
     * - No custom windowing or mode-based logic
     *
     * @param fileId TDLib file ID (integer)
     * @param startPosition Starting byte offset (ignored, always downloads from 0)
     * @param minBytes Minimum bytes needed for playback start
     * @param mode Mode hint (ignored, kept for compatibility)
     * @param fileSizeBytes Optional known file size from TDLib DTOs
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return Local file path from TDLib cache
     * @throws Exception if download fails
     */
    @Deprecated(
        message = "Use ensureFileReadyWithMp4Validation() for proper MP4 header validation",
        replaceWith = ReplaceWith("ensureFileReadyWithMp4Validation(fileId, null, timeoutMs)"),
        level = DeprecationLevel.WARNING,
    )
    suspend fun ensureFileReady(
        fileId: Int,
        startPosition: Long,
        minBytes: Long,
        mode: EnsureFileReadyMode = EnsureFileReadyMode.INITIAL_START,
        fileSizeBytes: Long? = null,
        timeoutMs: Long = 30_000L,
    ): String {
        TelegramLogRepository.warn(
            source = "T_TelegramFileDownloader",
            message = "ensureFileReady is deprecated - use ensureFileReadyWithMp4Validation instead",
            details =
                mapOf(
                    "fileId" to fileId.toString(),
                    "mode" to mode.name,
                ),
        )

        // Delegate to the new method
        return ensureFileReadyWithMp4Validation(
            fileId = fileId,
            remoteId = null,
            timeoutMs = timeoutMs,
        )
    }

    /**
     * Get file size from TDLib. Returns -1 if size is unknown.
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
                if (file.length() == position && fileInfo.local?.isDownloadingCompleted == true
                ) {
                    return@withContext true
                }
                return@withContext false
            } catch (e: Exception) {
                // Any error means data is not available
                return@withContext false
            }
        }

    /**
     * Start downloading a file and return immediately. Use observeDownloadProgress() to track
     * progress.
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
                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "Starting download",
                    details =
                        mapOf(
                            "fileId" to fileId.toString(),
                            "priority" to priority.toString(),
                        ),
                )

                @Suppress("UNCHECKED_CAST")
                when (
                    val result =
                        startOrQueueDownload(
                            fileId = fileId,
                            priority = priority,
                            offset = 0L,
                            limit = 0L,
                        ) as TdlResult<File>
                ) {
                    is TdlResult.Success -> {
                        TelegramLogRepository.logFileDownload(
                            fileId = fileId,
                            progress = 0,
                            total = (result.result.expectedSize ?: 0).toInt(),
                            status = "started",
                        )
                        true
                    }
                    is TdlResult.Failure -> {
                        TelegramLogRepository.error(
                            source = "T_TelegramFileDownloader",
                            message = "Download start failed",
                            details =
                                mapOf(
                                    "fileId" to fileId.toString(),
                                    "error" to result.message,
                                ),
                        )
                        false
                    }
                }
            } catch (e: Exception) {
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
                releaseDownloadSlot(fileId = fileIdInt, reason = "cancelled_string")

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
                releaseDownloadSlot(fileId = fileId, reason = "cancelled_int")

                TelegramLogRepository.debug(
                    source = "T_TelegramFileDownloader",
                    message = "Cancelled download",
                    details = mapOf("fileId" to fileId.toString()),
                )
            }
        }

    /**
     * Explicitly cleanup file handle for a given file ID.
     *
     * @deprecated File handles are no longer cached (2025-12-03).
     * FileDataSource manages file handles directly. This is a no-op kept for compatibility.
     *
     * @param fileId TDLib file ID (integer)
     */
    @Deprecated(
        message = "File handles no longer cached. FileDataSource manages handles",
        level = DeprecationLevel.WARNING,
    )
    suspend fun cleanupFileHandle(fileId: Int) =
        withContext(Dispatchers.IO) {
            TelegramLogRepository.debug(
                source = "T_TelegramFileDownloader",
                message = "cleanupFileHandle is deprecated (no-op)",
                details = mapOf("fileId" to fileId.toString()),
            )
        }

    /**
     * Observe download progress for a specific file. Returns a Flow that emits progress updates.
     *
     * Based on tdlib-coroutines documentation for file updates.
     *
     * @param fileId TDLib file ID (integer)
     * @return Flow of download progress
     */
    fun observeDownloadProgress(fileId: Int): Flow<DownloadProgress> =
        client.fileUpdates.filter { update -> update.file.id == fileId }.map { update ->
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
     * Get a file from TDLib or throw an exception on failure. This is a shared helper to avoid
     * duplicating error handling.
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
                    throw Exception(
                        "Failed to get file $fileId: ${result.code} - ${result.message}",
                    )
            }
        }

    /**
     * Get fresh file state from TDLib without using cache. Used by thumbnail polling
     * to get real-time download progress.
     *
     * @param fileId TDLib file ID (integer)
     * @return Fresh File object from TDLib
     */
    suspend fun getFreshFileState(fileId: Int): File = getFileOrThrow(fileId)

    /**
     * Get file information from TDLib. Uses cache to avoid repeated API calls.
     *
     * @param fileId TDLib file ID (string or integer)
     * @return File object
     */
    private suspend fun getFileInfo(fileId: String): File =
        withContext(Dispatchers.IO) {
            // Check cache first
            fileInfoCache[fileId]?.let {
                return@withContext it
            }

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
            fileInfoCache[cacheKey]?.let {
                return@withContext it
            }

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
     * Phase D+: This is the key method for remoteId-first playback wiring. Uses TDLib's
     * getRemoteFile API to resolve a stable remoteId to a volatile fileId.
     *
     * The remoteId is stable across sessions and devices, while fileId is only valid for the
     * current TDLib instance and may become stale.
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
     * Clear old cached files to prevent bloat. Should be called periodically or when cache size
     * limit is reached.
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
                        val targetSize =
                            ((currentSizeMb - maxCacheSizeMb) * 1024 * 1024).toLong()
                        val optimizeResult =
                            client.optimizeStorage(
                                size = targetSize,
                                ttl = 7 * 24 * 60 * 60, // Files older than 7 days
                                count = Int.MAX_VALUE,
                                immunityDelay =
                                    24 * 60 * 60, // Keep files from last 24h
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
     * Cancel download for a file when playback ends (Phase D+ cache management). This helps keep
     * cache size under control by stopping downloads for inactive files.
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

    /** Clear the file info cache. Useful when you want to force fresh data from TDLib. */
    fun clearFileCache() {
        fileInfoCache.clear()
        println("[T_TelegramFileDownloader] File info cache cleared")
    }

    /**
     * Ensure file ready with MP4 header validation (StreamingConfigRefactor integration).
     *
     * **TDLib Best Practices Strategy:**
     * 1. Start progressive download: downloadFile(fileId, offset=0, limit=0, priority=32)
     * 2. Poll file.local.downloaded_prefix_size until >= MIN_PREFIX_FOR_VALIDATION_BYTES
     * 3. Use Mp4HeaderParser to validate complete moov atom
     * 4. Return local path only when moov is complete (no hard thresholds)
     *
     * **Stale FileId Handling (Phase D+):**
     * - If downloadFile fails with "File not found" error and remoteId is provided
     * - Automatically resolves remoteId to fresh fileId via resolveRemoteFileId()
     * - Retries download with new fileId
     * - Logs "Stale fileId, will fall back to remoteId" matching thumbnail pattern
     *
     * **This is the recommended method for video streaming starting 2025-12-03.**
     * It replaces fixed byte thresholds with intelligent MP4 structure validation.
     *
     * @param fileId TDLib file ID
     * @param remoteId Optional stable remote file ID for stale fileId fallback
     * @param timeoutMs Maximum wait time (default: 30 seconds from StreamingConfigRefactor)
     * @return Local file path from TDLib cache
     * @throws Exception if download fails, timeout occurs, or file is not streamable
     */
    suspend fun ensureFileReadyWithMp4Validation(
        fileId: Int,
        remoteId: String? = null,
        timeoutMs: Long = StreamingConfigRefactor.ENSURE_READY_TIMEOUT_MS,
    ): String =
        withContext(Dispatchers.IO) {
            val startTimeMs = System.currentTimeMillis()

            TelegramLogRepository.info(
                source = "T_TelegramFileDownloader",
                message = "ensureFileReadyWithMp4Validation: Starting download with MP4 header validation",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "remoteId" to (remoteId ?: "none"),
                        "timeoutMs" to timeoutMs.toString(),
                        "minPrefixForValidation" to StreamingConfigRefactor.MIN_PREFIX_FOR_VALIDATION_BYTES.toString(),
                        "maxPrefixScan" to StreamingConfigRefactor.MAX_PREFIX_SCAN_BYTES.toString(),
                    ),
            )

            // Step 1: Try to start progressive download from offset=0, limit=0 (full file)
            // If this fails with 404 and we have remoteId, resolve and retry
            var actualFileId = fileId
            var downloadResult =
                client.downloadFile(
                    fileId = actualFileId,
                    priority = StreamingConfigRefactor.DOWNLOAD_PRIORITY_STREAMING,
                    offset = StreamingConfigRefactor.DOWNLOAD_OFFSET_START,
                    limit = StreamingConfigRefactor.DOWNLOAD_LIMIT_FULL,
                    synchronous = false, // Asynchronous for progressive streaming
                )

            when (downloadResult) {
                is dev.g000sha256.tdl.TdlResult.Failure -> {
                    // Check if this is a 404/"File not found" error and we have remoteId for fallback
                    val errorMessage = downloadResult.message
                    val errorCode = downloadResult.code

                    // Check for 404/not found errors using both code and message
                    // TDLib error code 400 (FILE_NOT_FOUND) or string matching
                    val is404Error =
                        errorCode == 400 ||
                            // TDLib FILE_NOT_FOUND error code
                            errorMessage.contains("404", ignoreCase = true) ||
                            errorMessage.contains("not found", ignoreCase = true) ||
                            errorMessage.contains("file not found", ignoreCase = true)

                    if (is404Error && !remoteId.isNullOrBlank()) {
                        // Log stale fileId warning (matching thumbnail pattern)
                        TelegramLogRepository.warn(
                            source = "T_TelegramFileDownloader",
                            message = "Stale fileId, will fall back to remoteId",
                            details =
                                mapOf(
                                    "staleFileId" to actualFileId.toString(),
                                    "remoteId" to remoteId,
                                    "errorCode" to errorCode.toString(),
                                    "errorMessage" to errorMessage,
                                ),
                        )

                        // Resolve remoteId to fresh fileId
                        TelegramLogRepository.debug(
                            source = "T_TelegramFileDownloader",
                            message = "Resolving remoteId to fileId",
                            details = mapOf("remoteId" to remoteId),
                        )

                        val newFileId = resolveRemoteFileId(remoteId)
                        if (newFileId == null || newFileId <= 0) {
                            TelegramLogRepository.error(
                                source = "T_TelegramFileDownloader",
                                message = "ensureFileReadyWithMp4Validation: remoteId resolution failed after 404",
                                details =
                                    mapOf(
                                        "staleFileId" to actualFileId.toString(),
                                        "remoteId" to remoteId,
                                    ),
                            )
                            throw Exception("Failed to resolve remoteId=$remoteId after stale fileId=$actualFileId")
                        }

                        TelegramLogRepository.debug(
                            source = "T_TelegramFileDownloader",
                            message = "Resolved remoteId to fileId",
                            details =
                                mapOf(
                                    "remoteId" to remoteId,
                                    "newFileId" to newFileId.toString(),
                                ),
                        )

                        // Update actualFileId and retry download
                        actualFileId = newFileId
                        downloadResult =
                            client.downloadFile(
                                fileId = actualFileId,
                                priority = StreamingConfigRefactor.DOWNLOAD_PRIORITY_STREAMING,
                                offset = StreamingConfigRefactor.DOWNLOAD_OFFSET_START,
                                limit = StreamingConfigRefactor.DOWNLOAD_LIMIT_FULL,
                                synchronous = false,
                            )

                        // Check retry result
                        when (downloadResult) {
                            is dev.g000sha256.tdl.TdlResult.Failure -> {
                                TelegramLogRepository.error(
                                    source = "T_TelegramFileDownloader",
                                    message = "ensureFileReadyWithMp4Validation: Download failed even after remoteId resolution",
                                    details =
                                        mapOf(
                                            "newFileId" to actualFileId.toString(),
                                            "error" to downloadResult.message,
                                        ),
                                )
                                throw Exception("Failed to start download for resolved fileId=$actualFileId: ${downloadResult.message}")
                            }
                            is dev.g000sha256.tdl.TdlResult.Success -> {
                                TelegramLogRepository.info(
                                    source = "T_TelegramFileDownloader",
                                    message = "ensureFileReadyWithMp4Validation: Download initiated after remoteId resolution",
                                    details =
                                        mapOf(
                                            "staleFileId" to fileId.toString(),
                                            "newFileId" to actualFileId.toString(),
                                        ),
                                )
                            }
                        }
                    } else {
                        // Not a 404 or no remoteId available - fail immediately
                        TelegramLogRepository.error(
                            source = "T_TelegramFileDownloader",
                            message = "ensureFileReadyWithMp4Validation: Download initiation failed",
                            details =
                                mapOf(
                                    "fileId" to actualFileId.toString(),
                                    "error" to errorMessage,
                                    "is404" to is404Error.toString(),
                                    "hasRemoteId" to (!remoteId.isNullOrBlank()).toString(),
                                ),
                        )
                        throw Exception("Failed to start download for fileId=$actualFileId: $errorMessage")
                    }
                }
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    // Download started successfully
                    TelegramLogRepository.debug(
                        source = "T_TelegramFileDownloader",
                        message = "ensureFileReadyWithMp4Validation: Download initiated",
                        details = mapOf("fileId" to actualFileId.toString()),
                    )
                }
            }

            // Step 2: Poll until minimum prefix is available for validation
            var lastLoggedPrefixSize = 0L
            var moovCheckStarted = false
            var moovIncompleteWarningLogged = false

            while (true) {
                val elapsedMs = System.currentTimeMillis() - startTimeMs
                if (elapsedMs > timeoutMs) {
                    TelegramLogRepository.error(
                        source = "T_TelegramFileDownloader",
                        message = "ensureFileReadyWithMp4Validation: Timeout waiting for file download",
                        details =
                            mapOf(
                                "fileId" to actualFileId.toString(),
                                "elapsedMs" to elapsedMs.toString(),
                                "timeoutMs" to timeoutMs.toString(),
                            ),
                    )
                    throw Exception("Timeout waiting for file download: fileId=$actualFileId, elapsed=${elapsedMs}ms")
                }

                // Get fresh file state from TDLib
                val file = getFreshFileState(actualFileId)
                val localPath = file.local?.path
                val downloadedPrefixSize = file.local?.downloadedPrefixSize?.toLong() ?: 0L
                val isDownloadingCompleted = file.local?.isDownloadingCompleted ?: false

                // Verbose logging (only log significant progress changes)
                if (StreamingConfigRefactor.ENABLE_VERBOSE_LOGGING ||
                    downloadedPrefixSize - lastLoggedPrefixSize >= 256 * 1024
                ) {
                    TelegramLogRepository.debug(
                        source = "T_TelegramFileDownloader",
                        message = "ensureFileReadyWithMp4Validation: Download progress",
                        details =
                            mapOf(
                                "fileId" to actualFileId.toString(),
                                "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                                "isDownloadingCompleted" to isDownloadingCompleted.toString(),
                                "elapsedMs" to elapsedMs.toString(),
                            ),
                    )
                    lastLoggedPrefixSize = downloadedPrefixSize
                }

                // Check if we have minimum prefix for header validation
                if (downloadedPrefixSize < StreamingConfigRefactor.MIN_PREFIX_FOR_VALIDATION_BYTES) {
                    delay(StreamingConfigRefactor.PREFIX_POLL_INTERVAL_MS)
                    continue
                }

                // Check if local path is available
                if (localPath.isNullOrBlank()) {
                    TelegramLogRepository.warn(
                        source = "T_TelegramFileDownloader",
                        message = "ensureFileReadyWithMp4Validation: Local path not available yet",
                        details =
                            mapOf(
                                "fileId" to actualFileId.toString(),
                                "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                            ),
                    )
                    delay(StreamingConfigRefactor.PREFIX_POLL_INTERVAL_MS)
                    continue
                }

                // Check if file exists
                val localFile = java.io.File(localPath)
                if (!localFile.exists()) {
                    TelegramLogRepository.warn(
                        source = "T_TelegramFileDownloader",
                        message = "ensureFileReadyWithMp4Validation: Local file does not exist yet",
                        details =
                            mapOf(
                                "fileId" to actualFileId.toString(),
                                "localPath" to localPath,
                            ),
                    )
                    delay(StreamingConfigRefactor.PREFIX_POLL_INTERVAL_MS)
                    continue
                }

                // Step 3: Validate MP4 header
                if (!moovCheckStarted) {
                    moovCheckStarted = true
                    TelegramLogRepository.info(
                        source = "T_TelegramFileDownloader",
                        message = "ensureFileReadyWithMp4Validation: Starting MP4 header validation",
                        details =
                            mapOf(
                                "fileId" to actualFileId.toString(),
                                "localPath" to localPath,
                                "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                            ),
                    )
                }

                // Validate moov atom
                val validationResult = Mp4HeaderParser.validateMoovAtom(localFile, downloadedPrefixSize)

                when (validationResult) {
                    is Mp4HeaderParser.ValidationResult.MoovComplete -> {
                        // SUCCESS: moov atom complete, ready for playback
                        TelegramLogRepository.info(
                            source = "T_TelegramFileDownloader",
                            message = "ensureFileReadyWithMp4Validation: SUCCESS - MP4 header validation complete",
                            details =
                                mapOf(
                                    "fileId" to actualFileId.toString(),
                                    "localPath" to localPath,
                                    "moovOffset" to validationResult.moovOffset.toString(),
                                    "moovSize" to validationResult.moovSize.toString(),
                                    "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                                    "elapsedMs" to elapsedMs.toString(),
                                ),
                        )
                        return@withContext localPath
                    }

                    is Mp4HeaderParser.ValidationResult.MoovIncomplete -> {
                        // moov started but not complete yet
                        if (!moovIncompleteWarningLogged) {
                            TelegramLogRepository.info(
                                source = "T_TelegramFileDownloader",
                                message = "ensureFileReadyWithMp4Validation: MP4 moov atom incomplete, waiting",
                                details =
                                    mapOf(
                                        "fileId" to actualFileId.toString(),
                                        "moovOffset" to validationResult.moovOffset.toString(),
                                        "moovSize" to validationResult.moovSize.toString(),
                                        "availableBytes" to validationResult.availableBytes.toString(),
                                        "remainingBytes" to
                                            (validationResult.moovOffset + validationResult.moovSize - validationResult.availableBytes).toString(),
                                    ),
                            )
                            moovIncompleteWarningLogged = true
                        }
                        delay(StreamingConfigRefactor.PREFIX_POLL_INTERVAL_MS)
                        continue
                    }

                    is Mp4HeaderParser.ValidationResult.MoovNotFound -> {
                        // moov not found yet - check limits
                        if (downloadedPrefixSize >= StreamingConfigRefactor.MAX_PREFIX_SCAN_BYTES) {
                            TelegramLogRepository.error(
                                source = "T_TelegramFileDownloader",
                                message = "ensureFileReadyWithMp4Validation: MP4 moov not found within scan limit",
                                details =
                                    mapOf(
                                        "fileId" to actualFileId.toString(),
                                        "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                                        "maxPrefixScanBytes" to StreamingConfigRefactor.MAX_PREFIX_SCAN_BYTES.toString(),
                                        "scannedAtoms" to validationResult.scannedAtoms.joinToString(","),
                                    ),
                            )
                            throw Exception(
                                "MP4 moov atom not found within ${StreamingConfigRefactor.MAX_PREFIX_SCAN_BYTES} bytes. " +
                                    "File not optimized for streaming (moov likely at end). " +
                                    "Scanned atoms: ${validationResult.scannedAtoms.joinToString(", ")}",
                            )
                        }

                        // Download complete but moov not found
                        if (isDownloadingCompleted) {
                            TelegramLogRepository.error(
                                source = "T_TelegramFileDownloader",
                                message = "ensureFileReadyWithMp4Validation: Download complete but moov not found",
                                details =
                                    mapOf(
                                        "fileId" to actualFileId.toString(),
                                        "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                                        "scannedAtoms" to validationResult.scannedAtoms.joinToString(","),
                                    ),
                            )
                            throw Exception(
                                "MP4 moov atom not found in complete file. " +
                                    "File may be corrupted or not valid MP4. " +
                                    "Scanned atoms: ${validationResult.scannedAtoms.joinToString(", ")}",
                            )
                        }

                        // Keep waiting for more data
                        if (StreamingConfigRefactor.ENABLE_VERBOSE_LOGGING) {
                            TelegramLogRepository.debug(
                                source = "T_TelegramFileDownloader",
                                message = "ensureFileReadyWithMp4Validation: moov not found yet, continuing",
                                details =
                                    mapOf(
                                        "fileId" to actualFileId.toString(),
                                        "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                                        "scannedAtoms" to validationResult.scannedAtoms.joinToString(","),
                                    ),
                            )
                        }
                        delay(StreamingConfigRefactor.PREFIX_POLL_INTERVAL_MS)
                        continue
                    }

                    is Mp4HeaderParser.ValidationResult.Invalid -> {
                        // File format invalid
                        TelegramLogRepository.error(
                            source = "T_TelegramFileDownloader",
                            message = "ensureFileReadyWithMp4Validation: MP4 header validation failed",
                            details =
                                mapOf(
                                    "fileId" to actualFileId.toString(),
                                    "reason" to validationResult.reason,
                                ),
                        )
                        throw Exception("Invalid MP4 format: ${validationResult.reason}")
                    }
                }
            }

            // This line is unreachable but satisfies the Kotlin compiler's type checking
            throw Exception("Unreachable code")
        }
}
