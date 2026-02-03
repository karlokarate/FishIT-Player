package com.fishit.player.core.catalogsync.enhanced

import com.fishit.player.core.catalogsync.enhanced.handlers.ItemDiscoveredHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.ScanCancelledHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.ScanCompletedHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.ScanErrorHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.ScanProgressHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.SeriesEpisodeHandler
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for dispatching events to appropriate handlers
 *
 * **Cyclomatic Complexity: ≤8**
 * - handle: 8 (when branches for each event type)
 *
 * This replaces the large when-block in the original implementation.
 * Each event type is dispatched to its dedicated handler.
 */
@Singleton
class XtreamEventHandlerRegistry
    @Inject
    constructor(
        private val itemDiscoveredHandler: ItemDiscoveredHandler,
        private val scanCompletedHandler: ScanCompletedHandler,
        private val scanProgressHandler: ScanProgressHandler,
        private val scanCancelledHandler: ScanCancelledHandler,
        private val scanErrorHandler: ScanErrorHandler,
        private val seriesEpisodeHandler: SeriesEpisodeHandler,
    ) {
        /**
         * Dispatch event to appropriate handler
         *
         * **CC: 8** (when branches)
         */
        suspend fun handle(
            event: XtreamCatalogEvent,
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): EnhancedSyncResult =
            when (event) {
                is XtreamCatalogEvent.ItemDiscovered -> itemDiscoveredHandler.handle(event, state, context)
                is XtreamCatalogEvent.ScanCompleted -> scanCompletedHandler.handle(event, state, context)
                is XtreamCatalogEvent.ScanProgress -> scanProgressHandler.handle(event, state, context)
                is XtreamCatalogEvent.ScanCancelled -> scanCancelledHandler.handle(event, state, context)
                is XtreamCatalogEvent.ScanError -> scanErrorHandler.handle(event, state, context)
                is XtreamCatalogEvent.ScanStarted -> EnhancedSyncResult.Continue(state) // No-op
                is XtreamCatalogEvent.SeriesEpisodeComplete -> seriesEpisodeHandler.handleComplete(event, state, context)
                is XtreamCatalogEvent.SeriesEpisodeFailed -> seriesEpisodeHandler.handleFailed(event, state, context)
            }
    }
