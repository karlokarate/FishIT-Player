package com.fishit.player.core.catalogsync.enhanced.handlers

import com.fishit.player.core.catalogsync.CatalogSyncContract.SyncStatus
import com.fishit.player.core.catalogsync.SyncPhase
import com.fishit.player.core.catalogsync.enhanced.EnhancedBatchRouter
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncContext
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncResult
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncState
import com.fishit.player.core.catalogsync.enhanced.XtreamEventHandler
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import com.fishit.player.pipeline.xtream.catalog.XtreamItemKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for Scan-Completion
 *
 * **Cyclomatic Complexity: ≤5**
 * - handle: 2 (for loop + isEmpty check within loop)
 * - Total: 2
 *
 * Complexity drivers:
 * - Final flush loop: iterates 3 phases with isEmpty check
 * - Metrics recording: simple calls
 */
@Singleton
class ScanCompletedHandler
    @Inject
    constructor(
        private val batchRouter: EnhancedBatchRouter,
    ) : XtreamEventHandler<XtreamCatalogEvent.ScanCompleted> {
        /**
         * Handle ScanCompleted event
         *
         * **CC: 2** (for loop + isEmpty check)
         */
        override suspend fun handle(
            event: XtreamCatalogEvent.ScanCompleted,
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): EnhancedSyncResult {
            // Flush all remaining batches
            var finalState = state
            var totalPersisted = 0

            // Force flush all batches
            for ((kind, batch) in
                listOf(
                    XtreamItemKind.VOD to state.catalogBatch,
                    XtreamItemKind.SERIES to state.seriesBatch,
                    XtreamItemKind.LIVE to state.liveBatch,
                )) {
                if (batch.isNotEmpty()) {
                    batchRouter.forceFlush(kind, batch, context)
                    totalPersisted += batch.size
                    finalState = finalState.clearBatch(kind)

                    // Record metrics
                    val phase = batchRouter.mapToPhase(kind)
                    context.metrics.recordPersist(phase, 0, batch.size, isTimeBased = false)
                    context.metrics.endPhase(phase)
                }
            }

            finalState = finalState.withPersisted(totalPersisted.toLong())

            return EnhancedSyncResult.Complete(
                SyncStatus.Completed(
                    source = "XTREAM",
                    totalItems = finalState.itemsPersisted,
                    durationMs = finalState.durationMs,
                ),
            )
        }
    }
