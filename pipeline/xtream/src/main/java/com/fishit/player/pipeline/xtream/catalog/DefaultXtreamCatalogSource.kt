package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.pipeline.xtream.adapter.XtreamPipelineAdapter
import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [XtreamCatalogSource] that delegates to [XtreamPipelineAdapter].
 *
 * This implementation:
 * - Loads content via the pipeline adapter (which wraps XtreamApiClient)
 * - Handles errors by wrapping in XtreamCatalogSourceException
 * - Episodes are loaded per-series (batch loading via loadAllEpisodes)
 *
 * **Architecture:**
 * - Pipeline Layer: This source uses XtreamPipelineAdapter
 * - XtreamPipelineAdapter wraps XtreamApiClient (Transport Layer)
 * - Internal DTOs (XtreamVodItem, etc.) stay within pipeline, converted to RawMediaMetadata
 */
@Singleton
class DefaultXtreamCatalogSource @Inject constructor(
    private val adapter: XtreamPipelineAdapter
) : XtreamCatalogSource {

    override suspend fun loadVodItems(): List<XtreamVodItem> {
        return try {
            adapter.loadVodItems()
        } catch (e: Exception) {
            throw XtreamCatalogSourceException("Failed to load VOD items", e)
        }
    }

    override suspend fun loadSeriesItems(): List<XtreamSeriesItem> {
        return try {
            adapter.loadSeriesItems()
        } catch (e: Exception) {
            throw XtreamCatalogSourceException("Failed to load series items", e)
        }
    }

    override suspend fun loadEpisodes(): List<XtreamEpisode> {
        return try {
            // First load all series, then fetch episodes for each
            val series = adapter.loadSeriesItems()
            val allEpisodes = mutableListOf<XtreamEpisode>()
            
            for (seriesItem in series) {
                try {
                    val episodes = adapter.loadEpisodes(seriesItem.id)
                    allEpisodes.addAll(episodes)
                } catch (e: Exception) {
                    // Log and continue - don't fail entire load for one series
                    // UnifiedLog.w(TAG, "Failed to load episodes for series ${seriesItem.id}", e)
                }
            }
            
            allEpisodes
        } catch (e: Exception) {
            throw XtreamCatalogSourceException("Failed to load episodes", e)
        }
    }

    override suspend fun loadLiveChannels(): List<XtreamChannel> {
        return try {
            adapter.loadLiveChannels()
        } catch (e: Exception) {
            throw XtreamCatalogSourceException("Failed to load live channels", e)
        }
    }

    companion object {
        private const val TAG = "XtreamCatalogSource"
    }
}
