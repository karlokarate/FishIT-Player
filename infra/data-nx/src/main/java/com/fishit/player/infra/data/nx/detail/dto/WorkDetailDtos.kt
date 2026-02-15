package com.fishit.player.infra.data.nx.detail.dto

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.ids.PipelineItemId

/**
 * DTOs for feature:detail NX migration.
 *
 * These DTOs replace the legacy `CanonicalMediaWithSources` and `CanonicalResumeInfo`
 * types that were backed by ObxCanonicalMedia entities.
 *
 * **NX SSOT Contract (INV-6):**
 * UI reads exclusively from NX_* entities. These DTOs are the feature:detail
 * projection of the NX Work Graph.
 *
 * **Mapping:**
 * - [WorkWithSources] ← NX_Work + NX_WorkSourceRef + NX_WorkVariant
 * - [WorkResumeInfo] ← NX_WorkUserState
 * - [WorkSourceInfo] ← NX_WorkSourceRef + NX_WorkVariant (combined)
 */

/**
 * Complete work with all sources for detail screen display.
 *
 * Replaces: `CanonicalMediaWithSources`
 *
 * @property workKey Unique NX_Work key (e.g., "movie:tmdb:550" or "movie:inception:2010")
 * @property workType MOVIE, EPISODE, SERIES, CLIP, LIVE
 * @property canonicalTitle Normalized display title
 * @property year Release year (null for LIVE)
 * @property season Season number (EPISODE only)
 * @property episode Episode number (EPISODE only)
 * @property tmdbId TMDB ID if resolved
 * @property imdbId IMDB ID if available
 * @property poster Best poster ImageRef
 * @property backdrop Best backdrop ImageRef
 * @property thumbnail Thumbnail for lists
 * @property plot Description/synopsis
 * @property rating Rating (0-10 scale)
 * @property durationMs Runtime in milliseconds
 * @property genres Comma-separated genres
 * @property director Director name(s)
 * @property cast Comma-separated cast
 * @property trailer YouTube trailer URL
 * @property sources All available sources with variants
 */
data class WorkWithSources(
    val workKey: String,
    val workType: String,
    val canonicalTitle: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: String?,
    val imdbId: String?,
    val poster: ImageRef?,
    val backdrop: ImageRef?,
    val thumbnail: ImageRef?,
    val plot: String?,
    val rating: Double?,
    val durationMs: Long?,
    val genres: String?,
    val director: String?,
    val cast: String?,
    val trailer: String?,
    val sources: List<WorkSourceInfo>,
) {
    /** Best quality source (by priority) */
    val bestSource: WorkSourceInfo?
        get() = sources.maxByOrNull { it.priority }

    /** Check if multiple sources are available */
    val hasMultipleSources: Boolean
        get() = sources.size > 1

    /** Group sources by pipeline type */
    val sourcesByType: Map<String, List<WorkSourceInfo>>
        get() = sources.groupBy { it.sourceType }

    /** Check if this is a series (has child episodes via NX_WorkRelation) */
    val isSeries: Boolean
        get() = workType == "SERIES"

    /** Check if this is an episode */
    val isEpisode: Boolean
        get() = workType == "EPISODE"

    /** Check if this is live content */
    val isLive: Boolean
        get() = workType == "LIVE"
}

/**
 * Combined source reference and variant information.
 *
 * Replaces: `MediaSourceRef` (which combined source + quality + hints)
 *
 * In NX schema, source (NX_WorkSourceRef) and variant (NX_WorkVariant) are separate.
 * This DTO combines them for UI convenience.
 *
 * @property sourceKey Unique source key (e.g., "xtream:user@server:vod:123")
 * @property sourceType Pipeline type: xtream, telegram, local
 * @property accountKey Account identifier
 * @property sourceLabel Human-readable label
 * @property variantKey Unique variant key
 * @property qualityTag Quality: source, 720p, 1080p, 4k
 * @property languageTag Language: original, en, de
 * @property width Resolution width
 * @property height Resolution height
 * @property playbackUrl Direct playback URL (if available)
 * @property playbackMethod DIRECT, STREAMING, DOWNLOAD_FIRST
 * @property containerFormat mp4, mkv, ts
 * @property videoCodec h264, h265, vp9
 * @property audioCodec aac, ac3, dts
 * @property fileSizeBytes File size in bytes
 * @property playbackHints Source-specific hints for PlaybackSourceFactory
 * @property priority Selection priority (higher = preferred)
 * @property isAvailable Whether source is currently reachable
 */
