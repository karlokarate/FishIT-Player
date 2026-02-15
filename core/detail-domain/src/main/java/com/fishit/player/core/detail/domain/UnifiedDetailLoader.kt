package com.fishit.player.core.detail.domain

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import kotlinx.coroutines.flow.Flow

/**
 * PLATIN: Unified Detail Loader for Series AND VOD.
 *
 * **Why this exists:**
 * The original v2 architecture made MULTIPLE API calls for series details:
 * 1. `enrichSeriesFromXtream()` → getSeriesInfo → saved metadata (plot, cast, genres)
 * 2. `refreshSeasons()` → getSeriesInfo → saved seasons only
 * 3. `refreshEpisodes()` → getSeriesInfo → saved episodes only
 *
 * This resulted in 3x the same API call, wasting bandwidth and time!
 *
 * **PLATIN Solution:**
 * ONE API call → persist EVERYTHING atomically (metadata + seasons + episodes)
 * With deduplication to prevent concurrent duplicate calls.
 *
 * **Usage:**
 * - Call [loadDetailImmediate] when user clicks a tile (HIGH priority)
 * - Call [ensureDetailLoaded] before playback (CRITICAL priority)
 * - Use [observeDetail] for reactive UI updates
 *
 * **Supported Media Types:**
 * - [MediaType.SERIES] → calls getSeriesInfo, persists metadata + seasons + episodes
 * - [MediaType.MOVIE] → calls getVodInfo, persists metadata + playbackHints
 * - [MediaType.LIVE] → no enrichment needed (stream URL in source already)
 */
interface UnifiedDetailLoader {
    /**
     * Load complete detail data with HIGH priority.
     *
     * **Call this when user clicks on a media tile!**
     * - Pauses background sync
     * - Makes ONE API call
     * - Persists ALL data atomically
     * - Deduplicates concurrent calls for same media
     *
     * @param media The canonical media to load details for
     * @return DetailBundle containing all fetched data, or null if failed
     */
    suspend fun loadDetailImmediate(media: CanonicalMediaWithSources): DetailBundle?

    /**
     * Ensure detail is loaded with CRITICAL priority and timeout.
     *
     * **Call this before playback starts!**
     * - Highest priority (blocks all other operations)
     * - Guarantees playback hints are available
     *
     * @param media The canonical media
     * @param timeoutMs Maximum wait time
     * @return DetailBundle or null if timeout/failure
     */
    suspend fun ensureDetailLoaded(
        media: CanonicalMediaWithSources,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): DetailBundle?

    /**
     * Check if detail is already loaded and fresh (no API call).
     *
     * @param media The canonical media
     * @return True if detail data is cached and not stale
     */
    suspend fun isDetailFresh(media: CanonicalMediaWithSources): Boolean

    /**
     * Observe detail bundle updates reactively.
     *
     * @param media The canonical media
     * @return Flow of DetailBundle updates
     */
    fun observeDetail(media: CanonicalMediaWithSources): Flow<DetailBundle?>

    // =========================================================================
    // PLATIN Phase 2: Direct ID-based loading for delegation from deprecated APIs
    // =========================================================================

    /**
     * Load series detail directly by Xtream series ID.
     *
     * **Use Case:** Called by deprecated [XtreamSeriesIndexRefresher] methods
     * to delegate to PLATIN implementation without breaking existing API.
     *
     * This method:
     * 1. Finds the CanonicalMedia by sourceId "xtream:series:<seriesId>"
     * 2. Delegates to [loadDetailImmediate]
     * 3. Returns the DetailBundle.Series
     *
     * @param seriesId The Xtream series ID
     * @return DetailBundle.Series or null if not found/failed
     */
    suspend fun loadSeriesDetailBySeriesId(seriesId: Int): DetailBundle.Series?

    /**
     * Load VOD detail directly by Xtream VOD ID.
     *
     * **Use Case:** Called by deprecated enrichment methods
     * to delegate to PLATIN implementation without breaking existing API.
     *
     * @param vodId The Xtream VOD ID
     * @return DetailBundle.Vod or null if not found/failed
     */
    suspend fun loadVodDetailByVodId(vodId: Int): DetailBundle.Vod?

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}

/**
 * Unified detail bundle containing ALL data from a single API call.
 *
 * This is what the UI receives - everything it needs in one object.
 */
sealed class DetailBundle {
    /** Timestamp when this data was fetched */
    abstract val fetchedAtMs: Long

    /** Check if data is stale and should be refreshed */
    abstract val isStale: Boolean

    /**
     * VOD (Movie) detail bundle.
     *
     * Contains enriched metadata + playback hints.
     */
    data class Vod(
        val vodId: Int,
        val plot: String?,
        val genres: List<String>?,
        val director: String?,
        val cast: List<String>?,
        val rating: Double?,
        val durationMs: Long?,
        val poster: ImageRef?,
        val backdrop: ImageRef?,
        val trailer: String?,
        val containerExtension: String?,
        val tmdbId: Int?,
        val imdbId: String?,
        override val fetchedAtMs: Long,
    ) : DetailBundle() {
        override val isStale: Boolean
            get() = System.currentTimeMillis() - fetchedAtMs > VOD_TTL_MS

        companion object {
            const val VOD_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        }
    }

    /**
     * Series detail bundle.
     *
     * Contains enriched metadata + ALL seasons + ALL episodes.
     * This is the key improvement - ONE call gets EVERYTHING.
     */
    data class Series(
        val seriesId: Int,
        val plot: String?,
        val genres: List<String>?,
        val director: String?,
        val cast: List<String>?,
        val rating: Double?,
        val poster: ImageRef?,
        val backdrop: ImageRef?,
        val trailer: String?,
        val tmdbId: Int?,
        val imdbId: String?,
        /** All seasons in this series */
        val seasons: List<SeasonIndexItem>,
        /** All episodes, grouped by season number */
        val episodesBySeason: Map<Int, List<EpisodeIndexItem>>,
        /** Total episode count across all seasons */
        val totalEpisodeCount: Int,
        override val fetchedAtMs: Long,
    ) : DetailBundle() {
        override val isStale: Boolean
            get() = System.currentTimeMillis() - fetchedAtMs > SERIES_TTL_MS

        /** Get episodes for a specific season */
        fun getEpisodesForSeason(seasonNumber: Int): List<EpisodeIndexItem> = episodesBySeason[seasonNumber] ?: emptyList()

        /** Get season numbers sorted */
        fun getSeasonNumbers(): List<Int> = seasons.map { it.seasonNumber }.sorted()

        companion object {
            const val SERIES_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        }
    }

    /**
     * Live stream bundle (minimal - no enrichment needed).
     */
    data class Live(
        val channelId: Int,
        val streamUrl: String?,
        val epgChannelId: String?,
        override val fetchedAtMs: Long,
    ) : DetailBundle() {
        override val isStale: Boolean = false // Live streams don't go stale
    }
}
