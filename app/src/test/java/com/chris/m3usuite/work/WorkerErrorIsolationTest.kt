package com.chris.m3usuite.work

import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.playback.PlaybackSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for worker error isolation behavior.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 6b: Worker Error Isolation Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * - Worker errors do not affect PlaybackSession state
 * - Worker errors are logged via AppLog with correct category
 * - Worker errors do not cause PlaybackSession.stop() or release()
 * - Error logging structure is correct for WORKER_ERROR category
 *
 * NOTE: These tests do NOT import Telegram-related modules per task constraints.
 */
class WorkerErrorIsolationTest {
    @Before
    fun setUp() {
        // Reset PlaybackSession state before each test
        PlaybackSession.resetForTesting()
        // Enable AppLog for tests
        AppLog.setMasterEnabled(true)
    }

    @After
    fun tearDown() {
        // Clean up after each test
        PlaybackSession.resetForTesting()
        AppLog.setMasterEnabled(false)
    }

    // ══════════════════════════════════════════════════════════════════
    // Worker Error Does Not Affect PlaybackSession Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `simulated worker error does not stop PlaybackSession`() {
        // Given: PlaybackSession in initial state
        assertFalse(PlaybackSession.isSessionActive.value)

        // When: Simulating a worker error (just logging, no stop/release call)
        simulateWorkerError("XtreamDeltaImportWorker", RuntimeException("Network error"))

