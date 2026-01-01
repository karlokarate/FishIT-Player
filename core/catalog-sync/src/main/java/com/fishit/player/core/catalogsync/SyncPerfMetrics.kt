package com.fishit.player.core.catalogsync

import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight performance metrics collector for catalog sync.
 *
 * **Design:**
 * - Thread-safe via Mutex and ConcurrentHashMap
 * - Debug-safe: only enabled when [isEnabled] is true
 * - Exports to text format for debug bundle
 * - Measures fetch_ms, parse_ms, persist_ms per phase
 * - Tracks items/sec throughput
 *
 * **Usage:**
 * ```kotlin
 * val metrics = SyncPerfMetrics()
 * metrics.startPhase(SyncPhase.LIVE)
 * metrics.recordFetch(fetchDurationMs)
 * metrics.recordPersist(persistDurationMs, itemCount)
 * metrics.endPhase(SyncPhase.LIVE)
 * val report = metrics.exportReport()
 * ```
 */
class SyncPerfMetrics(
    val isEnabled: Boolean = true,
) {
    companion object {
        private const val TAG = "SyncPerfMetrics"
    }

    private val mutex = Mutex()
    private val phaseMetrics = ConcurrentHashMap<SyncPhase, PhaseMetrics>()
    private val activePhases = ConcurrentHashMap<SyncPhase, Long>()

    /**
     * Metrics for a single sync phase.
     */
    data class PhaseMetrics(
        var fetchMs: Long = 0L,
        var fetchCount: Int = 0,
        var parseMs: Long = 0L,
        var parseCount: Int = 0,
        var persistMs: Long = 0L,
        var persistCount: Int = 0,
        var itemsDiscovered: Long = 0L,
        var itemsPersisted: Long = 0L,
        var batchesFlushed: Int = 0,
        var timeBasedFlushes: Int = 0,
        var phaseStartMs: Long = 0L,
        var phaseEndMs: Long = 0L,
    ) {
        /** Average fetch duration per call */
        val avgFetchMs: Double
            get() = if (fetchCount > 0) fetchMs.toDouble() / fetchCount else 0.0

        /** Average persist duration per batch */
        val avgPersistMs: Double
            get() = if (persistCount > 0) persistMs.toDouble() / persistCount else 0.0

        /** Total phase duration */
        val totalDurationMs: Long
            get() = if (phaseEndMs > 0 && phaseStartMs > 0) phaseEndMs - phaseStartMs else 0L

        /** Items discovered per second */
        val itemsDiscoveredPerSec: Double
            get() = if (totalDurationMs > 0) itemsDiscovered * 1000.0 / totalDurationMs else 0.0

        /** Items persisted per second */
        val itemsPersistedPerSec: Double
            get() = if (totalDurationMs > 0) itemsPersisted * 1000.0 / totalDurationMs else 0.0
    }

    /**
     * Mark the start of a sync phase.
     */
    suspend fun startPhase(phase: SyncPhase) {
        if (!isEnabled) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            activePhases[phase] = now
            phaseMetrics.getOrPut(phase) { PhaseMetrics() }.apply {
                phaseStartMs = now
            }
            UnifiedLog.d(TAG) { "Phase $phase started" }
        }
    }

    /**
     * Mark the end of a sync phase.
     */
    suspend fun endPhase(phase: SyncPhase) {
        if (!isEnabled) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            activePhases.remove(phase)
            phaseMetrics[phase]?.apply {
                phaseEndMs = now
            }
            UnifiedLog.d(TAG) { "Phase $phase ended (${phaseMetrics[phase]?.totalDurationMs}ms)" }
        }
    }

    /**
     * Record fetch operation duration.
     */
    suspend fun recordFetch(
        phase: SyncPhase,
        durationMs: Long,
    ) {
        if (!isEnabled) return
        mutex.withLock {
            phaseMetrics.getOrPut(phase) { PhaseMetrics() }.apply {
                fetchMs += durationMs
                fetchCount++
            }
        }
    }

    /**
     * Record parse operation duration.
     */
    suspend fun recordParse(
        phase: SyncPhase,
        durationMs: Long,
        itemCount: Int = 0,
    ) {
        if (!isEnabled) return
        mutex.withLock {
            phaseMetrics.getOrPut(phase) { PhaseMetrics() }.apply {
                parseMs += durationMs
                parseCount++
                if (itemCount > 0) itemsDiscovered += itemCount
            }
        }
    }

    /**
     * Record persist/flush operation.
     *
     * @param phase The sync phase
     * @param durationMs Duration of the persist operation
     * @param itemCount Number of items persisted
     * @param isTimeBased Whether this was a time-based flush (vs batch-full)
     */
    suspend fun recordPersist(
        phase: SyncPhase,
        durationMs: Long,
        itemCount: Int,
        isTimeBased: Boolean = false,
    ) {
        if (!isEnabled) return
        mutex.withLock {
            phaseMetrics.getOrPut(phase) { PhaseMetrics() }.apply {
                persistMs += durationMs
                persistCount++
                itemsPersisted += itemCount
                batchesFlushed++
                if (isTimeBased) timeBasedFlushes++
            }
        }
    }

    /**
     * Record items discovered (without timing).
     */
    suspend fun recordItemsDiscovered(
        phase: SyncPhase,
        count: Int,
    ) {
        if (!isEnabled) return
        mutex.withLock {
            phaseMetrics.getOrPut(phase) { PhaseMetrics() }.apply {
                itemsDiscovered += count
            }
        }
    }

    /**
     * Get current metrics snapshot for a phase.
     */
    fun getPhaseMetrics(phase: SyncPhase): PhaseMetrics? = phaseMetrics[phase]?.copy()

    /**
     * Get all metrics as a map.
     */
    fun getAllMetrics(): Map<SyncPhase, PhaseMetrics> = phaseMetrics.toMap()

    /**
     * Export metrics report as text for debug bundle.
     */
    fun exportReport(): String =
        buildString {
            appendLine("=== Xtream Catalog Sync Performance Report ===")
            appendLine("Generated: ${java.time.Instant.now()}")
            appendLine()

            var totalDuration = 0L
            var totalDiscovered = 0L
            var totalPersisted = 0L

            for (phase in SyncPhase.entries) {
                val metrics = phaseMetrics[phase] ?: continue

                appendLine("--- ${phase.name} ---")
                appendLine("  Duration: ${metrics.totalDurationMs}ms")
                appendLine(
                    "  Fetch: ${metrics.fetchCount} calls, ${metrics.fetchMs}ms total, ${String.format("%.1f", metrics.avgFetchMs)}ms avg",
                )
                appendLine("  Parse: ${metrics.parseCount} calls, ${metrics.parseMs}ms total")
                appendLine(
                    "  Persist: ${metrics.persistCount} batches, ${metrics.persistMs}ms total, ${String.format(
                        "%.1f",
                        metrics.avgPersistMs,
                    )}ms avg",
                )
                appendLine("  Items Discovered: ${metrics.itemsDiscovered} (${String.format("%.1f", metrics.itemsDiscoveredPerSec)}/sec)")
                appendLine("  Items Persisted: ${metrics.itemsPersisted} (${String.format("%.1f", metrics.itemsPersistedPerSec)}/sec)")
                appendLine("  Batches Flushed: ${metrics.batchesFlushed} (${metrics.timeBasedFlushes} time-based)")
                appendLine()

                totalDuration += metrics.totalDurationMs
                totalDiscovered += metrics.itemsDiscovered
                totalPersisted += metrics.itemsPersisted
            }

            appendLine("=== TOTALS ===")
            appendLine("  Total Duration: ${totalDuration}ms (${totalDuration / 1000.0}s)")
            appendLine("  Total Discovered: $totalDiscovered items")
            appendLine("  Total Persisted: $totalPersisted items")
            if (totalDuration > 0) {
                appendLine("  Overall Throughput: ${String.format("%.1f", totalPersisted * 1000.0 / totalDuration)} items/sec")
            }
        }

    /**
     * Reset all metrics.
     */
    suspend fun reset() {
        mutex.withLock {
            phaseMetrics.clear()
            activePhases.clear()
        }
    }
}
