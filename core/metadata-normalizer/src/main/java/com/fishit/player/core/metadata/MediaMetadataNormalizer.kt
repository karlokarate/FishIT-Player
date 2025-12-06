package com.fishit.player.core.metadata

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata

/**
 * Interface for media metadata normalization.
 *
 * This service transforms RawMediaMetadata from pipelines into
 * NormalizedMediaMetadata with cleaned, standardized fields.
 *
 * Phase 3 skeleton: Default implementation is a no-op pass-through.
 * Full normalization logic (title cleaning, scene-naming parser) comes later.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Must be deterministic: same input → same output
 * - Cleans titles (strips technical tags, normalizes whitespace/case/punctuation)
 * - Extracts structural metadata (year, season, episode from scene-style naming)
 * - Does NOT perform TMDB lookups (that's TmdbMetadataResolver's job)
 */
interface MediaMetadataNormalizer {
    /**
     * Normalize raw metadata from a pipeline source.
     *
     * @param raw Raw metadata from pipeline
     * @return Normalized metadata with cleaned fields
     */
    suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata
}
