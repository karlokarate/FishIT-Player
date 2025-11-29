package com.chris.m3usuite.telegram.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTO layer for Telegram export JSON files.
 *
 * This sealed hierarchy matches the JSON schema found in docs/telegram/exports
 * and is used for offline parsing as well as mapping from live TDLib messages.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md:
 * - remoteId and uniqueId are REQUIRED for stable file references
 * - fileId (integer TDLib local ID) is OPTIONAL and may become stale
 */

// =============================================================================
// Remote File Reference DTOs
// =============================================================================

/**
 * TDLib remote file reference with stable identifiers.
 */
@Serializable
data class ExportRemoteFile(
    val id: String? = null,
    val uniqueId: String? = null,
    val isUploadingActive: Boolean = false,
    val isUploadingCompleted: Boolean = false,
    val uploadedSize: Long = 0,
)

/**
 * TDLib local file reference.
 */
@Serializable
data class ExportLocalFile(
    val path: String = "",
    val canBeDownloaded: Boolean = false,
    val canBeDeleted: Boolean = false,
    val isDownloadingActive: Boolean = false,
    val isDownloadingCompleted: Boolean = false,
    val downloadOffset: Long = 0,
    val downloadedPrefixSize: Long = 0,
    val downloadedSize: Long = 0,
)

/**
 * Complete TDLib file reference with both local and remote data.
 */
@Serializable
data class ExportFile(
    val id: Int = 0,
    val size: Long = 0,
    val expectedSize: Long = 0,
    val local: ExportLocalFile = ExportLocalFile(),
    val remote: ExportRemoteFile = ExportRemoteFile(),
)

// =============================================================================
// Video Content DTOs
// =============================================================================

/**
 * Video thumbnail/minithumbnail structure.
 */
@Serializable
data class ExportThumbnail(
    val format: JsonElement? = null,
    val width: Int = 0,
    val height: Int = 0,
    val file: ExportFile = ExportFile(),
)

/**
 * Video file content from TDLib MessageVideo.
 */
@Serializable
data class ExportVideoContent(
    val duration: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val fileName: String = "",
    val mimeType: String = "",
    val supportsStreaming: Boolean = false,
    val thumbnail: ExportThumbnail? = null,
    val video: ExportFile = ExportFile(),
)

// =============================================================================
// Photo Content DTOs
// =============================================================================

/**
 * Single photo size with file reference.
 */
@Serializable
data class ExportPhotoSize(
    val type: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val photo: ExportFile = ExportFile(),
)

/**
 * Photo content from TDLib MessagePhoto.
 */
@Serializable
data class ExportPhotoContent(
    val id: Long = 0,
    val addedDate: Long = 0,
    val minithumbnail: ExportMinithumbnail? = null,
    val sizes: List<ExportPhotoSize> = emptyList(),
)

/**
 * Minithumbnail (low-res preview).
 */
@Serializable
data class ExportMinithumbnail(
    val width: Int = 0,
    val height: Int = 0,
    val data: List<Int> = emptyList(),
)

// =============================================================================
// Document Content DTOs (for RAR, audiobooks, etc.)
// =============================================================================

/**
 * Document file content from TDLib MessageDocument.
 */
@Serializable
data class ExportDocumentContent(
    val fileName: String = "",
    val mimeType: String = "",
    val thumbnail: ExportThumbnail? = null,
    val document: ExportFile = ExportFile(),
)

// =============================================================================
// Audio Content DTOs
// =============================================================================

/**
 * Audio file content from TDLib MessageAudio.
 */
@Serializable
data class ExportAudioContent(
    val duration: Int = 0,
    val title: String = "",
    val performer: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val albumCoverThumbnail: ExportThumbnail? = null,
    val audio: ExportFile = ExportFile(),
)

// =============================================================================
// Text/Caption DTOs
// =============================================================================

/**
 * Text entity (URLs, mentions, etc.).
 */
@Serializable
data class ExportTextEntity(
    val offset: Int = 0,
    val length: Int = 0,
    val type: ExportTextEntityType? = null,
)

/**
 * Text entity type.
 */
@Serializable
data class ExportTextEntityType(
    @SerialName("url")
    val url: String? = null,
)

/**
 * Formatted text with entities.
 */
@Serializable
data class ExportFormattedText(
    val text: String = "",
    val entities: List<ExportTextEntity> = emptyList(),
)

// =============================================================================
// Message Content Container
// =============================================================================

/**
 * Message content container that can hold various content types.
 */
@Serializable
data class ExportMessageContent(
    val video: ExportVideoContent? = null,
    val photo: ExportPhotoContent? = null,
    val document: ExportDocumentContent? = null,
    val audio: ExportAudioContent? = null,
    val caption: ExportFormattedText? = null,
    val type: String? = null,
    val sizes: List<ExportPhotoSize>? = null,
)

// =============================================================================
// Message Sender
// =============================================================================

/**
 * Message sender information.
 */
@Serializable
data class ExportMessageSender(
    val chatId: Long? = null,
    val userId: Long? = null,
)

// =============================================================================
// ExportMessage Sealed Hierarchy
// =============================================================================

/**
 * Base sealed interface for all exported message types.
 */
sealed interface ExportMessage {
    val id: Long
    val chatId: Long
    val dateEpochSeconds: Long
    val dateIso: String
}

/**
 * Video message (MessageVideo).
 */
