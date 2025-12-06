package com.fishit.player.core.model

/**
 * Enumeration of supported playback content types in FishIT Player.
 *
 * Each type represents a different source or format of media content
 * that the player can handle.
 */
enum class PlaybackType {
    /** Video on Demand content from Xtream IPTV providers */
    VOD,

    /** Live TV channel streams from Xtream IPTV providers */
    LIVE,

    /** Series/show episodes from Xtream IPTV providers */
    SERIES,

    /** Media files from Telegram chats */
    TELEGRAM,

    /** Local files or network URLs (file://, http://, https://) */
    IO,

    /** Audiobook content */
    AUDIOBOOK,
}
