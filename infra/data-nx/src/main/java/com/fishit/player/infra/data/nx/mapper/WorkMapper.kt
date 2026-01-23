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
    type = workType.toWorkType(),
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
    recognitionState = if (needsReview) RecognitionState.NEEDS_REVIEW else RecognitionState.HEURISTIC,
    createdAtMs = createdAt,
    updatedAtMs = updatedAt,
    isDeleted = false, // ObjectBox soft delete not implemented yet
)

/**
 * Converts Work domain model to NX_Work entity.
 * Note: Only sets fields from Work domain model, preserves entity ID.
 */
fun Work.toEntity(existingEntity: NX_Work? = null): NX_Work {
    val entity = existingEntity ?: NX_Work()
    return entity.copy(
        id = existingEntity?.id ?: 0,
        workKey = workKey,
        workType = type.toEntityString(),
        canonicalTitle = displayTitle,
        canonicalTitleLower = titleNormalized,
        year = year,
        durationMs = runtimeMs,
        // ImageRef conversion requires full ImageRef, but Work only has URL string
        // Preserving existing poster/backdrop if not provided
        poster = existingEntity?.poster,
        backdrop = existingEntity?.backdrop,
        rating = rating,
        genres = genres,
        plot = plot,
        needsReview = recognitionState == RecognitionState.NEEDS_REVIEW,
        createdAt = if (existingEntity == null) createdAtMs.takeIf { it > 0 } ?: System.currentTimeMillis() else existingEntity.createdAt,
        updatedAt = System.currentTimeMillis(),
    )
}

/**
 * Extracts URL string from ImageRef for domain model.
 * Works with Http and LocalFile variants, returns null for TelegramThumb.
 */
private fun ImageRef.toUrlString(): String? = when (this) {
    is ImageRef.Http -> url
    is ImageRef.LocalFile -> path
    is ImageRef.TelegramThumb -> "tg://$remoteId" // Encode as special URI
    is ImageRef.InlineBytes -> null // Cannot convert to URL
}

/**
 * Maps WorkType enum to entity string.
 */
private fun WorkType.toEntityString(): String = when (this) {
    WorkType.MOVIE -> "MOVIE"
    WorkType.SERIES -> "SERIES"
    WorkType.EPISODE -> "EPISODE"
    WorkType.CLIP -> "CLIP"
    WorkType.LIVE_CHANNEL -> "LIVE"
    WorkType.AUDIOBOOK -> "AUDIOBOOK"
    WorkType.MUSIC_TRACK -> "MUSIC"
    WorkType.UNKNOWN -> "UNKNOWN"
}

/**
 * Maps entity string to WorkType enum.
 */
private fun String.toWorkType(): WorkType = when (this.uppercase()) {
    "MOVIE" -> WorkType.MOVIE
    "SERIES" -> WorkType.SERIES
    "EPISODE" -> WorkType.EPISODE
    "CLIP" -> WorkType.CLIP
    "LIVE", "LIVE_CHANNEL" -> WorkType.LIVE_CHANNEL
    "AUDIOBOOK" -> WorkType.AUDIOBOOK
    "MUSIC", "MUSIC_TRACK" -> WorkType.MUSIC_TRACK
    else -> WorkType.UNKNOWN
}
