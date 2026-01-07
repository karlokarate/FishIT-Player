package com.fishit.player.core.catalogsync

import com.fishit.player.core.model.SourceType

/**
 * Scheduler for canonical linking backlog workers.
 *
 * **Purpose:** Schedule background workers to process items that were persisted without
 * canonical linking during fast initial sync (hot path relief).
 *
 * **Task 2: Hot Path Entlastung**
 * When `SyncConfig.enableCanonicalLinking=false`, items are stored raw only for maximum speed.
 * This scheduler automatically enqueues backlog workers to complete canonical unification later.
 *
 * **Architecture:**
 * - Called automatically after fast initial sync completes
 * - Enqueues workers per source type (Xtream, Telegram)
 * - Respects runtime guards (battery, network)
 * - Batched processing for efficiency
 *
 * @see CanonicalLinkingBacklogWorker for worker implementation
 */
interface CanonicalLinkingScheduler {
    /**
     * Schedule backlog processing for a specific source.
     *
     * @param sourceType The source type to process (XTREAM, TELEGRAM)
     * @param estimatedItemCount Estimated number of unlinked items (for batch sizing)
     * @param delayMs Optional delay before starting (default: 5 seconds to let UI stabilize)
     */
    fun scheduleBacklogProcessing(
        sourceType: SourceType,
        estimatedItemCount: Long = 0,
        delayMs: Long = DEFAULT_DELAY_MS,
    )

    /**
     * Cancel any pending backlog processing for a source.
     *
     * @param sourceType The source type to cancel
     */
    fun cancelBacklogProcessing(sourceType: SourceType)

    companion object {
        /** Default delay before starting backlog processing (5 seconds) */
        const val DEFAULT_DELAY_MS = 5_000L

        /** Default batch size for backlog processing */
        const val DEFAULT_BATCH_SIZE = 100
    }
}
