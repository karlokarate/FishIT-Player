package com.fishit.player.playback.telegram.config

/**
 * Detects the appropriate playback mode for Telegram video files based on MIME type and container.
 *
 * **Design Principle (Platinum Playback):**
 * - Prefer PROGRESSIVE_FILE when possible (faster start)
 * - Fall back to FULL_FILE when progressive not supported (MKV) or moov not found (MP4)
 * - NEVER treat "moov not found" as fatal - just means FULL_FILE mode required
 *
 * **Container Support:**
 * - **MP4/MOV:** Check for moov atom (PROGRESSIVE_FILE if found, FULL_FILE if not)
 * - **MKV/WEBM:** Always use FULL_FILE (no moov atom concept)
 * - **Unknown:** Assume MP4 and try progressive first, fall back to FULL_FILE
 *
 * @see TelegramPlaybackMode for mode definitions
 */
object TelegramPlaybackModeDetector {

    /**
     * Determines if a MIME type indicates an MP4-like container that supports moov validation.
     *
     * MP4-like containers:
     * - video/mp4
     * - video/quicktime (MOV)
     * - video/x-m4v
     *
     * @param mimeType MIME type from file metadata (e.g., "video/mp4", "video/x-matroska")
     * @return true if this is an MP4-like container that may have a moov atom
     */
    fun isMp4Container(mimeType: String?): Boolean {
        if (mimeType == null) return true // Default to MP4 assumption for unknown types

        val normalizedMime = mimeType.lowercase()
        return normalizedMime == "video/mp4" ||
            normalizedMime == "video/quicktime" ||
            normalizedMime == "video/x-m4v" ||
            normalizedMime.contains("mp4")
    }

    /**
     * Determines if a MIME type indicates a container that does NOT support progressive playback.
     *
     * Non-progressive containers:
     * - video/x-matroska (MKV)
     * - video/webm (WebM - based on Matroska)
     * - video/avi (AVI - interleaved but not progressive)
     *
     * @param mimeType MIME type from file metadata
     * @return true if this container requires full download before playback
     */
    fun requiresFullDownload(mimeType: String?): Boolean {
        if (mimeType == null) return false // Unknown types try progressive first

        val normalizedMime = mimeType.lowercase()
        return normalizedMime == "video/x-matroska" ||
            normalizedMime == "video/matroska" ||
            normalizedMime == "video/webm" ||
            normalizedMime == "video/avi" ||
            normalizedMime == "video/x-msvideo" ||
            normalizedMime.contains("matroska") ||
            normalizedMime.contains("mkv")
    }

    /**
     * Selects the initial playback mode based on MIME type.
     *
     * **Decision Tree:**
     * 1. If container requires full download (MKV, WebM, AVI) → FULL_FILE
     * 2. If MP4-like container → Try PROGRESSIVE_FILE first (will check moov)
     * 3. If unknown → Try PROGRESSIVE_FILE first
     *
     * **Note:** PROGRESSIVE_FILE may be downgraded to FULL_FILE during moov validation if:
     * - moov atom not found within MAX_PREFIX_SCAN_BYTES
     * - moov atom incomplete after timeout
     *
     * @param mimeType MIME type from file metadata (null = unknown, assumes MP4)
     * @return Initial playback mode to attempt
     */
    fun selectInitialMode(mimeType: String?): TelegramPlaybackMode {
        return if (requiresFullDownload(mimeType)) {
            TelegramPlaybackMode.FULL_FILE
        } else {
            TelegramPlaybackMode.PROGRESSIVE_FILE
        }
    }

    /**
     * Returns a human-readable description of the playback mode decision.
     *
     * Useful for logging and diagnostics.
     *
     * @param mimeType MIME type from file metadata
     * @return Description string (e.g., "MKV container, requires full download")
     */
    fun describeMode(mimeType: String?): String {
        return when {
            requiresFullDownload(mimeType) -> {
                val containerName = when {
                    mimeType?.contains("matroska") == true || mimeType?.contains("mkv") == true -> "MKV"
                    mimeType?.contains("webm") == true -> "WebM"
                    mimeType?.contains("avi") == true -> "AVI"
                    else -> "Non-progressive"
                }
                "$containerName container, requires full download"
            }
            isMp4Container(mimeType) -> {
                "MP4-like container, attempting progressive playback"
            }
            else -> {
                "Unknown container (mime=$mimeType), attempting progressive playback"
            }
        }
    }
}
