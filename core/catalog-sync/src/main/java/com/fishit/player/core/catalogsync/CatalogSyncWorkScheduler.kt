package com.fishit.player.core.catalogsync

/**
 * Schedules catalog synchronization work (typically via WorkManager).
 *
 * This is triggered by app bootstraps once authentication/connectivity
 * is ready, avoiding direct orchestration from the UI layer.
 */
interface CatalogSyncWorkScheduler {
    /**
     * Enqueue the default/automatic catalog sync policy.
     */
    fun enqueueAutoSync()

    /**
     * Enqueue an on-demand sync with the most aggressive settings available.
     */
    fun enqueueExpertSyncNow()
}
