package com.fishit.player.playback.telegram.config

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TgFileUpdate
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.RandomAccessFile

/**
 * Ensures Telegram files are ready for playback (v2 Architecture - Playback Layer).
 *
 * This component handles MP4 moov atom validation and streaming readiness checks.
 * It bridges the Transport layer (file downloads) with Playback layer (ExoPlayer).
 *
 * **Key Behaviors (from legacy T_TelegramFileDownloader):**
 * - Wait for minimum prefix before validation
 * - Validate MP4 moov atom completeness
 * - Poll downloaded_prefix_size until ready
 * - Handle seek-path (no moov validation needed)
 *
 * **What belongs here (Playback):**
 * - MP4 moov atom validation
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
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md Section 5.2
 */
class TelegramFileReadyEnsurer(
    private val fileClient: TelegramFileClient
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
            limit = TelegramStreamingConfig.DOWNLOAD_LIMIT_FULL
        )

        // Wait for readiness
        return withTimeout(TelegramStreamingConfig.ENSURE_READY_TIMEOUT_MS) {
            pollUntilReady(fileId, validateMoov = true)
        }
    }

    /**
     * Ensures a file is ready for playback from a seek position.
     *
     * For seek operations, we don't need moov validation (already have metadata).
     * We just need to ensure minimum read-ahead bytes are available.
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
            limit = TelegramStreamingConfig.MIN_READ_AHEAD_BYTES
        )

        // Wait for minimum read-ahead (no moov validation)
        return withTimeout(TelegramStreamingConfig.ENSURE_READY_TIMEOUT_MS) {
            pollUntilReady(fileId, validateMoov = false, minBytes = seekPosition + TelegramStreamingConfig.MIN_READ_AHEAD_BYTES)
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
            if (validateMoovAtom(localPath)) {
                return localPath
            }
        }

        return null
    }

    // ========== Internal Methods ==========

    private suspend fun pollUntilReady(
        fileId: Int,
        validateMoov: Boolean,
        minBytes: Long = TelegramStreamingConfig.MIN_PREFIX_FOR_VALIDATION_BYTES
    ): String {
        var lastLogTime = 0L

        while (true) {
            val file = fileClient.getFile(fileId)
                ?: throw TelegramStreamingException("File not found: $fileId")

            val prefixSize = file.downloadedPrefixSize
            val localPath = file.localPath

            // Throttled logging
            val now = System.currentTimeMillis()
            if (TelegramStreamingConfig.ENABLE_VERBOSE_LOGGING ||
                now - lastLogTime >= TelegramStreamingConfig.PROGRESS_DEBOUNCE_MS) {
                UnifiedLog.d(TAG, "Polling: prefix=${prefixSize / 1024}KB, target=${minBytes / 1024}KB")
                lastLogTime = now
            }

            // Check if download is complete
            if (file.isDownloadingCompleted && localPath != null) {
                UnifiedLog.d(TAG, "Download complete: $localPath")
                return localPath
            }

            // Check if we have enough prefix
            if (prefixSize >= minBytes && localPath != null) {
                if (!validateMoov) {
                    // Seek path - no moov validation needed
                    UnifiedLog.d(TAG, "Seek ready: $localPath")
                    return localPath
                }

                // Validate moov atom
                if (validateMoovAtom(localPath)) {
                    UnifiedLog.d(TAG, "Moov validated, ready: $localPath")
                    return localPath
                }

                // Moov not complete - need more data
                if (prefixSize >= TelegramStreamingConfig.MAX_PREFIX_SCAN_BYTES) {
                    throw TelegramStreamingException(
                        "Moov atom not found after ${prefixSize / 1024}KB - file may not support streaming"
                    )
                }
            }

            delay(TelegramStreamingConfig.PREFIX_POLL_INTERVAL_MS)
        }
    }

    /**
     * Validates that the MP4 moov atom is complete in the downloaded prefix.
     *
     * The moov atom contains all metadata (codec info, duration, etc.) needed
     * by ExoPlayer to initialize playback. If it's incomplete, playback will fail.
     *
     * @param localPath Path to the partially downloaded file
     * @return true if moov atom is complete
     */
    private fun validateMoovAtom(localPath: String): Boolean {
        return try {
            RandomAccessFile(localPath, "r").use { file ->
                // Parse MP4 atoms looking for complete moov
                var position = 0L
                val fileLength = file.length()

                while (position < fileLength - 8) {
                    file.seek(position)

                    // Read atom size and type
                    val size = file.readInt().toLong() and 0xFFFFFFFFL
                    val typeBytes = ByteArray(4)
                    file.read(typeBytes)
                    val type = String(typeBytes, Charsets.US_ASCII)

                    // Handle extended size
                    val atomSize = if (size == 1L) {
                        file.readLong()
                    } else if (size == 0L) {
                        fileLength - position // Atom extends to end of file
                    } else {
                        size
                    }

                    if (type == "moov") {
                        // Check if we have the complete moov atom
                        val moovEnd = position + atomSize
                        val complete = moovEnd <= fileLength
                        if (TelegramStreamingConfig.ENABLE_VERBOSE_LOGGING) {
                            UnifiedLog.d(TAG, "Moov found at $position, size=$atomSize, complete=$complete")
                        }
                        return complete
                    }

                    // Move to next atom
                    position += atomSize
                }

                // Moov not found yet
                false
            }
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "Moov validation error: ${e.message}")
            false
        }
    }
}

/**
 * Exception thrown when streaming readiness cannot be achieved.
 */
class TelegramStreamingException(message: String, cause: Throwable? = null) : Exception(message, cause)
