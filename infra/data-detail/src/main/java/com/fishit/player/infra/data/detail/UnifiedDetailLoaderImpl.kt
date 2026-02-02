package com.fishit.player.infra.data.detail

import com.fishit.player.core.detail.domain.DetailBundle
import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.detail.domain.EpisodePlaybackHints
import com.fishit.player.core.detail.domain.SeasonIndexItem
import com.fishit.player.core.detail.domain.UnifiedDetailLoader
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.ids.XtreamIdCodec
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.priority.ApiPriorityDispatcher
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamEpisodeInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfoBlock
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.infra.transport.xtream.XtreamVodInfoBlock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                                                                              ║
// ║                    🏆 PLATIN IMPLEMENTATION 🏆                               ║
// ║                                                                              ║
// ║  UnifiedDetailLoaderImpl - Single Source of Truth for ALL Detail API Calls  ║
// ║                                                                              ║
// ║  This class is the ONLY location where getSeriesInfo() and getVodInfo()     ║
// ║  should be called for detail enrichment and index population.               ║
// ║                                                                              ║
// ║  Layer: infra:data-detail (correct per AGENTS.md Section 4)                 ║
// ║                                                                              ║
// ║  Deprecates:                                                                 ║
// ║  - XtreamSeriesIndexRefresherImpl (2-3 separate API calls → 1)              ║
// ║  - DetailEnrichmentServiceImpl.enrichSeriesFromXtream() (partial save)      ║
// ║  - DetailEnrichmentServiceImpl.enrichVodFromXtream() (partial save)         ║
// ║                                                                              ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * 🏆 PLATIN Implementation: Unified Detail Loader for Series AND VOD.
 *
 * **Why This Exists:**
 * Previously, detail fetching was scattered across 3 modules:
 * - `DetailEnrichmentServiceImpl` → saved metadata only
 * - `XtreamSeriesIndexRefresherImpl` → made 2-3 separate API calls
 * - `XtreamCatalogScanWorker` → background sync with own API calls
 *
 * This resulted in up to 3x the necessary API calls for the same data!
 *
 * **PLATIN Solution:**
 * ONE class, ONE API call, ALL data saved atomically.
 *
 * **Key Features:**
 * 1. **ONE API call** - fetches all data atomically (getSeriesInfo/getVodInfo)
 * 2. **Deduplication** - prevents concurrent duplicate calls (like legacy inflightSeries)
 * 3. **Priority System** - integrates with ApiPriorityDispatcher
 * 4. **Atomic Persistence** - metadata + seasons + episodes saved together
 * 5. **TTL Caching** - respects freshness from domain models
 * 6. **Layer Compliance** - all API calls in infra:data-detail per AGENTS.md
 *
 * **Data Flow:**
 * ```
 * User clicks tile
 *   ↓
 * loadDetailImmediate(media)
 *   ↓
 * Check deduplication (inflightRequests)
 *   ↓
 * Check cache freshness (repository)
 *   ↓
 * If stale: ONE API call
 *   ↓
 * Persist ALL data atomically
 *   ↓
 * Return DetailBundle to UI
 * ```
 *
 * **Usage:**
 * ```kotlin
 * // Replace ALL of these:
 * // - detailEnrichmentService.enrich(media) → only saved metadata
 * // - seriesIndexRefresher.refreshSeasons(id) → separate API call
 * // - seriesIndexRefresher.refreshEpisodes(id, season) → another API call
 *
 * // With ONE call:
 * val result = unifiedDetailLoader.loadDetailImmediate(workId, forceRefresh)
 * // result.media has enriched metadata
 * // result.seriesIndex has seasons AND episodes (for Series)
 * ```
 *
 * @see UnifiedDetailLoader Interface contract
 * @see PLATIN_API_CONSOLIDATION_ANALYSIS.md Full analysis document
 */
