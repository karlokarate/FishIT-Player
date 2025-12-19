package com.fishit.player.core.catalogsync

/**
 * Schedules catalog synchronization work (typically via WorkManager).
 *
 * This is triggered by app bootstraps once authentication/connectivity
 * is ready, avoiding direct orchestration from the UI layer.
 *
 * SSOT Contract (CATALOG_SYNC_WORKERS_CONTRACT_V2):
 * - uniqueWorkName = "catalog_sync_global"
 * - All sync triggers MUST go through this scheduler
 * - No UI/ViewModel may call CatalogSyncService directly
 */
interface CatalogSyncWorkScheduler {
    /**
     * Enqueue the default/automatic catalog sync policy.
     * Uses ExistingWorkPolicy.KEEP (won't interrupt running sync).
     */
    fun enqueueAutoSync()

    /**
     * Enqueue an on-demand sync with the most aggressive settings available.
     * Uses ExistingWorkPolicy.KEEP (won't interrupt running sync).
     */
    fun enqueueExpertSyncNow()

    /**
     * Enqueue a force rescan that replaces any running sync.
     * Uses ExistingWorkPolicy.REPLACE (cancels running sync and restarts).
     */
    fun enqueueForceRescan()

    /**
     * Cancel any currently running or enqueued catalog sync work.
     */
    fun cancelSync()
}
