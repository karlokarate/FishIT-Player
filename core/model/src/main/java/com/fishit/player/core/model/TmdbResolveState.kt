package com.fishit.player.core.model

/**
 * TMDB resolution state for canonical media items.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md T-15:
 * - Tracks the TMDB enrichment status per item
 * - Enables efficient worker batching and cooldown enforcement
 *
 * @see TmdbResolvedBy for how resolved items were identified
 */
enum class TmdbResolveState {
    /**
     * Item has not been processed by TMDB resolver.
     * Initial state for new items.
     */
    UNRESOLVED,

    /**
     * Item has been successfully resolved.
     * - TmdbRef has been set
     * - Details (poster, backdrop, year) have been fetched
     */
    RESOLVED,

    /**
     * Permanently unresolvable (max attempts, no match, marked skip).
     *
     * The canonical item remains in storage, but workers should stop trying to resolve it.
     */
    UNRESOLVABLE_PERMANENT,

    /**
     * Was resolved but needs refresh (stale SSOT).
     */
    STALE_REFRESH_REQUIRED,
}

/**
 * How a canonical media item's TMDB reference was resolved.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md T-16:
 * - Enables tracing the source of TMDB enrichment
 * - Useful for debugging and quality metrics
 *
 * @see TmdbResolveState for the resolution status
 */
enum class TmdbResolvedBy {
    /**
     * TMDB ref passed through from upstream pipeline.
     *
     * Example: Xtream provider includes TMDB ID in metadata,
     * or Telegram Structured Bundle contains TMDB reference.
     */
    PASS_THROUGH,

    /**
     * TMDB ref existed, details fetched by ID.
     *
     * No search was needed, resolver directly fetched
     * movie/show details using the existing ID.
     */
    DETAILS_BY_ID,

    /**
     * Resolved via TMDB search + deterministic scoring.
     *
     * Item had no TMDB ref, resolver searched by title/year
     * and accepted the best match (score ≥85 with gap ≥10).
     */
    SEARCH_MATCH,

    /**
     * Manual override by user (reserved for future).
     *
     * User explicitly selected a TMDB match, overriding
     * any automatic resolution.
     */
    MANUAL_OVERRIDE,
}
