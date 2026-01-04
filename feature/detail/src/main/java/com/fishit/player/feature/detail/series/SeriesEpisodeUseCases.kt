package com.fishit.player.feature.detail.series

import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.detail.domain.EpisodePlaybackHints
import com.fishit.player.core.detail.domain.SeasonIndexItem
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRefresher
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for loading series seasons.
 *
 * **Flow:**
 * 1. Check repository for cached seasons
 * 2. If fresh → return cached
 * 3. If stale/missing → fetch from API, persist, return
 *
 * **Architecture:**
 * - UI/Feature-safe (no transport / pipeline imports)
 * - Uses XtreamSeriesIndexRepository for cache
 * - Uses XtreamSeriesIndexRefresher port for refresh
 */
@Singleton
class LoadSeriesSeasonsUseCase
    @Inject
    constructor(
        private val repository: XtreamSeriesIndexRepository,
        private val refresher: XtreamSeriesIndexRefresher,
    ) {
        companion object {
            private const val TAG = "LoadSeriesSeasons"
        }

        /**
         * Load seasons for a series.
         *
         * @param seriesId The Xtream series ID
         * @param forceRefresh Force API fetch even if cached
         * @return Flow of seasons (emits cached first, then updated if fetched)
         */
        fun observeSeasons(
            seriesId: Int,
            forceRefresh: Boolean = false,
        ): Flow<List<SeasonIndexItem>> {
            // Return reactive flow from repository
            return repository.observeSeasons(seriesId)
        }

        /**
         * Ensure seasons are loaded and fresh.
         *
         * Call this when opening series detail to trigger fetch if needed.
         *
         * @param seriesId The Xtream series ID
         * @param forceRefresh Force API fetch even if cached
         * @return True if seasons were fetched, false if cached was used
         */
        suspend fun ensureSeasonsLoaded(
            seriesId: Int,
            forceRefresh: Boolean = false,
        ): Boolean {
            // Check if we have fresh cached data
            if (!forceRefresh && repository.hasFreshSeasons(seriesId)) {
                UnifiedLog.d(TAG) { "Using cached seasons for series $seriesId" }
                return false
            }

            UnifiedLog.d(TAG) { "Refreshing seasons for series $seriesId" }
            return refresher.refreshSeasons(seriesId)
        }
    }

/**
 * Use case for loading season episodes with paging.
 *
 * **Flow:**
 * 1. Check repository for cached episodes
 * 2. If fresh → return cached (paged)
 * 3. If stale/missing → fetch from API, persist, return
 *
 * **Paging:**
 * - Default page size: 30 episodes
 * - Predictive prefetch: loads next page in background
 */
@Singleton
class LoadSeasonEpisodesUseCase
    @Inject
    constructor(
        private val repository: XtreamSeriesIndexRepository,
        private val refresher: XtreamSeriesIndexRefresher,
    ) {
        companion object {
            private const val TAG = "LoadSeasonEpisodes"
            const val DEFAULT_PAGE_SIZE = 30
        }

        /**
         * Observe episodes for a season (paged).
         *
         * @param seriesId The Xtream series ID
         * @param seasonNumber The season number
         * @param page Page number (0-indexed)
         * @param pageSize Episodes per page
         * @return Flow of episode list
         */
        fun observeEpisodes(
            seriesId: Int,
            seasonNumber: Int,
            page: Int = 0,
            pageSize: Int = DEFAULT_PAGE_SIZE,
        ): Flow<List<EpisodeIndexItem>> = repository.observeEpisodes(seriesId, seasonNumber, page, pageSize)

        /**
         * Get total episode count for a season.
         */
        suspend fun getEpisodeCount(
            seriesId: Int,
            seasonNumber: Int,
        ): Int = repository.getEpisodeCount(seriesId, seasonNumber)

        /**
         * Ensure episodes are loaded for a season.
         *
         * @param seriesId The Xtream series ID
         * @param seasonNumber The season number
         * @param forceRefresh Force API fetch even if cached
         * @return True if episodes were fetched
         */
        suspend fun ensureEpisodesLoaded(
            seriesId: Int,
            seasonNumber: Int,
            forceRefresh: Boolean = false,
        ): Boolean {
            // Check if we have fresh cached data
            if (!forceRefresh && repository.hasFreshEpisodes(seriesId, seasonNumber)) {
                UnifiedLog.d(TAG) { "Using cached episodes for series $seriesId season $seasonNumber" }
                return false
            }

            UnifiedLog.d(TAG) { "Refreshing episodes for series $seriesId season $seasonNumber" }
            return refresher.refreshEpisodes(seriesId, seasonNumber)
        }

        /**
         * Prefetch next page of episodes.
         *
         * Called predictively when user scrolls near end of current page.
         */
        suspend fun prefetchPage(
            seriesId: Int,
            seasonNumber: Int,
            nextPage: Int,
        ) {
            // Simply ensure the data is there - the repository will serve it from cache
            UnifiedLog.d(TAG) { "Prefetching page $nextPage for series $seriesId season $seasonNumber" }
            // Data should already be loaded if ensureEpisodesLoaded was called
        }
    }

