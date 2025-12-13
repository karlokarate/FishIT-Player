package com.fishit.player.feature.telegram.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing Telegram media in the feature layer.
 *
 * **Architecture Compliance:**
 * - Interface lives in feature/telegram-media (domain package)
 * - Implementation lives in infra/data-telegram (adapter layer)
 * - Returns domain models (TelegramMediaItem), NOT pipeline models (RawMediaMetadata)
 * - Feature layer depends on this interface, NOT on infra:data-telegram module
 *
 * **Dependency Inversion:**
 * ```
 * feature/telegram-media (owns interface)
 *        ↑
 *        | implements
 *        |
 * infra/data-telegram (adapter)
 * ```
 */
interface TelegramMediaRepository {

    /**
     * Observe all Telegram media items.
     *
     * @return Flow of domain media items for UI display
     */
    fun observeAll(): Flow<List<TelegramMediaItem>>

    /**
     * Observe media from a specific chat.
     *
     * @param chatId Telegram chat ID
     * @return Flow of media items from the specified chat
     */
    fun observeByChat(chatId: Long): Flow<List<TelegramMediaItem>>

    /**
     * Get a single media item by ID.
     *
     * @param mediaId Media identifier
     * @return Media item or null if not found
     */
    suspend fun getById(mediaId: String): TelegramMediaItem?

    /**
     * Search media by title.
     *
     * @param query Search query
     * @param limit Maximum results
     * @return Matching media items
     */
    suspend fun search(query: String, limit: Int = 50): List<TelegramMediaItem>
}
