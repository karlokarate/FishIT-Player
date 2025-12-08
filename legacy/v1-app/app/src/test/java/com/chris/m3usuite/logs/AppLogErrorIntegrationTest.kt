package com.chris.m3usuite.logs

import com.chris.m3usuite.core.logging.AppLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AppLog integration with PLAYER_ERROR and WORKER_ERROR categories.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 6b: AppLog Player/Worker Error Integration Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * - PLAYER_ERROR category entries can be logged
 * - WORKER_ERROR category entries can be logged
 * - Entries contain expected extras keys
 * - History is populated correctly
 */
class AppLogErrorIntegrationTest {
    @Before
    fun setUp() {
        // Enable logging for tests
        AppLog.setMasterEnabled(true)
        AppLog.setCategoriesEnabled(emptySet()) // All categories enabled
    }

    @After
    fun tearDown() {
        // Disable logging after tests
        AppLog.setMasterEnabled(false)
    }

    // ══════════════════════════════════════════════════════════════════
    // PLAYER_ERROR Category Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAYER_ERROR entry can be logged`() {
        val historyBefore = AppLog.history.value.size

        // Log a PLAYER_ERROR entry
        AppLog.log(
            category = "PLAYER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Playback error: Network error (1001)",
            extras =
                mapOf(
                    "type" to "Network",
                    "code" to "1001",
                    "positionMs" to "5000",
                ),
            bypassMaster = true, // Always log errors
        )

        val historyAfter = AppLog.history.value
        assertTrue("History should have new entry", historyAfter.size > historyBefore)

