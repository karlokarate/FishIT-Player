package com.fishit.player.infra.transport.telegram.api

/**
 * Photo size descriptor for Telegram photos.
 *
 * Photos in Telegram are sent in multiple sizes (thumbnail, medium, large).
 * This represents one size variant.
 *
 * @property fileId Telegram API file ID for this size
 * @property remoteId Stable remote file identifier
 * @property width Width in pixels
 * @property height Height in pixels
 * @property fileSize File size in bytes
 */
data class TgPhotoSize(
    val fileId: Int,
    val remoteId: String,
    val width: Int,
    val height: Int,
    val fileSize: Long,
)

/**
 * Thumbnail descriptor for media content.
 *
 * @property fileId Telegram API file ID
 * @property remoteId Stable remote file identifier
 * @property width Width in pixels
 * @property height Height in pixels
 * @property fileSize File size in bytes
 */
data class TgThumbnail(
    val fileId: Int,
    val remoteId: String,
    val width: Int,
    val height: Int,
    val fileSize: Long,
)

/**
 * Inline minithumbnail (~40px JPEG) for instant placeholders.
 *
 * @property width Width in pixels
 * @property height Height in pixels
 * @property data Base64-decoded JPEG data
 */
data class TgMinithumbnail(
    val width: Int,
    val height: Int,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TgMinithumbnail
        return width == other.width && height == other.height && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Transport-layer content descriptor for Telegram message content.
 *
 * Sealed interface representing different media types that can be
 * contained in a Telegram message. No Telegram API dependencies.
 *
 * **v2 Architecture:**
 * - Transport maps Telegram API MessageContent to these DTOs
 * - Pipeline filters and classifies these into RawMediaMetadata
 * - No normalization happens here (that's pipeline's job)
 *
 * **Note on file IDs:**
 * - `fileId` is Telegram API's internal ID, may become stale
 * - `remoteId` is stable and can be used to re-resolve files
 */
sealed interface TgContent {
    /**
     * Video content (MP4, MOV, etc.)
     *
     * @property fileId Telegram API file ID
     * @property remoteId Stable remote file identifier
     * @property fileName Original file name
     * @property mimeType MIME type (e.g., "video/mp4")
     * @property duration Duration in seconds
     * @property width Video width in pixels
     * @property height Video height in pixels
     * @property fileSize File size in bytes
     * @property supportsStreaming Whether Telegram API indicates streaming support
     * @property caption Message caption text
     */
    data class Video(
        val fileId: Int,
        val remoteId: String,
        val fileName: String?,
        val mimeType: String?,
        val duration: Int,
        val width: Int,
        val height: Int,
        val fileSize: Long,
        val supportsStreaming: Boolean = false,
        val caption: String? = null,
        val thumbnail: TgThumbnail? = null,
        val minithumbnail: TgMinithumbnail? = null,
    ) : TgContent

    /**
     * Audio content (music files)
     *
     * @property fileId Telegram API file ID
     * @property remoteId Stable remote file identifier
     * @property fileName Original file name
     * @property mimeType MIME type (e.g., "audio/mpeg")
     * @property duration Duration in seconds
     * @property title Audio title (ID3 tag)
     * @property performer Performer/artist name
     * @property fileSize File size in bytes
     * @property caption Message caption text
     */
    data class Audio(
        val fileId: Int,
        val remoteId: String,
        val fileName: String?,
        val mimeType: String?,
        val duration: Int,
        val title: String?,
        val performer: String?,
        val fileSize: Long,
        val caption: String? = null,
        val albumCoverThumbnail: TgThumbnail? = null,
        val albumCoverMinithumbnail: TgMinithumbnail? = null,
    ) : TgContent

    /**
     * Document/file content
     *
     * @property fileId Telegram API file ID
     * @property remoteId Stable remote file identifier
     * @property fileName Original file name
     * @property mimeType MIME type
     * @property fileSize File size in bytes
     * @property caption Message caption text
     */
    data class Document(
        val fileId: Int,
        val remoteId: String,
        val fileName: String?,
        val mimeType: String?,
        val fileSize: Long,
        val caption: String? = null,
        val thumbnail: TgThumbnail? = null,
        val minithumbnail: TgMinithumbnail? = null,
    ) : TgContent

    /**
     * Photo content with multiple sizes.
     *
     * @property sizes Available photo sizes (sorted by size)
     * @property caption Message caption text
     */
    data class Photo(
        val sizes: List<TgPhotoSize>,
        val caption: String? = null,
        val minithumbnail: TgMinithumbnail? = null,
    ) : TgContent

    /**
     * Voice message content
     *
     * @property fileId Telegram API file ID
     * @property remoteId Stable remote file identifier
     * @property duration Duration in seconds
     * @property mimeType MIME type
     * @property fileSize File size in bytes
     * @property caption Message caption text
     */
    data class VoiceNote(
        val fileId: Int,
        val remoteId: String,
        val duration: Int,
        val mimeType: String?,
        val fileSize: Long,
        val caption: String? = null,
    ) : TgContent

    /**
     * Video note (round video message)
     *
     * @property fileId Telegram API file ID
     * @property remoteId Stable remote file identifier
     * @property duration Duration in seconds
     * @property length Video dimension (square)
     * @property fileSize File size in bytes
     */
    data class VideoNote(
        val fileId: Int,
        val remoteId: String,
        val duration: Int,
        val length: Int,
        val fileSize: Long,
        val thumbnail: TgThumbnail? = null,
        val minithumbnail: TgMinithumbnail? = null,
    ) : TgContent

    /**
     * Animation (GIF/MP4 animation)
     *
     * @property fileId Telegram API file ID
     * @property remoteId Stable remote file identifier
     * @property fileName Original file name
     * @property mimeType MIME type
     * @property duration Duration in seconds
     * @property width Animation width
     * @property height Animation height
     * @property fileSize File size in bytes
     * @property caption Message caption text
     */
    data class Animation(
        val fileId: Int,
        val remoteId: String,
        val fileName: String?,
        val mimeType: String?,
        val duration: Int,
        val width: Int,
        val height: Int,
        val fileSize: Long,
        val caption: String? = null,
        val thumbnail: TgThumbnail? = null,
        val minithumbnail: TgMinithumbnail? = null,
    ) : TgContent

    /**
     * Text message content.
     *
     * Used for TEXT messages in structured bundles that contain metadata.
     *
     * @property text The message text content
     */
    data class Text(
        val text: String,
    ) : TgContent

    /**
     * Unknown or unsupported content type
     *
     * @property kind String describing the content type for debugging
     */
    data class Unknown(
        val kind: String,
    ) : TgContent
}
