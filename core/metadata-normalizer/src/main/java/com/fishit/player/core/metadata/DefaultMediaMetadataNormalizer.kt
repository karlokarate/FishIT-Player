package com.fishit.player.core.metadata

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata

/**
 * Default no-op implementation of MediaMetadataNormalizer.
 *
 * Phase 3 skeleton behavior:
 * - Returns the input RawMediaMetadata wrapped as NormalizedMediaMetadata
 * - No title cleaning, no scene-naming parsing
 * - Uses originalTitle as canonicalTitle
 * - Copies all other fields as-is
 *
 * This is intentional – full normalization logic comes in later phases.
 */
class DefaultMediaMetadataNormalizer : MediaMetadataNormalizer {
    override suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata {
        return NormalizedMediaMetadata(
            canonicalTitle = raw.originalTitle, // No cleaning - pass through
            year = raw.year,
            season = raw.season,
            episode = raw.episode,
            tmdbId = raw.externalIds.tmdbId, // Pass through if provided by source
            externalIds = raw.externalIds,
        )
    }
}
