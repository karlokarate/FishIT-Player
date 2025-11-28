package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DefaultTvInputController].
 *
 * Verifies:
 * - TvScreenInputConfig resolution
 * - Quick actions visibility toggling
 * - Focused action tracking
 * - Navigation delegate dispatch
 * - Action listener dispatch
 * - State reset behavior
 */
class TvInputControllerBasicTest {
    private lateinit var controller: DefaultTvInputController
    private lateinit var mockNavigationDelegate: MockTvNavigationDelegate
    private lateinit var mockActionListener: MockTvActionListener

    @Before
    fun setup() {
        mockNavigationDelegate = MockTvNavigationDelegate()
        mockActionListener = MockTvActionListener()
        controller =
            DefaultTvInputController(
                configs = DefaultTvScreenConfigs.all,
                navigationDelegate = mockNavigationDelegate,
            )
        controller.actionListener = mockActionListener
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CONFIG RESOLUTION TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `onKeyEvent resolves action from config`() {
        // PLAYER screen: FAST_FORWARD -> SEEK_FORWARD_30S
        val ctx = TvScreenContext.player()
        val handled = controller.onKeyEvent(TvKeyRole.FAST_FORWARD, ctx)

        // The action listener should have received SEEK_FORWARD_30S
        assertEquals(TvAction.SEEK_FORWARD_30S, mockActionListener.lastAction)
    }

    @Test
    fun `onKeyEvent returns false for unmapped roles`() {
        // Use NUM_0 which is mapped to null in PLAYER config
        val ctx = TvScreenContext.player()
        val handled = controller.onKeyEvent(TvKeyRole.NUM_0, ctx)

        assertFalse(handled)
        assertNull(mockActionListener.lastAction)
    }

    @Test
    fun `onKeyEvent applies Kids Mode filtering`() {
        // PLAYER screen with kid profile: FAST_FORWARD should be blocked
        val ctx =
            TvScreenContext.player(
                isKidProfile = true,
            )
        val handled = controller.onKeyEvent(TvKeyRole.FAST_FORWARD, ctx)

        // Action should be blocked (filtered) for kids
        assertFalse(handled)
        assertNull(mockActionListener.lastAction)
    }

    @Test
    fun `onKeyEvent applies overlay blocking`() {
        // PLAYER screen with blocking overlay: Only NAVIGATE_* and BACK allowed
        val ctx =
            TvScreenContext.player(
                hasBlockingOverlay = true,
            )

        // FAST_FORWARD should be blocked
        val handled = controller.onKeyEvent(TvKeyRole.FAST_FORWARD, ctx)
        assertFalse(handled)
        assertNull(mockActionListener.lastAction)
    }

    @Test
    fun `onKeyEvent allows BACK in overlay`() {
        // BACK should be allowed in blocking overlay
        val ctx =
            TvScreenContext.player(
                hasBlockingOverlay = true,
            )
        val handled = controller.onKeyEvent(TvKeyRole.BACK, ctx)

        // BACK resolves to TvAction.BACK which is allowed
        assertTrue(handled)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // QUICK ACTIONS VISIBILITY TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `quickActionsVisible is initially false`() {
        assertFalse(controller.quickActionsVisible.value)
    }

    @Test
    fun `OPEN_QUICK_ACTIONS sets quickActionsVisible to true`() {
        // Create a config that explicitly maps a key to OPEN_QUICK_ACTIONS
        // Note: In the default PLAYER config, DPAD_UP maps to FOCUS_QUICK_ACTIONS (focus zone)
        // and MENU maps to OPEN_PLAYER_MENU.
        // This test verifies the controller behavior when OPEN_QUICK_ACTIONS is resolved.
        val customConfigs =
            tvInputConfig {
                screen(TvScreenId.PLAYER) {
                    on(TvKeyRole.MENU) mapsTo TvAction.OPEN_QUICK_ACTIONS
                }
            }
        val customController =
            DefaultTvInputController(
                configs = customConfigs,
                navigationDelegate = mockNavigationDelegate,
            )
        val ctx = TvScreenContext.player()

        // MENU -> OPEN_QUICK_ACTIONS with custom config
        customController.onKeyEvent(TvKeyRole.MENU, ctx)

        assertTrue(customController.quickActionsVisible.value)
    }

    @Test
    fun `BACK when quickActionsVisible closes quick actions`() {
        val ctx = TvScreenContext.player()

        // First, open quick actions
        controller.setQuickActionsVisible(true)
        assertTrue(controller.quickActionsVisible.value)

        // BACK should close them
        controller.onKeyEvent(TvKeyRole.BACK, ctx)

        assertFalse(controller.quickActionsVisible.value)
    }

    @Test
    fun `BACK when quickActionsVisible handles the event`() {
        val ctx = TvScreenContext.player()

        // Open quick actions
        controller.setQuickActionsVisible(true)

        // BACK should handle the event (return true)
        val handled = controller.onKeyEvent(TvKeyRole.BACK, ctx)
        assertTrue(handled)

        // And action listener should NOT have received BACK
        assertNotEquals(TvAction.BACK, mockActionListener.lastAction)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // FOCUSED ACTION TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `focusedAction is initially null`() {
        assertNull(controller.focusedAction.value)
    }

    @Test
    fun `navigation actions update focusedAction`() {
        val ctx = TvScreenContext.library()

        // DPAD_UP -> NAVIGATE_UP on LIBRARY screen
        controller.onKeyEvent(TvKeyRole.DPAD_UP, ctx)

        assertEquals(TvAction.NAVIGATE_UP, controller.focusedAction.value)
    }

    @Test
    fun `focus actions update focusedAction`() {
        val ctx = TvScreenContext.player()

        // DPAD_UP -> FOCUS_QUICK_ACTIONS on PLAYER screen
        controller.onKeyEvent(TvKeyRole.DPAD_UP, ctx)

        assertEquals(TvAction.FOCUS_QUICK_ACTIONS, controller.focusedAction.value)
    }

    @Test
    fun `handled playback actions update focusedAction`() {
        mockActionListener.shouldHandle = true
        val ctx = TvScreenContext.player()

        // CENTER -> PLAY_PAUSE on PLAYER screen
        controller.onKeyEvent(TvKeyRole.DPAD_CENTER, ctx)

        assertEquals(TvAction.PLAY_PAUSE, controller.focusedAction.value)
    }

    @Test
    fun `unhandled playback actions do not update focusedAction`() {
        mockActionListener.shouldHandle = false
        val ctx = TvScreenContext.player()

        // Set initial focused action
        controller.setFocusedAction(TvAction.NAVIGATE_UP)

        // Playback action not handled by listener
        controller.onKeyEvent(TvKeyRole.DPAD_CENTER, ctx)

        // focusedAction should remain unchanged since action wasn't handled
        assertEquals(TvAction.NAVIGATE_UP, controller.focusedAction.value)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // NAVIGATION DELEGATE TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `navigation actions dispatch to TvNavigationDelegate`() {
        val ctx = TvScreenContext.library()

        // DPAD_DOWN -> NAVIGATE_DOWN on LIBRARY screen
        controller.onKeyEvent(TvKeyRole.DPAD_DOWN, ctx)

        assertEquals(TvAction.NAVIGATE_DOWN, mockNavigationDelegate.lastMoveFocusAction)
    }

    @Test
    fun `focus actions dispatch to TvNavigationDelegate focusZone`() {
        val ctx = TvScreenContext.player()

        // DPAD_DOWN -> FOCUS_TIMELINE on PLAYER screen
        controller.onKeyEvent(TvKeyRole.DPAD_DOWN, ctx)

        assertEquals(TvAction.FOCUS_TIMELINE, mockNavigationDelegate.lastFocusZoneAction)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ACTION LISTENER TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `playback actions dispatch to actionListener`() {
        val ctx = TvScreenContext.player()

        // PLAY_PAUSE key -> PLAY_PAUSE action
        controller.onKeyEvent(TvKeyRole.PLAY_PAUSE, ctx)

        assertEquals(TvAction.PLAY_PAUSE, mockActionListener.lastAction)
    }

    @Test
    fun `seek actions dispatch to actionListener`() {
        val ctx = TvScreenContext.player()

        // REWIND -> SEEK_BACKWARD_30S on PLAYER screen
        controller.onKeyEvent(TvKeyRole.REWIND, ctx)

        assertEquals(TvAction.SEEK_BACKWARD_30S, mockActionListener.lastAction)
    }

    @Test
    fun `channel actions dispatch to actionListener`() {
        val ctx = TvScreenContext.player(isLive = true)

        // CHANNEL_UP -> CHANNEL_UP action
        controller.onKeyEvent(TvKeyRole.CHANNEL_UP, ctx)

        assertEquals(TvAction.CHANNEL_UP, mockActionListener.lastAction)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `resetState clears quickActionsVisible`() {
        controller.setQuickActionsVisible(true)
        assertTrue(controller.quickActionsVisible.value)

        controller.resetState()

        assertFalse(controller.quickActionsVisible.value)
    }

    @Test
    fun `resetState clears focusedAction`() {
        controller.setFocusedAction(TvAction.PLAY_PAUSE)
        assertNotNull(controller.focusedAction.value)

        controller.resetState()

        assertNull(controller.focusedAction.value)
    }

    @Test
    fun `setQuickActionsVisible updates state`() {
        assertFalse(controller.quickActionsVisible.value)

        controller.setQuickActionsVisible(true)
        assertTrue(controller.quickActionsVisible.value)

        controller.setQuickActionsVisible(false)
        assertFalse(controller.quickActionsVisible.value)
    }

    @Test
    fun `setFocusedAction updates state`() {
        assertNull(controller.focusedAction.value)

        controller.setFocusedAction(TvAction.SEEK_FORWARD_10S)
        assertEquals(TvAction.SEEK_FORWARD_10S, controller.focusedAction.value)

        controller.setFocusedAction(null)
        assertNull(controller.focusedAction.value)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SCREEN-SPECIFIC CONFIG TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `LIBRARY screen uses ROW_FAST_SCROLL for FF_RW`() {
        val ctx = TvScreenContext.library()

        controller.onKeyEvent(TvKeyRole.FAST_FORWARD, ctx)
        assertEquals(TvAction.ROW_FAST_SCROLL_FORWARD, mockActionListener.lastAction)

        controller.onKeyEvent(TvKeyRole.REWIND, ctx)
        assertEquals(TvAction.ROW_FAST_SCROLL_BACKWARD, mockActionListener.lastAction)
    }

    @Test
    fun `SETTINGS screen maps FF_RW to tab switching`() {
        val ctx = TvScreenContext.settings()

        // DPAD navigation works
        controller.onKeyEvent(TvKeyRole.DPAD_UP, ctx)
        assertEquals(TvAction.NAVIGATE_UP, mockNavigationDelegate.lastMoveFocusAction)

        // FF/RW are mapped to tab switching in SETTINGS per behavior map
        mockNavigationDelegate.lastMoveFocusAction = null
        mockActionListener.lastAction = null

        controller.onKeyEvent(TvKeyRole.FAST_FORWARD, ctx)
        assertEquals(TvAction.SWITCH_SETTINGS_TAB_NEXT, mockActionListener.lastAction)

        controller.onKeyEvent(TvKeyRole.REWIND, ctx)
        assertEquals(TvAction.SWITCH_SETTINGS_TAB_PREVIOUS, mockActionListener.lastAction)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // MOCKS
    // ════════════════════════════════════════════════════════════════════════════

    private class MockTvNavigationDelegate : TvNavigationDelegate {
        var lastMoveFocusAction: TvAction? = null
        var lastFocusZoneAction: TvAction? = null
        var shouldHandleMoveFocus = false
        var shouldHandleFocusZone = false

        override fun moveFocus(action: TvAction): Boolean {
            lastMoveFocusAction = action
            return shouldHandleMoveFocus
        }

        override fun focusZone(action: TvAction): Boolean {
            lastFocusZoneAction = action
            return shouldHandleFocusZone
        }
    }

    private class MockTvActionListener : TvActionListener {
        var lastAction: TvAction? = null
        var shouldHandle = true

        override fun onAction(action: TvAction): Boolean {
            lastAction = action
            return shouldHandle
        }
    }
}