data class WorkSourceInfo(
    // Source identity
    val sourceKey: String,
    val sourceType: String,
    val accountKey: String,
    val sourceLabel: String,
    // Variant identity
    val variantKey: String,
    val qualityTag: String,
    val languageTag: String,
    // Quality info
    val width: Int?,
    val height: Int?,
    // Playback
    val playbackUrl: String?,
    val playbackMethod: String,
    val containerFormat: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    // File info
    val fileSizeBytes: Long?,
    // Hints for PlaybackSourceFactory
    val playbackHints: Map<String, String>,
    // Selection
    val priority: Int,
    val isAvailable: Boolean,
) {
    /** Pipeline item ID for compatibility with existing code */
    val sourceId: PipelineItemId
        get() = PipelineItemId(sourceKey)

    /** Resolution as string (e.g., "1920x1080") */
    val resolutionLabel: String?
        get() = if (width != null && height != null) "${width}x$height" else null

    /** Quality label for UI (prefers explicit tag over resolution) */
    val displayQuality: String
        get() =
            when {
                qualityTag != "source" -> qualityTag
                else ->
                    com.fishit.player.core.model.util.ResolutionLabel
                        .fromHeight(height) ?: "Unknown"
            }

    /** File size as human-readable string */
    val fileSizeLabel: String?
        get() =
            fileSizeBytes?.let {
                com.fishit.player.core.model.util.FileSizeFormatter
                    .format(it)
            }
}

/**
 * Resume information for a work.
 *
 * Replaces: `CanonicalResumeInfo`
 *
 * Mapped from NX_WorkUserState.
 *
 * @property workKey Work this resume is for
 * @property profileId Profile ID
 * @property resumePositionMs Position in milliseconds
 * @property totalDurationMs Total duration
 * @property progressPercent Progress as percentage (0.0 - 1.0)
 * @property isWatched True if watched to completion
 * @property watchCount Number of times watched
 * @property isFavorite True if favorited
 * @property lastWatchedAt Timestamp of last watch
 * @property lastSourceKey Last source used (for same-source resume)
 * @property lastVariantKey Last variant used
 */
data class WorkResumeInfo(
    val workKey: String,
    val profileId: Long,
    val resumePositionMs: Long,
    val totalDurationMs: Long,
    val progressPercent: Float,
    val isWatched: Boolean,
    val watchCount: Int,
    val isFavorite: Boolean,
    val lastWatchedAt: Long?,
    val lastSourceKey: String?,
    val lastVariantKey: String?,
    val updatedAt: Long,
) {
    /** Whether resume is significant (>2% and <95%) */
    val hasSignificantProgress: Boolean
        get() = progressPercent > 0.02f && progressPercent < 0.95f

    /** Format remaining time as string */
    val remainingTimeLabel: String
        get() {
            val remainingMs = totalDurationMs - resumePositionMs
            val remainingMins = (remainingMs / 60_000).toInt()
            return when {
                remainingMins >= 60 -> "${remainingMins / 60}h ${remainingMins % 60}m left"
                remainingMins > 0 -> "${remainingMins}m left"
                else -> "Almost done"
            }
        }

    /** Progress as percentage string */
    val progressLabel: String
        get() = "${(progressPercent * 100).toInt()}%"

    /**
     * Calculate resume position for a specific source/variant.
     *
     * @param sourceKey The source to resume on
     * @param variantKey The variant to resume on
     * @param variantDurationMs Duration of that variant in milliseconds
     * @return ResumePosition with calculated position
     */
    fun calculatePositionForVariant(
        sourceKey: String,
        variantKey: String,
        variantDurationMs: Long,
    ): ResumePosition =
        if (sourceKey == lastSourceKey && variantKey == lastVariantKey) {
            // Same source+variant - use exact position
            ResumePosition(
                positionMs = resumePositionMs,
                isExact = true,
                note = null,
            )
        } else {
            // Different source/variant - use percentage
            val calculatedPosition = (progressPercent * variantDurationMs).toLong()
            ResumePosition(
                positionMs = calculatedPosition,
                isExact = false,
                note = "Resume approximated from $progressLabel",
            )
        }
}

/**
 * Calculated resume position for playback.
 *
 * @property positionMs Position to seek to in milliseconds
 * @property isExact True if this is an exact position from the same source
 * @property note Optional UI note explaining approximation
 */
data class ResumePosition(
    val positionMs: Long,
    val isExact: Boolean,
    val note: String?,
)

/**
 * Unified detail state for all media types.
 *
 * This is the state container used by UnifiedDetailViewModel.
 *
 * @property isLoading Loading indicator
 * @property error Error message if any
 * @property work The work with all sources
 * @property resume Resume information
 * @property selectedSourceKey User-selected source (null = auto-select)
 */
data class WorkDetailState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val work: WorkWithSources? = null,
    val resume: WorkResumeInfo? = null,
    val selectedSourceKey: String? = null,
) {
    /** Check if data is loaded successfully */
    val isSuccess: Boolean
        get() = !isLoading && error == null && work != null

    /** Check if in error state */
    val isError: Boolean
        get() = error != null
}
