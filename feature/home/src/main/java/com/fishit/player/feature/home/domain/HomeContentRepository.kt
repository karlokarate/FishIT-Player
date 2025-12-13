package com.fishit.player.feature.home.domain

import com.fishit.player.core.model.RawMediaMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing aggregated content for the Home screen.
 *
 * **Architecture Compliance:**
 * - Interface lives in feature/home (domain package)
 * - Implementations live in infra/data-* (adapter layer)
 * - Returns RawMediaMetadata (canonical media model)
 * - Feature layer depends on this interface, NOT on infra:data-* modules
 *
 * **Dependency Inversion:**
 * ```
 * feature/home (owns interface)
 *        ↑
 *        | implements
 *        |
 * infra/data-telegram, infra/data-xtream (adapters)
 * ```
 *
 * **Design Note:**
 * This interface aggregates multiple content sources (Telegram, Xtream VOD/Live/Series)
 * into a single interface for the Home screen. Each method corresponds to a row
 * displayed on the Home screen.
 */
interface HomeContentRepository {

    /**
     * Observe Telegram media items.
     *
     * @return Flow of Telegram media for Home display
     */
    fun observeTelegramMedia(): Flow<List<RawMediaMetadata>>

    /**
     * Observe Xtream live TV channels.
     *
     * @return Flow of live channels for Home display
     */
    fun observeXtreamLive(): Flow<List<RawMediaMetadata>>

    /**
     * Observe Xtream VOD items.
     *
     * @return Flow of VOD items for Home display
     */
    fun observeXtreamVod(): Flow<List<RawMediaMetadata>>

    /**
     * Observe Xtream series items.
     *
     * @return Flow of series items for Home display
     */
    fun observeXtreamSeries(): Flow<List<RawMediaMetadata>>
}
