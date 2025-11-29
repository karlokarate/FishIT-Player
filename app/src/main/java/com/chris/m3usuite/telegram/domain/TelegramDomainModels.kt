package com.chris.m3usuite.telegram.domain

import com.chris.m3usuite.telegram.parser.ExportMessage

/**
 * Domain models for Telegram content items.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Section 5.3:
 * - These are the canonical output models produced by the parser pipeline
 * - remoteId and uniqueId are REQUIRED for all file references
 * - fileId is OPTIONAL and may become stale
 */

// =============================================================================
// Item Type Enum
// =============================================================================

/**
 * Classification of Telegram media items.
 */
enum class TelegramItemType {
    /** Standalone movie */
    MOVIE,

    /** Single episode of a series */
    SERIES_EPISODE,

    /** Short clip or unknown duration content */
    CLIP,

    /** Audio book file */
    AUDIOBOOK,

    /** RAR/ZIP/7z archive item */
    RAR_ITEM,

    /** Photo with text metadata but no video (poster-only entry) */
    POSTER_ONLY,
}

// =============================================================================
// File Reference DTOs
// =============================================================================

/**
 * Reference to a video file in TDLib.
 *
 * Per contract: remoteId and uniqueId are REQUIRED and stable across sessions.
 * fileId is OPTIONAL and may become stale - use getRemoteFile(remoteId) to refresh.
 *
 * @property remoteId Stable remote file identifier (REQUIRED)
 * @property uniqueId Stable unique file identifier (REQUIRED)
 * @property fileId Volatile TDLib-local file ID (OPTIONAL, may become stale)
 * @property sizeBytes File size in bytes
 * @property mimeType MIME type of the video
 * @property durationSeconds Video duration in seconds
 * @property width Video width in pixels
 * @property height Video height in pixels
 */
data class TelegramMediaRef(
    val remoteId: String,
    val uniqueId: String,
    val fileId: Int? = null,
    val sizeBytes: Long,
    val mimeType: String?,
    val durationSeconds: Int?,
    val width: Int?,
    val height: Int?,
)

/**
 * Reference to a document/archive file in TDLib.
 * Used for AUDIOBOOK and RAR_ITEM types.
 *
 * Per contract: remoteId and uniqueId are REQUIRED and stable across sessions.
 * fileId is OPTIONAL and may become stale.
 *
 * @property remoteId Stable remote file identifier (REQUIRED)
 * @property uniqueId Stable unique file identifier (REQUIRED)
 * @property fileId Volatile TDLib-local file ID (OPTIONAL, may become stale)
 * @property sizeBytes File size in bytes
 * @property mimeType MIME type of the document
 * @property fileName Original filename
 */
data class TelegramDocumentRef(
    val remoteId: String,
    val uniqueId: String,
    val fileId: Int? = null,
    val sizeBytes: Long,
    val mimeType: String?,
    val fileName: String?,
)

/**
 * Reference to an image file in TDLib.
 *
 * Per contract: remoteId and uniqueId are REQUIRED and stable across sessions.
 * fileId is OPTIONAL and may become stale.
 *
 * @property remoteId Stable remote file identifier (REQUIRED)
 * @property uniqueId Stable unique file identifier (REQUIRED)
 * @property fileId Volatile TDLib-local file ID (OPTIONAL, may become stale)
 * @property width Image width in pixels
 * @property height Image height in pixels
 * @property sizeBytes File size in bytes
 */
data class TelegramImageRef(
    val remoteId: String,
    val uniqueId: String,
    val fileId: Int? = null,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

// =============================================================================
// Metadata DTO
// =============================================================================

/**
 * Extracted metadata for a Telegram item.
 *
 * Per contract Section 2.1 (Finalized Design Decisions):
 * - isAdult is determined ONLY via AdultHeuristics
 * - FSK and genres are for display only, NOT used for adult classification
 *
 * @property title Display title
 * @property originalTitle Original language title
 * @property year Release year
 * @property lengthMinutes Runtime in minutes
 * @property fsk German age rating (for display only, NOT used for isAdult)
 * @property productionCountry Production country
 * @property collection Film series/collection name
 * @property director Director name
 * @property tmdbRating TMDb rating value
 * @property genres List of genres (for display only, NOT used for isAdult)
 * @property tmdbUrl TMDb URL for deep linking
 * @property isAdult True if content is adult (determined by AdultHeuristics only)
 */
data class TelegramMetadata(
    val title: String?,
    val originalTitle: String?,
    val year: Int?,
    val lengthMinutes: Int?,
    val fsk: Int?,
    val productionCountry: String?,
    val collection: String?,
    val director: String?,
    val tmdbRating: Double?,
    val genres: List<String>,
    val tmdbUrl: String?,
    val isAdult: Boolean,
)

// =============================================================================
// TelegramItem
// =============================================================================

/**
 * Normalized domain item representing a Telegram media item.
 *
 * Key invariants per contract:
 * - For MOVIE/SERIES_EPISODE/CLIP: videoRef is non-null, documentRef is null
 * - For AUDIOBOOK/RAR_ITEM: documentRef is non-null, videoRef is null
 * - For POSTER_ONLY: both videoRef and documentRef are null
 * - Grouping is based on 120-second time windows (canonical)
 *
 * @property chatId ID of the chat containing this item
 * @property anchorMessageId Message ID of the anchor (video or primary content)
 * @property type Classification of this item
 * @property videoRef Video file reference (for MOVIE/SERIES_EPISODE/CLIP)
 * @property documentRef Document file reference (for AUDIOBOOK/RAR_ITEM)
 * @property posterRef Poster image reference (aspect ratio ≤ 0.85)
 * @property backdropRef Backdrop image reference (aspect ratio ≥ 1.6)
 * @property textMessageId Message ID of associated text (if any)
 * @property photoMessageId Message ID of associated photo (if any)
 * @property createdAtIso ISO 8601 timestamp of creation
 * @property metadata Extracted content metadata
 */
data class TelegramItem(
    val chatId: Long,
    val anchorMessageId: Long,
    val type: TelegramItemType,
    val videoRef: TelegramMediaRef?,
    val documentRef: TelegramDocumentRef?,
    val posterRef: TelegramImageRef?,
    val backdropRef: TelegramImageRef?,
    val textMessageId: Long?,
    val photoMessageId: Long?,
    val createdAtIso: String,
    val metadata: TelegramMetadata,
) {
    init {
        // Validate type-ref consistency
        when (type) {
            TelegramItemType.MOVIE,
            TelegramItemType.SERIES_EPISODE,
            TelegramItemType.CLIP,
            -> require(documentRef == null) {
                "Video types must not have documentRef"
            }
            TelegramItemType.AUDIOBOOK,
            TelegramItemType.RAR_ITEM,
            -> require(videoRef == null) {
                "Document types must not have videoRef"
            }
            TelegramItemType.POSTER_ONLY -> {
                require(videoRef == null && documentRef == null) {
                    "POSTER_ONLY must not have videoRef or documentRef"
                }
            }
        }
    }
}

// =============================================================================
// Message Block (Grouping)
// =============================================================================

/**
 * A group of messages within the same chat that belong to a single content item.
 *
 * Per contract Section 6.1:
 * - Messages must all share the same chatId
 * - Messages are typically within a 120-second time window
 * - Blocks are built from messages sorted descending by date
 */
data class MessageBlock(
    val chatId: Long,
    val messages: List<ExportMessage>,
)
