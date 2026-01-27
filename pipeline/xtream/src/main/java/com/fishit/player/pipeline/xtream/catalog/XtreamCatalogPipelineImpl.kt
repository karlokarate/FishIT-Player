package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamHttpHeaders
import com.fishit.player.pipeline.xtream.mapper.XtreamCatalogMapper
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
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
 * **Scan Order (Optimized for Perceived Speed):**
 * 1. LIVE channels first (smallest items, most frequently accessed)
 * 2. VOD/Movies next (quick browsing, no child items)
 * 3. Series containers last (index only, episodes loaded lazily)
 * 4. Episodes are NOT synced during initial scan - loaded on-demand
 *
 * **Streaming-First Emit (Jan 2026 - Performance Fix):**
 * Items are emitted immediately as they are parsed from JSON, WITHOUT
 * waiting for accumulation or sorting. This ensures:
 * - UI updates progressively (not blocked until all items parsed)
 * - All phases can start without waiting for previous phase to complete
 * - Memory stays low (no large in-memory lists)
 *
 * **Sorting moved to Repository:**
 * The NxWorkRepository sorts by canonicalTitle (indexed in ObjectBox).
 * This is more efficient than in-memory sort because:
 * - ObjectBox uses B-tree indexes for O(log n) ordering
 * - Sort happens at query time, not at sync time
 * - Incremental updates don't require re-sorting everything
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

                    // Thread-safe counters for parallel phases
                    val vodCounter = AtomicInteger(0)
                    val seriesCounter = AtomicInteger(0)
                    val episodeCounter = AtomicInteger(0)
                    val liveCounter = AtomicInteger(0)
                    
                    // Mutex for progress emissions to avoid interleaving
                    val progressMutex = Mutex()

                    val headers = XtreamHttpHeaders.withDefaults(config.imageAuthHeaders)

                    // ================================================================
                    // PARALLEL PHASE EXECUTION
                    // Live, VOD, and Series scan in parallel for maximum speed.
                    // Each phase streams items immediately as parsed.
                    // UI sees content from ALL types within seconds, not minutes.
                    // ================================================================
                    
                    val liveJob = if (config.includeLive) {
                        launch {
                            UnifiedLog.d(TAG, "[LIVE] Starting parallel scan (streaming-first)...")
                            try {
                                source.streamLiveChannels(batchSize = config.batchSize) { batch ->
                                    for (channel in batch) {
                                        if (!currentCoroutineContext().isActive) return@streamLiveChannels

                                        val catalogItem = mapper.fromChannel(channel, headers)
                                        send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                        val count = liveCounter.incrementAndGet()

                                        if (count % PROGRESS_LOG_INTERVAL == 0) {
                                            progressMutex.withLock {
                                                send(
                                                    XtreamCatalogEvent.ScanProgress(
                                                        vodCount = vodCounter.get(),
                                                        seriesCount = seriesCounter.get(),
                                                        episodeCount = episodeCounter.get(),
                                                        liveCount = count,
                                                        currentPhase = XtreamScanPhase.LIVE,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                UnifiedLog.d(TAG, "[LIVE] Scan complete: ${liveCounter.get()} channels")
                            } catch (e: XtreamCatalogSourceException) {
                                UnifiedLog.w(TAG, "[LIVE] Scan failed: ${e.message}")
                            }
                        }
                    } else null

                    val vodJob = if (config.includeVod) {
                        launch {
                            UnifiedLog.d(TAG, "[VOD] Starting parallel scan (streaming-first)...")
                            try {
                                source.streamVodItems(batchSize = config.batchSize) { batch ->
                                    for (item in batch) {
                                        if (!currentCoroutineContext().isActive) return@streamVodItems

                                        val catalogItem = mapper.fromVod(item, headers)
                                        send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                        val count = vodCounter.incrementAndGet()

                                        if (count % PROGRESS_LOG_INTERVAL == 0) {
                                            progressMutex.withLock {
                                                send(
                                                    XtreamCatalogEvent.ScanProgress(
                                                        vodCount = count,
                                                        seriesCount = seriesCounter.get(),
                                                        episodeCount = episodeCounter.get(),
                                                        liveCount = liveCounter.get(),
                                                        currentPhase = XtreamScanPhase.VOD,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                UnifiedLog.d(TAG, "[VOD] Scan complete: ${vodCounter.get()} items")
                            } catch (e: XtreamCatalogSourceException) {
                                UnifiedLog.w(TAG, "[VOD] Scan failed: ${e.message}")
                            }
                        }
                    } else null

                    val seriesJob = if (config.includeSeries) {
                        launch {
                            UnifiedLog.d(TAG, "[SERIES] Starting parallel scan (streaming-first, episodes deferred)...")
                            try {
                                source.streamSeriesItems(batchSize = config.batchSize) { batch ->
                                    for (item in batch) {
                                        if (!currentCoroutineContext().isActive) return@streamSeriesItems

                                        val catalogItem = mapper.fromSeries(item, headers)
                                        send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                        val count = seriesCounter.incrementAndGet()

                                        if (count % PROGRESS_LOG_INTERVAL == 0) {
                                            progressMutex.withLock {
                                                send(
                                                    XtreamCatalogEvent.ScanProgress(
                                                        vodCount = vodCounter.get(),
                                                        seriesCount = count,
                                                        episodeCount = episodeCounter.get(),
                                                        liveCount = liveCounter.get(),
                                                        currentPhase = XtreamScanPhase.SERIES,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                UnifiedLog.d(TAG, "[SERIES] Scan complete: ${seriesCounter.get()} items")
                            } catch (e: XtreamCatalogSourceException) {
                                UnifiedLog.w(TAG, "[SERIES] Scan failed: ${e.message}")
                            }
                        }
                    } else null

                    // Wait for all parallel phases to complete
                    liveJob?.join()
                    vodJob?.join()
                    seriesJob?.join()
                    
                    // Copy final counts from atomic counters
                    val liveCount = liveCounter.get()
                    val vodCount = vodCounter.get()
                    val seriesCount = seriesCounter.get()
                    var episodeCount = episodeCounter.get()

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
