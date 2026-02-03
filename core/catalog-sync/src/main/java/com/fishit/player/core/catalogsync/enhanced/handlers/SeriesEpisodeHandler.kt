package com.fishit.player.core.catalogsync.enhanced.handlers

import com.fishit.player.core.catalogsync.SyncStatus
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncContext
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncResult
import com.fishit.player.core.catalogsync.enhanced.EnhancedSyncState
import com.fishit.player.core.catalogsync.enhanced.XtreamEventHandler
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for Series Episode Events
 *
 * **Cyclomatic Complexity: ≤3**
 * - handleComplete: 0 (no conditionals)
 * - handleFailed: 0 (no conditionals)
 * - Total: 0
 */
@Singleton
class SeriesEpisodeHandler
    @Inject
    constructor() {
        /**
         * Handle SeriesEpisodeComplete event
         *
         * **CC: 0** (no conditionals)
         */
        suspend fun handleComplete(
            event: XtreamCatalogEvent.SeriesEpisodeComplete,
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): EnhancedSyncResult {
            val emit =
                SyncStatus.SeriesEpisodeComplete(
                    source = "XTREAM",
                    seriesId = event.seriesId,
                    episodeCount = event.episodeCount,
                )

            return EnhancedSyncResult.Continue(state, emit)
        }

        /**
         * Handle SeriesEpisodeFailed event
         *
         * **CC: 0** (no conditionals)
         */
        suspend fun handleFailed(
            event: XtreamCatalogEvent.SeriesEpisodeFailed,
            state: EnhancedSyncState,
            context: EnhancedSyncContext,
        ): EnhancedSyncResult {
            UnifiedLog.w(
                TAG,
                "Series ${event.seriesId} episode load failed: ${event.reason}",
            )

            // Continue sync - don't fail entire operation
            return EnhancedSyncResult.Continue(state)
        }

        companion object {
            private const val TAG = "SeriesEpisodeHandler"
        }
    }
