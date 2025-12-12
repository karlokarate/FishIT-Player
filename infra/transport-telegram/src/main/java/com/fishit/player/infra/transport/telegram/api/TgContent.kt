package com.fishit.player.infra.transport.telegram.api

/**
 * Photo size descriptor for Telegram photos.
 *
 * Photos in Telegram are sent in multiple sizes (thumbnail, medium, large).
 * This represents one size variant.
 *
 * @property fileId TDLib file ID for this size
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
    val fileSize: Long
)

/**
 * Transport-layer content descriptor for Telegram message content.
 *
 * Sealed interface representing different media types that can be
 * contained in a Telegram message. No TDLib dependencies.
 *
 * **v2 Architecture:**
 * - Transport maps TDLib MessageContent to these DTOs
 * - Pipeline filters and classifies these into RawMediaMetadata
 * - No normalization happens here (that's pipeline's job)
 *
 * **Note on file IDs:**
 * - `fileId` is TDLib's internal ID, may become stale
 * - `remoteId` is stable and can be used to re-resolve files
 */
sealed interface TgContent {

    /**
     * Video content (MP4, MOV, etc.)
     *
     * @property fileId TDLib file ID
     * @property remoteId Stable remote file identifier
     * @property fileName Original file name
     * @property mimeType MIME type (e.g., "video/mp4")
     * @property duration Duration in seconds
     * @property width Video width in pixels
     * @property height Video height in pixels
     * @property fileSize File size in bytes
     * @property supportsStreaming Whether TDLib indicates streaming support
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
        val caption: String? = null
    ) : TgContent

    /**
     * Audio content (music files)
     *
     * @property fileId TDLib file ID
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
        val caption: String? = null
    ) : TgContent

    /**
     * Document/file content
     *
     * @property fileId TDLib file ID
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
        val caption: String? = null
    ) : TgContent

    /**
     * Photo content with multiple sizes.
     *
     * @property sizes Available photo sizes (sorted by size)
     * @property caption Message caption text
     */
    data class Photo(
        val sizes: List<TgPhotoSize>,
        val caption: String? = null
    ) : TgContent

    /**
     * Voice message content
     *
     * @property fileId TDLib file ID
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
        val caption: String? = null
    ) : TgContent

    /**
     * Video note (round video message)
     *
     * @property fileId TDLib file ID
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
        val fileSize: Long
    ) : TgContent

    /**
     * Animation (GIF/MP4 animation)
     *
     * @property fileId TDLib file ID
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
        val caption: String? = null
    ) : TgContent

    /**
     * Unknown or unsupported content type
     *
     * @property kind String describing the content type for debugging
     */
    data class Unknown(val kind: String) : TgContent
}
