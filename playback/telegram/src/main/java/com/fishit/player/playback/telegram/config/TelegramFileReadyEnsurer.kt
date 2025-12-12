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
     * This method:
     * 1. Starts a high-priority download (priority=32)
     * 2. Polls downloaded_prefix_size
     * 3. Validates MP4 moov atom when minimum prefix is available
     * 4. Returns local file path when ready
     *
     * @param fileId TDLib file ID
     * @return Local file path ready for playback
     * @throws TelegramStreamingException if validation fails or times out
     */
    suspend fun ensureReadyForPlayback(fileId: Int): String {
        UnifiedLog.d(TAG, "ensureReadyForPlayback(fileId=$fileId)")

        // Start high-priority download
        fileClient.startDownload(
                fileId = fileId,
                priority = TelegramStreamingConfig.DOWNLOAD_PRIORITY_STREAMING,
                offset = TelegramStreamingConfig.DOWNLOAD_OFFSET_START,
                limit = TelegramStreamingConfig.DOWNLOAD_LIMIT_FULL,
        )

        // Wait for readiness
        return withTimeout(TelegramStreamingConfig.ENSURE_READY_TIMEOUT_MS) {
            pollUntilReady(fileId, validateMoov = true)
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
    suspend fun ensureReadyForSeek(fileId: Int, seekPosition: Long): String {
        UnifiedLog.d(TAG, "ensureReadyForSeek(fileId=$fileId, seekPosition=$seekPosition)")

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
     * @param fileId TDLib file ID
     * @return Local file path if ready, null otherwise
     */
    suspend fun isReadyForPlayback(fileId: Int): String? {
        val file = fileClient.getFile(fileId) ?: return null

        if (file.isDownloadingCompleted && file.localPath != null) {
            return file.localPath
        }

        // Check if we have enough for streaming
        if (file.downloadedPrefixSize >= TelegramStreamingConfig.MIN_PREFIX_FOR_VALIDATION_BYTES) {
            val localPath = file.localPath ?: return null
            val result = Mp4MoovAtomValidator.checkMoovAtom(localPath, file.downloadedPrefixSize)
            if (result.isReadyForPlayback) {
                return localPath
            }
        }

        return null
    }

    // ========== Internal Methods ==========

    private suspend fun pollUntilReady(
            fileId: Int,
            validateMoov: Boolean,
            minBytes: Long = TelegramStreamingConfig.MIN_PREFIX_FOR_VALIDATION_BYTES,
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
                UnifiedLog.d(
                        TAG,
                        "Polling: prefix=${prefixSize / 1024}KB, target=${minBytes / 1024}KB"
                )
                lastLogTime = now
            }

            // Check if download is complete
            if (file.isDownloadingCompleted && localPath != null) {
                UnifiedLog.d(TAG, "Download complete: $localPath")
                return localPath
            }

            // Check if we have enough prefix for validation
            if (prefixSize >= minBytes && localPath != null) {
                if (!validateMoov) {
                    // Non-MP4 path - no moov validation needed
                    UnifiedLog.d(TAG, "Prefix ready (no moov check): $localPath")
                    return localPath
                }

                // Validate moov atom using shared validator
                val moovResult = Mp4MoovAtomValidator.checkMoovAtom(localPath, prefixSize)

                when {
                    moovResult.isReadyForPlayback -> {
                        UnifiedLog.d(
                                TAG,
                                "Moov validated, ready: $localPath (moovStart=${moovResult.moovStart}, moovSize=${moovResult.moovSize})"
                        )
                        return localPath
                    }
                    moovResult.found && !moovResult.complete -> {
                        // Moov found but incomplete - start/continue timeout
                        if (moovIncompleteStartTime == null) {
                            moovIncompleteStartTime = now
                            UnifiedLog.d(TAG, "Moov found but incomplete, waiting for more data...")
                        } else if (now - moovIncompleteStartTime >=
                                        Mp4MoovValidationConfig.MOOV_VALIDATION_TIMEOUT_MS
                        ) {
                            // Timeout waiting for moov to complete
                            throw TelegramStreamingException(
                                    "Moov atom incomplete after ${Mp4MoovValidationConfig.MOOV_VALIDATION_TIMEOUT_MS}ms " +
                                            "(moovStart=${moovResult.moovStart}, moovSize=${moovResult.moovSize}, available=$prefixSize)"
                            )
                        }
                    }
                    !moovResult.found &&
                            prefixSize >= TelegramStreamingConfig.MAX_PREFIX_SCAN_BYTES -> {
                        // Scanned max prefix, moov not found
                        throw TelegramStreamingException(
                                "Moov atom not found after ${prefixSize / 1024}KB - file may not support streaming"
                        )
                    }
                // else: moov not found yet, continue polling
                }
            }

            delay(TelegramStreamingConfig.PREFIX_POLL_INTERVAL_MS)
        }
    }

    /**
     * Polls until file is ready for seek at the specified position.
     *
     * Uses correct check: downloadedPrefixSize >= seekPosition + MIN_READ_AHEAD_BYTES (not just
     * comparing against minBytes threshold).
     */
    private suspend fun pollUntilReadyForSeek(fileId: Int, seekPosition: Long): String {
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
                UnifiedLog.d(
                        TAG,
                        "Seek poll: prefix=${prefixSize / 1024}KB, required=${requiredBytes / 1024}KB"
                )
                lastLogTime = now
            }

            // Check if download is complete
            if (file.isDownloadingCompleted && localPath != null) {
                UnifiedLog.d(TAG, "Download complete for seek: $localPath")
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
                    UnifiedLog.d(TAG, "Seek ready at $seekPosition: $localPath")
                    return localPath
                }
            }

            delay(TelegramStreamingConfig.PREFIX_POLL_INTERVAL_MS)
        }
    }
}

/** Exception thrown when streaming readiness cannot be achieved. */
class TelegramStreamingException(message: String, cause: Throwable? = null) :
        Exception(message, cause)
