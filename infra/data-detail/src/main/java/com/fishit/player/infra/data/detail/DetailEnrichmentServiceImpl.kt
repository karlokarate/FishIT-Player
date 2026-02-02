package com.fishit.player.infra.data.detail

import com.fishit.player.core.detail.domain.DetailEnrichmentService
import com.fishit.player.core.detail.domain.UnifiedDetailLoader
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.priority.ApiPriorityDispatcher
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [DetailEnrichmentService].
 *
 * **PLATIN Phase 2:** Now delegates to [UnifiedDetailLoader] for Xtream enrichment.
 *
 * Enriches canonical media with additional metadata from:
 * - ✅ Xtream API via [UnifiedDetailLoader] (PLATIN - ONE API call)
 * - TMDB resolver (future) for media with TMDB IDs
 *
 * **Priority System:**
 * - [enrichImmediate] uses HIGH_USER_ACTION priority (pauses background sync)
 * - [ensureEnriched] uses CRITICAL_PLAYBACK priority (highest, blocks other ops)
 * - [enrichIfNeeded] runs at normal priority (background)
 *
 * **Thread Safety:** Uses per-canonicalId mutexes to prevent concurrent enrichment.
 */
@Singleton
class DetailEnrichmentServiceImpl
    @Inject
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val canonicalMediaRepository: CanonicalMediaRepository,
        private val priorityDispatcher: ApiPriorityDispatcher,
        private val unifiedDetailLoader: UnifiedDetailLoader,
    ) : DetailEnrichmentService {
        companion object {
            private const val TAG = "DetailEnrichment"
        }

        private val enrichmentLocks = mutableMapOf<String, Mutex>()
        private val locksLock = Mutex()

        private suspend fun getMutexForCanonicalId(canonicalId: String): Mutex =
            locksLock.withLock {
                enrichmentLocks.getOrPut(canonicalId) { Mutex() }
            }

        override suspend fun ensureEnriched(
            canonicalId: CanonicalMediaId,
            sourceKey: PipelineItemId?,
            requiredHints: List<String>,
            timeoutMs: Long,
        ): CanonicalMediaWithSources? {
            val startMs = System.currentTimeMillis()

            val media = canonicalMediaRepository.findByCanonicalId(canonicalId)
            if (media == null) {
                UnifiedLog.w(TAG) {
                    "ensureEnriched: media not found canonicalId=${canonicalId.key.value}"
                }
                return null
            }

            // Fast path: check if hints are already present
            val source = sourceKey?.let { key -> media.sources.find { it.sourceId == key } }
            if (source != null && requiredHints.isNotEmpty()) {
                val missingHints = requiredHints.filter { source.playbackHints[it].isNullOrBlank() }
                if (missingHints.isEmpty()) {
                    UnifiedLog.d(TAG) {
                        "ensureEnriched: fast path (hints present) canonicalId=${canonicalId.key.value}"
                    }
                    return media
                }
                UnifiedLog.d(TAG) {
                    "ensureEnriched: missing hints=$missingHints canonicalId=${canonicalId.key.value} sourceKey=${sourceKey.value}"
                }
            }

            // CRITICAL priority - pauses all other operations
            val enriched = priorityDispatcher.withCriticalPriority(
                tag = "EnsureEnriched:${canonicalId.key.value}",
                timeoutMs = timeoutMs,
            ) {
                val mutex = getMutexForCanonicalId(canonicalId.key.value)
                mutex.withLock {
                    val currentMedia = canonicalMediaRepository.findByCanonicalId(canonicalId)
                        ?: return@withCriticalPriority null

                    val currentSource =
                        sourceKey?.let { key -> currentMedia.sources.find { it.sourceId == key } }
                    if (currentSource != null && requiredHints.isNotEmpty()) {
                        val stillMissing = requiredHints.filter {
                            currentSource.playbackHints[it].isNullOrBlank()
                        }
                        if (stillMissing.isEmpty()) {
                            UnifiedLog.d(TAG) {
                                "ensureEnriched: post-lock fast path canonicalId=${canonicalId.key.value}"
                            }
                            return@withLock currentMedia
                        }
                    }

                    enrichIfNeededInternal(currentMedia)
                }
            }

            val durationMs = System.currentTimeMillis() - startMs
            UnifiedLog.d(TAG) {
                "ensureEnriched: completed in ${durationMs}ms canonicalId=${canonicalId.key.value} success=${enriched != null}"
            }

            return enriched ?: canonicalMediaRepository.findByCanonicalId(canonicalId)
        }

        override suspend fun enrichImmediate(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            val startMs = System.currentTimeMillis()

            // Fast path: already enriched
            if (!media.plot.isNullOrBlank()) {
                UnifiedLog.d(TAG) {
                    "enrichImmediate: skipped (already has plot) canonicalId=${media.canonicalId.key.value}"
                }
                return media
            }

            // HIGH priority - pauses background sync
            val result = priorityDispatcher.withHighPriority(
                tag = "DetailEnrich:${media.canonicalId.key.value}",
            ) {
                val mutex = getMutexForCanonicalId(media.canonicalId.key.value)
                mutex.withLock {
                    // Re-check after acquiring lock
                    val currentMedia = canonicalMediaRepository.findByCanonicalId(media.canonicalId)
                        ?: return@withHighPriority media

                    if (!currentMedia.plot.isNullOrBlank()) {
                        return@withHighPriority currentMedia
                    }

                    enrichIfNeededInternal(currentMedia)
                }
            }

            val durationMs = System.currentTimeMillis() - startMs
            UnifiedLog.i(TAG) {
                "enrichImmediate: completed in ${durationMs}ms canonicalId=${media.canonicalId.key.value} " +
                    "hasPlot=${!result.plot.isNullOrBlank()}"
            }

            return result
        }

        override suspend fun enrichIfNeeded(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            // Normal background priority (no special handling)
            return enrichIfNeededInternal(media)
        }

        /**
         * Internal enrichment logic (no priority handling).
         * Called by all public methods after priority is established.
         */
        private suspend fun enrichIfNeededInternal(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            val startMs = System.currentTimeMillis()

            if (!media.plot.isNullOrBlank()) {
                UnifiedLog.d(TAG) {
                    "enrichIfNeededInternal: skipped (already has plot) canonicalId=${media.canonicalId.key.value}"
                }
                return media
            }

            val hasXtreamSource = media.sources.any { it.sourceType == SourceType.XTREAM }

            val result = when {
                hasXtreamSource -> enrichFromXtream(media)
                // TODO: TMDB enrichment when TmdbMetadataResolver supports resolveByTmdbId
                else -> {
                    UnifiedLog.d(TAG) {
                        "enrichIfNeededInternal: skipped (no enrichment path) canonicalId=${media.canonicalId.key.value} hasXtream=$hasXtreamSource"
                    }
                    media
                }
            }

            val durationMs = System.currentTimeMillis() - startMs
            if (result !== media) {
                UnifiedLog.i(TAG) {
                    "enrichIfNeededInternal: completed in ${durationMs}ms canonicalId=${media.canonicalId.key.value} " +
                        "hasPlot=${!result.plot.isNullOrBlank()} source=xtream"
                }
            }

            return result
        }

        // =========================================================================
        // PLATIN Phase 2: Xtream Enrichment via UnifiedDetailLoader
        // =========================================================================

        /**
         * 🏆 PLATIN: Delegate Xtream enrichment to [UnifiedDetailLoader].
         *
         * Instead of calling getSeriesInfo/getVodInfo directly (which caused partial saves),
         * we now delegate to UnifiedDetailLoader which:
         * - Makes ONE API call
         * - Saves EVERYTHING atomically (metadata + seasons + episodes for Series)
         * - Has deduplication built-in
         */
        private suspend fun enrichFromXtream(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            val xtreamSource =
                media.sources.firstOrNull { it.sourceType == SourceType.XTREAM } ?: return media

            val sourceKey = xtreamSource.sourceId.value

            UnifiedLog.d(TAG) { "enrichFromXtream: delegating to UnifiedDetailLoader for sourceKey=$sourceKey (PLATIN)" }

            // Delegate to UnifiedDetailLoader - ONE API call, saves everything
            val bundle = unifiedDetailLoader.loadDetailImmediate(media)

            // If bundle is null, return original media
            if (bundle == null) {
                UnifiedLog.w(TAG) { "enrichFromXtream: UnifiedDetailLoader returned null for canonicalId=${media.canonicalId.key.value}" }
                return media
            }

            // Fetch the updated media from repository (UnifiedDetailLoader already persisted it)
            val updatedMedia = canonicalMediaRepository.findByCanonicalId(media.canonicalId)
            if (updatedMedia != null) {
                UnifiedLog.d(TAG) {
                    "enrichFromXtream: success via UnifiedDetailLoader, hasPlot=${!updatedMedia.plot.isNullOrBlank()}"
                }
                return updatedMedia
            }

            UnifiedLog.w(TAG) { "enrichFromXtream: could not reload media after enrichment" }
            return media
        }

        // =========================================================================
        // DEPRECATED: Legacy enrichment methods (kept for reference, not called)
        // =========================================================================

        /**
         * ⚠️ DEPRECATED: Enrich VOD item from Xtream API.
         *
         * **Problem:** This is a separate code path from UnifiedDetailLoader.
         * The PLATIN implementation consolidates all detail fetching into one place.
         *
         * **PLATIN Alternative:** Use [UnifiedDetailLoaderImpl.loadVodDetailInternal]
         * via [UnifiedDetailLoader.loadDetailImmediate].
         *
         * @see com.fishit.player.infra.data.detail.UnifiedDetailLoaderImpl
         */
        @Deprecated(
            message = "Use UnifiedDetailLoader.loadDetailImmediate() for consolidated VOD detail fetching",
            level = DeprecationLevel.WARNING,
        )
        private suspend fun enrichVodFromXtream(
            media: CanonicalMediaWithSources,
            vodId: Int,
            sourceKey: String,
        ): CanonicalMediaWithSources {
            return try {
                UnifiedLog.d(TAG) {
                    "enrichVodFromXtream: fetching vod info vodId=$vodId canonicalId=${media.canonicalId.key.value} (DEPRECATED: use UnifiedDetailLoader)"
                }

                val vodInfo = xtreamApiClient.getVodInfo(vodId)
                if (vodInfo == null) {
                    UnifiedLog.w(TAG) { "enrichVodFromXtream: API returned null vodId=$vodId" }
                    return media
                }

                val containerExtFromApi =
                    vodInfo.movieData?.containerExtension?.takeIf { it.isNotBlank() }
                val containerExtFromSourceId = parseXtreamContainerExt(sourceKey)
                val containerExt = containerExtFromApi ?: containerExtFromSourceId

                UnifiedLog.d(TAG) {
                    "enrichVodFromXtream: vodId=$vodId containerExt=$containerExt (api=$containerExtFromApi, sourceId=$containerExtFromSourceId)"
                }

                val vodItem = XtreamVodItem(
                    id = vodId,
                    name = media.canonicalTitle,
                    containerExtension = containerExt,
                )
                val rawMetadata = vodInfo.toRawMediaMetadata(vodItem)

                // Build TmdbRef from rawMetadata or existing media
                val tmdbRef = rawMetadata.externalIds.tmdb
                    ?: media.tmdbId?.let { TmdbRef(TmdbMediaType.MOVIE, it.value) }

                // Build normalized metadata with correct properties
                val normalized = NormalizedMediaMetadata(
                    canonicalTitle = media.canonicalTitle,
                    mediaType = media.mediaType,
                    year = rawMetadata.year ?: media.year,
                    season = media.season,
                    episode = media.episode,
                    tmdb = tmdbRef,
                    externalIds = ExternalIds(
                        tmdb = tmdbRef,
                        imdbId = rawMetadata.externalIds.imdbId ?: media.imdbId,
                    ),
                    poster = rawMetadata.poster ?: media.poster,
                    backdrop = rawMetadata.backdrop ?: media.backdrop,
                    thumbnail = media.thumbnail,
                    plot = rawMetadata.plot ?: media.plot,
                    genres = rawMetadata.genres ?: media.genres,
                    director = rawMetadata.director ?: media.director,
                    cast = rawMetadata.cast ?: media.cast,
                    rating = rawMetadata.rating ?: media.rating,
                    durationMs = rawMetadata.durationMs ?: media.durationMs,
                    trailer = rawMetadata.trailer ?: media.trailer,
                )

                canonicalMediaRepository.upsertCanonicalMedia(normalized)

                val updated = canonicalMediaRepository.findByCanonicalId(media.canonicalId)
                updated ?: media
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "enrichVodFromXtream: failed vodId=$vodId", e)
                media
            }
        }

        /**
         * ⚠️ DEPRECATED: Enrich Series item from Xtream API.
         *
         * **Problem:** This method calls `getSeriesInfo()` but only saves METADATA.
         * It IGNORES the seasons and episodes that come in the same API response!
         * This leads to duplicate API calls when seasons/episodes are loaded separately.
         *
         * **v2 Problem (3x API calls):**
         * 1. `enrichSeriesFromXtream()` → getSeriesInfo → saves metadata only
         * 2. `XtreamSeriesIndexRefresher.refreshSeasons()` → getSeriesInfo → saves seasons only
         * 3. `XtreamSeriesIndexRefresher.refreshEpisodes()` → getSeriesInfo → saves episodes only
         *
         * **PLATIN Alternative:** Use [UnifiedDetailLoaderImpl.loadSeriesDetailInternal]
         * via [UnifiedDetailLoader.loadDetailImmediate] - makes ONE API call and saves EVERYTHING.
         *
         * @see com.fishit.player.infra.data.detail.UnifiedDetailLoaderImpl
         */
        @Deprecated(
            message = "Use UnifiedDetailLoader.loadDetailImmediate() - this only saves metadata, ignoring seasons/episodes from same response",
            level = DeprecationLevel.WARNING,
        )
        private suspend fun enrichSeriesFromXtream(
            media: CanonicalMediaWithSources,
            seriesId: Int,
        ): CanonicalMediaWithSources {
            return try {
                UnifiedLog.d(TAG) {
                    "enrichSeriesFromXtream: fetching series info seriesId=$seriesId canonicalId=${media.canonicalId.key.value} (DEPRECATED: use UnifiedDetailLoader)"
                }

                val seriesInfo = xtreamApiClient.getSeriesInfo(seriesId)
                if (seriesInfo == null) {
                    UnifiedLog.w(TAG) { "enrichSeriesFromXtream: API returned null seriesId=$seriesId" }
                    return media
                }

                val info = seriesInfo.info
                if (info == null) {
                    UnifiedLog.d(TAG) { "enrichSeriesFromXtream: no info block seriesId=$seriesId" }
                    return media
                }

                // Build TmdbRef from series info or existing media
                val tmdbIdFromApi = info.tmdbId?.toIntOrNull()
                val tmdbRef = when {
                    tmdbIdFromApi != null -> TmdbRef(TmdbMediaType.TV, tmdbIdFromApi)
                    media.tmdbId != null -> TmdbRef(TmdbMediaType.TV, media.tmdbId!!.value)
                    else -> null
                }

                // Build normalized metadata from series info
                // Use resolvedPoster for consistency with XtreamSeriesInfoBlock helper
                val posterUrl = info.resolvedPoster
                val backdropUrl = info.backdropPath?.firstOrNull()?.takeIf { it.isNotBlank() }

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
                    poster = posterUrl?.let { ImageRef.Http(it) } ?: media.poster,
                    backdrop = backdropUrl?.let { ImageRef.Http(it) } ?: media.backdrop,
                    thumbnail = media.thumbnail,
                    plot = info.resolvedPlot ?: media.plot,
                    genres = info.resolvedGenre ?: media.genres,
                    director = info.director?.takeIf { it.isNotBlank() } ?: media.director,
                    cast = info.resolvedCast ?: media.cast,
                    rating = info.rating?.toDoubleOrNull()
                        ?: info.rating5Based?.let { it * 2.0 }
                        ?: media.rating,
                    durationMs = media.durationMs, // Series duration not in series info
                    trailer = info.resolvedTrailer ?: media.trailer,
                )

                canonicalMediaRepository.upsertCanonicalMedia(normalized)

                UnifiedLog.i(TAG) {
                    "enrichSeriesFromXtream: enriched seriesId=$seriesId hasPlot=${!normalized.plot.isNullOrBlank()}"
                }

                val updated = canonicalMediaRepository.findByCanonicalId(media.canonicalId)
                updated ?: media
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "enrichSeriesFromXtream: failed seriesId=$seriesId", e)
                media
            }
        }

        // =========================================================================
        // SourceKey Parsing Utilities
        // =========================================================================

        /**
         * Check if sourceKey represents a VOD item.
         *
         * Formats:
         * - `src:xtream:{accountKey}:vod:{id}`
         * - `xtream:vod:{id}` or `xtream:vod:{id}:{ext}`
         */
        private fun isVodSourceKey(sourceId: String): Boolean {
            val parts = sourceId.split(":")
            // New format: src:xtream:{accountKey}:vod:{id}
            if (parts.size >= 5 && parts[0] == "src" && parts[1] == "xtream" && parts[3] == "vod") {
                return true
            }
            // Legacy format: xtream:vod:{id}
            if (parts.size >= 3 && parts[0] == "xtream" && parts[1] == "vod") {
                return true
            }
            return false
        }

        /**
         * Check if sourceKey represents a Series item.
         *
         * Formats:
         * - `src:xtream:{accountKey}:series:{id}`
         * - `xtream:series:{id}`
         */
        private fun isSeriesSourceKey(sourceId: String): Boolean {
            val parts = sourceId.split(":")
            // New format: src:xtream:{accountKey}:series:{id}
            if (parts.size >= 5 && parts[0] == "src" && parts[1] == "xtream" && parts[3] == "series") {
                return true
            }
            // Legacy format: xtream:series:{id}
            if (parts.size >= 3 && parts[0] == "xtream" && parts[1] == "series") {
                return true
            }
            return false
        }

        /**
         * Parse VOD ID from sourceKey.
         *
         * Supported formats:
         * - New format: `src:xtream:{accountKey}:vod:{id}` (from NxCatalogWriter)
         * - Legacy format: `xtream:vod:{id}` or `xtream:vod:{id}:{ext}`
         */
        private fun parseXtreamVodId(sourceId: String): Int? {
            val parts = sourceId.split(":")

            // New format: src:xtream:{accountKey}:vod:{id}
            if (parts.size >= 5 && parts[0] == "src" && parts[1] == "xtream" && parts[3] == "vod") {
                return parts[4].toIntOrNull()
            }

            // Legacy format: xtream:vod:{id} or xtream:vod:{id}:{ext}
            if (parts.size >= 3 && parts[0] == "xtream" && parts[1] == "vod") {
                return parts[2].toIntOrNull()
            }

            return null
        }

        /**
         * Parse Series ID from sourceKey.
         *
         * Supported formats:
         * - New format: `src:xtream:{accountKey}:series:{id}`
         * - Legacy format: `xtream:series:{id}`
         */
        private fun parseXtreamSeriesId(sourceId: String): Int? {
            val parts = sourceId.split(":")

            // New format: src:xtream:{accountKey}:series:{id}
            if (parts.size >= 5 && parts[0] == "src" && parts[1] == "xtream" && parts[3] == "series") {
                return parts[4].toIntOrNull()
            }

            // Legacy format: xtream:series:{id}
            if (parts.size >= 3 && parts[0] == "xtream" && parts[1] == "series") {
                return parts[2].toIntOrNull()
            }

            return null
        }

        /**
         * Parse container extension from sourceKey (if present).
         *
         * Supported formats:
         * - New format: `src:xtream:{accountKey}:vod:{id}` → no ext in key
         * - Legacy format: `xtream:vod:{id}:{ext}` → returns ext
         */
        private fun parseXtreamContainerExt(sourceId: String): String? {
            val parts = sourceId.split(":")

            // New format doesn't include container ext in the key
            if (parts.size >= 5 && parts[0] == "src") {
                return null
            }

            // Legacy format: xtream:vod:{id}:{ext}
            if (parts.size >= 4 && parts[0] == "xtream" && parts[1] == "vod") {
                return parts[3].takeIf { it.isNotBlank() }
            }

            return null
        }
    }
