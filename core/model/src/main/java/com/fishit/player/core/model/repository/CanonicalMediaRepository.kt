package com.fishit.player.core.model.repository

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.ids.CanonicalId
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.ids.TmdbId

/**
 * Repository interface for managing canonical media identity and cross-pipeline unification.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Each unique media work is stored once with a CanonicalMediaId
 * - Multiple sources (Telegram, Xtream, IO) are linked via MediaSourceRef
 * - Resume positions are tied to canonical identity
 *
 * This repository is the central point for:
 * - Storing and retrieving canonical media works
 * - Linking multiple sources to the same media work
 * - Cross-pipeline resume synchronization
 * - Unified detail screen data
 */
interface CanonicalMediaRepository {
    // ========== Core CRUD Operations ==========

    /**
     * Upsert a canonical media work from normalized metadata.
     *
     * If a matching canonical key exists, updates the entry. Otherwise, creates a new canonical
     * media entry.
     *
     * @param normalized The normalized metadata from the normalizer
     * @return The canonical ID for this media work
     */
    suspend fun upsertCanonicalMedia(normalized: NormalizedMediaMetadata): CanonicalMediaId

    /**
     * Add or update a source reference linked to a canonical media work.
     *
     * Multiple sources can link to the same canonical media. If the source already exists (by
     * sourceId), it will be updated.
     *
     * @param canonicalId The canonical media to link to
     * @param source The source reference to add/update
     */
    suspend fun addOrUpdateSourceRef(
        canonicalId: CanonicalMediaId,
        source: MediaSourceRef,
    )

    /**
     * Remove a source reference.
     *
     * Does not remove the canonical media even if it becomes orphaned.
     *
     * @param sourceId The unique source identifier to remove
     */
    suspend fun removeSourceRef(sourceId: PipelineItemId)

    // ========== Query Operations ==========

    /**
     * Find canonical media by its key.
     *
     * @param canonicalId The canonical ID to look up
     * @return The media with all linked sources, or null if not found
     */
    suspend fun findByCanonicalId(canonicalId: CanonicalMediaId): CanonicalMediaWithSources?

    /**
     * Find canonical media by any of its external IDs.
     *
     * @param tmdbId TMDB ID (preferred)
     * @param imdbId IMDB ID
     * @param tvdbId TVDB ID
     * @return The media with all linked sources, or null if not found
     */
    suspend fun findByExternalId(
        tmdbId: TmdbId? = null,
        imdbId: String? = null,
        tvdbId: String? = null,
    ): CanonicalMediaWithSources?

    /**
     * Find canonical media by normalized title and year.
     *
     * Used as fallback when no external IDs are available.
     *
     * @param canonicalTitle Normalized title (case-insensitive search)
     * @param year Optional year filter
     * @param kind Optional media kind filter (MOVIE or EPISODE)
     * @return List of matching media with sources
     */
    suspend fun findByTitleAndYear(
        canonicalTitle: String,
        year: Int? = null,
        kind: MediaKind? = null,
    ): List<CanonicalMediaWithSources>

    /**
     * Find canonical media by source ID.
     *
     * Useful for reverse lookup: given a pipeline item, find its canonical entry.
     *
     * @param sourceId The pipeline-specific source identifier
     * @return The canonical media with all linked sources, or null
     */
    suspend fun findBySourceId(sourceId: PipelineItemId): CanonicalMediaWithSources?

    /**
     * Get all sources for a canonical media work.
     *
     * @param canonicalId The canonical ID
     * @return List of all linked sources, sorted by priority descending
     */
    suspend fun getSourcesForMedia(canonicalId: CanonicalMediaId): List<MediaSourceRef>

    /**
     * Search canonical media by title.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param kind Optional kind filter
     * @param limit Maximum results
     * @return Matching media with sources
     */
    suspend fun search(
        query: String,
        kind: MediaKind? = null,
        limit: Int = 50,
    ): List<CanonicalMediaWithSources>

    // ========== Cross-Pipeline Resume ==========

    /**
     * Get canonical resume position for a profile.
     *
     * @param canonicalId The canonical media ID
     * @param profileId The user profile ID
     * @return Resume info or null if no resume exists
     */
    suspend fun getCanonicalResume(
        canonicalId: CanonicalMediaId,
        profileId: Long,
    ): CanonicalResumeInfo?

    /**
     * Set canonical resume position.
     *
     * Position applies to all sources of the same media.
     *
     * @param canonicalId The canonical media ID
     * @param profileId The user profile ID
     * @param positionMs Current playback position in milliseconds
     * @param durationMs Total duration in milliseconds
     * @param sourceRef The source currently being played
     */
    suspend fun setCanonicalResume(
        canonicalId: CanonicalMediaId,
        profileId: Long,
        positionMs: Long,
        durationMs: Long,
        sourceRef: MediaSourceRef,
    )

