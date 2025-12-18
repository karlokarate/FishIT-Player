package com.fishit.player.core.metadata

import com.fishit.player.core.metadata.tmdb.TmdbResolutionResult
import com.fishit.player.core.model.RawMediaMetadata

/**
 * Interface for TMDB metadata resolution and enrichment.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md:
 * - TMDB is enrichment-only (resolver-only; never in pipelines)
 * - Canonical key preference: tmdb:<tmdbId> when available
 * - Race-free image rule: TMDB images are SSOT only when explicitly populated
 * - Upgrade-only: source → TMDB; never automatic reversion
 *
 * Two resolution paths:
 * - Path A (tmdbId present): Fetch details by ID
 * - Path B (tmdbId missing): Search deterministically + score candidates
 */
interface TmdbMetadataResolver {
    /**
     * Resolve TMDB metadata for a raw media item.
     *
     * @param raw Raw metadata from pipeline
     * @return Resolution result (Success, NotFound, Ambiguous, Disabled, or Failed)
     */
    suspend fun resolve(raw: RawMediaMetadata): TmdbResolutionResult
}
