package com.fishit.player.pipeline.xtream.repository.stub

import com.fishit.player.pipeline.xtream.model.XtreamEpisode
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import com.fishit.player.pipeline.xtream.repository.XtreamCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stub implementation of [XtreamCatalogRepository] for Phase 2.
 *
 * This implementation returns deterministic empty or mock data for all operations.
 * It is designed to allow the v2 architecture to compile and integrate without
 * requiring real API implementation.
 *
 * Production implementation will replace this with actual Xtream API calls
 * and ObjectBox persistence.
 *
 * Constructor is injectable - no Hilt modules required for stub phase.
 */
class StubXtreamCatalogRepository : XtreamCatalogRepository {
    override fun getVodItems(
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<XtreamVodItem>> = flowOf(emptyList())

    override fun getVodById(vodId: Int): Flow<XtreamVodItem?> = flowOf(null)

    override fun getSeriesItems(
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<XtreamSeriesItem>> = flowOf(emptyList())

    override fun getSeriesById(seriesId: Int): Flow<XtreamSeriesItem?> = flowOf(null)

    override fun getEpisodes(
        seriesId: Int,
        seasonNumber: Int,
    ): Flow<List<XtreamEpisode>> = flowOf(emptyList())

    override fun search(
        query: String,
        limit: Int,
    ): Flow<List<Any>> = flowOf(emptyList())

    override suspend fun refreshCatalog(): Result<Unit> = Result.success(Unit)
}
