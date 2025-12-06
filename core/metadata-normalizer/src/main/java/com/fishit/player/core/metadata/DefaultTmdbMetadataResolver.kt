package com.fishit.player.core.metadata

import com.fishit.player.core.model.NormalizedMediaMetadata

/**
 * Default no-op implementation of TmdbMetadataResolver.
 *
 * Phase 3 skeleton behavior:
 * - Returns the input NormalizedMediaMetadata unmodified
 * - No TMDB API calls
 * - No enrichment
 *
 * This is intentional – full TMDB search and enrichment logic comes in later phases.
 */
class DefaultTmdbMetadataResolver : TmdbMetadataResolver {
    override suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata {
        // No-op: return input unmodified
        return normalized
    }
}
