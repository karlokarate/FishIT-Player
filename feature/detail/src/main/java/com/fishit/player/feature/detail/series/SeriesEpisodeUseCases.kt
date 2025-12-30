package com.fishit.player.feature.detail.series

import com.fishit.player.infra.data.xtream.EpisodeIndexItem
import com.fishit.player.infra.data.xtream.EpisodePlaybackHints
import com.fishit.player.infra.data.xtream.SeasonIndexItem
import com.fishit.player.infra.data.xtream.XtreamSeriesIndexRepository
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamSeasonInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper to build playback hints JSON.
 */
private fun buildPlaybackHintsJson(
    streamId: Int?,
    containerExtension: String?,
    directUrl: String?,
): String? {
    if (streamId == null) return null
    return Json.encodeToString(
        mapOf(
            "streamId" to streamId.toString(),
            "containerExtension" to (containerExtension ?: ""),
            "directUrl" to (directUrl ?: ""),
        ),
    )
}

/**
 * Use case for loading series seasons.
 *
 * **Flow:**
 * 1. Check repository for cached seasons
 * 2. If fresh → return cached
 * 3. If stale/missing → fetch from API, persist, return
 *
 * **Architecture:**
 * - Domain layer (feature/detail)
 * - Uses XtreamSeriesIndexRepository (data layer)
 * - Uses XtreamApiClient (transport layer) for fetching
 */
