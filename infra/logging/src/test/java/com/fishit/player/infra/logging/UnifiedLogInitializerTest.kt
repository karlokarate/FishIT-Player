package com.fishit.player.infra.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Unit tests for [UnifiedLogInitializer].
 *
 * Tests initialization behavior and tree planting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // Use API 28 for compatibility
class UnifiedLogInitializerTest {
    @After
    fun teardown() {
        Timber.uprootAll()
    }

    @Test
    fun `init plants DebugTree in debug mode`() {
        UnifiedLogInitializer.init(isDebug = true)

        // Verify a tree was planted (we can't easily check the type without reflection)
        // but we can verify that Timber is configured by checking forest size
        val treeCount = Timber.forest().size
        assertEquals("Expected exactly one tree to be planted", 1, treeCount)
    }

    @Test
    fun `init plants ProductionReportingTree in release mode`() {
        UnifiedLogInitializer.init(isDebug = false)

        // Verify a tree was planted
        val treeCount = Timber.forest().size
        assertEquals("Expected exactly one tree to be planted", 1, treeCount)
    }

    @Test
    fun `init clears existing trees before planting`() {
        // Plant a tree first
        Timber.plant(Timber.DebugTree())
        assertEquals(1, Timber.forest().size)

        // Initialize again
        UnifiedLogInitializer.init(isDebug = true)

        // Should still have exactly 1 tree (old one cleared)
        assertEquals(1, Timber.forest().size)
    }

    @Test
    fun `init can be called multiple times safely`() {
        UnifiedLogInitializer.init(isDebug = true)
        UnifiedLogInitializer.init(isDebug = false)
        UnifiedLogInitializer.init(isDebug = true)

        // Should have exactly 1 tree after multiple initializations
        assertEquals(1, Timber.forest().size)
    }

    @Test
    fun `init with debug true enables verbose logging`() {
        UnifiedLogInitializer.init(isDebug = true)

        // In debug mode, all logs should work
        // This is a smoke test to ensure no exceptions are thrown
        UnifiedLog.setMinLevel(UnifiedLog.Level.VERBOSE)
        UnifiedLog.v("Test", "verbose")
        UnifiedLog.d("Test", "debug")
        UnifiedLog.i("Test", "info")
        UnifiedLog.w("Test", "warn")
        UnifiedLog.e("Test", "error")

        // If we get here without exceptions, the test passes
        // Verify tree count to ensure initialization worked
        assertEquals(1, Timber.forest().size)
    }

    @Test
    fun `init with debug false configures production logging`() {
        UnifiedLogInitializer.init(isDebug = false)

        // In production mode, logs should work but only WARN/ERROR are output
        // This is a smoke test to ensure no exceptions are thrown
        UnifiedLog.w("Test", "warn")
        UnifiedLog.e("Test", "error")

        // If we get here without exceptions, the test passes
        // Verify tree count to ensure initialization worked
        assertEquals(1, Timber.forest().size)
    }
}
