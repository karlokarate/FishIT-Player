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
                        pollUntilReadyProgressive(fileId, mimeType)
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
     * **Platinum Playback Logic:**
     * 1. Wait for MIN_PREFIX_FOR_VALIDATION_BYTES
     * 2. Check moov atom with Mp4MoovAtomValidator
     * 3. If moov found and complete → return (fast start)
     * 4. If moov not found after MAX_PREFIX_SCAN_BYTES → switch to FULL_FILE mode (NOT an error)
     * 5. If moov incomplete after timeout → switch to FULL_FILE mode (NOT an error)
     * 6. FULL_FILE mode: wait for complete download, then return
     *
     * @param fileId TDLib file ID
     * @param mimeType Optional MIME type for logging context
     * @return Local file path ready for playback
     */
    private suspend fun pollUntilReadyProgressive(
        fileId: Int,
        mimeType: String?,
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
                UnifiedLog.i(TAG) { "Download complete, ready for playback: $localPath" }
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
                            UnifiedLog.i(TAG) {
                                "Moov incomplete after ${Mp4MoovValidationConfig.MOOV_VALIDATION_TIMEOUT_MS}ms, " +
                                    "switching to FULL_FILE mode (moovSize=${moovResult.moovSize}B, available=${prefixSize}B)"
                            }
                            return pollUntilReadyFullFile(fileId)
                        }
                    }
                    !moovResult.found &&
                        prefixSize >= TelegramStreamingConfig.MAX_PREFIX_SCAN_BYTES -> {
                        // Scanned max prefix, moov not found - switch to FULL_FILE mode (NOT an error!)
                        UnifiedLog.i(TAG) {
                            "Moov atom not found after scanning ${prefixSize / 1024}KB, " +
                                "switching to FULL_FILE mode (mime=$mimeType). This is normal for non-faststart MP4 files."
                        }
                        return pollUntilReadyFullFile(fileId)
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
            }

            // Safety check: if downloadedSize >= totalSize, consider complete
            if (totalSize > 0 && downloadedSize >= totalSize && localPath != null) {
                UnifiedLog.i(TAG) { "Telegram file download reached total size, starting playback: ${totalSize / 1024}KB" }
                return localPath
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
                UnifiedLog.d(TAG) { "Download complete for seek: $localPath" }
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
                    UnifiedLog.d(TAG) { "Seek ready at $seekPosition: $localPath" }
                    return localPath
                }
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
