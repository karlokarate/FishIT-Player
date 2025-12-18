package com.fishit.player.core.metadata.tmdb

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.ids.TmdbId

/**
 * Result of TMDB metadata resolution.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md Section 4.1.
 */
sealed interface TmdbResolutionResult {
    /**
     * TMDB match found and accepted.
     *
     * @property tmdbId Matched TMDB ID
     * @property canonicalTitle Official TMDB title
     * @property year Release year from TMDB
     * @property poster TMDB poster image (SSOT when present)
     * @property backdrop TMDB backdrop image (SSOT when present)
     * @property externalIds External IDs from TMDB (IMDB, TVDB, etc.)
     */
    data class Success(
        val tmdbId: TmdbId,
        val canonicalTitle: String,
        val year: Int?,
        val poster: ImageRef.Http?,
        val backdrop: ImageRef.Http?,
        val externalIds: ExternalIds
    ) : TmdbResolutionResult

    /**
     * No matching TMDB entry found.
     *
     * @property reason Human-readable reason for debugging
     */
    data class NotFound(val reason: String) : TmdbResolutionResult

    /**
     * Multiple ambiguous matches found (no clear winner).
     *
     * @property reason Human-readable reason
     * @property candidates Top candidates for manual review
     */
    data class Ambiguous(
        val reason: String,
        val candidates: List<TmdbCandidate>
    ) : TmdbResolutionResult

    /**
     * TMDB resolver is disabled (apiKey missing/blank).
     */
    data object Disabled : TmdbResolutionResult

    /**
     * TMDB API call failed (network error, rate limit, etc.).
     *
     * @property error Exception that caused the failure
     */
    data class Failed(val error: Throwable) : TmdbResolutionResult
}

/**
 * TMDB search candidate for ambiguous results.
 *
 * @property tmdbId TMDB ID
 * @property title TMDB title
 * @property year Release year
 * @property score Match score (0..100)
 */
data class TmdbCandidate(
    val tmdbId: TmdbId,
    val title: String,
    val year: Int?,
    val score: Int
)
