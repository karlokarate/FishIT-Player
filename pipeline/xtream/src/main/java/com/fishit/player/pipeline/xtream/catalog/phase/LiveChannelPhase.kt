package com.fishit.player.pipeline.xtream.catalog.phase

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogConfig
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogSource
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogSourceException
import com.fishit.player.pipeline.xtream.catalog.XtreamScanPhase
import com.fishit.player.pipeline.xtream.debug.XtcLogger
import com.fishit.player.pipeline.xtream.mapper.XtreamCatalogMapper
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Phase handler for Live channel scanning.
 *
 * **Responsibilities:**
 * - Stream live channels from source
 * - Map channels to catalog items
 * - Emit ItemDiscovered events
 * - Track progress and emit ScanProgress events
 * - Handle errors gracefully
 *
 * **CC: ≤ 6** (down from ~38 in monolithic function)
 */
internal class LiveChannelPhase @Inject constructor(
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
        if (!config.includeLive) return

        val phaseStart = System.currentTimeMillis()
        UnifiedLog.d(TAG, "[LIVE] Starting parallel scan (streaming)...")
        
        try {
            source.streamLiveChannels(batchSize = config.batchSize) { batch ->
                for (liveChannel in batch) {
                    // Check cancellation
                    if (!currentCoroutineContext().isActive) return@streamLiveChannels

                    // Map and emit
                    val catalogItem = mapper.fromChannel(liveChannel, headers, config.accountName)
                    channel.send(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                    
                    // Update counter and emit progress
                    val count = counters.liveCounter.incrementAndGet()
                    if (count % PROGRESS_LOG_INTERVAL == 0) {
                        progressMutex.withLock {
                            channel.send(
                                XtreamCatalogEvent.ScanProgress(
                                    vodCount = counters.vodCounter.get(),
                                    seriesCount = counters.seriesCounter.get(),
                                    episodeCount = counters.episodeCounter.get(),
                                    liveCount = count,
                                    currentPhase = XtreamScanPhase.LIVE,
                                ),
                            )
                        }
                    }
                }
            }
            
            val phaseDuration = System.currentTimeMillis() - phaseStart
            UnifiedLog.d(TAG, "[LIVE] Scan complete: ${counters.liveCounter.get()} channels")
            XtcLogger.logPhaseComplete("LIVE", counters.liveCounter.get(), phaseDuration)
        } catch (e: XtreamCatalogSourceException) {
            UnifiedLog.w(TAG, "[LIVE] Scan failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LiveChannelPhase"
        private const val PROGRESS_LOG_INTERVAL = 500
    }
}
