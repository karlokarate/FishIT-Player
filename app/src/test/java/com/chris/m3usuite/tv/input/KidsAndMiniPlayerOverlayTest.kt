package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for combined Kids Mode, MiniPlayer, and Overlay filtering.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 TASK 3: Validation, Navigation Hardening & Cleanup
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **Purpose:**
 * Verify that Kids Mode, MiniPlayer visibility, and overlay blocking filters
 * compose correctly when multiple conditions are active simultaneously.
 *
 * **Contract References:**
 * - docs/INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 7 (Kids Mode)
 * - docs/INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 8 (Overlays)
 * - docs/INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 5 (MiniPlayer filter)
 *
 * **Blocking Priorities:**
 * 1. Kids Mode → Blocks SEEK_*, OPEN_CC_MENU, OPEN_ASPECT_MENU, OPEN_LIVE_LIST,
 *                PIP_SEEK_*, OPEN_ADVANCED_SETTINGS
 * 2. Overlay → Blocks everything except NAVIGATE_* and BACK
 * 3. MiniPlayer visible → Blocks ROW_FAST_SCROLL_FORWARD, ROW_FAST_SCROLL_BACKWARD
 */
class KidsAndMiniPlayerOverlayTest {
    // ══════════════════════════════════════════════════════════════════
    // COMBINED FILTERS: Kids Mode + MiniPlayer + Overlay
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `triple filter - almost everything blocked except safe navigation`() {
        // Scenario: Kid profile + MiniPlayer visible + Blocking overlay
        val ctx =
            TvScreenContext.library(
                isKidProfile = true,
                isMiniPlayerVisible = true,
                hasBlockingOverlay = true,
            )

        // Only NAVIGATE_* and BACK should pass through
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_DOWN,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_DOWN, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_LEFT,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_LEFT, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_RIGHT,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_RIGHT, ctx),
        )
        assertEquals(
            TvAction.BACK,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.BACK, ctx),
        )

        // Everything else should be blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.REWIND, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.MENU, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_CENTER, ctx))
    }

    // ══════════════════════════════════════════════════════════════════
    // KIDS MODE + MINI PLAYER (no overlay)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Kids + MiniPlayer - seek blocked, fast scroll blocked, navigation allowed`() {
        val playerCtx = TvScreenContext.player(isKidProfile = true, isMiniPlayerVisible = true)
        val libraryCtx = TvScreenContext.library(isKidProfile = true, isMiniPlayerVisible = true)

        // In PLAYER: Seek is blocked by Kids Mode
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_LEFT, playerCtx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, playerCtx))

        // In LIBRARY: ROW_FAST_SCROLL is blocked by MiniPlayer visibility
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, libraryCtx))

        // Navigation still works
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, libraryCtx),
        )

        // PLAY_FOCUSED_RESUME still works for kids in LIBRARY
        assertEquals(
            TvAction.PLAY_FOCUSED_RESUME,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, libraryCtx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // KIDS MODE + OVERLAY (no MiniPlayer)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Kids + Overlay - overlay blocking takes precedence`() {
        val ctx = TvScreenContext.player(isKidProfile = true, hasBlockingOverlay = true)

        // Even though PLAY_PAUSE is allowed for kids, overlay blocks it
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.PLAY_PAUSE, ctx))

        // BACK is allowed both by kids and overlay
        assertEquals(
            TvAction.BACK,
            DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx),
        )

        // Seek is blocked by kids, and would be blocked by overlay anyway
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx))
    }

    // ══════════════════════════════════════════════════════════════════
    // MINI PLAYER + OVERLAY (no Kids Mode)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MiniPlayer + Overlay - overlay blocking takes precedence`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true, hasBlockingOverlay = true)

        // Everything blocked except NAVIGATE + BACK
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.MENU, ctx))

        // NAVIGATE allowed
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx),
        )

        // BACK allowed
        assertEquals(
            TvAction.BACK,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.BACK, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // MINI PLAYER ONLY - ROW_FAST_SCROLL blocked
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MiniPlayer visible blocks ROW_FAST_SCROLL but allows other actions`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)

        // ROW_FAST_SCROLL blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.REWIND, ctx))

        // Other actions still work
        assertEquals(
            TvAction.OPEN_DETAILS,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_CENTER, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx),
        )
        assertEquals(
            TvAction.PLAY_FOCUSED_RESUME,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, ctx),
        )
        assertEquals(
            TvAction.OPEN_FILTER_SORT,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.MENU, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // KIDS MODE ONLY - Seek blocked
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Kids Mode blocks seek but allows ROW_FAST_SCROLL in LIBRARY`() {
        val playerCtx = TvScreenContext.player(isKidProfile = true)
        val libraryCtx = TvScreenContext.library(isKidProfile = true)

        // In PLAYER: Seek is blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, playerCtx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_LEFT, playerCtx))

        // In LIBRARY: ROW_FAST_SCROLL is allowed for kids
        assertEquals(
            TvAction.ROW_FAST_SCROLL_FORWARD,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, libraryCtx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // OVERLAY ONLY - Navigation + BACK allowed
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Overlay blocking allows only NAVIGATE and BACK`() {
        val ctx = TvScreenContext.library(hasBlockingOverlay = true)

        // NAVIGATE allowed
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_DOWN,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_DOWN, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_LEFT,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_LEFT, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_RIGHT,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_RIGHT, ctx),
        )

        // BACK allowed
        assertEquals(
            TvAction.BACK,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.BACK, ctx),
        )

        // Everything else blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.REWIND, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.MENU, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_CENTER, ctx))
    }

    // ══════════════════════════════════════════════════════════════════
    // PLAYER SCREEN with Overlay (important for showing controls)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAYER with overlay - DPAD seek blocked by overlay`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)

        // In PLAYER, DPAD_LEFT normally maps to SEEK_BACKWARD_10S
        // With overlay active, this non-navigation action is blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_LEFT, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_RIGHT, ctx))

        // DPAD_UP maps to FOCUS_QUICK_ACTIONS which is a focus action, not navigation
        // Focus actions are blocked by overlay
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_UP, ctx))

        // BACK still works
        assertEquals(
            TvAction.BACK,
            DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // MINI_PLAYER SCREEN with Kids Mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MINI_PLAYER with Kids Mode - PIP seek blocked`() {
        val ctx = TvScreenContext.miniPlayer(isKidProfile = true)

        // PIP seek blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.FAST_FORWARD, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.REWIND, ctx))

        // PIP play/pause allowed
        assertEquals(
            TvAction.PIP_TOGGLE_PLAY_PAUSE,
            DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.PLAY_PAUSE, ctx),
        )

        // PIP move allowed (these are not seek/restricted actions)
        assertEquals(
            TvAction.PIP_MOVE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_UP, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // SETTINGS SCREEN with Kids Mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `SETTINGS with Kids Mode - advanced settings blocked`() {
        val ctx = TvScreenContext.settings(isKidProfile = true)

        // OPEN_ADVANCED_SETTINGS blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.MENU, ctx))

        // Navigation and ACTIVATE_FOCUSED_SETTING still work
        assertEquals(
            TvAction.ACTIVATE_FOCUSED_SETTING,
            DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.DPAD_CENTER, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.DPAD_UP, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `no filters active - all actions pass through`() {
        val ctx = TvScreenContext.library()

        // All actions should work
        assertEquals(
            TvAction.OPEN_DETAILS,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_CENTER, ctx),
        )
        assertEquals(
            TvAction.ROW_FAST_SCROLL_FORWARD,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx),
        )
        assertEquals(
            TvAction.PLAY_FOCUSED_RESUME,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, ctx),
        )
        assertEquals(
            TvAction.OPEN_FILTER_SORT,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.MENU, ctx),
        )
    }

    @Test
    fun `filter order does not affect result`() {
        // The same final result should occur regardless of filter order
        val ctx1 = TvScreenContext.library(isKidProfile = true, isMiniPlayerVisible = true, hasBlockingOverlay = true)
        val ctx2 = TvScreenContext.library(hasBlockingOverlay = true, isKidProfile = true, isMiniPlayerVisible = true)

        // Same result for both contexts
        assertEquals(
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx1),
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx2),
        )
        assertEquals(
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx1),
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx2),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // CONTRACT VERIFICATION: Blocked actions list
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Kids Mode blocks expected actions per contract`() {
        val ctx = TvScreenContext.player(isKidProfile = true)

        // Contract Section 7.1: Blocked actions for kids
        val blockedActions =
            listOf(
                TvAction.SEEK_FORWARD_10S,
                TvAction.SEEK_FORWARD_30S,
                TvAction.SEEK_BACKWARD_10S,
                TvAction.SEEK_BACKWARD_30S,
                TvAction.OPEN_CC_MENU,
                TvAction.OPEN_ASPECT_MENU,
                TvAction.OPEN_LIVE_LIST,
                TvAction.PIP_SEEK_FORWARD,
                TvAction.PIP_SEEK_BACKWARD,
                TvAction.OPEN_ADVANCED_SETTINGS,
            )

        for (action in blockedActions) {
            assertNull("$action should be blocked for kids", filterForKidsMode(action, ctx))
        }
    }

    @Test
    fun `Kids Mode allows expected actions per contract`() {
        val ctx = TvScreenContext.player(isKidProfile = true)

        // Contract Section 7.1: Allowed actions for kids
        val allowedActions =
            listOf(
                TvAction.NAVIGATE_UP,
                TvAction.NAVIGATE_DOWN,
                TvAction.NAVIGATE_LEFT,
                TvAction.NAVIGATE_RIGHT,
                TvAction.BACK,
                TvAction.PLAY_PAUSE,
                TvAction.OPEN_QUICK_ACTIONS,
                TvAction.PAGE_UP,
                TvAction.PAGE_DOWN,
                TvAction.CHANNEL_UP,
                TvAction.CHANNEL_DOWN,
                TvAction.FOCUS_QUICK_ACTIONS,
                TvAction.FOCUS_TIMELINE,
            )

        for (action in allowedActions) {
            assertEquals("$action should be allowed for kids", action, filterForKidsMode(action, ctx))
        }
    }

    @Test
    fun `Overlay allows only NAVIGATE and BACK per contract`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)

        // Contract Section 8.1: Only NAVIGATE_* and BACK allowed
        val allowedActions =
            listOf(
                TvAction.NAVIGATE_UP,
                TvAction.NAVIGATE_DOWN,
                TvAction.NAVIGATE_LEFT,
                TvAction.NAVIGATE_RIGHT,
                TvAction.BACK,
            )

        for (action in allowedActions) {
            assertEquals("$action should be allowed in overlay", action, filterForOverlays(action, ctx))
        }

        // Everything else blocked
        val blockedActions =
            listOf(
                TvAction.PLAY_PAUSE,
                TvAction.SEEK_FORWARD_10S,
                TvAction.OPEN_QUICK_ACTIONS,
                TvAction.ROW_FAST_SCROLL_FORWARD,
                TvAction.OPEN_DETAILS,
            )

        for (action in blockedActions) {
            assertNull("$action should be blocked in overlay", filterForOverlays(action, ctx))
        }
    }

    @Test
    fun `MiniPlayer filter blocks only ROW_FAST_SCROLL per contract`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)

        // Contract Phase 7 Section 5: Only ROW_FAST_SCROLL blocked
        assertNull(filterForMiniPlayer(TvAction.ROW_FAST_SCROLL_FORWARD, ctx))
        assertNull(filterForMiniPlayer(TvAction.ROW_FAST_SCROLL_BACKWARD, ctx))

        // Everything else passes through
        val allowedActions =
            listOf(
                TvAction.NAVIGATE_UP,
                TvAction.PLAY_PAUSE,
                TvAction.SEEK_FORWARD_10S,
                TvAction.OPEN_DETAILS,
                TvAction.BACK,
            )

        for (action in allowedActions) {
            assertEquals("$action should pass through MiniPlayer filter", action, filterForMiniPlayer(action, ctx))
        }
    }
}
