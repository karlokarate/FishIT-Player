package com.fishit.player.core.metadata

import com.fishit.player.core.metadata.tmdb.TmdbConfig
import com.fishit.player.core.metadata.tmdb.TmdbResolutionResult
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TMDB metadata resolver implementation.
 *
 * NOTE: This is a skeleton implementation pending resolution of app.moviebase:tmdb-api-jvm API access.
 * The library's internal constructor prevents direct instantiation in the current version.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md:
 * - Path A (tmdbId present): Fetch details by ID
 * - Path B (tmdbId missing): Search + deterministic scoring
 * - Bounded in-memory LRU caches (256 entries, TTL-based)
 * - Logging via UnifiedLog only (no secrets)
 *
 * TODO: Complete TMDB client integration once library API access is resolved.
 * Current implementation returns Disabled to allow compilation and testing of other components.
 */
@Singleton
class DefaultTmdbMetadataResolver
    @Inject
    constructor(
        private val config: TmdbConfig,
    ) : TmdbMetadataResolver {
        override suspend fun resolve(raw: RawMediaMetadata): TmdbResolutionResult {
            // Check if TMDB is enabled
            if (!config.isEnabled) {
                UnifiedLog.d(TAG) { "TMDB resolver disabled (no API key)" }
                return TmdbResolutionResult.Disabled
            }

            // TODO: Implement TMDB API integration once library API is accessible
            // Currently blocked by internal constructor in app.moviebase:tmdb-api-jvm:1.6.0
            UnifiedLog.w(TAG) {
                "TMDB resolver skeleton: returning Disabled (API integration pending)"
            }

            return TmdbResolutionResult.Disabled
        }

        companion object {
            private const val TAG = "core/metadata-normalizer/DefaultTmdbMetadataResolver"
        }
    }
