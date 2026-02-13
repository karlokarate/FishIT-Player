/**
 * Maps between NX_Work entity and NxWorkRepository.Work domain model.
 *
 * SSOT: domain/repo interface lives in core/model
 * Implementation lives here in infra/data-nx
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkRepository.RecognitionState
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.infra.data.nx.mapper.base.MappingUtils

/**
 * Converts NX_Work entity to Work domain model.
 */
fun NX_Work.toDomain(): Work = Work(
    workKey = workKey,
    type = WorkTypeMapper.toWorkType(workType),
    displayTitle = canonicalTitle,
    sortTitle = canonicalTitle,
    titleNormalized = canonicalTitleLower,
    year = year,
    season = season,
    episode = episode,
    runtimeMs = durationMs,
    // NX_CONSOLIDATION_PLAN Phase 4: ImageRef direct — no String roundtrip
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
    // External IDs
    tmdbId = tmdbId,
    imdbId = imdbId,
    tvdbId = tvdbId,
    isAdult = isAdult,
    recognitionState = MappingUtils.safeEnumFromString(
        this.recognitionState,
        RecognitionState.HEURISTIC,
    ),
    createdAtMs = createdAt,
    updatedAtMs = updatedAt,
    isDeleted = false, // ObjectBox soft delete not implemented yet
)

/**
 * Converts Work domain model to NX_Work entity.
 * Note: Only sets fields from Work domain model, preserves entity ID.
 *
 * NX_CONSOLIDATION_PLAN Phase 4: ImageRef flows directly — no String↔ImageRef roundtrip.
 * Falls back to existing entity values if new value is null (preserves existing images).
 */
fun Work.toEntity(existingEntity: NX_Work? = null): NX_Work {
    val entity = existingEntity ?: NX_Work()
    return entity.copy(
        id = existingEntity?.id ?: 0,
        workKey = workKey,
        workType = WorkTypeMapper.toEntityString(type),
        canonicalTitle = displayTitle,
        canonicalTitleLower = titleNormalized,
        year = year,
        season = season,
        episode = episode,
        durationMs = runtimeMs,
        // ImageRef direct — use new values if provided, else preserve existing
        poster = poster ?: existingEntity?.poster,
        backdrop = backdrop ?: existingEntity?.backdrop,
        thumbnail = thumbnail ?: existingEntity?.thumbnail,
        rating = rating,
        genres = genres,
        plot = plot,
        director = director,
        cast = cast,
        trailer = trailer ?: existingEntity?.trailer,
        releaseDate = releaseDate ?: existingEntity?.releaseDate,
        // External IDs - preserve existing if new is null
        tmdbId = tmdbId ?: existingEntity?.tmdbId,
        imdbId = imdbId ?: existingEntity?.imdbId,
        tvdbId = tvdbId ?: existingEntity?.tvdbId,
        isAdult = isAdult,
        recognitionState = this@toEntity.recognitionState.name,
        needsReview = this@toEntity.recognitionState == RecognitionState.NEEDS_REVIEW,
        createdAt = if (existingEntity == null) createdAtMs.takeIf { it > 0 } ?: System.currentTimeMillis() else existingEntity.createdAt,
        updatedAt = System.currentTimeMillis(),
    )
}

// NX_CONSOLIDATION_PLAN Phase 4: Removed toUrlString() and parseSerializedImageRef()
// — ImageRef flows directly between domain and entity, no String roundtrip needed.
