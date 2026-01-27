package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [XtreamCatalogSource] that delegates to [XtreamPipelineAdapter].
 *
 * This implementation:
 * - Loads content via the pipeline adapter (which wraps XtreamApiClient)
 * - Handles errors by wrapping in XtreamCatalogSourceException
 * - **PLATINUM:** Episodes loaded in parallel with streaming results
 *
 * **Architecture:**
 * - Pipeline Layer: This source uses XtreamPipelineAdapter
 * - XtreamPipelineAdapter wraps XtreamApiClient (Transport Layer)
 * - Internal DTOs (XtreamVodItem, etc.) stay within pipeline, converted to RawMediaMetadata
 */
@Singleton
class DefaultXtreamCatalogSource
    @Inject
    constructor(
        private val adapter: XtreamPipelineAdapter,
    ) : XtreamCatalogSource {
        override suspend fun loadVodItems(): List<XtreamVodItem> =
            try {
                adapter.loadVodItems()
            } catch (e: Exception) {
                throw XtreamCatalogSourceException("Failed to load VOD items", e)
            }

        override suspend fun loadSeriesItems(): List<XtreamSeriesItem> =
            try {
                adapter.loadSeriesItems()
            } catch (e: Exception) {
                throw XtreamCatalogSourceException("Failed to load series items", e)
            }

        @Deprecated(
            message = "Use loadEpisodesStreaming() for parallel loading with checkpoint support",
            replaceWith = ReplaceWith("loadEpisodesStreaming()"),
        )
        @Suppress("DEPRECATION")
        override suspend fun loadEpisodes(): List<XtreamEpisode> =
            try {
                // First load all series, then fetch episodes for each
                val series = adapter.loadSeriesItems()
                val allEpisodes = mutableListOf<XtreamEpisode>()

                for (seriesItem in series) {
                    try {
                        // Pass seriesName for context in RawMediaMetadata
                        val episodes = adapter.loadEpisodes(seriesItem.id, seriesItem.name)
                        allEpisodes.addAll(episodes)
                    } catch (e: Exception) {
                        UnifiedLog.w(TAG) { "Failed to load episodes for series ${seriesItem.id}: ${e.message}" }
                    }
                }

                allEpisodes
            } catch (e: Exception) {
                throw XtreamCatalogSourceException("Failed to load episodes", e)
            }

        /**
         * PLATINUM: Stream episodes from all series with parallel loading.
         *
         * **Performance Characteristics:**
         * - 4 concurrent series by default (configurable via [parallelism])
         * - ~4x faster than sequential loading for typical catalogs
         * - Memory-efficient: episodes emitted immediately, not accumulated
         * - Checkpoint-friendly: skip already-processed series via [excludeSeriesIds]
         *
         * **Flow Emission Order:**
         * For each series (in parallel):
         * 1. EpisodeBatchResult.Batch (if series has episodes)
         * 2. EpisodeBatchResult.SeriesComplete OR EpisodeBatchResult.SeriesFailed
         *
         * @param parallelism Max concurrent getSeriesInfo() calls
         * @param excludeSeriesIds Series to skip (for checkpoint resume)
         */
        override fun loadEpisodesStreaming(
            parallelism: Int,
            excludeSeriesIds: Set<Int>,
        ): Flow<EpisodeBatchResult> =
            channelFlow {
                val startTimeMs = System.currentTimeMillis()

                // Step 1: Load series list
                val allSeries =
                    try {
                        adapter.loadSeriesItems()
                    } catch (e: Exception) {
                        UnifiedLog.e(TAG, e) { "Failed to load series list for episode streaming" }
                        throw XtreamCatalogSourceException("Failed to load series list", e)
                    }

                // Step 2: Filter out already-processed series
                val seriesToProcess =
                    if (excludeSeriesIds.isEmpty()) {
                        allSeries
                    } else {
                        allSeries.filter { it.id !in excludeSeriesIds }
                    }

                val totalSeries = allSeries.size
                val skippedSeries = totalSeries - seriesToProcess.size

                UnifiedLog.i(TAG) {
                    "Episode streaming: $totalSeries series total, $skippedSeries skipped, " +
                        "${seriesToProcess.size} to process (parallelism=$parallelism)"
                }

                if (seriesToProcess.isEmpty()) {
                    UnifiedLog.d(TAG) { "No series to process for episodes" }
                    return@channelFlow
                }

                // Step 3: Process series in parallel with semaphore limiting
                val semaphore = Semaphore(parallelism)
                var completedCount = 0
                var failedCount = 0
                var totalEpisodes = 0
                val lock = Any()

                supervisorScope {
                    seriesToProcess
                        .map { seriesItem ->
                            async {
                                semaphore.withPermit {
                                    val result = processSeriesEpisodes(seriesItem)
                                    synchronized(lock) {
                                        when (result) {
                                            is ProcessResult.Success -> {
                                                completedCount++
                                                totalEpisodes += result.episodeCount
                                            }
                                            is ProcessResult.Failed -> failedCount++
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                }

                val durationMs = System.currentTimeMillis() - startTimeMs
                UnifiedLog.i(TAG) {
                    "Episode streaming complete: ${seriesToProcess.size} series in ${durationMs}ms " +
                        "(completed=$completedCount, failed=$failedCount, episodes=$totalEpisodes)"
                }
            }

        /** Internal result type for process tracking. */
        private sealed class ProcessResult {
            data class Success(
                val episodeCount: Int,
            ) : ProcessResult()

            data object Failed : ProcessResult()
        }

        /**
         * Process a single series: load episodes and emit results.
         *
         * @return ProcessResult for counter tracking
         */
        private suspend fun kotlinx.coroutines.channels.SendChannel<EpisodeBatchResult>.processSeriesEpisodes(
            seriesItem: XtreamSeriesItem,
        ): ProcessResult =
            try {
                val episodes = adapter.loadEpisodes(seriesItem.id, seriesItem.name)

                if (episodes.isNotEmpty()) {
                    send(
                        EpisodeBatchResult.Batch(
                            seriesId = seriesItem.id,
                            seriesName = seriesItem.name,
                            episodes = episodes,
                        ),
                    )
                }

                send(
                    EpisodeBatchResult.SeriesComplete(
                        seriesId = seriesItem.id,
                        episodeCount = episodes.size,
                    ),
                )

                if (episodes.isNotEmpty()) {
                    UnifiedLog.v(TAG) { "Series ${seriesItem.id} (${seriesItem.name}): ${episodes.size} episodes" }
                }

                ProcessResult.Success(episodes.size)
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Series ${seriesItem.id} (${seriesItem.name}) failed: ${e.message}" }
                send(
                    EpisodeBatchResult.SeriesFailed(
                        seriesId = seriesItem.id,
                        error = e,
                    ),
                )
                ProcessResult.Failed
            }

        override suspend fun loadLiveChannels(): List<XtreamChannel> =
            try {
                adapter.loadLiveChannels()
            } catch (e: Exception) {
                throw XtreamCatalogSourceException("Failed to load live channels", e)
            }

        // ============================================================================
        // Batch Streaming Implementations
        // ============================================================================

        override suspend fun streamVodItems(
            batchSize: Int,
            onBatch: suspend (List<XtreamVodItem>) -> Unit,
        ): Int =
            try {
                adapter.streamVodItems(batchSize = batchSize, onBatch = onBatch)
            } catch (e: Exception) {
                throw XtreamCatalogSourceException("Failed to stream VOD items", e)
            }

        override suspend fun streamSeriesItems(
            batchSize: Int,
            onBatch: suspend (List<XtreamSeriesItem>) -> Unit,
        ): Int =
            try {
                adapter.streamSeriesItems(batchSize = batchSize, onBatch = onBatch)
            } catch (e: Exception) {
                throw XtreamCatalogSourceException("Failed to stream series items", e)
            }

        override suspend fun streamLiveChannels(
            batchSize: Int,
            onBatch: suspend (List<XtreamChannel>) -> Unit,
        ): Int =
            try {
                adapter.streamLiveChannels(batchSize = batchSize, onBatch = onBatch)
            } catch (e: Exception) {
                throw XtreamCatalogSourceException("Failed to stream live channels", e)
            }

        companion object {
            private const val TAG = "XtreamCatalogSource"
        }
    }
