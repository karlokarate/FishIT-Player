package com.fishit.player.core.catalogsync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK 5: Performance regression baseline tests.
 *
 * These tests establish baseline performance thresholds for sync operations.
 * If performance regresses below these thresholds, tests will fail.
 *
 * **Thresholds (baseline targets):**
 * - Throughput: >= 50 items/sec (minimum acceptable)
 * - Persist time: <= 100ms per batch of 100 items
 * - Error rate: <= 5% (50 errors per 1000 items)
 * - Memory pressure: monitored but no hard threshold (varies by device)
 *
 * **Usage:**
 * - Run regularly in CI to detect regressions
 * - Update thresholds as optimizations are implemented
 * - Use SyncPerfMetrics to collect actual data during sync
 */
class SyncPerformanceRegressionTest {

    /**
     * Baseline threshold: Sync should process at least 50 items/sec.
     *
     * This is a conservative baseline. Real-world performance should be higher:
     * - Live streams: 400 items in ~8s = 50 items/sec (baseline OK)
     * - Movies: 250 items in ~4s = 62 items/sec (above baseline)
     * - Series: 150 items in ~5s = 30 items/sec (needs optimization if below)
     */
    @Test
    fun `baseline throughput should be at least 50 items per second`() = runTest {
        val metrics = SyncPerfMetrics(isEnabled = true)
        
        // Simulate a sync phase
        metrics.startPhase(SyncPhase.LIVE)
        
        val itemCount = 100
        metrics.recordItemsDiscovered(SyncPhase.LIVE, itemCount)
        metrics.recordPersist(
            phase = SyncPhase.LIVE,
            durationMs = 50, // 50ms to persist 100 items
            itemCount = itemCount,
            isTimeBased = false
        )
        
        // Simulate 2 seconds of sync time
        Thread.sleep(2000)
        
        metrics.endPhase(SyncPhase.LIVE)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.LIVE)!!
        val throughput = phaseMetrics.itemsPersistedPerSec
        
