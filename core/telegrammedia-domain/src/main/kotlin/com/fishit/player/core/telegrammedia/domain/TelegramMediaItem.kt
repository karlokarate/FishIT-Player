package com.fishit.player.core.telegrammedia.domain

import com.fishit.player.core.model.MediaType

/**
 * Domain model for Telegram media items.
 */
data class TelegramMediaItem(
    val mediaId: String,
    val title: String,
    val sourceLabel: String,
    val mediaType: MediaType,
    val durationMs: Long? = null,
    val posterUrl: String? = null,
    val chatId: Long? = null,
    val messageId: Long? = null,
    val remoteId: String? = null,
    val mimeType: String? = null,
)
