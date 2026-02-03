package com.fishit.player.core.catalogsync.enhanced

import com.fishit.player.core.catalogsync.EnhancedSyncConfig
import com.fishit.player.core.catalogsync.SyncPhase
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.pipeline.xtream.catalog.XtreamItemKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides batch flush per Content-Type
 *
 * **Cyclomatic Complexity: ≤4**
 * - getBatchAndLimit: 3 (when branches)
 * - flushIfNeeded: 1 (size check)
 * - flushBatch: 1 (kind check)
 *
 * Separates batch size decisions from persistence logic.
 *
 * **Config Handling:**
 * Config is passed via EnhancedSyncContext (per-call) instead of constructor injection
 * to support different batch sizes per sync operation (QUICK_SYNC, FIRETV_SAFE, etc.)
 */
@Singleton
class EnhancedBatchRouter
    @Inject
    constructor() {
        /**
         * Flush batch if size limit reached
         *
         * **CC: 1** (size check)
         *
         * @return Pair of (updated state, persisted count)
         */
        suspend fun flushIfNeeded(
            state: EnhancedSyncState,
            kind: XtreamItemKind,
            context: EnhancedSyncContext,
        ): Pair<EnhancedSyncState, Int> {
            val (batch, limit) = getBatchAndLimit(state, kind, context)

            if (batch.size < limit) return state to 0

            return flushBatch(state, kind, context)
        }

        /**
         * Force flush batch regardless of size
         *
         * **CC: 1** (kind check for persistence routing)
         */
        suspend fun forceFlush(
            kind: XtreamItemKind,
            batch: List<RawMediaMetadata>,
            context: EnhancedSyncContext,
        ) {
            if (batch.isEmpty()) return

            if (kind == XtreamItemKind.LIVE) {
                context.persistLive(batch)
            } else {
                context.persistCatalog(batch)
            }
        }

        /**
         * Get batch and size limit for content kind
         *
         * **CC: 3** (when branches)
         *
         * Config comes from context (per-call) to support different batch sizes
         */
        private fun getBatchAndLimit(
            state: EnhancedSyncState,
            kind: XtreamItemKind,
            context: EnhancedSyncContext,
        ): Pair<List<RawMediaMetadata>, Int> =
            when (kind) {
                XtreamItemKind.LIVE -> state.liveBatch to context.config.liveConfig.batchSize
                XtreamItemKind.SERIES, XtreamItemKind.EPISODE -> state.seriesBatch to context.config.seriesConfig.batchSize
                else -> state.catalogBatch to context.config.moviesConfig.batchSize
            }

        /**
         * Flush batch and update state
         *
         * **CC: 1** (kind check)
         */
        private suspend fun flushBatch(
            state: EnhancedSyncState,
            kind: XtreamItemKind,
            context: EnhancedSyncContext,
        ): Pair<EnhancedSyncState, Int> {
            val (batch, _) = getBatchAndLimit(state, kind, context)

            if (kind == XtreamItemKind.LIVE) {
                context.persistLive(batch)
            } else {
                context.persistCatalog(batch)
            }

            return state.clearBatch(kind) to batch.size
        }

        /**
         * Map XtreamItemKind to SyncPhase
         *
         * **CC: 3** (when branches)
         */
        fun mapToPhase(kind: XtreamItemKind): SyncPhase =
            when (kind) {
                XtreamItemKind.LIVE -> SyncPhase.LIVE
                XtreamItemKind.SERIES -> SyncPhase.SERIES
                XtreamItemKind.EPISODE -> SyncPhase.EPISODES
                else -> SyncPhase.MOVIES
            }
    }
