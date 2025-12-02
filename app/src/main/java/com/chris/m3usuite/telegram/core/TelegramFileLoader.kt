package com.chris.m3usuite.telegram.core

import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles downloading and accessing Telegram files via TDLib.
 * Supports lazy thumbnail loading and zero-copy file access (Requirement 3, 6).
 *
 * Key Features:
 * - ensureThumbDownloaded(): Coroutine-based thumbnail downloading
 * - Returns local file paths from TDLib cache
 * - No file copying - uses TDLib's local directory
 * - Automatic retry with exponential backoff
 * - Progress logging for debugging
 *
 * Phase T2 Additions:
 * - prefetchImages(): Background prefetch for posterRef and backdropRef
 * - ensureImageDownloaded(): Download via TelegramImageRef (remoteId-first)
 *
 * This is a thin wrapper around T_TelegramFileDownloader for convenience.
 */
class TelegramFileLoader(
    private val serviceClient: T_TelegramServiceClient,
) {
    private val downloader: T_TelegramFileDownloader
        get() = serviceClient.downloader()

    // Background scope for prefetching (fire and forget)
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track remoteIds that returned 404 to avoid repeated logging
    // Use a bounded set with LRU eviction to prevent unbounded growth
    private val logged404RemoteIds =
        object : LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                if (size >= MAX_404_LOG_CACHE) {
                    // Remove oldest entry (first in insertion order)
                    iterator().apply {
                        if (hasNext()) {
                            next()
                            remove()
                        }
                    }
                }
                return super.add(element)
            }
        }

    companion object {
        private const val TAG = "TelegramFileLoader"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val DEFAULT_PRIORITY = 16 // Lower priority for thumbnails
        private const val MAX_404_LOG_CACHE = 1000 // Maximum remoteIds to track for 404 logging
    }

    /**
     * Ensure thumbnail is downloaded and return local path (Requirement 3).
     *
     * **Phase D+ RemoteId-first approach:**
     * - Accepts TelegramImageRef with stable remoteId instead of volatile fileId
     * - Tries cached fileId first (fast path)
     * - Falls back to remoteId resolution on 404 (stale fileId)
     * - Checks service state before attempting download
     *
     * This is designed to be called from LaunchedEffect in tiles.
     *
     * @param ref TelegramImageRef with remoteId/uniqueId/fileId
     * @param timeoutMs Maximum time to wait for download
     * @return Local file path or null
     */
    suspend fun ensureThumbDownloaded(
        ref: TelegramImageRef,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? {
        // 0) Check Telegram service state – fail fast if not started
        if (!serviceClient.isStarted || !serviceClient.isAuthReady()) {
            TelegramLogRepository.debug(
                source = TAG,
                message = "Skipping thumb download – Telegram not ready",
                details =
                    mapOf(
                        "remoteId" to ref.remoteId,
                        "isStarted" to serviceClient.isStarted.toString(),
                        "isAuthReady" to serviceClient.isAuthReady().toString(),
                    ),
            )
            return null
        }

        // 1) If we have a fileId, try it once
        val cachedFileId = ref.fileId?.takeIf { it > 0 }
        if (cachedFileId != null) {
            try {
                val result = tryDownloadThumbByFileId(cachedFileId, timeoutMs)
                when (result) {
                    is ThumbResult.Success -> return result.localPath
                    is ThumbResult.NotFound404 -> {
                        // Mark this fileId as stale for this session
                        TelegramLogRepository.warn(
                            source = TAG,
                            message = "Stale fileId, will fall back to remoteId",
                            details =
                                mapOf(
                                    "staleFileId" to cachedFileId.toString(),
                                    "remoteId" to ref.remoteId,
                                ),
                        )
                        // Continue to remoteId resolution
                    }
                    is ThumbResult.OtherError -> {
                        // For other errors (network, timeout), log and return null
                        TelegramLogRepository.debug(
                            source = TAG,
                            message = "ensureThumbDownloaded failed with non-404 error",
                            details =
                                mapOf(
                                    "fileId" to cachedFileId.toString(),
                                    "remoteId" to ref.remoteId,
                                    "error" to result.message,
                                ),
                        )
                        return null
                    }
                }
            } catch (e: Exception) {
                // Unexpected exception, log and continue to remoteId
                TelegramLogRepository.error(
                    source = TAG,
                    message = "Unexpected exception during fileId download, will try remoteId resolution",
                    exception = e,
                    details =
                        mapOf(
                            "fileId" to cachedFileId.toString(),
                            "remoteId" to ref.remoteId,
                        ),
                )
            }
        }

        // 2) Resolve a fresh fileId via remoteId in the current TDLib DB
        val remoteId = ref.remoteId
        if (remoteId.isBlank()) {
            TelegramLogRepository.warn(
                source = TAG,
                message = "No remoteId available for fallback",
            )
            return null
        }

        val newFileId = downloader.resolveRemoteFileId(remoteId)
        if (newFileId == null || newFileId <= 0) {
            // Log once per remoteId (avoid spam)
            if (logged404RemoteIds.add(remoteId)) {
                TelegramLogRepository.warn(
                    source = TAG,
                    message = "remoteId resolution failed (404)",
                    details = mapOf("remoteId" to remoteId),
                )
            }
            return null
        }

        TelegramLogRepository.debug(
            source = TAG,
            message = "remoteId resolved to new fileId",
            details =
                mapOf(
                    "remoteId" to remoteId,
                    "newFileId" to newFileId.toString(),
                ),
        )

        // 3) Download thumb via new fileId
        val result = tryDownloadThumbByFileId(newFileId, timeoutMs)
        return when (result) {
            is ThumbResult.Success -> result.localPath
            else -> null
        }
    }

    /**
     * Internal sealed class for thumb download results.
     */
    private sealed class ThumbResult {
        data class Success(
            val localPath: String,
        ) : ThumbResult()

        object NotFound404 : ThumbResult()

        data class OtherError(
            val message: String,
        ) : ThumbResult()
    }

    /**
     * Try to download thumbnail by fileId and return result.
     * Encapsulates download logic and error handling.
     *
     * **Phase D+ Fix:** Downloads entire file for thumbnails/backdrops by passing
     * minBytes = totalSizeBytes to ensureFileReady. This ensures full download
     * instead of partial 256KB prefix.
     */
    private suspend fun tryDownloadThumbByFileId(
        fileId: Int,
        timeoutMs: Long,
    ): ThumbResult =
        try {
            TelegramLogRepository.debug(
                source = TAG,
                message = "tryDownloadThumbByFileId start",
                details = mapOf("fileId" to fileId.toString()),
            )

            // Get file info to determine total size
            val fileInfo = downloader.getFileInfo(fileId)
            val totalSizeBytes = fileInfo?.expectedSize?.toLong() ?: 0L

            // Log if totalSize is unknown
            if (totalSizeBytes <= 0L) {
                TelegramLogRepository.warn(
                    source = TAG,
                    message = "tryDownloadThumbByFileId: totalSize unknown, will attempt download anyway",
                    details =
                        mapOf(
                            "fileId" to fileId.toString(),
                            "expectedSize" to (fileInfo?.expectedSize?.toString() ?: "null"),
                        ),
                )
            }

            // Use downloader to ensure the file is ready (entire file for thumbnails)
            // Pass minBytes = totalSizeBytes to force FULL download instead of 256KB prefix
            val path =
                downloader.ensureFileReady(
                    fileId = fileId,
                    startPosition = 0,
                    minBytes = totalSizeBytes, // Force FULL download
                    mode = T_TelegramFileDownloader.EnsureFileReadyMode.INITIAL_START,
                    fileSizeBytes = totalSizeBytes,
                    timeoutMs = timeoutMs,
                )

            // Verify download is complete
            val finalFileInfo = downloader.getFileInfo(fileId)
            val downloadedPrefixSize = finalFileInfo?.local?.downloadedPrefixSize?.toLong() ?: 0L
            val isComplete = finalFileInfo?.local?.isDownloadingCompleted ?: false

            TelegramLogRepository.debug(
                source = TAG,
                message = "tryDownloadThumbByFileId success",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "path" to path,
                        "totalSizeBytes" to totalSizeBytes.toString(),
                        "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                        "isComplete" to isComplete.toString(),
                        "fullyDownloaded" to (downloadedPrefixSize >= totalSizeBytes || isComplete).toString(),
                    ),
            )

            // Confirm full download for thumbnails/backdrops
            if (totalSizeBytes > 0L && downloadedPrefixSize < totalSizeBytes && !isComplete) {
                TelegramLogRepository.warn(
                    source = TAG,
                    message = "tryDownloadThumbByFileId: partial download for thumbnail (expected full)",
                    details =
                        mapOf(
                            "fileId" to fileId.toString(),
                            "totalSizeBytes" to totalSizeBytes.toString(),
                            "downloadedPrefixSize" to downloadedPrefixSize.toString(),
                        ),
                )
            }

            ThumbResult.Success(path)
        } catch (e: Exception) {
            val message = e.message ?: ""
            if (message.contains("404", ignoreCase = true) || message.contains("not found", ignoreCase = true)) {
                ThumbResult.NotFound404
            } else {
                TelegramLogRepository.debug(
                    source = TAG,
                    message = "tryDownloadThumbByFileId failed",
                    details =
                        mapOf(
                            "fileId" to fileId.toString(),
                            "error" to message,
                        ),
                )
                ThumbResult.OtherError(message)
            }
        }

    /**
     * Get local path for a file if already downloaded (Requirement 6).
     * Does not trigger download - returns null if not locally available.
     *
     * @param fileId TDLib file ID
     * @return Local file path or null
     */
    suspend fun getLocalPathIfAvailable(fileId: Int): String? {
        return try {
            val fileInfo = downloader.getFileInfo(fileId) ?: return null

            if (fileInfo.local?.isDownloadingCompleted == true) {
                fileInfo.local?.path?.takeUnless { it.isEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = TAG,
                message = "getLocalPathIfAvailable: Exception for fileId=$fileId",
                exception = e,
            )
            null
        }
    }

    /**
     * Legacy wrapper for backward compatibility with old code that uses fileId directly.
     * **Deprecated**: Use ensureThumbDownloaded(TelegramImageRef) instead.
     *
     * This exists only to support legacy code paths that haven't been migrated to TelegramItem yet.
     * New code should use the remoteId-first TelegramImageRef-based API.
     *
     * @param fileId TDLib file ID
     * @param timeoutMs Maximum time to wait for download
     * @return Local file path or null
     */
    @Deprecated(
        "Use ensureThumbDownloaded(TelegramImageRef) for remoteId-first approach",
        ReplaceWith("ensureThumbDownloaded(ref)"),
    )
    suspend fun ensureThumbDownloaded(
        fileId: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? {
        // Check service state
        if (!serviceClient.isStarted || !serviceClient.isAuthReady()) {
            TelegramLogRepository.debug(
                source = TAG,
                message = "Skipping thumb download (legacy API) – Telegram not ready",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                    ),
            )
            return null
        }

        // Try download with fileId directly (no remoteId fallback)
        val result = tryDownloadThumbByFileId(fileId, timeoutMs)
        return when (result) {
            is ThumbResult.Success -> result.localPath
            else -> null
        }
    }

    /**
     * Optional helper for future direct file-path playback.
     * Currently unused by the main Telegram playback path,
     * which relies on TelegramFileDataSource + downloader.
     *
     * Thin wrapper around T_TelegramFileDownloader.ensureFileReady().
     * Downloads with high priority and ensures sufficient prefix is available.
     *
     * @param fileId TDLib file ID
     * @param minPrefixBytes Minimum bytes to download from start
     * @param timeoutMs Maximum time to wait
     * @return Local file path or null
     */
    suspend fun ensureFileForPlayback(
        fileId: Int,
        minPrefixBytes: Long = 1024 * 1024, // 1 MB default
        timeoutMs: Long = 60_000L,
    ): String? =
        try {
            TelegramLogRepository.info(
                source = TAG,
                message = "ensureFileForPlayback: Starting for fileId=$fileId, minPrefix=$minPrefixBytes",
            )

            val path =
                downloader.ensureFileReady(
                    fileId = fileId,
                    startPosition = 0,
                    minBytes = minPrefixBytes,
                    timeoutMs = timeoutMs,
                )

            TelegramLogRepository.info(
                source = TAG,
                message = "ensureFileForPlayback: Ready fileId=$fileId, path=$path",
            )
            path
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = TAG,
                message = "ensureFileForPlayback: Exception for fileId=$fileId",
                exception = e,
            )
            null
        }

    // ==========================================================================
    // Phase T2: TelegramImageRef-based Image Loading
    // ==========================================================================

    // ==========================================================================
    // Phase T2: Prefetch Support
    // ==========================================================================

    /**
     * Prefetch poster and backdrop images in the background.
     *
     * Phase T2: Called when a Telegram detail screen opens to kick off
     * a lightweight prefetch of both images. This is fire-and-forget -
     * failures are logged but do not affect the caller.
     *
     * Constraint: NO TDLib calls in Composables - all image loading goes
     * through this infra-level helper.
     *
     * @param posterRef Poster image reference (may be null)
     * @param backdropRef Backdrop image reference (may be null)
     */
    fun prefetchImages(
        posterRef: TelegramImageRef?,
        backdropRef: TelegramImageRef?,
    ) {
        // Fire and forget - don't block caller
        prefetchScope.launch {
            // Check service state before prefetching
            if (!serviceClient.isStarted || !serviceClient.isAuthReady()) {
                TelegramLogRepository.debug(
                    source = TAG,
                    message = "prefetchImages: Skipping - Telegram not ready",
                    details =
                        mapOf(
                            "isStarted" to serviceClient.isStarted.toString(),
                            "isAuthReady" to serviceClient.isAuthReady().toString(),
                        ),
                )
                return@launch
            }

            TelegramLogRepository.debug(
                source = TAG,
                message = "prefetchImages: Starting prefetch",
                details =
                    mapOf(
                        "hasPoster" to (posterRef != null).toString(),
                        "hasBackdrop" to (backdropRef != null).toString(),
                    ),
            )

            // Prefetch poster
            posterRef?.let { ref ->
                try {
                    ensureThumbDownloaded(ref, timeoutMs = 10_000L)
                    TelegramLogRepository.debug(
                        source = TAG,
                        message = "prefetchImages: Poster prefetched",
                        details = mapOf("remoteId" to ref.remoteId),
                    )
                } catch (e: Exception) {
                    TelegramLogRepository.debug(
                        source = TAG,
                        message = "prefetchImages: Poster prefetch failed (non-critical)",
                        details = mapOf("remoteId" to ref.remoteId, "error" to (e.message ?: "unknown")),
                    )
                }
            }

            // Prefetch backdrop
            backdropRef?.let { ref ->
                try {
                    ensureThumbDownloaded(ref, timeoutMs = 10_000L)
                    TelegramLogRepository.debug(
                        source = TAG,
                        message = "prefetchImages: Backdrop prefetched",
                        details = mapOf("remoteId" to ref.remoteId),
                    )
                } catch (e: Exception) {
                    TelegramLogRepository.debug(
                        source = TAG,
                        message = "prefetchImages: Backdrop prefetch failed (non-critical)",
                        details = mapOf("remoteId" to ref.remoteId, "error" to (e.message ?: "unknown")),
                    )
                }
            }
        }
    }
}