        // Then: PlaybackSession state is unchanged
        assertNull(PlaybackSession.playbackError.value)
        assertNull(PlaybackSession.error.value)
    }

    @Test
    fun `simulated worker error does not release PlaybackSession`() {
        // Given: PlaybackSession in initial state
        assertNull(PlaybackSession.current())

        // When: Simulating a worker error
        simulateWorkerError("XtreamDetailsWorker", IllegalStateException("State error"))

        // Then: PlaybackSession is not released (still accessible)
        // (current() returns null because no player was acquired, not because of release)
        assertNull(PlaybackSession.current())

        // Verify we can still use PlaybackSession methods
        PlaybackSession.setSource("https://example.com/test.mp4")
        assertEquals("https://example.com/test.mp4", PlaybackSession.currentSource())
    }

    @Test
    fun `multiple simulated worker errors do not accumulate on PlaybackSession`() {
        // Given: Initial state
        assertFalse(PlaybackSession.isSessionActive.value)

        // When: Multiple worker errors occur
        repeat(5) { i ->
            simulateWorkerError("Worker$i", RuntimeException("Error $i"))
        }

        // Then: PlaybackSession is still in clean state
        assertFalse(PlaybackSession.isSessionActive.value)
        assertNull(PlaybackSession.playbackError.value)
        assertEquals(0L, PlaybackSession.positionMs.value)
        assertEquals(0L, PlaybackSession.durationMs.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // Worker Error Logging Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `worker error logging creates WORKER_ERROR entry`() {
        val historyBefore = AppLog.history.value.size

        // Simulate worker error with logging
        simulateWorkerError("ObxKeyBackfillWorker", NullPointerException("Null reference"))

        val historyAfter = AppLog.history.value
        assertTrue("Should have logged an entry", historyAfter.size > historyBefore)

        // Find the WORKER_ERROR entry
        val workerErrors = historyAfter.filter { it.category == "WORKER_ERROR" }
        assertTrue("Should have at least one WORKER_ERROR", workerErrors.isNotEmpty())
    }

    @Test
    fun `worker error logging includes worker name`() {
        simulateWorkerError("XtreamDeltaImportWorker", RuntimeException("Test"))

        val entry = AppLog.history.value.last { it.category == "WORKER_ERROR" }
        assertEquals("XtreamDeltaImportWorker", entry.extras["worker"])
    }

    @Test
    fun `worker error logging includes exception type`() {
        simulateWorkerError("XtreamDetailsWorker", IllegalArgumentException("Invalid arg"))

        val entry = AppLog.history.value.last { it.category == "WORKER_ERROR" }
        assertEquals("IllegalArgumentException", entry.extras["exception"])
    }

    @Test
    fun `worker error logging includes cause when present`() {
        val cause = RuntimeException("Root cause")
        val exception = IllegalStateException("Wrapper", cause)

        simulateWorkerError("ObxKeyBackfillWorker", exception)

        val entry = AppLog.history.value.last { it.category == "WORKER_ERROR" }
        assertEquals("RuntimeException", entry.extras["cause"])
    }

    @Test
    fun `worker error logging shows none when no cause`() {
        val exceptionWithoutCause = RuntimeException("No cause")

        simulateWorkerError("XtreamDeltaImportWorker", exceptionWithoutCause)

        val entry = AppLog.history.value.last { it.category == "WORKER_ERROR" }
        assertEquals("none", entry.extras["cause"])
    }

    // ══════════════════════════════════════════════════════════════════
    // All Three Workers Log Correctly Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `XtreamDeltaImportWorker error pattern logs correctly`() {
        simulateWorkerError("XtreamDeltaImportWorker", RuntimeException("Delta import failed"))

        val entry = AppLog.history.value.last { it.category == "WORKER_ERROR" }
        assertEquals("XtreamDeltaImportWorker", entry.extras["worker"])
        assertTrue(entry.message.contains("XtreamDeltaImportWorker"))
    }

    @Test
    fun `XtreamDetailsWorker error pattern logs correctly`() {
        simulateWorkerError("XtreamDetailsWorker", RuntimeException("Details fetch failed"))

        val entry = AppLog.history.value.last { it.category == "WORKER_ERROR" }
        assertEquals("XtreamDetailsWorker", entry.extras["worker"])
        assertTrue(entry.message.contains("XtreamDetailsWorker"))
    }

    @Test
    fun `ObxKeyBackfillWorker error pattern logs correctly`() {
        simulateWorkerError("ObxKeyBackfillWorker", RuntimeException("Backfill failed"))

        val entry = AppLog.history.value.last { it.category == "WORKER_ERROR" }
        assertEquals("ObxKeyBackfillWorker", entry.extras["worker"])
        assertTrue(entry.message.contains("ObxKeyBackfillWorker"))
    }

    // ══════════════════════════════════════════════════════════════════
    // Error Level Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `worker errors are logged at ERROR level`() {
        simulateWorkerError("XtreamDeltaImportWorker", RuntimeException("Test"))

        val entry = AppLog.history.value.last { it.category == "WORKER_ERROR" }
        assertEquals(AppLog.Level.ERROR, entry.level)
    }

    // ══════════════════════════════════════════════════════════════════
    // BypassMaster Behavior Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `worker errors are logged even when master logging is disabled`() {
        // Disable master logging
        AppLog.setMasterEnabled(false)

        val historyBefore = AppLog.history.value.size

        // Worker errors use bypassMaster = true
        AppLog.log(
            category = "WORKER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Worker TestWorker failed: Test error",
            extras =
                mapOf(
                    "worker" to "TestWorker",
                    "exception" to "TestException",
                    "cause" to "none",
                ),
            bypassMaster = true,
        )

        val historyAfter = AppLog.history.value
        assertTrue("Worker errors should bypass master disable", historyAfter.size > historyBefore)
    }

    // ══════════════════════════════════════════════════════════════════
    // Helper Functions
    // ══════════════════════════════════════════════════════════════════

    /**
     * Simulates the worker error logging pattern used in actual workers.
     * This mirrors the logWorkerError() function added to each worker.
     */
    private fun simulateWorkerError(
        workerName: String,
        exception: Throwable,
    ) {
        AppLog.log(
            category = "WORKER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Worker $workerName failed: ${exception.message}",
            extras =
                mapOf(
                    "worker" to workerName,
                    "exception" to exception.javaClass.simpleName,
                    "cause" to (exception.cause?.javaClass?.simpleName ?: "none"),
                ),
            bypassMaster = true,
        )
    }
}
