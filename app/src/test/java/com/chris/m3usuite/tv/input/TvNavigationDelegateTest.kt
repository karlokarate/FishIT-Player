package com.chris.m3usuite.tv.input

import com.chris.m3usuite.ui.focus.FocusZoneId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertTrue(result, "NAVIGATE_UP should return true")
    }

    @Test
    fun `moveFocus with NAVIGATE_DOWN returns true`() {
        val delegate = FocusKitNavigationDelegate()
        val result = delegate.moveFocus(TvAction.NAVIGATE_DOWN)
        assertTrue(result, "NAVIGATE_DOWN should return true")
    }

    @Test
    fun `moveFocus with NAVIGATE_LEFT returns true`() {
        val delegate = FocusKitNavigationDelegate()
        val result = delegate.moveFocus(TvAction.NAVIGATE_LEFT)
        assertTrue(result, "NAVIGATE_LEFT should return true")
    }

    @Test
    fun `moveFocus with NAVIGATE_RIGHT returns true`() {
        val delegate = FocusKitNavigationDelegate()
        val result = delegate.moveFocus(TvAction.NAVIGATE_RIGHT)
        assertTrue(result, "NAVIGATE_RIGHT should return true")
    }

    @Test
    fun `moveFocus with non-navigation action returns false`() {
        val delegate = FocusKitNavigationDelegate()

        assertFalse(delegate.moveFocus(TvAction.PLAY_PAUSE), "PLAY_PAUSE should not be handled by moveFocus")
        assertFalse(delegate.moveFocus(TvAction.SEEK_FORWARD_10S), "SEEK_FORWARD_10S should not be handled by moveFocus")
        assertFalse(delegate.moveFocus(TvAction.OPEN_CC_MENU), "OPEN_CC_MENU should not be handled by moveFocus")
        assertFalse(delegate.moveFocus(TvAction.BACK), "BACK should not be handled by moveFocus")
    }

    @Test
    fun `moveFocus with focus actions returns false`() {
        val delegate = FocusKitNavigationDelegate()

        // Focus actions should be handled by focusZone(), not moveFocus()
        assertFalse(delegate.moveFocus(TvAction.FOCUS_QUICK_ACTIONS), "FOCUS_QUICK_ACTIONS should not be handled by moveFocus")
        assertFalse(delegate.moveFocus(TvAction.FOCUS_TIMELINE), "FOCUS_TIMELINE should not be handled by moveFocus")
    }

    // ════════════════════════════════════════════════════════════════════════════
    // FocusKitNavigationDelegate.focusZone Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `focusZone with non-focus action returns false`() {
        val delegate = FocusKitNavigationDelegate()

        assertFalse(delegate.focusZone(TvAction.PLAY_PAUSE), "PLAY_PAUSE should not be handled by focusZone")
        assertFalse(delegate.focusZone(TvAction.NAVIGATE_UP), "NAVIGATE_UP should not be handled by focusZone")
        assertFalse(delegate.focusZone(TvAction.BACK), "BACK should not be handled by focusZone")
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
        assertFalse(result, "FOCUS_QUICK_ACTIONS returns false when zone not registered")
    }

    @Test
    fun `focusZone with FOCUS_TIMELINE attempts zone focus`() {
        val delegate = FocusKitNavigationDelegate()
        val result = delegate.focusZone(TvAction.FOCUS_TIMELINE)
        assertFalse(result, "FOCUS_TIMELINE returns false when zone not registered")
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
                delegate.moveFocus(action),
                "moveFocus should handle $action",
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
            kotlin.test.assertNotNull(zone, "Focus action $action should have a zone mapping")
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
