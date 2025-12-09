package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.RawMediaMetadata

/**
 * Telegram catalog item emitted by the catalog pipeline.
 *
 * Wraps [RawMediaMetadata] with Telegram-specific context (chat, message IDs, chat title).
 * This is the primary output of [TelegramCatalogPipeline.scanCatalog].
 *
 * **Design Principles:**
 * - Immutable value object
 * - Chat context preserved for UI and tracking
 * - Raw metadata follows MEDIA_NORMALIZATION_CONTRACT (no cleaning/normalization)
 *
 * @property raw Raw media metadata from the Telegram message (per MEDIA_NORMALIZATION_CONTRACT).
 * @property chatId Telegram chat ID where the message originated.
 * @property messageId Telegram message ID for this media item.
 * @property chatTitle Human-readable chat title (nullable for privacy chats or missing data).
 */
data class TelegramCatalogItem(
    val raw: RawMediaMetadata,
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String?,
)
