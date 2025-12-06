package com.fishit.player.pipeline.xtream.repository

import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpgEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stub implementation of [XtreamLiveRepository] for Phase 2.
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
class XtreamLiveRepositoryStub : XtreamLiveRepository {
    override fun getChannels(categoryId: Long?): Flow<List<XtreamChannel>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override fun searchChannels(query: String): Flow<List<XtreamChannel>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override suspend fun getChannelById(channelId: Long): XtreamChannel? {
        delay(NETWORK_DELAY_MS)
        return null
    }

    override fun getCurrentAndNextEpg(channelId: Long): Flow<List<XtreamEpgEntry>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override fun getEpgByTimeRange(
        channelId: Long,
        startTime: Long,
        endTime: Long,
    ): Flow<List<XtreamEpgEntry>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyList())
        }

    override fun getEpgAtTime(
        channelIds: List<Long>,
        timestamp: Long,
    ): Flow<Map<Long, XtreamEpgEntry?>> =
        flow {
            delay(NETWORK_DELAY_MS)
            emit(emptyMap())
        }

    companion object {
        /** Simulated network delay in milliseconds */
        private const val NETWORK_DELAY_MS = 100L
    }
}
