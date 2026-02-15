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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Phase handler for VOD (Video on Demand) item scanning.
 *
 * **Responsibilities:**
 * - Stream VOD items from source
 * - Map items to catalog items
 * - Emit ItemDiscovered events
 * - Track progress and emit ScanProgress events
 * - Handle errors gracefully
 *
 * **CC: ≤ 6** (down from ~38 in monolithic function)
 */
internal class VodItemPhase
    @Inject
    constructor(
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
            if (!config.includeVod) return

            // Small delay to let LIVE start first (better UX)
            delay(STARTUP_DELAY_MS)

            val phaseStart = System.currentTimeMillis()
            val categoryFilter = config.vodCategoryIds
            val hasFilter = categoryFilter.isNotEmpty()
            UnifiedLog.d(
                TAG,
                "[VOD] Starting parallel scan (streaming)${if (hasFilter) " with ${categoryFilter.size} category filter(s) [server-side]" else ""}...",
            )

            try {
                if (hasFilter) {
                    // Server-side filtering: parallel fetch per category via API category_id parameter
                    // Semaphore limits concurrent HTTP calls per phase to avoid server overload
                    coroutineScope {
                        val categorySemaphore = Semaphore(CATEGORY_PARALLELISM)
                        categoryFilter
                            .map { catId ->
                                async {
                                    categorySemaphore.withPermit {
                                        source.streamVodItems(batchSize = config.batchSize, categoryId = catId) { batch ->
                                            for (vodItem in batch) {
                                                if (!currentCoroutineContext().isActive) return@streamVodItems
                                                val catalogItem = mapper.fromVod(vodItem, headers, config.accountLabel)
                                                channel.send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                                                val count = counters.vodCounter.incrementAndGet()
                                                if (count % PROGRESS_LOG_INTERVAL == 0) {
                                                    progressMutex.withLock {
                                                        channel.send(
                                                            XtreamCatalogEvent.ScanProgress(
                                                                vodCount = count,
                                                                seriesCount = counters.seriesCounter.get(),
                                                                episodeCount = counters.episodeCounter.get(),
                                                                liveCount = counters.liveCounter.get(),
                                                                currentPhase = XtreamScanPhase.VOD,
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
                    source.streamVodItems(batchSize = config.batchSize) { batch ->
                        for (vodItem in batch) {
                            if (!currentCoroutineContext().isActive) return@streamVodItems
                            val catalogItem = mapper.fromVod(vodItem, headers, config.accountLabel)
                            channel.send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                            val count = counters.vodCounter.incrementAndGet()
                            if (count % PROGRESS_LOG_INTERVAL == 0) {
                                progressMutex.withLock {
                                    channel.send(
                                        XtreamCatalogEvent.ScanProgress(
                                            vodCount = count,
                                            seriesCount = counters.seriesCounter.get(),
                                            episodeCount = counters.episodeCounter.get(),
                                            liveCount = counters.liveCounter.get(),
                                            currentPhase = XtreamScanPhase.VOD,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                val phaseDuration = System.currentTimeMillis() - phaseStart
                UnifiedLog.d(TAG, "[VOD] Scan complete: ${counters.vodCounter.get()} items")
                XtcLogger.logPhaseComplete("VOD", counters.vodCounter.get(), phaseDuration)
            } catch (e: XtreamCatalogSourceException) {
                UnifiedLog.w(TAG, "[VOD] Scan failed: ${e.message}")
            }
        }

        companion object {
            private const val TAG = "VodItemPhase"
            private const val PROGRESS_LOG_INTERVAL = 500
            private const val STARTUP_DELAY_MS = 500L

            /** Max concurrent category HTTP calls within this phase */
            private const val CATEGORY_PARALLELISM = 4
        }
    }
