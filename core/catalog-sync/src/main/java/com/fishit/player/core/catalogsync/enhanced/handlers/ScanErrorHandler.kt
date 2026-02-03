package com.fishit.player.core.catalogsync.enhanced.handlers

import com.fishit.player.core.catalogsync.CatalogSyncContract.SyncStatus
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncContext
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncResult
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncState
import com.fishit.player.core.catalogsync.enhanced.XtreamEventHandler
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for ScanError Events
 *
 * **Cyclomatic Complexity: ≤3**
 * - handle: 0 (no conditionals)
 * - Total: 0
 */
@Singleton
class ScanErrorHandler
    @Inject
    constructor() : XtreamEventHandler<XtreamCatalogEvent.ScanError> {
        /**
         * Handle ScanError event
         *
         * **CC: 0** (no conditionals)
         */
        override suspend fun handle(
            event: XtreamCatalogEvent.ScanError,
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): EnhancedSyncResult {
            UnifiedLog.e(
                TAG,
                "Enhanced sync error: ${event.reason} - ${event.message}",
            )

            return EnhancedSyncResult.Error(
                SyncStatus.Error(
                    source = "XTREAM",
                    reason = event.reason,
                    message = event.message,
                    throwable = event.throwable,
                ),
            )
        }

        companion object {
            private const val TAG = "ScanErrorHandler"
        }
    }
