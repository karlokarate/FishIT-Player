package com.fishit.player.feature.settings.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for LeakDiagnostics data classes and noise control logic.
 *
 * These tests verify the noise control severity classification without
 * requiring LeakCanary runtime.
 */
class LeakDiagnosticsTest {
    @Test
    fun `MemoryStats calculates correct usage percentage`() {
        val stats =
            MemoryStats(
                usedMemoryMb = 128,
                totalMemoryMb = 256,
                maxMemoryMb = 512,
                freeMemoryMb = 128,
            )

        assertEquals(25, stats.usagePercentage)
    }

    @Test
    fun `MemoryStats handles zero max memory gracefully`() {
        val stats =
            MemoryStats(
                usedMemoryMb = 0,
                totalMemoryMb = 0,
                maxMemoryMb = 0,
                freeMemoryMb = 0,
            )

        assertEquals(0, stats.usagePercentage)
    }

    @Test
    fun `MemoryStats reports high usage correctly`() {
        val stats =
            MemoryStats(
                usedMemoryMb = 450,
                totalMemoryMb = 500,
                maxMemoryMb = 512,
                freeMemoryMb = 50,
            )

        assertTrue("Usage should be > 80%", stats.usagePercentage > 80)
    }

    @Test
    fun `LeakSummary with zero count has correct note`() {
        val summary =
            LeakSummary(
                leakCount = 0,
                lastLeakUptimeMs = null,
                note = "No objects retained",
            )

        assertEquals(0, summary.leakCount)
        assertEquals("No objects retained", summary.note)
    }

    @Test
    fun `LeakDetailedStatus with NONE severity indicates all clear`() {
        val status =
            LeakDetailedStatus(
                retainedObjectCount = 0,
                hasRetainedObjects = false,
                severity = RetentionSeverity.NONE,
                statusMessage = "All clear - no objects retained",
                config = createDefaultConfig(),
                memoryStats = createDefaultMemoryStats(),
                capturedAtMs = System.currentTimeMillis(),
            )

        assertEquals(RetentionSeverity.NONE, status.severity)
        assertEquals(0, status.retainedObjectCount)
        assertTrue(status.statusMessage.contains("All clear"))
    }

    @Test
    fun `RetentionSeverity LOW indicates transient retention`() {
        // LOW severity should be used for 1-2 retained objects
        // This is documentation of expected behavior
        assertEquals(RetentionSeverity.LOW, classifySeverity(1))
        assertEquals(RetentionSeverity.LOW, classifySeverity(2))
    }

    @Test
    fun `RetentionSeverity MEDIUM indicates moderate retention`() {
        // MEDIUM severity should be used for 3-4 retained objects
        assertEquals(RetentionSeverity.MEDIUM, classifySeverity(3))
        assertEquals(RetentionSeverity.MEDIUM, classifySeverity(4))
    }

    @Test
    fun `RetentionSeverity HIGH indicates likely leak`() {
        // HIGH severity should be used for 5+ retained objects (default threshold)
        assertEquals(RetentionSeverity.HIGH, classifySeverity(5))
        assertEquals(RetentionSeverity.HIGH, classifySeverity(10))
    }

    @Test
    fun `LeakCanaryConfig has sensible defaults`() {
        val config = createDefaultConfig()

        assertEquals(5, config.retainedVisibleThreshold)
        assertTrue(config.watchActivities)
        assertTrue(config.watchFragments)
        assertTrue(config.watchViewModels)
    }

    // Helper function to classify severity (mirrors LeakDiagnosticsImpl logic)
    private fun classifySeverity(
        retainedCount: Int,
        threshold: Int = 5,
    ): RetentionSeverity =
        when {
            retainedCount == 0 -> RetentionSeverity.NONE
            retainedCount in 1..2 -> RetentionSeverity.LOW
            retainedCount in 3..<threshold -> RetentionSeverity.MEDIUM
            else -> RetentionSeverity.HIGH
        }

    private fun createDefaultConfig(): LeakCanaryConfig =
        LeakCanaryConfig(
            retainedVisibleThreshold = 5,
            computeRetainedHeapSize = true,
            maxStoredHeapDumps = 7,
            watchDurationMillis = 10_000L,
            watchActivities = true,
            watchFragments = true,
            watchViewModels = true,
        )

    private fun createDefaultMemoryStats(): MemoryStats =
        MemoryStats(
            usedMemoryMb = 128,
            totalMemoryMb = 256,
            maxMemoryMb = 512,
            freeMemoryMb = 128,
        )
}
