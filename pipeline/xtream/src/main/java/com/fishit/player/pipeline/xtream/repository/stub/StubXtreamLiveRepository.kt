package com.fishit.player.pipeline.xtream.repository.stub

import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpgEntry
import com.fishit.player.pipeline.xtream.repository.XtreamLiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stub implementation of [XtreamLiveRepository] for Phase 2.
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
class StubXtreamLiveRepository : XtreamLiveRepository {
    override fun getChannels(
        categoryId: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<XtreamChannel>> = flowOf(emptyList())

    override fun getChannelById(channelId: Int): Flow<XtreamChannel?> = flowOf(null)

    override fun getEpgForChannel(
        epgChannelId: String,
        startTime: Long,
        endTime: Long,
    ): Flow<List<XtreamEpgEntry>> = flowOf(emptyList())

    override fun getCurrentEpg(epgChannelId: String): Flow<XtreamEpgEntry?> = flowOf(null)

    override fun searchChannels(
        query: String,
        limit: Int,
    ): Flow<List<XtreamChannel>> = flowOf(emptyList())

    override suspend fun refreshLiveData(): Result<Unit> = Result.success(Unit)
}
