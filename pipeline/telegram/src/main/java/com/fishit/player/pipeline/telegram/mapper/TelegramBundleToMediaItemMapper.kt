package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.infra.transport.telegram.api.TgPhotoSize
import com.fishit.player.pipeline.telegram.grouper.StructuredMetadata
import com.fishit.player.pipeline.telegram.grouper.TelegramMessageBundle
import com.fishit.player.pipeline.telegram.grouper.TelegramStructuredMetadataExtractor
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import com.fishit.player.pipeline.telegram.model.TelegramPhotoSize
import javax.inject.Inject

/**
 * Maps [TelegramMessageBundle] to [TelegramMediaItem](s).
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md Section 4.3:
 * - MUST emit one TelegramMediaItem per VIDEO in the bundle (lossless, Rule R7)
 * - MUST apply Poster Selection Rules (Rule R9) - max pixel area
 * - MUST correctly set all bundle fields on each emitted item
 * - MUST correctly set bundleType on each emitted item
 * - MUST NOT define "primary asset" or UI selection policy (Rule R8b)
 *
 * Per Contract R8 (Shared externalIds):
 * - All emitted items from the same bundle share the same structured metadata
 * - Enables downstream normalizer to unify into single canonical media entry
 *
 * Usage:
 * ```kotlin
 * val mapper = TelegramBundleToMediaItemMapper()
 * val bundles: List<TelegramMessageBundle> = bundler.groupMessages(messages)
 * val items: List<TelegramMediaItem> = bundles.flatMap { mapper.mapBundle(it) }
 * ```
 */
