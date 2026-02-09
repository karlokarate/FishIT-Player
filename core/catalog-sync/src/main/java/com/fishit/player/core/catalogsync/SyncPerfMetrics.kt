package com.fishit.player.core.catalogsync

import com.fishit.player.core.model.sync.SyncPhase
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
        // TASK 5: Error tracking
        var errorCount: Int = 0,
        var retryCount: Int = 0,
        // TASK 5: Memory pressure tracking
        var memoryUsageMBAtStart: Long = 0L,
        var memoryUsageMBAtEnd: Long = 0L,
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

        /** TASK 5: Memory variance (approximates GC pressure) */
        val memoryVarianceMB: Long
            get() = if (memoryUsageMBAtEnd >= memoryUsageMBAtStart) memoryUsageMBAtEnd - memoryUsageMBAtStart else 0L

        /** TASK 5: Error rate (errors per 1000 items) */
        val errorRatePer1000: Double
            get() = if (itemsDiscovered > 0) errorCount * 1000.0 / itemsDiscovered else 0.0
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
                // TASK 5: Capture memory usage at phase start
                memoryUsageMBAtStart = getMemoryUsageMB()
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
                // TASK 5: Capture memory usage at phase end
                memoryUsageMBAtEnd = getMemoryUsageMB()
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
     * TASK 5: Record an error during sync.
     *
     * @param phase The sync phase where error occurred
     */
    suspend fun recordError(phase: SyncPhase) {
        if (!isEnabled) return
        mutex.withLock {
            phaseMetrics.getOrPut(phase) { PhaseMetrics() }.apply {
                errorCount++
            }
        }
    }

    /**
     * TASK 5: Record a retry attempt during sync.
     *
     * @param phase The sync phase where retry occurred
     */
    suspend fun recordRetry(phase: SyncPhase) {
        if (!isEnabled) return
        mutex.withLock {
            phaseMetrics.getOrPut(phase) { PhaseMetrics() }.apply {
                retryCount++
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
     * TASK 5: Get current memory usage in MB.
     *
     * Tracks allocated memory as a proxy for GC pressure. Memory variance (delta between
     * phase start and end) indicates memory pressure - larger deltas suggest more allocations
     * and potential GC activity.
     *
     * Note: This is an approximation as Android doesn't expose detailed GC stats without Debug API.
     * The returned value represents used memory in MB, not actual GC event count.
     */
    private fun getMemoryUsageMB(): Long {
        return try {
            // Track allocated memory as proxy for GC pressure
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            
            // Return used memory in MB
            usedMemory / (1024 * 1024)
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Failed to get memory stats: ${e.message}" }
            0L
        }
    }

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
            var totalErrors = 0
            var totalRetries = 0
            var totalGcEvents = 0L

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
                // TASK 5: Add error and GC metrics
                appendLine("  Errors: ${metrics.errorCount} (${String.format("%.2f", metrics.errorRatePer1000)}/1000 items)")
                appendLine("  Retries: ${metrics.retryCount}")
                appendLine("  Memory Variance: ${metrics.memoryVarianceMB} MB")
                appendLine()

                totalDuration += metrics.totalDurationMs
                totalDiscovered += metrics.itemsDiscovered
                totalPersisted += metrics.itemsPersisted
                totalErrors += metrics.errorCount
                totalRetries += metrics.retryCount
                totalGcEvents += metrics.memoryVarianceMB
            }

            appendLine("=== TOTALS ===")
            appendLine("  Total Duration: ${totalDuration}ms (${totalDuration / 1000.0}s)")
            appendLine("  Total Discovered: $totalDiscovered items")
            appendLine("  Total Persisted: $totalPersisted items")
            appendLine("  Total Errors: $totalErrors")
            appendLine("  Total Retries: $totalRetries")
            appendLine("  Memory Variance: ${totalGcEvents} MB")
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
