/**
 * Shared type mapping utilities for NX entity ↔ domain conversions.
 *
 * SSOT for ALL type conversions to eliminate duplication across mapper files.
 * This file centralizes the mapping logic that was previously duplicated in:
 * - WorkMapper.kt (WorkType mappings)
 * - WorkSourceRefMapper.kt (SourceType mappings)
 * - IngestLedgerMapper.kt (SourceType via enum.name)
 * - SourceAccountMapper.kt (SourceType via enum.name)
 *
 * **Architecture:**
 * - Each mapper object is an internal singleton
 * - Bidirectional mapping: enum → String and String → enum
 * - Consistent naming: toEntityString() and toXxxType()
 *
 * **Usage:**
 * ```kotlin
 * val entityString = WorkTypeMapper.toEntityString(WorkType.MOVIE)
 * val workType = WorkTypeMapper.toWorkType("MOVIE")
 * ```
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType

/**
 * Maps between WorkType enum and entity string representation.
 *
 * **Eliminates ~20 lines × 2 occurrences = 40 lines of duplication**
 */
internal object WorkTypeMapper {
    /**
     * Converts WorkType enum to entity string.
     * Used when persisting to ObjectBox.
     */
    fun toEntityString(type: WorkType): String = when (type) {
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
     * Converts entity string to WorkType enum.
     * Used when reading from ObjectBox.
     *
     * **Case-insensitive** and supports aliases:
     * - "LIVE" or "LIVE_CHANNEL" → LIVE_CHANNEL
     * - "MUSIC" or "MUSIC_TRACK" → MUSIC_TRACK
     */
    fun toWorkType(value: String): WorkType = when (value.uppercase()) {
        "MOVIE" -> WorkType.MOVIE
        "SERIES" -> WorkType.SERIES
        "EPISODE" -> WorkType.EPISODE
        "CLIP" -> WorkType.CLIP
        "LIVE", "LIVE_CHANNEL" -> WorkType.LIVE_CHANNEL
        "AUDIOBOOK" -> WorkType.AUDIOBOOK
        "MUSIC", "MUSIC_TRACK" -> WorkType.MUSIC_TRACK
        else -> WorkType.UNKNOWN
    }
}

/**
 * Maps between SourceType enum and entity string representation.
 *
 * **Eliminates ~14 lines × 2 occurrences = 28 lines of duplication**
 */
internal object SourceTypeMapper {
    /**
     * Converts SourceType enum to entity string.
     * Used when persisting to ObjectBox.
     */
    fun toEntityString(type: SourceType): String = when (type) {
        SourceType.TELEGRAM -> "telegram"
        SourceType.XTREAM -> "xtream"
        SourceType.IO -> "io"
        SourceType.LOCAL -> "local"
        SourceType.PLEX -> "plex"
        SourceType.UNKNOWN -> "unknown"
    }

    /**
     * Converts entity string to SourceType enum.
     * Used when reading from ObjectBox.
     *
     * **Case-insensitive** for robustness.
     */
    fun toSourceType(value: String): SourceType = when (value.lowercase()) {
        "telegram" -> SourceType.TELEGRAM
        "xtream" -> SourceType.XTREAM
        "io" -> SourceType.IO
        "local" -> SourceType.LOCAL
        "plex" -> SourceType.PLEX
        else -> SourceType.UNKNOWN
    }
}
