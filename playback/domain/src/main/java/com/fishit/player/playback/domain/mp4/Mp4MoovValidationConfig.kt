package com.fishit.player.playback.domain.mp4

/**
 * Configuration constants for MP4 moov atom validation.
 *
 * These constants are **shared** across all playback sources (Telegram, Xtream, Local, etc.) that
 * require MP4 header validation before playback start.
 *
 * **MP4 Streaming Architecture:**
 * - Progressive downloads require moov atom before playback can start
 * - ExoPlayer needs complete moov to parse codec info, duration, tracks
 * - If moov is incomplete or missing, playback will fail
 *
 * @see Mp4MoovAtomValidator for the validation implementation
 */
object Mp4MoovValidationConfig {

    /**
     * Minimum prefix size before attempting moov validation (64 KB).
     *
     * Most MP4 files have ftyp+moov within the first 64-512 KB. This is a soft threshold - we check
     * moov completeness, not just byte count.
     */
    const val MIN_PREFIX_FOR_VALIDATION_BYTES: Long = 64 * 1024 // 64 KiB

    /**
     * Maximum prefix size to scan for moov atom (2 MB).
     *
     * If moov atom not found after 2 MB, the file is likely:
     * - Not optimized for streaming (moov at end)
     * - Corrupted or invalid MP4
     * - Requires full download before playback
     */
    const val MAX_PREFIX_SCAN_BYTES: Long = 2 * 1024 * 1024 // 2 MiB

    /**
     * Timeout for moov atom completeness check (5 seconds).
     *
     * Once moov is detected but incomplete, wait up to this timeout for more bytes to arrive. If
     * still incomplete, assume non-streamable file.
     */
    const val MOOV_VALIDATION_TIMEOUT_MS: Long = 5_000L
}
