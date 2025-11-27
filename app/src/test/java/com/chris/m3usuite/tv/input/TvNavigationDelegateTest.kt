package com.chris.m3usuite.tv.input

import com.chris.m3usuite.ui.focus.FocusZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TvNavigationDelegate] and [FocusKitNavigationDelegate].
 *
 * Phase 6 Task 5: Tests for FocusKit integration.
 *
 * These tests verify:
 * - NAVIGATE_* actions call the right FocusKit methods
 * - FOCUS_* actions map to correct FocusZoneIds
 * - Non-navigation/focus actions return false
 */
class TvNavigationDelegateTest {
    // ════════════════════════════════════════════════════════════════════════════
    // FocusKitNavigationDelegate.moveFocus Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `moveFocus with NAVIGATE_UP returns true`() {
        val delegate = FocusKitNavigationDelegate()
        // Note: FocusKit.moveDpadUp() always returns true per current implementation
        val result = delegate.moveFocus(TvAction.NAVIGATE_UP)
        assertTrue("NAVIGATE_UP should return true", result)
    }

    @Test
    fun `moveFocus with NAVIGATE_DOWN returns true`() {
        val delegate = FocusKitNavigationDelegate()
        val result = delegate.moveFocus(TvAction.NAVIGATE_DOWN)
        assertTrue("NAVIGATE_DOWN should return true", result)
    }

    @Test
    fun `moveFocus with NAVIGATE_LEFT returns true`() {
        val delegate = FocusKitNavigationDelegate()
        val result = delegate.moveFocus(TvAction.NAVIGATE_LEFT)
        assertTrue("NAVIGATE_LEFT should return true", result)
    }

    @Test
    fun `moveFocus with NAVIGATE_RIGHT returns true`() {
        val delegate = FocusKitNavigationDelegate()
        val result = delegate.moveFocus(TvAction.NAVIGATE_RIGHT)
        assertTrue("NAVIGATE_RIGHT should return true", result)
    }

    @Test
    fun `moveFocus with non-navigation action returns false`() {
        val delegate = FocusKitNavigationDelegate()

        assertFalse("PLAY_PAUSE should not be handled by moveFocus", delegate.moveFocus(TvAction.PLAY_PAUSE))
        assertFalse("SEEK_FORWARD_10S should not be handled by moveFocus", delegate.moveFocus(TvAction.SEEK_FORWARD_10S))
        assertFalse("OPEN_CC_MENU should not be handled by moveFocus", delegate.moveFocus(TvAction.OPEN_CC_MENU))
        assertFalse("BACK should not be handled by moveFocus", delegate.moveFocus(TvAction.BACK))
    }

    @Test
    fun `moveFocus with focus actions returns false`() {
        val delegate = FocusKitNavigationDelegate()

        // Focus actions should be handled by focusZone(), not moveFocus()
        assertFalse("FOCUS_QUICK_ACTIONS should not be handled by moveFocus", delegate.moveFocus(TvAction.FOCUS_QUICK_ACTIONS))
        assertFalse("FOCUS_TIMELINE should not be handled by moveFocus", delegate.moveFocus(TvAction.FOCUS_TIMELINE))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // FocusKitNavigationDelegate.focusZone Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `focusZone with non-focus action returns false`() {
        val delegate = FocusKitNavigationDelegate()

        assertFalse("PLAY_PAUSE should not be handled by focusZone", delegate.focusZone(TvAction.PLAY_PAUSE))
        assertFalse("NAVIGATE_UP should not be handled by focusZone", delegate.focusZone(TvAction.NAVIGATE_UP))
        assertFalse("BACK should not be handled by focusZone", delegate.focusZone(TvAction.BACK))
    }

    // Note: focusZone with FOCUS_QUICK_ACTIONS and FOCUS_TIMELINE would return false
    // because the zones are not registered in unit tests (no Compose composition).
    // These are better tested in instrumented/UI tests.

    @Test
    fun `focusZone with FOCUS_QUICK_ACTIONS attempts zone focus`() {
        val delegate = FocusKitNavigationDelegate()
        // Will return false because zone is not registered, but verifies mapping exists
        val result = delegate.focusZone(TvAction.FOCUS_QUICK_ACTIONS)
        // The zone is not registered in unit tests, so this returns false
        assertFalse("FOCUS_QUICK_ACTIONS returns false when zone not registered", result)
    }

    @Test
    fun `focusZone with FOCUS_TIMELINE attempts zone focus`() {
        val delegate = FocusKitNavigationDelegate()
        val result = delegate.focusZone(TvAction.FOCUS_TIMELINE)
        assertFalse("FOCUS_TIMELINE returns false when zone not registered", result)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // FocusKitNavigationDelegate.zoneForAction Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `zoneForAction maps FOCUS_QUICK_ACTIONS to QUICK_ACTIONS`() {
        val zone = FocusKitNavigationDelegate.zoneForAction(TvAction.FOCUS_QUICK_ACTIONS)
        assertEquals(FocusZoneId.QUICK_ACTIONS, zone)
    }

    @Test
    fun `zoneForAction maps FOCUS_TIMELINE to TIMELINE`() {
        val zone = FocusKitNavigationDelegate.zoneForAction(TvAction.FOCUS_TIMELINE)
        assertEquals(FocusZoneId.TIMELINE, zone)
    }

    @Test
    fun `zoneForAction returns null for non-focus actions`() {
        assertNull(FocusKitNavigationDelegate.zoneForAction(TvAction.PLAY_PAUSE))
        assertNull(FocusKitNavigationDelegate.zoneForAction(TvAction.NAVIGATE_UP))
        assertNull(FocusKitNavigationDelegate.zoneForAction(TvAction.SEEK_FORWARD_10S))
        assertNull(FocusKitNavigationDelegate.zoneForAction(TvAction.OPEN_CC_MENU))
        assertNull(FocusKitNavigationDelegate.zoneForAction(TvAction.BACK))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // NoOpTvNavigationDelegate Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `NoOpTvNavigationDelegate moveFocus always returns false`() {
        val delegate = NoOpTvNavigationDelegate

        assertFalse(delegate.moveFocus(TvAction.NAVIGATE_UP))
        assertFalse(delegate.moveFocus(TvAction.NAVIGATE_DOWN))
        assertFalse(delegate.moveFocus(TvAction.NAVIGATE_LEFT))
        assertFalse(delegate.moveFocus(TvAction.NAVIGATE_RIGHT))
    }

    @Test
    fun `NoOpTvNavigationDelegate focusZone always returns false`() {
        val delegate = NoOpTvNavigationDelegate

        assertFalse(delegate.focusZone(TvAction.FOCUS_QUICK_ACTIONS))
        assertFalse(delegate.focusZone(TvAction.FOCUS_TIMELINE))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // All Navigation Actions Coverage
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `moveFocus handles all navigation actions`() {
        val delegate = FocusKitNavigationDelegate()
        val navigationActions =
            listOf(
                TvAction.NAVIGATE_UP,
                TvAction.NAVIGATE_DOWN,
                TvAction.NAVIGATE_LEFT,
                TvAction.NAVIGATE_RIGHT,
            )

        navigationActions.forEach { action ->
            assertTrue(
                "moveFocus should handle $action",
                delegate.moveFocus(action),
            )
        }
    }

    @Test
    fun `all focus actions have zone mappings`() {
        val focusActions =
            listOf(
                TvAction.FOCUS_QUICK_ACTIONS,
                TvAction.FOCUS_TIMELINE,
            )

        focusActions.forEach { action ->
            val zone = FocusKitNavigationDelegate.zoneForAction(action)
            assertNotNull("Focus action $action should have a zone mapping", zone)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // TvNavigationDelegate Interface Contract Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `FocusKitNavigationDelegate implements TvNavigationDelegate`() {
        val delegate: TvNavigationDelegate = FocusKitNavigationDelegate()
        // Compilation success proves implementation
        assertTrue(delegate is TvNavigationDelegate)
    }

    @Test
    fun `NoOpTvNavigationDelegate implements TvNavigationDelegate`() {
        val delegate: TvNavigationDelegate = NoOpTvNavigationDelegate
        assertTrue(delegate is TvNavigationDelegate)
    }
}
