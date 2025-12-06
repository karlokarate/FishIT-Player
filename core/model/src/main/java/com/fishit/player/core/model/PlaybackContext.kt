package com.fishit.player.core.model

/**
 * Complete context for media playback in FishIT Player.
 *
 * This class encapsulates all information needed to:
 * - Resolve the media source
 * - Display metadata (title, poster, etc.)
 * - Track resume positions
 * - Apply content filtering (kids mode, etc.)
 *
 * @property type The type of content being played
 * @property uri The primary URI/URL for the media stream
 * @property title Human-readable title of the content
 * @property contentId Unique identifier for tracking resume points and history
 * @property posterUrl Optional URL for poster/thumbnail image
 * @property vodId Optional ID for VOD content (Xtream)
 * @property seriesId Optional ID for series content (Xtream)
 * @property season Optional season number for series episodes
 * @property episode Optional episode number for series episodes
 * @property liveChannelId Optional ID for live TV channels (Xtream)
 * @property telegramFileId Optional file ID for Telegram media
 * @property telegramChatId Optional chat ID for Telegram media
 * @property telegramMessageId Optional message ID for Telegram media
 * @property ioFilePath Optional local file path for IO content
 * @property audiobookId Optional ID for audiobook content
 */
data class PlaybackContext(
    val type: PlaybackType,
    val uri: String,
    val title: String? = null,
    val contentId: String? = null,
    val posterUrl: String? = null,
    val vodId: Long? = null,
    val seriesId: Long? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val liveChannelId: Long? = null,
    val telegramFileId: String? = null,
    val telegramChatId: Long? = null,
    val telegramMessageId: Long? = null,
    val ioFilePath: String? = null,
    val audiobookId: String? = null,
)