class TelegramBundleToMediaItemMapper @Inject constructor(
        private val metadataExtractor: TelegramStructuredMetadataExtractor,
) {

    /**
     * Maps a single bundle to one or more TelegramMediaItem(s).
     *
     * Per Contract R7 (Lossless Emission):
     * - Returns one TelegramMediaItem per VIDEO message in the bundle
     * - Returns empty list if bundle has no VIDEO messages (should not happen for valid bundles)
     *
     * @param bundle The bundle to map
     * @return List of TelegramMediaItem, one per VIDEO in the bundle
     */
    fun mapBundle(bundle: TelegramMessageBundle): List<TelegramMediaItem> {
        if (bundle.videoMessages.isEmpty()) {
            UnifiedLog.w(TAG) {
                "Bundle has no VIDEO messages: chatId=${bundle.chatId}, timestamp=${bundle.timestamp}"
            }
            return emptyList()
        }

        // Extract structured metadata from TEXT message (if present)
        val structuredMetadata =
                bundle.textMessage?.let { extractMetadata(it) } ?: StructuredMetadata.EMPTY

        // Select best poster from PHOTO message (if present)
        val bestPoster = bundle.photoMessage?.let { selectBestPhoto(it) }

        // Map each VIDEO to a TelegramMediaItem (lossless emission per R7)
        val items =
                bundle.videoMessages.map { videoMessage ->
                    mapVideoToMediaItem(
                            videoMessage = videoMessage,
                            bundle = bundle,
                            structuredMetadata = structuredMetadata,
                            posterSize = bestPoster,
                    )
                }

        UnifiedLog.d(TAG) {
            "Mapped bundle: chatId=${bundle.chatId}, " +
                    "bundleType=${bundle.bundleType}, " +
                    "emittedItems=${items.size}, " +
                    "hasTypedTmdb=${structuredMetadata.hasTypedTmdb}, " +
                    "tmdbType=${structuredMetadata.tmdbType}"
        }

        return items
    }

    /**
     * Maps multiple bundles to TelegramMediaItems.
     *
     * Convenience method for processing a list of bundles.
     */
    fun mapBundles(bundles: List<TelegramMessageBundle>): List<TelegramMediaItem> =
            bundles.flatMap { mapBundle(it) }

    /** Extracts structured metadata from a TEXT message. */
    private fun extractMetadata(textMessage: TgMessage): StructuredMetadata {
        return metadataExtractor.extractStructuredMetadata(textMessage) ?: StructuredMetadata.EMPTY
    }

    /**
     * Selects the best photo size from a PHOTO message.
     *
     * Per Contract R9 (Poster Selection):
     * - Select size with maximum pixel area (width * height)
     * - Ties broken by: larger height → larger width
     */
    private fun selectBestPhoto(photoMessage: TgMessage): TgPhotoSize? {
        val photoContent = photoMessage.content as? TgContent.Photo
        if (photoContent == null) {
            UnifiedLog.w(TAG) {
                "Expected PHOTO content but got ${photoMessage.content?.javaClass?.simpleName}"
            }
            return null
        }

        return selectBestPhotoSize(photoContent.sizes)
    }

    /**
     * Maps a VIDEO message to a TelegramMediaItem.
     *
     * Per Contract R7 (Lossless Emission):
     * - Handles both TgContent.Video and TgContent.Document (with video MIME)
     * - Document-videos are common in Telegram exports
     *
     * Per Contract D (Timestamp Normalization):
     * - Converts TgMessage.date (seconds) to milliseconds for consistency
     *
     * **Thumbnail Priority (Dec 2025 fix):**
     * 1. PHOTO message in bundle (posterSize) - highest quality poster
     * 2. Video's own thumbnail (videoProps.thumbRemoteId) - fallback
     * 3. No thumbnail if neither available
     */
    private fun mapVideoToMediaItem(
            videoMessage: TgMessage,
            bundle: TelegramMessageBundle,
            structuredMetadata: StructuredMetadata,
            posterSize: TgPhotoSize?,
    ): TelegramMediaItem {
        // Extract video properties from either Video or Document content
        val videoProps = extractVideoProperties(videoMessage)

        // Convert timestamp from seconds to milliseconds (Contract D)
        val timestampMs = videoMessage.date * 1000L

        // Thumbnail selection: PHOTO message takes priority, then video's own thumbnail
        val effectiveThumbRemoteId = posterSize?.remoteId ?: videoProps.thumbRemoteId
        val effectiveThumbWidth = posterSize?.width ?: videoProps.thumbWidth
        val effectiveThumbHeight = posterSize?.height ?: videoProps.thumbHeight
        
        // Minithumbnail: from video (PHOTO messages don't provide minithumbnail in bundles)
        val effectiveMinithumbBytes = videoProps.minithumbnailBytes
        val effectiveMinithumbWidth = videoProps.minithumbnailWidth
        val effectiveMinithumbHeight = videoProps.minithumbnailHeight

        return TelegramMediaItem(
                // Core identifiers
                chatId = videoMessage.chatId,
                messageId = videoMessage.messageId,
                mediaType = TelegramMediaType.VIDEO,

                // File reference (remoteId only - resolve fileId at runtime)
                remoteId = videoProps.remoteId,

                // Video properties
                title = videoProps.fileName ?: videoProps.caption?.take(MAX_CAPTION_TITLE_LENGTH) ?: "",
                fileName = videoProps.fileName,
                caption = videoProps.caption,
                mimeType = videoProps.mimeType,
                sizeBytes = videoProps.fileSize,
                durationSecs = videoProps.duration,
                width = videoProps.width,
                height = videoProps.height,
                supportsStreaming = videoProps.supportsStreaming,

                // Thumbnail: PHOTO > Video's own thumbnail (v2 fix)
                thumbRemoteId = effectiveThumbRemoteId,
                thumbnailWidth = effectiveThumbWidth,
                thumbnailHeight = effectiveThumbHeight,
                
                // Minithumbnail for instant blur placeholder
                minithumbnailBytes = effectiveMinithumbBytes,
                minithumbnailWidth = effectiveMinithumbWidth,
                minithumbnailHeight = effectiveMinithumbHeight,

                // Timestamp in MILLISECONDS (Contract D)
                date = timestampMs,

                // === Structured Bundle Fields (Contract Section 3.1) ===
                // Per Gold Decision Dec 2025: Both tmdbId AND tmdbType for typed canonical IDs
                structuredTmdbId = structuredMetadata.tmdbId,
                structuredTmdbType = structuredMetadata.tmdbType,
                structuredRating = structuredMetadata.tmdbRating,
                structuredYear = structuredMetadata.year,
                structuredFsk = structuredMetadata.fsk,
                structuredGenres = structuredMetadata.genres.takeIf { it.isNotEmpty() },
                structuredDirector = structuredMetadata.director,
                structuredOriginalTitle = structuredMetadata.originalTitle,
                structuredProductionCountry = structuredMetadata.productionCountry,
                structuredLengthMinutes = structuredMetadata.lengthMinutes,

                // Bundle metadata for debugging
                bundleType = bundle.bundleType,
                textMessageId = bundle.textMessage?.messageId,
                photoMessageId = bundle.photoMessage?.messageId,
        )
    }

    /**
     * Extracts video properties from either TgContent.Video or TgContent.Document.
     *
     * Per Contract B (DOC+VIDEO):
     * - Document with video MIME is treated as VIDEO for emission
     * - MUST NOT crash on Document content
     *
     * **Bug fix (Dec 2025):** Now also extracts video's own thumbnail and minithumbnail.
     * Previously these were ignored, causing thumbnails to be missing when no PHOTO was in the bundle.
     */
    private fun extractVideoProperties(videoMessage: TgMessage): VideoProperties {
        return when (val content = videoMessage.content) {
            is TgContent.Video ->
                    VideoProperties(
                            remoteId = content.remoteId,
                            fileName = content.fileName,
                            caption = content.caption,
                            mimeType = content.mimeType,
                            fileSize = content.fileSize,
                            duration = content.duration,
                            width = content.width,
                            height = content.height,
                            supportsStreaming = content.supportsStreaming,
                            // Extract video's own thumbnail (v2 fix)
                            thumbRemoteId = content.thumbnail?.remoteId,
                            thumbWidth = content.thumbnail?.width,
                            thumbHeight = content.thumbnail?.height,
                            minithumbnailBytes = content.minithumbnail?.data,
                            minithumbnailWidth = content.minithumbnail?.width,
                            minithumbnailHeight = content.minithumbnail?.height,
                    )
            is TgContent.Document ->
                    VideoProperties(
                            remoteId = content.remoteId,
                            fileName = content.fileName,
                            caption = content.caption,
                            mimeType = content.mimeType,
                            fileSize = content.fileSize,
                            duration = null, // Document doesn't have duration
                            width = null,
                            height = null,
                            supportsStreaming = false,
                            // Extract document's thumbnail (v2 fix)
                            thumbRemoteId = content.thumbnail?.remoteId,
                            thumbWidth = content.thumbnail?.width,
                            thumbHeight = content.thumbnail?.height,
                            minithumbnailBytes = content.minithumbnail?.data,
                            minithumbnailWidth = content.minithumbnail?.width,
                            minithumbnailHeight = content.minithumbnail?.height,
                    )
            else -> {
                UnifiedLog.w(TAG) {
                    "Unexpected content type for VIDEO message: " +
                            "${content?.javaClass?.simpleName}, messageId=${videoMessage.messageId}"
                }
                VideoProperties(
                        remoteId = null,
                        fileName = null,
                        caption = null,
                        mimeType = null,
                        fileSize = null,
                        duration = null,
                        width = null,
                        height = null,
                        supportsStreaming = false,
                )
            }
        }
    }

    /**
     * Internal data class for video properties extraction.
     *
     * Now includes thumbnail and minithumbnail from the video content itself,
     * as a fallback when no separate PHOTO message is in the bundle.
     */
    private data class VideoProperties(
            val remoteId: String?,
            val fileName: String?,
            val caption: String?,
            val mimeType: String?,
            val fileSize: Long?,
            val duration: Int?,
            val width: Int?,
            val height: Int?,
            val supportsStreaming: Boolean,
            // === Video's own thumbnail (fallback when no PHOTO in bundle) ===
            val thumbRemoteId: String? = null,
            val thumbWidth: Int? = null,
            val thumbHeight: Int? = null,
            val minithumbnailBytes: ByteArray? = null,
            val minithumbnailWidth: Int? = null,
            val minithumbnailHeight: Int? = null,
    )

    companion object {
        private const val TAG = "TelegramBundleToMediaItemMapper"

        /** Maximum caption length to use as title fallback. */
        private const val MAX_CAPTION_TITLE_LENGTH = 256

        /**
         * Selects the best photo size by maximum pixel area.
         *
         * Per Contract R9 (Poster Selection):
         * - Select size with maximum pixel area (width * height)
         * - Ties broken deterministically by: larger height → larger width
         *
         * @param sizes Available photo sizes
         * @return Best size, or null if empty
         */
        fun selectBestPhotoSize(sizes: List<TgPhotoSize>): TgPhotoSize? {
            if (sizes.isEmpty()) return null

            return sizes.maxWithOrNull(
                    compareBy<TgPhotoSize> { it.width.toLong() * it.height.toLong() }
                            .thenBy { it.height }
                            .thenBy { it.width },
            )
        }

        /**
         * Converts TgPhotoSize list to TelegramPhotoSize list.
         *
         * Used when preserving all photo sizes in TelegramMediaItem.
         */
        fun convertPhotoSizes(sizes: List<TgPhotoSize>): List<TelegramPhotoSize> =
                sizes.map { size ->
                    TelegramPhotoSize(
                            remoteId = size.remoteId,
                            width = size.width,
                            height = size.height,
                            sizeBytes = size.fileSize,
                    )
                }
    }
}
