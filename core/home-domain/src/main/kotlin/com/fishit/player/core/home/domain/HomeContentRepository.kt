package com.fishit.player.core.home.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing aggregated content for the Home screen.
 *
 * **Architecture:**
 * - Contract lives in core modules (domain API)
 * - Implementations live in infra modules (adapter/repository)
 * - UI features depend only on this interface
 */
interface HomeContentRepository {
    fun observeContinueWatching(): Flow<List<HomeMediaItem>>

    fun observeRecentlyAdded(): Flow<List<HomeMediaItem>>

    fun observeMovies(): Flow<List<HomeMediaItem>>

    fun observeSeries(): Flow<List<HomeMediaItem>>

    fun observeClips(): Flow<List<HomeMediaItem>>

    fun observeXtreamLive(): Flow<List<HomeMediaItem>>

    // ==================== Legacy Methods (backward compatibility) ====================

    /**
     * @deprecated Use observeMovies(), observeSeries(), observeClips() instead.
     */
    fun observeTelegramMedia(): Flow<List<HomeMediaItem>>

    /**
     * @deprecated Use observeMovies() instead.
     */
    fun observeXtreamVod(): Flow<List<HomeMediaItem>>

    /**
     * @deprecated Use observeSeries() instead.
     */
    fun observeXtreamSeries(): Flow<List<HomeMediaItem>>
}
