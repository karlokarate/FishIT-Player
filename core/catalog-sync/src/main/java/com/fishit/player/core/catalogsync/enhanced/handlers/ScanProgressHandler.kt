package com.fishit.player.core.catalogsync.enhanced.handlers

import com.fishit.player.core.catalogsync.CatalogSyncContract.SyncStatus
import com.fishit.player.core.catalogsync.SyncPhase
import com.fishit.player.core.catalogsync.enhanced.EnhancedBatchRouter
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncContext
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncResult
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncState
import com.fishit.player.core.catalogsync.enhanced.XtreamEventHandler
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import com.fishit.player.pipeline.xtream.catalog.XtreamScanPhase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for ScanProgress Events
 *
 * **Cyclomatic Complexity: ≤3**
 * - handle: 1 (phase change check)
 * - mapPhase: 3 (when branches)
 * - Total: 4
 *
 * Complexity drivers:
 * - Phase tracking to avoid duplicate metrics
 * - Phase mapping: 3 cases
 */
@Singleton
class ScanProgressHandler
    @Inject
    constructor(
        private val batchRouter: EnhancedBatchRouter,
    ) : XtreamEventHandler<XtreamCatalogEvent.ScanProgress> {
        /**
         * Handle ScanProgress event
         *
         * **CC: 1** (phase change check)
         */
        override suspend fun handle(
            event: XtreamCatalogEvent.ScanProgress,
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): EnhancedSyncResult {
            val syncPhase = mapPhase(event.currentPhase)

            // Only start metrics when phase changes
            val newState =
                if (syncPhase != state.currentPhase) {
                    context.metrics.startPhase(syncPhase)
                    state.withPhase(syncPhase)
                } else {
                    state
                }

            val emit =
                SyncStatus.InProgress(
                    source = "XTREAM",
                    itemsDiscovered =
                        (
                            event.vodCount +
                                event.seriesCount +
                                event.episodeCount +
                                event.liveCount
                        ).toLong(),
                    itemsPersisted = state.itemsPersisted,
                    currentPhase = event.currentPhase.name,
                )

            return EnhancedSyncResult.Continue(newState, emit)
        }

        /**
         * Map XtreamScanPhase to SyncPhase
         *
         * **CC: 3** (when branches)
         */
        private fun mapPhase(phase: XtreamScanPhase): SyncPhase =
            when (phase) {
                XtreamScanPhase.LIVE -> SyncPhase.LIVE
                XtreamScanPhase.VOD -> SyncPhase.MOVIES
                XtreamScanPhase.SERIES -> SyncPhase.SERIES
                XtreamScanPhase.EPISODES -> SyncPhase.EPISODES
            }
    }
