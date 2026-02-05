package com.fishit.player.core.synccommon.metrics

import com.fishit.player.core.model.sync.SyncPhase
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Source-agnostic performance metrics collector for catalog sync.
 *
 * **Purpose:**
 * Track performance metrics across all sync sources (Xtream, Telegram, IO, etc.)
 * using the canonical [SyncPhase] enum.
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
 * val metrics = SyncPerfMetrics(isEnabled = debugSettings.syncMetricsEnabled)
 * metrics.startPhase(SyncPhase.LIVE_CHANNELS)
 * metrics.recordFetch(SyncPhase.LIVE_CHANNELS, fetchDurationMs)
 * metrics.recordPersist(SyncPhase.LIVE_CHANNELS, persistDurationMs, itemCount)
 * metrics.endPhase(SyncPhase.LIVE_CHANNELS)
 * val report = metrics.exportReport()
 * ```
 *
 * @param isEnabled Whether metrics collection is active (disable in production for perf)
 * @param syncId Unique identifier for this sync run
 */
class SyncPerfMetrics(
    val isEnabled: Boolean = true,
    val syncId: String = System.currentTimeMillis().toString(),
) {
    companion object {
        private const val TAG = "SyncPerfMetrics"
    }

    private val mutex = Mutex()
    private val phaseMetrics = ConcurrentHashMap<SyncPhase, PhaseMetrics>()
    private val activePhases = ConcurrentHashMap<SyncPhase, Long>()
    private var syncStartMs: Long = 0L
    private var syncEndMs: Long = 0L

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
        // Error tracking
        var errorCount: Int = 0,
        var retryCount: Int = 0,
        // Memory pressure tracking
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

        /** Memory variance (approximates GC pressure) */
        val memoryVarianceMB: Long
            get() = if (memoryUsageMBAtEnd >= memoryUsageMBAtStart) {
                memoryUsageMBAtEnd - memoryUsageMBAtStart
            } else {
                0L
            }

        /** Error rate (errors per 1000 items) */
        val errorRatePer1000: Double
            get() = if (itemsDiscovered > 0) errorCount * 1000.0 / itemsDiscovered else 0.0
    }

    /**
     * Mark the start of the entire sync run.
     */
    suspend fun startSync() {
        if (!isEnabled) return
        mutex.withLock {
            syncStartMs = System.currentTimeMillis()
            UnifiedLog.d(TAG) { "Sync $syncId started" }
        }
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
                memoryUsageMBAtEnd = getMemoryUsageMB()
            }
            UnifiedLog.d(TAG) { "Phase $phase ended (${phaseMetrics[phase]?.totalDurationMs}ms)" }
        }
    }

    /**
     * Mark the end of the entire sync run.
     */
    suspend fun endSync() {
        if (!isEnabled) return
        mutex.withLock {
            syncEndMs = System.currentTimeMillis()
            val totalDuration = syncEndMs - syncStartMs
            UnifiedLog.i(TAG) { "Sync $syncId completed in ${totalDuration}ms" }
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
     * Record persist operation duration.
     */
    suspend fun recordPersist(
        phase: SyncPhase,
        durationMs: Long,
        itemCount: Int = 0,
        wasBatchFlush: Boolean = false,
        wasTimeBasedFlush: Boolean = false,
    ) {
        if (!isEnabled) return
        mutex.withLock {
            phaseMetrics.getOrPut(phase) { PhaseMetrics() }.apply {
                persistMs += durationMs
                persistCount++
                itemsPersisted += itemCount
                if (wasBatchFlush) batchesFlushed++
                if (wasTimeBasedFlush) timeBasedFlushes++
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
     * Record an error.
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
     * Record a retry attempt.
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
     * Get metrics for a specific phase.
     */
    fun getPhaseMetrics(phase: SyncPhase): PhaseMetrics? = phaseMetrics[phase]

    /**
     * Get total sync duration.
     */
    val totalSyncDurationMs: Long
        get() = if (syncEndMs > 0 && syncStartMs > 0) syncEndMs - syncStartMs else 0L

    /**
     * Get total items discovered across all phases.
     */
    val totalItemsDiscovered: Long
        get() = phaseMetrics.values.sumOf { it.itemsDiscovered }

    /**
     * Get total items persisted across all phases.
     */
    val totalItemsPersisted: Long
        get() = phaseMetrics.values.sumOf { it.itemsPersisted }

    /**
     * Get total errors across all phases.
     */
    val totalErrors: Int
        get() = phaseMetrics.values.sumOf { it.errorCount }

    /**
     * Export metrics to human-readable report format.
     */
    fun exportReport(): String = buildString {
        appendLine("=== Sync Performance Report (ID: $syncId) ===")
        appendLine("Total Duration: ${totalSyncDurationMs}ms")
        appendLine("Total Discovered: $totalItemsDiscovered")
        appendLine("Total Persisted: $totalItemsPersisted")
        appendLine("Total Errors: $totalErrors")
        appendLine()

        phaseMetrics.entries.sortedBy { it.key.ordinal }.forEach { (phase, metrics) ->
            appendLine("--- Phase: $phase ---")
            appendLine("  Duration: ${metrics.totalDurationMs}ms")
            appendLine("  Discovered: ${metrics.itemsDiscovered} (${String.format("%.1f", metrics.itemsDiscoveredPerSec)}/s)")
            appendLine("  Persisted: ${metrics.itemsPersisted} (${String.format("%.1f", metrics.itemsPersistedPerSec)}/s)")
            appendLine("  Fetch: ${metrics.fetchMs}ms (${metrics.fetchCount} calls, avg ${String.format("%.1f", metrics.avgFetchMs)}ms)")
            appendLine("  Persist: ${metrics.persistMs}ms (${metrics.persistCount} batches, avg ${String.format("%.1f", metrics.avgPersistMs)}ms)")
            appendLine("  Errors: ${metrics.errorCount}, Retries: ${metrics.retryCount}")
            appendLine("  Memory: ${metrics.memoryVarianceMB}MB variance")
            appendLine()
        }
    }

    /**
     * Export metrics to structured map for telemetry.
     */
    fun exportMap(): Map<String, Any> = buildMap {
        put("sync_id", syncId)
        put("total_duration_ms", totalSyncDurationMs)
        put("total_discovered", totalItemsDiscovered)
        put("total_persisted", totalItemsPersisted)
        put("total_errors", totalErrors)

        phaseMetrics.forEach { (phase, metrics) ->
            val prefix = "phase_${phase.name.lowercase()}"
            put("${prefix}_duration_ms", metrics.totalDurationMs)
            put("${prefix}_discovered", metrics.itemsDiscovered)
            put("${prefix}_persisted", metrics.itemsPersisted)
            put("${prefix}_errors", metrics.errorCount)
            put("${prefix}_fetch_ms", metrics.fetchMs)
            put("${prefix}_persist_ms", metrics.persistMs)
        }
    }
}

/**
 * Get current memory usage in MB.
 */
internal fun getMemoryUsageMB(): Long {
    val runtime = Runtime.getRuntime()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
    return usedMemory / (1024 * 1024)
}
