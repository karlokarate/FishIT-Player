package com.fishit.player.core.detail.domain

import com.fishit.player.core.model.ImageRef
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for detail screen media retrieval.
 *
 * **Architecture (v2 - INV-6 compliant):**
 * - Interface lives in Domain layer (core/detail-domain)
 * - Implementation lives in Data layer (infra/data-nx)
 * - Feature layer (feature/detail) consumes this interface
 *
 * **Returns domain-safe models:**
 * - [DomainDetailMedia] - Aggregated media info with sources
 * - [DomainResumeState] - User watch state
 *
 * **Implementation note:**
 * Backed by NX_* entities (NX_Work, NX_WorkSourceRef, NX_WorkVariant, NX_WorkUserState).
 * The implementation maps NX entities to domain models internally.
 */
interface NxDetailMediaRepository {
    /**
     * Load media details by work key.
     *
     * @param workKey Unique work identifier (NX_Work.workKey)
     * @return Domain model with all sources and variants, null if not found
     */
    suspend fun loadByWorkKey(workKey: String): DomainDetailMedia?

    /**
     * Observe media details reactively.
     *
     * @param workKey Unique work identifier
     * @return Flow that emits when media or its sources change
     */
    fun observeByWorkKey(workKey: String): Flow<DomainDetailMedia?>

    /**
     * Load resume state for a work.
     *
     * @param workKey Work identifier
     * @param profileId Profile for which to get state
     * @return Resume state or null if never watched
     */
    suspend fun loadResumeState(
        workKey: String,
        profileId: Long,
    ): DomainResumeState?

    /**
     * Observe resume state reactively.
     *
     * @param workKey Work identifier
     * @param profileId Profile for which to observe
     * @return Flow that emits when watch state changes
     */
    fun observeResumeState(
        workKey: String,
        profileId: Long,
    ): Flow<DomainResumeState?>

    /**
     * Update resume state after playback.
     *
     * @param workKey Work identifier
     * @param profileId Profile to update
     * @param positionMs Current position in milliseconds
     * @param durationMs Total duration in milliseconds
     * @param sourceKey Source that was used for playback
     * @param sourceType Type of source (TELEGRAM, XTREAM, etc.)
     */
    suspend fun updateResumeState(
        workKey: String,
        profileId: Long,
        positionMs: Long,
        durationMs: Long,
        sourceKey: String,
        sourceType: String,
    )

    /**
     * Mark work as completed.
     *
     * @param workKey Work identifier
     * @param profileId Profile to update
     */
    suspend fun markCompleted(
        workKey: String,
        profileId: Long,
    )

    /**
     * Toggle favorite status.
     *
     * @param workKey Work identifier
     * @param profileId Profile to update
     * @return New favorite state
     */
    suspend fun toggleFavorite(
        workKey: String,
        profileId: Long,
    ): Boolean

    /**
     * Toggle watchlist status.
     *
     * @param workKey Work identifier
     * @param profileId Profile to update
     * @return New watchlist state
     */
    suspend fun toggleWatchlist(
        workKey: String,
        profileId: Long,
    ): Boolean
}

/**
 * Domain model for detail media with sources.
 *
 * This is the domain-safe representation returned by [NxDetailMediaRepository].
 * Feature layer maps this to its UI-specific models.
 */
data class DomainDetailMedia(
    val workKey: String,
    val title: String,
    val mediaType: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: String?,
    val imdbId: String?,
    val poster: ImageRef?,
    val backdrop: ImageRef?,
    val plot: String?,
    val rating: Double?,
    val durationMs: Long?,
    val genres: String?,
    val director: String?,
    val cast: String?,
    val trailer: String?,
    val isAdult: Boolean,
    val sources: List<DomainSourceInfo>,
)

/**
 * Domain model for a playback source.
 */
data class DomainSourceInfo(
    val sourceKey: String,
    val sourceType: String,
    val sourceLabel: String,
    val accountKey: String,
    val qualityTag: String?,
    val width: Int?,
    val height: Int?,
    val videoCodec: String?,
    val containerFormat: String?,
    val fileSizeBytes: Long?,
    val language: String?,
    val priority: Int,
    val isAvailable: Boolean,
    val playbackHints: Map<String, String>,
)

/**
 * Domain model for user watch/resume state.
 */
data class DomainResumeState(
    val workKey: String,
    val profileId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val progressPercent: Float,
    val isCompleted: Boolean,
    val watchCount: Int,
    val lastSourceKey: String?,
    val lastSourceType: String?,
    val lastWatchedAt: Long?,
    val isFavorite: Boolean,
    val inWatchlist: Boolean,
)
