package com.fishit.player.core.metadata

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.ids.TmdbId
import com.fishit.player.infra.transport.tmdb.api.TmdbError
import com.fishit.player.infra.transport.tmdb.api.TmdbGateway
import com.fishit.player.infra.transport.tmdb.api.TmdbRequestParams
import com.fishit.player.infra.transport.tmdb.api.TmdbResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TMDB metadata resolver using the infra gateway.
 *
 * Contract Compliance:
 * - ID-first: Only enriches when tmdbId is already present
 * - No search: Does not perform title-based TMDB searches
 * - Type safety: Uses gateway API, no tmdb-java types leak here
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - If tmdbId exists: fetch details and enrich
 * - If no tmdbId: return input unmodified (no search)
 *
 * Future Enhancement:
 * - Search capability can be added as a separate higher-level service
 * - That service would handle policy decisions (when to search, how to match)
 * - This resolver remains ID-first only
 */
@Singleton
class DefaultTmdbMetadataResolver
    @Inject
    constructor(
        private val tmdbGateway: TmdbGateway,
        private val defaultParams: TmdbRequestParams,
    ) : TmdbMetadataResolver {
        override suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata {
            // ID-first contract: Only enrich if tmdbId is already present
            val tmdbId = normalized.externalIds.tmdbId ?: return normalized

            // Determine if this is a movie or TV show based on season/episode presence
            val isTvShow = normalized.season != null || normalized.episode != null

            return if (isTvShow) {
                enrichFromTvDetails(normalized, tmdbId)
            } else {
                enrichFromMovieDetails(normalized, tmdbId)
            }
        }

        /**
         * Enrich metadata from TMDB movie details.
         */
        private suspend fun enrichFromMovieDetails(
            normalized: NormalizedMediaMetadata,
            tmdbId: TmdbId,
        ): NormalizedMediaMetadata {
            when (val result = tmdbGateway.getMovieDetails(tmdbId.value, defaultParams)) {
                is TmdbResult.Ok -> {
                    val movie = result.value
                    return normalized.copy(
                        canonicalTitle = movie.title,
                        year = movie.releaseDate?.take(4)?.toIntOrNull() ?: normalized.year,
                        // Note: Full enrichment (images, genres, etc.) would be added here
                        // For now, just canonical title and year
                    )
                }
                is TmdbResult.Err -> {
                    // Log error but return original normalized data
                    logTmdbError("getMovieDetails", tmdbId.value, result.error)
                    return normalized
                }
            }
        }

        /**
         * Enrich metadata from TMDB TV details.
         */
        private suspend fun enrichFromTvDetails(
            normalized: NormalizedMediaMetadata,
            tmdbId: TmdbId,
        ): NormalizedMediaMetadata {
            when (val result = tmdbGateway.getTvDetails(tmdbId.value, defaultParams)) {
                is TmdbResult.Err -> {
                    logTmdbError("getTvDetails", tmdbId.value, result.error)
                    return normalized
                }
                is TmdbResult.Ok -> {
                    val tv = result.value
                    return normalized.copy(
                        canonicalTitle = tv.name,
                        year = tv.firstAirDate?.take(4)?.toIntOrNull() ?: normalized.year,
                        // Note: Season/episode validation would be added here
                    )
                }
            }
        }

        /**
         * Log TMDB errors (structured logging would use UnifiedLog in production).
         */
        private fun logTmdbError(
            operation: String,
            tmdbId: Int,
            error: TmdbError,
        ) {
            val message =
                when (error) {
                    is TmdbError.Network -> "Network error"
                    is TmdbError.Timeout -> "Timeout"
                    is TmdbError.Unauthorized -> "Unauthorized (check API key)"
                    is TmdbError.NotFound -> "Not found"
                    is TmdbError.RateLimited -> "Rate limited (retry after ${error.retryAfter}s)"
                    is TmdbError.Unknown -> "Unknown error: ${error.message}"
                }
            // TODO: Use UnifiedLog when available in metadata-normalizer
            println("TMDB $operation failed for ID $tmdbId: $message")
        }
    }
