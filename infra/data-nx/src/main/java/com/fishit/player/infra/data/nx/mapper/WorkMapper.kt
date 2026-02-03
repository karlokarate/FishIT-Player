/**
 * Maps between NX_Work entity and NxWorkRepository.Work domain model.
 *
 * SSOT: domain/repo interface lives in core/model
 * Implementation lives here in infra/data-nx
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.repository.NxWorkRepository.RecognitionState
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.persistence.obx.NX_Work

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
    runtimeMs = durationMs,
    posterRef = poster?.toUrlString(),
    backdropRef = backdrop?.toUrlString(),
    rating = rating,
    genres = genres,
    plot = plot,
    director = director,
    cast = cast,
    trailer = trailer,
    // External IDs
    tmdbId = tmdbId,
    imdbId = imdbId,
    tvdbId = tvdbId,
    isAdult = isAdult,
    recognitionState = if (needsReview) RecognitionState.NEEDS_REVIEW else RecognitionState.HEURISTIC,
    createdAtMs = createdAt,
    updatedAtMs = updatedAt,
    isDeleted = false, // ObjectBox soft delete not implemented yet
)

/**
 * Converts Work domain model to NX_Work entity.
 * Note: Only sets fields from Work domain model, preserves entity ID.
 *
 * **Important:** Converts posterRef/backdropRef URL strings back to ImageRef objects.
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
        durationMs = runtimeMs,
        // Convert URL strings back to ImageRef - use new values if provided, else preserve existing
        poster = posterRef?.let { parseSerializedImageRef(it) } ?: existingEntity?.poster,
        backdrop = backdropRef?.let { parseSerializedImageRef(it) } ?: existingEntity?.backdrop,
        rating = rating,
        genres = genres,
        plot = plot,
        director = director,
        cast = cast,
        trailer = trailer ?: existingEntity?.trailer,
        // External IDs - preserve existing if new is null
        tmdbId = tmdbId ?: existingEntity?.tmdbId,
        imdbId = imdbId ?: existingEntity?.imdbId,
        tvdbId = tvdbId ?: existingEntity?.tvdbId,
        isAdult = isAdult,
        needsReview = recognitionState == RecognitionState.NEEDS_REVIEW,
        createdAt = if (existingEntity == null) createdAtMs.takeIf { it > 0 } ?: System.currentTimeMillis() else existingEntity.createdAt,
        updatedAt = System.currentTimeMillis(),
    )
}

/**
 * Extracts URL string from ImageRef for domain model.
 * Works with Http and LocalFile variants, returns special URI for TelegramThumb.
 */
private fun ImageRef.toUrlString(): String? = when (this) {
    is ImageRef.Http -> url
    is ImageRef.LocalFile -> path
    is ImageRef.TelegramThumb -> "tg://$remoteId" // Encode as special URI
    is ImageRef.InlineBytes -> null // Cannot convert to URL
}

/**
 * Parses a serialized ImageRef string back to ImageRef.
 *
 * **Delegates to canonical parsing function** [ImageRef.fromString] in core/model.
 *
 * This private function exists only for backward compatibility and to clarify intent.
 * All actual parsing logic is centralized in core/model to respect layer boundaries.
 */
private fun parseSerializedImageRef(serialized: String): ImageRef? =
    ImageRef.fromString(serialized)
