package com.fishit.player.core.live.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Live TV content.
 */
interface LiveContentRepository {
    fun observeChannels(categoryId: String? = null): Flow<List<LiveChannel>>

    fun observeCategories(): Flow<List<LiveCategory>>

    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<LiveChannel>

    suspend fun getRecentChannels(limit: Int = 10): List<LiveChannel>

    fun observeFavorites(): Flow<List<LiveChannel>>

    suspend fun setFavorite(
        channelId: String,
        isFavorite: Boolean,
    )

    suspend fun recordWatched(channelId: String)
}
