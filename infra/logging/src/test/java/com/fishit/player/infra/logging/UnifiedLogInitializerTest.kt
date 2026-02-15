package com.fishit.player.infra.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Unit tests for [UnifiedLogInitializer].
 *
 * Tests initialization behavior and tree planting.
 *
 * Current implementation behavior:
 * - Debug mode: plants DebugTree + LogBufferTree = 2 trees
 * - Release mode: plants NO trees (performance optimization, logging disabled)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // Use API 28 for compatibility
class UnifiedLogInitializerTest {
    @After
    fun teardown() {
        Timber.uprootAll()
        // Reset logging state to avoid cross-test contamination
        UnifiedLog.setEnabled(true)
        UnifiedLog.setMinLevel(UnifiedLog.Level.VERBOSE)
    }

    @Test
    fun `init plants DebugTree and LogBufferTree in debug mode`() {
        UnifiedLogInitializer.init(isDebug = true)

        // Debug mode plants DebugTree + LogBufferTree
        val treeCount = Timber.forest().size
        assertEquals("Expected DebugTree + LogBufferTree", 2, treeCount)
    }

    @Test
    fun `init plants only DebugTree when log buffer disabled`() {
        UnifiedLogInitializer.init(isDebug = true, enableLogBuffer = false)

        // Only DebugTree planted when log buffer is explicitly disabled
        val treeCount = Timber.forest().size
        assertEquals("Expected only DebugTree", 1, treeCount)
    }

    @Test
    fun `init plants no trees in release mode for performance`() {
        UnifiedLogInitializer.init(isDebug = false)

        // Release mode: NO trees planted (performance optimization)
        val treeCount = Timber.forest().size
        assertEquals("Expected no trees in release mode", 0, treeCount)
    }

    @Test
    fun `init clears existing trees before planting`() {
        // Plant a tree first
        Timber.plant(Timber.DebugTree())
        assertEquals(1, Timber.forest().size)

        // Initialize in debug mode
        UnifiedLogInitializer.init(isDebug = true)

        // Old tree cleared, new DebugTree + LogBufferTree planted
        assertEquals("Expected DebugTree + LogBufferTree after clearing", 2, Timber.forest().size)
    }

    @Test
    fun `init can be called multiple times safely`() {
        UnifiedLogInitializer.init(isDebug = true)
        UnifiedLogInitializer.init(isDebug = false)
        UnifiedLogInitializer.init(isDebug = true)

        // Last call was debug=true, so DebugTree + LogBufferTree
        assertEquals("Expected DebugTree + LogBufferTree after last init", 2, Timber.forest().size)
    }

    @Test
    fun `init with debug true enables verbose logging`() {
        UnifiedLogInitializer.init(isDebug = true)

        // In debug mode, all logs should work
        // This is a smoke test to ensure no exceptions are thrown
        UnifiedLog.v("Test", "verbose")
        UnifiedLog.d("Test", "debug")
        UnifiedLog.i("Test", "info")
        UnifiedLog.w("Test", "warn")
        UnifiedLog.e("Test", "error")

        // Verify debug mode configuration
        assertEquals("Expected DebugTree + LogBufferTree", 2, Timber.forest().size)
        assertTrue("Logging should be enabled in debug", UnifiedLog.isEnabled())
    }

    @Test
    fun `init with debug false disables logging for performance`() {
        UnifiedLogInitializer.init(isDebug = false)

        // In production mode, logging is completely disabled
        // This is a smoke test to ensure no exceptions are thrown even when disabled
        UnifiedLog.w("Test", "warn")
        UnifiedLog.e("Test", "error")

        // Verify release mode configuration: no trees, logging disabled
        assertEquals("Expected no trees in release mode", 0, Timber.forest().size)
        assertFalse("Logging should be disabled in release", UnifiedLog.isEnabled())
    }
}
