package com.fishit.player.feature.live.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Live TV content.
 *
 * Provides live channel data for the Live TV browser screen.
 *
 * **Architecture:**
 * - Feature layer defines this interface
 * - Implementation lives in infra/data-xtream layer
 * - Returns domain models (LiveChannel), not RawMediaMetadata
 *
 * **Content Sources:**
 * - Xtream live channels
 */
interface LiveContentRepository {

    /**
     * Observe all live channels.
     *
     * @param categoryId Optional category filter
     * @return Flow of live channels for display
     */
    fun observeChannels(categoryId: String? = null): Flow<List<LiveChannel>>

    /**
     * Get available channel categories.
     *
     * @return Flow of category list
     */
    fun observeCategories(): Flow<List<LiveCategory>>

    /**
     * Search channels by name.
     *
     * @param query Search query
     * @param limit Maximum results
     * @return Matching channels
     */
    suspend fun search(query: String, limit: Int = 50): List<LiveChannel>

    /**
     * Get recently watched channels.
     *
     * @param limit Maximum channels to return
     * @return List of recently watched channels
     */
    suspend fun getRecentChannels(limit: Int = 10): List<LiveChannel>

    /**
     * Get favorite channels.
     *
     * @return List of favorite channels
     */
    fun observeFavorites(): Flow<List<LiveChannel>>

    /**
     * Toggle favorite status for a channel.
     *
     * @param channelId Channel source ID
     * @param isFavorite New favorite status
     */
    suspend fun setFavorite(channelId: String, isFavorite: Boolean)

    /**
     * Record that a channel was watched (for recent channels).
     *
     * @param channelId Channel source ID
     */
    suspend fun recordWatched(channelId: String)
}
