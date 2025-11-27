package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for overlay blocking input filtering.
 *
 * Tests verify that when ctx.hasBlockingOverlay is true, only NAVIGATE_* and BACK
 * actions are allowed.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 8.1
 */
class OverlayBlockingTest {
    // ══════════════════════════════════════════════════════════════════
    // ALLOWED ACTIONS IN OVERLAY
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `NAVIGATE_UP is allowed with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.NAVIGATE_UP, ctx)

        assertEquals(TvAction.NAVIGATE_UP, result)
    }

    @Test
    fun `NAVIGATE_DOWN is allowed with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.NAVIGATE_DOWN, ctx)

        assertEquals(TvAction.NAVIGATE_DOWN, result)
    }

    @Test
    fun `NAVIGATE_LEFT is allowed with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.NAVIGATE_LEFT, ctx)

        assertEquals(TvAction.NAVIGATE_LEFT, result)
    }

    @Test
    fun `NAVIGATE_RIGHT is allowed with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.NAVIGATE_RIGHT, ctx)

        assertEquals(TvAction.NAVIGATE_RIGHT, result)
    }

    @Test
    fun `BACK is allowed with blocking overlay to close it`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.BACK, ctx)

        assertEquals(TvAction.BACK, result)
    }

    // ══════════════════════════════════════════════════════════════════
    // BLOCKED ACTIONS IN OVERLAY
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAY_PAUSE is blocked with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.PLAY_PAUSE, ctx)

        assertNull(result)
    }

    @Test
    fun `SEEK_FORWARD_30S is blocked with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.SEEK_FORWARD_30S, ctx)

        assertNull(result)
    }

    @Test
    fun `SEEK_BACKWARD_30S is blocked with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.SEEK_BACKWARD_30S, ctx)

        assertNull(result)
    }

    @Test
    fun `OPEN_CC_MENU is blocked with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.OPEN_CC_MENU, ctx)

        assertNull(result)
    }

    @Test
    fun `OPEN_QUICK_ACTIONS is blocked with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.OPEN_QUICK_ACTIONS, ctx)

        assertNull(result)
    }

    @Test
    fun `CHANNEL_UP is blocked with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.CHANNEL_UP, ctx)

        assertNull(result)
    }

    @Test
    fun `PAGE_UP is blocked with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.PAGE_UP, ctx)

        assertNull(result)
    }

    @Test
    fun `FOCUS_QUICK_ACTIONS is blocked with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.FOCUS_QUICK_ACTIONS, ctx)

        assertNull(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // NO OVERLAY - ALL PASS THROUGH
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all actions pass through without blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = false)

        assertEquals(TvAction.PLAY_PAUSE, filterForOverlays(TvAction.PLAY_PAUSE, ctx))
        assertEquals(TvAction.SEEK_FORWARD_30S, filterForOverlays(TvAction.SEEK_FORWARD_30S, ctx))
        assertEquals(TvAction.OPEN_CC_MENU, filterForOverlays(TvAction.OPEN_CC_MENU, ctx))
        assertEquals(TvAction.CHANNEL_UP, filterForOverlays(TvAction.CHANNEL_UP, ctx))
        assertEquals(TvAction.PAGE_UP, filterForOverlays(TvAction.PAGE_UP, ctx))
        assertEquals(TvAction.FOCUS_TIMELINE, filterForOverlays(TvAction.FOCUS_TIMELINE, ctx))
    }

    // ══════════════════════════════════════════════════════════════════
    // NULL INPUT HANDLING
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `null action returns null with blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(null, ctx)

        assertNull(result)
    }

    @Test
    fun `null action returns null without blocking overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = false)
        val result = filterForOverlays(null, ctx)

        assertNull(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // INTEGRATION WITH RESOLVE
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `resolve blocks non-navigation actions with overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)

        // FAST_FORWARD maps to SEEK_FORWARD_30S, but overlay blocks it
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx)
        assertNull(action)
    }

    @Test
    fun `resolve allows navigation with overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)

        // DPAD_LEFT maps to NAVIGATE_LEFT, which is allowed in overlay
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_LEFT, ctx)
        assertEquals(TvAction.NAVIGATE_LEFT, action)
    }

    @Test
    fun `resolve allows BACK with overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)

        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx)
        assertEquals(TvAction.BACK, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // PROFILE GATE (ALWAYS HAS BLOCKING OVERLAY)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `ProfileGate context has blocking overlay by default`() {
        val ctx = TvScreenContext.profileGate()

        // Verify hasBlockingOverlay is true
        assertEquals(true, ctx.hasBlockingOverlay)
    }

    @Test
    fun `ProfileGate allows navigation`() {
        val ctx = TvScreenContext.profileGate()

        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PROFILE_GATE, TvKeyRole.DPAD_UP, ctx)
        assertEquals(TvAction.NAVIGATE_UP, action)
    }

    @Test
    fun `ProfileGate allows BACK`() {
        val ctx = TvScreenContext.profileGate()

        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PROFILE_GATE, TvKeyRole.BACK, ctx)
        assertEquals(TvAction.BACK, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // COMPREHENSIVE BLOCKING TEST
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all non-navigation actions are blocked in overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)

        val blockedActions =
            listOf(
                TvAction.PLAY_PAUSE,
                TvAction.SEEK_FORWARD_10S,
                TvAction.SEEK_FORWARD_30S,
                TvAction.SEEK_BACKWARD_10S,
                TvAction.SEEK_BACKWARD_30S,
                TvAction.OPEN_CC_MENU,
                TvAction.OPEN_ASPECT_MENU,
                TvAction.OPEN_QUICK_ACTIONS,
                TvAction.OPEN_LIVE_LIST,
                TvAction.PAGE_UP,
                TvAction.PAGE_DOWN,
                TvAction.FOCUS_QUICK_ACTIONS,
                TvAction.FOCUS_TIMELINE,
                TvAction.CHANNEL_UP,
                TvAction.CHANNEL_DOWN,
                // Phase 6 Task 4: Additional blocked actions
                TvAction.OPEN_PLAYER_MENU,
                TvAction.OPEN_DETAILS,
                TvAction.ROW_FAST_SCROLL_FORWARD,
                TvAction.ROW_FAST_SCROLL_BACKWARD,
                TvAction.PLAY_FOCUSED_RESUME,
                TvAction.OPEN_FILTER_SORT,
                TvAction.NEXT_EPISODE,
                TvAction.PREVIOUS_EPISODE,
                TvAction.OPEN_DETAIL_MENU,
                TvAction.ACTIVATE_FOCUSED_SETTING,
                TvAction.SWITCH_SETTINGS_TAB_NEXT,
                TvAction.SWITCH_SETTINGS_TAB_PREVIOUS,
                TvAction.OPEN_ADVANCED_SETTINGS,
                TvAction.SELECT_PROFILE,
                TvAction.OPEN_PROFILE_OPTIONS,
                TvAction.PIP_SEEK_FORWARD,
                TvAction.PIP_SEEK_BACKWARD,
                TvAction.PIP_TOGGLE_PLAY_PAUSE,
                TvAction.PIP_ENTER_RESIZE_MODE,
                TvAction.PIP_CONFIRM_RESIZE,
                TvAction.PIP_MOVE_LEFT,
                TvAction.PIP_MOVE_RIGHT,
                TvAction.PIP_MOVE_UP,
                TvAction.PIP_MOVE_DOWN,
                TvAction.EXIT_TO_HOME,
                TvAction.OPEN_GLOBAL_SEARCH,
            )

        for (action in blockedActions) {
            assertNull("$action should be blocked in overlay", filterForOverlays(action, ctx))
        }
    }

    @Test
    fun `only navigation and BACK allowed in overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)

        val allowedActions =
            listOf(
                TvAction.NAVIGATE_UP,
                TvAction.NAVIGATE_DOWN,
                TvAction.NAVIGATE_LEFT,
                TvAction.NAVIGATE_RIGHT,
                TvAction.BACK,
            )

        for (action in allowedActions) {
            assertNotNull("$action should be allowed in overlay", filterForOverlays(action, ctx))
            assertEquals(action, filterForOverlays(action, ctx))
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // COMBINED KIDS + OVERLAY FILTERING
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `kids filter and overlay filter work together`() {
        // Kid profile with blocking overlay - most restrictive
        val ctx = TvScreenContext.player(isKidProfile = true, hasBlockingOverlay = true)

        // Navigation should still work - but note DPAD_LEFT now maps to SEEK_BACKWARD_10S in PLAYER
        // which is blocked by kids filter, so we test with a manual navigation action
        val navResult = filterForOverlays(TvAction.NAVIGATE_LEFT, ctx)
        assertEquals(TvAction.NAVIGATE_LEFT, navResult)

        // BACK should still work
        val backAction = resolve(DefaultTvScreenConfigs.forScreen(TvScreenId.PLAYER), TvKeyRole.BACK, ctx)
        assertEquals(TvAction.BACK, backAction)

        // Seek should be blocked (by kids filter AND overlay filter)
        val seekAction = resolve(DefaultTvScreenConfigs.forScreen(TvScreenId.PLAYER), TvKeyRole.FAST_FORWARD, ctx)
        assertNull(seekAction)
    }

    @Test
    fun `overlay filter applied after kids filter`() {
        // Non-kid profile with blocking overlay
        val ctx = TvScreenContext.player(isKidProfile = false, hasBlockingOverlay = true)

        // Quick actions would pass kids filter but fail overlay filter
        val quickActionsConfig =
            TvScreenInputConfig(
                screenId = TvScreenId.PLAYER,
                bindings = mapOf(TvKeyRole.MENU to TvAction.OPEN_QUICK_ACTIONS),
            )

        val action = resolve(quickActionsConfig, TvKeyRole.MENU, ctx)
        assertNull(action) // Blocked by overlay filter, even though allowed by kids filter
    }

    // ══════════════════════════════════════════════════════════════════
    // PHASE 6 TASK 4: NEW ACTION OVERLAY BLOCKING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PIP actions are blocked in overlay`() {
        val ctx = TvScreenContext.miniPlayer().copy(hasBlockingOverlay = true)

        val pipActions =
            listOf(
                TvAction.PIP_SEEK_FORWARD,
                TvAction.PIP_SEEK_BACKWARD,
                TvAction.PIP_TOGGLE_PLAY_PAUSE,
                TvAction.PIP_ENTER_RESIZE_MODE,
                TvAction.PIP_CONFIRM_RESIZE,
                TvAction.PIP_MOVE_LEFT,
                TvAction.PIP_MOVE_RIGHT,
                TvAction.PIP_MOVE_UP,
                TvAction.PIP_MOVE_DOWN,
            )

        for (action in pipActions) {
            assertNull("$action should be blocked in overlay", filterForOverlays(action, ctx))
        }
    }

    @Test
    fun `library actions are blocked in overlay`() {
        val ctx = TvScreenContext.library().copy(hasBlockingOverlay = true)

        val libraryActions =
            listOf(
                TvAction.OPEN_DETAILS,
                TvAction.ROW_FAST_SCROLL_FORWARD,
                TvAction.ROW_FAST_SCROLL_BACKWARD,
                TvAction.PLAY_FOCUSED_RESUME,
                TvAction.OPEN_FILTER_SORT,
            )

        for (action in libraryActions) {
            assertNull("$action should be blocked in overlay", filterForOverlays(action, ctx))
        }
    }

    @Test
    fun `EXIT_TO_HOME is blocked in overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.EXIT_TO_HOME, ctx)
        assertNull(result)
    }

    @Test
    fun `OPEN_GLOBAL_SEARCH is blocked in overlay`() {
        val ctx = TvScreenContext.library().copy(hasBlockingOverlay = true)
        val result = filterForOverlays(TvAction.OPEN_GLOBAL_SEARCH, ctx)
        assertNull(result)
    }
}
