package com.fishit.player.pipeline.telegram.model

/**
 * Domain model representing a Telegram media item for FishIT Player v2.
 *
 * Updated based on analysis of real Telegram export JSONs from docs/telegram/exports/exports/.
 * Represents playable media content from Telegram (video, document, audio, photo).
 *
 * Maps to ObxTelegramMessage but provides a cleaner domain interface.
 *
 * **CONTRACT COMPLIANCE (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - All fields are RAW as extracted from Telegram messages
 * - Scene-style filenames preserved exactly (e.g., "Movie.2000.1080p.BluRay.x264-GROUP.mkv")
 * - NO title cleaning, normalization, or heuristics applied
 * - All processing delegated to :core:metadata-normalizer
 *
 * @property id Unique identifier for this media item
 * @property chatId Telegram chat ID where the media is located
 * @property messageId Telegram message ID
 * @property mediaAlbumId Media album ID for grouped messages (optional)
 * @property mediaType Type of media content (VIDEO, DOCUMENT, AUDIO, PHOTO, OTHER)
 * @property fileId Telegram file ID (nullable for stub)
 * @property fileUniqueId Telegram unique file ID (stable across sessions)
 * @property remoteId Stable TDLib remote ID (session-independent)
 * @property title Display title (from caption or parsed metadata)
 * @property fileName Original file name (RAW, no cleaning)
 * @property caption Message caption text (RAW)
 * @property mimeType MIME type of the media file
 * @property sizeBytes File size in bytes
 * @property durationSecs Duration in seconds (for video/audio)
 * @property width Video/photo width in pixels
 * @property height Video/photo height in pixels
 * @property supportsStreaming Whether the file supports streaming playback
 * @property localPath Local file path if downloaded (nullable)
 * @property thumbnailFileId Telegram file ID for thumbnail
 * @property thumbnailUniqueId Telegram unique file ID for thumbnail
 * @property thumbnailWidth Thumbnail width in pixels
 * @property thumbnailHeight Thumbnail height in pixels
 * @property thumbnailPath Local thumbnail path (nullable)
 * @property photoSizes List of photo sizes (for photo messages)
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
        val mediaAlbumId: Long? = null,
        val mediaType: TelegramMediaType = TelegramMediaType.OTHER,
        val fileId: Int? = null,
        val fileUniqueId: String? = null,
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
        val thumbnailFileId: String? = null,
        val thumbnailUniqueId: String? = null,
        val thumbnailWidth: Int? = null,
        val thumbnailHeight: Int? = null,
        val thumbnailPath: String? = null,
        val photoSizes: List<TelegramPhotoSize> = emptyList(),
        // === Minithumbnail (inline JPEG bytes for instant blur placeholder) ===
        val minithumbnailBytes: ByteArray? = null,
        val minithumbnailWidth: Int? = null,
        val minithumbnailHeight: Int? = null,
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

    /** Checks if this media item is playable (has sufficient metadata). */
    fun isPlayable(): Boolean = fileId != null && mimeType != null

    /** Checks if this media item has a minithumbnail for instant preview. */
    fun hasMinithumbnail(): Boolean = minithumbnailBytes != null && minithumbnailBytes.isNotEmpty()

    // ByteArray requires custom equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TelegramMediaItem) return false
        return id == other.id &&
                chatId == other.chatId &&
                messageId == other.messageId &&
                mediaAlbumId == other.mediaAlbumId &&
                mediaType == other.mediaType &&
                fileId == other.fileId &&
                fileUniqueId == other.fileUniqueId &&
                remoteId == other.remoteId &&
                title == other.title &&
                fileName == other.fileName &&
                caption == other.caption &&
                mimeType == other.mimeType &&
                sizeBytes == other.sizeBytes &&
                durationSecs == other.durationSecs &&
                width == other.width &&
                height == other.height &&
                supportsStreaming == other.supportsStreaming &&
                localPath == other.localPath &&
                thumbnailFileId == other.thumbnailFileId &&
                thumbnailUniqueId == other.thumbnailUniqueId &&
                thumbnailWidth == other.thumbnailWidth &&
                thumbnailHeight == other.thumbnailHeight &&
                thumbnailPath == other.thumbnailPath &&
                photoSizes == other.photoSizes &&
                minithumbnailBytes.contentEquals(other.minithumbnailBytes) &&
                minithumbnailWidth == other.minithumbnailWidth &&
                minithumbnailHeight == other.minithumbnailHeight &&
                date == other.date &&
                isSeries == other.isSeries &&
                seriesName == other.seriesName &&
                seasonNumber == other.seasonNumber &&
                episodeNumber == other.episodeNumber &&
                episodeTitle == other.episodeTitle &&
                year == other.year &&
                genres == other.genres &&
                description == other.description
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + chatId.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + (minithumbnailBytes?.contentHashCode() ?: 0)
        return result
    }
}
