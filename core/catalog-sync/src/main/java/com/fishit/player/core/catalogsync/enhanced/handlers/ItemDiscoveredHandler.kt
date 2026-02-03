package com.fishit.player.core.catalogsync.enhanced.handlers

import com.fishit.player.core.catalogsync.SyncStatus
import com.fishit.player.core.catalogsync.enhanced.EnhancedBatchRouter
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncContext
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncResult
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncState
import com.fishit.player.core.catalogsync.enhanced.XtreamEventHandler
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for ItemDiscovered Events
 *
 * **Cyclomatic Complexity: ≤6**
 * - handle: 2 (flushIfNeeded check + progress check)
 * - shouldEmitProgress: 1 (modulo check)
 * - Total: 3
 *
 * Complexity drivers:
 * - Batch flush decision: delegated to BatchRouter
 * - Progress emit decision: 1 conditional
 */
@Singleton
class ItemDiscoveredHandler
    @Inject
    constructor(
        private val batchRouter: EnhancedBatchRouter,
    ) : XtreamEventHandler<XtreamCatalogEvent.ItemDiscovered> {
        /**
         * Handle ItemDiscovered event
         *
         * **CC: 2** (flush check + emit check)
         */
        override suspend fun handle(
            event: XtreamCatalogEvent.ItemDiscovered,
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): EnhancedSyncResult {
            // Update state with discovered item
            val newState =
                state
                    .withDiscovered(1)
                    .addToBatch(event.item.kind, event.item.raw)

            // Record metrics
            val phase = batchRouter.mapToPhase(event.item.kind)
            context.metrics.recordItemsDiscovered(phase, 1)

            // Delegate batch flush decision to BatchRouter
            val (flushedState, persistedCount) =
                batchRouter.flushIfNeeded(
                    newState,
                    event.item.kind,
                    context,
                )

            // Record persistence if any
            if (persistedCount > 0) {
                context.metrics.recordPersist(phase, 0, persistedCount, isTimeBased = false)
            }

            val finalState = flushedState.withPersisted(persistedCount.toLong())

            // Progress emit decision
            val emit =
                if (shouldEmitProgress(finalState, context)) {
                    createProgressStatus(finalState)
                } else {
                    null
                }

            return EnhancedSyncResult.Continue(finalState, emit)
        }

        /**
         * Check if progress should be emitted
         *
         * **CC: 1** (modulo check)
         */
        private fun shouldEmitProgress(
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): Boolean = state.itemsDiscovered % context.config.emitProgressEvery == 0L

        /**
         * Create progress status
         */
        private fun createProgressStatus(state: EnhancedSyncState) =
            SyncStatus.InProgress(
                source = "XTREAM",
                itemsDiscovered = state.itemsDiscovered,
                itemsPersisted = state.itemsPersisted,
            )
    }
