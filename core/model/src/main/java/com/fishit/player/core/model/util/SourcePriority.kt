package com.fishit.player.core.model.util

/**
 * SSOT for source selection priority calculation.
 *
 * Priority determines which source is auto-selected when multiple are available.
 * Higher priority = preferred. Used across:
 * - Catalog sync (base priority only — quality not yet known)
 * - Detail view (full priority with quality + bonuses)
 * - Source picker (sorting order)
 *
 * **Ranking rationale:**
 * - LOCAL highest: No network latency, always available
 * - XTREAM: Reliable streaming infrastructure, structured metadata
 * - PLEX: Local network streaming, slightly less structured than Xtream
 * - IO: Generic I/O sources (SAF, SMB, etc.)
 * - TELEGRAM: Requires download/buffering, variable quality
 * - AUDIOBOOK: Specialized niche format
 */
object SourcePriority {
    /**
     * Base priority by source type string (entity storage format).
     *
     * Used at both sync time (base only) and read time (as component of total).
     */
    fun basePriority(sourceType: String): Int =
        when (sourceType.lowercase()) {
            "local" -> 100
            "xtream" -> 80
            "plex" -> 70
            "io" -> 50
            "telegram" -> 40
            "audiobook" -> 30
            else -> 10
        }

    /**
     * Quality bonus from quality tag string.
     *
     * @param qualityTag e.g., "4k", "1080p", "720p", "480p", "source"
     * @return Bonus points (0–50)
     */
    fun qualityBonus(qualityTag: String?): Int =
        when (qualityTag?.lowercase()) {
            "4k", "2160p", "uhd" -> 50
            "1080p", "fhd" -> 40
            "720p", "hd" -> 30
            "480p", "sd" -> 20
            "source" -> 25 // Slightly lower than explicit qualities
            else -> if (qualityTag != null) 10 else 0
        }

    /**
     * Quality bonus from pixel height.
     *
     * @param height Video height in pixels (e.g., 1080, 2160)
     * @return Bonus points (0–50)
     */
    fun qualityBonusFromHeight(height: Int?): Int =
        when {
            height == null || height <= 0 -> 0
            height >= 2160 -> 50
            height >= 1080 -> 40
            height >= 720 -> 30
            height >= 480 -> 20
            else -> 10
        }

    /**
     * Full priority calculation for source/variant auto-selection.
     *
     * @param sourceType Source type string (e.g., "xtream", "telegram")
     * @param qualityTag Optional quality tag (e.g., "1080p", "4k")
     * @param hasDirectUrl Whether a direct playback URL is available
     * @param isExplicitVariant Whether this is an explicit variant (vs. default)
     * @return Total priority score
     */
    fun totalPriority(
        sourceType: String,
        qualityTag: String? = null,
        hasDirectUrl: Boolean = false,
        isExplicitVariant: Boolean = false,
    ): Int {
        var p = basePriority(sourceType)
        if (qualityTag != null) p += qualityBonus(qualityTag)
        if (hasDirectUrl) p += 10
        if (isExplicitVariant) p += 5
        return p
    }
}
