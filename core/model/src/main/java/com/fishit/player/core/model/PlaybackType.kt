package com.fishit.player.core.model

/**
 * Types of playback content supported by FishIT Player v2.
 */
enum class PlaybackType {
    /** Video on Demand (movies, standalone videos) */
    VOD,

    /** Series episodes */
    SERIES,

    /** Live TV channels */
    LIVE,

    /** Telegram media files */
    TELEGRAM,

    /** Audiobook content */
    AUDIOBOOK,

    /** Local/IO content (device storage, SAF, network shares) */
    IO,
}
