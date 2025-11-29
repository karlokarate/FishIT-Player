package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import java.time.Instant

/**
 * Maps TDLib DTOs (via tdlib-coroutines) to ExportMessage domain types.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Phase C.1:
 * - Introduces mappings from TDLib DTOs to ExportMessage
 * - Must handle: Video, Photo, Text, Document, Audio messages
 * - Always extracts remoteId (required), uniqueId (required), fileId (optional)
 * - Keeps schema 1:1 aligned with CLI/fixture JSON
 *
 * Usage:
 * ```kotlin
 * val tdlMessage: Message = ...
 * val exportMessage: ExportMessage? = TdlMessageMapper.toExportMessage(tdlMessage)
 * ```
 */
object TdlMessageMapper {
    private const val TAG = "TdlMessageMapper"

    /**
     * Convert a TDLib Message to an ExportMessage.
     *
     * @param message TDLib Message object
     * @return ExportMessage or null if the message type is not supported or lacks required fields
     */
    fun toExportMessage(message: Message): ExportMessage? {
        val dateEpoch = message.date.toLong()
        val dateIso =
            try {
                Instant.ofEpochSecond(dateEpoch).toString()
            } catch (e: Exception) {
                ""
            }

        return when (val content = message.content) {
            is MessageVideo -> mapVideo(message, content, dateEpoch, dateIso)
            is MessagePhoto -> mapPhoto(message, content, dateEpoch, dateIso)
            is MessageText -> mapText(message, content, dateEpoch, dateIso)
            is MessageDocument -> mapDocument(message, content, dateEpoch, dateIso)
            is MessageAudio -> mapAudio(message, content, dateEpoch, dateIso)
            else -> mapOther(message, dateEpoch, dateIso)
        }
    }

    /**
     * Convert a list of TDLib Messages to ExportMessages.
     * Filters out null results from messages that couldn't be converted.
     *
     * @param messages List of TDLib Message objects
     * @return List of successfully converted ExportMessage objects
     */
    fun toExportMessages(messages: List<Message>): List<ExportMessage> = messages.mapNotNull { toExportMessage(it) }

    /**
     * Map MessageVideo to ExportVideo.
     */
    private fun mapVideo(
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
            TelegramLogRepository.debug(
                TAG,
                "Skipping video message ${message.id}: missing remoteId or uniqueId",
            )
            return null
        }

        // Build thumbnail if present
        val thumbnail =
            video.thumbnail?.let { thumb ->
                ExportThumbnail(
                    width = thumb.width,
                    height = thumb.height,
                    file =
                        ExportFile(
                            id = thumb.file?.id ?: 0,
                            size = thumb.file?.size ?: 0,
                            expectedSize = thumb.file?.expectedSize ?: 0,
                            local =
                                ExportLocalFile(
                                    path = thumb.file?.local?.path ?: "",
                                    canBeDownloaded = thumb.file?.local?.canBeDownloaded ?: false,
                                    canBeDeleted = thumb.file?.local?.canBeDeleted ?: false,
                                    isDownloadingActive = thumb.file?.local?.isDownloadingActive ?: false,
                                    isDownloadingCompleted = thumb.file?.local?.isDownloadingCompleted ?: false,
                                    downloadOffset = thumb.file?.local?.downloadOffset ?: 0,
                                    downloadedPrefixSize = thumb.file?.local?.downloadedPrefixSize ?: 0,
                                    downloadedSize = thumb.file?.local?.downloadedSize ?: 0,
                                ),
                            remote =
                                ExportRemoteFile(
                                    id = thumb.file?.remote?.id,
                                    uniqueId = thumb.file?.remote?.uniqueId,
                                    isUploadingActive = thumb.file?.remote?.isUploadingActive ?: false,
                                    isUploadingCompleted = thumb.file?.remote?.isUploadingCompleted ?: false,
                                    uploadedSize = thumb.file?.remote?.uploadedSize ?: 0,
                                ),
                        ),
                )
            }

