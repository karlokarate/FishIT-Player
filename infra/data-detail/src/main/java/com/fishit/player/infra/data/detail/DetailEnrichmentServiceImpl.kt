package com.fishit.player.infra.data.detail

import com.fishit.player.core.detail.domain.DetailEnrichmentService
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [DetailEnrichmentService].
 *
 * Enriches canonical media with additional metadata from:
 * - Xtream API (getVodInfo) for XTREAM sources
 * - TMDB resolver (future) for media with TMDB IDs
 *
 * **Thread Safety:** Uses per-canonicalId mutexes to prevent concurrent enrichment.
 */
@Singleton
class DetailEnrichmentServiceImpl
    @Inject
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val canonicalMediaRepository: CanonicalMediaRepository,
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

            val mutex = getMutexForCanonicalId(canonicalId.key.value)
            val enriched =
                mutex.withLock {
                    val currentMedia = canonicalMediaRepository.findByCanonicalId(canonicalId)
                    if (currentMedia == null) return@withLock null

                    val currentSource =
                        sourceKey?.let { key ->
                            currentMedia.sources.find { it.sourceId == key }
                        }
                    if (currentSource != null && requiredHints.isNotEmpty()) {
                        val stillMissing =
                            requiredHints.filter {
                                currentSource.playbackHints[it].isNullOrBlank()
                            }
                        if (stillMissing.isEmpty()) {
                            UnifiedLog.d(TAG) {
                                "ensureEnriched: post-lock fast path canonicalId=${canonicalId.key.value}"
                            }
                            return@withLock currentMedia
                        }
                    }

                    withTimeoutOrNull(timeoutMs) { enrichIfNeeded(currentMedia) }
                }

            val durationMs = System.currentTimeMillis() - startMs
            UnifiedLog.d(TAG) {
                "ensureEnriched: completed in ${durationMs}ms canonicalId=${canonicalId.key.value} success=${enriched != null}"
            }

            return canonicalMediaRepository.findByCanonicalId(canonicalId)
        }

        override suspend fun enrichIfNeeded(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            val startMs = System.currentTimeMillis()

            if (!media.plot.isNullOrBlank()) {
                UnifiedLog.d(TAG) {
                    "enrichIfNeeded: skipped (already has plot) canonicalId=${media.canonicalId.key.value}"
                }
                return media
            }

            val hasXtreamSource = media.sources.any { it.sourceType == SourceType.XTREAM }

            val result =
                when {
                    hasXtreamSource -> enrichFromXtream(media)
                    // TODO: TMDB enrichment when TmdbMetadataResolver supports resolveByTmdbId
                    else -> {
                        UnifiedLog.d(TAG) {
                            "enrichIfNeeded: skipped (no enrichment path) canonicalId=${media.canonicalId.key.value} hasXtream=$hasXtreamSource"
                        }
                        media
                    }
                }

            val durationMs = System.currentTimeMillis() - startMs
            if (result !== media) {
                UnifiedLog.i(TAG) {
                    "enrichIfNeeded: completed in ${durationMs}ms canonicalId=${media.canonicalId.key.value} " +
                        "hasPlot=${!result.plot.isNullOrBlank()} source=xtream"
                }
            }

            return result
        }

        private suspend fun enrichFromXtream(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
            val xtreamSource =
                media.sources.firstOrNull { it.sourceType == SourceType.XTREAM } ?: return media

            val vodId = parseXtreamVodId(xtreamSource.sourceId.value)
            if (vodId == null) {
                UnifiedLog.w(TAG) {
                    "enrichFromXtream: invalid sourceId=${xtreamSource.sourceId.value}"
                }
                return media
            }

            return try {
                UnifiedLog.d(TAG) {
                    "enrichFromXtream: fetching vod info vodId=$vodId canonicalId=${media.canonicalId.key.value}"
                }

                val vodInfo = xtreamApiClient.getVodInfo(vodId)
                if (vodInfo == null) {
                    UnifiedLog.w(TAG) { "enrichFromXtream: API returned null vodId=$vodId" }
                    return media
                }

                val containerExtFromApi =
                    vodInfo.movieData?.containerExtension?.takeIf { it.isNotBlank() }
                val containerExtFromSourceId = parseXtreamContainerExt(xtreamSource.sourceId.value)
                val containerExt = containerExtFromApi ?: containerExtFromSourceId

                UnifiedLog.d(TAG) {
                    "enrichFromXtream: vodId=$vodId containerExt=$containerExt (api=$containerExtFromApi, sourceId=$containerExtFromSourceId)"
                }

                val vodItem =
                    XtreamVodItem(
                        id = vodId,
                        name = media.canonicalTitle,
                        containerExtension = containerExt,
                    )
                val rawMetadata = vodInfo.toRawMediaMetadata(vodItem)

                // Build TmdbRef from rawMetadata or existing media
                val tmdbRef =
                    rawMetadata.externalIds.tmdb
                        ?: media.tmdbId?.let { TmdbRef(TmdbMediaType.MOVIE, it.value) }

                // Build normalized metadata with correct properties
                val normalized =
                    NormalizedMediaMetadata(
                        canonicalTitle = media.canonicalTitle,
                        mediaType = media.mediaType,
                        year = rawMetadata.year ?: media.year,
                        season = media.season,
                        episode = media.episode,
                        tmdb = tmdbRef,
                        externalIds =
                            ExternalIds(
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
                UnifiedLog.e(TAG, "enrichFromXtream: failed vodId=$vodId", e)
                media
            }
        }

        private fun parseXtreamVodId(sourceId: String): Int? {
            // Expected: xtream:vod:{id} or xtream:vod:{id}:{ext}
            val parts = sourceId.split(":")
            if (parts.size < 3) return null
            if (parts[0] != "xtream" || parts[1] != "vod") return null
            return parts[2].toIntOrNull()
        }

        private fun parseXtreamContainerExt(sourceId: String): String? {
            val parts = sourceId.split(":")
            if (parts.size < 4) return null
            return parts[3].takeIf { it.isNotBlank() }
        }
    }
