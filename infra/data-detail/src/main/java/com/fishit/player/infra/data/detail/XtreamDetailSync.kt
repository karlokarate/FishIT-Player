package com.fishit.player.infra.data.detail

import com.fishit.player.core.detail.domain.DetailBundle
import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.detail.domain.SeasonIndexItem
import com.fishit.player.core.detail.domain.UnifiedDetailLoader
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.ids.XtreamIdCodec
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRelationRepository.RelationType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.writer.NxCatalogWriter
import com.fishit.player.infra.data.nx.writer.NxEnrichmentWriter
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.priority.ApiPriorityDispatcher
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfoBlock
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.infra.transport.xtream.XtreamVodInfoBlock
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * XtreamDetailSync — On-demand detail loader for Xtream sources.
 *
 * Implements [UnifiedDetailLoader] for Series, VOD, and Live detail loading.
 *
 * ## Architecture (2-Writer)
 * | Writer | When | What |
 * |--------|------|------|
 * | [NxCatalogWriter] | Catalog Sync + Detail Open | Creates/updates NX_Work + SourceRef + Variant |
 * | [NxEnrichmentWriter] | Detail Open | Enriches EXISTING works with info_call metadata |
 *
 * ## Episode Pipeline Chain (SSOT)
 * When a series detail is opened:
 * 1. `pipelineAdapter.convertEpisodesToRaw(seriesInfo, seriesId, accountLabel)` → `List<RawMediaMetadata>`
 * 2. `normalizer.normalize(raw)` → `NormalizedMediaMetadata`
 * 3. `nxCatalogWriter.ingest(raw, normalized, accountKey)` → NX entities
 * 4. `relationRepository.upsertBatch(relations)` → series↔episode links
 *
 * This uses the SAME pipeline chain that XtreamCatalogSync uses during catalog sync,
 * guaranteeing identical field population. The adapter method avoids duplicate API calls
 * by accepting the already-fetched `XtreamSeriesInfo` and returns `RawMediaMetadata`
 * directly, preventing pipeline DTO leakage to the data layer.
 *
 * @see NxCatalogWriter for entity creation (shared with catalog sync)
 * @see NxEnrichmentWriter for detail-time enrichment of existing works
 */