data class ExportVideo(
    override val id: Long,
    override val chatId: Long,
    override val dateEpochSeconds: Long,
    override val dateIso: String,
    val video: ExportVideoContent,
    val caption: String?,
    val captionEntities: List<ExportTextEntity> = emptyList(),
) : ExportMessage

/**
 * Photo message (MessagePhoto).
 */
data class ExportPhoto(
    override val id: Long,
    override val chatId: Long,
    override val dateEpochSeconds: Long,
    override val dateIso: String,
    val sizes: List<ExportPhotoSize>,
    val caption: String?,
) : ExportMessage

/**
 * Text message (MessageText).
 */
data class ExportText(
    override val id: Long,
    override val chatId: Long,
    override val dateEpochSeconds: Long,
    override val dateIso: String,
    val text: String,
    val entities: List<ExportTextEntity> = emptyList(),
    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val lengthMinutes: Int? = null,
    val fsk: Int? = null,
    val productionCountry: String? = null,
    val collection: String? = null,
    val director: String? = null,
    val tmdbRating: Double? = null,
    val genres: List<String> = emptyList(),
    val tmdbUrl: String? = null,
) : ExportMessage

/**
 * Document message (MessageDocument).
 */
data class ExportDocument(
    override val id: Long,
    override val chatId: Long,
    override val dateEpochSeconds: Long,
    override val dateIso: String,
    val document: ExportDocumentContent,
    val caption: String?,
) : ExportMessage

/**
 * Audio message (MessageAudio).
 */
data class ExportAudio(
    override val id: Long,
    override val chatId: Long,
    override val dateEpochSeconds: Long,
    override val dateIso: String,
    val audio: ExportAudioContent,
    val caption: String?,
) : ExportMessage

/**
 * Other/unknown message type.
 */
data class ExportOtherRaw(
    override val id: Long,
    override val chatId: Long,
    override val dateEpochSeconds: Long,
    override val dateIso: String,
    val rawJson: String,
    val messageType: String,
) : ExportMessage

// =============================================================================
// Chat Export Container
// =============================================================================

/**
 * Complete chat export containing metadata and messages.
 */
@Serializable
data class ChatExport(
    val chatId: Long,
    val title: String,
    val exportedAt: String = "",
    val count: Int = 0,
    val messages: List<RawExportMessage> = emptyList(),
)

/**
 * Raw message as serialized from JSON.
 */
@Serializable
data class RawExportMessage(
    val id: Long,
    val chatId: Long,
    val date: Long = 0,
    val dateIso: String = "",
    val senderId: ExportMessageSender? = null,
    val content: ExportMessageContent? = null,
    val text: String? = null,
    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val lengthMinutes: Int? = null,
    val fsk: Int? = null,
    val productionCountry: String? = null,
    val collection: String? = null,
    val director: String? = null,
    val tmdbRating: Double? = null,
    val genres: List<String> = emptyList(),
    val tmdbUrl: String? = null,
    val entities: List<ExportTextEntity> = emptyList(),
)

/**
 * Convert raw message to typed ExportMessage.
 */
fun RawExportMessage.toExportMessage(): ExportMessage {
    val dateIsoValue =
        dateIso.ifEmpty {
            java.time.Instant
                .ofEpochSecond(date)
                .toString()
        }

    // Check for video content
    content?.video?.let { video ->
        return ExportVideo(
            id = id,
            chatId = chatId,
            dateEpochSeconds = date,
            dateIso = dateIsoValue,
            video = video,
            caption = content.caption?.text,
            captionEntities = content.caption?.entities ?: emptyList(),
        )
    }

    // Check for photo content (either nested or at content level)
    content?.photo?.let { photo ->
        return ExportPhoto(
            id = id,
            chatId = chatId,
            dateEpochSeconds = date,
            dateIso = dateIsoValue,
            sizes = photo.sizes,
            caption = content.caption?.text,
        )
    }

    // Photo sizes at content level
    content?.sizes?.let { sizes ->
        if (sizes.isNotEmpty()) {
            return ExportPhoto(
                id = id,
                chatId = chatId,
                dateEpochSeconds = date,
                dateIso = dateIsoValue,
                sizes = sizes,
                caption = content.caption?.text,
            )
        }
    }

    // Check for document content
    content?.document?.let { doc ->
        return ExportDocument(
            id = id,
            chatId = chatId,
            dateEpochSeconds = date,
            dateIso = dateIsoValue,
            document = doc,
            caption = content.caption?.text,
        )
    }

    // Check for audio content
    content?.audio?.let { audio ->
        return ExportAudio(
            id = id,
            chatId = chatId,
            dateEpochSeconds = date,
            dateIso = dateIsoValue,
            audio = audio,
            caption = content.caption?.text,
        )
    }

    // Check for text message (text field at top level)
    text?.let { textContent ->
        return ExportText(
            id = id,
            chatId = chatId,
            dateEpochSeconds = date,
            dateIso = dateIsoValue,
            text = textContent,
            entities = entities,
            title = title,
            originalTitle = originalTitle,
            year = year,
            lengthMinutes = lengthMinutes,
            fsk = fsk,
            productionCountry = productionCountry,
            collection = collection,
            director = director,
            tmdbRating = tmdbRating,
            genres = genres,
            tmdbUrl = tmdbUrl,
        )
    }

    // Fallback to OtherRaw
    return ExportOtherRaw(
        id = id,
        chatId = chatId,
        dateEpochSeconds = date,
        dateIso = dateIsoValue,
        rawJson = "",
        messageType = "unknown",
    )
}
