package com.chris.m3usuite.telegram.parser

import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import java.time.Instant

/**
 * Centralized factory for creating ExportMessage instances from various sources.
 *
 * This factory ensures that both:
 * - JSON-based pipeline (CLI exports) and
 * - TDLib-based runtime pipeline
 *
 * use **identical logic** for building ExportMessage instances, including:
 * - remoteId/uniqueId extraction
 * - fileId handling
 * - thumbnail/poster/backdrop resolution
 *
 * Per the Pipeline Equivalence Contract:
 * - JSON path: ExportFlatFile → ExportFile → ExportMessage
 * - TDLib path: TDLib DTO → ExportFile → ExportMessage
 * - Both must produce identical ExportMessage for identical logical content.
 */
object ExportMessageFactory {
    // ==========================================================================
    // TDLib DTO → ExportFile helpers
    // ==========================================================================

    /**
     * Build an ExportFile from TDLib file components.
     * Uses nested format (remote.id, remote.uniqueId).
     */
    fun buildExportFile(file: dev.g000sha256.tdl.dto.File?): ExportFile =
        ExportFile(
            id = file?.id ?: 0,
            size = file?.size ?: 0,
            expectedSize = file?.expectedSize ?: 0,
            local = buildExportLocalFile(file?.local),
            remote = buildExportRemoteFile(file?.remote),
        )

    /**
     * Build an ExportFile from flat JSON format.
     * Uses flat format (flatRemoteId, flatUniqueId).
     */
    fun buildExportFileFromFlat(
        id: Int,
        size: Long,
        remoteId: String?,
        uniqueId: String?,
    ): ExportFile =
        ExportFile(
            id = id,
            size = size,
            flatRemoteId = remoteId,
            flatUniqueId = uniqueId,
        )

    /**
     * Build an ExportLocalFile from TDLib local file info.
     */
    fun buildExportLocalFile(local: dev.g000sha256.tdl.dto.LocalFile?): ExportLocalFile =
        ExportLocalFile(
            path = local?.path ?: "",
            canBeDownloaded = local?.canBeDownloaded ?: false,
            canBeDeleted = local?.canBeDeleted ?: false,
            isDownloadingActive = local?.isDownloadingActive ?: false,
            isDownloadingCompleted = local?.isDownloadingCompleted ?: false,
            downloadOffset = local?.downloadOffset ?: 0,
            downloadedPrefixSize = local?.downloadedPrefixSize ?: 0,
            downloadedSize = local?.downloadedSize ?: 0,
        )

    /**
     * Build an ExportRemoteFile from TDLib remote file info.
     */
    fun buildExportRemoteFile(remote: dev.g000sha256.tdl.dto.RemoteFile?): ExportRemoteFile =
        ExportRemoteFile(
            id = remote?.id,
            uniqueId = remote?.uniqueId,
            isUploadingActive = remote?.isUploadingActive ?: false,
            isUploadingCompleted = remote?.isUploadingCompleted ?: false,
            uploadedSize = remote?.uploadedSize ?: 0,
        )

    /**
     * Build an ExportThumbnail from TDLib thumbnail info.
     */
    fun buildExportThumbnail(thumbnail: dev.g000sha256.tdl.dto.Thumbnail?): ExportThumbnail? {
        if (thumbnail == null) return null
        return ExportThumbnail(
            width = thumbnail.width,
            height = thumbnail.height,
            file = buildExportFile(thumbnail.file),
        )
    }

    // ==========================================================================
    // TDLib Message → ExportMessage mapping
    // ==========================================================================

    /**
     * Convert a TDLib Message to an ExportMessage.
     * Delegates to type-specific mappers.
     *
     * @param message TDLib Message object
     * @return ExportMessage or null if the message type is not supported or lacks required fields
     */
    fun fromTdlMessage(message: Message): ExportMessage? {
        val dateEpoch = message.date.toLong()
        val dateIso =
            try {
                Instant.ofEpochSecond(dateEpoch).toString()
            } catch (e: Exception) {
                ""
            }

        return when (val content = message.content) {
            is MessageVideo -> fromTdlVideo(message, content, dateEpoch, dateIso)
            is MessagePhoto -> fromTdlPhoto(message, content, dateEpoch, dateIso)
            is MessageText -> fromTdlText(message, content, dateEpoch, dateIso)
            is MessageDocument -> fromTdlDocument(message, content, dateEpoch, dateIso)
            is MessageAudio -> fromTdlAudio(message, content, dateEpoch, dateIso)
            else -> fromTdlOther(message, dateEpoch, dateIso)
        }
    }

