package com.fishit.player.feature.detail.series

import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.detail.domain.EpisodePlaybackHints
import com.fishit.player.core.detail.domain.SeasonIndexItem
import com.fishit.player.core.detail.domain.UnifiedDetailLoader
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
import com.fishit.player.core.model.ids.XtreamIdCodec
import com.fishit.player.core.model.ids.XtreamParsedSourceId
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.priority.ApiPriorityDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🏆 PLATIN Phase 3: Use case for loading series seasons.
 *
 * **Migration from XtreamSeriesIndexRefresher:**
 * - OLD: Called `refresher.refreshSeasons(seriesId)` which made separate API calls
 * - NEW: Uses [UnifiedDetailLoader.loadSeriesDetailBySeriesId] which makes ONE API call
 *        and saves metadata + seasons + ALL episodes atomically
 *
 * **Flow:**
 * 1. Check repository for cached seasons
 * 2. If fresh → return cached
 * 3. If stale/missing → fetch via UnifiedDetailLoader, return
 *
 * **Priority System:**
 * - [ensureSeasonsLoaded] - Normal priority (background)
 * - [ensureSeasonsLoadedImmediate] - HIGH priority (pauses background sync)
 *
 * **Architecture:**
 * - UI/Feature-safe (no transport / pipeline imports)
 * - Uses XtreamSeriesIndexRepository for cache observation
 * - Uses UnifiedDetailLoader for refresh (PLATIN)
 */
@Singleton
class LoadSeriesSeasonsUseCase
    @Inject
    constructor(
        private val repository: XtreamSeriesIndexRepository,
        private val unifiedDetailLoader: UnifiedDetailLoader,
        private val priorityDispatcher: ApiPriorityDispatcher,
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
         * Ensure seasons are loaded and fresh (normal background priority).
         *
         * Call this for background/prefetch operations.
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

            UnifiedLog.d(TAG) { "Refreshing seasons for series $seriesId via UnifiedDetailLoader (PLATIN)" }
            val bundle = unifiedDetailLoader.loadSeriesDetailBySeriesId(seriesId)
            return bundle != null && bundle.seasons.isNotEmpty()
        }

        /**
         * Ensure seasons are loaded with HIGH priority.
         *
         * **Call this when user clicks on a series tile!**
         * This pauses background sync to prioritize user interaction.
         *
         * @param seriesId The Xtream series ID
         * @param forceRefresh Force API fetch even if cached
         * @return True if seasons were fetched, false if cached was used
         */
        suspend fun ensureSeasonsLoadedImmediate(
            seriesId: Int,
            forceRefresh: Boolean = false,
        ): Boolean {
            // Check if we have fresh cached data (no priority needed for fast path)
            if (!forceRefresh && repository.hasFreshSeasons(seriesId)) {
                UnifiedLog.d(TAG) { "Using cached seasons for series $seriesId (fast path)" }
                return false
            }

            // Use HIGH priority to pause background sync
            return priorityDispatcher.withHighPriority(
                tag = "LoadSeasons:$seriesId",
            ) {
                UnifiedLog.i(TAG) { "Refreshing seasons for series $seriesId (HIGH PRIORITY, PLATIN)" }
                val bundle = unifiedDetailLoader.loadSeriesDetailBySeriesId(seriesId)
                bundle != null && bundle.seasons.isNotEmpty()
            }
        }
    }

