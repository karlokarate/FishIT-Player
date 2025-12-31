package com.fishit.player.playback.telegram.config

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.playback.domain.mp4.Mp4MoovAtomValidator
import com.fishit.player.playback.domain.mp4.Mp4MoovValidationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Ensures Telegram files are ready for playback (v2 Architecture - Playback Layer).
 *
 * This component handles MP4 moov atom validation and streaming readiness checks. It bridges the
 * Transport layer (file downloads) with Playback layer (ExoPlayer).
 *
 * **Key Behaviors (from legacy T_TelegramFileDownloader):**
 * - Wait for minimum prefix before validation
 * - Validate MP4 moov atom completeness using [Mp4MoovAtomValidator]
 * - Poll downloaded_prefix_size until ready
 * - Handle seek-path (no moov validation needed)
 *
 * **What belongs here (Playback):**
 * - MP4 moov atom validation orchestration
 * - Streaming readiness checks
 * - Playback-specific thresholds
 *
 * **What does NOT belong here (Transport):**
 * - TDLib download primitives
 * - File state observation
 * - Storage maintenance
 *
 * @param fileClient The transport-layer file client (injected via DI)
 *
 * @see TelegramStreamingConfig for configuration constants
 * @see Mp4MoovAtomValidator for shared MP4 validation
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md Section 5.2
 */
