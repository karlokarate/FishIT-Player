package com.fishit.player.playback.telegram.config

import com.fishit.player.playback.domain.mp4.Mp4MoovValidationConfig

/**
 * TDLib-Optimized Streaming Configuration for Video Playback (v2 Architecture).
 *
 * This is the **single source of truth** for **Telegram-specific** streaming constants. Ported from
 * legacy `StreamingConfigRefactor` with v2 compliance.
 *
 * **Note:** MP4 moov atom validation constants are defined in [Mp4MoovValidationConfig] (shared
 * across all playback sources).
 *
 * **TDLib Streaming Architecture (Official Best Practices):**
 *
 * 1. **Progressive Download with isStreamable=true:**
 * ```
 *    - TDLib manages internal buffering and prefix caching
 *    - App calls `downloadFile(fileId, priority=32, offset=0, limit=0)` for full progressive download
 *    - TDLib automatically detects streamable MP4/MOV files (moov atom at start)
 *    - No manual windowing or chunk management needed
 * ```
 * 2. **Prefix Size Management:**
 * ```
 *    - TDLib downloads in chunks and updates `file.local.downloaded_prefix_size`
 *    - App polls this value to determine when playback can start
 *    - Minimum prefix must contain complete MP4 moov atom (metadata)
 * ```
 * 3. **Header Validation (Critical):**
 * ```
 *    - Use [Mp4MoovAtomValidator.checkMoovAtom] before starting playback
 *    - Wait until: downloaded_prefix_size >= MIN_PREFIX_BYTES AND moov atom complete
 *    - This prevents ExoPlayer initialization errors with incomplete metadata
 * ```
 * 4. **Priority Levels (TDLib Standard):**
 * ```
 *    - Priority 32: Active streaming (high priority, prevents interruption)
 *    - Priority 16: Background prefetch (medium priority)
 *    - Priority 1-8: Low priority background downloads
 * ```
 * 5. **Zero-Copy Streaming:**
 * ```
 *    - TDLib caches files on disk (unavoidable but efficient)
 *    - App uses FileDataSource to read directly from TDLib cache
 *    - No in-memory buffers or additional copies
 *    - ExoPlayer handles all seek/scrub operations via FileDataSource
 *
 * @see contracts
 * ```
 * /TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 * @see TelegramFileReadyEnsurer for playback readiness logic
 * @see Mp4MoovValidationConfig for shared MP4 validation constants
 */
@Suppress("unused") // Constants referenced by TelegramFileReadyEnsurer
object TelegramStreamingConfig {

    // ========== MP4 Header Validation (delegated to shared config) ==========

    /**
     * Minimum prefix size before attempting header validation (64 KB).
     * @see Mp4MoovValidationConfig.MIN_PREFIX_FOR_VALIDATION_BYTES
     */
    val MIN_PREFIX_FOR_VALIDATION_BYTES: Long
        get() = Mp4MoovValidationConfig.MIN_PREFIX_FOR_VALIDATION_BYTES

    /**
     * Maximum prefix size to scan for moov atom (2 MB).
     * @see Mp4MoovValidationConfig.MAX_PREFIX_SCAN_BYTES
     */
    val MAX_PREFIX_SCAN_BYTES: Long
        get() = Mp4MoovValidationConfig.MAX_PREFIX_SCAN_BYTES

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

    /** Download priority for background prefetch (TDLib standard: 16). */
    const val DOWNLOAD_PRIORITY_BACKGROUND: Int = 16

    /** Download priority for thumbnail prefetch. */
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
     * Balance between responsiveness and CPU usage. TDLib updates prefix size incrementally as
     * chunks arrive.
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
     * Timeout for moov atom completeness check.
     * @see Mp4MoovValidationConfig.MOOV_VALIDATION_TIMEOUT_MS
     */
    val MOOV_VALIDATION_TIMEOUT_MS: Long
        get() = Mp4MoovValidationConfig.MOOV_VALIDATION_TIMEOUT_MS

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
     * Reduces log spam during active downloads. Progress updates still occur via Flow but logs are
     * throttled.
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
