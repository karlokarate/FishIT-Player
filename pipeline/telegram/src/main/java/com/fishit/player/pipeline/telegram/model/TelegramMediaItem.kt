package com.fishit.player.pipeline.telegram.model

/**
 * Domain model representing a Telegram media item for FishIT Player v2.
 *
 * This is a STUB implementation for Phase 2 Task 3 (P2-T3).
 * Represents media content from Telegram that can be played in the internal player.
 *
 * Maps to ObxTelegramMessage but provides a cleaner domain interface.
 *
 * @property id Unique identifier for this media item
 * @property chatId Telegram chat ID where the media is located
 * @property messageId Telegram message ID
 * @property fileId Telegram file ID (nullable for stub)
 * @property remoteId Stable TDLib remote ID (session-independent)
 * @property title Display title (from caption or parsed metadata)
 * @property fileName Original file name
 * @property caption Message caption
 * @property mimeType MIME type of the media file
 * @property sizeBytes File size in bytes
 * @property durationSecs Duration in seconds (for video/audio)
 * @property width Video width in pixels
 * @property height Video height in pixels
 * @property supportsStreaming Whether the file supports streaming playback
 * @property localPath Local file path if downloaded (nullable)
 * @property thumbnailPath Local thumbnail path (nullable)
 * @property date Message timestamp
 * @property isSeries Whether this is part of a series
 * @property seriesName Series name if applicable
 * @property seasonNumber Season number if applicable
 * @property episodeNumber Episode number if applicable
 * @property episodeTitle Episode title if applicable
 * @property year Release year (from parsed metadata)
 * @property genres Comma-separated genre list
 * @property description Content description
 */
data class TelegramMediaItem(
    val id: Long = 0,
    val chatId: Long = 0,
    val messageId: Long = 0,
    val fileId: Int? = null,
    val remoteId: String? = null,
    val title: String = "",
    val fileName: String? = null,
    val caption: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val durationSecs: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val supportsStreaming: Boolean? = null,
    val localPath: String? = null,
    val thumbnailPath: String? = null,
    val date: Long? = null,
    val isSeries: Boolean = false,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val year: Int? = null,
    val genres: String? = null,
    val description: String? = null,
) {
    /**
     * Returns a Telegram URI for this media item in the format:
     * tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>
     *
     * STUB: Returns placeholder URI for testing.
     */
    fun toTelegramUri(): String =
        if (fileId != null) {
            "tg://file/$fileId?chatId=$chatId&messageId=$messageId"
        } else {
            "tg://stub/$id?chatId=$chatId&messageId=$messageId"
        }

    /**
     * Checks if this media item is playable (has sufficient metadata).
     */
    fun isPlayable(): Boolean = fileId != null && mimeType != null
}
