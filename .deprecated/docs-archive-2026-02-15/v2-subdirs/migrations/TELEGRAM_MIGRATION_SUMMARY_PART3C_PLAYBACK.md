# Telegram Legacy Module Migration Summary - Part 3C: Playback Layer

**Migration Date:** 2025-01-16  
**Commit:** `52709299`

---

## 5. TelegramStreamingConfig.kt (playback/telegram/config/)

**Path:** `playback/telegram/src/main/java/com/fishit/player/playback/telegram/config/TelegramStreamingConfig.kt`

**Lines:** 182

```kotlin
package com.fishit.player.playback.telegram.config

/**
 * TDLib-Optimized Streaming Configuration for Video Playback (v2 Architecture).
 *
 * This is the **single source of truth** for Telegram streaming constants.
 * Ported from legacy `StreamingConfigRefactor` with v2 compliance.
 *
 * **TDLib Streaming Architecture (Official Best Practices):**
 *
 * 1. **Progressive Download with isStreamable=true:**
 *    - TDLib manages internal buffering and prefix caching
 *    - App calls `downloadFile(fileId, priority=32, offset=0, limit=0)` for full progressive download
 *    - TDLib automatically detects streamable MP4/MOV files (moov atom at start)
 *    - No manual windowing or chunk management needed
 *
 * 2. **Prefix Size Management:**
 *    - TDLib downloads in chunks and updates `file.local.downloaded_prefix_size`
 *    - App polls this value to determine when playback can start
 *    - Minimum prefix must contain complete MP4 moov atom (metadata)
 *
 * 3. **Header Validation (Critical):**
 *    - Use Mp4HeaderParser.validateMoovAtom() before starting playback
 *    - Wait until: downloaded_prefix_size >= MIN_PREFIX_BYTES AND moov atom complete
 *    - This prevents ExoPlayer initialization errors with incomplete metadata
 *
 * 4. **Priority Levels (TDLib Standard):**
 *    - Priority 32: Active streaming (high priority, prevents interruption)
 *    - Priority 16: Background prefetch (medium priority)
 *    - Priority 1-8: Low priority background downloads
 *
 * 5. **Zero-Copy Streaming:**
 *    - TDLib caches files on disk (unavoidable but efficient)
 *    - App uses FileDataSource to read directly from TDLib cache
 *    - No in-memory buffers or additional copies
 *    - ExoPlayer handles all seek/scrub operations via FileDataSource
 *
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 * @see TelegramFileReadyEnsurer for playback readiness logic
 */
object TelegramStreamingConfig {

    // ========== MP4 Header Validation ==========

    /**
     * Minimum prefix size before attempting header validation (64 KB).
     *
     * Most MP4 files have ftyp+moov within the first 64-512 KB.
     * This is a soft threshold - we check moov completeness, not just byte count.
     *
     * **TDLib Behavior:**
     * - Downloads in chunks (TDLib internal chunk size varies)
     * - Updates downloaded_prefix_size incrementally
     * - App should wait for this minimum + complete moov atom
     */
    const val MIN_PREFIX_FOR_VALIDATION_BYTES: Long = 64 * 1024 // 64 KiB

    /**
     * Maximum prefix size to wait for if moov not found (2 MB).
     *
     * If moov atom not found after 2MB, file is likely:
     * - Not optimized for streaming (moov at end)
     * - Corrupted or invalid MP4
     * - Requires full download before playback
     *
     * TDLib's `supportsStreaming` flag should already filter these,
     * but this provides a safety timeout.
     */
    const val MAX_PREFIX_SCAN_BYTES: Long = 2 * 1024 * 1024 // 2 MiB

    // ========== Download Strategy ==========

    /**
     * Download priority for active streaming (TDLib standard: 32).
     *
     * **TDLib Priority Levels:**
     * - 32: Highest priority for user-initiated playback
     * - 16-24: Medium priority for prefetch
     * - 1-8: Low priority for background downloads
     *
     * Using priority=32 ensures download is not interrupted by other operations.
     */
    const val DOWNLOAD_PRIORITY_STREAMING: Int = 32

    /**
     * Download priority for background prefetch (TDLib standard: 16).
     */
    const val DOWNLOAD_PRIORITY_BACKGROUND: Int = 16

    /**
     * Download priority for thumbnail prefetch.
     */
    const val DOWNLOAD_PRIORITY_THUMBNAIL: Int = 16

    /**
     * Initial download offset (always 0 for streaming).
     *
     * **TDLib Best Practice:**
     * - Always start at offset=0 for streamable files
     * - TDLib automatically optimizes prefix download
     * - Seeking is handled later by FileDataSource reading from cache
     */
    const val DOWNLOAD_OFFSET_START: Long = 0L

    /**
     * Download limit (0 = download entire file progressively).
     *
     * **TDLib Best Practice:**
     * - Use limit=0 for progressive download of full file
     * - TDLib streams chunks as needed (no need for manual windowing)
     * - File remains in TDLib cache for efficient seeking
     */
    const val DOWNLOAD_LIMIT_FULL: Long = 0L

    // ========== Polling & Timeouts ==========

    /**
     * Polling interval for checking downloaded_prefix_size (100ms).
     *
     * Balance between responsiveness and CPU usage.
     * TDLib updates prefix size incrementally as chunks arrive.
     */
    const val PREFIX_POLL_INTERVAL_MS: Long = 100L

    /**
     * Maximum wait time for initial prefix + moov validation (30 seconds).
     *
     * Covers:
     * - Initial TDLib connection delay
     * - Network latency for first chunks
     * - Slow connections (minimum ~20 KB/s for 512KB in 30s)
     *
     * If timeout occurs, file is likely:
     * - Network issue (check connectivity)
     * - Very large moov atom (rare, indicates non-optimized file)
     */
    const val ENSURE_READY_TIMEOUT_MS: Long = 30_000L

    /**
     * Timeout for moov atom completeness check (5 seconds).
     *
     * Once we have MIN_PREFIX_FOR_VALIDATION_BYTES, we check moov status.
     * If moov is started but incomplete, wait up to this timeout.
     * If still incomplete, likely indicates non-streamable file.
     */
    const val MOOV_VALIDATION_TIMEOUT_MS: Long = 5_000L

    // ========== Read-Ahead (for Seek Operations) ==========

    /**
     * Minimum read-ahead for seek operations (1 MB).
     *
     * When user seeks to position X:
     * - Ensure TDLib has downloaded X + MIN_READ_AHEAD_BYTES
     * - Prevents immediate rebuffering after seek
     * - TDLib progressive download continues from seek point
     */
    const val MIN_READ_AHEAD_BYTES: Long = 1 * 1024 * 1024 // 1 MiB

    // ========== Logging & Debugging ==========

    /**
     * Debounce interval for progress logging (250ms).
     *
     * Reduces log spam during active downloads.
     * Progress updates still occur via Flow but logs are throttled.
     */
    const val PROGRESS_DEBOUNCE_MS: Long = 250L

    /**
     * Enable verbose logging for streaming operations.
     *
     * When true:
     * - Logs every prefix poll iteration
     * - Logs detailed moov atom parsing
     * - Logs TDLib file state changes
     *
     * Default: false (enable only for debugging)
     */
    const val ENABLE_VERBOSE_LOGGING: Boolean = false
}
```

