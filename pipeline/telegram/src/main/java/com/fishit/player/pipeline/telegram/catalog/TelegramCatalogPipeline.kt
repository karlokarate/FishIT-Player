package com.fishit.player.pipeline.telegram.catalog

import kotlinx.coroutines.flow.Flow

/**
 * Telegram Catalog Pipeline for media discovery.
 *
 * Provides a catalog-scanning API that produces a complete, incrementally updatable media catalog
 * from Telegram chats without performing any work outside the catalog domain.
 *
 * **Design Principles:**
 * - Stateless: No side effects, no downloads, no DB writes
 * - Pure scanning: Only reads TDLib message history
 * - Flow-based: Emits events as items are discovered
 * - Compliance: Follows MEDIA_NORMALIZATION_CONTRACT and TELEGRAM_TDLIB_V2_INTEGRATION
 *
 * **Architecture Boundaries:**
 * - NO TDLib cache management
 * - NO media downloads (thumbnails or files)
 * - NO writes to any DB or index
 * - NO interaction with ExoPlayer or UI
 * - NO title normalization or TMDB lookups (delegated to :core:metadata-normalizer)
 *
 * **Error Handling:**
 * - Auth/connection errors: Emit ScanError and close flow (no auto-fix)
 * - TDLib errors: Log and continue to next chat/message where possible
 * - Cancellation: Gracefully stop and emit ScanCompleted with partial counts
 *
 * @see TelegramCatalogConfig for scan configuration
 * @see TelegramCatalogEvent for emitted events
 */
interface TelegramCatalogPipeline {
    /**
     * Scan Telegram chats for media and emit catalog events.
     *
     * This is a **cold Flow**: each collection starts a fresh scan. The scan is stateless and
     * does not persist any results; consumers must handle item storage if needed.
     *
     * **Behavior:**
     * 1. Check auth state and connection state
     * 2. If not ready/connected: emit ScanError and close
     * 3. Resolve chats to scan (from config or defaults)
     * 4. Emit ScanningInitial
     * 5. For each chat: scan message history, emit ItemDiscovered for media items
     * 6. Emit ScanProgress periodically
     * 7. Emit ScanCompleted or ScanError at end
     *
     * **Threading:**
     * - Safe to call from any coroutine context
     * - Flow collection happens on the caller's context
     * - Internal TDLib operations use IO dispatcher
     *
     * @param config Scan configuration (chat selection, filters, media types).
     * @return Flow of catalog events (items, progress, completion, errors).
     */
    fun scanCatalog(config: TelegramCatalogConfig = TelegramCatalogConfig.DEFAULT): Flow<TelegramCatalogEvent>
}
