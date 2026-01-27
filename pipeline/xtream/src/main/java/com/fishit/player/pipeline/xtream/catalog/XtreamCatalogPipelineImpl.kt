package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamHttpHeaders
import com.fishit.player.pipeline.xtream.mapper.XtreamCatalogMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * Default implementation of [XtreamCatalogPipeline].
 *
 * Stateless producer that:
 * - Reads from [XtreamCatalogSource]
 * - Uses [XtreamCatalogMapper] to build catalog items
 * - Emits [XtreamCatalogEvent] through a cold Flow
 *
 * **Architecture:**
 * - No DB writes, no UI interaction, no playback URL generation
 * - Respects cancellation via coroutine context
 * - Logs progress via UnifiedLog
 *
 * **Scan Order (Optimized for Perceived Speed - Dec 2025):**
 * 1. LIVE channels first (smallest items, most frequently accessed)
 * 2. VOD/Movies next (quick browsing, no child items)
 * 3. Series containers last (index only, episodes loaded lazily)
 * 4. Episodes are NOT synced during initial scan - loaded on-demand via EnsureEpisodePlaybackReadyUseCase
 *
 * **Rationale:**
 * - Live tiles appear within ~1-2 seconds
 * - Movies appear progressively (not all at end)
 * - Series shows immediately without waiting for 100k+ episodes
 * - Episode fetching happens when user opens a series (lazy loading)
 *
 * @param source Data source for Xtream catalog items
 * @param mapper Mapper for converting models to catalog items
 */
