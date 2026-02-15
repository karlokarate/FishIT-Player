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

import com.fishit.player.core.model.MediaKind
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
    fun toEntityString(type: WorkType): String =
        when (type) {
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
    fun toWorkType(value: String): WorkType =
        when (value.uppercase()) {
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
    fun toEntityString(type: SourceType): String =
        when (type) {
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
    fun toSourceType(value: String): SourceType =
        when (value.lowercase()) {
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
    fun toWorkType(mediaType: MediaType): WorkType =
        when (mediaType) {
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
    fun toMediaType(workType: WorkType): MediaType =
        when (workType) {
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

    /**
     * Converts MediaType to MediaKind (for CanonicalMediaId).
     *
     * **NX_CONSOLIDATION_PLAN Phase 2 — Replaces duplicate mediaTypeToKind() in NxCanonicalMediaRepositoryImpl**
     */
    fun toMediaKind(mediaType: MediaType): MediaKind =
        when (mediaType) {
            MediaType.SERIES_EPISODE -> MediaKind.EPISODE
            else -> MediaKind.MOVIE
        }

    /**
     * Converts WorkType entity string to MediaKind.
     *
     * **NX_CONSOLIDATION_PLAN Phase 2 — Replaces duplicate workTypeToKind() in NxCanonicalMediaRepositoryImpl**
     */
    fun workTypeStringToMediaKind(workTypeString: String): MediaKind =
        when (workTypeString.uppercase()) {
            "EPISODE", "SERIES_EPISODE" -> MediaKind.EPISODE
            else -> MediaKind.MOVIE
        }

    /**
     * Converts MediaKind back to workType entity string.
     *
     * **NX_CONSOLIDATION_PLAN Phase 7 #2 — Replaces inline kindToWorkType() in NxCanonicalMediaRepositoryImpl**
     */
    fun fromMediaKind(kind: MediaKind): String =
        when (kind) {
            MediaKind.MOVIE -> "MOVIE"
            MediaKind.EPISODE -> "EPISODE"
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
    fun fromMediaType(mediaType: MediaType): SourceItemKind =
        when (mediaType) {
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
     * Format: "IPTV: MyServer", "Telegram: MyChannel", "Local File"
     *
     * @param sourceType The source type (e.g., "telegram", "xtream")
     * @param accountLabel The human-readable account label
     * @return Formatted label for UI display
     */
    fun buildLabel(
        sourceType: String,
        accountLabel: String,
    ): String =
        when (sourceType.lowercase()) {
            "telegram" -> "Telegram: $accountLabel"
            "xtream" -> "IPTV: $accountLabel"
            "local" -> "Local File"
            "plex" -> "Plex: $accountLabel"
            else -> "$sourceType: $accountLabel"
        }

    /**
     * Builds a label with optional quality suffix for list views.
     *
     * Format: "IPTV: MyServer - 1080p", "Telegram: MyChannel - 4K"
     * Falls back to [buildLabel] base when no quality info available.
     *
     * @param sourceType The source type
     * @param accountLabel The human-readable account label
     * @param qualityHeight Optional video height in pixels for quality suffix
     * @return Formatted label with optional quality
     */
    fun buildLabelWithQuality(
        sourceType: String,
        accountLabel: String,
        qualityHeight: Int?,
    ): String =
        buildString {
            append(buildLabel(sourceType, accountLabel))
            com.fishit.player.core.model.util.ResolutionLabel.fromHeight(qualityHeight)?.let {
                append(" - ")
                append(it)
            }
        }
}

/**
 * Calculates source selection priority for auto-selection.
 *
 * **Eliminates ~20 lines × 1 occurrence = 20 lines of duplication**
 *
 * Used in WorkDetailMapper for determining which source/variant to prefer.
 */

/**
 * @deprecated Use [com.fishit.player.core.model.util.SourcePriority] instead.
 * Retained temporarily as a delegate — all new code should use SourcePriority directly.
 */
@Deprecated("Use SourcePriority from core:model", ReplaceWith("SourcePriority", "com.fishit.player.core.model.util.SourcePriority"))
internal object SourcePriorityCalculator {
    fun calculateTotalPriority(
        sourceType: String,
        qualityTag: String? = null,
        hasDirectUrl: Boolean = false,
        isExplicitVariant: Boolean = false,
    ): Int =
        com.fishit.player.core.model.util.SourcePriority.totalPriority(
            sourceType = sourceType,
            qualityTag = qualityTag,
            hasDirectUrl = hasDirectUrl,
            isExplicitVariant = isExplicitVariant,
        )
}
