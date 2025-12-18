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
 *
 * ## Future Implementation Requirements (Phase 2.4.7 Normalizer Optimization)
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md Section 2.3 (Normalizer Shortcut):
 *
 * **MANDATORY SHORTCUT RULE:**
 * When `externalIds.tmdbId` is already present (from Structured Bundles):
 * 1. SKIP TMDB search entirely (zero API calls)
 * 2. Return input unmodified (pass-through)
 * 3. Log the shortcut for debugging
 *
 * Example future implementation:
 * ```kotlin
 * override suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata {
 *     // SHORTCUT: Skip search if tmdbId already present
 *     if (normalized.tmdbId != null) {
 *         UnifiedLog.d(TAG) { "TMDB shortcut: tmdbId=${normalized.tmdbId} already present" }
 *         return normalized
 *     }
 *
 *     // Full TMDB search logic here...
 * }
 * ```
 *
 * This optimization enables:
 * - **Zero-API-Call Path:** Structured chats → RawMediaMetadata.externalIds.tmdbId → skip TMDB
 * - **Kids Filter:** FSK values from Structured Bundles work without any TMDB lookup
 * - **Performance:** No network latency for pre-enriched content
 */
class DefaultTmdbMetadataResolver : TmdbMetadataResolver {
    override suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata {
        // No-op: return input unmodified
        // Future: implement TMDB search with shortcut for existing tmdbId
        return normalized
    }
}
