package com.fishit.player.pipeline.telegram.catalog

import com.fishit.player.core.model.RawMediaMetadata
import dev.g000sha256.tdl.dto.Message

/**
 * Mapper for converting TDLib messages to catalog metadata.
 *
 * Provides message classification and mapping to [RawMediaMetadata] per MEDIA_NORMALIZATION_CONTRACT.
 *
 * **Design Principles:**
 * - NO title cleaning or normalization (raw field extraction only)
 * - NO TMDB lookups or cross-pipeline decisions
 * - Scene-style filenames preserved exactly
 * - Uses ImageRef for thumbnails (no raw URLs or TDLib DTOs)
 * - Builds Telegram URIs in tg://file/<fileId>?remoteId=...&uniqueId=... format
 *
 * **Architecture Boundaries:**
 * - NO direct ExoPlayer or UI dependencies
 * - NO persistence or caching logic
 * - NO downloads (thumbnails or media files)
 *
 * @see MediaKind for message classification
 * @see RawMediaMetadata for output format
 */
interface TelegramCatalogMessageMapper {
    /**
     * Classify a TDLib message by media kind.
     *
     * Analyzes message content type and MIME type to determine the kind of media.
     *
     * **Classification Rules:**
     * - MessagePhoto or image/star MIME → Image
     * - MessageVideo, MessageAnimation, or video/star MIME → Video
     * - MessageAudio, MessageVoiceNote, or audio/star MIME → Audio
     * - MessageDocument with archive MIME → Document
     * - Other → Unknown
     *
     * @param message TDLib message to classify.
     * @return Media kind classification.
     */
    fun classifyMediaKind(message: Message): MediaKind

    /**
     * Convert a TDLib message to RawMediaMetadata.
     *
     * Extracts raw metadata from the message content and builds a RawMediaMetadata instance
     * following MEDIA_NORMALIZATION_CONTRACT.
     *
     * **Mapping Rules:**
     * - originalTitle: caption or fileName (NO cleaning/normalization)
     * - sourceType: SourceType.TELEGRAM
     * - sourceLabel: "Telegram: {chatTitle}"
     * - sourceId: "tg://file/<fileId>?remoteId={remoteId}&uniqueId={uniqueId}"
     * - thumbnail: ImageRef.TelegramThumb for video/document thumbnails
     * - placeholderThumbnail: ImageRef.InlineBytes for minithumbnails
     * - duration: extracted from video/audio content
     * - width/height: extracted from video/photo content
     * - year/season/episode: null (delegated to normalizer)
     *
     * @param message TDLib message to map.
     * @param chatId Chat ID where the message originated.
     * @param chatTitle Human-readable chat title (nullable).
     * @param mediaKind Pre-classified media kind (default: call classifyMediaKind).
     * @return RawMediaMetadata instance, or null if the message has no extractable media content.
     */
    fun toRawMediaMetadata(
        message: Message,
        chatId: Long,
        chatTitle: String?,
        mediaKind: MediaKind = classifyMediaKind(message),
    ): RawMediaMetadata?
}
