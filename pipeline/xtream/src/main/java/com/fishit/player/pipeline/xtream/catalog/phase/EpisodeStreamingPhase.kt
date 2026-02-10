package com.fishit.player.pipeline.xtream.catalog.phase

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.catalog.EpisodeBatchResult
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogConfig
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogSource
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogSourceException
import com.fishit.player.pipeline.xtream.catalog.XtreamScanPhase
import com.fishit.player.pipeline.xtream.mapper.XtreamCatalogMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

/**
 * Phase handler for Episode streaming.
 *
 * **Responsibilities:**
 * - Stream episodes from source in parallel
 * - Map episodes to catalog items
 * - Emit ItemDiscovered events
 * - Track progress per series and emit ScanProgress events
 * - Handle checkpoint resume (excludeSeriesIds)
 * - Emit SeriesEpisodeComplete/Failed events
 *
 * **Key Features:**
 * - Parallel loading with configurable parallelism
 * - Checkpoint resume support
 * - Per-series progress tracking
 * - Graceful error handling (series failures don't stop entire scan)
 *
 * **CC: ≤ 8** (down from ~38 in monolithic function)
 */
internal class EpisodeStreamingPhase @Inject constructor(
    private val source: XtreamCatalogSource,
    private val mapper: XtreamCatalogMapper,
) : ScanPhase {
    
    override suspend fun execute(
        config: XtreamCatalogConfig,
        channel: SendChannel<XtreamCatalogEvent>,
        counters: PhaseCounters,
        progressMutex: Mutex,
        headers: Map<String, String>,
    ) {
        if (!config.includeEpisodes) return
        if (!currentCoroutineContext().isActive) return

        UnifiedLog.d(TAG, "[Phase 4/4] Scanning episodes (parallel streaming mode)...")

        try {
            // Use streaming API with parallel loading
            // excludeSeriesIds from config allows checkpoint resume
            source
                .loadEpisodesStreaming(
                    parallelism = config.episodeParallelism,
                    excludeSeriesIds = config.excludeSeriesIds,
                ).collect { result ->
                    // Check cancellation
                    if (!currentCoroutineContext().isActive) {
                        throw CancellationException("Pipeline cancelled during episode streaming")
                    }

                    when (result) {
                        is EpisodeBatchResult.Batch -> {
                            // Emit each episode immediately
                            for (episode in result.episodes) {
                                val catalogItem = mapper.fromEpisode(
                                    episode = episode,
                                    seriesName = result.seriesName,
                                    imageAuthHeaders = headers,
                                    accountName = config.accountName,
                                )
                                channel.send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                
                                val count = counters.episodeCounter.incrementAndGet()
                                if (count % PROGRESS_LOG_INTERVAL == 0) {
                                    channel.send(
                                        XtreamCatalogEvent.ScanProgress(
                                            vodCount = counters.vodCounter.get(),
                                            seriesCount = counters.seriesCounter.get(),
                                            episodeCount = count,
                                            liveCount = counters.liveCounter.get(),
                                            currentPhase = XtreamScanPhase.EPISODES,
                                        ),
                                    )
                                }
                            }
                        }
                        is EpisodeBatchResult.SeriesComplete -> {
                            // Emit event for checkpoint tracking (PLATINUM)
                            channel.send(
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
                            channel.send(
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

            UnifiedLog.d(TAG, "[Phase 4/4] Episodes scan complete: ${counters.episodeCounter.get()} episodes")
        } catch (e: CancellationException) {
            UnifiedLog.i(TAG, "[Phase 4/4] Episode scan cancelled at ${counters.episodeCounter.get()} episodes")
            throw e
        } catch (e: XtreamCatalogSourceException) {
            UnifiedLog.w(TAG, "[Phase 4/4] Episodes scan failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "EpisodeStreamingPhase"
        private const val PROGRESS_LOG_INTERVAL = 500
    }
}
