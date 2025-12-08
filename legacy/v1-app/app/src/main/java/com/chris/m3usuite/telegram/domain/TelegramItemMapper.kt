package com.chris.m3usuite.telegram.domain

import com.chris.m3usuite.data.obx.ObxChatScanState
import com.chris.m3usuite.data.obx.ObxTelegramItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mappers for converting between TelegramItem domain objects and ObjectBox entities.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md:
 * - (chatId, anchorMessageId) is the logical identity, but ObjectBox id is technical
 * - remoteId/uniqueId/fileId fields map 1:1 where applicable
 * - genres is serialized to JSON for storage
 */

private val jsonParser = Json { ignoreUnknownKeys = true }

/**
 * Convert TelegramItem domain object to ObxTelegramItem entity.
 */
fun TelegramItem.toObx(): ObxTelegramItem =
    ObxTelegramItem(
        // Identity
        chatId = chatId,
        anchorMessageId = anchorMessageId,
        itemType = type.name,
        // Video reference
        videoRemoteId = videoRef?.remoteId,
        videoUniqueId = videoRef?.uniqueId,
        videoFileId = videoRef?.fileId,
        videoSizeBytes = videoRef?.sizeBytes,
        videoMimeType = videoRef?.mimeType,
        videoDurationSeconds = videoRef?.durationSeconds,
        videoWidth = videoRef?.width,
        videoHeight = videoRef?.height,
        // Document reference
        documentRemoteId = documentRef?.remoteId,
        documentUniqueId = documentRef?.uniqueId,
        documentFileId = documentRef?.fileId,
        documentSizeBytes = documentRef?.sizeBytes,
        documentMimeType = documentRef?.mimeType,
        documentFileName = documentRef?.fileName,
        // Poster
        posterRemoteId = posterRef?.remoteId,
        posterUniqueId = posterRef?.uniqueId,
        posterFileId = posterRef?.fileId,
        posterWidth = posterRef?.width,
        posterHeight = posterRef?.height,
        posterSizeBytes = posterRef?.sizeBytes,
        // Backdrop
        backdropRemoteId = backdropRef?.remoteId,
        backdropUniqueId = backdropRef?.uniqueId,
        backdropFileId = backdropRef?.fileId,
        backdropWidth = backdropRef?.width,
        backdropHeight = backdropRef?.height,
        backdropSizeBytes = backdropRef?.sizeBytes,
        // Metadata
        title = metadata.title,
        originalTitle = metadata.originalTitle,
        year = metadata.year,
        lengthMinutes = metadata.lengthMinutes,
        fsk = metadata.fsk,
        productionCountry = metadata.productionCountry,
        collection = metadata.collection,
        director = metadata.director,
        tmdbRating = metadata.tmdbRating,
        tmdbUrl = metadata.tmdbUrl,
        isAdult = metadata.isAdult,
        genresJson =
            if (metadata.genres.isNotEmpty()) {
                jsonParser.encodeToString(metadata.genres)
            } else {
                null
            },
        // Message references
        textMessageId = textMessageId,
        photoMessageId = photoMessageId,
        // Timestamps
        createdAtIso = createdAtIso,
        createdAtUtc = parseIsoToEpochMillis(createdAtIso),
    )

/**
 * Convert ObxTelegramItem entity to TelegramItem domain object.
 */
fun ObxTelegramItem.toDomain(): TelegramItem {
    val type =
        try {
            TelegramItemType.valueOf(itemType)
        } catch (e: IllegalArgumentException) {
            TelegramItemType.CLIP // Safe fallback
        }

    // Reconstruct video ref if present
    val videoRef =
        if (videoRemoteId != null && videoUniqueId != null) {
            TelegramMediaRef(
                remoteId = videoRemoteId!!,
                uniqueId = videoUniqueId!!,
                fileId = videoFileId,
                sizeBytes = videoSizeBytes ?: 0L,
                mimeType = videoMimeType,
                durationSeconds = videoDurationSeconds,
                width = videoWidth,
                height = videoHeight,
            )
        } else {
            null
        }

    // Reconstruct document ref if present
    val documentRef =
        if (documentRemoteId != null && documentUniqueId != null) {
            TelegramDocumentRef(
                remoteId = documentRemoteId!!,
                uniqueId = documentUniqueId!!,
                fileId = documentFileId,
                sizeBytes = documentSizeBytes ?: 0L,
                mimeType = documentMimeType,
                fileName = documentFileName,
            )
        } else {
            null
        }

    // Reconstruct poster ref if present
    val posterRef =
        if (posterRemoteId != null && posterUniqueId != null) {
            TelegramImageRef(
                remoteId = posterRemoteId!!,
                uniqueId = posterUniqueId!!,
                fileId = posterFileId,
                width = posterWidth ?: 0,
                height = posterHeight ?: 0,
                sizeBytes = posterSizeBytes ?: 0L,
            )
        } else {
            null
        }

    // Reconstruct backdrop ref if present
    val backdropRef =
        if (backdropRemoteId != null && backdropUniqueId != null) {
            TelegramImageRef(
                remoteId = backdropRemoteId!!,
                uniqueId = backdropUniqueId!!,
                fileId = backdropFileId,
                width = backdropWidth ?: 0,
                height = backdropHeight ?: 0,
                sizeBytes = backdropSizeBytes ?: 0L,
            )
        } else {
            null
        }

    // Deserialize genres
    val genres =
        genresJson?.let { json ->
            try {
                jsonParser.decodeFromString<List<String>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()

    val metadata =
        TelegramMetadata(
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
            isAdult = isAdult,
        )

    return TelegramItem(
        chatId = chatId,
        anchorMessageId = anchorMessageId,
        type = type,
        videoRef = videoRef,
        documentRef = documentRef,
        posterRef = posterRef,
        backdropRef = backdropRef,
        textMessageId = textMessageId,
        photoMessageId = photoMessageId,
        createdAtIso = createdAtIso ?: "",
        metadata = metadata,
    )
}

/**
 * Convert ChatScanState domain object to ObxChatScanState entity.
 */
fun ChatScanState.toObx(): ObxChatScanState =
    ObxChatScanState(
        chatId = chatId,
        lastScannedMessageId = lastScannedMessageId,
        hasMoreHistory = hasMoreHistory,
        status = status.name,
        lastError = lastError,
        updatedAt = updatedAt,
    )

/**
 * Convert ObxChatScanState entity to ChatScanState domain object.
 */
fun ObxChatScanState.toDomain(): ChatScanState {
    val scanStatus =
        try {
            ScanStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            ScanStatus.IDLE
        }

    return ChatScanState(
        chatId = chatId,
        lastScannedMessageId = lastScannedMessageId,
        hasMoreHistory = hasMoreHistory,
        status = scanStatus,
        lastError = lastError,
        updatedAt = updatedAt,
    )
}

/**
 * Parse ISO 8601 timestamp to epoch milliseconds.
 */
private fun parseIsoToEpochMillis(isoString: String?): Long? {
    if (isoString.isNullOrBlank()) return null
    return try {
        java.time.Instant
            .parse(isoString)
            .toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
