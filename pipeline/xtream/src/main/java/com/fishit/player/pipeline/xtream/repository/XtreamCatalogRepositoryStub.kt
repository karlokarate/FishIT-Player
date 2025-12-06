package com.fishit.player.pipeline.xtream.repository

import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stub implementation of [XtreamCatalogRepository] for Phase 2.
 *
 * This implementation returns empty lists and null values for all queries,
 * simulating network latency with delays. It is fully wired via constructor
 * injection and is Hilt-compatible (though no DI module is provided in Phase 2).
 *
 * ## Constructor Injection
 * All dependencies are injected via constructor. For Phase 2, this class
 * has no dependencies, but the pattern is established for Phase 3+.
 *
 * ## Behavior
 * - All list queries return empty Flow
 * - All single-item lookups return null
 * - Network simulation uses 100ms delay
 * - Deterministic and testable
 */
class XtreamCatalogRepositoryStub : XtreamCatalogRepository {
    override fun getVodItems(categoryId: Long?): Flow<List<XtreamVodItem>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override fun searchVodItems(query: String): Flow<List<XtreamVodItem>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override suspend fun getVodById(vodId: Long): XtreamVodItem? {
        delay(NETWORK_DELAY_MS)
        return null
    }

    override fun getSeries(categoryId: Long?): Flow<List<XtreamSeriesItem>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override fun searchSeries(query: String): Flow<List<XtreamSeriesItem>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override suspend fun getSeriesById(seriesId: Long): XtreamSeriesItem? {
        delay(NETWORK_DELAY_MS)
        return null
    }

    override fun getEpisodes(seriesId: Long): Flow<List<XtreamEpisode>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override fun getEpisodesBySeason(
        seriesId: Long,
        seasonNumber: Int,
    ): Flow<List<XtreamEpisode>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override suspend fun getEpisode(
        seriesId: Long,
        seasonNumber: Int,
        episodeNumber: Int,
    ): XtreamEpisode? {
        delay(NETWORK_DELAY_MS)
        return null
    }

    companion object {
        /** Simulated network delay in milliseconds */
        private const val NETWORK_DELAY_MS = 100L
    }
}