---

## 6. TelegramFileReadyEnsurer.kt (playback/telegram/config/)

**Path:** `playback/telegram/src/main/java/com/fishit/player/playback/telegram/config/TelegramFileReadyEnsurer.kt`

**Lines:** 245

```kotlin
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
```

---

## Summary of All Created Files

| # | File | Module | Lines | Purpose |
|---|------|--------|-------|---------|
| 1 | `TelegramAuthClient.kt` | transport-telegram | 77 | Auth interface |
| 2 | `TelegramHistoryClient.kt` | transport-telegram | 107 | Chat/message interface |
| 3 | `TelegramFileClient.kt` | transport-telegram | 163 | File download interface |
| 4 | `TelegramThumbFetcher.kt` | transport-telegram | 75 | Thumbnail interface |
| 5 | `auth/TdlibAuthSession.kt` | transport-telegram | 365 | Auth state machine impl |
| 6 | `chat/TelegramChatBrowser.kt` | transport-telegram | 409 | Chat browser impl |
| 7 | `file/TelegramFileDownloadManager.kt` | transport-telegram | 242 | Download manager impl |
| 8 | `imaging/TelegramThumbFetcherImpl.kt` | transport-telegram | 161 | Thumbnail fetcher impl |
| 9 | `config/TelegramStreamingConfig.kt` | playback/telegram | 182 | Streaming constants |
| 10 | `config/TelegramFileReadyEnsurer.kt` | playback/telegram | 245 | MP4 validation |

**Total Lines of Code:** ~2,026 lines

---

**See also:**
- [Part 1: Overview](TELEGRAM_MIGRATION_SUMMARY_PART1_OVERVIEW.md)
- [Part 2: Interface Definitions](TELEGRAM_MIGRATION_SUMMARY_PART2_INTERFACES.md)
- [Part 3A: Auth + Download Manager](TELEGRAM_MIGRATION_SUMMARY_PART3A_IMPLEMENTATIONS.md)
- [Part 3B: Chat Browser + Thumb Fetcher](TELEGRAM_MIGRATION_SUMMARY_PART3B_IMPLEMENTATIONS.md)
