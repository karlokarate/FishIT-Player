package com.fishit.player.pipeline.telegram.model

/**
 * Type of Telegram media content.
 *
 * Based on analysis of real Telegram export JSONs in docs/telegram/exports/exports/.
 * Represents the different types of content that can appear in Telegram messages.
 */
enum class TelegramMediaType {
    /** Video file (content.video or document with video mime type) */
    VIDEO,

    /** Document file (archives, RARs, ZIPs, or non-video documents) */
    DOCUMENT,

    /** Audio file (music, soundtracks, audio episodes) */
    AUDIO,

    /** Photo message with multiple sizes */
    PHOTO,

    /** Pure text message with metadata (no playable media) */
    TEXT_METADATA,

    /** Unknown or unsupported media type */
    OTHER,
}
