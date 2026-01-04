package com.fishit.player.infra.transport.telegram

/**
 * Typed interface for Telegram thumbnail fetching.
 *
 * This is part of the v2 Transport API Surface. Integrates with Coil 3 image loading for efficient
 * thumbnail display.
 *
 * ## v2 remoteId-First Architecture
 *
 * This interface follows the **remoteId-first design** defined in
 * `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`.
 *
 * ### Key Points:
 * - [TgThumbnailRef] contains only `remoteId` (stable identifier)
 * - `fileId` is resolved at runtime via `getRemoteFile(remoteId)`
 * - No `fileId` or `uniqueId` stored in persistence
 *
 * **Bounded Error Tracking:** To prevent log spam, implementations should track failed remoteIds in
 * a bounded LRU set and skip repeated fetch attempts.
 *
 * **Implementation:** [TelegramThumbFetcherImpl] in `infra/transport-telegram/imaging/`
 *
 * @see TelegramFileClient for general file downloads
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 */
interface TelegramThumbFetcher {
    /**
     * Fetch thumbnail for a Telegram file.
     *
     * Downloads the thumbnail if not cached, returns local path.
     *
     * **Resolution Flow:**
     * 1. Resolve `remoteId` → `fileId` via `getRemoteFile(remoteId)`
     * 2. Check if already downloaded (TDLib cache)
     * 3. If not, call `downloadFile(fileId, priority)`
     * 4. Return local path
     *
     * **Priority:** Thumbnails use medium priority (16) to avoid blocking playback downloads but
     * still load quickly for UI.
     *
     * @param thumbRef Reference to the thumbnail (remoteId, dimensions)
     * @return Local file path to thumbnail, or null if unavailable
     */
    suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String?

    /**
     * Check if thumbnail is already cached locally.
     *
     * Resolves remoteId → fileId first, then checks TDLib cache.
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
    suspend fun clearFailedCache()
}

/**
 * Reference to a Telegram thumbnail.
 *
 * ## v2 remoteId-First Design
 *
 * This class follows the **remoteId-first architecture** defined in
 * `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`.
 *
 * ### Key Points:
 * - `remoteId` is the **only** identifier stored
 * - `fileId` is resolved at runtime via `getRemoteFile(remoteId)`
 * - No `uniqueId` needed (no API to resolve it back)
 *
 * @property remoteId Stable remote identifier (cross-session stable)
 * @property width Thumbnail width in pixels
 * @property height Thumbnail height in pixels
 * @property format Thumbnail format (jpeg, webp, etc.)
 */
data class TgThumbnailRef(
    /** Stable remote identifier - use getRemoteFile(remoteId) to get fileId */
    val remoteId: String,
    /** Thumbnail width */
    val width: Int,
    /** Thumbnail height */
    val height: Int,
    /** Thumbnail format (jpeg, webp, etc.) */
    val format: String = "jpeg",
)
