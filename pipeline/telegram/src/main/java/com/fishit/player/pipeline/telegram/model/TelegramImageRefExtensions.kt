package com.fishit.player.pipeline.telegram.model

import com.fishit.player.core.model.ImageRef

/**
 * Extensions for extracting ImageRef from Telegram pipeline models.
 *
 * ## v2 remoteId-First Architecture
 *
 * Per `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`:
 * - Only `remoteId` is used for ImageRef.TelegramThumb
 * - `fileId` is resolved at runtime via `getRemoteFile(remoteId)`
 * - `uniqueId` is NOT used (no API to resolve it back)
 *
 * **Contract (IMAGING_SYSTEM.md):**
 * - Pipelines produce ImageRef (not raw TDLib DTOs)
 * - UI consumes ImageRef via GlobalImageLoader
 * - NO Coil dependency in pipeline modules
 *
 * **Telegram Thumbnail Strategy:**
 * - Minithumbnail: Inline JPEG bytes (~40px) for instant blur placeholder
 * - Video/Document: thumbRemoteId (320px) - resolved via getRemoteFile()
 * - Photo: Best photo size remoteId
 *
 * **Tiered Loading (Netflix-style):**
 * 1. Minithumbnail (instant, ~40x40px blur) via ImageRef.InlineBytes
 * 2. Full thumbnail (async download, ~320px) via ImageRef.TelegramThumb
 *
 * **Cache Key Stability:**
 * - Uses `remoteId` for cross-session caching
 * - `remoteId` is stable across TDLib sessions
 *
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 */

// =============================================================================
// TelegramMediaItem Extensions
// =============================================================================

/**
 * Extract minithumbnail ImageRef for instant blur placeholder.
 *
 * Returns an InlineBytes ImageRef if minithumbnail data is available. This is a ~40x40 pixel JPEG
 * that can be displayed instantly without network.
 *
 * Use this as a blur placeholder while the full thumbnail downloads.
 *
 * @return ImageRef.InlineBytes or null if no minithumbnail available
 */
fun TelegramMediaItem.toMinithumbnailImageRef(): ImageRef? {
    val bytes = minithumbnailBytes ?: return null
    if (bytes.isEmpty()) return null

    return ImageRef.InlineBytes(
            bytes = bytes,
            mimeType = "image/jpeg",
            preferredWidth = minithumbnailWidth,
            preferredHeight = minithumbnailHeight,
    )
}

/**
 * Extract thumbnail ImageRef from Telegram media item.
 *
 * Uses remoteId-first design: stores only remoteId, fileId resolved at runtime.
 *
 * Priority order:
 * 1. TelegramThumb from video/document thumbnail (thumbRemoteId)
 * 2. Largest photo size (for photo messages)
 *
 * @return ImageRef or null if no thumbnail available
 */
fun TelegramMediaItem.toThumbnailImageRef(): ImageRef? {
    // Priority 1: TelegramThumb reference (video/document thumbnail)
    thumbRemoteId?.takeIf { it.isNotBlank() }?.let { remoteId ->
        return ImageRef.TelegramThumb(
                remoteId = remoteId,
                chatId = chatId,
                messageId = messageId,
                preferredWidth = thumbnailWidth,
                preferredHeight = thumbnailHeight,
        )
    }

    // Priority 2: Photo sizes (for photo messages)
    return photoSizes.toBestImageRef(chatId, messageId)
}

/**
 * Extract full-quality poster ImageRef from photo message.
 *
 * For photo messages, extracts the largest available size. For video/document, returns null (use
 * toThumbnailImageRef instead).
 *
 * @return ImageRef or null if not a photo message
 */
fun TelegramMediaItem.toPosterImageRef(): ImageRef? {
    if (photoSizes.isEmpty()) return null
    return photoSizes.toBestImageRef(chatId, messageId)
}

/** Check if this media item has any thumbnail available. */
fun TelegramMediaItem.hasThumbnail(): Boolean =
        thumbRemoteId?.isNotBlank() == true || photoSizes.isNotEmpty()

// =============================================================================
// TelegramPhotoSize Extensions
// =============================================================================

/**
 * Convert a list of photo sizes to the best ImageRef.
 *
 * Selects the largest available photo size for quality.
 *
 * @param chatId Chat ID for context
 * @param messageId Message ID for context
 * @return ImageRef.TelegramThumb or null if empty
 */
fun List<TelegramPhotoSize>.toBestImageRef(chatId: Long, messageId: Long): ImageRef? {
    if (isEmpty()) return null

    // Select largest by pixel count
    val best = maxByOrNull { it.width * it.height } ?: return null

    return best.toImageRef(chatId, messageId)
}

/**
 * Convert a single photo size to ImageRef.
 *
 * Uses remoteId-first design per TELEGRAM_ID_ARCHITECTURE_CONTRACT.md.
 *
 * @param chatId Chat ID for context
 * @param messageId Message ID for context
 * @return ImageRef.TelegramThumb
 */
fun TelegramPhotoSize.toImageRef(chatId: Long, messageId: Long): ImageRef {
    return ImageRef.TelegramThumb(
            remoteId = remoteId,
            chatId = chatId,
            messageId = messageId,
            preferredWidth = width,
            preferredHeight = height,
    )
}

/**
 * Find the thumbnail-sized photo (smallest).
 *
 * @param chatId Chat ID for context
 * @param messageId Message ID for context
 * @return ImageRef for smallest size, or null if empty
 */
fun List<TelegramPhotoSize>.toThumbnailImageRef(chatId: Long, messageId: Long): ImageRef? {
    if (isEmpty()) return null

    // Select smallest by pixel count (thumbnail)
    val smallest = minByOrNull { it.width * it.height } ?: return null

    return smallest.toImageRef(chatId, messageId)
}

// =============================================================================
// Validation Helpers
// =============================================================================

/** Validate if the ImageRef is usable. */
fun ImageRef.TelegramThumb.isValid(): Boolean = remoteId.isNotBlank()