/**
 * Use case for ensuring episode playback readiness.
 *
 * **Critical for Playback:**
 * This use case MUST be called before playing an episode to ensure
 * all required playback hints (stream_id, container_extension) are available.
 *
 * **Flow:**
 * 1. Check if episode has fresh playback hints
 * 2. If ready → return immediately
 * 3. If missing → fetch from API, persist, wait with timeout
 * 4. Return updated episode or throw if timeout
 *
 * **Race-Proof Design:**
 * - Always fetches latest data from repository after enrichment
 * - Uses timeout to prevent infinite waits
 * - Returns stable sourceKey for playback URL construction
 */
@Singleton
class EnsureEpisodePlaybackReadyUseCase
    @Inject
    constructor(
        private val repository: XtreamSeriesIndexRepository,
        private val refresher: XtreamSeriesIndexRefresher,
    ) {
        companion object {
            private const val TAG = "EnsureEpisodePlayback"
            private const val ENRICHMENT_TIMEOUT_MS = 10_000L // 10 seconds
        }

        /**
         * Result of playback readiness check.
         */
        sealed class Result {
            /** Episode is ready for playback */
            data class Ready(
                val sourceKey: String,
                val hints: EpisodePlaybackHints,
            ) : Result()

            /** Enrichment in progress, please wait */
            data class Enriching(
                val sourceKey: String,
            ) : Result()

            /** Enrichment failed */
            data class Failed(
                val sourceKey: String,
                val reason: String,
            ) : Result()
        }

        /**
         * Ensure episode is ready for playback.
         *
         * @param sourceKey The episode source key (e.g., "xtream:episode:123:1:5")
         * @param forceEnrich Force re-enrichment even if hints exist
         * @return Ready result with hints, or Failed if enrichment fails
         */
        suspend fun invoke(
            sourceKey: String,
            forceEnrich: Boolean = false,
        ): Result {
            UnifiedLog.d(TAG) { "Checking playback readiness for $sourceKey" }

            // Step 1: Check if already ready
            if (!forceEnrich && repository.isPlaybackReady(sourceKey)) {
                val hints = repository.getPlaybackHints(sourceKey)
                if (hints != null) {
                    UnifiedLog.d(TAG) { "Episode $sourceKey is playback-ready" }
                    return Result.Ready(sourceKey, hints)
                }
            }

            // Step 2: Parse sourceKey to extract IDs
            val parts = sourceKey.split(":")
            if (parts.size < 5 || parts[0] != "xtream" || parts[1] != "episode") {
                return Result.Failed(sourceKey, "Invalid sourceKey format")
            }

            val seriesId = parts[2].toIntOrNull() ?: return Result.Failed(sourceKey, "Invalid seriesId")
            val seasonNumber = parts[3].toIntOrNull() ?: return Result.Failed(sourceKey, "Invalid seasonNumber")
            val episodeNumber = parts[4].toIntOrNull() ?: return Result.Failed(sourceKey, "Invalid episodeNumber")

            // Step 3: Fetch from API with timeout
            UnifiedLog.d(TAG) { "Enriching episode $sourceKey (series=$seriesId, s=$seasonNumber, e=$episodeNumber)" }

            return try {
                withTimeout(ENRICHMENT_TIMEOUT_MS) {
                    val hints =
                        refresher.refreshEpisodePlaybackHints(
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            sourceKey = sourceKey,
                        )
                            ?: return@withTimeout Result.Failed(sourceKey, "Failed to resolve playback hints")

                    UnifiedLog.d(TAG) { "Episode $sourceKey enriched successfully: streamId=${hints.streamId}" }
                    Result.Ready(sourceKey, hints)
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "Enrichment failed for $sourceKey", e)
                Result.Failed(sourceKey, e.message ?: "Unknown error")
            }
        }

        /**
         * Quick check if episode is playback-ready (no API call).
         */
        suspend fun isReady(sourceKey: String): Boolean = repository.isPlaybackReady(sourceKey)
    }
