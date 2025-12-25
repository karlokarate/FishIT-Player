package com.fishit.player.feature.home.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing aggregated content for the Home screen.
 *
 * **Architecture Compliance:**
 * - Interface lives in feature/home (domain package)
 * - Implementations live in infra/data-* (adapter layer)
 * - Returns HomeMediaItem (feature-domain model)
 * - Feature layer depends on this interface, NOT on infra:data-* modules
 *
 * **Dependency Inversion:**
 * ```
 * feature/home (owns interface)
 *        ↑
 *        | implements
 *        |
 * infra/data-home (adapter)
 * ```
 *
 * **Design Note:**
 * This interface aggregates multiple content sources (Telegram, Xtream VOD/Live/Series)
 * into a single interface for the Home screen. Each method corresponds to a row
 * displayed on the Home screen.
 *
 * The adapter layer is responsible for mapping RawMediaMetadata to HomeMediaItem,
 * keeping the feature layer decoupled from core model details.
 */
interface HomeContentRepository {

    /**
     * Observe items the user has started but not finished watching.
     * 
     * @return Flow of continue watching items for Home display
     */
    fun observeContinueWatching(): Flow<List<HomeMediaItem>>

    /**
     * Observe recently added items across all sources.
     * 
     * @return Flow of recently added items for Home display
     */
    fun observeRecentlyAdded(): Flow<List<HomeMediaItem>>

    /**
     * Observe all movies from all sources (Xtream VOD + Telegram movies).
     * 
     * Cross-pipeline: Items with same canonical ID are merged.
     *
     * @return Flow of movie items for Home display
     */
    fun observeMovies(): Flow<List<HomeMediaItem>>

    /**
     * Observe all series from all sources (Xtream Series + Telegram series).
     * 
     * Cross-pipeline: Items with same canonical ID are merged.
     *
     * @return Flow of series items for Home display
     */
    fun observeSeries(): Flow<List<HomeMediaItem>>

    /**
     * Observe Telegram clips (short videos without TMDB match).
     * 
     * Clips are typically:
     * - Short duration (< 30 min)
     * - No TMDB enrichment possible
     * - Exclusively from Telegram pipeline
     *
     * @return Flow of clip items for Home display
     */
    fun observeClips(): Flow<List<HomeMediaItem>>

    /**
     * Observe Xtream live TV channels.
     *
     * @return Flow of live channels for Home display
     */
    fun observeXtreamLive(): Flow<List<HomeMediaItem>>

    // ==================== Legacy Methods (for backward compatibility) ====================

    /**
     * Observe Telegram media items.
     * @deprecated Use observeMovies(), observeSeries(), observeClips() instead
     * @return Flow of Telegram media items for Home display
     */
    fun observeTelegramMedia(): Flow<List<HomeMediaItem>>

    /**
     * Observe Xtream VOD items.
     * @deprecated Use observeMovies() instead
     * @return Flow of VOD items for Home display
     */
    fun observeXtreamVod(): Flow<List<HomeMediaItem>>

    /**
     * Observe Xtream series items.
     * @deprecated Use observeSeries() instead
     * @return Flow of series items for Home display
     */
    fun observeXtreamSeries(): Flow<List<HomeMediaItem>>
}
