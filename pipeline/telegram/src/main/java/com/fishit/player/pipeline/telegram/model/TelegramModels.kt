package com.fishit.player.pipeline.telegram.model

/**
 * Represents a Telegram chat where media content is stored.
 *
 * Maps conceptually from TDLib's Chat object but simplified for Phase 2.
 * In Phase 3+, this will be populated from actual TDLib chat data.
 *
 * @property chatId Unique identifier for the chat (from TDLib)
 * @property title Chat title/name
 * @property type Chat type (e.g., "private", "group", "supergroup", "channel")
 * @property username Optional username for public chats/channels
 * @property photoUrl Optional chat photo URL
 */
data class TelegramChat(
    val chatId: Long,
    val title: String,
    val type: String = "unknown",
    val username: String? = null,
    val photoUrl: String? = null,
)

/**
 * Represents a Telegram message containing media content.
 *
 * Maps conceptually from TDLib's Message object. In Phase 2, this is a stub.
 * Real implementation in Phase 3+ will populate from TDLib API.
 *
 * @property messageId Unique identifier for the message within the chat
 * @property chatId Chat ID where this message belongs
 * @property date Message timestamp (Unix epoch seconds)
 * @property caption Optional message caption/text
 * @property hasMedia Whether this message contains media content
 */
data class TelegramMessage(
    val messageId: Long,
    val chatId: Long,
    val date: Long,
    val caption: String? = null,
    val hasMedia: Boolean = false,
)

/**
 * Represents a media item from Telegram that can be played.
 *
 * This model maps from v1's `ObxTelegramMessage` entity structure
 * (app/src/main/java/com/chris/m3usuite/data/obx/ObxEntities.kt)
 * and will be used for playback integration.
 *
 * In Phase 2, this is a domain model only. Phase 3+ will:
 * - Populate from TDLib file downloads
 * - Support windowed zero-copy streaming
 * - Handle movie/series metadata extraction
 *
 * @property chatId Chat ID where the media is located
 * @property messageId Message ID containing the media
 * @property fileId TDLib file ID (nullable in stub phase)
 * @property fileUniqueId TDLib stable unique ID across sessions
 * @property remoteId Stable TDLib remote_id (session-independent)
 * @property supportsStreaming Whether the file supports streaming playback
 * @property caption Message caption (often contains movie/series info)
 * @property captionLower Lowercase caption for search/filtering
 * @property date Message date (Unix epoch seconds)
 * @property localPath Local file path if downloaded
 * @property thumbFileId Thumbnail file ID
 * @property thumbLocalPath Local thumbnail path if downloaded
 * @property fileName Original file name
 * @property durationSecs Video duration in seconds
 * @property mimeType MIME type (e.g., "video/mp4")
 * @property sizeBytes File size in bytes
 * @property width Video width in pixels
 * @property height Video height in pixels
 * @property language Content language code
 * @property title Movie/episode title (from structured metadata)
 * @property year Release year
 * @property genres Comma-separated genre list
 * @property fsk Age rating (FSK/MPAA equivalent)
 * @property description Content description/plot
 * @property posterFileId Poster image file ID
 * @property posterLocalPath Local poster path if downloaded
 * @property isSeries Whether this is part of a series
 * @property seriesName Series name
 * @property seriesNameNormalized Normalized series name for grouping
 * @property seasonNumber Season number (for series)
 * @property episodeNumber Episode number (for series)
 * @property episodeTitle Episode title
 */
data class TelegramMediaItem(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int? = null,
    val fileUniqueId: String? = null,
    val remoteId: String? = null,
    val supportsStreaming: Boolean? = null,
    val caption: String? = null,
    val captionLower: String? = null,
    val date: Long? = null,
    val localPath: String? = null,
    val thumbFileId: Int? = null,
    val thumbLocalPath: String? = null,
    val fileName: String? = null,
    // Enriched metadata
    val durationSecs: Int? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val language: String? = null,
    // Movie metadata
    val title: String? = null,
    val year: Int? = null,
    val genres: String? = null,
    val fsk: Int? = null,
    val description: String? = null,
    val posterFileId: Int? = null,
    val posterLocalPath: String? = null,
    // Series metadata
    val isSeries: Boolean = false,
    val seriesName: String? = null,
    val seriesNameNormalized: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
)