    /**
     * Build ExportVideo from TDLib MessageVideo.
     */
    fun fromTdlVideo(
        message: Message,
        content: MessageVideo,
        dateEpoch: Long,
        dateIso: String,
    ): ExportVideo? {
        val video = content.video
        val videoFile = video.video

        // Validate required fields
        val remoteId = videoFile?.remote?.id
        val uniqueId = videoFile?.remote?.uniqueId

        if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
            return null
        }

        // Build video content using helper functions
        val videoContent =
            ExportVideoContent(
                duration = video.duration,
                width = video.width,
                height = video.height,
                fileName = video.fileName ?: "",
                mimeType = video.mimeType ?: "",
                supportsStreaming = video.supportsStreaming,
                thumbnail = buildExportThumbnail(video.thumbnail),
                video = buildExportFile(videoFile),
            )

        // Extract caption and entities
        val caption = content.caption?.text
        val captionEntities = mapTextEntities(content.caption?.entities)

        return ExportVideo(
            id = message.id,
            chatId = message.chatId,
            dateEpochSeconds = dateEpoch,
            dateIso = dateIso,
            video = videoContent,
            caption = caption,
            captionEntities = captionEntities,
        )
    }

    /**
     * Build ExportPhoto from TDLib MessagePhoto.
     */
    fun fromTdlPhoto(
        message: Message,
        content: MessagePhoto,
        dateEpoch: Long,
        dateIso: String,
    ): ExportPhoto? {
        val photo = content.photo
        val sizes =
            photo.sizes?.filterNotNull()?.mapNotNull { photoSize ->
                val photoFile = photoSize.photo
                val remoteId = photoFile?.remote?.id
                val uniqueId = photoFile?.remote?.uniqueId

                // Skip sizes without valid remote IDs
                if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
                    return@mapNotNull null
                }

                ExportPhotoSize(
                    type = photoSize.type ?: "",
                    width = photoSize.width,
                    height = photoSize.height,
                    photo = buildExportFile(photoFile),
                )
            } ?: emptyList()

        // Must have at least one valid size
        if (sizes.isEmpty()) {
            return null
        }

        val caption = content.caption?.text

        return ExportPhoto(
            id = message.id,
            chatId = message.chatId,
            dateEpochSeconds = dateEpoch,
            dateIso = dateIso,
            sizes = sizes,
            caption = caption,
        )
    }

    /**
     * Build ExportText from TDLib MessageText.
     */
    fun fromTdlText(
        message: Message,
        content: MessageText,
        dateEpoch: Long,
        dateIso: String,
    ): ExportText {
        val rawText = content.text?.text ?: ""
        val entities = mapTextEntities(content.text?.entities)

        // Extract metadata from text content using the same logic as CLI
        val metadata = extractTextMetadata(rawText, entities)

        return ExportText(
            id = message.id,
            chatId = message.chatId,
            dateEpochSeconds = dateEpoch,
            dateIso = dateIso,
            text = rawText,
            entities = entities,
            title = metadata.title,
            originalTitle = metadata.originalTitle,
            year = metadata.year,
            lengthMinutes = metadata.lengthMinutes,
            fsk = metadata.fsk,
            productionCountry = metadata.productionCountry,
            collection = metadata.collection,
            director = metadata.director,
            tmdbRating = metadata.tmdbRating,
            genres = metadata.genres,
            tmdbUrl = metadata.tmdbUrl,
        )
    }

    /**
     * Build ExportDocument from TDLib MessageDocument.
     */
    fun fromTdlDocument(
        message: Message,
        content: MessageDocument,
        dateEpoch: Long,
        dateIso: String,
    ): ExportDocument? {
        val doc = content.document
        val docFile = doc.document

        // Validate required fields
        val remoteId = docFile?.remote?.id
        val uniqueId = docFile?.remote?.uniqueId

        if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
            return null
        }

        val docContent =
            ExportDocumentContent(
                fileName = doc.fileName ?: "",
                mimeType = doc.mimeType ?: "",
                thumbnail = buildExportThumbnail(doc.thumbnail),
                document = buildExportFile(docFile),
            )

        val caption = content.caption?.text

        return ExportDocument(
            id = message.id,
            chatId = message.chatId,
            dateEpochSeconds = dateEpoch,
            dateIso = dateIso,
            document = docContent,
            caption = caption,
        )
    }

    /**
     * Build ExportAudio from TDLib MessageAudio.
     */
    fun fromTdlAudio(
        message: Message,
        content: MessageAudio,
        dateEpoch: Long,
        dateIso: String,
    ): ExportAudio? {
        val audio = content.audio
        val audioFile = audio.audio

        // Validate required fields
        val remoteId = audioFile?.remote?.id
        val uniqueId = audioFile?.remote?.uniqueId

        if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
            return null
        }

        val audioContent =
            ExportAudioContent(
                duration = audio.duration,
                title = audio.title ?: "",
                performer = audio.performer ?: "",
                fileName = audio.fileName ?: "",
                mimeType = audio.mimeType ?: "",
                albumCoverThumbnail = buildExportThumbnail(audio.albumCoverThumbnail),
                audio = buildExportFile(audioFile),
            )

        val caption = content.caption?.text

        return ExportAudio(
            id = message.id,
            chatId = message.chatId,
            dateEpochSeconds = dateEpoch,
            dateIso = dateIso,
            audio = audioContent,
            caption = caption,
        )
    }

    /**
     * Build ExportOtherRaw for unsupported message types.
     */
    fun fromTdlOther(
        message: Message,
        dateEpoch: Long,
        dateIso: String,
    ): ExportOtherRaw {
        val messageType = message.content::class.simpleName ?: "Unknown"
        return ExportOtherRaw(
            id = message.id,
            chatId = message.chatId,
            dateEpochSeconds = dateEpoch,
            dateIso = dateIso,
            rawJson = "", // Not serializing full message to avoid overhead
            messageType = messageType,
        )
    }

    // ==========================================================================
    // Shared helpers
    // ==========================================================================

    /**
     * Map TDLib text entities to ExportTextEntity.
     */
    fun mapTextEntities(entities: Array<dev.g000sha256.tdl.dto.TextEntity>?): List<ExportTextEntity> =
        entities?.mapNotNull { entity ->
            val url =
                when (val type = entity.type) {
                    is TextEntityTypeTextUrl -> type.url
                    else -> null
                }
            ExportTextEntity(
                offset = entity.offset,
                length = entity.length,
                type =
                    if (url != null) {
                        ExportTextEntityType(url = url)
                    } else {
                        null
                    },
            )
        } ?: emptyList()

    /**
     * Extract metadata from text message content.
     * Matches the CLI reference parsing logic.
     */
    fun extractTextMetadata(
        rawText: String,
        entities: List<ExportTextEntity>,
    ): TextMessageMetadata {
        val lines = rawText.split("\n")

        fun findValue(prefix: String): String? =
            lines
                .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                ?.substringAfter(prefix)
                ?.trim()
                ?.ifEmpty { null }

        val title = findValue("Titel:")
        val year = findValue("Erscheinungsjahr:")?.toIntOrNull()
        val lengthMinutes =
            findValue("Länge:")
                ?.replace("Minuten", "", ignoreCase = true)
                ?.trim()
                ?.toIntOrNull()
        val fsk = findValue("FSK:")?.toIntOrNull()
        val collection = findValue("Filmreihe:")
        val originalTitle = findValue("Originaltitel:")
        val productionCountry = findValue("Produktionsland:")
        val director = findValue("Regie:")
        val tmdbRating = findValue("TMDbRating:")?.toDoubleOrNull()
        val genres =
            findValue("Genres:")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

        // Extract TMDb URL from entities first, then fallback to regex
        val tmdbUrl =
            entities
                .mapNotNull { it.type?.url }
                .firstOrNull { it.contains("/movie/", ignoreCase = true) || it.contains("/tv/", ignoreCase = true) }
                ?: TMDB_URL_REGEX.find(rawText)?.value

        return TextMessageMetadata(
            title = title,
            originalTitle = originalTitle,
            year = year,
            lengthMinutes = lengthMinutes,
            fsk = fsk,
            collection = collection,
            productionCountry = productionCountry,
            director = director,
            tmdbRating = tmdbRating,
            genres = genres,
            tmdbUrl = tmdbUrl,
        )
    }

    /**
     * Internal data class for parsed text metadata.
     */
    data class TextMessageMetadata(
        val title: String?,
        val originalTitle: String?,
        val year: Int?,
        val lengthMinutes: Int?,
        val fsk: Int?,
        val collection: String?,
        val productionCountry: String?,
        val director: String?,
        val tmdbRating: Double?,
        val genres: List<String>,
        val tmdbUrl: String?,
    )

    /**
     * Regex to extract TMDb URLs from text.
     */
    private val TMDB_URL_REGEX =
        Regex("""https?://(?:www\.)?themoviedb\.org/(movie|tv)/\d+[^\s]*""", RegexOption.IGNORE_CASE)
}
