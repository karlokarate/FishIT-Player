package com.fishit.player.pipeline.xtream.catalog.phase

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogConfig
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogSource
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogSourceException
import com.fishit.player.pipeline.xtream.catalog.XtreamScanPhase
import com.fishit.player.pipeline.xtream.debug.XtcLogger
import com.fishit.player.pipeline.xtream.mapper.XtreamCatalogMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Phase handler for Series container scanning.
 *
 * **Responsibilities:**
 * - Stream series containers from source
 * - Map containers to catalog items
 * - Emit ItemDiscovered events
 * - Track progress and emit ScanProgress events
 * - Handle errors gracefully
 *
 * **Note:** Episodes are NOT loaded here - they're loaded separately
 * by EpisodeStreamingPhase for better performance and memory efficiency.
 *
 * **CC: ≤ 6** (down from ~38 in monolithic function)
 */
internal class SeriesItemPhase @Inject constructor(
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
        if (!config.includeSeries) return

        val phaseStart = System.currentTimeMillis()
        val categoryFilter = config.seriesCategoryIds
        val hasFilter = categoryFilter.isNotEmpty()
        UnifiedLog.d(TAG, "[SERIES] Starting scan (after slot available)${if (hasFilter) " with ${categoryFilter.size} category filter(s) [server-side]" else ""}...")
        
        try {
            if (hasFilter) {
                // Server-side filtering: parallel fetch per category via API category_id parameter
                // Semaphore limits concurrent HTTP calls per phase to avoid server overload
                coroutineScope {
                    val categorySemaphore = Semaphore(CATEGORY_PARALLELISM)
                    categoryFilter.map { catId ->
                        async {
                            categorySemaphore.withPermit {
                                source.streamSeriesItems(batchSize = config.batchSize, categoryId = catId) { batch ->
                                    for (seriesItem in batch) {
                                        if (!currentCoroutineContext().isActive) return@streamSeriesItems
                                        val catalogItem = mapper.fromSeries(seriesItem, headers, config.accountName)
                                        channel.send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                        val count = counters.seriesCounter.incrementAndGet()
                                        if (count % PROGRESS_LOG_INTERVAL == 0) {
                                            progressMutex.withLock {
                                                channel.send(
                                                    XtreamCatalogEvent.ScanProgress(
                                                        vodCount = counters.vodCounter.get(),
                                                        seriesCount = count,
                                                        episodeCount = counters.episodeCounter.get(),
                                                        liveCount = counters.liveCounter.get(),
                                                        currentPhase = XtreamScanPhase.SERIES,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
            } else {
                // No filter: fetch all items at once
                source.streamSeriesItems(batchSize = config.batchSize) { batch ->
                    for (seriesItem in batch) {
                        if (!currentCoroutineContext().isActive) return@streamSeriesItems
                        val catalogItem = mapper.fromSeries(seriesItem, headers, config.accountName)
                        channel.send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                        val count = counters.seriesCounter.incrementAndGet()
                        if (count % PROGRESS_LOG_INTERVAL == 0) {
                            progressMutex.withLock {
                                channel.send(
                                    XtreamCatalogEvent.ScanProgress(
                                        vodCount = counters.vodCounter.get(),
                                        seriesCount = count,
                                        episodeCount = counters.episodeCounter.get(),
                                        liveCount = counters.liveCounter.get(),
                                        currentPhase = XtreamScanPhase.SERIES,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            
            val phaseDuration = System.currentTimeMillis() - phaseStart
            UnifiedLog.d(TAG, "[SERIES] Scan complete: ${counters.seriesCounter.get()} items")
            XtcLogger.logPhaseComplete("SERIES", counters.seriesCounter.get(), phaseDuration)
        } catch (e: XtreamCatalogSourceException) {
            UnifiedLog.w(TAG, "[SERIES] Scan failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SeriesItemPhase"
        private const val PROGRESS_LOG_INTERVAL = 500
        /** Max concurrent category HTTP calls within this phase */
        private const val CATEGORY_PARALLELISM = 4
    }
}
