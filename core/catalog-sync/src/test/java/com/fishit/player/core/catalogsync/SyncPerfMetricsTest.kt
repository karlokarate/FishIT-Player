package com.fishit.player.core.catalogsync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TASK 5: Performance metrics collection tests.
 *
 * Validates that SyncPerfMetrics correctly tracks:
 * - Phase timing
 * - Items discovered/persisted
 * - Errors and retries
 * - Memory pressure (memory variance approximation)
 * - Throughput calculations
 */
class SyncPerfMetricsTest {

    private lateinit var metrics: SyncPerfMetrics

    @Before
    fun setup() {
        metrics = SyncPerfMetrics(isEnabled = true)
    }

    @Test
    fun `test phase timing tracking`() = runTest {
        // Start phase
        val startTime = System.currentTimeMillis()
        metrics.startPhase(SyncPhase.LIVE)
        
        // Simulate work with actual timing
        val workStartTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - workStartTime < 100) {
            // Busy wait for precise timing
        }
        
        // End phase
        metrics.endPhase(SyncPhase.LIVE)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.LIVE)
        assertNotNull(phaseMetrics)
        assertTrue("Phase duration should be >= 100ms", phaseMetrics!!.totalDurationMs >= 100)
    }

    @Test
    fun `test items discovered tracking`() = runTest {
        metrics.startPhase(SyncPhase.MOVIES)
        metrics.recordItemsDiscovered(SyncPhase.MOVIES, 50)
        metrics.recordItemsDiscovered(SyncPhase.MOVIES, 30)
        metrics.endPhase(SyncPhase.MOVIES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.MOVIES)
        assertEquals(80L, phaseMetrics?.itemsDiscovered)
    }

    @Test
    fun `test persist operation tracking`() = runTest {
        metrics.startPhase(SyncPhase.SERIES)
        
        // Record persist operations
        metrics.recordPersist(SyncPhase.SERIES, durationMs = 50, itemCount = 25, isTimeBased = false)
        metrics.recordPersist(SyncPhase.SERIES, durationMs = 60, itemCount = 30, isTimeBased = true)
        
        metrics.endPhase(SyncPhase.SERIES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.SERIES)
        assertNotNull(phaseMetrics)
        assertEquals(2, phaseMetrics!!.persistCount)
        assertEquals(110L, phaseMetrics.persistMs)
        assertEquals(55L, phaseMetrics.itemsPersisted)
        assertEquals(2, phaseMetrics.batchesFlushed)
        assertEquals(1, phaseMetrics.timeBasedFlushes)
    }

    @Test
    fun `test error tracking`() = runTest {
        metrics.startPhase(SyncPhase.EPISODES)
        metrics.recordItemsDiscovered(SyncPhase.EPISODES, 100)
        
        // Record errors
        metrics.recordError(SyncPhase.EPISODES)
        metrics.recordError(SyncPhase.EPISODES)
        metrics.recordError(SyncPhase.EPISODES)
        
        metrics.endPhase(SyncPhase.EPISODES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.EPISODES)
        assertEquals(3, phaseMetrics?.errorCount)
        val errorRate = phaseMetrics?.errorRatePer1000 ?: 0.0
        assertEquals(30.0, errorRate, 0.1) // 3 errors per 100 items = 30/1000
    }

    @Test
    fun `test retry tracking`() = runTest {
        metrics.startPhase(SyncPhase.LIVE)
        
        // Record retries
        metrics.recordRetry(SyncPhase.LIVE)
        metrics.recordRetry(SyncPhase.LIVE)
        
        metrics.endPhase(SyncPhase.LIVE)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.LIVE)
        assertEquals(2, phaseMetrics?.retryCount)
    }

    @Test
    fun `test throughput calculation`() = runTest {
        metrics.startPhase(SyncPhase.MOVIES)
        
        // Simulate discovering and persisting items
        metrics.recordItemsDiscovered(SyncPhase.MOVIES, 100)
        metrics.recordPersist(SyncPhase.MOVIES, durationMs = 50, itemCount = 100, isTimeBased = false)
        
        // Simulate elapsed time for phase
        val workStartTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - workStartTime < 1000) {
            // Busy wait for precise timing
        }
        
        metrics.endPhase(SyncPhase.MOVIES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.MOVIES)
        assertNotNull(phaseMetrics)
        assertTrue("Should have positive throughput", phaseMetrics!!.itemsPersistedPerSec > 0)
        assertTrue("Items per sec should be reasonable", phaseMetrics.itemsPersistedPerSec < 1000)
    }

    @Test
    fun `test GC tracking captures memory pressure`() = runTest {
        metrics.startPhase(SyncPhase.SERIES)
        
        // Simulate some memory allocation (this won't necessarily trigger GC, but tracks memory)
        val garbage = mutableListOf<ByteArray>()
        repeat(10) {
            garbage.add(ByteArray(1024 * 1024)) // 1MB allocations
        }
        
        metrics.endPhase(SyncPhase.SERIES)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.SERIES)
        assertNotNull(phaseMetrics)
        // Memory variance is tracked, should be >= 0
        assertTrue("Memory variance should be non-negative", phaseMetrics!!.memoryVarianceMB >= 0)
    }

    @Test
    fun `test average calculations`() = runTest {
        metrics.startPhase(SyncPhase.LIVE)
        
        // Record fetch operations
        metrics.recordFetch(SyncPhase.LIVE, durationMs = 100)
        metrics.recordFetch(SyncPhase.LIVE, durationMs = 200)
        metrics.recordFetch(SyncPhase.LIVE, durationMs = 300)
        
        // Record persist operations
        metrics.recordPersist(SyncPhase.LIVE, durationMs = 50, itemCount = 10, isTimeBased = false)
        metrics.recordPersist(SyncPhase.LIVE, durationMs = 150, itemCount = 20, isTimeBased = false)
        
        metrics.endPhase(SyncPhase.LIVE)
        
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.LIVE)
        assertNotNull(phaseMetrics)
        assertEquals(200.0, phaseMetrics!!.avgFetchMs, 0.1) // (100+200+300)/3
        assertEquals(100.0, phaseMetrics.avgPersistMs, 0.1) // (50+150)/2
    }

    @Test
    fun `test export report contains all metrics`() = runTest {
        // Setup multiple phases with metrics
        metrics.startPhase(SyncPhase.LIVE)
        
        // Simulate elapsed time for the phase
        val workStartTime1 = System.currentTimeMillis()
        while (System.currentTimeMillis() - workStartTime1 < 100) {
            // Busy wait for precise timing
        }
        
        metrics.recordItemsDiscovered(SyncPhase.LIVE, 100)
        metrics.recordPersist(SyncPhase.LIVE, durationMs = 50, itemCount = 100, isTimeBased = false)
        metrics.recordError(SyncPhase.LIVE)
        metrics.recordRetry(SyncPhase.LIVE)
        metrics.endPhase(SyncPhase.LIVE)
        
        metrics.startPhase(SyncPhase.MOVIES)
        
        // Simulate elapsed time for the phase
        val workStartTime2 = System.currentTimeMillis()
        while (System.currentTimeMillis() - workStartTime2 < 100) {
            // Busy wait for precise timing
        }
        
        metrics.recordItemsDiscovered(SyncPhase.MOVIES, 200)
        metrics.recordPersist(SyncPhase.MOVIES, durationMs = 100, itemCount = 200, isTimeBased = true)
        metrics.endPhase(SyncPhase.MOVIES)
        
        val report = metrics.exportReport()
        
        // Verify report contains key sections
        assertTrue("Report should contain LIVE section", report.contains("LIVE"))
        assertTrue("Report should contain MOVIES section", report.contains("MOVIES"))
        assertTrue("Report should contain totals", report.contains("TOTALS"))
        assertTrue("Report should contain throughput", report.contains("items/sec"))
        assertTrue("Report should contain error metrics", report.contains("Errors:"))
        assertTrue("Report should contain retry metrics", report.contains("Retries:"))
        assertTrue("Report should contain memory metrics", report.contains("Memory Variance:"))
    }

    @Test
    fun `test metrics when disabled`() = runTest {
        val disabledMetrics = SyncPerfMetrics(isEnabled = false)
        
        // These should all be no-ops
        disabledMetrics.startPhase(SyncPhase.LIVE)
        disabledMetrics.recordItemsDiscovered(SyncPhase.LIVE, 100)
        disabledMetrics.recordError(SyncPhase.LIVE)
        disabledMetrics.endPhase(SyncPhase.LIVE)
        
        val phaseMetrics = disabledMetrics.getPhaseMetrics(SyncPhase.LIVE)
        // Should be null or empty since metrics are disabled
        assertTrue("Disabled metrics should not track data", 
            phaseMetrics == null || phaseMetrics.itemsDiscovered == 0L)
    }

    @Test
    fun `test reset clears all metrics`() = runTest {
        metrics.startPhase(SyncPhase.LIVE)
        metrics.recordItemsDiscovered(SyncPhase.LIVE, 100)
        metrics.endPhase(SyncPhase.LIVE)
        
        // Verify metrics exist
        assertNotNull(metrics.getPhaseMetrics(SyncPhase.LIVE))
        
        // Reset
        metrics.reset()
        
        // Verify metrics are cleared
        val phaseMetrics = metrics.getPhaseMetrics(SyncPhase.LIVE)
        assertTrue("Metrics should be cleared after reset", 
            phaseMetrics == null || phaseMetrics.itemsDiscovered == 0L)
    }
}