        // Build video content
        val videoContent =
            ExportVideoContent(
                duration = video.duration,
                width = video.width,
                height = video.height,
                fileName = video.fileName ?: "",
                mimeType = video.mimeType ?: "",
                supportsStreaming = video.supportsStreaming,
                thumbnail = thumbnail,
                video =
                    ExportFile(
                        id = videoFile?.id ?: 0,
                        size = videoFile?.size ?: 0,
                        expectedSize = videoFile?.expectedSize ?: 0,
                        local =
                            ExportLocalFile(
                                path = videoFile?.local?.path ?: "",
                                canBeDownloaded = videoFile?.local?.canBeDownloaded ?: false,
                                canBeDeleted = videoFile?.local?.canBeDeleted ?: false,
                                isDownloadingActive = videoFile?.local?.isDownloadingActive ?: false,
                                isDownloadingCompleted = videoFile?.local?.isDownloadingCompleted ?: false,
                                downloadOffset = videoFile?.local?.downloadOffset ?: 0,
                                downloadedPrefixSize = videoFile?.local?.downloadedPrefixSize ?: 0,
                                downloadedSize = videoFile?.local?.downloadedSize ?: 0,
                            ),
                        remote =
                            ExportRemoteFile(
                                id = remoteId,
                                uniqueId = uniqueId,
                                isUploadingActive = videoFile?.remote?.isUploadingActive ?: false,
                                isUploadingCompleted = videoFile?.remote?.isUploadingCompleted ?: false,
                                uploadedSize = videoFile?.remote?.uploadedSize ?: 0,
                            ),
                    ),
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
     * Map MessagePhoto to ExportPhoto.
     */
    private fun mapPhoto(
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
                    photo =
                        ExportFile(
                            id = photoFile?.id ?: 0,
                            size = photoFile?.size ?: 0,
                            expectedSize = photoFile?.expectedSize ?: 0,
                            local =
                                ExportLocalFile(
                                    path = photoFile?.local?.path ?: "",
                                    canBeDownloaded = photoFile?.local?.canBeDownloaded ?: false,
                                    canBeDeleted = photoFile?.local?.canBeDeleted ?: false,
                                    isDownloadingActive = photoFile?.local?.isDownloadingActive ?: false,
                                    isDownloadingCompleted = photoFile?.local?.isDownloadingCompleted ?: false,
                                    downloadOffset = photoFile?.local?.downloadOffset ?: 0,
                                    downloadedPrefixSize = photoFile?.local?.downloadedPrefixSize ?: 0,
                                    downloadedSize = photoFile?.local?.downloadedSize ?: 0,
                                ),
                            remote =
                                ExportRemoteFile(
                                    id = remoteId,
                                    uniqueId = uniqueId,
                                    isUploadingActive = photoFile?.remote?.isUploadingActive ?: false,
                                    isUploadingCompleted = photoFile?.remote?.isUploadingCompleted ?: false,
                                    uploadedSize = photoFile?.remote?.uploadedSize ?: 0,
                                ),
                        ),
                )
            } ?: emptyList()

