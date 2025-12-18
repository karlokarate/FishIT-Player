package com.fishit.player.infra.work

import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder implementation that will route catalog sync triggers through WorkManager.
 *
 * Currently performs a no-op aside from logging; WorkManager integration will be
 * wired when background sync is implemented.
 */
@Singleton
class DefaultCatalogSyncWorkScheduler @Inject constructor() : CatalogSyncWorkScheduler {

    override fun enqueueAutoSync() {
        UnifiedLog.i(TAG) { "enqueueAutoSync() invoked (no-op placeholder)" }
    }

    override fun enqueueExpertSyncNow() {
        UnifiedLog.i(TAG) { "enqueueExpertSyncNow() invoked (no-op placeholder)" }
    }

    private companion object {
        private const val TAG = "CatalogSyncWorkScheduler"
    }
}
