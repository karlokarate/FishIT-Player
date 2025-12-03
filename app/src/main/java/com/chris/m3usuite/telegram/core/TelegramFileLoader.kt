package com.chris.m3usuite.telegram.core

import android.content.Context
import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.domain.TelegramStreamingSettingsProvider
import com.chris.m3usuite.telegram.domain.TelegramStreamingSettingsProviderHolder
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.min

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
    context: Context,
    private val serviceClient: T_TelegramServiceClient,
    private val settingsProvider: TelegramStreamingSettingsProvider =
        TelegramStreamingSettingsProviderHolder.get(context),
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
        private const val MIN_THUMB_PREFIX_BYTES = 64 * 1024L
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
     * **Standard TDLib Behavior (2025-12-03):**
     * - Always downloads entire thumbnail file using offset=0, limit=0
     * - Waits for isDownloadingCompleted==true
     * - Verifies local file length matches expectedSize
     * - No partial/prefix downloads for thumbnails
     */
    private suspend fun tryDownloadThumbByFileId(
        fileId: Int,
        timeoutMs: Long,
    ): ThumbResult {
        try {
            TelegramLogRepository.info(
                source = TAG,
                message = "thumb download started",
                details = mapOf("fileId" to fileId.toString()),
            )

            // Get file info to determine total size
            val fileInfo = downloader.getFileInfo(fileId)
            val totalSizeBytes = fileInfo?.expectedSize?.toLong() ?: 0L

            if (totalSizeBytes <= 0L) {
                TelegramLogRepository.warn(
                    source = TAG,
                    message = "thumb download: size unknown",
                    details =
                        mapOf(
                            "fileId" to fileId.toString(),
                            "expectedSize" to (fileInfo?.expectedSize?.toString() ?: "null"),
                        ),
                )
            }

            // Check if already fully downloaded
            val localPath = fileInfo?.local?.path
            val isAlreadyComplete = fileInfo?.local?.isDownloadingCompleted ?: false
            if (isAlreadyComplete && !localPath.isNullOrBlank()) {
                val localFile = java.io.File(localPath)
                if (localFile.exists() && (totalSizeBytes <= 0L || localFile.length() >= totalSizeBytes)) {
                    TelegramLogRepository.info(
                        source = TAG,
                        message = "thumb already complete, skipping download",
                        details =
                            mapOf(
                                "fileId" to fileId.toString(),
                                "path" to localPath,
                                "fileSize" to localFile.length().toString(),
                                "expectedSize" to totalSizeBytes.toString(),
                            ),
                    )
                    return ThumbResult.Success(localPath)
                }
            }

            // Start full download: offset=0, limit=0 (standard TDLib)
            val downloadStarted = downloader.startDownload(
                fileId = fileId,
                priority = DEFAULT_PRIORITY, // Lower priority for thumbnails
            )

            if (!downloadStarted) {
                TelegramLogRepository.error(
                    source = TAG,
                    message = "thumb download failed to start",
                    details = mapOf("fileId" to fileId.toString()),
                )
                return ThumbResult.OtherError("Failed to start download")
            }

            // Poll until download is complete
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsedMs = System.currentTimeMillis() - startTime
                if (elapsedMs > timeoutMs) {
                    TelegramLogRepository.error(
                        source = TAG,
                        message = "thumb download timeout",
                        details =
                            mapOf(
                                "fileId" to fileId.toString(),
                                "timeoutMs" to timeoutMs.toString(),
                            ),
                    )
                    return ThumbResult.OtherError("Download timeout after ${timeoutMs}ms")
                }

                // Get fresh file state
                val currentInfo = downloader.getFileInfo(fileId)
                val currentPath = currentInfo?.local?.path
                val isComplete = currentInfo?.local?.isDownloadingCompleted ?: false

                // Check if download is complete
                if (isComplete && !currentPath.isNullOrBlank()) {
                    val localFile = java.io.File(currentPath)
                    if (localFile.exists()) {
                        val fileLength = localFile.length()
                        val fullyDownloaded = totalSizeBytes <= 0L || fileLength >= totalSizeBytes

                        TelegramLogRepository.info(
                            source = TAG,
                            message = "thumb download completed",
                            details =
                                mapOf(
                                    "fileId" to fileId.toString(),
                                    "path" to currentPath,
                                    "fileSize" to fileLength.toString(),
                                    "expectedSize" to totalSizeBytes.toString(),
                                    "fullyDownloaded" to fullyDownloaded.toString(),
                                ),
                        )

                        if (!fullyDownloaded) {
                            TelegramLogRepository.warn(
                                source = TAG,
                                message = "thumb download incomplete",
                                details =
                                    mapOf(
                                        "fileId" to fileId.toString(),
                                        "fileSize" to fileLength.toString(),
                                        "expectedSize" to totalSizeBytes.toString(),
                                    ),
                            )
                        }

                        return ThumbResult.Success(currentPath)
                    }
                }

                // Wait before next poll
                kotlinx.coroutines.delay(100L)
            }
        } catch (e: Exception) {
            val message = e.message ?: ""
            return if (message.contains("404", ignoreCase = true) || message.contains("not found", ignoreCase = true)) {
                ThumbResult.NotFound404
            } else {
                TelegramLogRepository.error(
                    source = TAG,
                    message = "thumb download failed",
                    exception = e,
                    details = mapOf("fileId" to fileId.toString()),
                )
                ThumbResult.OtherError(message)
            }
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
     * Ensure file ready for playback with MP4 header validation.
     * Used by legacy TelegramVideoDetailScreen ONLY.
     *
     * Phase D+ (2025-12-03): Updated to use ensureFileReadyWithMp4Validation()
     * following TDLib best practices (offset=0, limit=0, moov validation).
     * Now supports remoteId for stale fileId resolution.
     *
     * New playback paths should use TelegramFileDataSource via standard
     * Media3/ExoPlayer tg:// URLs.
     *
     * @param fileId TDLib file ID
     * @param remoteId Optional stable remote file ID for stale fileId fallback
     * @param timeoutMs Maximum wait time in milliseconds
     * @return Local path or null on failure
     */
    suspend fun ensureFileForPlayback(
        fileId: Int,
        remoteId: String? = null,
        timeoutMs: Long = StreamingConfigRefactor.ENSURE_READY_TIMEOUT_MS,
    ): String? =
        try {
            TelegramLogRepository.info(
                source = TAG,
                message = "ensureFileForPlayback: Starting with MP4 validation for fileId=$fileId, remoteId=${remoteId ?: "none"}",
            )

            val path =
                downloader.ensureFileReadyWithMp4Validation(
                    fileId = fileId,
                    remoteId = remoteId,
                    timeoutMs = timeoutMs,
                )

            TelegramLogRepository.info(
                source = TAG,
                message = "ensureFileForPlayback: Ready (moov validated) fileId=$fileId, path=$path",
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