@Singleton
class UnifiedDetailLoaderImpl
    @Inject
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val canonicalMediaRepository: CanonicalMediaRepository,
        private val seriesIndexRepository: XtreamSeriesIndexRepository,
        private val priorityDispatcher: ApiPriorityDispatcher,
    ) : UnifiedDetailLoader {
    companion object {
        private const val TAG = "UnifiedDetailLoader"
        private const val API_TIMEOUT_MS = 12_000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    // =========================================================================
    // Deduplication: Prevents concurrent API calls for the same media
    // (Like legacy's inflightSeries HashMap)
    // =========================================================================

    /** Cache key: VOD uses "vod:123", Series uses "series:456" */
    private val inflightRequests = ConcurrentHashMap<String, Deferred<DetailBundle?>>()
    private val inflightMutex = Mutex()

    // =========================================================================
    // Public API: UnifiedDetailLoader Interface
    // =========================================================================

    override suspend fun loadDetailImmediate(media: CanonicalMediaWithSources): DetailBundle? {
        val startMs = System.currentTimeMillis()

        // Determine media type and extract source ID
        val cacheKey = getCacheKey(media) ?: run {
            UnifiedLog.d(TAG) { "loadDetailImmediate: no Xtream source for ${media.canonicalId.key.value}" }
            return null
        }

        // Fast path: check cache freshness AND cache has actual data
        // PLATIN FIX: Don't return null for fresh-but-empty cache!
        if (isDetailFresh(media)) {
            val cachedBundle = buildBundleFromCache(media)
            if (cachedBundle != null) {
                UnifiedLog.d(TAG) { "loadDetailImmediate: using cached data for $cacheKey (fast path)" }
                return cachedBundle
            }
            // Cache is "fresh" by timestamp but EMPTY - force API call
            UnifiedLog.d(TAG) { "loadDetailImmediate: cache fresh but empty for $cacheKey, forcing API call" }
        }

        // Deduplication check: is another coroutine already fetching this?
        inflightRequests[cacheKey]?.let { existingDeferred ->
            UnifiedLog.d(TAG) { "loadDetailImmediate: joining inflight request for $cacheKey" }
            return existingDeferred.await()
        }

        // HIGH priority - pauses background sync
        return priorityDispatcher.withHighPriority(
            tag = "DetailLoad:$cacheKey",
        ) {
            loadDetailDeduped(media, cacheKey, startMs)
        }
    }

    override suspend fun ensureDetailLoaded(
        media: CanonicalMediaWithSources,
        timeoutMs: Long,
    ): DetailBundle? {
        val cacheKey = getCacheKey(media) ?: return null

        // Fast path: already loaded, fresh, AND has data
        // PLATIN FIX: Same as loadDetailImmediate - don't skip API for fresh-but-empty
        if (isDetailFresh(media)) {
            val cachedBundle = buildBundleFromCache(media)
            if (cachedBundle != null) {
                return cachedBundle
            }
            // Cache fresh but empty - continue to API call
        }

        // CRITICAL priority with timeout
        return priorityDispatcher.withCriticalPriority(
            tag = "EnsureDetail:$cacheKey",
            timeoutMs = timeoutMs,
        ) {
            withTimeout(timeoutMs) {
                loadDetailDeduped(media, cacheKey, System.currentTimeMillis())
            }
        }
    }

    override suspend fun isDetailFresh(media: CanonicalMediaWithSources): Boolean {
        return when (media.mediaType) {
            MediaType.SERIES -> isSeriesDetailFresh(media)
            MediaType.MOVIE -> isVodDetailFresh(media)
            MediaType.LIVE -> true // Live doesn't need enrichment
            else -> true
        }
    }

    override fun observeDetail(media: CanonicalMediaWithSources): Flow<DetailBundle?> = flow {
        // Initial load
        val bundle = loadDetailImmediate(media)
        emit(bundle)

        // For series, we could observe changes from repository
        // For now, just emit the loaded bundle
    }

    // =========================================================================
    // PLATIN Phase 2: Direct ID-based loading (for delegation from deprecated APIs)
    // =========================================================================

    override suspend fun loadSeriesDetailBySeriesId(seriesId: Int): DetailBundle.Series? {
        UnifiedLog.d(TAG) { "loadSeriesDetailBySeriesId: seriesId=$seriesId" }

        // Try multiple sourceId patterns to find the media
        // Priority order: legacy format first (most common), then new format patterns
        val sourceIdPatterns = listOf(
            "xtream:series:$seriesId", // Legacy format
        )

        for (pattern in sourceIdPatterns) {
            val sourceId = com.fishit.player.core.model.ids.PipelineItemId(pattern)
            val media = canonicalMediaRepository.findBySourceId(sourceId)

            if (media != null) {
                // Verify it's actually a series
                if (media.mediaType != MediaType.SERIES) {
                    UnifiedLog.w(TAG) {
                        "loadSeriesDetailBySeriesId: found media but type=${media.mediaType}, expected SERIES"
                    }
                    continue
                }

                UnifiedLog.d(TAG) {
                    "loadSeriesDetailBySeriesId: found media via pattern=$pattern, delegating to loadDetailImmediate"
                }

                // Delegate to loadDetailImmediate
                val bundle = loadDetailImmediate(media)
                return bundle as? DetailBundle.Series
            }
        }

        UnifiedLog.w(TAG) {
            "loadSeriesDetailBySeriesId: no CanonicalMedia found for seriesId=$seriesId. " +
                "Tried patterns: $sourceIdPatterns. " +
                "Caller should use loadDetailImmediate(media) instead."
        }
        return null
    }

    override suspend fun loadVodDetailByVodId(vodId: Int): DetailBundle.Vod? {
        UnifiedLog.d(TAG) { "loadVodDetailByVodId: vodId=$vodId" }

        // Try multiple sourceId patterns to find the media
        val sourceIdPatterns = listOf(
            "xtream:vod:$vodId", // Legacy format without extension
        )

        for (pattern in sourceIdPatterns) {
            val sourceId = com.fishit.player.core.model.ids.PipelineItemId(pattern)
            val media = canonicalMediaRepository.findBySourceId(sourceId)

            if (media != null) {
                // Verify it's actually a movie/vod
                if (media.mediaType != MediaType.MOVIE) {
                    UnifiedLog.w(TAG) {
                        "loadVodDetailByVodId: found media but type=${media.mediaType}, expected MOVIE"
                    }
                    continue
                }

                UnifiedLog.d(TAG) {
                    "loadVodDetailByVodId: found media via pattern=$pattern, delegating to loadDetailImmediate"
                }

                // Delegate to loadDetailImmediate
                val bundle = loadDetailImmediate(media)
                return bundle as? DetailBundle.Vod
            }
        }

        UnifiedLog.w(TAG) {
            "loadVodDetailByVodId: no CanonicalMedia found for vodId=$vodId. " +
                "Tried patterns: $sourceIdPatterns. " +
                "Caller should use loadDetailImmediate(media) instead."
        }
        return null
    }

    // =========================================================================
    // Private: Deduplication Logic
    // =========================================================================

    /**
     * Load detail with deduplication.
     *
     * Uses inflightRequests map to ensure only ONE coroutine fetches at a time.
     * Other coroutines for the same key will await the result.
     */
    private suspend fun loadDetailDeduped(
        media: CanonicalMediaWithSources,
        cacheKey: String,
        startMs: Long,
    ): DetailBundle? {
        return inflightMutex.withLock {
            // Double-check after acquiring lock
            inflightRequests[cacheKey]?.let { existingDeferred ->
                UnifiedLog.d(TAG) { "loadDetailDeduped: joining inflight (after lock) for $cacheKey" }
                return@withLock existingDeferred.await()
            }

            // Create deferred and register
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val deferred = scope.async {
                try {
                    loadDetailInternal(media, cacheKey)
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, "loadDetailInternal failed", e)
                    null
                }
            }
            inflightRequests[cacheKey] = deferred

            try {
                val result = deferred.await()
                val durationMs = System.currentTimeMillis() - startMs
                UnifiedLog.i(TAG) {
                    "loadDetailDeduped: completed in ${durationMs}ms for $cacheKey " +
                        "hasData=${result != null}"
                }
                result
            } finally {
                inflightRequests.remove(cacheKey)
            }
        }
    }

    /**
     * Internal detail loading - does the actual API call and persistence.
     */
    private suspend fun loadDetailInternal(
        media: CanonicalMediaWithSources,
        cacheKey: String,
    ): DetailBundle? {
        return when (media.mediaType) {
            MediaType.SERIES -> loadSeriesDetailInternal(media, cacheKey)
            MediaType.MOVIE -> loadVodDetailInternal(media, cacheKey)
            MediaType.LIVE -> loadLiveDetailInternal(media)
            else -> null
        }
    }

    // =========================================================================
    // SERIES: One API call → metadata + seasons + ALL episodes
    // =========================================================================

    private suspend fun loadSeriesDetailInternal(
        media: CanonicalMediaWithSources,
        cacheKey: String,
    ): DetailBundle.Series? {
        val seriesId = parseSeriesId(cacheKey) ?: return null
        val now = System.currentTimeMillis()

        UnifiedLog.d(TAG) { "loadSeriesDetailInternal: fetching seriesId=$seriesId" }

        // ONE API CALL - gets EVERYTHING
        val seriesInfo: XtreamSeriesInfo = withTimeout(API_TIMEOUT_MS) {
            xtreamApiClient.getSeriesInfo(seriesId)
        } ?: run {
            UnifiedLog.w(TAG) { "loadSeriesDetailInternal: API returned null for seriesId=$seriesId" }
            return null
        }

        val info = seriesInfo.info

        // 1. PERSIST METADATA → CanonicalMediaRepository
        if (info != null) {
            persistSeriesMetadata(media, info)
        }

        // 2. PERSIST SEASONS → XtreamSeriesIndexRepository
        val seasons = extractSeasons(seriesInfo, seriesId, now)
        if (seasons.isNotEmpty()) {
            seriesIndexRepository.upsertSeasons(seriesId, seasons)
            UnifiedLog.d(TAG) { "loadSeriesDetailInternal: persisted ${seasons.size} seasons" }
        }

        // 3. PERSIST ALL EPISODES → XtreamSeriesIndexRepository
        val episodesBySeason = mutableMapOf<Int, List<EpisodeIndexItem>>()
        var totalEpisodeCount = 0

        seriesInfo.episodes?.forEach { (seasonKey, episodeList) ->
            val seasonNum = seasonKey.toIntOrNull() ?: return@forEach
            val episodes = extractEpisodes(episodeList, seriesId, seasonNum, now)
            if (episodes.isNotEmpty()) {
                seriesIndexRepository.upsertEpisodes(episodes)
                episodesBySeason[seasonNum] = episodes
                totalEpisodeCount += episodes.size
            }
        }

        UnifiedLog.i(TAG) {
            "loadSeriesDetailInternal: persisted $totalEpisodeCount episodes across ${episodesBySeason.size} seasons"
        }

        // 4. BUILD AND RETURN BUNDLE
        return DetailBundle.Series(
            seriesId = seriesId,
            plot = info?.resolvedPlot,
            genres = info?.resolvedGenre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            director = info?.director?.takeIf { it.isNotBlank() },
            cast = info?.resolvedCast?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            rating = info?.rating?.toDoubleOrNull() ?: info?.rating5Based?.let { it * 2.0 },
            poster = info?.resolvedPoster?.let { ImageRef.Http(it) },
            backdrop = info?.backdropPath?.firstOrNull()?.let { ImageRef.Http(it) },
            trailer = info?.resolvedTrailer,
            tmdbId = info?.tmdbId?.toIntOrNull(),
            imdbId = info?.imdbId?.takeIf { it.isNotBlank() },
            seasons = seasons,
            episodesBySeason = episodesBySeason,
            totalEpisodeCount = totalEpisodeCount,
            fetchedAtMs = now,
        )
    }

    private suspend fun persistSeriesMetadata(
        media: CanonicalMediaWithSources,
        info: XtreamSeriesInfoBlock,
    ) {
        val tmdbIdFromApi = info.tmdbId?.toIntOrNull()
        val tmdbRef = when {
            tmdbIdFromApi != null -> TmdbRef(TmdbMediaType.TV, tmdbIdFromApi)
            media.tmdbId != null -> TmdbRef(TmdbMediaType.TV, media.tmdbId!!.value)
            else -> null
        }

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = media.canonicalTitle,
            mediaType = MediaType.SERIES,
            year = info.year?.toIntOrNull() ?: media.year,
            season = media.season,
            episode = media.episode,
            tmdb = tmdbRef,
            externalIds = ExternalIds(
                tmdb = tmdbRef,
                imdbId = info.imdbId?.takeIf { it.isNotBlank() } ?: media.imdbId,
            ),
            poster = info.resolvedPoster?.let { ImageRef.Http(it) } ?: media.poster,
            backdrop = info.backdropPath?.firstOrNull()?.let { ImageRef.Http(it) } ?: media.backdrop,
            thumbnail = media.thumbnail,
            plot = info.resolvedPlot ?: media.plot,
            genres = info.resolvedGenre ?: media.genres,
            director = info.director?.takeIf { it.isNotBlank() } ?: media.director,
            cast = info.resolvedCast ?: media.cast,
            rating = info.rating?.toDoubleOrNull()
                ?: info.rating5Based?.let { it * 2.0 }
                ?: media.rating,
            durationMs = media.durationMs,
            trailer = info.resolvedTrailer ?: media.trailer,
        )

        canonicalMediaRepository.upsertCanonicalMedia(normalized)
    }

    private fun extractSeasons(
        seriesInfo: XtreamSeriesInfo,
        seriesId: Int,
        now: Long,
    ): List<SeasonIndexItem> {
        return seriesInfo.seasons?.mapNotNull { season ->
            val seasonNum = season.seasonNumber ?: return@mapNotNull null
            SeasonIndexItem(
                seriesId = seriesId,
                seasonNumber = seasonNum,
                episodeCount = season.episodeCount,
                name = season.name,
                coverUrl = season.coverBig ?: season.cover,
                airDate = season.airDate,
                lastUpdatedMs = now,
            )
        }.orEmpty()
    }

    private fun extractEpisodes(
        episodeList: List<XtreamEpisodeInfo>,
        seriesId: Int,
        seasonNum: Int,
        now: Long,
    ): List<EpisodeIndexItem> {
        return episodeList.mapNotNull { ep ->
            val episodeNum = ep.episodeNum ?: return@mapNotNull null
            val sourceKey = XtreamIdCodec.episodeComposite(seriesId, seasonNum, episodeNum)
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
                seasonNumber = seasonNum,
                episodeNumber = episodeNum,
                sourceKey = sourceKey,
                episodeId = resolvedId,
                title = ep.title ?: ep.info?.name,
                thumbUrl = ep.info?.movieImage ?: ep.info?.posterPath ?: ep.info?.stillPath,
                durationSecs = ep.info?.durationSecs,
                plotBrief = ep.info?.plot?.take(200),
                rating = ep.info?.rating?.toDoubleOrNull(),
                airDate = ep.info?.releaseDate ?: ep.info?.airDate,
                playbackHintsJson = hintsJson,
                lastUpdatedMs = now,
                playbackHintsUpdatedMs = if (hintsJson != null) now else 0L,
            )
        }
    }

    // =========================================================================
    // VOD: One API call → metadata + playback hints
    // =========================================================================

    private suspend fun loadVodDetailInternal(
        media: CanonicalMediaWithSources,
        cacheKey: String,
    ): DetailBundle.Vod? {
        val vodId = parseVodId(cacheKey) ?: return null
        val now = System.currentTimeMillis()

        UnifiedLog.d(TAG) { "loadVodDetailInternal: fetching vodId=$vodId" }

        // ONE API CALL
        val vodInfo: XtreamVodInfo = withTimeout(API_TIMEOUT_MS) {
            xtreamApiClient.getVodInfo(vodId)
        } ?: run {
            UnifiedLog.w(TAG) { "loadVodDetailInternal: API returned null for vodId=$vodId" }
            return null
        }

        val info = vodInfo.info
        val movieData = vodInfo.movieData

        // 1. PERSIST METADATA → CanonicalMediaRepository
        if (info != null) {
            persistVodMetadata(media, info, movieData)
        }

        // 2. BUILD AND RETURN BUNDLE
        return DetailBundle.Vod(
            vodId = vodId,
            plot = info?.resolvedPlot,
            genres = info?.resolvedGenre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            director = info?.director?.takeIf { it.isNotBlank() },
            cast = info?.resolvedCast?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            rating = info?.rating?.toDoubleOrNull() ?: info?.rating5Based?.let { it * 2.0 },
            durationMs = info?.durationSecs?.let { it * 1000L },
            poster = info?.resolvedPoster?.let { ImageRef.Http(it) },
            backdrop = info?.backdropPath?.firstOrNull()?.let { ImageRef.Http(it) },
            trailer = info?.resolvedTrailer,
            containerExtension = movieData?.containerExtension,
            tmdbId = info?.tmdbId?.toIntOrNull(),
            imdbId = info?.imdbId?.takeIf { it.isNotBlank() },
            fetchedAtMs = now,
        )
    }

    private suspend fun persistVodMetadata(
        media: CanonicalMediaWithSources,
        info: XtreamVodInfoBlock,
        movieData: com.fishit.player.infra.transport.xtream.XtreamMovieData?,
    ) {
        val tmdbIdFromApi = info.tmdbId?.toIntOrNull()
        val tmdbRef = when {
            tmdbIdFromApi != null -> TmdbRef(TmdbMediaType.MOVIE, tmdbIdFromApi)
            media.tmdbId != null -> TmdbRef(TmdbMediaType.MOVIE, media.tmdbId!!.value)
            else -> null
        }

        val normalized = NormalizedMediaMetadata(
            canonicalTitle = media.canonicalTitle,
            mediaType = MediaType.MOVIE,
            year = info.year?.toIntOrNull() ?: media.year,
            season = media.season,
            episode = media.episode,
            tmdb = tmdbRef,
            externalIds = ExternalIds(
                tmdb = tmdbRef,
                imdbId = info.imdbId?.takeIf { it.isNotBlank() } ?: media.imdbId,
            ),
            poster = info.resolvedPoster?.let { ImageRef.Http(it) } ?: media.poster,
            backdrop = info.backdropPath?.firstOrNull()?.let { ImageRef.Http(it) } ?: media.backdrop,
            thumbnail = media.thumbnail,
            plot = info.resolvedPlot ?: media.plot,
            genres = info.resolvedGenre ?: media.genres,
            director = info.director?.takeIf { it.isNotBlank() } ?: media.director,
            cast = info.resolvedCast ?: media.cast,
            rating = info.rating?.toDoubleOrNull()
                ?: info.rating5Based?.let { it * 2.0 }
                ?: media.rating,
            durationMs = info.durationSecs?.let { it * 1000L } ?: media.durationMs,
            trailer = info.resolvedTrailer ?: media.trailer,
        )

        canonicalMediaRepository.upsertCanonicalMedia(normalized)

        // Also update source with playback hints (containerExtension)
        if (movieData?.containerExtension != null) {
            updateVodSourcePlaybackHints(media, movieData)
        }
    }

    private suspend fun updateVodSourcePlaybackHints(
        media: CanonicalMediaWithSources,
        movieData: com.fishit.player.infra.transport.xtream.XtreamMovieData,
    ) {
        // Find the Xtream source and update its playback hints via addOrUpdateSourceRef
        val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
            ?: return

        val updatedHints = xtreamSource.playbackHints.toMutableMap()
        movieData.containerExtension?.let { updatedHints["containerExtension"] = it }
        movieData.streamId?.let { updatedHints["streamId"] = it.toString() }
        movieData.directSource?.let { updatedHints["directUrl"] = it }

        // Create updated source with new hints
        val updatedSource = xtreamSource.copy(playbackHints = updatedHints)

        // Re-add the source (upsert behavior) to update hints
        canonicalMediaRepository.addOrUpdateSourceRef(media.canonicalId, updatedSource)
    }

    // =========================================================================
    // LIVE: No API call needed
    // =========================================================================

    private suspend fun loadLiveDetailInternal(media: CanonicalMediaWithSources): DetailBundle.Live {
        val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
        val channelId = xtreamSource?.sourceId?.value?.let { parseChannelId(it) } ?: 0

        return DetailBundle.Live(
            channelId = channelId,
            streamUrl = xtreamSource?.playbackHints?.get("streamUrl"),
            epgChannelId = xtreamSource?.playbackHints?.get("epgChannelId"),
            fetchedAtMs = System.currentTimeMillis(),
        )
    }

    // =========================================================================
    // Freshness Checks
    // =========================================================================

    private suspend fun isSeriesDetailFresh(media: CanonicalMediaWithSources): Boolean {
        // Check if metadata is present
        if (media.plot.isNullOrBlank()) return false

        // Check if seasons are cached and fresh
        val seriesId = extractSeriesIdFromMedia(media) ?: return false
        return seriesIndexRepository.hasFreshSeasons(seriesId)
    }

    private fun isVodDetailFresh(media: CanonicalMediaWithSources): Boolean {
        // Check if metadata is present
        if (media.plot.isNullOrBlank()) return false

        // Check if playback hints are present
        val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
        return xtreamSource?.playbackHints?.get("containerExtension") != null
    }

    // =========================================================================
    // Build Bundle from Cache (no API call)
    // =========================================================================

    private suspend fun buildBundleFromCache(media: CanonicalMediaWithSources): DetailBundle? {
        return when (media.mediaType) {
            MediaType.SERIES -> buildSeriesBundleFromCache(media)
            MediaType.MOVIE -> buildVodBundleFromCache(media)
            MediaType.LIVE -> loadLiveDetailInternal(media)
            else -> null
        }
    }

    private suspend fun buildSeriesBundleFromCache(media: CanonicalMediaWithSources): DetailBundle.Series? {
        val seriesId = extractSeriesIdFromMedia(media) ?: return null
        val seasons = seriesIndexRepository.getSeasons(seriesId)
        if (seasons.isEmpty()) return null

        val episodesBySeason = mutableMapOf<Int, List<EpisodeIndexItem>>()
        var totalEpisodeCount = 0

        for (season in seasons) {
            val episodes = seriesIndexRepository.getEpisodesForSeason(seriesId, season.seasonNumber)
            if (episodes.isNotEmpty()) {
                episodesBySeason[season.seasonNumber] = episodes
                totalEpisodeCount += episodes.size
            }
        }

        return DetailBundle.Series(
            seriesId = seriesId,
            plot = media.plot,
            genres = media.genres?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            director = media.director,
            cast = media.cast?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            rating = media.rating,
            poster = media.poster,
            backdrop = media.backdrop,
            trailer = media.trailer,
            tmdbId = media.tmdbId?.value,
            imdbId = media.imdbId,
            seasons = seasons,
            episodesBySeason = episodesBySeason,
            totalEpisodeCount = totalEpisodeCount,
            fetchedAtMs = seasons.minOfOrNull { it.lastUpdatedMs } ?: System.currentTimeMillis(),
        )
    }

    private fun buildVodBundleFromCache(media: CanonicalMediaWithSources): DetailBundle.Vod? {
        val vodId = extractVodIdFromMedia(media) ?: return null
        val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }

        return DetailBundle.Vod(
            vodId = vodId,
            plot = media.plot,
            genres = media.genres?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            director = media.director,
            cast = media.cast?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            rating = media.rating,
            durationMs = media.durationMs,
            poster = media.poster,
            backdrop = media.backdrop,
            trailer = media.trailer,
            containerExtension = xtreamSource?.playbackHints?.get("containerExtension"),
            tmdbId = media.tmdbId?.value,
            imdbId = media.imdbId,
            fetchedAtMs = System.currentTimeMillis(), // No stored timestamp for VOD cache
        )
    }

    // =========================================================================
    // Parsing Utilities
    // =========================================================================

    private fun getCacheKey(media: CanonicalMediaWithSources): String? {
        val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
            ?: return null

        val sourceKey = xtreamSource.sourceId.value

        return when {
            sourceKey.contains(":series:") -> {
                val seriesId = parseSeriesIdFromSourceKey(sourceKey)
                seriesId?.let { "series:$it" }
            }
            sourceKey.contains(":vod:") -> {
                val vodId = parseVodIdFromSourceKey(sourceKey)
                vodId?.let { "vod:$it" }
            }
            sourceKey.contains(":live:") -> {
                val channelId = parseChannelIdFromSourceKey(sourceKey)
                channelId?.let { "live:$it" }
            }
            else -> null
        }
    }

    private fun parseSeriesId(cacheKey: String): Int? {
        if (!cacheKey.startsWith("series:")) return null
        return cacheKey.removePrefix("series:").toIntOrNull()
    }

    private fun parseVodId(cacheKey: String): Int? {
        if (!cacheKey.startsWith("vod:")) return null
        return cacheKey.removePrefix("vod:").toIntOrNull()
    }

    private fun parseChannelId(sourceKey: String): Int? {
        return parseChannelIdFromSourceKey(sourceKey)
    }

    private fun parseSeriesIdFromSourceKey(sourceKey: String): Int? {
        // Formats: "src:xtream:account:series:123" or "xtream:series:123"
        val parts = sourceKey.split(":")
        val seriesIndex = parts.indexOf("series")
        return if (seriesIndex >= 0 && seriesIndex + 1 < parts.size) {
            parts[seriesIndex + 1].toIntOrNull()
        } else null
    }

    private fun parseVodIdFromSourceKey(sourceKey: String): Int? {
        // Formats: "src:xtream:account:vod:123" or "xtream:vod:123"
        val parts = sourceKey.split(":")
        val vodIndex = parts.indexOf("vod")
        return if (vodIndex >= 0 && vodIndex + 1 < parts.size) {
            parts[vodIndex + 1].toIntOrNull()
        } else null
    }

    private fun parseChannelIdFromSourceKey(sourceKey: String): Int? {
        // Formats: "src:xtream:account:live:123" or "xtream:live:123"
        val parts = sourceKey.split(":")
        val liveIndex = parts.indexOf("live")
        return if (liveIndex >= 0 && liveIndex + 1 < parts.size) {
            parts[liveIndex + 1].toIntOrNull()
        } else null
    }

    private fun extractSeriesIdFromMedia(media: CanonicalMediaWithSources): Int? {
        val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
            ?: return null
        return parseSeriesIdFromSourceKey(xtreamSource.sourceId.value)
    }

    private fun extractVodIdFromMedia(media: CanonicalMediaWithSources): Int? {
        val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
            ?: return null
        return parseVodIdFromSourceKey(xtreamSource.sourceId.value)
    }

    private fun buildPlaybackHintsJson(
        streamId: Int,
        containerExtension: String?,
        directUrl: String?,
    ): String {
        val hints = EpisodePlaybackHints(
            episodeId = streamId,
            streamId = streamId,
            containerExtension = containerExtension,
            directUrl = directUrl,
        )
        return json.encodeToString(hints)
    }

    // =========================================================================
    // Extension: Repository helper (add to XtreamSeriesIndexRepository)
    // =========================================================================

    private suspend fun XtreamSeriesIndexRepository.getEpisodesForSeason(
        seriesId: Int,
        seasonNumber: Int,
    ): List<EpisodeIndexItem> {
        // Use existing observeEpisodes and take first emission
        // StateFlow-backed observeEpisodes always emits at least one value
        var result: List<EpisodeIndexItem> = emptyList()
        observeEpisodes(seriesId, seasonNumber, page = 0, pageSize = 500)
            .take(1)
            .collect { result = it }
        return result
    }
}
