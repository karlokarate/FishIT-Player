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

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.PlaybackHintKeys
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
         * @param playbackHints Optional playback hints from raw metadata (for episode tmdb_id extraction)
         * @return Work entity ready for upsert
         */
        fun build(
            normalized: NormalizedMediaMetadata,
            workKey: String,
            now: Long = System.currentTimeMillis(),
            playbackHints: Map<String, String> = emptyMap(),
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
                // For episodes: extract episode-specific tmdb_id from playback hints
                tmdbId = resolveTmdbId(normalized, playbackHints),
                imdbId = normalized.externalIds.imdbId,
                tvdbId = normalized.externalIds.tvdbId,
                isAdult = normalized.isAdult,
                recognitionState = determineRecognitionState(normalized),
                createdAtMs = createdAt,
                updatedAtMs = now,
            )
        }

        /**
         * Resolve the tmdb_id for this work.
         *
         * For episodes, extracts the episode-specific TMDB ID from playback hints
         * (stored as xtream.episodeTmdbId). This is necessary because:
         * - normalized.tmdb contains the series TMDB ID (per Gold Decision)
         * - Episodes need their own episode-specific TMDB ID in the work entity
         * - The episode TMDB ID is stored in playback hints by the pipeline
         *
         * CRITICAL: Episodes MUST NOT fall back to series TMDB ID to avoid
         * collision in external-ID lookups (NxCanonicalMediaRepositoryImpl.findByExternalId
         * queries by tmdbId without filtering by workType).
         *
         * For all other media types, uses the standard tmdb resolution logic.
         *
         * @param normalized The normalized metadata
         * @param playbackHints The playback hints from raw metadata
         * @return The tmdb_id as a string, or null if not available
         */
        private fun resolveTmdbId(
            normalized: NormalizedMediaMetadata,
            playbackHints: Map<String, String>,
        ): String? {
            // For episodes: ONLY use episode-specific TMDB ID from playback hints
            // Do NOT fall back to series TMDB ID to avoid data integrity issues
            if (normalized.mediaType == MediaType.SERIES_EPISODE) {
                val episodeTmdbId = playbackHints[PlaybackHintKeys.Xtream.EPISODE_TMDB_ID]
                if (episodeTmdbId != null) {
                    // Validate that the hint value is numeric before using it
                    val cleaned = episodeTmdbId.trim()
                    if (cleaned.isNotEmpty() && cleaned.toLongOrNull() != null) {
                        return cleaned
                    }
                }
                // Return null for episodes without valid episode-specific TMDB ID
                return null
            }

            // For all other media types:
            // Use standard resolution (prefer typed tmdb ref, fall back to externalIds)
            return (normalized.tmdb ?: normalized.externalIds.tmdb)?.id?.toString()
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
