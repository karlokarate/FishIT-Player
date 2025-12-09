package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.infra.logging.UnifiedLog
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
 * - Reads from [XtreamCatalogSource]
 * - Uses [XtreamCatalogMapper] to build catalog items
 * - Emits [XtreamCatalogEvent] through a cold Flow
 *
 * **Architecture:**
 * - No DB writes, no UI interaction, no playback URL generation
 * - Respects cancellation via coroutine context
 * - Logs progress via UnifiedLog
 *
 * **Scan Order:**
 * 1. VOD items (if enabled)
 * 2. Series containers (if enabled)
 * 3. Episodes (if enabled)
 * 4. Live channels (if enabled)
 *
 * @param source Data source for Xtream catalog items
 * @param mapper Mapper for converting models to catalog items
 */
class XtreamCatalogPipelineImpl @Inject constructor(
    private val source: XtreamCatalogSource,
    private val mapper: XtreamCatalogMapper,
) : XtreamCatalogPipeline {

    override fun scanCatalog(
        config: XtreamCatalogConfig,
    ): Flow<XtreamCatalogEvent> = channelFlow {
        val startTime = System.currentTimeMillis()

        try {
            UnifiedLog.i(
                TAG,
                "Starting Xtream catalog scan: vod=${config.includeVod}, " +
                    "series=${config.includeSeries}, episodes=${config.includeEpisodes}, " +
                    "live=${config.includeLive}",
            )

            trySend(
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

            val headers = config.imageAuthHeaders

            // Phase 1: VOD
            if (config.includeVod && currentCoroutineContext().isActive) {
                UnifiedLog.d(TAG, "Scanning VOD items...")

                try {
                    val vodItems = source.loadVodItems()

                    for (item in vodItems) {
                        if (!currentCoroutineContext().isActive) break

                        val catalogItem = mapper.fromVod(item, headers)
                        trySend(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                        vodCount++

                        if (vodCount % PROGRESS_LOG_INTERVAL == 0) {
                            trySend(
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

                    UnifiedLog.d(TAG, "VOD scan complete: $vodCount items")
                } catch (e: XtreamCatalogSourceException) {
                    UnifiedLog.w(TAG, "VOD scan failed: ${e.message}")
                    // Continue with other phases
                }
            }

            // Phase 2: Series
            if (config.includeSeries && currentCoroutineContext().isActive) {
                UnifiedLog.d(TAG, "Scanning series...")

                try {
                    val seriesItems = source.loadSeriesItems()

                    for (item in seriesItems) {
                        if (!currentCoroutineContext().isActive) break

                        val catalogItem = mapper.fromSeries(item, headers)
                        trySend(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                        seriesCount++

                        if (seriesCount % PROGRESS_LOG_INTERVAL == 0) {
                            trySend(
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

                    UnifiedLog.d(TAG, "Series scan complete: $seriesCount items")
                } catch (e: XtreamCatalogSourceException) {
                    UnifiedLog.w(TAG, "Series scan failed: ${e.message}")
                }
            }

            // Phase 3: Episodes
            if (config.includeEpisodes && currentCoroutineContext().isActive) {
                UnifiedLog.d(TAG, "Scanning episodes...")

                try {
                    val episodes = source.loadEpisodes()

                    for (episode in episodes) {
                        if (!currentCoroutineContext().isActive) break

                        // TODO: Ideally we'd have seriesName here for better context
                        val catalogItem = mapper.fromEpisode(episode, null, headers)
                        trySend(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                        episodeCount++

                        if (episodeCount % PROGRESS_LOG_INTERVAL == 0) {
                            trySend(
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

                    UnifiedLog.d(TAG, "Episodes scan complete: $episodeCount items")
                } catch (e: XtreamCatalogSourceException) {
                    UnifiedLog.w(TAG, "Episodes scan failed: ${e.message}")
                }
            }

            // Phase 4: Live
            if (config.includeLive && currentCoroutineContext().isActive) {
                UnifiedLog.d(TAG, "Scanning live channels...")

                try {
                    val channels = source.loadLiveChannels()

                    for (channel in channels) {
                        if (!currentCoroutineContext().isActive) break

                        val catalogItem = mapper.fromChannel(channel, headers)
                        trySend(XtreamCatalogEvent.ItemDiscovered(catalogItem))
                        liveCount++

                        if (liveCount % PROGRESS_LOG_INTERVAL == 0) {
                            trySend(
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

                    UnifiedLog.d(TAG, "Live scan complete: $liveCount items")
                } catch (e: XtreamCatalogSourceException) {
                    UnifiedLog.w(TAG, "Live scan failed: ${e.message}")
                }
            }

            // Check if cancelled
            if (!currentCoroutineContext().isActive) {
                UnifiedLog.i(TAG, "Scan cancelled")
                trySend(
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
                    "(vod=$vodCount, series=$seriesCount, episodes=$episodeCount, live=$liveCount) " +
                    "in ${durationMs}ms",
            )

            trySend(
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
            trySend(
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