class TelegramFileReadyEnsurer(
    private val fileClient: TelegramFileClient,
) {
    companion object {
        private const val TAG = "TelegramFileReadyEnsurer"
    }

    /**
     * Ensures a file is ready for playback with attempt-aware strategy (DIRECT_FIRST or BUFFERED_5MB).
     *
     * **DIRECT_FIRST Strategy:**
     * - Does NOT hard-block on MP4 moov checks
     * - Starts download and returns immediately once TDLib has local path
     * - Allows player to attempt playback as soon as possible
     * - May fail if file is not suitable for direct playback
     *
     * **BUFFERED_5MB Strategy:**
     * - Waits until downloadedBytes >= 5MB AND local path exists
     * - Provides deterministic fallback with reasonable latency
     * - MP4 moov checks are advisory only (not fatal)
     *
     * @param fileId TDLib file ID
     * @param attemptMode "DIRECT_FIRST" or "BUFFERED_5MB"
     * @param mimeType Optional MIME type hint
     * @return Local file path ready for playback
     * @throws TelegramStreamingException if download fails or times out
     */
    suspend fun ensureReadyForAttempt(
        fileId: Int,
        attemptMode: String,
        mimeType: String? = null,
    ): String {
        UnifiedLog.i(TAG) { "ensureReadyForAttempt(fileId=$fileId, attemptMode=$attemptMode, mime=$mimeType)" }

        // Start high-priority download
        fileClient.startDownload(
            fileId = fileId,
            priority = TelegramStreamingConfig.DOWNLOAD_PRIORITY_STREAMING,
            offset = TelegramStreamingConfig.DOWNLOAD_OFFSET_START,
            limit = TelegramStreamingConfig.DOWNLOAD_LIMIT_FULL,
        )

        return when (attemptMode) {
            "DIRECT_FIRST" -> {
                UnifiedLog.i(TAG) { "DIRECT_FIRST: Starting playback as soon as local path available" }
                withTimeout(TelegramStreamingConfig.DIRECT_FIRST_TIMEOUT_MS) {
                    pollUntilLocalPathAvailable(fileId)
                }
            }
            "BUFFERED_5MB" -> {
                UnifiedLog.i(TAG) { "BUFFERED_5MB: Waiting for 5MB buffer before playback" }
                withTimeout(TelegramStreamingConfig.BUFFERED_5MB_TIMEOUT_MS) {
                    pollUntilBuffered5MB(fileId, mimeType)
                }
            }
            else -> {
                throw TelegramStreamingException("Unknown attempt mode: $attemptMode")
            }
        }
    }

    /**
     * Ensures a file is ready for playback from the beginning (offset=0).
     *
     * **Platinum Playback Behavior:**
     * - Determines playback mode based on MIME type (PROGRESSIVE_FILE vs FULL_FILE)
     * - MP4 with moov atom → Fast progressive start
     * - MP4 without moov or MKV → Full download, then play (NEVER fails with "moov not found")
     *
     * This method:
     * 1. Starts a high-priority download (priority=32)
     * 2. Polls downloaded_prefix_size
     * 3. For MP4: Validates moov atom; if not found, switches to FULL_FILE mode
     * 4. For FULL_FILE mode: Waits for complete download
     * 5. Returns local file path when ready
     *
     * @param fileId TDLib file ID
     * @param mimeType Optional MIME type hint (e.g., "video/mp4", "video/x-matroska")
     * @return Local file path ready for playback
     * @throws TelegramStreamingException if download fails or times out (NOT for "moov not found")
     */
    suspend fun ensureReadyForPlayback(
        fileId: Int,
        mimeType: String? = null,
    ): String {
        UnifiedLog.d(TAG) { "ensureReadyForPlayback(fileId=$fileId, mime=$mimeType)" }

        // Determine initial playback mode based on MIME type
        val initialMode = TelegramPlaybackModeDetector.selectInitialMode(mimeType)
        UnifiedLog.i(TAG) { "Telegram playback mode selected: $initialMode - ${TelegramPlaybackModeDetector.describeMode(mimeType)}" }

        // Start high-priority download
        fileClient.startDownload(
            fileId = fileId,
            priority = TelegramStreamingConfig.DOWNLOAD_PRIORITY_STREAMING,
            offset = TelegramStreamingConfig.DOWNLOAD_OFFSET_START,
            limit = TelegramStreamingConfig.DOWNLOAD_LIMIT_FULL,
        )

        // Wait for readiness based on mode
        // Use different timeouts for PROGRESSIVE vs FULL_FILE modes
        val timeout = if (initialMode == TelegramPlaybackMode.FULL_FILE) {
            TelegramStreamingConfig.FULL_FILE_DOWNLOAD_TIMEOUT_MS
        } else {
            TelegramStreamingConfig.ENSURE_READY_TIMEOUT_MS
        }

        return try {
            withTimeout(timeout) {
                when (initialMode) {
                    TelegramPlaybackMode.PROGRESSIVE_FILE -> {
                        // Try progressive first, may fallback to FULL_FILE if moov not found
                        pollUntilReadyProgressiveWithFallback(fileId, mimeType)
                    }
                    TelegramPlaybackMode.FULL_FILE -> {
                        // Skip moov validation, wait for full download
                        pollUntilReadyFullFile(fileId)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Provide context-specific timeout message
            val message = if (initialMode == TelegramPlaybackMode.FULL_FILE) {
                "Download too slow: file not ready after ${timeout / 1000}s. " +
                    "Network may be too slow for this file size, or connection interrupted."
            } else {
                "Playback readiness timeout after ${timeout / 1000}s. " +
                    "Check network connectivity or file integrity."
            }
            throw TelegramStreamingException(message, cause = e)
        }
    }

    /**
     * Ensures a file is ready for playback from a seek position.
     *
     * For seek operations, we don't need moov validation (already have metadata). We just need to
     * ensure minimum read-ahead bytes are available at the seek position.
     *
     * @param fileId TDLib file ID
     * @param seekPosition Target seek position in bytes
     * @return Local file path ready for seek
     * @throws TelegramStreamingException if fails or times out
     */
    suspend fun ensureReadyForSeek(
        fileId: Int,
        seekPosition: Long,
    ): String {
        UnifiedLog.d(TAG) { "ensureReadyForSeek(fileId=$fileId, seekPosition=$seekPosition)" }

        // Start download at seek position with read-ahead
        fileClient.startDownload(
            fileId = fileId,
            priority = TelegramStreamingConfig.DOWNLOAD_PRIORITY_STREAMING,
            offset = seekPosition,
            limit = TelegramStreamingConfig.MIN_READ_AHEAD_BYTES,
        )

        // Wait for minimum read-ahead at seek position (no moov validation)
        return withTimeout(TelegramStreamingConfig.ENSURE_READY_TIMEOUT_MS) {
            pollUntilReadyForSeek(fileId, seekPosition)
        }
    }

    /**
     * Check if a file is immediately ready for playback (cached locally).
     *
     * **Checks (in order):**
     * 1. File fully downloaded → Ready
     * 2. File has enough prefix for progressive streaming → Check moov (if MP4)
     *
     * @param fileId TDLib file ID
     * @param mimeType Optional MIME type hint
     * @return Local file path if ready, null otherwise
     */
    suspend fun isReadyForPlayback(
        fileId: Int,
        mimeType: String? = null,
    ): String? {
        val file = fileClient.getFile(fileId) ?: return null

        if (file.isDownloadingCompleted && file.localPath != null) {
            return file.localPath
        }

        // For MP4-like containers, check if we have enough for progressive streaming
        if (TelegramPlaybackModeDetector.isMp4Container(mimeType)) {
            if (file.downloadedPrefixSize >= TelegramStreamingConfig.MIN_PREFIX_FOR_VALIDATION_BYTES) {
                val localPath = file.localPath ?: return null
                val result = Mp4MoovAtomValidator.checkMoovAtom(localPath, file.downloadedPrefixSize)
                if (result.isReadyForPlayback) {
                    return localPath
                }
            }
        }

        return null
    }

    // ========== Internal Methods ==========

    /**
     * Polls for PROGRESSIVE_FILE mode readiness with automatic fallback to FULL_FILE.
     *
     * **Platinum Playback Logic with Timeout Management:**
     * 1. Wait for MIN_PREFIX_FOR_VALIDATION_BYTES
     * 2. Check moov atom with Mp4MoovAtomValidator
     * 3. If moov found and complete → return (fast start)
     * 4. If moov not found after MAX_PREFIX_SCAN_BYTES → switch to FULL_FILE mode
     * 5. If moov incomplete after timeout → switch to FULL_FILE mode
     * 6. FULL_FILE mode: Uses extended timeout (60s) for large file downloads
     *
     * **Timeout Handling:**
     * - This method restarts the timeout when falling back to FULL_FILE mode
     * - FULL_FILE fallback gets its own 60-second timeout instead of remaining PROGRESSIVE timeout
     *
     * @param fileId TDLib file ID
     * @param mimeType Optional MIME type for logging context
     * @return Local file path ready for playback
     */
    private suspend fun pollUntilReadyProgressiveWithFallback(
        fileId: Int,
        mimeType: String?,
    ): String {
        try {
            // Try progressive playback (will throw exception if fallback needed)
            return pollUntilReadyProgressive(fileId, mimeType, throwOnFallback = true)
        } catch (e: FallbackToFullFileException) {
            // Moov not found or incomplete - switch to FULL_FILE mode with extended timeout
            UnifiedLog.i(TAG) { "Switching to FULL_FILE mode after progressive attempt: ${e.message}" }
            return withTimeout(TelegramStreamingConfig.FULL_FILE_DOWNLOAD_TIMEOUT_MS) {
                pollUntilReadyFullFile(fileId)
            }
        }
    }

    /**
     * Polls for PROGRESSIVE_FILE mode readiness.
     *
     * **Platinum Playback Logic:**
     * 1. Wait for MIN_PREFIX_FOR_VALIDATION_BYTES
     * 2. Check moov atom with Mp4MoovAtomValidator
     * 3. If moov found and complete → return (fast start)
     * 4. If moov not found after MAX_PREFIX_SCAN_BYTES → throw FallbackToFullFileException or call pollUntilReadyFullFile
     * 5. If moov incomplete after timeout → throw FallbackToFullFileException or call pollUntilReadyFullFile
     *
     * @param fileId TDLib file ID
     * @param mimeType Optional MIME type for logging context
     * @param throwOnFallback If true, throws FallbackToFullFileException when fallback needed; if false, returns direct result
     * @return Local file path ready for playback
     */
    private suspend fun pollUntilReadyProgressive(
        fileId: Int,
        mimeType: String?,
        throwOnFallback: Boolean = false,
    ): String {
        var lastLogTime = 0L
        var moovIncompleteStartTime: Long? = null

        while (true) {
            val file =
                fileClient.getFile(fileId)
                    ?: throw TelegramStreamingException("File not found: $fileId")

            val prefixSize = file.downloadedPrefixSize
            val localPath = file.localPath

            // Throttled logging
            val now = System.currentTimeMillis()
            if (TelegramStreamingConfig.ENABLE_VERBOSE_LOGGING ||
                now - lastLogTime >= TelegramStreamingConfig.PROGRESS_DEBOUNCE_MS
            ) {
                UnifiedLog.d(TAG) {
                    "Polling progressive: prefix=${prefixSize / 1024}KB, target=${TelegramStreamingConfig.MIN_PREFIX_FOR_VALIDATION_BYTES / 1024}KB"
                }
                lastLogTime = now
            }

            // Check if download is complete (can happen if file is very small)
            if (file.isDownloadingCompleted && localPath != null) {
                UnifiedLog.i(TAG) { "Download complete, ready for playback (fileId=$fileId, size=${prefixSize / 1024}KB)" }
                return localPath
            }

            // Check if we have enough prefix for moov validation
            if (prefixSize >= TelegramStreamingConfig.MIN_PREFIX_FOR_VALIDATION_BYTES && localPath != null) {
                // Validate moov atom using shared validator
                val moovResult = Mp4MoovAtomValidator.checkMoovAtom(localPath, prefixSize)

                when {
                    moovResult.isReadyForPlayback -> {
                        // Moov found and complete - progressive playback ready
                        UnifiedLog.i(TAG) {
                            "Moov validated, progressive playback ready: moovStart=${moovResult.moovStart}B, moovSize=${moovResult.moovSize}B"
                        }
                        return localPath
                    }
                    moovResult.found && !moovResult.complete -> {
                        // Moov found but incomplete - wait with timeout
                        if (moovIncompleteStartTime == null) {
                            moovIncompleteStartTime = now
                            UnifiedLog.d(TAG) {
                                "Moov found but incomplete, waiting for more data (moovSize=${moovResult.moovSize}B, available=${prefixSize}B)..."
                            }
                        } else if (now - moovIncompleteStartTime >=
                            Mp4MoovValidationConfig.MOOV_VALIDATION_TIMEOUT_MS
                        ) {
                            // Timeout waiting for moov to complete - switch to FULL_FILE mode
                            val reason = "Moov incomplete after ${Mp4MoovValidationConfig.MOOV_VALIDATION_TIMEOUT_MS}ms, " +
                                "moovSize=${moovResult.moovSize}B, available=${prefixSize}B"
                            UnifiedLog.i(TAG) { "$reason, switching to FULL_FILE mode" }
                            if (throwOnFallback) {
                                throw FallbackToFullFileException(reason)
                            } else {
                                return pollUntilReadyFullFile(fileId)
                            }
                        }
                    }
                    !moovResult.found &&
                        prefixSize >= TelegramStreamingConfig.MAX_PREFIX_SCAN_BYTES -> {
                        // Scanned max prefix, moov not found - switch to FULL_FILE mode (NOT an error!)
                        val reason = "Moov atom not found after scanning ${prefixSize / 1024}KB (mime=$mimeType). " +
                            "This is normal for non-faststart MP4 files."
                        UnifiedLog.i(TAG) { "$reason, switching to FULL_FILE mode" }
                        if (throwOnFallback) {
                            throw FallbackToFullFileException(reason)
                        } else {
                            return pollUntilReadyFullFile(fileId)
                        }
                    }
                    // else: moov not found yet, continue polling
                }
            }

            delay(TelegramStreamingConfig.PREFIX_POLL_INTERVAL_MS)
        }
    }

    /**
     * Polls for FULL_FILE mode readiness (waits for complete download).
     *
     * **Behavior:**
     * - Waits until isDownloadingCompleted == true
     * - OR until downloadedPrefixSize >= totalSize (safety check)
     * - Returns local file path when fully downloaded
     *
     * **Use Cases:**
     * - MKV files (no moov atom)
     * - MP4 files with moov at end (not faststart-optimized)
     * - MP4 files where moov validation failed/timed out
     *
     * @param fileId TDLib file ID
     * @return Local file path of fully downloaded file
     */
    private suspend fun pollUntilReadyFullFile(fileId: Int): String {
        var lastLogTime = 0L

        UnifiedLog.i(TAG) { "Telegram playback mode: FULL_FILE (waiting for complete download)" }

        while (true) {
            val file =
                fileClient.getFile(fileId)
                    ?: throw TelegramStreamingException("File not found: $fileId")

            val downloadedSize = file.downloadedPrefixSize
            val totalSize = if (file.size > 0) file.size else file.expectedSize
            val localPath = file.localPath

            // Throttled logging
            val now = System.currentTimeMillis()
            if (TelegramStreamingConfig.ENABLE_VERBOSE_LOGGING ||
                now - lastLogTime >= TelegramStreamingConfig.PROGRESS_DEBOUNCE_MS
            ) {
                val progress = if (totalSize > 0) {
                    "${(downloadedSize * 100 / totalSize)}%"
                } else {
                    "${downloadedSize / 1024}KB"
                }
                UnifiedLog.d(TAG) {
                    "FULL_FILE download: $progress (${downloadedSize / 1024}KB / ${totalSize / 1024}KB)"
                }
                lastLogTime = now
            }

            // Check if download is complete
            if (file.isDownloadingCompleted && localPath != null) {
                UnifiedLog.i(TAG) { "Telegram file fully downloaded, starting playback: ${totalSize / 1024}KB" }
                return localPath
            } else if (file.isDownloadingCompleted && localPath == null) {
                // Defensive: unexpected state, log and continue polling
                UnifiedLog.w(TAG) {
                    "Telegram file reported as fully downloaded but localPath is null " +
                        "(fileId=$fileId, downloadedSize=${downloadedSize / 1024}KB, totalSize=${totalSize / 1024}KB)"
                }
            }

            // Safety check: if downloadedSize >= totalSize, consider complete
            if (totalSize > 0 && downloadedSize >= totalSize && localPath != null) {
                UnifiedLog.i(TAG) { "Telegram file download reached total size, starting playback: ${totalSize / 1024}KB" }
                return localPath
            } else if (totalSize > 0 && downloadedSize >= totalSize && localPath == null) {
                // Defensive: unexpected state, log and continue polling
                UnifiedLog.w(TAG) {
                    "Telegram file download reached total size but localPath is null " +
                        "(fileId=$fileId, downloadedSize=${downloadedSize / 1024}KB, totalSize=${totalSize / 1024}KB)"
                }
            }

            delay(TelegramStreamingConfig.PREFIX_POLL_INTERVAL_MS)
        }
    }

    /**
     * Legacy method for backward compatibility.
     *
     * @deprecated Use pollUntilReadyProgressive or pollUntilReadyFullFile directly
     */
    @Deprecated("Use pollUntilReadyProgressive or pollUntilReadyFullFile")
    private suspend fun pollUntilReady(
        fileId: Int,
        validateMoov: Boolean,
        minBytes: Long = TelegramStreamingConfig.MIN_PREFIX_FOR_VALIDATION_BYTES,
    ): String {
        return if (validateMoov) {
            pollUntilReadyProgressive(fileId, mimeType = null)
        } else {
            pollUntilReadyFullFile(fileId)
        }
    }

    /**
     * Polls until file is ready for seek at the specified position.
     *
     * Uses correct check: downloadedPrefixSize >= seekPosition + MIN_READ_AHEAD_BYTES (not just
     * comparing against minBytes threshold).
     */
    private suspend fun pollUntilReadyForSeek(
        fileId: Int,
        seekPosition: Long,
    ): String {
        val requiredBytes = seekPosition + TelegramStreamingConfig.MIN_READ_AHEAD_BYTES
        var lastLogTime = 0L

        while (true) {
            val file =
                fileClient.getFile(fileId)
                    ?: throw TelegramStreamingException("File not found: $fileId")

            val prefixSize = file.downloadedPrefixSize
            // Use expectedSize when size is 0 (file size not yet known)
            val totalSize = if (file.size > 0) file.size else file.expectedSize
            val localPath = file.localPath

            // Throttled logging
            val now = System.currentTimeMillis()
            if (TelegramStreamingConfig.ENABLE_VERBOSE_LOGGING ||
                now - lastLogTime >= TelegramStreamingConfig.PROGRESS_DEBOUNCE_MS
            ) {
                UnifiedLog.d(TAG) {
                    "Seek poll: prefix=${prefixSize / 1024}KB, required=${requiredBytes / 1024}KB"
                }
                lastLogTime = now
            }

            // Check if download is complete
            if (file.isDownloadingCompleted && localPath != null) {
                UnifiedLog.d(TAG) { "Download complete for seek (fileId=$fileId, seekPosition=$seekPosition)" }
                return localPath
            }

            // Check if we have enough for seek (correct comparison)
            if (localPath != null) {
                // Either we have the required bytes, or we're at/past end of file
                val hasEnoughBytes = prefixSize >= requiredBytes
                val isNearEndOfFile =
                    totalSize > 0 &&
                        seekPosition + TelegramStreamingConfig.MIN_READ_AHEAD_BYTES >
                        totalSize &&
                        prefixSize >= seekPosition

                if (hasEnoughBytes || isNearEndOfFile) {
                    UnifiedLog.d(TAG) { "Seek ready (fileId=$fileId, seekPosition=$seekPosition, prefixSize=${prefixSize / 1024}KB)" }
                    return localPath
                }
            }

            delay(TelegramStreamingConfig.PREFIX_POLL_INTERVAL_MS)
        }
    }

    /**
     * Polls until local file path is available (DIRECT_FIRST strategy).
     *
     * This returns as soon as TDLib has a local path, without waiting for any specific amount
     * of data. The player will attempt playback immediately, which may succeed for well-optimized
     * files or fail and trigger fallback to BUFFERED_5MB.
     *
     * @param fileId TDLib file ID
     * @return Local file path (may have minimal data downloaded)
     */
    private suspend fun pollUntilLocalPathAvailable(fileId: Int): String {
        var lastLogTime = 0L

        while (true) {
            val file =
                fileClient.getFile(fileId)
                    ?: throw TelegramStreamingException("File not found: $fileId")

            // Throttled logging
            val now = System.currentTimeMillis()
            if (TelegramStreamingConfig.ENABLE_VERBOSE_LOGGING ||
                now - lastLogTime >= TelegramStreamingConfig.PROGRESS_DEBOUNCE_MS
            ) {
                UnifiedLog.d(TAG) {
                    "DIRECT_FIRST: Waiting for local path (downloaded=${file.downloadedPrefixSize / 1024}KB)"
                }
                lastLogTime = now
            }

            val localPath = file.localPath
            if (localPath != null) {
                UnifiedLog.i(TAG) {
                    "DIRECT_FIRST: Local path available, starting playback immediately " +
                        "(downloaded=${file.downloadedPrefixSize / 1024}KB)"
                }
                return localPath
            }

            delay(TelegramStreamingConfig.PREFIX_POLL_INTERVAL_MS)
        }
    }

    /**
     * Polls until 5MB is buffered (BUFFERED_5MB strategy).
     *
     * Waits until:
     * - downloadedPrefixSize >= 5MB (5 * 1024 * 1024 bytes)
     * - Local path exists
     *
     * For MP4 files, performs advisory moov check but does NOT fail if moov not found.
     * This provides deterministic fallback with reasonable latency.
     *
     * @param fileId TDLib file ID
     * @param mimeType Optional MIME type for advisory moov checks
     * @return Local file path with 5MB buffer
     */
    private suspend fun pollUntilBuffered5MB(fileId: Int, mimeType: String?): String {
        var lastLogTime = 0L
        val targetBytes = TelegramStreamingConfig.BUFFERED_5MB_THRESHOLD_BYTES

        while (true) {
            val file =
                fileClient.getFile(fileId)
                    ?: throw TelegramStreamingException("File not found: $fileId")

            val prefixSize = file.downloadedPrefixSize
            val localPath = file.localPath

            // Throttled logging
            val now = System.currentTimeMillis()
            if (TelegramStreamingConfig.ENABLE_VERBOSE_LOGGING ||
                now - lastLogTime >= TelegramStreamingConfig.PROGRESS_DEBOUNCE_MS
            ) {
                val progress = if (prefixSize < targetBytes) {
                    "${(prefixSize * 100 / targetBytes)}%"
                } else {
                    "100%"
                }
                UnifiedLog.d(TAG) {
                    "BUFFERED_5MB: $progress (${prefixSize / 1024}KB / ${targetBytes / 1024}KB)"
                }
                lastLogTime = now
            }

            // Check if download is complete (can happen for small files)
            if (file.isDownloadingCompleted && localPath != null) {
                UnifiedLog.i(TAG) { "BUFFERED_5MB: File fully downloaded, starting playback" }
                return localPath
            }

            // Check if we have 5MB buffer
            if (prefixSize >= targetBytes && localPath != null) {
                // For MP4, perform advisory moov check (not fatal)
                if (TelegramPlaybackModeDetector.isMp4Container(mimeType)) {
                    val moovResult = Mp4MoovAtomValidator.checkMoovAtom(localPath, prefixSize)
                    if (moovResult.isReadyForPlayback) {
                        UnifiedLog.i(TAG) {
                            "BUFFERED_5MB: Ready with moov validated (moovStart=${moovResult.moovStart}B)"
                        }
                    } else {
                        UnifiedLog.i(TAG) {
                            "BUFFERED_5MB: Ready (5MB buffered, moov check: ${if (moovResult.found) "incomplete" else "not found"} - not fatal)"
                        }
                    }
                } else {
                    UnifiedLog.i(TAG) { "BUFFERED_5MB: Ready (5MB buffered, non-MP4 container)" }
                }
                return localPath
            }

            delay(TelegramStreamingConfig.PREFIX_POLL_INTERVAL_MS)
        }
    }
}

/** Exception thrown when streaming readiness cannot be achieved. */
class TelegramStreamingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Internal exception used to signal fallback from PROGRESSIVE_FILE to FULL_FILE mode.
 *
 * This is not a user-facing error - it's a control flow mechanism to restart the timeout
 * when switching modes to ensure FULL_FILE downloads get their full 60-second timeout.
 */
private class FallbackToFullFileException(
    message: String,
) : Exception(message)