        // Find the logged entry
        val entry = historyAfter.last()
        assertEquals("PLAYER_ERROR", entry.category)
        assertEquals(AppLog.Level.ERROR, entry.level)
        assertTrue(entry.message.contains("Playback error"))
    }

    @Test
    fun `PLAYER_ERROR entry contains expected extras`() {
        val expectedExtras =
            mapOf(
                "type" to "Http",
                "code" to "404",
                "url" to "https://example.com/video.mp4",
                "mediaId" to "media-123",
                "positionMs" to "12000",
                "durationMs" to "120000",
            )

        AppLog.log(
            category = "PLAYER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Playback error: HTTP 404",
            extras = expectedExtras,
            bypassMaster = true,
        )

        val entry = AppLog.history.value.last()
        assertEquals("PLAYER_ERROR", entry.category)

        // Verify all expected keys are present
        expectedExtras.keys.forEach { key ->
            assertTrue(
                "Entry should contain key: $key",
                entry.extras.containsKey(key),
            )
            assertEquals(
                "Entry value for $key should match",
                expectedExtras[key],
                entry.extras[key],
            )
        }
    }

    @Test
    fun `PLAYER_ERROR entries appear in history`() {
        val countBefore = AppLog.history.value.count { it.category == "PLAYER_ERROR" }

        // Log 3 PLAYER_ERROR entries
        repeat(3) { i ->
            AppLog.log(
                category = "PLAYER_ERROR",
                level = AppLog.Level.ERROR,
                message = "Test error $i",
                bypassMaster = true,
            )
        }

        val countAfter = AppLog.history.value.count { it.category == "PLAYER_ERROR" }
        assertEquals("Should have 3 more PLAYER_ERROR entries", countBefore + 3, countAfter)
    }

    // ══════════════════════════════════════════════════════════════════
    // WORKER_ERROR Category Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `WORKER_ERROR entry can be logged`() {
        val historyBefore = AppLog.history.value.size

        // Log a WORKER_ERROR entry
        AppLog.log(
            category = "WORKER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Worker XtreamDeltaImportWorker failed: Network timeout",
            extras =
                mapOf(
                    "worker" to "XtreamDeltaImportWorker",
                    "exception" to "SocketTimeoutException",
                    "cause" to "none",
                ),
            bypassMaster = true,
        )

        val historyAfter = AppLog.history.value
        assertTrue("History should have new entry", historyAfter.size > historyBefore)

        // Find the logged entry
        val entry = historyAfter.last()
        assertEquals("WORKER_ERROR", entry.category)
        assertEquals(AppLog.Level.ERROR, entry.level)
        assertTrue(entry.message.contains("Worker"))
    }

    @Test
    fun `WORKER_ERROR entry contains worker name and exception info`() {
        val expectedExtras =
            mapOf(
                "worker" to "ObxKeyBackfillWorker",
                "exception" to "IllegalStateException",
                "cause" to "NullPointerException",
            )

        AppLog.log(
            category = "WORKER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Worker ObxKeyBackfillWorker failed: State error",
            extras = expectedExtras,
            bypassMaster = true,
        )

        val entry = AppLog.history.value.last()
        assertEquals("WORKER_ERROR", entry.category)

        // Verify expected keys
        assertEquals("ObxKeyBackfillWorker", entry.extras["worker"])
        assertEquals("IllegalStateException", entry.extras["exception"])
        assertEquals("NullPointerException", entry.extras["cause"])
    }

    @Test
    fun `WORKER_ERROR entries appear in history`() {
        val countBefore = AppLog.history.value.count { it.category == "WORKER_ERROR" }

        // Log 3 WORKER_ERROR entries for different workers
        val workers = listOf("XtreamDeltaImportWorker", "XtreamDetailsWorker", "ObxKeyBackfillWorker")
        workers.forEach { worker ->
            AppLog.log(
                category = "WORKER_ERROR",
                level = AppLog.Level.ERROR,
                message = "Worker $worker failed",
                extras = mapOf("worker" to worker, "exception" to "TestException", "cause" to "none"),
                bypassMaster = true,
            )
        }

        val countAfter = AppLog.history.value.count { it.category == "WORKER_ERROR" }
        assertEquals("Should have 3 more WORKER_ERROR entries", countBefore + 3, countAfter)
    }

    // ══════════════════════════════════════════════════════════════════
    // BypassMaster Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `bypassMaster allows logging even when master disabled`() {
        // Disable master
        AppLog.setMasterEnabled(false)

        val historyBefore = AppLog.history.value.size

        // Log with bypassMaster = true
        AppLog.log(
            category = "PLAYER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Bypass test",
            bypassMaster = true,
        )

        val historyAfter = AppLog.history.value
        assertTrue("BypassMaster should allow logging", historyAfter.size > historyBefore)
    }

    @Test
    fun `normal logging is blocked when master disabled`() {
        // Disable master
        AppLog.setMasterEnabled(false)

        val historyBefore = AppLog.history.value.size

        // Log without bypassMaster
        AppLog.log(
            category = "TEST_CATEGORY",
            level = AppLog.Level.DEBUG,
            message = "Should not appear",
            bypassMaster = false,
        )

        val historyAfter = AppLog.history.value
        assertEquals("Normal logging should be blocked", historyBefore, historyAfter.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // Entry Structure Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `AppLog Entry has timestamp`() {
        AppLog.log(
            category = "PLAYER_ERROR",
            level = AppLog.Level.ERROR,
            message = "Timestamp test",
            bypassMaster = true,
        )

        val entry = AppLog.history.value.last()
        assertTrue("Timestamp should be recent", entry.timestamp > 0)
        assertTrue("Timestamp should be within last minute", System.currentTimeMillis() - entry.timestamp < 60_000)
    }

    @Test
    fun `AppLog Entry preserves all Level values`() {
        val levels =
            listOf(
                AppLog.Level.VERBOSE,
                AppLog.Level.DEBUG,
                AppLog.Level.INFO,
                AppLog.Level.WARN,
                AppLog.Level.ERROR,
            )

        levels.forEach { level ->
            AppLog.log(
                category = "TEST",
                level = level,
                message = "Level: $level",
                bypassMaster = true,
            )

            val entry = AppLog.history.value.last()
            assertEquals("Level should match", level, entry.level)
        }
    }
}
