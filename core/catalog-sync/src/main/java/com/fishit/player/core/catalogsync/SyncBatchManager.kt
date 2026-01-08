package com.fishit.player.core.catalogsync

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Time-based batch manager for progressive catalog sync.
 *
 * **Key Features:**
 * - Per-phase batch sizing (Live=600, Movies=400, Series=200)
 * - Time-based flush: emit every [flushIntervalMs] even if batch isn't full
 * - Thread-safe via per-phase Mutex
 * - Metrics integration for performance tracking
 *
 * **Progressive UI:**
 * Instead of waiting for large batches to fill, this manager ensures
 * tiles appear within ~1200ms of discovery, providing perceived speed.
 *
 * **Usage:**
 * ```kotlin
 * val manager = SyncBatchManager(config, metrics)
 *
 * // Add items - returns batch if flush needed
 * val toFlush = manager.add(SyncPhase.LIVE, item)
 * if (toFlush != null) {
 *     persistLiveBatch(toFlush)
 * }
 *
 * // Check time-based flush periodically
 * val timeFlush = manager.checkTimeBasedFlush(SyncPhase.LIVE)
 * if (timeFlush != null) {
 *     persistLiveBatch(timeFlush)
 * }
 *
 * // Flush remaining on completion
 * val remaining = manager.flushAll(SyncPhase.LIVE)
 * ```
 */
class SyncBatchManager(
    private val config: EnhancedSyncConfig = EnhancedSyncConfig.DEFAULT,
    private val metrics: SyncPerfMetrics? = null,
) {
    companion object {
        private const val TAG = "SyncBatchManager"
    }

    // Per-phase state
    private data class PhaseBatch(
        val items: MutableList<RawMediaMetadata> = mutableListOf(),
        var lastFlushTimeMs: Long = System.currentTimeMillis(),
    )

    private val batches = ConcurrentHashMap<SyncPhase, PhaseBatch>()
    private val locks = ConcurrentHashMap<SyncPhase, Mutex>()

    private fun getLock(phase: SyncPhase) = locks.getOrPut(phase) { Mutex() }

    private fun getBatch(phase: SyncPhase) = batches.getOrPut(phase) { PhaseBatch() }

    /**
     * Add an item to the appropriate phase batch.
     *
     * @return List to flush if batch is full, null otherwise
     */
    suspend fun add(
        phase: SyncPhase,
        item: RawMediaMetadata,
    ): List<RawMediaMetadata>? =
        getLock(phase).withLock {
            val batch = getBatch(phase)
            batch.items.add(item)

            val batchSize = config.batchSizeFor(phase)
            if (batch.items.size >= batchSize) {
                flushBatchInternal(phase, batch, isTimeBased = false)
            } else {
                null
            }
        }

    /**
     * Add multiple items to a phase batch.
     *
     * @return List to flush if batch exceeds size, null otherwise
     */
    suspend fun addAll(
        phase: SyncPhase,
        items: List<RawMediaMetadata>,
    ): List<RawMediaMetadata>? =
        getLock(phase).withLock {
            val batch = getBatch(phase)
            batch.items.addAll(items)

            val batchSize = config.batchSizeFor(phase)
            if (batch.items.size >= batchSize) {
                flushBatchInternal(phase, batch, isTimeBased = false)
            } else {
                null
            }
        }

    /**
     * Check if time-based flush is needed for a phase.
     *
     * Call this periodically (e.g., every 200-500ms) to ensure
     * progressive UI updates.
     *
     * @return List to flush if time threshold exceeded, null otherwise
     */
    suspend fun checkTimeBasedFlush(phase: SyncPhase): List<RawMediaMetadata>? {
        if (!config.enableTimeBasedFlush) return null

        return getLock(phase).withLock {
            val batch = getBatch(phase)
            if (batch.items.isEmpty()) return@withLock null

            val elapsed = System.currentTimeMillis() - batch.lastFlushTimeMs
            val flushInterval =
                when (phase) {
                    SyncPhase.LIVE -> config.liveConfig.flushIntervalMs
                    SyncPhase.MOVIES -> config.moviesConfig.flushIntervalMs
                    SyncPhase.SERIES -> config.seriesConfig.flushIntervalMs
                    SyncPhase.EPISODES -> config.episodesConfig.flushIntervalMs
                }

            if (elapsed >= flushInterval) {
                UnifiedLog.d(TAG) { "Time-based flush for $phase: ${batch.items.size} items after ${elapsed}ms" }
                flushBatchInternal(phase, batch, isTimeBased = true)
            } else {
                null
            }
        }
    }

    /**
     * Flush all remaining items for a phase.
     *
     * Call on completion or cancellation to ensure no items are lost.
     *
     * @return Remaining items or empty list
     */
    suspend fun flushAll(phase: SyncPhase): List<RawMediaMetadata> {
        return getLock(phase).withLock {
            val batch = getBatch(phase)
            if (batch.items.isEmpty()) return@withLock emptyList()
            flushBatchInternal(phase, batch, isTimeBased = false) ?: emptyList()
        }
    }

    /**
     * Flush all phases. Returns map of phase to items.
     */
    suspend fun flushAllPhases(): Map<SyncPhase, List<RawMediaMetadata>> {
        val result = mutableMapOf<SyncPhase, List<RawMediaMetadata>>()
        for (phase in SyncPhase.entries) {
            val items = flushAll(phase)
            if (items.isNotEmpty()) {
                result[phase] = items
            }
        }
        return result
    }

    /**
     * Get current batch size for a phase.
     */
    fun currentBatchSize(phase: SyncPhase): Int = batches[phase]?.items?.size ?: 0

    /**
     * Internal flush - extracts items and resets batch.
     * MUST be called within lock.
     */
    private fun flushBatchInternal(
        phase: SyncPhase,
        batch: PhaseBatch,
        isTimeBased: Boolean,
    ): List<RawMediaMetadata>? {
        if (batch.items.isEmpty()) return null

        val items = batch.items.toList()
        batch.items.clear()
        batch.lastFlushTimeMs = System.currentTimeMillis()

        UnifiedLog.d(TAG) { "Flushing $phase batch: ${items.size} items (timeBased=$isTimeBased)" }
        return items
    }

    /**
     * Clear all batches (e.g., on error or reset).
     */
    suspend fun clear() {
        for (phase in SyncPhase.entries) {
            getLock(phase).withLock {
                batches[phase]?.items?.clear()
            }
        }
    }
}
