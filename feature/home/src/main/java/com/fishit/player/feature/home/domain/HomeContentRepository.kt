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
     * Observe Telegram media items.
     *
     * @return Flow of Telegram media items for Home display
     */
    fun observeTelegramMedia(): Flow<List<HomeMediaItem>>

    /**
     * Observe Xtream live TV channels.
     *
     * @return Flow of live channels for Home display
     */
    fun observeXtreamLive(): Flow<List<HomeMediaItem>>

    /**
     * Observe Xtream VOD items.
     *
     * @return Flow of VOD items for Home display
     */
    fun observeXtreamVod(): Flow<List<HomeMediaItem>>

    /**
     * Observe Xtream series items.
     *
     * @return Flow of series items for Home display
     */
    fun observeXtreamSeries(): Flow<List<HomeMediaItem>>
}
