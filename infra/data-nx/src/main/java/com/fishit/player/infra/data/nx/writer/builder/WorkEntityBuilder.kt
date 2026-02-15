/**
 * WorkEntityBuilder - Constructs NX_Work entities from normalized metadata.
 *
 * Extracts entity construction logic from NxCatalogWriter to reduce CC.
 * Handles:
 * - Recognition state (CONFIRMED vs HEURISTIC)
 * - External IDs (tmdb, imdb, tvdb)
 * - Timestamp logic (createdAt from API vs now)
 *
 * CC: ~6 (well below target of 15)
 */
package com.fishit.player.infra.data.nx.writer.builder

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds NX_Work entities from normalized metadata.
 */
@Singleton
class WorkEntityBuilder
    @Inject
    constructor() {
        /**
         * Build a Work entity from normalized metadata.
         *
         * @param normalized The normalized metadata
         * @param workKey The computed work key
         * @param now Current timestamp (for updatedAtMs)
         * @return Work entity ready for upsert
         */
        fun build(
            normalized: NormalizedMediaMetadata,
            workKey: String,
            now: Long = System.currentTimeMillis(),
        ): NxWorkRepository.Work {
            // Use addedTimestamp from API if available, otherwise use current time
            val createdAt = normalized.addedTimestamp?.takeIf { it > 0 } ?: now

            return NxWorkRepository.Work(
                workKey = workKey,
                type = MediaTypeMapper.toWorkType(normalized.mediaType),
                displayTitle = normalized.canonicalTitle,
                sortTitle = normalized.canonicalTitle,
                titleNormalized = normalized.canonicalTitle.lowercase(),
                year = normalized.year,
                season = normalized.season,
                episode = normalized.episode,
                runtimeMs = normalized.durationMs,
                // NX_CONSOLIDATION_PLAN Phase 4: ImageRef direct — no serialization roundtrip
                poster = normalized.poster,
                backdrop = normalized.backdrop,
                thumbnail = normalized.thumbnail,
                rating = normalized.rating,
                genres = normalized.genres,
                plot = normalized.plot,
                director = normalized.director,
                cast = normalized.cast,
                trailer = normalized.trailer,
                releaseDate = normalized.releaseDate,
                // External IDs - prefer typed tmdb ref, fall back to externalIds
                tmdbId = (normalized.tmdb ?: normalized.externalIds.tmdb)?.id?.toString(),
                imdbId = normalized.externalIds.imdbId,
                tvdbId = normalized.externalIds.tvdbId,
                isAdult = normalized.isAdult,
                recognitionState = determineRecognitionState(normalized),
                createdAtMs = createdAt,
                updatedAtMs = now,
            )
        }

        /**
         * Determine recognition state based on TMDB availability.
         *
         * CONFIRMED: Has typed tmdb ref (from enrichment)
         * HEURISTIC: No tmdb ref (fallback canonical key)
         */
        private fun determineRecognitionState(normalized: NormalizedMediaMetadata): NxWorkRepository.RecognitionState =
            if (normalized.tmdb != null) {
                NxWorkRepository.RecognitionState.CONFIRMED
            } else {
                NxWorkRepository.RecognitionState.HEURISTIC
            }
    }