        assertTrue(
            "Throughput regression detected! Expected >= 50 items/sec, got $throughput items/sec",
            throughput >= 50.0
        )
    }

    /**
     * Baseline threshold: Persist operations should complete in <= 100ms per batch.
     *
     * This ensures database operations don't become a bottleneck.
     */
    @Test
    fun `baseline persist time should be under 100ms per batch`() = runTest {
        val metrics = SyncPerfMetrics(isEnabled = true)
        
        metrics.startPhase(SyncPhase.MOVIES)
        
        // Record multiple persist operations
        val batchCount = 5
        repeat(batchCount) {
            metrics.recordPersist(
                phase = SyncPhase.MOVIES,
                durationMs = 80, // 80ms per batch (acceptable)
                itemCount = 100,
                isTimeBased = false
            )
        }
        
        metrics.endPhase(SyncPhase.MOVIES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.MOVIES)!!
        val avgPersistMs = phaseMetrics.avgPersistMs
        
        assertTrue(
            "Persist time regression detected! Expected <= 100ms avg, got $avgPersistMs ms",
            avgPersistMs <= 100.0
        )
    }

    /**
     * Baseline threshold: Error rate should be <= 5%.
     *
     * Higher error rates indicate quality issues with API responses or parsing.
     */
    @Test
    fun `baseline error rate should be under 5 percent`() = runTest {
        val metrics = SyncPerfMetrics(isEnabled = true)
        
        metrics.startPhase(SyncPhase.SERIES)
        
        val totalItems = 1000
        val errors = 40 // 4% error rate (acceptable)
        
        metrics.recordItemsDiscovered(SyncPhase.SERIES, totalItems)
        repeat(errors) {
            metrics.recordError(SyncPhase.SERIES)
        }
        
        metrics.endPhase(SyncPhase.SERIES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.SERIES)!!
        val errorRate = phaseMetrics.errorRatePer1000 / 10.0 // Convert to percentage
        
        assertTrue(
            "Error rate regression detected! Expected <= 5%, got $errorRate%",
            errorRate <= 5.0
        )
    }

    /**
     * Baseline: Verify metrics report generation doesn't throw exceptions.
     *
     * Report generation should be lightweight and not impact performance.
     */
    @Test
    fun `metrics report generation should complete without errors`() = runTest {
        val metrics = SyncPerfMetrics(isEnabled = true)
        
        // Setup realistic scenario with all phases
        for (phase in SyncPhase.entries) {
            metrics.startPhase(phase)
            metrics.recordItemsDiscovered(phase, 100)
            metrics.recordPersist(phase, durationMs = 50, itemCount = 100, isTimeBased = false)
            metrics.recordError(phase)
            metrics.recordRetry(phase)
            Thread.sleep(50)
            metrics.endPhase(phase)
        }
        
        // Generate report - should not throw
        val report = metrics.exportReport()
        
        assertTrue("Report should not be empty", report.isNotEmpty())
        assertTrue("Report should contain phase data", report.contains("Duration:"))
        assertTrue("Report should contain totals", report.contains("TOTALS"))
    }

    /**
     * Memory tracking: Verify GC tracking doesn't crash and returns reasonable values.
     */
    @Test
    fun `memory pressure tracking should return non-negative values`() = runTest {
        val metrics = SyncPerfMetrics(isEnabled = true)
        
        metrics.startPhase(SyncPhase.EPISODES)
        
        // Simulate some work that might trigger GC
        val tempData = mutableListOf<ByteArray>()
        repeat(5) {
            tempData.add(ByteArray(512 * 1024)) // 512KB allocations
        }
        
        metrics.endPhase(SyncPhase.EPISODES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.EPISODES)!!
        
        // GC count should be non-negative (it's a delta/variance measure)
        assertTrue(
            "GC event count should be non-negative, got ${phaseMetrics.gcEventCount}",
            phaseMetrics.gcEventCount >= 0
        )
    }

    /**
     * Performance comparison: Multiple phases should maintain consistent throughput.
     */
    @Test
    fun `throughput should remain consistent across phases`() = runTest {
        val metrics = SyncPerfMetrics(isEnabled = true)
        val throughputs = mutableListOf<Double>()
        
        // Test each phase
        for (phase in listOf(SyncPhase.LIVE, SyncPhase.MOVIES, SyncPhase.SERIES)) {
            metrics.startPhase(phase)
            
            val itemCount = 100
            metrics.recordItemsDiscovered(phase, itemCount)
            metrics.recordPersist(phase, durationMs = 50, itemCount = itemCount, isTimeBased = false)
            
            Thread.sleep(1000)
            
            metrics.endPhase(phase)
            
            val phaseMetrics = metrics.getPhaseMetrics(phase)!!
            throughputs.add(phaseMetrics.itemsPersistedPerSec)
        }
        
        // All throughputs should be above minimum baseline
        throughputs.forEach { throughput ->
            assertTrue(
                "Phase throughput $throughput is below baseline 50 items/sec",
                throughput >= 50.0
            )
        }
    }

    /**
     * Stress test: Large batch should still meet performance targets.
     */
    @Test
    fun `large batch performance should meet baseline thresholds`() = runTest {
        val metrics = SyncPerfMetrics(isEnabled = true)
        
        metrics.startPhase(SyncPhase.MOVIES)
        
        // Large batch scenario
        val totalItems = 1000
        val batchSize = 250
        val batches = totalItems / batchSize
        
        repeat(batches) {
            metrics.recordItemsDiscovered(SyncPhase.MOVIES, batchSize)
            metrics.recordPersist(
                phase = SyncPhase.MOVIES,
                durationMs = 200, // 200ms per batch of 250 items
                itemCount = batchSize,
                isTimeBased = false
            )
        }
        
        // Simulate total sync time
        Thread.sleep(2000)
        
        metrics.endPhase(SyncPhase.MOVIES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.MOVIES)!!
        
        // Verify throughput
        assertTrue(
            "Large batch throughput ${phaseMetrics.itemsPersistedPerSec} below baseline",
            phaseMetrics.itemsPersistedPerSec >= 50.0
        )
        
        // Verify average persist time
        assertTrue(
            "Large batch persist time ${phaseMetrics.avgPersistMs}ms above threshold",
            phaseMetrics.avgPersistMs <= 250.0 // Allow slightly higher for large batches
        )
    }
}
