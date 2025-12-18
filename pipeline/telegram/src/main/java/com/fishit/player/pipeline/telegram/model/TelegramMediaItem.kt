package com.fishit.player.pipeline.telegram.model

/**
 * Domain model representing a Telegram media item for FishIT Player v2.
 *
 * Updated based on analysis of real Telegram export JSONs from docs/telegram/exports/exports/.
 * Represents playable media content from Telegram (video, document, audio, photo).
 *
 * ## v2 remoteId-First Architecture
 *
 * Per `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`:
 * - Only `remoteId` and `thumbRemoteId` are stored (stable across sessions)
 * - `fileId` is resolved at runtime via `getRemoteFile(remoteId)`
 * - `uniqueId` is NOT stored (no API to resolve it back)
 *
 * **CONTRACT COMPLIANCE (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - All fields are RAW as extracted from Telegram messages
 * - Scene-style filenames preserved exactly (e.g., "Movie.2000.1080p.BluRay.x264-GROUP.mkv")
 * - NO title cleaning, normalization, or heuristics applied
 * - All processing delegated to :core:metadata-normalizer
 *
 * @property id Unique identifier for this media item
 * @property chatId Telegram chat ID where the media is located
 * @property messageId Telegram message ID
 * @property mediaAlbumId Media album ID for grouped messages (optional)
 * @property mediaType Type of media content (VIDEO, DOCUMENT, AUDIO, PHOTO, OTHER)
 * @property remoteId Stable TDLib remote ID (session-independent) - use getRemoteFile() to get fileId
 * @property title Display title (from caption or parsed metadata)
 * @property fileName Original file name (RAW, no cleaning)
 * @property caption Message caption text (RAW)
 * @property mimeType MIME type of the media file
 * @property sizeBytes File size in bytes
 * @property durationSecs Duration in seconds (for video/audio)
 * @property width Video/photo width in pixels
 * @property height Video/photo height in pixels
 * @property supportsStreaming Whether the file supports streaming playback
 * @property thumbRemoteId Stable TDLib remote ID for thumbnail - use getRemoteFile() to get fileId
 * @property thumbnailWidth Thumbnail width in pixels
 * @property thumbnailHeight Thumbnail height in pixels
 * @property photoSizes List of photo sizes (for photo messages)
 * @property minithumbnailBytes Inline JPEG bytes for instant blur placeholder
 * @property minithumbnailWidth Minithumbnail width in pixels
 * @property minithumbnailHeight Minithumbnail height in pixels
 * @property date Message timestamp
 * @property isSeries Whether this is part of a series
 * @property seriesName Series name if applicable
 * @property seasonNumber Season number if applicable
 * @property episodeNumber Episode number if applicable
 * @property episodeTitle Episode title if applicable
 * @property year Release year (from parsed metadata)
 * @property genres Comma-separated genre list
 * @property description Content description
 *
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 */
data class TelegramMediaItem(
        val id: Long = 0,
        val chatId: Long = 0,
        val messageId: Long = 0,
        val mediaAlbumId: Long? = null,
        val mediaType: TelegramMediaType = TelegramMediaType.OTHER,
        
        // === File Reference (remoteId only - resolve fileId at runtime) ===
        /** Stable remote ID for media file. Use getRemoteFile(remoteId) to get fileId. */
        val remoteId: String? = null,
        
        val title: String = "",
        val fileName: String? = null,
        val caption: String? = null,
        val mimeType: String? = null,
        val sizeBytes: Long? = null,
        val durationSecs: Int? = null,
        val width: Int? = null,
        val height: Int? = null,
        val supportsStreaming: Boolean? = null,
        
        // === Thumbnail Reference (remoteId only - resolve fileId at runtime) ===
        /** Stable remote ID for thumbnail. Use getRemoteFile(thumbRemoteId) to get fileId. */
        val thumbRemoteId: String? = null,
        val thumbnailWidth: Int? = null,
        val thumbnailHeight: Int? = null,
        
        // === Photo Sizes (each has remoteId) ===
        val photoSizes: List<TelegramPhotoSize> = emptyList(),
        
        // === Minithumbnail (inline JPEG bytes for instant blur placeholder) ===
        val minithumbnailBytes: ByteArray? = null,
        val minithumbnailWidth: Int? = null,
        val minithumbnailHeight: Int? = null,
        
        val date: Long? = null,
        val isSeries: Boolean = false,
        val seriesName: String? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val episodeTitle: String? = null,
        val year: Int? = null,
        val genres: String? = null,
        val description: String? = null,
        
        // === Structured Bundle Fields (v2 - TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md) ===
        
        /**
         * TMDB ID from structured TEXT message.
         *
         * Extracted from tmdbUrl (e.g., "https://themoviedb.org/movie/12345" → 12345).
         * Pass-through to RawMediaMetadata.externalIds.tmdb (enables downstream unification).
         * Per Contract R4 Schema Guards: raw extracted, validation happens in extractor.
         *
         * @see structuredTmdbType for the media type (MOVIE or TV)
         */
        val structuredTmdbId: Int? = null,
        
        /**
         * TMDB media type from structured TEXT message (Gold Decision Dec 2025).
         *
         * Extracted from tmdbUrl path:
         * - "/movie/123" → MOVIE
         * - "/tv/456" → TV
         *
         * Episodes use TV type with season/episode from other fields.
         * Never use EPISODE type (TMDB has no episode root type).
         *
         * @see structuredTmdbId for the numeric ID
         */
        val structuredTmdbType: TelegramTmdbType? = null,
        
        /**
         * TMDB rating from structured TEXT message (0.0-10.0 scale).
         *
         * Pass-through to RawMediaMetadata.rating.
         * Per Contract R4 Schema Guards: must be in range 0.0..10.0 else null.
         */
        val structuredRating: Double? = null,
        
        /**
         * Year from structured TEXT message.
         *
         * Overrides filename-parsed year when present (structured data is more reliable).
         * Per Contract R4 Schema Guards: must be in range 1800..2100 else null.
         */
        val structuredYear: Int? = null,
        
        /**
         * FSK age rating from structured TEXT message.
         *
         * Used for Kids profile content filtering.
         * Per Contract R4 Schema Guards: must be in range 0..21 else null.
         * Common values: 0 (all ages), 6, 12, 16, 18 (standard FSK), 21 (explicit adult).
         */
        val structuredFsk: Int? = null,
        
        /**
         * Genres from structured TEXT message.
         *
         * Raw list of genre strings (e.g., ["Action", "Drama"]).
         * Converted to comma-separated for existing genres field compatibility.
         */
        val structuredGenres: List<String>? = null,
        
        /**
         * Director from structured TEXT message.
         *
         * Raw director name as provided by source.
         */
        val structuredDirector: String? = null,
        
        /**
         * Original title from structured TEXT message.
         *
         * Original language title (e.g., "Die Hard" for German release of English film).
         * Different from title which may be localized.
         */
        val structuredOriginalTitle: String? = null,
        
        /**
         * Production country from structured TEXT message.
         *
         * ISO country code or full name (e.g., "US", "Germany").
         */
        val structuredProductionCountry: String? = null,
        
        /**
         * Runtime in minutes from structured TEXT message.
         *
         * Per Contract R4 Schema Guards: must be in range 1..600 else null.
         * Takes precedence over durationSecs when present.
         */
        val structuredLengthMinutes: Int? = null,
        
        /**
         * Bundle type for debugging/logging.
         *
         * Indicates how this item was detected: as part of a complete 3-cluster,
         * compact 2-cluster, or as a single message.
         */
        val bundleType: TelegramBundleType = TelegramBundleType.SINGLE,
        
        /**
         * Message ID of TEXT message in bundle (for debugging/logging).
         *
         * Null if bundleType is SINGLE or no TEXT message in bundle.
         */
        val textMessageId: Long? = null,
        
        /**
         * Message ID of PHOTO message in bundle (for debugging/logging).
         *
         * Null if bundleType is SINGLE or no PHOTO message in bundle.
         */
        val photoMessageId: Long? = null,
) {
    /**
     * Returns a Telegram URI for this media item in the format:
     * tg://media/<remoteId>?chatId=<chatId>&messageId=<messageId>
     *
     * The remoteId is URL-encoded to handle special characters.
     */
    fun toTelegramUri(): String =
            if (remoteId != null) {
                val encodedRemoteId = java.net.URLEncoder.encode(remoteId, "UTF-8")
                "tg://media/$encodedRemoteId?chatId=$chatId&messageId=$messageId"
            } else {
                "tg://stub/$id?chatId=$chatId&messageId=$messageId"
            }

    /** Checks if this media item is playable (has sufficient metadata). */
    fun isPlayable(): Boolean = remoteId != null && mimeType != null

    /** Checks if this media item has a minithumbnail for instant preview. */
    fun hasMinithumbnail(): Boolean = minithumbnailBytes != null && minithumbnailBytes.isNotEmpty()

    // ByteArray requires custom equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TelegramMediaItem) return false
        return id == other.id &&
                chatId == other.chatId &&
                messageId == other.messageId &&
                mediaAlbumId == other.mediaAlbumId &&
                mediaType == other.mediaType &&
                remoteId == other.remoteId &&
                title == other.title &&
                fileName == other.fileName &&
                caption == other.caption &&
                mimeType == other.mimeType &&
                sizeBytes == other.sizeBytes &&
                durationSecs == other.durationSecs &&
                width == other.width &&
                height == other.height &&
                supportsStreaming == other.supportsStreaming &&
                thumbRemoteId == other.thumbRemoteId &&
                thumbnailWidth == other.thumbnailWidth &&
                thumbnailHeight == other.thumbnailHeight &&
                photoSizes == other.photoSizes &&
                minithumbnailBytes.contentEquals(other.minithumbnailBytes) &&
                minithumbnailWidth == other.minithumbnailWidth &&
                minithumbnailHeight == other.minithumbnailHeight &&
                date == other.date &&
                isSeries == other.isSeries &&
                seriesName == other.seriesName &&
                seasonNumber == other.seasonNumber &&
                episodeNumber == other.episodeNumber &&
                episodeTitle == other.episodeTitle &&
                year == other.year &&
                genres == other.genres &&
                description == other.description &&
                // Structured Bundle Fields (v2)
                structuredTmdbId == other.structuredTmdbId &&
                structuredRating == other.structuredRating &&
                structuredYear == other.structuredYear &&
                structuredFsk == other.structuredFsk &&
                structuredGenres == other.structuredGenres &&
                structuredDirector == other.structuredDirector &&
                structuredOriginalTitle == other.structuredOriginalTitle &&
                structuredProductionCountry == other.structuredProductionCountry &&
                structuredLengthMinutes == other.structuredLengthMinutes &&
                bundleType == other.bundleType &&
                textMessageId == other.textMessageId &&
                photoMessageId == other.photoMessageId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + chatId.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + (minithumbnailBytes?.contentHashCode() ?: 0)
        return result
    }
    
    /**
     * Checks if this media item has structured bundle metadata.
     *
     * @return true if any structured field is populated
     */
    fun hasStructuredMetadata(): Boolean =
            structuredTmdbId != null ||
                    structuredYear != null ||
                    structuredFsk != null ||
                    !structuredGenres.isNullOrEmpty()
    
    /**
     * Checks if this media item is from a complete structured bundle (FULL_3ER).
     *
     * Complete bundles have PHOTO + TEXT + VIDEO with all metadata fields.
     */
    fun isFromCompleteBunde(): Boolean = bundleType == TelegramBundleType.FULL_3ER
}
