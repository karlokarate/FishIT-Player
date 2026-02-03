package com.fishit.player.core.catalogsync.enhanced

import com.fishit.player.core.catalogsync.CatalogSyncContract.SyncStatus
import com.fishit.player.core.catalogsync.EnhancedSyncConfig
import com.fishit.player.core.catalogsync.SyncConfig
import com.fishit.player.core.catalogsync.SyncPerfMetrics
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent

/**
 * Strategy Pattern for Event-Handling
 *
 * Each handler has CC ≤ 6
 *
 * **CC: 0** (interface)
 */
sealed interface XtreamEventHandler<E : XtreamCatalogEvent> {
    suspend fun handle(
        event: E,
        state: EnhancedSyncState,
        context: EnhancedSyncContext,
    ): EnhancedSyncResult
}

/**
 * Result-Typ instead of Exception-based control flow
 *
 * **CC: 0** (sealed class hierarchy)
 */
sealed class EnhancedSyncResult {
    data class Continue(
        val state: EnhancedSyncState,
        val emit: SyncStatus? = null,
    ) : EnhancedSyncResult()

    data class Complete(val status: SyncStatus.Completed) : EnhancedSyncResult()

    data class Cancel(val status: SyncStatus.Cancelled) : EnhancedSyncResult()

    data class Error(val status: SyncStatus.Error) : EnhancedSyncResult()
}

/**
 * Shared Context for all handlers
 *
 * @property config Enhanced sync configuration
 * @property batchRouter Batch flush decision logic
 * @property metrics Performance metrics tracker
 * @property syncConfig Base sync configuration
 * @property persistCatalog Catalog persistence function
 * @property persistLive Live channel persistence function
 *
 * **CC: 0** (data class)
 */
data class EnhancedSyncContext(
    val config: EnhancedSyncConfig,
    val batchRouter: EnhancedBatchRouter,
    val metrics: SyncPerfMetrics,
    val syncConfig: SyncConfig,
    val persistCatalog: suspend (List<RawMediaMetadata>) -> Unit,
    val persistLive: suspend (List<RawMediaMetadata>) -> Unit,
)
