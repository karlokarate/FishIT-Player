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
 * **Episode Facts (Gold Decision Dec 2025):**
 * - [season] and [episode] are preserved as separate fields (never parse from canonicalId)
 * - For episodes with TMDB: canonicalId = "tmdb:tv:{seriesId}", use season/episode for API calls
 * - TMDB Episode API: GET /tv/{id}/season/{season}/episode/{episode}
 *
 * **Example:** "Breaking Bad S01E01" might have:
 * - Variant 1: Telegram FHD German
 * - Variant 2: Xtream HD German
 * - Variant 3: Telegram SD English OmU
 *
 * @property canonicalId Canonical ID shared across pipelines. Format:
 *   - TMDB movie: "tmdb:movie:{id}"
 *   - TMDB TV/episode: "tmdb:tv:{id}" (use season/episode for episode specifics)
 *   - Fallback movie: "movie:{slug}[:{year}]"
 *   - Fallback episode: "episode:{slug}:SxxExx"
 *   Null for unlinked items (LIVE, insufficient metadata).
 * @property title Normalized/cleaned title for display
 * @property year Release year if known
 * @property mediaType Content type (MOVIE, SERIES_EPISODE, etc.)
 * @property season Season number for episodes. Null for movies/non-episodic content.
 * @property episode Episode number for episodes. Null for movies/non-episodic content.
 * @property tmdb Typed TMDB reference for direct API calls. Null if no TMDB identity.
 * @property primaryPipelineIdTag Pipeline of the currently best/selected variant
 * @property primarySourceId Source ID of the currently best/selected variant
 * @property variants All available playback variants, ordered by preference (best first)
 */
data class NormalizedMedia(
    val canonicalId: CanonicalId?,
    val title: String,
    val year: Int?,
    val mediaType: MediaType,
    /**
     * Season number for episodes.
     *
     * For TMDB episodes, combine with [tmdb] (TV type) and [episode] for API calls:
     * GET /tv/{tmdb.id}/season/{season}/episode/{episode}
     *
     * Null for movies and non-episodic content.
     */
    val season: Int? = null,
    /**
     * Episode number for episodes.
     *
     * For TMDB episodes, combine with [tmdb] (TV type) and [season] for API calls:
     * GET /tv/{tmdb.id}/season/{season}/episode/{episode}
     *
     * Null for movies and non-episodic content.
     */
    val episode: Int? = null,
    /**
     * Typed TMDB reference for direct API calls.
     *
     * - For movies: TmdbRef(MOVIE, movieId) → GET /movie/{id}
     * - For TV shows: TmdbRef(TV, seriesId) → GET /tv/{id}
     * - For episodes: TmdbRef(TV, seriesId) + season/episode → GET /tv/{id}/season/{s}/episode/{e}
     *
     * Null if no TMDB identity is available.
     */
    val tmdb: TmdbRef? = null,
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
     * Check if this is an episode with complete episode facts.
     *
     * True when mediaType is SERIES_EPISODE and both season and episode are present.
     */
    val hasCompleteEpisodeFacts: Boolean
        get() = mediaType == MediaType.SERIES_EPISODE && season != null && episode != null

    /**
     * Check if this has a TMDB identity that can be used for API calls.
     *
     * For episodes, also checks that season/episode are present for episode-level API calls.
     */
    val hasTmdbIdentity: Boolean
        get() = tmdb != null

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