        // Must have at least one valid size
        if (sizes.isEmpty()) {
            TelegramLogRepository.debug(
                TAG,
                "Skipping photo message ${message.id}: no valid photo sizes with remoteId",
            )
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
     * Map MessageText to ExportText with metadata extraction.
     */
    private fun mapText(
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
     * Map MessageDocument to ExportDocument.
     */
    private fun mapDocument(
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
            TelegramLogRepository.debug(
                TAG,
                "Skipping document message ${message.id}: missing remoteId or uniqueId",
            )
            return null
        }

        // Build thumbnail if present
        val thumbnail =
            doc.thumbnail?.let { thumb ->
                ExportThumbnail(
                    width = thumb.width,
                    height = thumb.height,
                    file =
                        ExportFile(
                            id = thumb.file?.id ?: 0,
                            size = thumb.file?.size ?: 0,
                            expectedSize = thumb.file?.expectedSize ?: 0,
                            local =
                                ExportLocalFile(
                                    path = thumb.file?.local?.path ?: "",
                                    canBeDownloaded = thumb.file?.local?.canBeDownloaded ?: false,
                                    canBeDeleted = thumb.file?.local?.canBeDeleted ?: false,
                                    isDownloadingActive = thumb.file?.local?.isDownloadingActive ?: false,
                                    isDownloadingCompleted = thumb.file?.local?.isDownloadingCompleted ?: false,
                                    downloadOffset = thumb.file?.local?.downloadOffset ?: 0,
                                    downloadedPrefixSize = thumb.file?.local?.downloadedPrefixSize ?: 0,
                                    downloadedSize = thumb.file?.local?.downloadedSize ?: 0,
                                ),
                            remote =
                                ExportRemoteFile(
                                    id = thumb.file?.remote?.id,
                                    uniqueId = thumb.file?.remote?.uniqueId,
                                    isUploadingActive = thumb.file?.remote?.isUploadingActive ?: false,
                                    isUploadingCompleted = thumb.file?.remote?.isUploadingCompleted ?: false,
                                    uploadedSize = thumb.file?.remote?.uploadedSize ?: 0,
                                ),
                        ),
                )
            }

        val docContent =
            ExportDocumentContent(
                fileName = doc.fileName ?: "",
                mimeType = doc.mimeType ?: "",
                thumbnail = thumbnail,
                document =
                    ExportFile(
                        id = docFile?.id ?: 0,
                        size = docFile?.size ?: 0,
                        expectedSize = docFile?.expectedSize ?: 0,
                        local =
                            ExportLocalFile(
                                path = docFile?.local?.path ?: "",
                                canBeDownloaded = docFile?.local?.canBeDownloaded ?: false,
                                canBeDeleted = docFile?.local?.canBeDeleted ?: false,
                                isDownloadingActive = docFile?.local?.isDownloadingActive ?: false,
                                isDownloadingCompleted = docFile?.local?.isDownloadingCompleted ?: false,
                                downloadOffset = docFile?.local?.downloadOffset ?: 0,
                                downloadedPrefixSize = docFile?.local?.downloadedPrefixSize ?: 0,
                                downloadedSize = docFile?.local?.downloadedSize ?: 0,
                            ),
                        remote =
                            ExportRemoteFile(
                                id = remoteId,
                                uniqueId = uniqueId,
                                isUploadingActive = docFile?.remote?.isUploadingActive ?: false,
                                isUploadingCompleted = docFile?.remote?.isUploadingCompleted ?: false,
                                uploadedSize = docFile?.remote?.uploadedSize ?: 0,
                            ),
                    ),
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
     * Map MessageAudio to ExportAudio.
     */
    private fun mapAudio(
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
            TelegramLogRepository.debug(
                TAG,
                "Skipping audio message ${message.id}: missing remoteId or uniqueId",
            )
            return null
        }

        // Build album cover thumbnail if present
        val albumCover =
            audio.albumCoverThumbnail?.let { thumb ->
                ExportThumbnail(
                    width = thumb.width,
                    height = thumb.height,
                    file =
                        ExportFile(
                            id = thumb.file?.id ?: 0,
                            size = thumb.file?.size ?: 0,
                            expectedSize = thumb.file?.expectedSize ?: 0,
                            local =
                                ExportLocalFile(
                                    path = thumb.file?.local?.path ?: "",
                                    canBeDownloaded = thumb.file?.local?.canBeDownloaded ?: false,
                                    canBeDeleted = thumb.file?.local?.canBeDeleted ?: false,
                                    isDownloadingActive = thumb.file?.local?.isDownloadingActive ?: false,
                                    isDownloadingCompleted = thumb.file?.local?.isDownloadingCompleted ?: false,
                                    downloadOffset = thumb.file?.local?.downloadOffset ?: 0,
                                    downloadedPrefixSize = thumb.file?.local?.downloadedPrefixSize ?: 0,
                                    downloadedSize = thumb.file?.local?.downloadedSize ?: 0,
                                ),
                            remote =
                                ExportRemoteFile(
                                    id = thumb.file?.remote?.id,
                                    uniqueId = thumb.file?.remote?.uniqueId,
                                    isUploadingActive = thumb.file?.remote?.isUploadingActive ?: false,
                                    isUploadingCompleted = thumb.file?.remote?.isUploadingCompleted ?: false,
                                    uploadedSize = thumb.file?.remote?.uploadedSize ?: 0,
                                ),
                        ),
                )
            }

        val audioContent =
            ExportAudioContent(
                duration = audio.duration,
                title = audio.title ?: "",
                performer = audio.performer ?: "",
                fileName = audio.fileName ?: "",
                mimeType = audio.mimeType ?: "",
                albumCoverThumbnail = albumCover,
                audio =
                    ExportFile(
                        id = audioFile?.id ?: 0,
                        size = audioFile?.size ?: 0,
                        expectedSize = audioFile?.expectedSize ?: 0,
                        local =
                            ExportLocalFile(
                                path = audioFile?.local?.path ?: "",
                                canBeDownloaded = audioFile?.local?.canBeDownloaded ?: false,
                                canBeDeleted = audioFile?.local?.canBeDeleted ?: false,
                                isDownloadingActive = audioFile?.local?.isDownloadingActive ?: false,
                                isDownloadingCompleted = audioFile?.local?.isDownloadingCompleted ?: false,
                                downloadOffset = audioFile?.local?.downloadOffset ?: 0,
                                downloadedPrefixSize = audioFile?.local?.downloadedPrefixSize ?: 0,
                                downloadedSize = audioFile?.local?.downloadedSize ?: 0,
                            ),
                        remote =
                            ExportRemoteFile(
                                id = remoteId,
                                uniqueId = uniqueId,
                                isUploadingActive = audioFile?.remote?.isUploadingActive ?: false,
                                isUploadingCompleted = audioFile?.remote?.isUploadingCompleted ?: false,
                                uploadedSize = audioFile?.remote?.uploadedSize ?: 0,
                            ),
                    ),
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
     * Map unsupported message types to ExportOtherRaw.
     */
    private fun mapOther(
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

    /**
     * Map TDLib text entities to ExportTextEntity.
     */
    private fun mapTextEntities(entities: Array<dev.g000sha256.tdl.dto.TextEntity>?): List<ExportTextEntity> =
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
    private fun extractTextMetadata(
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
    private data class TextMessageMetadata(
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
