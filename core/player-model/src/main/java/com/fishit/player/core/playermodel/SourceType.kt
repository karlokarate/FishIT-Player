package com.fishit.player.core.playermodel

/**
 * Types of content sources supported by FishIT Player.
 *
 * This enum identifies WHERE the content comes from,
 * which determines which PlaybackSourceFactory handles it.
 */
enum class SourceType {
    /** Telegram media files (via Telegram API) */
    TELEGRAM,

    /** Xtream Codes API content (Live TV, VOD, Series) */
    XTREAM,

    /** Local file on device storage */
    FILE,

    /** Generic HTTP/HTTPS URL */
    HTTP,

    /** Audiobook content */
    AUDIOBOOK,

    /** Unknown or unsupported source */
    UNKNOWN,
}
