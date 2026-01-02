package com.fishit.player.core.telegrammedia.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing Telegram media.
 */
interface TelegramMediaRepository {

    fun observeAll(): Flow<List<TelegramMediaItem>>

    fun observeByChat(chatId: Long): Flow<List<TelegramMediaItem>>

    suspend fun getById(mediaId: String): TelegramMediaItem?

    suspend fun search(query: String, limit: Int = 50): List<TelegramMediaItem>
}
