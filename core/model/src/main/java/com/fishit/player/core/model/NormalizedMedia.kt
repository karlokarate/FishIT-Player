package com.fishit.player.core.model

import com.fishit.player.core.model.ids.CanonicalId
import com.fishit.player.core.model.ids.PipelineItemId

/**
 * Normalized media representation after cross-pipeline merge.
 *
 * Represents a single logical media item (movie, episode, etc.) that may have multiple playback
 * [variants] from different sources.
 *
 * **Invariants:**
 * - [variants] is NEVER empty
 * - [primaryPipelineIdTag] + [primarySourceId] always match the currently best variant
 * - [canonicalId] is shared across all variants when present (enabling cross-pipeline
 *   deduplication). It is null for unlinked media (e.g., LIVE or insufficient metadata).
 *
 * **Example:** "Breaking Bad S01E01" might have:
 * - Variant 1: Telegram FHD German
 * - Variant 2: Xtream HD German
 * - Variant 3: Telegram SD English OmU
 *
 * @property canonicalId Canonical ID shared across pipelines (tmdb:<id> or movie:/episode:
 * fallback). Null for unlinked items.
 * @property title Normalized/cleaned title for display
 * @property year Release year if known
 * @property mediaType Content type (MOVIE, SERIES_EPISODE, etc.)
 * @property primaryPipelineIdTag Pipeline of the currently best/selected variant
 * @property primarySourceId Source ID of the currently best/selected variant
 * @property variants All available playback variants, ordered by preference (best first)
 */
data class NormalizedMedia(
        val canonicalId: CanonicalId?,
        val title: String,
        val year: Int?,
        val mediaType: MediaType,
        val primaryPipelineIdTag: PipelineIdTag,
        val primarySourceId: PipelineItemId,
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
