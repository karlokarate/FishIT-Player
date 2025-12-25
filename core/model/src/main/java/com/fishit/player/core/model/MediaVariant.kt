package com.fishit.player.core.model

/**
 * Represents a specific playback variant of a media item.
 *
 * A single logical media (e.g., "Breaking Bad S01E01") may have multiple variants from different
 * sources (Telegram, Xtream) with different qualities/languages.
 *
 * The [VariantSelector] chooses the best variant based on user preferences.
 *
 * @property sourceKey Unique identifier combining pipeline + source ID
 * @property qualityTag Human-readable quality label (e.g., "SD", "HD", "FHD", "UHD", "CAM")
 * @property resolutionHeight Video height in pixels (e.g., 480, 720, 1080, 2160), null if unknown
 * @property language ISO-639-1 language code (e.g., "de", "en"), null if unknown
 * @property isOmu True if this is Original with subtitles (OmU/OV)
 * @property sourceUrl Direct playback URL for URL-based sources (Xtream), null for file-based
 * (Telegram)
 * @property available False if this variant is known to be dead/unavailable
 */
data class MediaVariant(
    val sourceKey: SourceKey,
    val qualityTag: String,
    val resolutionHeight: Int? = null,
    val language: String? = null,
    val isOmu: Boolean = false,
    val sourceUrl: String? = null,
    var available: Boolean = true,
) {
    /**
     * Human-readable label for UI display.
     *
     * Example: "FHD – de (Xtream)", "HD – en OmU (Telegram)"
     */
    fun toDisplayLabel(): String =
        buildString {
            append(qualityTag)
            language?.let { append(" – $it") }
            if (isOmu) append(" OmU")
            append(" (${sourceKey.pipeline.name.lowercase().replaceFirstChar { it.uppercase() }})")
        }
}

/** Quality tag constants for consistency. */
object QualityTags {
    const val UHD = "UHD" // 2160p / 4K
    const val FHD = "FHD" // 1080p
    const val HD = "HD" // 720p
    const val SD = "SD" // 480p and below
    const val CAM = "CAM" // Camera/theater rip (low quality)
    const val WEB = "WEB" // Web-DL/WEB-Rip
    const val BLURAY = "BluRay"

    /** Derive quality tag from resolution height. */
    fun fromResolutionHeight(height: Int?): String =
        when {
            height == null -> SD
            height >= 2160 -> UHD
            height >= 1080 -> FHD
            height >= 720 -> HD
            else -> SD
        }
}
