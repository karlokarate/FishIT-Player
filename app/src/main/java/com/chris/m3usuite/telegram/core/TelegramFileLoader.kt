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

    companion object {
        private const val TAG = "TelegramFileLoader"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val DEFAULT_PRIORITY = 16 // Lower priority for thumbnails
    }

    /**
     * Ensure thumbnail is downloaded and return local path (Requirement 3).
     *
     * Thin wrapper around T_TelegramFileDownloader.ensureFileReady().
     * Downloads the file with low priority and returns local path.
     *
     * This is designed to be called from LaunchedEffect in tiles.
     *
     * @param fileId TDLib file ID
     * @param timeoutMs Maximum time to wait for download
     * @return Local file path or null
     */
    suspend fun ensureThumbDownloaded(
        fileId: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? =
        try {
            // Log start (Requirement 3.1.1)
            TelegramLogRepository.debug(
                source = TAG,
                message = "ensureThumbDownloaded start",
                details = mapOf("fileId" to fileId.toString()),
            )

            // Use downloader to ensure the file is ready (entire file for thumbnails)
            val path =
                downloader.ensureFileReady(
                    fileId = fileId,
                    startPosition = 0,
                    minBytes = 0, // Download entire file
                    timeoutMs = timeoutMs,
                )

            // Log success (Requirement 3.1.2)
            TelegramLogRepository.debug(
                source = TAG,
                message = "ensureThumbDownloaded success",
                details =
                    mapOf(
                        "fileId" to fileId.toString(),
                        "path" to path,
                    ),
            )
            path
        } catch (e: Exception) {
            // Log failure/timeout with exception (Requirement 3.1.3)
            TelegramLogRepository.error(
                source = TAG,
                message = "ensureThumbDownloaded failed",
                exception = e,
                details = mapOf("fileId" to fileId.toString()),
            )
            null
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

    /**
     * Ensure image is downloaded using TelegramImageRef (remoteId-first).
     *
     * Phase T2: Uses remoteId-first resolution strategy:
     * 1. If fileId is valid (> 0), try to use it directly
     * 2. If that fails with 404, resolve fileId via remoteId
     * 3. Else resolve fileId via remoteId using downloader.resolveRemoteFileId()
     *
     * **Phase D+ Fix**: If the cached fileId returns 404 (stale), fall back to
     * remoteId resolution. This handles cases where fileIds become invalid
     * after app restarts or TDLib session changes.
     *
     * @param imageRef TelegramImageRef with remoteId/uniqueId/fileId
     * @param timeoutMs Maximum time to wait for download
     * @return Local file path or null
     */
    suspend fun ensureImageDownloaded(
        imageRef: TelegramImageRef,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? {
        // First, try with the stored fileId if available
        val storedFileId = imageRef.fileId
        if (storedFileId != null && storedFileId > 0) {
            val result = ensureThumbDownloaded(storedFileId, timeoutMs)
            if (result != null) {
                return result
            }

            // If failed, check if it was a 404 and try remoteId resolution
            TelegramLogRepository.debug(
                source = TAG,
                message = "ensureImageDownloaded: fileId failed, trying remoteId resolution",
                details =
                    mapOf(
                        "staleFileId" to storedFileId.toString(),
                        "remoteId" to imageRef.remoteId,
                    ),
            )
        }

        // Resolve via remoteId
        val remoteId = imageRef.remoteId
        if (remoteId.isBlank()) {
            TelegramLogRepository.warn(
                source = TAG,
                message = "ensureImageDownloaded: No remoteId available for fallback",
            )
            return null
        }

        val resolvedFileId = downloader.resolveRemoteFileId(remoteId)
        if (resolvedFileId == null || resolvedFileId <= 0) {
            TelegramLogRepository.warn(
                source = TAG,
                message = "ensureImageDownloaded: remoteId resolution failed",
                details = mapOf("remoteId" to remoteId),
            )
            return null
        }

        TelegramLogRepository.debug(
            source = TAG,
            message = "ensureImageDownloaded: remoteId resolved to new fileId",
            details =
                mapOf(
                    "remoteId" to remoteId,
                    "resolvedFileId" to resolvedFileId.toString(),
                ),
        )

        return ensureThumbDownloaded(resolvedFileId, timeoutMs)
    }

    /**
     * Resolve fileId from TelegramImageRef using remoteId-first strategy.
     *
     * @param imageRef TelegramImageRef with remoteId/uniqueId/fileId
     * @return Resolved fileId or null if resolution fails
     */
    private suspend fun resolveFileId(imageRef: TelegramImageRef): Int? {
        // Fast path: use fileId if available
        val fileId = imageRef.fileId
        if (fileId != null && fileId > 0) {
            return fileId
        }

        // Slow path: resolve via remoteId
        val remoteId = imageRef.remoteId
        if (remoteId.isBlank()) {
            TelegramLogRepository.warn(
                source = TAG,
                message = "resolveFileId: No fileId or remoteId available",
            )
            return null
        }

        TelegramLogRepository.debug(
            source = TAG,
            message = "resolveFileId: Resolving via remoteId",
            details = mapOf("remoteId" to remoteId),
        )

        return downloader.resolveRemoteFileId(remoteId)
    }

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
                    ensureImageDownloaded(ref, timeoutMs = 10_000L)
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
                    ensureImageDownloaded(ref, timeoutMs = 10_000L)
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
