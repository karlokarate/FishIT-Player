package com.fishit.player.playback.telegram.config

/**
 * Represents the playback mode for Telegram video files.
 *
 * Telegram video files can be played in two different modes depending on their container format
 * and structure:
 * - **PROGRESSIVE_FILE**: MP4/MOV files with moov atom at the start (faststart-optimized)
 * - **FULL_FILE**: MKV files or MP4 without early moov (requires full download before playback)
 *
 * **Design Philosophy (Platinum Playback):**
 * - "moov not found" is NEVER a fatal error
 * - Files without progressive support simply take longer to start (full download required)
 * - All Telegram video files eventually become playable once downloaded
 *
 * @see TelegramFileReadyEnsurer for playback readiness orchestration
 */
enum class TelegramPlaybackMode {
    /**
     * Progressive file playback (MP4/MOV with moov atom at start).
     *
     * **Requirements:**
     * - MP4 or MOV container
     * - moov atom found within MIN_PREFIX_FOR_VALIDATION_BYTES
     * - moov atom complete before playback starts
     *
     * **Start Time:** Fast (typically < 5 seconds)
     *
     * **Use Case:**
     * - Most web-optimized MP4 files
     * - Files encoded with `-movflags faststart`
     */
    PROGRESSIVE_FILE,

    /**
     * Full file playback (MKV or MP4 without early moov).
     *
     * **Requirements:**
     * - File fully downloaded (isDownloadingCompleted == true)
     * - Local file path accessible
     *
     * **Start Time:** Depends on file size and network speed
     *
     * **Use Cases:**
     * - MKV files (no moov atom concept)
     * - MP4 files with moov at end (not faststart-optimized)
     * - MP4 files with very large moov atoms (> MAX_PREFIX_SCAN_BYTES)
     */
    FULL_FILE;

    /**
     * Returns true if this mode supports progressive streaming (playback before full download).
     */
    val supportsProgressive: Boolean
        get() = this == PROGRESSIVE_FILE

    /**
     * Returns true if this mode requires full download before playback.
     */
    val requiresFullDownload: Boolean
        get() = this == FULL_FILE
}