    /**
     * Mark canonical media as completed (watched to end).
     *
     * @param canonicalId The canonical media ID
     * @param profileId The user profile ID
     */
    suspend fun markCompleted(
        canonicalId: CanonicalMediaId,
        profileId: Long,
    )

    /**
     * Clear resume position for canonical media.
     *
     * @param canonicalId The canonical media ID
     * @param profileId The user profile ID
     */
    suspend fun clearCanonicalResume(
        canonicalId: CanonicalMediaId,
        profileId: Long,
    )

    /**
     * Get all media with resume positions for a profile.
     *
     * Useful for "Continue Watching" sections.
     *
     * @param profileId The user profile ID
     * @param limit Maximum results
     * @return Media with resume info, sorted by last updated
     */
    suspend fun getResumeList(
        profileId: Long,
        limit: Int = 20,
    ): List<CanonicalMediaWithResume>

    // ========== TMDB Resolution Queries (per TMDB_ENRICHMENT_CONTRACT.md T-17) ==========

    /**
     * Find items with TmdbRef but missing SSOT data (poster, metadata).
     *
     * For DETAILS_BY_ID enrichment: these items have a TMDB ID but haven't
     * been enriched with full details yet.
     *
     * @param limit Maximum number of candidates to return
     * @return Items needing TMDB details fetch
     */
    suspend fun findCandidatesDetailsByIdMissingSsot(limit: Int): List<CanonicalMediaId>

    /**
     * Find items without TmdbRef that are eligible for search.
     *
     * Respects cooldown: only returns items where tmdbNextEligibleAt <= now.
     * For SEARCH_MATCH resolution path.
     *
     * @param limit Maximum number of candidates to return
     * @param now Current timestamp for cooldown comparison
     * @return Items eligible for TMDB search
     */
    suspend fun findCandidatesMissingTmdbRefEligible(
        limit: Int,
        now: Long,
    ): List<CanonicalMediaId>

    /**
     * Mark item as having TMDB details applied.
     *
     * Updates the resolve state to RESOLVED and records the resolution method.
     *
     * @param canonicalId The canonical media ID
     * @param tmdbId The TMDB ID (for cross-reference)
     * @param resolvedBy How the item was resolved
     * @param resolvedAt Timestamp of resolution
     */
    suspend fun markTmdbDetailsApplied(
        canonicalId: CanonicalMediaId,
        tmdbId: TmdbId,
        resolvedBy: String,
        resolvedAt: Long,
    )

    /**
     * Mark a failed resolution attempt.
     *
     * Updates resolve state and sets cooldown for retry.
     *
     * @param canonicalId The canonical media ID
     * @param state The new resolve state (FAILED or AMBIGUOUS)
     * @param reason Description of why resolution failed
     * @param attemptAt Timestamp of the attempt
     * @param nextEligibleAt When the item can be retried
     */
    suspend fun markTmdbResolveAttemptFailed(
        canonicalId: CanonicalMediaId,
        state: String,
        reason: String,
        attemptAt: Long,
        nextEligibleAt: Long,
    )

    /**
     * Mark item as successfully resolved via search.
     *
     * Sets the TmdbRef and updates resolve state to RESOLVED.
     *
     * @param canonicalId The canonical media ID
     * @param tmdbId The resolved TMDB ID
     * @param resolvedAt Timestamp of resolution
     */
    suspend fun markTmdbResolved(
        canonicalId: CanonicalMediaId,
        tmdbId: TmdbId,
        resolvedAt: Long,
    )

    /**
     * Update canonical media with TMDB-enriched metadata.
     *
     * Per TMDB_ENRICHMENT_CONTRACT.md:
     * - T-5/T-6/T-7: SSOT images via ImageRef.TmdbPoster/TmdbBackdrop
     * - Only updates fields that are non-null in enriched metadata
     * - Preserves existing source-specific data
     *
     * @param canonicalId The canonical media ID to update
     * @param enriched The TMDB-enriched metadata (from TmdbMetadataResolver.enrich)
     * @param resolvedBy How the item was resolved (DETAILS_BY_ID or SEARCH_MATCH)
     * @param resolvedAt Timestamp of enrichment
     */
    suspend fun updateTmdbEnriched(
        canonicalId: CanonicalMediaId,
        enriched: NormalizedMediaMetadata,
        resolvedBy: String,
        resolvedAt: Long,
    )

    // ========== Maintenance Operations ==========

    /**
     * Remove orphaned canonical media without any sources.
     *
     * @return Number of entries removed
     */
    suspend fun pruneOrphans(): Int

