package com.fishit.player.core.model.util

/**
 * SSOT for video resolution → human-readable label mapping.
 *
 * Eliminates 5+ duplicate implementations of the height→label pattern.
 *
 * **Label variants:**
 * - [fromHeight]: Standard labels ("4K", "1080p", "720p", "480p", "SD") — for data/domain layers
 * - [badgeLabel]: Compact badge labels ("4K", "FHD", "HD") — for UI overlays
 */
object ResolutionLabel {

    /**
     * Standard resolution label from pixel height.
     *
     * @param height Video height in pixels (e.g., 2160, 1080, 720)
     * @return Label string, or null if height is null/zero
     */
    fun fromHeight(height: Int?): String? = when {
        height == null || height <= 0 -> null
        height >= 2160 -> "4K"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        else -> "SD"
    }

    /**
     * Compact badge label for UI thumbnail overlays.
     *
     * Uses abbreviated labels (FHD, HD) suitable for small badge display.
     * Returns null for low resolutions that don't merit a badge.
     *
     * @param height Video height in pixels
     * @return Badge label, or null if not badge-worthy
     */
    fun badgeLabel(height: Int?): String? = when {
        height == null || height <= 0 -> null
        height >= 2160 -> "4K"
        height >= 1080 -> "FHD"
        height >= 720 -> "HD"
        else -> null
    }
}
