/**
 * Shared type mapping utilities for NX entity ↔ domain conversions.
 *
 * SSOT for ALL type conversions to eliminate duplication across mapper files.
 * This file centralizes the mapping logic that was previously duplicated in:
 * - WorkMapper.kt (WorkType mappings)
 * - WorkSourceRefMapper.kt (SourceType mappings)
 * - IngestLedgerMapper.kt (SourceType via enum.name)
 * - SourceAccountMapper.kt (SourceType via enum.name)
 * - NxXtreamCatalogRepositoryImpl (MediaType ↔ WorkType conversions)
 * - NxTelegramMediaRepositoryImpl (MediaType ↔ WorkType conversions)
 * - NxHomeContentRepositoryImpl (MediaType ↔ WorkType conversions)
 * - NxLibraryContentRepositoryImpl (MediaType ↔ WorkType conversions)
 * - NxCatalogWriter (MediaType ↔ WorkType, MediaType ↔ SourceItemKind)
 * - WorkDetailMapper (source labels, priority calculation)
 *
 * **Architecture:**
 * - Each mapper object is an internal singleton
 * - Bidirectional mapping: enum → String and String → enum
 * - Consistent naming: toEntityString() and toXxxType()
 * - Helper functions for UI labels and priority calculation
 *
 * **Usage:**
 * ```kotlin
 * // Type conversions
 * val entityString = WorkTypeMapper.toEntityString(WorkType.MOVIE)
 * val workType = WorkTypeMapper.toWorkType("MOVIE")
 * val mediaType = MediaTypeMapper.toMediaType(WorkType.SERIES)
 * val sourceItemKind = SourceItemKindMapper.fromMediaType(MediaType.MOVIE)
 *
 * // Helper functions
 * val label = SourceLabelBuilder.buildLabel("telegram", "My Channel")
 * val priority = SourcePriorityCalculator.calculateTotalPriority(
 *     sourceType = "xtream",
 *     qualityTag = "1080p",
 *     hasDirectUrl = true,
 *     isExplicitVariant = true
 * )
 * ```
 *
 * **Expected Impact (Phase 3 of Issue #669):**
 * - Eliminates ~200 lines of duplicated when-blocks
 * - Reduces CC in 6+ mapper/repository files
 * - Creates single source of truth for all type conversions
 * - Improves testability (test once, use everywhere)
 */
package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
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

/**
 * Maps between MediaType (core) and WorkType (repository).
 *
 * **Eliminates ~15 lines × 6 occurrences = 90 lines of duplication**
 *
 * Used in:
 * - NxXtreamCatalogRepositoryImpl
 * - NxTelegramMediaRepositoryImpl
 * - NxHomeContentRepositoryImpl
 * - NxLibraryContentRepositoryImpl
 * - NxCatalogWriter
 */
internal object MediaTypeMapper {
    /**
     * Converts MediaType to WorkType.
     * Used when storing RawMediaMetadata to NX_Work.
     */
    fun toWorkType(mediaType: MediaType): WorkType = when (mediaType) {
        MediaType.MOVIE -> WorkType.MOVIE
        MediaType.SERIES -> WorkType.SERIES
        MediaType.SERIES_EPISODE -> WorkType.EPISODE
        MediaType.LIVE -> WorkType.LIVE_CHANNEL
        MediaType.CLIP -> WorkType.CLIP
        MediaType.AUDIOBOOK -> WorkType.AUDIOBOOK
        MediaType.MUSIC -> WorkType.MUSIC_TRACK
        MediaType.PODCAST -> WorkType.UNKNOWN // No direct mapping yet
        MediaType.UNKNOWN -> WorkType.UNKNOWN
    }

    /**
     * Converts WorkType to MediaType.
     * Used when converting NX_Work back to domain models.
     */
    fun toMediaType(workType: WorkType): MediaType = when (workType) {
        WorkType.MOVIE -> MediaType.MOVIE
        WorkType.SERIES -> MediaType.SERIES
        WorkType.EPISODE -> MediaType.SERIES_EPISODE
        WorkType.LIVE_CHANNEL -> MediaType.LIVE
        WorkType.CLIP -> MediaType.CLIP
        WorkType.AUDIOBOOK -> MediaType.AUDIOBOOK
        WorkType.MUSIC_TRACK -> MediaType.MUSIC
        WorkType.UNKNOWN -> MediaType.UNKNOWN
    }

