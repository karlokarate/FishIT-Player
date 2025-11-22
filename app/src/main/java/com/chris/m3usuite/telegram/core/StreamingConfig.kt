package com.chris.m3usuite.telegram.core

/**
 * Windowing configuration for Zero-Copy Streaming.
 *
 * **Windowed Zero-Copy Streaming** means:
 * - TDLib continues to cache media files on disk (unavoidable)
 * - Only a window of the file around the current playback position is downloaded
 * - When seeking, old windows are discarded and new windows are opened at the target position
 * - `readFileChunk()` writes directly from the TDLib cache into the player buffer (zero-copy at the app layer)
 *
 * **Window size rationale:**
 * - For large files (e.g., 4GB for a 90min movie), the window must be large enough
 * - 16MB window allows about 1-2 minutes of buffer at ~8 Mbit/s bitrate
 * - 4MB prefetch margin triggers timely reloading before the end of the window
 * - These values prevent stuttering and excessive reloading
 *
 * **Applies only to:**
 * - MediaKind.MOVIE
 * - MediaKind.EPISODE
 * - MediaKind.CLIP
 * - MediaKind.AUDIO (if available)
 *
 * **NOT for RAR_ARCHIVE** - these use full download.
 */
object StreamingConfig {
    /**
     * Window size for streaming (16 MB).
     * Sufficient for smooth playback of typical HD videos.
     */
    const val TELEGRAM_STREAM_WINDOW_BYTES = 16 * 1024 * 1024L

    /**
     * Prefetch margin (4 MB).
     * When the read position falls below this distance to the end of the window,
     * the next window is prepared.
     */
    const val TELEGRAM_STREAM_PREFETCH_MARGIN = 4 * 1024 * 1024L

    /**
     * Timeout for window transition operations (30 seconds).
     * Prevents indefinite blocking during window setup failures.
     */
    const val WINDOW_TRANSITION_TIMEOUT_MS = 30_000L

    /**
     * Timeout for read operations (10 seconds).
     * Prevents indefinite blocking during file read operations.
     */
    const val READ_OPERATION_TIMEOUT_MS = 10_000L

    /**
     * Maximum read attempts including initial attempt (3 attempts total).
     * With 3 attempts, there are 2 retries after the initial attempt.
     * Handles race conditions where file handles may be closed by another thread.
     */
    const val MAX_READ_ATTEMPTS = 3

    /**
     * @deprecated Legacy ringbuffer configuration - no longer used.
     * TelegramFileDataSource now uses FileDataSource directly without in-memory caching.
     * Kept for reference only.
     */
    @Deprecated("Legacy ringbuffer - no longer used", ReplaceWith(""))
    const val RINGBUFFER_CHUNK_SIZE_BYTES = 256 * 1024

    /**
     * @deprecated Legacy ringbuffer configuration - no longer used.
     * TelegramFileDataSource now uses FileDataSource directly without in-memory caching.
     * Kept for reference only.
     */
    @Deprecated("Legacy ringbuffer - no longer used", ReplaceWith(""))
    const val RINGBUFFER_MAX_CHUNKS = 64

    /**
     * Maximum retry attempts for waiting on chunk download (200 attempts).
     * With 15ms delay per attempt, this allows up to 3 seconds wait time.
     * Prevents immediate IOException when TDLib is still downloading the first bytes.
     */
    const val READ_RETRY_MAX_ATTEMPTS = 200

    /**
     * Delay between retry attempts in milliseconds (15ms).
     * Short enough for responsive playback start, long enough to avoid busy-waiting.
     */
    const val READ_RETRY_DELAY_MS = 15L
}
