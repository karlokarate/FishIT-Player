package com.fishit.player.infra.data.xtream

import kotlinx.coroutines.flow.Flow

/**
 * Data class representing a season in the index.
 */
data class SeasonIndexItem(
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeCount: Int?,
    val name: String?,
    val coverUrl: String?,
    val airDate: String?,
    val lastUpdatedMs: Long,
) {
    /** Check if season index is stale (older than 7 days) */
    val isStale: Boolean
        get() = System.currentTimeMillis() - lastUpdatedMs > SEASON_INDEX_TTL_MS

    companion object {
        const val SEASON_INDEX_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

/**
 * Data class representing an episode in the index.
 */
data class EpisodeIndexItem(
    val seriesId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val sourceKey: String,
    val episodeId: Int?,
    val title: String?,
    val thumbUrl: String?,
    val durationSecs: Int?,
    val plotBrief: String?,
    val rating: Double?,
    val airDate: String?,
    val playbackHintsJson: String?,
    val lastUpdatedMs: Long,
    val playbackHintsUpdatedMs: Long,
) {
    /** Check if episode index is stale (older than 7 days) */
    val isIndexStale: Boolean
        get() = System.currentTimeMillis() - lastUpdatedMs > INDEX_TTL_MS

    /** Check if playback hints are stale (older than 30 days) */
    val arePlaybackHintsStale: Boolean
        get() = playbackHintsUpdatedMs == 0L ||
                System.currentTimeMillis() - playbackHintsUpdatedMs > PLAYBACK_HINTS_TTL_MS

    /** Check if episode is ready for playback (has valid hints) */
    val isPlaybackReady: Boolean
        get() = !playbackHintsJson.isNullOrEmpty() && !arePlaybackHintsStale

    companion object {
        const val INDEX_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        const val PLAYBACK_HINTS_TTL_MS = 30 * 24 * 60 * 60 * 1000L
    }
}

/**
 * Playback hints for an episode.
 *
 * Required for deterministic playback URL construction.
 */
data class EpisodePlaybackHints(
    val episodeId: Int?,
    val streamId: Int?,
    val containerExtension: String?,
    val directUrl: String? = null,
)

/**
 * Repository interface for series season and episode index operations.
 *
 * **Purpose:**
 * - Lazy loading of seasons/episodes (not during initial sync)
 * - Paged episode lists for smooth UI
 * - TTL-based invalidation (7 days index, 30 days playback hints)
 * - Deterministic playback via EnsureEpisodePlaybackReadyUseCase
 *
 * **Architecture:**
 * - Data layer only - no transport/pipeline imports
 * - Works with ObxSeasonIndex and ObxEpisodeIndex entities
 * - Consumed by feature/detail use cases
 *
 * **Typical Flow:**
 * 1. User opens series detail
 * 2. UI calls observeSeasons(seriesId)
 * 3. If empty/stale → LoadSeriesSeasonsUseCase fetches from API
 * 4. User opens season → observeEpisodes(seriesId, seasonNumber, page, pageSize)
 * 5. User plays episode → EnsureEpisodePlaybackReadyUseCase checks hints
 */
interface XtreamSeriesIndexRepository {
    // =========================================================================
    // Season Index
    // =========================================================================

    /**
     * Observe seasons for a series.
     *
     * @param seriesId The series ID
     * @return Flow of season list (ordered by seasonNumber)
     */
    fun observeSeasons(seriesId: Int): Flow<List<SeasonIndexItem>>

    /**
     * Get seasons for a series (one-shot).
     *
     * @param seriesId The series ID
     * @return List of seasons or empty if not cached
     */
    suspend fun getSeasons(seriesId: Int): List<SeasonIndexItem>

    /**
     * Check if seasons are cached and fresh for a series.
     *
     * @param seriesId The series ID
     * @return True if seasons exist and are within TTL
     */
    suspend fun hasFreshSeasons(seriesId: Int): Boolean

    /**
     * Upsert season index batch.
     *
     * @param seriesId The series ID
     * @param seasons List of seasons to upsert
     */
    suspend fun upsertSeasons(seriesId: Int, seasons: List<SeasonIndexItem>)

    /**
     * Delete all seasons for a series.
     *
     * @param seriesId The series ID
     */
    suspend fun deleteSeasons(seriesId: Int)

    // =========================================================================
    // Episode Index
    // =========================================================================

    /**
     * Observe episodes for a season (paged).
     *
     * @param seriesId The series ID
     * @param seasonNumber The season number
     * @param page Page number (0-indexed)
     * @param pageSize Number of episodes per page (default 30)
     * @return Flow of episode list (ordered by episodeNumber)
     */
    fun observeEpisodes(
        seriesId: Int,
        seasonNumber: Int,
        page: Int = 0,
        pageSize: Int = DEFAULT_EPISODE_PAGE_SIZE,
    ): Flow<List<EpisodeIndexItem>>

    /**
     * Get total episode count for a season.
     *
     * @param seriesId The series ID
     * @param seasonNumber The season number
     * @return Total number of episodes
     */
    suspend fun getEpisodeCount(seriesId: Int, seasonNumber: Int): Int

    /**
     * Get episode by source key.
     *
     * @param sourceKey The stable source key (e.g., "xtream:episode:123:1:5")
     * @return Episode or null if not found
     */
    suspend fun getEpisodeBySourceKey(sourceKey: String): EpisodeIndexItem?

    /**
     * Check if episodes are cached and fresh for a season.
     *
     * @param seriesId The series ID
     * @param seasonNumber The season number
     * @return True if episodes exist and are within TTL
     */
    suspend fun hasFreshEpisodes(seriesId: Int, seasonNumber: Int): Boolean

    /**
     * Upsert episode index batch.
     *
     * @param episodes List of episodes to upsert
     */
    suspend fun upsertEpisodes(episodes: List<EpisodeIndexItem>)

    /**
     * Update playback hints for an episode.
     *
     * Called by EnsureEpisodePlaybackReadyUseCase after fetching from API.
     *
     * @param sourceKey The episode source key
     * @param hintsJson Serialized playback hints (null to clear)
     */
    suspend fun updatePlaybackHints(sourceKey: String, hintsJson: String?)

    /**
     * Get playback hints for an episode.
     *
     * @param sourceKey The episode source key
     * @return Playback hints or null if not available
     */
    suspend fun getPlaybackHints(sourceKey: String): EpisodePlaybackHints?

    /**
     * Check if an episode is playback-ready (has fresh hints).
     *
     * @param sourceKey The episode source key
     * @return True if playback can proceed immediately
     */
    suspend fun isPlaybackReady(sourceKey: String): Boolean

    /**
     * Delete all episodes for a season.
     *
     * @param seriesId The series ID
     * @param seasonNumber The season number
     */
    suspend fun deleteEpisodes(seriesId: Int, seasonNumber: Int)

    /**
     * Delete all episode data for a series.
     *
     * @param seriesId The series ID
     */
    suspend fun deleteAllEpisodesForSeries(seriesId: Int)

    // =========================================================================
    // TTL Management
    // =========================================================================

    /**
     * Invalidate all stale season/episode data.
     *
     * Called periodically to clean up expired entries.
     *
     * @return Number of entries deleted
     */
    suspend fun invalidateStaleEntries(): Int

    /**
     * Invalidate all data (e.g., on credential change).
     */
    suspend fun invalidateAll()

    companion object {
        const val DEFAULT_EPISODE_PAGE_SIZE = 30
    }
}
