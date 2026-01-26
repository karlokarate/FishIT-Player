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
    director = director,
    cast = cast,
    trailer = trailer,
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
        workType = type.toEntityString(),
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
 * Supports formats from NxCatalogWriter.toSerializedString():
 * - "http:<url>" → ImageRef.Http
 * - "tg:<remoteId>" → ImageRef.TelegramThumb
 * - "file:<path>" → ImageRef.LocalFile
 * - "tg://<remoteId>" → ImageRef.TelegramThumb (legacy format)
 * - Plain URL (starts with http/https) → ImageRef.Http (fallback)
 */
private fun parseSerializedImageRef(serialized: String): ImageRef? {
    val colonIndex = serialized.indexOf(':')
    if (colonIndex < 0) return null

    val prefix = serialized.substring(0, colonIndex)
    val value = serialized.substring(colonIndex + 1)

    return when (prefix) {
        "http" -> ImageRef.Http(url = value)
        "https" -> ImageRef.Http(url = "https:$value") // Reconstruct full URL
        "tg" -> {
            // Handle both "tg:<remoteId>" and "tg://<remoteId>" formats
            val remoteId = value.removePrefix("//")
            ImageRef.TelegramThumb(remoteId = remoteId)
        }
        "file" -> ImageRef.LocalFile(path = value)
        else -> {
            // Fallback: treat as full URL if it starts with http/https
            if (serialized.startsWith("http://") || serialized.startsWith("https://")) {
                ImageRef.Http(url = serialized)
            } else {
                null
            }
        }
    }
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
