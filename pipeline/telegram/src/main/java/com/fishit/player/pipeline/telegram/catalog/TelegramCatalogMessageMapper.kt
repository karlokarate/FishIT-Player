package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.pipeline.telegram.tdlib.TelegramChatInfo
import dev.g000sha256.tdl.dto.Message

/**
 * Maps TDLib messages to RawMediaMetadata for catalog scanning.
 *
 * **Architecture Compliance:**
 * - NO title cleaning or normalization (per MEDIA_NORMALIZATION_CONTRACT)
 * - NO TMDB lookups or external enrichment
 * - Raw field extraction only, delegate all processing to :core:metadata-normalizer
 *
 * @see RawMediaMetadata
 */
interface TelegramCatalogMessageMapper {
    /**
     * Classify the media kind of a TDLib message.
     *
     * @param message TDLib Message DTO
     * @return MediaKind classification (Image, Video, Audio, Document, Unknown)
     */
    fun classifyMediaKind(message: Message): MediaKind

    /**
     * Convert a TDLib message to RawMediaMetadata.
     *
     * **Contract:**
     * - Returns null if message has no extractable media or required fields are missing
     * - originalTitle from caption/filename (NO cleaning)
     * - mediaType inferred from content type (basic classification)
     * - durationMinutes computed from TDLib duration seconds
     * - sourceType = SourceType.TELEGRAM
     * - sourceLabel from chat title or "Telegram"
     * - sourceId = stable ID from chatId:messageId:fileUniqueId
     *
     * @param message TDLib Message DTO
     * @param chat Chat information for context
     * @param mediaKind Pre-classified media kind (defaults to calling classifyMediaKind)
     * @return RawMediaMetadata or null if message cannot be converted
     */
    fun toRawMediaMetadata(
        message: Message,
        chat: TelegramChatInfo,
        mediaKind: MediaKind = classifyMediaKind(message),
    ): RawMediaMetadata?
}

/**
 * Media kind classification for TDLib messages.
 *
 * Basic categorization used for filtering during catalog scan.
 */
enum class MediaKind {
    /** Photo/image content */
    Image,

    /** Video content (MessageVideo or video-mime documents) */
    Video,

    /** Audio content (MessageAudio, voice notes) */
    Audio,

    /** Document content (archives, PDFs, etc.) */
    Document,

    /** Unknown or non-media content */
    Unknown,
}
