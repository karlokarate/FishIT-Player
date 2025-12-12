package com.fishit.player.infra.transport.telegram

/**
 * Typed interface for Telegram thumbnail fetching.
 *
 * This is part of the v2 Transport API Surface. Integrates with Coil 3
 * image loading for efficient thumbnail display.
 *
 * **v2 Architecture:**
 * - Transport handles TDLib thumbnail download
 * - Returns local file path for Coil to load
 * - Uses remoteId-first design for stable caching
 *
 * **Bounded Error Tracking:**
 * To prevent log spam, implementations should track failed remoteIds
 * in a bounded LRU set and skip repeated fetch attempts.
 *
 * **Implementation:** [TelegramThumbFetcherImpl] in `infra/transport-telegram/imaging/`
 *
 * @see TelegramFileClient for general file downloads
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
interface TelegramThumbFetcher {

    /**
     * Fetch thumbnail for a Telegram file.
     *
     * Downloads the thumbnail if not cached, returns local path.
     *
     * **Priority:** Thumbnails use medium priority (16) to avoid
     * blocking playback downloads but still load quickly for UI.
     *
     * @param thumbRef Reference to the thumbnail (fileId, remoteId, etc.)
     * @return Local file path to thumbnail, or null if unavailable
     */
    suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String?

    /**
     * Check if thumbnail is already cached locally.
     *
     * @param thumbRef Reference to the thumbnail
     * @return true if available locally without download
     */
    suspend fun isCached(thumbRef: TgThumbnailRef): Boolean

    /**
     * Prefetch thumbnails for a list of items.
     *
     * Used for scroll-ahead prefetching. Lower priority than on-screen items.
     *
     * @param thumbRefs List of thumbnail references to prefetch
     */
    suspend fun prefetch(thumbRefs: List<TgThumbnailRef>)

    /**
     * Clear the "failed remoteIds" tracking set.
     *
     * Call this when network conditions change or user requests refresh.
     */
    fun clearFailedCache()
}

/**
 * Reference to a Telegram thumbnail.
 *
 * Uses remoteId as the stable identifier. fileId may become stale
 * after TDLib cache eviction.
 */
data class TgThumbnailRef(
    /** TDLib file ID (may be stale) */
    val fileId: Int,
    /** Stable remote identifier */
    val remoteId: String,
    /** Thumbnail width */
    val width: Int,
    /** Thumbnail height */
    val height: Int,
    /** Thumbnail format (jpeg, webp, etc.) */
    val format: String = "jpeg"
)