@Singleton
class XtreamDetailSync
    @Inject
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val pipelineAdapter: XtreamPipelineAdapter,
        private val canonicalMediaRepository: CanonicalMediaRepository,
        private val seriesIndexRepository: XtreamSeriesIndexRepository,
        private val priorityDispatcher: ApiPriorityDispatcher,
        private val enrichmentWriter: NxEnrichmentWriter,
        private val normalizer: MediaMetadataNormalizer,
        private val nxCatalogWriter: NxCatalogWriter,
        private val relationRepository: NxWorkRelationRepository,
        private val sourceRefRepository: NxWorkSourceRefRepository,
    ) : UnifiedDetailLoader {
        companion object {
            private const val TAG = "XtreamDetailSync"
            private const val API_TIMEOUT_MS = 12_000L

            /** Split a comma-separated string into a trimmed, non-blank list. */
            private fun String?.splitAndTrim(): List<String>? =
                this
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.ifEmpty { null }
        }

        // =========================================================================
        // Deduplication: Prevents concurrent API calls for the same media
        // =========================================================================

        private val inflightRequests = ConcurrentHashMap<String, Deferred<DetailBundle?>>()
        private val inflightMutex = Mutex()

        // =========================================================================
        // Public API: UnifiedDetailLoader Interface
        // =========================================================================

        override suspend fun loadDetailImmediate(media: CanonicalMediaWithSources): DetailBundle? {
            val startMs = System.currentTimeMillis()

            val cacheKey =
                getCacheKey(media) ?: run {
                    UnifiedLog.d(TAG) { "loadDetailImmediate: no Xtream source for ${media.canonicalId.key.value}" }
                    return null
                }

            if (isDetailFresh(media)) {
                val cachedBundle = buildBundleFromCache(media)
                if (cachedBundle != null) {
                    UnifiedLog.d(TAG) { "loadDetailImmediate: using cached data for $cacheKey (fast path)" }
                    return cachedBundle
                }
                UnifiedLog.d(TAG) { "loadDetailImmediate: cache fresh but empty for $cacheKey, forcing API call" }
            }

            inflightRequests[cacheKey]?.let { existingDeferred ->
                UnifiedLog.d(TAG) { "loadDetailImmediate: joining inflight request for $cacheKey" }
                return existingDeferred.await()
            }

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

            if (isDetailFresh(media)) {
                val cachedBundle = buildBundleFromCache(media)
                if (cachedBundle != null) return cachedBundle
            }

            return priorityDispatcher.withCriticalPriority(
                tag = "EnsureDetail:$cacheKey",
                timeoutMs = timeoutMs,
            ) {
                withTimeout(timeoutMs) {
                    loadDetailDeduped(media, cacheKey, System.currentTimeMillis())
                }
            }
        }

        override suspend fun isDetailFresh(media: CanonicalMediaWithSources): Boolean =
            when (media.mediaType) {
                MediaType.SERIES -> isSeriesDetailFresh(media)
                MediaType.MOVIE -> isVodDetailFresh(media)
                MediaType.LIVE -> true
                else -> true
            }

        override fun observeDetail(media: CanonicalMediaWithSources): Flow<DetailBundle?> =
            flow {
                val bundle = loadDetailImmediate(media)
                emit(bundle)
            }

        // =========================================================================
        // Direct ID-based loading (delegation from deprecated APIs)
        // =========================================================================

        override suspend fun loadSeriesDetailBySeriesId(seriesId: Int): DetailBundle.Series? {
            UnifiedLog.d(TAG) { "loadSeriesDetailBySeriesId: seriesId=$seriesId" }

            val sourceId =
                com.fishit.player.core.model.ids
                    .PipelineItemId(XtreamIdCodec.series(seriesId))
            val media = canonicalMediaRepository.findBySourceId(sourceId)

            if (media != null) {
                if (media.mediaType != MediaType.SERIES) {
                    UnifiedLog.w(TAG) { "loadSeriesDetailBySeriesId: type=${media.mediaType}, expected SERIES" }
                    return null
                }
                return loadDetailImmediate(media) as? DetailBundle.Series
            }

            UnifiedLog.w(TAG) { "loadSeriesDetailBySeriesId: no CanonicalMedia found for seriesId=$seriesId" }
            return null
        }

        override suspend fun loadVodDetailByVodId(vodId: Int): DetailBundle.Vod? {
            UnifiedLog.d(TAG) { "loadVodDetailByVodId: vodId=$vodId" }

            val sourceId =
                com.fishit.player.core.model.ids
                    .PipelineItemId(XtreamIdCodec.vod(vodId))
            val media = canonicalMediaRepository.findBySourceId(sourceId)

            if (media != null) {
                if (media.mediaType != MediaType.MOVIE) {
                    UnifiedLog.w(TAG) { "loadVodDetailByVodId: type=${media.mediaType}, expected MOVIE" }
                    return null
                }
                return loadDetailImmediate(media) as? DetailBundle.Vod
            }

            UnifiedLog.w(TAG) { "loadVodDetailByVodId: no CanonicalMedia found for vodId=$vodId" }
            return null
        }

        // =========================================================================
        // Private: Deduplication
        // =========================================================================

        private suspend fun loadDetailDeduped(
            media: CanonicalMediaWithSources,
            cacheKey: String,
            startMs: Long,
        ): DetailBundle? {
            // Acquire lock ONLY for map management, NOT during await().
            // Previous version held inflightMutex across the entire await(),
            // serializing ALL concurrent detail loads even for different cache keys.
            val deferred =
                inflightMutex.withLock {
                    inflightRequests[cacheKey]?.let { existingDeferred ->
                        UnifiedLog.d(TAG) { "loadDetailDeduped: joining inflight for $cacheKey" }
                        return@withLock existingDeferred
                    }

                    // CoroutineScope(coroutineContext) — no SupervisorJob! Properly parented
                    // to caller's Job for structured concurrency. Cancellation propagates correctly.
                    CoroutineScope(coroutineContext)
                        .async {
                            try {
                                loadDetailInternal(media, cacheKey)
                            } catch (e: Exception) {
                                UnifiedLog.e(TAG, "loadDetailInternal failed", e)
                                null
                            }
                        }.also { inflightRequests[cacheKey] = it }
                }

            // Await OUTSIDE the lock — parallel loads for different keys proceed concurrently.
            return try {
                val result = deferred.await()
                val durationMs = System.currentTimeMillis() - startMs
                UnifiedLog.i(TAG) {
                    "loadDetailDeduped: completed in ${durationMs}ms for $cacheKey hasData=${result != null}"
                }
                result
            } finally {
                inflightMutex.withLock { inflightRequests.remove(cacheKey) }
            }
        }

        private suspend fun loadDetailInternal(
            media: CanonicalMediaWithSources,
            cacheKey: String,
        ): DetailBundle? =
            when (media.mediaType) {
                MediaType.SERIES -> loadSeriesDetailInternal(media, cacheKey)
                MediaType.MOVIE -> loadVodDetailInternal(media, cacheKey)
                MediaType.LIVE -> loadLiveDetailInternal(media)
                else -> null
            }

        // =========================================================================
        // SERIES: Pipeline chain → NxCatalogWriter
        // =========================================================================

        private suspend fun loadSeriesDetailInternal(
            media: CanonicalMediaWithSources,
            cacheKey: String,
        ): DetailBundle.Series? {
            val seriesId = parseSeriesId(cacheKey) ?: return null
            val now = System.currentTimeMillis()

            UnifiedLog.d(TAG) { "loadSeriesDetailInternal: fetching seriesId=$seriesId" }

            // ONE API CALL
            val seriesInfo: XtreamSeriesInfo =
                withTimeout(API_TIMEOUT_MS) {
                    xtreamApiClient.getSeriesInfo(seriesId)
                } ?: run {
                    UnifiedLog.w(TAG) { "loadSeriesDetailInternal: API returned null for seriesId=$seriesId" }
                    return null
                }

            val info = seriesInfo.info

            // 1. PERSIST EPISODES via pipeline chain (SSOT) — episodes are child works
            //    that must exist BEFORE the parent is enriched, so that field inheritance
            //    from parent to children can run in a single pass.
            val seriesWorkKey = media.canonicalId.key.value
            val accountKey =
                sourceRefRepository
                    .findByWorkKey(seriesWorkKey)
                    .firstOrNull { it.sourceType == NxWorkSourceRefRepository.SourceType.XTREAM }
                    ?.accountKey
                    ?: "xtream:unknown"
            val accountLabel = accountKey.removePrefix("xtream:")

            // Pipeline chain: pipelineAdapter.convertEpisodesToRaw → normalizer → NxCatalogWriter
            // Uses already-fetched seriesInfo to avoid duplicate API call
            val rawEpisodes = pipelineAdapter.convertEpisodesToRaw(seriesInfo, seriesId, accountLabel)
            val episodeWorkKeys = mutableListOf<Pair<String, Int>>() // workKey to episodeIndex

            if (rawEpisodes.isNotEmpty()) {
                for ((index, raw) in rawEpisodes.withIndex()) {
                    val normalized = normalizer.normalize(raw)
                    val workKey = nxCatalogWriter.ingest(raw, normalized, accountKey)
                    if (workKey != null) {
                        episodeWorkKeys.add(workKey to index)
                    }
                }

                // Create series→episode relations
                val relations =
                    episodeWorkKeys.map { (workKey, index) ->
                        val raw = rawEpisodes[index]
                        NxWorkRelationRepository.Relation(
                            parentWorkKey = seriesWorkKey,
                            childWorkKey = workKey,
                            relationType = RelationType.SERIES_EPISODE,
                            seasonNumber = raw.season,
                            episodeNumber = raw.episode,
                            orderIndex = (raw.season ?: 0) * 1000 + (raw.episode ?: 0),
                        )
                    }
                relationRepository.upsertBatch(relations)

                // NOTE: Episode tmdbIds are already persisted via the pipeline chain.
                // toRawMediaMetadata() sets PlaybackHintKeys.Xtream.EPISODE_TMDB_ID,
                // which normalizer + NxCatalogWriter handle correctly.

                UnifiedLog.i(TAG) {
                    "loadSeriesDetailInternal: persisted ${episodeWorkKeys.size}/${rawEpisodes.size} episodes " +
                        "via pipeline chain for series $seriesId"
                }
            }

            // 2. ENRICH parent series work via NxEnrichmentWriter — runs AFTER episodes
            //    are persisted so the parent has the full info_call metadata before inheritance.
            if (info != null) {
                persistSeriesMetadata(media, info)
            }

            // 3. INHERIT parent fields to child episode works — propagates enriched fields
            //    (poster, backdrop, genres, rating, etc.) from the series to episodes
            //    that lack those fields. Uses enrichIfAbsent semantics (no overwrites).
            if (episodeWorkKeys.isNotEmpty()) {
                enrichmentWriter.inheritParentFields(seriesWorkKey)
            }

            // 4. SEASONS from API response (parsing only, no entity creation)
            val seasons = extractSeasons(seriesInfo, seriesId, now)

            // 5. BUILD RETURN BUNDLE — episodes from DB (just written by NxCatalogWriter)
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
                plot = info?.resolvedPlot,
                genres = info?.resolvedGenre.splitAndTrim(),
                director = info?.director?.takeIf { it.isNotBlank() },
                cast = info?.resolvedCast.splitAndTrim(),
                rating = info?.rating?.toDoubleOrNull() ?: info?.rating5Based?.let { it * 2.0 },
                poster = info?.resolvedPoster?.let { ImageRef.Http(it) },
                backdrop = info?.backdropPath?.let { ImageRef.Http(it) },
                trailer = info?.resolvedTrailer,
                tmdbId = info?.tmdbId?.toIntOrNull(),
                imdbId = info?.imdbId?.takeIf { it.isNotBlank() },
                seasons = seasons,
                episodesBySeason = episodesBySeason,
                totalEpisodeCount = totalEpisodeCount,
                fetchedAtMs = now,
            )
        }

        /**
         * Enrich existing series NX_Work with metadata from `get_series_info`.
         *
         * Delegates to [NxEnrichmentWriter.enrichWork] with enrichIfAbsent semantics.
         */
        private suspend fun persistSeriesMetadata(
            media: CanonicalMediaWithSources,
            info: XtreamSeriesInfoBlock,
        ) {
            val normalized =
                buildDetailMetadata(
                    media = media,
                    mediaType = MediaType.SERIES,
                    tmdbMediaType = TmdbMediaType.TV,
                    tmdbIdStr = info.tmdbId,
                    imdbId = info.imdbId,
                    year = info.year,
                    poster = info.resolvedPoster,
                    backdrop = info.backdropPath,
                    plot = info.resolvedPlot,
                    genres = info.resolvedGenre,
                    director = info.director,
                    cast = info.resolvedCast,
                    rating = info.rating,
                    rating5Based = info.rating5Based,
                    trailer = info.resolvedTrailer,
                    durationMs = media.durationMs,
                )
            enrichmentWriter.enrichWork(media.canonicalId.key.value, normalized)
        }

        private fun extractSeasons(
            seriesInfo: XtreamSeriesInfo,
            seriesId: Int,
            now: Long,
        ): List<SeasonIndexItem> {
            return seriesInfo.seasons
                ?.mapNotNull { season ->
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

        // =========================================================================
        // VOD: One API call → metadata + playback hints
        // =========================================================================

        private suspend fun loadVodDetailInternal(
            media: CanonicalMediaWithSources,
            cacheKey: String,
        ): DetailBundle.Vod? {
            val vodId = parseVodId(cacheKey) ?: return null
            val now = System.currentTimeMillis()

            val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
            val vodKind = xtreamSource?.playbackHints?.get(PlaybackHintKeys.Xtream.VOD_KIND)

            UnifiedLog.d(TAG) { "loadVodDetailInternal: fetching vodId=$vodId with kind=$vodKind" }

            val vodInfo: XtreamVodInfo =
                withTimeout(API_TIMEOUT_MS) {
                    xtreamApiClient.getVodInfo(vodId, vodKind)
                } ?: run {
                    UnifiedLog.w(TAG) { "loadVodDetailInternal: API returned null for vodId=$vodId kind=$vodKind" }
                    return null
                }

            val info = vodInfo.info
            val movieData = vodInfo.movieData

            if (info != null) {
                persistVodMetadata(media, info, movieData)
            }

            return DetailBundle.Vod(
                vodId = vodId,
                plot = info?.resolvedPlot,
                genres = info?.resolvedGenre.splitAndTrim(),
                director = info?.director?.takeIf { it.isNotBlank() },
                cast = info?.resolvedCast.splitAndTrim(),
                rating = info?.rating?.toDoubleOrNull() ?: info?.rating5Based?.let { it * 2.0 },
                durationMs = info?.durationSecs?.let { it * 1000L },
                poster = info?.resolvedPoster?.let { ImageRef.Http(it) },
                backdrop = info?.backdropPath?.let { ImageRef.Http(it) },
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
            val normalized =
                buildDetailMetadata(
                    media = media,
                    mediaType = MediaType.MOVIE,
                    tmdbMediaType = TmdbMediaType.MOVIE,
                    tmdbIdStr = info.tmdbId,
                    imdbId = info.imdbId,
                    year = info.year,
                    poster = info.resolvedPoster,
                    backdrop = info.backdropPath,
                    plot = info.resolvedPlot,
                    genres = info.resolvedGenre,
                    director = info.director,
                    cast = info.resolvedCast,
                    rating = info.rating,
                    rating5Based = info.rating5Based,
                    trailer = info.resolvedTrailer,
                    durationMs = info.durationSecs?.let { it * 1000L } ?: media.durationMs,
                )

            val workKey = media.canonicalId.key.value
            enrichmentWriter.enrichWork(workKey, normalized)

            if (movieData?.containerExtension != null) {
                updateVodSourcePlaybackHints(media, workKey, movieData)
            }
        }

        private suspend fun updateVodSourcePlaybackHints(
            media: CanonicalMediaWithSources,
            workKey: String,
            movieData: com.fishit.player.infra.transport.xtream.XtreamMovieData,
        ) {
            val xtreamSource =
                media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
                    ?: return

            val hintsUpdate =
                buildMap {
                    put(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
                    movieData.containerExtension?.let { put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it) }
                    movieData.streamId?.let { put(PlaybackHintKeys.Xtream.VOD_ID, it.toString()) }
                    movieData.directSource?.let { put(PlaybackHintKeys.Xtream.DIRECT_SOURCE, it) }
                }

            enrichmentWriter.updateVariantPlaybackHints(
                sourceKey = xtreamSource.sourceId.value,
                workKey = workKey,
                hintsUpdate = hintsUpdate,
            )
        }

        // =========================================================================
        // LIVE: No API call needed
        // =========================================================================

        private suspend fun loadLiveDetailInternal(media: CanonicalMediaWithSources): DetailBundle.Live {
            val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
            val channelId = xtreamSource?.sourceId?.value?.let { parseChannelId(it) } ?: 0

            return DetailBundle.Live(
                channelId = channelId,
                streamUrl = xtreamSource?.playbackHints?.get(PlaybackHintKeys.Xtream.DIRECT_SOURCE),
                epgChannelId = xtreamSource?.playbackHints?.get("epgChannelId"),
                fetchedAtMs = System.currentTimeMillis(),
            )
        }

        // =========================================================================
        // Freshness Checks
        // =========================================================================

        private suspend fun isSeriesDetailFresh(media: CanonicalMediaWithSources): Boolean {
            if (media.plot.isNullOrBlank()) return false
            val seriesId = extractXtreamIdFromMedia(media) ?: return false
            return seriesIndexRepository.hasFreshSeasons(seriesId)
        }

        private fun isVodDetailFresh(media: CanonicalMediaWithSources): Boolean {
            // VOD is "fresh" if we have key detail-enrichment markers:
            // 1. Plot must be present (from info-call)
            // 2. containerExtension must be present (playback-relevant)
            // Note: External IDs (tmdbId/imdbId) are NOT required — many titles on
            // Xtream servers simply don't have them, and requiring them causes an
            // infinite re-fetch loop for those titles.
            if (media.plot.isNullOrBlank()) return false
            val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
            return xtreamSource?.playbackHints?.get(PlaybackHintKeys.Xtream.CONTAINER_EXT) != null
        }

        // =========================================================================
        // Build Bundle from Cache
        // =========================================================================

        private suspend fun buildBundleFromCache(media: CanonicalMediaWithSources): DetailBundle? =
            when (media.mediaType) {
                MediaType.SERIES -> buildSeriesBundleFromCache(media)
                MediaType.MOVIE -> buildVodBundleFromCache(media)
                MediaType.LIVE -> loadLiveDetailInternal(media)
                else -> null
            }

        private suspend fun buildSeriesBundleFromCache(media: CanonicalMediaWithSources): DetailBundle.Series? {
            val seriesId = extractXtreamIdFromMedia(media) ?: return null
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
                genres = media.genres.splitAndTrim(),
                director = media.director,
                cast = media.cast.splitAndTrim(),
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
            val vodId = extractXtreamIdFromMedia(media) ?: return null
            val xtreamSource = media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }

            return DetailBundle.Vod(
                vodId = vodId,
                plot = media.plot,
                genres = media.genres.splitAndTrim(),
                director = media.director,
                cast = media.cast.splitAndTrim(),
                rating = media.rating,
                durationMs = media.durationMs,
                poster = media.poster,
                backdrop = media.backdrop,
                trailer = media.trailer,
                containerExtension = xtreamSource?.playbackHints?.get(PlaybackHintKeys.Xtream.CONTAINER_EXT),
                tmdbId = media.tmdbId?.value,
                imdbId = media.imdbId,
                fetchedAtMs = System.currentTimeMillis(),
            )
        }

        // =========================================================================
        // Parsing Utilities
        // =========================================================================

        private fun getCacheKey(media: CanonicalMediaWithSources): String? {
            val xtreamSource =
                media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
                    ?: return null

            val sourceKey = xtreamSource.sourceId.value

            return when {
                sourceKey.contains(":series:") -> {
                    val seriesId = parseNumericIdFromSourceKey(sourceKey)
                    seriesId?.let { "series:$it" }
                }
                sourceKey.contains(":vod:") -> {
                    val vodId = parseNumericIdFromSourceKey(sourceKey)
                    vodId?.let { "vod:$it" }
                }
                sourceKey.contains(":live:") -> {
                    val channelId = parseNumericIdFromSourceKey(sourceKey)
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

        private fun parseChannelId(sourceKey: String): Int? = parseNumericIdFromSourceKey(sourceKey)

        /**
         * Parse a numeric item ID from a sourceKey.
         *
         * Consolidates parseSeriesIdFromSourceKey, parseVodIdFromSourceKey,
         * parseChannelIdFromSourceKey — all three were identical delegates
         * to [SourceKeyParser.extractNumericItemKey].
         */
        private fun parseNumericIdFromSourceKey(sourceKey: String): Int? = SourceKeyParser.extractNumericItemKey(sourceKey)?.toInt()

        /**
         * Extract the numeric Xtream ID from a [CanonicalMediaWithSources].
         *
         * Consolidates the former `extractSeriesIdFromMedia` / `extractVodIdFromMedia`
         * which were 100% identical (SSOT dedup).
         */
        private fun extractXtreamIdFromMedia(media: CanonicalMediaWithSources): Int? {
            val xtreamSource =
                media.sources.firstOrNull { it.sourceType == SourceType.XTREAM }
                    ?: return null
            return parseNumericIdFromSourceKey(xtreamSource.sourceId.value)
        }

        // =========================================================================
        // Shared Detail Metadata Builder
        // =========================================================================

        /**
         * Build [NormalizedMediaMetadata] from detail API fields.
         *
         * SSOT for the shared construction logic between [persistSeriesMetadata]
         * and [persistVodMetadata]. Eliminates ~85% code duplication.
         *
         * @param media Current canonical media (provides fallback values)
         * @param mediaType SERIES or MOVIE
         * @param tmdbMediaType TV or MOVIE (for TmdbRef)
         * @param durationMs Duration override (series uses media.durationMs, VOD uses info.durationSecs * 1000)
         */
        private fun buildDetailMetadata(
            media: CanonicalMediaWithSources,
            mediaType: MediaType,
            tmdbMediaType: TmdbMediaType,
            tmdbIdStr: String?,
            imdbId: String?,
            year: String?,
            poster: String?,
            backdrop: String?,
            plot: String?,
            genres: String?,
            director: String?,
            cast: String?,
            rating: String?,
            rating5Based: Double?,
            trailer: String?,
            durationMs: Long?,
        ): NormalizedMediaMetadata {
            val tmdbIdFromApi = tmdbIdStr?.toIntOrNull()
            val tmdbRef =
                when {
                    tmdbIdFromApi != null -> TmdbRef(tmdbMediaType, tmdbIdFromApi)
                    media.tmdbId != null -> TmdbRef(tmdbMediaType, media.tmdbId!!.value)
                    else -> null
                }

            return NormalizedMediaMetadata(
                canonicalTitle = media.canonicalTitle,
                mediaType = mediaType,
                year = year?.toIntOrNull() ?: media.year,
                season = media.season,
                episode = media.episode,
                tmdb = tmdbRef,
                externalIds =
                    ExternalIds(
                        tmdb = tmdbRef,
                        imdbId = imdbId?.takeIf { it.isNotBlank() } ?: media.imdbId,
                    ),
                poster = poster?.let { ImageRef.Http(it) } ?: media.poster,
                backdrop = backdrop?.let { ImageRef.Http(it) } ?: media.backdrop,
                thumbnail = media.thumbnail,
                plot = plot ?: media.plot,
                genres = genres ?: media.genres,
                director = director?.takeIf { it.isNotBlank() } ?: media.director,
                cast = cast ?: media.cast,
                rating =
                    rating?.toDoubleOrNull()
                        ?: rating5Based?.let { it * 2.0 }
                        ?: media.rating,
                durationMs = durationMs,
                trailer = trailer ?: media.trailer,
            )
        }

        // =========================================================================
        // Extension: Repository helper
        // =========================================================================

        private suspend fun XtreamSeriesIndexRepository.getEpisodesForSeason(
            seriesId: Int,
            seasonNumber: Int,
        ): List<EpisodeIndexItem> {
            var result: List<EpisodeIndexItem> = emptyList()
            observeEpisodes(seriesId, seasonNumber, page = 0, pageSize = 500)
                .take(1)
                .collect { result = it }
            return result
        }
    }
