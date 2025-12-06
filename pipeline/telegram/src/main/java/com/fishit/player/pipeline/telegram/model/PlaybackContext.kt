package com.fishit.player.pipeline.telegram.model

/**
 * Simplified PlaybackContext for Phase 2 stub.
 *
 * This is a temporary model used in the stub phase. In Phase 3+,
 * this will be replaced by the actual PlaybackContext from :core:model.
 *
 * Maps to v1's PlaybackContext structure
 * (app/src/main/java/com/chris/m3usuite/player/internal/domain/PlaybackContext.kt)
 *
 * @property type Playback type (VOD, SERIES, LIVE, TELEGRAM)
 * @property mediaId Generic media identifier
 * @property telegramChatId Telegram chat ID (for Telegram content)
 * @property telegramMessageId Telegram message ID (for Telegram content)
 * @property telegramFileId Telegram file ID (for Telegram content)
 * @property seriesId Series ID (for series content)
 * @property season Season number (for series content)
 * @property episodeNumber Episode number (for series content)
 */
data class PlaybackContext(
    val type: PlaybackType,
    val mediaId: Long? = null,
    val telegramChatId: Long? = null,
    val telegramMessageId: Long? = null,
    val telegramFileId: String? = null,
    val seriesId: Int? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
)

/**
 * Playback type enumeration.
 *
 * Temporary enum for Phase 2. Will be replaced by :core:model version in Phase 3+.
 */
enum class PlaybackType {
    VOD,
    SERIES,
    LIVE,
    TELEGRAM,
    IO,
    AUDIOBOOK,
}
