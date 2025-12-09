package com.fishit.player.pipeline.telegram.catalog

/**
 * Media kind classification for Telegram messages.
 *
 * Provides a simple categorization of media types based on TDLib message content.
 * Used by [TelegramCatalogMessageMapper] to classify messages before mapping to [RawMediaMetadata].
 *
 * **Design Principles:**
 * - Simple enum for basic media type detection
 * - Based on TDLib message content types and MIME types
 * - Independent of MediaType (which is for normalized metadata)
 *
 * @see TelegramCatalogMessageMapper.classifyMediaKind
 */
enum class MediaKind {
    /** Image/photo content (MessagePhoto, image/star MIME types). */
    Image,

    /** Video content (MessageVideo, video/star MIME types, MessageAnimation). */
    Video,

    /** Audio content (MessageAudio, MessageVoiceNote, audio/star MIME types). */
    Audio,

    /** Document content (MessageDocument, archives, RAR, ZIP, etc.). */
    Document,

    /** Unknown or non-media content. */
    Unknown,
}