    /**
     * Converts WorkType entity string to MediaType.
     * Convenience method for direct entity string → MediaType conversion.
     */
    fun workTypeStringToMediaType(value: String): MediaType {
        val workType = WorkTypeMapper.toWorkType(value)
        return toMediaType(workType)
    }
}

/**
 * Maps between MediaType and SourceItemKind.
 *
 * **Eliminates ~10 lines × 2 occurrences = 20 lines of duplication**
 *
 * Used in NxCatalogWriter for deriving SourceItemKind from MediaType.
 */
internal object SourceItemKindMapper {
    /**
     * Converts MediaType to SourceItemKind.
     * Used when creating NX_WorkSourceRef from RawMediaMetadata.
     */
    fun fromMediaType(mediaType: MediaType): SourceItemKind = when (mediaType) {
        MediaType.MOVIE -> SourceItemKind.VOD
        MediaType.SERIES -> SourceItemKind.SERIES
        MediaType.SERIES_EPISODE -> SourceItemKind.EPISODE
        MediaType.LIVE -> SourceItemKind.LIVE
        MediaType.CLIP -> SourceItemKind.FILE
        MediaType.AUDIOBOOK -> SourceItemKind.FILE
        MediaType.MUSIC -> SourceItemKind.FILE
        MediaType.PODCAST -> SourceItemKind.FILE
        MediaType.UNKNOWN -> SourceItemKind.UNKNOWN
    }
}

/**
 * Builds human-readable source labels for UI display.
 *
 * **Eliminates ~10 lines × 1 occurrence = 10 lines of duplication**
 *
 * Used in WorkDetailMapper for consistent source labeling.
 */
internal object SourceLabelBuilder {
    /**
     * Builds a human-readable label for a source type and account.
     *
     * @param sourceType The source type (e.g., "telegram", "xtream")
     * @param accountLabel The human-readable account label
     * @return Formatted label for UI display
     */
    fun buildLabel(sourceType: String, accountLabel: String): String = when (sourceType.lowercase()) {
        "telegram" -> "Telegram: $accountLabel"
        "xtream" -> "IPTV: $accountLabel"
        "local" -> "Local File"
        "plex" -> "Plex: $accountLabel"
        else -> "${sourceType}: $accountLabel"
    }
}

/**
 * Calculates source selection priority for auto-selection.
 *
 * **Eliminates ~20 lines × 1 occurrence = 20 lines of duplication**
 *
 * Used in WorkDetailMapper for determining which source/variant to prefer.
 */
internal object SourcePriorityCalculator {
    /**
     * Calculates base priority from source type.
     *
     * Higher priority = preferred for auto-selection.
     * Priority order: local (100) > plex (80) > xtream (60) > telegram (40) > other (20)
     *
     * @param sourceType The source type string
     * @return Base priority score
     */
    fun getBasePriority(sourceType: String): Int = when (sourceType.lowercase()) {
        "local" -> 100
        "plex" -> 80
        "xtream" -> 60
        "telegram" -> 40
        else -> 20
    }

    /**
     * Calculates quality priority from quality tag.
     *
     * @param qualityTag Quality tag (e.g., "4k", "1080p", "720p", "source")
     * @return Quality priority score (0-50)
     */
    fun getQualityPriority(qualityTag: String): Int = when (qualityTag.lowercase()) {
        "4k", "2160p", "uhd" -> 50
        "1080p", "fhd" -> 40
        "720p", "hd" -> 30
        "480p", "sd" -> 20
        "source" -> 25 // Slightly lower than explicit qualities
        else -> 10
    }

    /**
     * Calculates total priority for a source/variant combination.
     *
     * @param sourceType The source type string
     * @param qualityTag Optional quality tag
     * @param hasDirectUrl Whether a direct playback URL is available
     * @param isExplicitVariant Whether this is an explicit variant (vs default)
     * @return Total priority score
     */
    fun calculateTotalPriority(
        sourceType: String,
        qualityTag: String?,
        hasDirectUrl: Boolean = false,
        isExplicitVariant: Boolean = false,
    ): Int {
        var priority = getBasePriority(sourceType)

        if (qualityTag != null) {
            priority += getQualityPriority(qualityTag)
        }

        if (hasDirectUrl) {
            priority += 10
        }

        if (isExplicitVariant) {
            priority += 5
        }

        return priority
    }
}

