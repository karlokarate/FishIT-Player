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

        // Navigation should still work
        val navAction = resolve(DefaultTvScreenConfigs.forScreen(TvScreenId.PLAYER), TvKeyRole.DPAD_LEFT, ctx)
        assertEquals(TvAction.NAVIGATE_LEFT, navAction)

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
}
