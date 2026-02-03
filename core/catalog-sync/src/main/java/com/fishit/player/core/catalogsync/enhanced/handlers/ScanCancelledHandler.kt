package com.fishit.player.core.catalogsync.enhanced.handlers

import com.fishit.player.core.catalogsync.SyncStatus
import com.fishit.player.core.catalogsync.enhanced.EnhancedBatchRouter
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncContext
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncResult
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncState
import com.fishit.player.core.catalogsync.enhanced.XtreamEventHandler
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import com.fishit.player.pipeline.xtream.catalog.XtreamItemKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for ScanCancelled Events
 *
 * **Cyclomatic Complexity: ≤4**
 * - handle: 2 (for loop + isEmpty check)
 * - Total: 2
 */
@Singleton
class ScanCancelledHandler
    @Inject
    constructor(
        private val batchRouter: EnhancedBatchRouter,
    ) : XtreamEventHandler<XtreamCatalogEvent.ScanCancelled> {
        /**
         * Handle ScanCancelled event
         *
         * **CC: 2** (for loop + isEmpty check)
         */
        override suspend fun handle(
            event: XtreamCatalogEvent.ScanCancelled,
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): EnhancedSyncResult {
            // Flush all remaining batches
            var finalState = state

            for ((kind, batch) in
                listOf(
                    XtreamItemKind.VOD to state.catalogBatch,
                    XtreamItemKind.SERIES to state.seriesBatch,
                    XtreamItemKind.LIVE to state.liveBatch,
                )) {
                if (batch.isNotEmpty()) {
                    batchRouter.forceFlush(kind, batch, context)
                    finalState = finalState.withPersisted(batch.size.toLong()).clearBatch(kind)
                }
            }

            return EnhancedSyncResult.Cancel(
                SyncStatus.Cancelled("XTREAM", finalState.itemsPersisted),
            )
        }
    }
