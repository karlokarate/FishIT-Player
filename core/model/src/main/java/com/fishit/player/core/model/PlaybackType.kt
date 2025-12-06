package com.fishit.player.core.model

/**
 * Represents the type of playback content.
 */
enum class PlaybackType {
    /**
     * Video on Demand content (movies, recorded shows, etc.)
     */
    VOD,

    /**
     * Live streaming content (live TV channels, live sports, etc.)
     */
    LIVE,

    /**
     * Series/episodic content
     */
    SERIES,

    /**
     * Audio content (music, podcasts, audiobooks)
     */
    AUDIO,

    /**
     * Local file content
     */
    LOCAL,
}
