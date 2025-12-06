package com.fishit.player.core.metadata

import com.fishit.player.core.model.NormalizedMediaMetadata

/**
 * Interface for TMDB metadata resolution and enrichment.
 *
 * This service enriches NormalizedMediaMetadata with TMDB data
 * (TMDB IDs, official titles, years, etc.).
 *
 * Phase 3 skeleton: Default implementation is a no-op pass-through.
 * Full TMDB search and enrichment logic comes later.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - If tmdbId already exists: skip search, return input
 * - If no tmdbId: search TMDB using canonicalTitle and year
 * - Enrich with TMDB ID, official titles, refined year
 * - Handle ambiguous matches gracefully (log and/or skip)
 */
interface TmdbMetadataResolver {
    /**
     * Enrich normalized metadata with TMDB data.
     *
     * @param normalized Normalized metadata from MediaMetadataNormalizer
     * @return Enriched metadata with TMDB data (if found)
     */
    suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata
}