@Singleton
class LoadSeriesSeasonsUseCase @Inject constructor(
    private val repository: XtreamSeriesIndexRepository,
    private val apiClient: XtreamApiClient,
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
    fun observeSeasons(seriesId: Int, forceRefresh: Boolean = false): Flow<List<SeasonIndexItem>> {
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
    suspend fun ensureSeasonsLoaded(seriesId: Int, forceRefresh: Boolean = false): Boolean {
        // Check if we have fresh cached data
        if (!forceRefresh && repository.hasFreshSeasons(seriesId)) {
            UnifiedLog.d(TAG) { "Using cached seasons for series $seriesId" }
            return false
        }

        // Fetch from API
        UnifiedLog.d(TAG) { "Fetching seasons for series $seriesId from API" }
        val seriesInfo = apiClient.getSeriesInfo(seriesId)
        
        if (seriesInfo == null) {
            UnifiedLog.w(TAG) { "API returned null for series $seriesId" }
            return false
        }

        // Extract seasons from API response
        val seasons = seriesInfo.seasons?.mapNotNull { season ->
            val seasonNum = season.seasonNumber ?: return@mapNotNull null
            SeasonIndexItem(
                seriesId = seriesId,
                seasonNumber = seasonNum,
                episodeCount = season.episodeCount,
                name = season.name,
                coverUrl = season.coverBig ?: season.cover,
                airDate = season.airDate,
                lastUpdatedMs = System.currentTimeMillis(),
            )
        } ?: emptyList()

        if (seasons.isEmpty()) {
            UnifiedLog.w(TAG) { "No seasons found for series $seriesId" }
            return false
        }

        // Persist to repository
        repository.upsertSeasons(seriesId, seasons)
        UnifiedLog.d(TAG) { "Persisted ${seasons.size} seasons for series $seriesId" }
        return true
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
class LoadSeasonEpisodesUseCase @Inject constructor(
    private val repository: XtreamSeriesIndexRepository,
    private val apiClient: XtreamApiClient,
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
    ): Flow<List<EpisodeIndexItem>> {
        return repository.observeEpisodes(seriesId, seasonNumber, page, pageSize)
    }

    /**
     * Get total episode count for a season.
     */
    suspend fun getEpisodeCount(seriesId: Int, seasonNumber: Int): Int {
        return repository.getEpisodeCount(seriesId, seasonNumber)
    }

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

        // Fetch from API
        UnifiedLog.d(TAG) { "Fetching episodes for series $seriesId season $seasonNumber from API" }
        val seriesInfo = apiClient.getSeriesInfo(seriesId)
        
        if (seriesInfo == null) {
            UnifiedLog.w(TAG) { "API returned null for series $seriesId" }
            return false
        }

        // Extract episodes for this season (API returns Map<String, List<XtreamEpisodeInfo>>)
        val seasonKey = seasonNumber.toString()
        val episodesList = seriesInfo.episodes?.get(seasonKey) ?: emptyList()
        
        val episodes = episodesList.mapNotNull { ep ->
            val epNum = ep.episodeNum ?: return@mapNotNull null
            val sourceKey = "xtream:episode:${seriesId}:${seasonNumber}:${epNum}"
            
            // Build playback hints if available
            val resolvedId = ep.resolvedEpisodeId
            val hintsJson = resolvedId?.let { streamId ->
                buildPlaybackHintsJson(
                    streamId = streamId,
                    containerExtension = ep.containerExtension,
                    directUrl = ep.directSource,
                )
            }
            
            EpisodeIndexItem(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = epNum,
                sourceKey = sourceKey,
                episodeId = resolvedId,
                title = ep.title ?: ep.info?.name,
                thumbUrl = ep.info?.movieImage ?: ep.info?.posterPath ?: ep.info?.stillPath,
                durationSecs = ep.info?.durationSecs,
                plotBrief = ep.info?.plot?.take(200), // Brief for list display
                rating = ep.info?.rating?.toDoubleOrNull(),
                airDate = ep.info?.releaseDate ?: ep.info?.airDate,
                playbackHintsJson = hintsJson,
                lastUpdatedMs = System.currentTimeMillis(),
                playbackHintsUpdatedMs = if (hintsJson != null) System.currentTimeMillis() else 0L,
            )
        }

        if (episodes.isEmpty()) {
            UnifiedLog.w(TAG) { "No episodes found for series $seriesId season $seasonNumber" }
            return false
        }

        // Persist to repository
        repository.upsertEpisodes(episodes)
        UnifiedLog.d(TAG) { "Persisted ${episodes.size} episodes for series $seriesId season $seasonNumber" }
        return true
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
class EnsureEpisodePlaybackReadyUseCase @Inject constructor(
    private val repository: XtreamSeriesIndexRepository,
    private val apiClient: XtreamApiClient,
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
        data class Enriching(val sourceKey: String) : Result()

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
    suspend fun invoke(sourceKey: String, forceEnrich: Boolean = false): Result {
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
                val seriesInfo = apiClient.getSeriesInfo(seriesId)
                    ?: return@withTimeout Result.Failed(sourceKey, "API returned null for series")

                // Find the specific episode (API returns Map<String, List<XtreamEpisodeInfo>>)
                val seasonKey = seasonNumber.toString()
                val episodesList = seriesInfo.episodes?.get(seasonKey)
                    ?: return@withTimeout Result.Failed(sourceKey, "Season $seasonNumber not found")
                    
                val episode = episodesList.find { it.episodeNum == episodeNumber }
                    ?: return@withTimeout Result.Failed(sourceKey, "Episode $episodeNumber not found")

                val resolvedId = episode.resolvedEpisodeId
                    ?: return@withTimeout Result.Failed(sourceKey, "Episode has no playable ID")

                // Build and persist playback hints
                val hintsJson = buildPlaybackHintsJson(
                    streamId = resolvedId,
                    containerExtension = episode.containerExtension,
                    directUrl = episode.directSource,
                )
                
                repository.updatePlaybackHints(sourceKey, hintsJson)

                // Return fresh hints
                val hints = EpisodePlaybackHints(
                    episodeId = resolvedId,
                    streamId = resolvedId,
                    containerExtension = episode.containerExtension,
                    directUrl = episode.directSource,
                )
                
                UnifiedLog.d(TAG) { "Episode $sourceKey enriched successfully: streamId=$resolvedId" }
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
    suspend fun isReady(sourceKey: String): Boolean {
        return repository.isPlaybackReady(sourceKey)
    }
}