    /**
     * Verify source availability and update status.
     *
     * @param canonicalId The canonical media to verify
     * @return Number of sources verified
     */
    suspend fun verifySourceAvailability(canonicalId: CanonicalMediaId): Int

    /** Get statistics about canonical media. */
    suspend fun getStats(): CanonicalMediaStats
}

/** Canonical media with all linked sources. */
data class CanonicalMediaWithSources(
    val canonicalId: CanonicalMediaId,
    val canonicalTitle: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: TmdbId?,
    val imdbId: String?,
    val poster: ImageRef?,
    val backdrop: ImageRef?,
    val thumbnail: ImageRef?,
    val plot: String?,
    val rating: Double?,
    val durationMs: Long?,
    val genres: String?,
    val sources: List<MediaSourceRef>,
) {
    /** Best quality source (by priority) */
    val bestSource: MediaSourceRef?
        get() = sources.maxByOrNull { it.priority }

    /** Check if multiple sources are available */
    val hasMultipleSources: Boolean
        get() = sources.size > 1

    /** Group sources by pipeline type */
    val sourcesByType: Map<String, List<MediaSourceRef>>
        get() = sources.groupBy { it.sourceType.name }
}

/**
 * Resume information for canonical media.
 *
 * IMPORTANT: Resume positions are stored both as percentage AND absolute ms.
 * - Use `progressPercent` for cross-source resuming (different files = different durations)
 * - Use `positionMs` + `durationMs` only when resuming the SAME source
 *
 * When switching sources:
 * 1. Check if `lastSourceId` matches current source
 * 2. If same: use `positionMs` directly for frame-accurate resume
 * 3. If different: calculate position = `progressPercent * newSourceDuration`
 */
data class CanonicalResumeInfo(
    val canonicalKey: CanonicalId,
    /** Percentage position (0.0 - 1.0) - PRIMARY for cross-source resume */
    val progressPercent: Float,
    /** Position in ms from LAST source played (use only for same-source resume) */
    val positionMs: Long,
    /** Duration in ms from LAST source played */
    val durationMs: Long,
    /** Last pipeline type used */
    val lastSourceType: String?,
    /** Last source ID used (for same-source detection) */
    val lastSourceId: PipelineItemId?,
    /** Duration of last source in ms (for conversion calculations) */
    val lastSourceDurationMs: Long?,
    val isCompleted: Boolean,
    val watchedCount: Int,
    val updatedAt: Long,
) {
    /** Whether resume is significant (>2% and <95%) */
    val hasSignificantProgress: Boolean
        get() = progressPercent > 0.02f && progressPercent < 0.95f

    /**
     * Calculate resume position for a specific source.
     *
     * @param sourceId The source to resume on
     * @param sourceDurationMs Duration of that source in milliseconds
     * @return Pair of (positionMs, isExact) - isExact=true if same source with frame-accurate
     * resume
     */
    fun calculatePositionForSource(
        sourceId: PipelineItemId,
        sourceDurationMs: Long,
    ): ResumePosition =
        if (sourceId == lastSourceId && lastSourceDurationMs == sourceDurationMs) {
            // Same source with same duration - use exact position
            ResumePosition(positionMs = positionMs, isExact = true, note = null)
        } else {
            // Different source or different duration - use percentage
            val calculatedPosition = (progressPercent * sourceDurationMs).toLong()
            ResumePosition(
                positionMs = calculatedPosition,
                isExact = false,
                note = "Resume approximated from ${formatPercent(progressPercent)}",
            )
        }

    /** Format remaining time as string (based on last source) */
    fun remainingTimeLabel(): String {
        val remainingMs = durationMs - positionMs
        val remainingMins = (remainingMs / 60_000).toInt()
        return when {
            remainingMins >= 60 -> "${remainingMins / 60}h ${remainingMins % 60}m left"
            remainingMins > 0 -> "${remainingMins}m left"
            else -> "Almost done"
        }
    }

    private fun formatPercent(percent: Float): String = "${(percent * 100).toInt()}%"
}

/**
 * Calculated resume position for a specific source.
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

/** Canonical media with resume information. */
data class CanonicalMediaWithResume(
    val media: CanonicalMediaWithSources,
    val resume: CanonicalResumeInfo,
)

/** Statistics about the canonical media database. */
data class CanonicalMediaStats(
    val totalCanonicalMedia: Long,
    val totalSourceRefs: Long,
    val movieCount: Long,
    val episodeCount: Long,
    val withTmdbId: Long,
    val withMultipleSources: Long,
    val orphanedCount: Long,
    val sourcesByType: Map<String, Long>,
)
