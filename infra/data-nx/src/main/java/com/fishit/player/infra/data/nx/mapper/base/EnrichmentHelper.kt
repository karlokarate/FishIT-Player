/**
 * Shared enrichment logic for NX_Work entities.
 *
 * Eliminates duplication between:
 * - NxWorkRepositoryImpl.enrichIfAbsent() (ENRICH_ONLY policy)
 * - NxCanonicalMediaRepositoryImpl.updateTmdbEnriched() (AUTHORITY_WINS policy)
 *
 * **DRY Principle**: Single source of truth for field-by-field enrichment mapping.
 *
 * **Issue Reference**: PR #716 - Consolidation of enrichment paths
 */
package com.fishit.player.infra.data.nx.mapper.base

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NxKeyGenerator

/**
 * Enrichment data container that can be constructed from various sources:
 * - NxWorkRepository.Enrichment (from enrichIfAbsent)
 * - NormalizedMediaMetadata (from TMDB enrichment)
 */
data class EnrichmentData(
    // ENRICH_ONLY fields
    val season: Int? = null,
    val episode: Int? = null,
    val durationMs: Long? = null,
    val poster: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val thumbnail: ImageRef? = null,
    val rating: Double? = null,
    val genres: String? = null,
    val plot: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val trailer: String? = null,
    val releaseDate: String? = null,
    
    // ALWAYS_UPDATE fields
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
    
    // MONOTONIC_UP field
    val recognitionState: NxWorkRepository.RecognitionState? = null,
)

/**
 * Convert NxWorkRepository.Enrichment to EnrichmentData.
 */
fun NxWorkRepository.Enrichment.toEnrichmentData(): EnrichmentData =
    EnrichmentData(
        season = season,
        episode = episode,
        durationMs = runtimeMs,
        poster = poster,
        backdrop = backdrop,
        thumbnail = thumbnail,
        rating = rating,
        genres = genres,
        plot = plot,
        director = director,
        cast = cast,
        trailer = trailer,
        releaseDate = releaseDate,
        tmdbId = tmdbId,
        imdbId = imdbId,
        tvdbId = tvdbId,
        recognitionState = recognitionState,
    )

/**
 * Convert NormalizedMediaMetadata to EnrichmentData.
 */
fun NormalizedMediaMetadata.toEnrichmentData(): EnrichmentData =
    EnrichmentData(
        season = season,
        episode = episode,
        durationMs = durationMs,
        poster = poster,
        backdrop = backdrop,
        thumbnail = thumbnail,
        rating = rating,
        genres = genres,
        plot = plot,
        director = director,
        cast = cast,
        trailer = trailer,
        releaseDate = year?.toString(),
        tmdbId = externalIds.effectiveTmdbId?.toString(),
        imdbId = externalIds.imdbId,
        tvdbId = externalIds.tvdbId,
        recognitionState = null, // NormalizedMediaMetadata doesn't have recognitionState
    )

/**
 * Apply enrichment to an NX_Work entity with specified update policy.
 *
 * This method is the SSOT for field-by-field enrichment logic, eliminating duplication
 * between enrichIfAbsent() and updateTmdbEnriched().
 *
 * @param entity The NX_Work entity to enrich (modified in place)
 * @param enrichment The enrichment data to apply
 * @param policy The update policy (ENRICH_ONLY or AUTHORITY_WINS)
 */
object EnrichmentHelper {
    
    /**
     * Apply enrichment to entity with specified policy.
     *
     * Field categories:
     * - IMMUTABLE: workKey, workType, canonicalTitle, canonicalTitleLower, year, createdAt → SKIP
     * - ENRICH_ONLY/AUTHORITY_WINS: Uses policy for poster, backdrop, thumbnail, plot, etc.
     * - ALWAYS_UPDATE: tmdbId, imdbId, tvdbId → always overwrite with new non-null value
     * - MONOTONIC_UP: recognitionState → only upgrade (lower ordinal = higher confidence)
     * - AUTO: updatedAt → always current time
     */
    fun applyEnrichment(
        entity: NX_Work,
        enrichment: EnrichmentData,
        policy: UpdatePolicy,
    ) {
        // IMMUTABLE: workKey, workType, canonicalTitle, canonicalTitleLower, year, createdAt — SKIP
        
        // Apply policy-based fields (ENRICH_ONLY or AUTHORITY_WINS depending on caller)
        entity.season = MappingUtils.applyWithPolicy(entity.season, enrichment.season, policy)
        entity.episode = MappingUtils.applyWithPolicy(entity.episode, enrichment.episode, policy)
        entity.durationMs = MappingUtils.applyWithPolicy(entity.durationMs, enrichment.durationMs, policy)
        entity.rating = MappingUtils.applyWithPolicy(entity.rating, enrichment.rating, policy)
        entity.genres = MappingUtils.applyWithPolicy(entity.genres, enrichment.genres, policy)
        entity.plot = MappingUtils.applyWithPolicy(entity.plot, enrichment.plot, policy)
        entity.director = MappingUtils.applyWithPolicy(entity.director, enrichment.director, policy)
        entity.cast = MappingUtils.applyWithPolicy(entity.cast, enrichment.cast, policy)
        entity.trailer = MappingUtils.applyWithPolicy(entity.trailer, enrichment.trailer, policy)
        entity.releaseDate = MappingUtils.applyWithPolicy(entity.releaseDate, enrichment.releaseDate, policy)
        
        // ImageRef fields — policy-based
        entity.poster = MappingUtils.applyWithPolicy(entity.poster, enrichment.poster, policy)
        entity.backdrop = MappingUtils.applyWithPolicy(entity.backdrop, enrichment.backdrop, policy)
        entity.thumbnail = MappingUtils.applyWithPolicy(entity.thumbnail, enrichment.thumbnail, policy)
        
        // ALWAYS_UPDATE: External IDs (regardless of policy, these are always updated)
        entity.tmdbId = MappingUtils.alwaysUpdate(entity.tmdbId, enrichment.tmdbId)
        entity.imdbId = MappingUtils.alwaysUpdate(entity.imdbId, enrichment.imdbId)
        entity.tvdbId = MappingUtils.alwaysUpdate(entity.tvdbId, enrichment.tvdbId)
        
        // Update authorityKey if tmdbId changed — delegate to NxKeyGenerator SSOT
        enrichment.tmdbId?.let { newTmdbId ->
            val workType = entity.workType.lowercase()
            entity.authorityKey = NxKeyGenerator.authorityKey("TMDB", workType, newTmdbId)
        }
        
        // MONOTONIC_UP: RecognitionState — only upgrade, never downgrade
        enrichment.recognitionState?.let { newState ->
            val currentState = MappingUtils.safeEnumFromString(
                entity.recognitionState,
                NxWorkRepository.RecognitionState.HEURISTIC,
            )
            val upgradedState = MappingUtils.monotonicUp(currentState, newState)
            if (upgradedState != null) {
                entity.recognitionState = upgradedState.name
                @Suppress("DEPRECATION")
                entity.needsReview = upgradedState == NxWorkRepository.RecognitionState.NEEDS_REVIEW
            }
        }
        
        // AUTO: always update timestamp
        entity.updatedAt = System.currentTimeMillis()
    }
}
