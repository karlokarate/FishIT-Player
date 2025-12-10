package com.fishit.player.core.model

/**
 * Normalized media representation after cross-pipeline merge.
 *
 * Represents a single logical media item (movie, episode, etc.) that may have multiple playback
 * [variants] from different sources.
 *
 * **Invariants:**
 * - [variants] is NEVER empty
 * - [primaryPipelineIdTag] + [primarySourceId] always match the currently best variant
 * - [globalId] is shared across all variants (enabling cross-pipeline deduplication)
 *
 * **Example:** "Breaking Bad S01E01" might have:
 * - Variant 1: Telegram FHD German
 * - Variant 2: Xtream HD German
 * - Variant 3: Telegram SD English OmU
 *
 * @property globalId Canonical ID shared across pipelines (format: "cm:<16-char-hex>")
 * @property title Normalized/cleaned title for display
 * @property year Release year if known
 * @property mediaType Content type (MOVIE, SERIES_EPISODE, etc.)
 * @property primaryPipelineIdTag Pipeline of the currently best/selected variant
 * @property primarySourceId Source ID of the currently best/selected variant
 * @property variants All available playback variants, ordered by preference (best first)
 */
data class NormalizedMedia(
        val globalId: String,
        val title: String,
        val year: Int?,
        val mediaType: MediaType,
        val primaryPipelineIdTag: PipelineIdTag,
        val primarySourceId: String,
        val variants: MutableList<MediaVariant>,
) {
    init {
        require(variants.isNotEmpty()) { "NormalizedMedia must have at least one variant" }
    }

    /** Get the primary (best) variant. */
    val primaryVariant: MediaVariant
        get() = variants.first()

    /** Get the SourceKey for the primary variant. */
    val primarySourceKey: SourceKey
        get() = SourceKey(primaryPipelineIdTag, primarySourceId)

    /** Check if this media has multiple playback options. */
    val hasMultipleVariants: Boolean
        get() = variants.size > 1

    /** Get only available variants. */
    val availableVariants: List<MediaVariant>
        get() = variants.filter { it.available }

    /**
     * Update the primary variant after resorting.
     *
     * Call this after sorting variants with [VariantSelector] to keep primary* fields in sync.
     */
    fun syncPrimaryFromVariants(): NormalizedMedia {
        val best = variants.firstOrNull { it.available } ?: variants.first()
        return copy(
                primaryPipelineIdTag = best.sourceKey.pipeline,
                primarySourceId = best.sourceKey.sourceId,
        )
    }
}
