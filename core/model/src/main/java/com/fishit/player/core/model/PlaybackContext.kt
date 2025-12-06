package com.fishit.player.core.model

/**
 * Core playback context model that represents all information needed to initiate playback.
 *
 * This is the primary data model used across all pipelines (Xtream, Telegram, IO, Audiobook)
 * to communicate playback intent to the player.
 *
 * @property type The type of content being played
 * @property uri The playback URI (can be http://, tg://, file://, etc.)
 * @property title Human-readable title for the content
 * @property subtitle Optional subtitle/description
 * @property posterUrl Optional poster/thumbnail URL
 * @property contentId Unique identifier for this content (used for resume points, history, etc.)
 * @property metadata Additional metadata as key-value pairs
 */
data class PlaybackContext(
    val type: PlaybackType,
    val uri: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val contentId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * Creates a test VOD playback context for testing purposes.
         */
        fun testVod(
            uri: String,
            title: String = "Test VOD",
            contentId: String = uri,
        ): PlaybackContext =
            PlaybackContext(
                type = PlaybackType.VOD,
                uri = uri,
                title = title,
                contentId = contentId,
            )

        /**
         * Creates a test audio playback context for testing purposes.
         */
        fun testAudio(
            uri: String,
            title: String = "Test Audio",
            contentId: String = uri,
        ): PlaybackContext =
            PlaybackContext(
                type = PlaybackType.AUDIO,
                uri = uri,
                title = title,
                contentId = contentId,
            )
    }
}
