package com.fishit.player.core.model.util

/**
 * SSOT for human-readable file size formatting.
 *
 * Consolidates 3 duplicate implementations (MediaSourceRef, SourceBadge, WorkDetailDtos).
 * Uses binary units (1 KB = 1024 bytes) consistent with Android conventions.
 */
object FileSizeFormatter {

    private const val KB = 1024L
    private const val MB = 1024L * 1024L
    private const val GB = 1024L * 1024L * 1024L

    /**
     * Formats byte count as human-readable string.
     *
     * Examples: "1.5 GB", "320 MB", "64 KB", "512 B"
     *
     * @param bytes File size in bytes
     * @return Formatted string
     */
    fun format(bytes: Long): String = when {
        bytes >= GB -> "%.1f GB".format(bytes / GB.toDouble())
        bytes >= MB -> "%.0f MB".format(bytes / MB.toDouble())
        bytes >= KB -> "%d KB".format(bytes / KB)
        else -> "$bytes B"
    }
}
