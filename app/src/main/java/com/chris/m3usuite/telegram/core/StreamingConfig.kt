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
     * Chunk size for in-memory ringbuffer (256 KB).
     * Balances memory efficiency with reduced fragmentation.
     * Each chunk can hold approximately 2 seconds of HD video at 1 Mbit/s.
     */
    const val RINGBUFFER_CHUNK_SIZE_BYTES = 256 * 1024

    /**
     * Maximum number of chunks in ringbuffer (64 chunks).
     * With 256 KB per chunk, this allows ~16 MB total ringbuffer capacity.
     * LRU eviction ensures old chunks are discarded when limit is reached.
     */
    const val RINGBUFFER_MAX_CHUNKS = 64
}