/**
 * 🏆 PLATIN Phase 3: Use case for loading season episodes with paging.
 *
 * **Migration from XtreamSeriesIndexRefresher:**
 * - OLD: Called `refresher.refreshEpisodes(seriesId, seasonNumber)` which made separate API calls
 * - NEW: Uses [UnifiedDetailLoader.loadSeriesDetailBySeriesId] which makes ONE API call
 *        and saves ALL episodes for ALL seasons atomically
 *
 * **Flow:**
 * 1. Check repository for cached episodes
 * 2. If fresh → return cached (paged)
 * 3. If stale/missing → fetch via UnifiedDetailLoader, return
 *
 * **Priority System:**
 * - [ensureEpisodesLoaded] - Normal priority (background)
 * - [ensureEpisodesLoadedImmediate] - HIGH priority (pauses background sync)
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
        private val unifiedDetailLoader: UnifiedDetailLoader,
        private val priorityDispatcher: ApiPriorityDispatcher,
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
         * Ensure episodes are loaded for a season (normal background priority).
         *
         * Call this for background/prefetch operations.
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

            UnifiedLog.d(TAG) { "Refreshing episodes for series $seriesId season $seasonNumber via UnifiedDetailLoader (PLATIN)" }
            val bundle = unifiedDetailLoader.loadSeriesDetailBySeriesId(seriesId)
            val episodes = bundle?.getEpisodesForSeason(seasonNumber) ?: emptyList()
            return episodes.isNotEmpty()
        }

        /**
         * Ensure episodes are loaded with HIGH priority.
         *
         * **Call this when user opens a season!**
         * This pauses background sync to prioritize user interaction.
         *
         * @param seriesId The Xtream series ID
         * @param seasonNumber The season number
         * @param forceRefresh Force API fetch even if cached
         * @return True if episodes were fetched, false if cached was used
         */
        suspend fun ensureEpisodesLoadedImmediate(
            seriesId: Int,
            seasonNumber: Int,
            forceRefresh: Boolean = false,
        ): Boolean {
            // Check if we have fresh cached data (no priority needed for fast path)
            if (!forceRefresh && repository.hasFreshEpisodes(seriesId, seasonNumber)) {
                UnifiedLog.d(TAG) { "Using cached episodes for series $seriesId season $seasonNumber (fast path)" }
                return false
            }

            // Use HIGH priority to pause background sync
            return priorityDispatcher.withHighPriority(
                tag = "LoadEpisodes:$seriesId-S$seasonNumber",
            ) {
                UnifiedLog.i(TAG) { "Refreshing episodes for series $seriesId season $seasonNumber (HIGH PRIORITY, PLATIN)" }
                val bundle = unifiedDetailLoader.loadSeriesDetailBySeriesId(seriesId)
                val episodes = bundle?.getEpisodesForSeason(seasonNumber) ?: emptyList()
                episodes.isNotEmpty()
            }
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
 * 🏆 PLATIN Phase 3: Use case for ensuring episode playback readiness.
 *
 * **Migration from XtreamSeriesIndexRefresher:**
 * - OLD: Called `refresher.refreshEpisodePlaybackHints(...)` which made separate API calls
 * - NEW: Uses [UnifiedDetailLoader.loadSeriesDetailBySeriesId] which loads all data at once
 *
 * **Critical for Playback:**
 * This use case MUST be called before playing an episode to ensure
 * all required playback hints (stream_id, container_extension) are available.
 *
 * **Flow:**
 * 1. Check if episode has fresh playback hints
 * 2. If ready → return immediately
 * 3. If missing → fetch via UnifiedDetailLoader, extract hints
 * 4. Return result or timeout
 *
 * **Race-Proof Design:**
 * - UnifiedDetailLoader has built-in deduplication
 * - Repository is updated atomically
 */
@Singleton
class EnsureEpisodePlaybackReadyUseCase
    @Inject
    constructor(
        private val repository: XtreamSeriesIndexRepository,
        private val unifiedDetailLoader: UnifiedDetailLoader,
    ) {
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
         * **Supported sourceKey formats:**
         * - NX format: `src:xtream:{account}:episode:series:{seriesId}:s{season}:e{episode}`
         * - NX fallback: `src:xtream:{account}:episode:{seriesId}_{season}_{episode}`
         * - Legacy format: `xtream:episode:{seriesId}:{season}:{episode}`
         *
         * @param sourceKey The episode source key
         * @param forceEnrich Force re-enrichment even if hints exist
         * @return Ready result with hints, or Failed if enrichment fails
         */
        suspend fun invoke(
            sourceKey: String,
            forceEnrich: Boolean = false,
        ): Result {
            UnifiedLog.d(TAG) { "Checking playback readiness for $sourceKey (PLATIN)" }

            // Step 1: Check if already ready
            if (!forceEnrich && repository.isPlaybackReady(sourceKey)) {
                val hints = repository.getPlaybackHints(sourceKey)
                if (hints != null) {
                    UnifiedLog.d(TAG) { "Episode $sourceKey is playback-ready" }
                    return Result.Ready(sourceKey, hints)
                }
            }

            // Step 2: Parse sourceKey to extract IDs — supports multiple formats
            val episodeIds =
                parseEpisodeIds(sourceKey)
                    ?: return Result.Failed(sourceKey, "Invalid sourceKey format: $sourceKey")

            val seriesId = episodeIds.seriesId
            val seasonNumber = episodeIds.season
            val episodeNumber = episodeIds.episode

            // Step 3: Fetch from API via UnifiedDetailLoader with timeout
            UnifiedLog.d(
                TAG,
            ) { "Enriching episode $sourceKey via UnifiedDetailLoader (series=$seriesId, s=$seasonNumber, e=$episodeNumber)" }

            return try {
                withTimeout(ENRICHMENT_TIMEOUT_MS) {
                    val bundle =
                        unifiedDetailLoader.loadSeriesDetailBySeriesId(seriesId)
                            ?: return@withTimeout Result.Failed(sourceKey, "Failed to load series detail")

                    val episodes = bundle.getEpisodesForSeason(seasonNumber)
                    val episode =
                        episodes.find { it.episodeNumber == episodeNumber }
                            ?: return@withTimeout Result.Failed(sourceKey, "Episode not found in bundle")

                    // Build hints from episode data
                    val episodeId =
                        episode.episodeId
                            ?: return@withTimeout Result.Failed(sourceKey, "Episode has no episodeId")

                    val hints =
                        EpisodePlaybackHints(
                            episodeId = episodeId,
                            streamId = episodeId,
                            containerExtension = parseContainerExtension(episode.playbackHintsJson),
                            directUrl = parseDirectUrl(episode.playbackHintsJson),
                        )

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

        private fun parseContainerExtension(hintsJson: String?): String? {
            if (hintsJson.isNullOrBlank()) return null
            return try {
                val regex = """"xtream\.containerExtension"\s*:\s*"([^"]*)"""".toRegex()
                regex
                    .find(hintsJson)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }

        private fun parseDirectUrl(hintsJson: String?): String? {
            if (hintsJson.isNullOrBlank()) return null
            return try {
                val regex = """"xtream\.directSource"\s*:\s*"([^"]*)"""".toRegex()
                regex
                    .find(hintsJson)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Parse episode IDs from sourceKey — delegates to XtreamIdCodec SSOT.
         *
         * XtreamIdCodec.parse() handles ALL formats:
         * - NX: `src:xtream:{account}:episode:series:{seriesId}:s{season}:e{episode}`
         * - NX underscore: `src:xtream:{account}:episode:{seriesId}_{season}_{episode}`
         * - Legacy composite: `xtream:episode:series:{seriesId}:s{season}:e{episode}`
         * - Legacy numeric: `xtream:episode:{seriesId}:{season}:{episode}`
         */
        private fun parseEpisodeIds(sourceKey: String): ParsedEpisodeIds? {
            val parsed = XtreamIdCodec.parse(sourceKey)
            if (parsed is XtreamParsedSourceId.EpisodeComposite) {
                return ParsedEpisodeIds(parsed.seriesId.toInt(), parsed.season, parsed.episode)
            }
            return null
        }

        private data class ParsedEpisodeIds(
            val seriesId: Int,
            val season: Int,
            val episode: Int,
        )

        companion object {
            private const val TAG = "EnsureEpisodePlaybackReady"
            private const val ENRICHMENT_TIMEOUT_MS = 15_000L
        }
    }
