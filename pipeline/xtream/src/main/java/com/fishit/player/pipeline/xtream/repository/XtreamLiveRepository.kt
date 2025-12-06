package com.fishit.player.pipeline.xtream.repository

import com.fishit.player.pipeline.xtream.model.XtreamChannel
import com.fishit.player.pipeline.xtream.model.XtreamEpgEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing Xtream Live TV channels and EPG data.
 *
 * This interface defines the contract for browsing live TV channels and
 * fetching Electronic Program Guide (EPG) information. In Phase 2,
 * implementations are stubs that return empty/mock data.
 *
 * ## Responsibilities
 * - List available live TV channels by category
 * - Single-channel lookups by ID
 * - Fetch current and next EPG entries for channels
 * - Query EPG by time range
 *
 * ## Phase 3+ Implementation
 * Full implementation will include:
 * - Real Xtream API network calls for live streams
 * - EPG data caching and refresh
 * - Channel favorites and recents
 * - Parental control filtering
 */
interface XtreamLiveRepository {
    /**
     * Retrieves all available live TV channels, optionally filtered by category.
     *
     * @param categoryId Optional category ID to filter results. If null, returns all channels.
     * @return Flow emitting list of channels (empty in Phase 2 stub)
     */
    fun getChannels(categoryId: Long? = null): Flow<List<XtreamChannel>>

    /**
     * Searches live TV channels by name.
     *
     * @param query Search query string
     * @return Flow emitting matching channels (empty in Phase 2 stub)
     */
    fun searchChannels(query: String): Flow<List<XtreamChannel>>

    /**
     * Retrieves a single live TV channel by ID.
     *
     * @param channelId Unique channel identifier
     * @return Channel if found, null otherwise (null in Phase 2 stub)
     */
    suspend fun getChannelById(channelId: Long): XtreamChannel?

    /**
     * Retrieves the current and next EPG entries for a specific channel.
     *
     * This is typically used for "Now & Next" display in the UI.
     *
     * @param channelId Unique channel identifier
     * @return Flow emitting list of EPG entries (current + next, empty in Phase 2 stub)
     */
    fun getCurrentAndNextEpg(channelId: Long): Flow<List<XtreamEpgEntry>>

    /**
     * Retrieves all EPG entries for a specific channel within a time range.
     *
     * @param channelId Unique channel identifier
     * @param startTime Start of time range (Unix timestamp in seconds)
     * @param endTime End of time range (Unix timestamp in seconds)
     * @return Flow emitting list of EPG entries in the time range (empty in Phase 2 stub)
     */
    fun getEpgByTimeRange(
        channelId: Long,
        startTime: Long,
        endTime: Long,
    ): Flow<List<XtreamEpgEntry>>

    /**
     * Retrieves EPG entries for multiple channels at a specific time.
     *
     * This is useful for building a grid-style EPG view.
     *
     * @param channelIds List of channel identifiers
     * @param timestamp Target time (Unix timestamp in seconds)
     * @return Flow emitting map of channel ID to EPG entry (empty in Phase 2 stub)
     */
    fun getEpgAtTime(
        channelIds: List<Long>,
        timestamp: Long,
    ): Flow<Map<Long, XtreamEpgEntry?>>
}
