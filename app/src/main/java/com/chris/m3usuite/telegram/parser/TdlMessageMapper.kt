package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo

/**
 * Maps TDLib DTOs (via tdlib-coroutines) to ExportMessage domain types.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Phase C.1:
 * - Introduces mappings from TDLib DTOs to ExportMessage
 * - Must handle: Video, Photo, Text, Document, Audio messages
 * - Always extracts remoteId (required), uniqueId (required), fileId (optional)
 * - Keeps schema 1:1 aligned with CLI/fixture JSON
 *
 * **Pipeline Equivalence**: This mapper delegates to ExportMessageFactory which
 * uses the same logic for building ExportMessage instances from TDLib DTOs as
 * the JSON-based loader uses for CLI exports. This ensures identical parsing
 * behavior between both pipelines.
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
     * Delegates to ExportMessageFactory.fromTdlMessage() for centralized logic.
     *
     * @param message TDLib Message object
     * @return ExportMessage or null if the message type is not supported or lacks required fields
     */
    fun toExportMessage(message: Message): ExportMessage? {
        val result = ExportMessageFactory.fromTdlMessage(message)
        if (result == null) {
            TelegramLogRepository.debug(
                TAG,
                "Skipping message ${message.id}: could not convert to ExportMessage",
            )
        }
        return result
    }

    /**
     * Convert a list of TDLib Messages to ExportMessages.
     * Filters out null results from messages that couldn't be converted.
     *
     * @param messages List of TDLib Message objects
     * @return List of successfully converted ExportMessage objects
     */
    fun toExportMessages(messages: List<Message>): List<ExportMessage> = messages.mapNotNull { toExportMessage(it) }

    // =========================================================================
    // Backward-compatible helper functions (delegate to ExportMessageFactory)
    // =========================================================================

    /**
     * Validate that a TDLib file has required remote identifiers.
     *
     * @param remoteId The remote file ID
     * @param uniqueId The unique file ID
     * @param messageId Message ID for logging
     * @param fileType Description of file type for logging
     * @return true if valid, false otherwise
     */
    private fun validateFileIdentifiers(
        remoteId: String?,
        uniqueId: String?,
        messageId: Long,
        fileType: String,
    ): Boolean {
        if (remoteId.isNullOrBlank() || uniqueId.isNullOrBlank()) {
            TelegramLogRepository.debug(
                TAG,
                "Skipping $fileType message $messageId: missing remoteId or uniqueId",
            )
            return false
        }
        return true
    }

    /**
     * Build an ExportFile from TDLib file components.
     * Delegates to ExportMessageFactory.
     */
    private fun buildExportFile(file: dev.g000sha256.tdl.dto.File?): ExportFile = ExportMessageFactory.buildExportFile(file)

    /**
     * Build an ExportLocalFile from TDLib local file info.
     * Delegates to ExportMessageFactory.
     */
    private fun buildExportLocalFile(local: dev.g000sha256.tdl.dto.LocalFile?): ExportLocalFile =
        ExportMessageFactory.buildExportLocalFile(local)

    /**
     * Build an ExportRemoteFile from TDLib remote file info.
     * Delegates to ExportMessageFactory.
     */
    private fun buildExportRemoteFile(remote: dev.g000sha256.tdl.dto.RemoteFile?): ExportRemoteFile =
        ExportMessageFactory.buildExportRemoteFile(remote)

    /**
     * Build an ExportThumbnail from TDLib thumbnail info.
     * Delegates to ExportMessageFactory.
     */
    private fun buildExportThumbnail(thumbnail: dev.g000sha256.tdl.dto.Thumbnail?): ExportThumbnail? =
        ExportMessageFactory.buildExportThumbnail(thumbnail)

    // =========================================================================
    // Message Type Mapping Functions (backward compatibility)
    // =========================================================================

    /**
     * Map MessageVideo to ExportVideo.
     * Now delegates to ExportMessageFactory.
     */
    private fun mapVideo(
        message: Message,
        content: MessageVideo,
        dateEpoch: Long,
        dateIso: String,
    ): ExportVideo? = ExportMessageFactory.fromTdlVideo(message, content, dateEpoch, dateIso)

    /**
     * Map MessagePhoto to ExportPhoto.
     * Now delegates to ExportMessageFactory.
     */
    private fun mapPhoto(
        message: Message,
        content: MessagePhoto,
        dateEpoch: Long,
        dateIso: String,
    ): ExportPhoto? = ExportMessageFactory.fromTdlPhoto(message, content, dateEpoch, dateIso)

    /**
     * Map MessageText to ExportText with metadata extraction.
     * Now delegates to ExportMessageFactory.
     */
    private fun mapText(
        message: Message,
        content: MessageText,
        dateEpoch: Long,
        dateIso: String,
    ): ExportText = ExportMessageFactory.fromTdlText(message, content, dateEpoch, dateIso)

    /**
     * Map MessageDocument to ExportDocument.
     * Now delegates to ExportMessageFactory.
     */
    private fun mapDocument(
        message: Message,
        content: MessageDocument,
        dateEpoch: Long,
        dateIso: String,
    ): ExportDocument? = ExportMessageFactory.fromTdlDocument(message, content, dateEpoch, dateIso)

    /**
     * Map MessageAudio to ExportAudio.
     * Now delegates to ExportMessageFactory.
     */
    private fun mapAudio(
        message: Message,
        content: MessageAudio,
        dateEpoch: Long,
        dateIso: String,
    ): ExportAudio? = ExportMessageFactory.fromTdlAudio(message, content, dateEpoch, dateIso)

    /**
     * Map unsupported message types to ExportOtherRaw.
     * Now delegates to ExportMessageFactory.
     */
    private fun mapOther(
        message: Message,
        dateEpoch: Long,
        dateIso: String,
    ): ExportOtherRaw = ExportMessageFactory.fromTdlOther(message, dateEpoch, dateIso)

    /**
     * Map TDLib text entities to ExportTextEntity.
     * Now delegates to ExportMessageFactory.
     */
    private fun mapTextEntities(entities: Array<dev.g000sha256.tdl.dto.TextEntity>?): List<ExportTextEntity> =
        ExportMessageFactory.mapTextEntities(entities)

    /**
     * Extract metadata from text message content.
     * Matches the CLI reference parsing logic.
     * Now delegates to ExportMessageFactory.
     */
    private fun extractTextMetadata(
        rawText: String,
        entities: List<ExportTextEntity>,
    ): ExportMessageFactory.TextMessageMetadata = ExportMessageFactory.extractTextMetadata(rawText, entities)
}
