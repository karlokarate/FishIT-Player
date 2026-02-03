package com.fishit.player.core.catalogsync.enhanced

import com.fishit.player.core.catalogsync.SyncPhase
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.pipeline.xtream.catalog.XtreamItemKind

/**
 * Immutable state container for Enhanced Sync - eliminates distributed mutable state.
 *
 * **Cyclomatic Complexity: ≤3**
 * - withPhase: 1
 * - addToBatch: 3 (when branches)
 * - clearBatch: 3 (when branches)
 *
 * @property itemsDiscovered Total items discovered from pipeline
 * @property itemsPersisted Total items persisted to database
 * @property currentPhase Current sync phase (nullable)
 * @property startTimeMs Sync start timestamp
 * @property catalogBatch Batch for VOD/Movies
 * @property seriesBatch Batch for Series/Episodes
 * @property liveBatch Batch for Live channels
 */
data class EnhancedSyncState(
    val itemsDiscovered: Long = 0,
    val itemsPersisted: Long = 0,
    val currentPhase: SyncPhase? = null,
    val startTimeMs: Long = System.currentTimeMillis(),
    val catalogBatch: List<RawMediaMetadata> = emptyList(),
    val seriesBatch: List<RawMediaMetadata> = emptyList(),
    val liveBatch: List<RawMediaMetadata> = emptyList(),
) {
    /**
     * Increment discovered count
     */
    fun withDiscovered(count: Long) = copy(itemsDiscovered = itemsDiscovered + count)

    /**
     * Increment persisted count
     */
    fun withPersisted(count: Long) = copy(itemsPersisted = itemsPersisted + count)

    /**
     * Update current phase
     *
     * **CC: 1**
     */
    fun withPhase(phase: SyncPhase) = copy(currentPhase = phase)

    /**
     * Add item to appropriate batch based on content kind
     *
     * **CC: 3** (when branches)
     */
    fun addToBatch(
        kind: XtreamItemKind,
        item: RawMediaMetadata,
    ): EnhancedSyncState =
        when (kind) {
            XtreamItemKind.LIVE -> copy(liveBatch = liveBatch + item)
            XtreamItemKind.SERIES, XtreamItemKind.EPISODE -> copy(seriesBatch = seriesBatch + item)
            else -> copy(catalogBatch = catalogBatch + item)
        }

    /**
     * Clear specific batch
     *
     * **CC: 3** (when branches)
     */
    fun clearBatch(kind: XtreamItemKind): EnhancedSyncState =
        when (kind) {
            XtreamItemKind.LIVE -> copy(liveBatch = emptyList())
            XtreamItemKind.SERIES, XtreamItemKind.EPISODE -> copy(seriesBatch = emptyList())
            else -> copy(catalogBatch = emptyList())
        }

    /**
     * Sync duration in milliseconds
     */
    val durationMs: Long
        get() = System.currentTimeMillis() - startTimeMs
}