class XtreamCatalogPipelineImpl
    @Inject
    constructor(
        private val source: XtreamCatalogSource,
        private val mapper: XtreamCatalogMapper,
    ) : XtreamCatalogPipeline {
        override fun scanCatalog(config: XtreamCatalogConfig): Flow<XtreamCatalogEvent> =
            channelFlow {
                val startTime = System.currentTimeMillis()

                try {
                    UnifiedLog.i(
                        TAG,
                        "Starting Xtream catalog scan: vod=${config.includeVod}, " +
                            "series=${config.includeSeries}, episodes=${config.includeEpisodes}, " +
                            "live=${config.includeLive}",
                    )

                    send(
                        XtreamCatalogEvent.ScanStarted(
                            includesVod = config.includeVod,
                            includesSeries = config.includeSeries,
                            includesEpisodes = config.includeEpisodes,
                            includesLive = config.includeLive,
                        ),
                    )

                    var vodCount = 0
                    var seriesCount = 0
                    var episodeCount = 0
                    var liveCount = 0

                    val headers = XtreamHttpHeaders.withDefaults(config.imageAuthHeaders)

                    // ================================================================
                    // Phase 1: LIVE (First for perceived speed)
                    // Smallest items, most frequently accessed, appear within ~1-2s
                    // Uses streaming for constant memory usage.
                    // ================================================================
                    if (config.includeLive && currentCoroutineContext().isActive) {
                        UnifiedLog.d(TAG, "[Phase 1/3] Scanning live channels first (streaming)...")

                        try {
                            source.streamLiveChannels(batchSize = config.batchSize) { batch ->
                                for (channel in batch) {
                                    if (!currentCoroutineContext().isActive) return@streamLiveChannels

                                    val catalogItem = mapper.fromChannel(channel, headers)
                                    send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                    liveCount++

                                    if (liveCount % PROGRESS_LOG_INTERVAL == 0) {
                                        send(
                                            XtreamCatalogEvent.ScanProgress(
                                                vodCount = vodCount,
                                                seriesCount = seriesCount,
                                                episodeCount = episodeCount,
                                                liveCount = liveCount,
                                                currentPhase = XtreamScanPhase.LIVE,
                                            ),
                                        )
                                    }
                                }
                            }

                            UnifiedLog.d(TAG, "[Phase 1/3] Live scan complete: $liveCount channels")
                        } catch (e: XtreamCatalogSourceException) {
                            UnifiedLog.w(TAG, "[Phase 1/3] Live scan failed: ${e.message}")
                            // Continue with other phases
                        }
                    }

                    // ================================================================
                    // Phase 2: VOD/Movies
                    // Quick browsing, no child items to fetch
                    // Uses streaming for constant memory usage.
                    // ================================================================
                    if (config.includeVod && currentCoroutineContext().isActive) {
                        UnifiedLog.d(TAG, "[Phase 2/3] Scanning VOD items (streaming)...")

                        try {
                            source.streamVodItems(batchSize = config.batchSize) { batch ->
                                for (item in batch) {
                                    if (!currentCoroutineContext().isActive) return@streamVodItems

                                    val catalogItem = mapper.fromVod(item, headers)
                                    send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                    vodCount++

                                    if (vodCount % PROGRESS_LOG_INTERVAL == 0) {
                                        send(
                                            XtreamCatalogEvent.ScanProgress(
                                                vodCount = vodCount,
                                                seriesCount = seriesCount,
                                                episodeCount = episodeCount,
                                                liveCount = liveCount,
                                                currentPhase = XtreamScanPhase.VOD,
                                            ),
                                        )
                                    }
                                }
                            }

                            UnifiedLog.d(TAG, "[Phase 2/3] VOD scan complete: $vodCount items")
                        } catch (e: XtreamCatalogSourceException) {
                            UnifiedLog.w(TAG, "[Phase 2/3] VOD scan failed: ${e.message}")
                        }
                    }

                    // ================================================================
                    // Phase 3: Series (Index Only - Episodes are lazy loaded)
                    // Series containers appear quickly, episodes fetched on-demand
                    // Uses streaming for constant memory usage.
                    // ================================================================
                    if (config.includeSeries && currentCoroutineContext().isActive) {
                        UnifiedLog.d(TAG, "[Phase 3/3] Scanning series index (streaming, episodes deferred)...")

                        try {
                            source.streamSeriesItems(batchSize = config.batchSize) { batch ->
                                for (item in batch) {
                                    if (!currentCoroutineContext().isActive) return@streamSeriesItems

                                    val catalogItem = mapper.fromSeries(item, headers)
                                    send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                    seriesCount++

                                    if (seriesCount % PROGRESS_LOG_INTERVAL == 0) {
                                        send(
                                            XtreamCatalogEvent.ScanProgress(
                                                vodCount = vodCount,
                                                seriesCount = seriesCount,
                                                episodeCount = episodeCount,
                                                liveCount = liveCount,
                                                currentPhase = XtreamScanPhase.SERIES,
                                            ),
                                        )
                                    }
                                }
                            }

                            UnifiedLog.d(TAG, "[Phase 3/3] Series scan complete: $seriesCount items")
                        } catch (e: XtreamCatalogSourceException) {
                            UnifiedLog.w(TAG, "[Phase 3/3] Series scan failed: ${e.message}")
                        }
                    }

                    // ================================================================
                    // Phase 4: Episodes (PLATINUM: Parallel streaming)
                    // Episodes are now loaded in parallel with immediate emission.
                    // This replaces the previous blocking loadEpisodes() that timed out.
                    // ================================================================
                    if (config.includeEpisodes && currentCoroutineContext().isActive) {
                        UnifiedLog.d(TAG, "[Phase 4/4] Scanning episodes (parallel streaming mode)...")

                        try {
                            // Use streaming API with parallel loading
                            // excludeSeriesIds from config allows checkpoint resume
                            source
                                .loadEpisodesStreaming(
                                    parallelism = config.episodeParallelism,
                                    excludeSeriesIds = config.excludeSeriesIds,
                                ).collect { result ->
                                    if (!currentCoroutineContext().isActive) {
                                        throw CancellationException("Pipeline cancelled during episode streaming")
                                    }

                                    when (result) {
                                        is EpisodeBatchResult.Batch -> {
                                            // Emit each episode immediately
                                            for (episode in result.episodes) {
                                                val catalogItem = mapper.fromEpisode(episode, result.seriesName, headers)
                                                send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                                episodeCount++

                                                if (episodeCount % PROGRESS_LOG_INTERVAL == 0) {
                                                    send(
                                                        XtreamCatalogEvent.ScanProgress(
                                                            vodCount = vodCount,
                                                            seriesCount = seriesCount,
                                                            episodeCount = episodeCount,
                                                            liveCount = liveCount,
                                                            currentPhase = XtreamScanPhase.EPISODES,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                        is EpisodeBatchResult.SeriesComplete -> {
                                            // Emit event for checkpoint tracking (PLATINUM)
                                            send(
                                                XtreamCatalogEvent.SeriesEpisodeComplete(
                                                    seriesId = result.seriesId,
                                                    episodeCount = result.episodeCount,
                                                ),
                                            )
                                            UnifiedLog.v(TAG) {
                                                "Series ${result.seriesId} complete: ${result.episodeCount} episodes"
                                            }
                                        }
                                        is EpisodeBatchResult.SeriesFailed -> {
                                            // Emit event for tracking (series won't be in processedSeriesIds)
                                            send(
                                                XtreamCatalogEvent.SeriesEpisodeFailed(
                                                    seriesId = result.seriesId,
                                                    reason = result.error.message ?: "Unknown error",
                                                ),
                                            )
                                            UnifiedLog.w(TAG) {
                                                "Series ${result.seriesId} failed: ${result.error.message}"
                                            }
                                        }
                                    }
                                }

                            UnifiedLog.d(TAG, "[Phase 4/4] Episodes scan complete: $episodeCount episodes")
                        } catch (e: CancellationException) {
                            UnifiedLog.i(TAG, "[Phase 4/4] Episode scan cancelled at $episodeCount episodes")
                            throw e
                        } catch (e: XtreamCatalogSourceException) {
                            UnifiedLog.w(TAG, "[Phase 4/4] Episodes scan failed: ${e.message}")
                        }
                    }

                    // ================================================================
                    // Completion / Cancellation Check
                    // ================================================================

                    // Check if cancelled
                    if (!currentCoroutineContext().isActive) {
                        UnifiedLog.i(TAG, "Scan cancelled")
                        send(
                            XtreamCatalogEvent.ScanCancelled(
                                vodCount = vodCount,
                                seriesCount = seriesCount,
                                episodeCount = episodeCount,
                                liveCount = liveCount,
                            ),
                        )
                        return@channelFlow
                    }

                    val durationMs = System.currentTimeMillis() - startTime
                    val totalItems = vodCount + seriesCount + episodeCount + liveCount

                    UnifiedLog.i(
                        TAG,
                        "Xtream catalog scan completed: $totalItems items " +
                            "(live=$liveCount, vod=$vodCount, series=$seriesCount, episodes=$episodeCount) " +
                            "in ${durationMs}ms",
                    )

                    send(
                        XtreamCatalogEvent.ScanCompleted(
                            vodCount = vodCount,
                            seriesCount = seriesCount,
                            episodeCount = episodeCount,
                            liveCount = liveCount,
                            durationMs = durationMs,
                        ),
                    )
                } catch (ce: CancellationException) {
                    UnifiedLog.i(TAG, "Scan cancelled by coroutine cancellation")
                    throw ce
                } catch (t: Throwable) {
                    UnifiedLog.e(TAG, "Xtream catalog scan failed", t)
                    send(
                        XtreamCatalogEvent.ScanError(
                            reason = "unexpected_error",
                            message = t.message ?: "Unknown error",
                            throwable = t,
                        ),
                    )
                }
            }

        companion object {
            private const val TAG = "XtreamCatalogPipeline"
            private const val PROGRESS_LOG_INTERVAL = 100
        }
    }
