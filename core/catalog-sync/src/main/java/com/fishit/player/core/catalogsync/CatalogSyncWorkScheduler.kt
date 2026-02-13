package com.fishit.player.core.catalogsync

/**
 * Schedules catalog synchronization work (typically via WorkManager).
 *
 * This is triggered by app bootstraps once authentication/connectivity
 * is ready, avoiding direct orchestration from the UI layer.
 *
 * ## Sync Modes
 * 
 * **Initial Sync (AUTO/EXPERT_NOW):**
 * - Full catalog scan on first launch or when user triggers refresh
 * - Scans all VOD, Series, Episodes, Live channels
 * - High traffic, runs infrequently
 *
 * **Incremental Sync (INCREMENTAL):**
 * - Lightweight check for newly added items
 * - Only fetches items where `added > lastSyncTimestamp`
 * - Low traffic, runs every 2 hours
 * - Triggered automatically via periodic work
 *
 * SSOT Contract (CATALOG_SYNC_WORKERS_CONTRACT_V2):
 * - uniqueWorkName = "catalog_sync_global"
 * - All sync triggers MUST go through this scheduler
 * - No UI/ViewModel may call sync services directly
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
     * Enqueue an incremental sync that only fetches newly added items.
     * 
     * This is a lightweight operation that:
     * - Compares item counts to detect changes
     * - Only fetches items where `added > lastSyncTimestamp`
     * - Skips unchanged sources entirely
     * 
     * Best for periodic background updates (every 2 hours).
     * Uses ExistingWorkPolicy.KEEP (won't interrupt running sync).
     */
    fun enqueueIncrementalSync()
    
    /**
     * Schedule periodic incremental sync.
     * 
     * Sets up a recurring background job that runs every [intervalHours]
     * to check for newly added content. This is battery-efficient and
     * only runs when:
     * - Device is connected to network
     * - Battery is not low
     * 
     * @param intervalHours Hours between sync attempts (min: 1, default: 2)
     */
    fun schedulePeriodicSync(intervalHours: Long = 2L)
    
    /**
     * Cancel any scheduled periodic sync.
     * Does not affect one-time sync requests.
     */
    fun cancelPeriodicSync()

    /**
     * Cancel any currently running or enqueued catalog sync work.
     */
    fun cancelSync()
}
