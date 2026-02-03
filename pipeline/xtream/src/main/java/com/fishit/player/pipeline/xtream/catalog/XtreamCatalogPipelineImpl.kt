package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.catalog.phase.PhaseScanOrchestrator
import com.fishit.player.pipeline.xtream.debug.XtcLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * Default implementation of [XtreamCatalogPipeline].
 *
 * Stateless producer that:
 * - Delegates phase execution to [PhaseScanOrchestrator]
 * - Emits [XtreamCatalogEvent] through a cold Flow
 * - Handles completion and error states
 *
 * **Architecture:**
 * - No DB writes, no UI interaction, no playback URL generation
 * - Respects cancellation via coroutine context
 * - Logs progress via UnifiedLog
 *
 * **Refactored (Jan 2026):**
 * - Extracted phase handlers (Live, VOD, Series, Episodes)
 * - Reduced CC from ~38 to ~10
 * - Improved testability and maintainability
 * - ~395 lines → ~120 lines
 *
 * **Scan Order (Optimized for Perceived Speed):**
 * 1. LIVE channels first (smallest items, most frequently accessed)
 * 2. VOD/Movies next (quick browsing, no child items)
 * 3. Series containers last (index only, episodes loaded lazily)
 * 4. Episodes are streamed in parallel after phases 1-3
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
 * @param orchestrator Coordinator for parallel phase execution
 */
class XtreamCatalogPipelineImpl
    @Inject
    constructor(
        private val orchestrator: PhaseScanOrchestrator,
    ) : XtreamCatalogPipeline {
        override fun scanCatalog(config: XtreamCatalogConfig): Flow<XtreamCatalogEvent> =
            channelFlow {
                val startTime = System.currentTimeMillis()

                // XTC: Reset counters for new sync run
                XtcLogger.reset()

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

                    // Execute all phases via orchestrator
                    val result = orchestrator.executeScan(config, this)

                    // ================================================================
                    // Completion / Cancellation Check
                    // ================================================================

                    // Check if cancelled
                    if (!currentCoroutineContext().isActive) {
                        UnifiedLog.i(TAG, "Scan cancelled")
                        send(
                            XtreamCatalogEvent.ScanCancelled(
                                vodCount = result.vodCount,
                                seriesCount = result.seriesCount,
                                episodeCount = result.episodeCount,
                                liveCount = result.liveCount,
                            ),
                        )
                        return@channelFlow
                    }

                    val durationMs = System.currentTimeMillis() - startTime

                    UnifiedLog.i(
                        TAG,
                        "Xtream catalog scan completed: ${result.totalItems} items " +
                            "(live=${result.liveCount}, vod=${result.vodCount}, " +
                            "series=${result.seriesCount}, episodes=${result.episodeCount}) " +
                            "in ${durationMs}ms",
                    )

                    send(
                        XtreamCatalogEvent.ScanCompleted(
                            vodCount = result.vodCount,
                            seriesCount = result.seriesCount,
                            episodeCount = result.episodeCount,
                            liveCount = result.liveCount,
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
        }
    }
