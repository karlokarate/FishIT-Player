package com.fishit.player.pipeline.xtream.repository

import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpgEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Xtream live TV and EPG operations.
 *
 * This interface defines the contract for accessing Xtream live TV channels
 * and Electronic Program Guide (EPG) data.
 *
 * Phase 2 stub: Returns empty/predictable results.
 */
interface XtreamLiveRepository {
    /**
     * Retrieves a list of live TV channels, optionally filtered by category.
     *
     * @param categoryId Optional category filter
     * @param limit Maximum number of channels to return
     * @param offset Pagination offset
     * @return Flow emitting list of channels
     */
    fun getChannels(
        categoryId: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): Flow<List<XtreamChannel>>

    /**
     * Retrieves a single channel by ID.
     *
     * @param channelId The channel stream ID
     * @return Flow emitting the channel, or null if not found
     */
    fun getChannelById(channelId: Int): Flow<XtreamChannel?>

    /**
     * Retrieves EPG entries for a specific channel within a time range.
     *
     * @param epgChannelId The EPG channel identifier
     * @param startTime Start time in Unix timestamp (seconds)
     * @param endTime End time in Unix timestamp (seconds)
     * @return Flow emitting list of EPG entries
     */
    fun getEpgForChannel(
        epgChannelId: String,
        startTime: Long,
        endTime: Long,
    ): Flow<List<XtreamEpgEntry>>

    /**
     * Retrieves the current EPG entry (now playing) for a channel.
     *
     * @param epgChannelId The EPG channel identifier
     * @return Flow emitting the current EPG entry, or null if none available
     */
    fun getCurrentEpg(epgChannelId: String): Flow<XtreamEpgEntry?>

    /**
     * Searches live TV channels by query string.
     *
     * @param query The search query
     * @param limit Maximum number of results
     * @return Flow emitting search results
     */
    fun searchChannels(
        query: String,
        limit: Int = 50,
    ): Flow<List<XtreamChannel>>

    /**
     * Refreshes the channel list and EPG data from the remote source.
     *
     * @return Result indicating success or failure
     */
    suspend fun refreshLiveData(): Result<Unit>
}
