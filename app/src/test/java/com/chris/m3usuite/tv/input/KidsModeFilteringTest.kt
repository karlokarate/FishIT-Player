package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for Kids Mode input filtering.
 *
 * Tests verify that SEEK/FF/RW/OPEN_* actions are blocked for kid profiles,
 * while DPAD and BACK remain allowed.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 7.1
 */
class KidsModeFilteringTest {
    // ══════════════════════════════════════════════════════════════════
    // BLOCKED ACTIONS FOR KIDS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `SEEK_FORWARD_10S is blocked for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.SEEK_FORWARD_10S, ctx)

        assertNull(result)
    }

    @Test
    fun `SEEK_FORWARD_30S is blocked for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.SEEK_FORWARD_30S, ctx)

        assertNull(result)
    }

    @Test
    fun `SEEK_BACKWARD_10S is blocked for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.SEEK_BACKWARD_10S, ctx)

        assertNull(result)
    }

    @Test
    fun `SEEK_BACKWARD_30S is blocked for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.SEEK_BACKWARD_30S, ctx)

        assertNull(result)
    }

    @Test
    fun `OPEN_CC_MENU is blocked for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.OPEN_CC_MENU, ctx)

        assertNull(result)
    }

    @Test
    fun `OPEN_ASPECT_MENU is blocked for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.OPEN_ASPECT_MENU, ctx)

        assertNull(result)
    }

    @Test
    fun `OPEN_LIVE_LIST is blocked for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.OPEN_LIVE_LIST, ctx)

        assertNull(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // ALLOWED ACTIONS FOR KIDS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `NAVIGATE_UP is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.NAVIGATE_UP, ctx)

        assertEquals(TvAction.NAVIGATE_UP, result)
    }

    @Test
    fun `NAVIGATE_DOWN is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.NAVIGATE_DOWN, ctx)

        assertEquals(TvAction.NAVIGATE_DOWN, result)
    }

    @Test
    fun `NAVIGATE_LEFT is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.NAVIGATE_LEFT, ctx)

        assertEquals(TvAction.NAVIGATE_LEFT, result)
    }

    @Test
    fun `NAVIGATE_RIGHT is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.NAVIGATE_RIGHT, ctx)

        assertEquals(TvAction.NAVIGATE_RIGHT, result)
    }

    @Test
    fun `BACK is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.BACK, ctx)

        assertEquals(TvAction.BACK, result)
    }

    @Test
    fun `PLAY_PAUSE is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.PLAY_PAUSE, ctx)

        assertEquals(TvAction.PLAY_PAUSE, result)
    }

    @Test
    fun `OPEN_QUICK_ACTIONS is allowed for kid profile`() {
        // Per contract, MENU opens kids overlay (quick actions is allowed)
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.OPEN_QUICK_ACTIONS, ctx)

        assertEquals(TvAction.OPEN_QUICK_ACTIONS, result)
    }

    @Test
    fun `PAGE_UP is allowed for kid profile`() {
        val ctx = TvScreenContext.library(isKidProfile = true)
        val result = filterForKidsMode(TvAction.PAGE_UP, ctx)

        assertEquals(TvAction.PAGE_UP, result)
    }

    @Test
    fun `PAGE_DOWN is allowed for kid profile`() {
        val ctx = TvScreenContext.library(isKidProfile = true)
        val result = filterForKidsMode(TvAction.PAGE_DOWN, ctx)

        assertEquals(TvAction.PAGE_DOWN, result)
    }

    @Test
    fun `CHANNEL_UP is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true, isLive = true)
        val result = filterForKidsMode(TvAction.CHANNEL_UP, ctx)

        assertEquals(TvAction.CHANNEL_UP, result)
    }

    @Test
    fun `CHANNEL_DOWN is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true, isLive = true)
        val result = filterForKidsMode(TvAction.CHANNEL_DOWN, ctx)

        assertEquals(TvAction.CHANNEL_DOWN, result)
    }

    // ══════════════════════════════════════════════════════════════════
    // NON-KID PROFILES PASS THROUGH
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `SEEK_FORWARD_30S is allowed for non-kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = false)
        val result = filterForKidsMode(TvAction.SEEK_FORWARD_30S, ctx)

        assertEquals(TvAction.SEEK_FORWARD_30S, result)
    }

    @Test
    fun `OPEN_CC_MENU is allowed for non-kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = false)
        val result = filterForKidsMode(TvAction.OPEN_CC_MENU, ctx)

        assertEquals(TvAction.OPEN_CC_MENU, result)
    }

    @Test
    fun `OPEN_LIVE_LIST is allowed for non-kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = false)
        val result = filterForKidsMode(TvAction.OPEN_LIVE_LIST, ctx)

        assertEquals(TvAction.OPEN_LIVE_LIST, result)
    }

    // ══════════════════════════════════════════════════════════════════
    // NULL INPUT HANDLING
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `null action returns null for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(null, ctx)

        assertNull(result)
    }

    @Test
    fun `null action returns null for non-kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = false)
        val result = filterForKidsMode(null, ctx)

        assertNull(result)
    }

    // ══════════════════════════════════════════════════════════════════
    // INTEGRATION WITH RESOLVE
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `resolve blocks seek for kid profile on PLAYER`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx)

        // FAST_FORWARD maps to SEEK_FORWARD_30S in PLAYER config, but kids filter blocks it
        assertNull(action)
    }

    @Test
    fun `resolve allows ROW_FAST_SCROLL for kid profile on LIBRARY`() {
        val ctx = TvScreenContext.library(isKidProfile = true)
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx)

        // In LIBRARY, FAST_FORWARD maps to ROW_FAST_SCROLL_FORWARD, which is allowed for kids
        assertEquals(TvAction.ROW_FAST_SCROLL_FORWARD, action)
    }

    @Test
    fun `resolve allows navigation for kid profile`() {
        // Use LIBRARY screen where DPAD_LEFT maps to NAVIGATE_LEFT
        val ctx = TvScreenContext.library(isKidProfile = true)
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_LEFT, ctx)

        // DPAD_LEFT maps to NAVIGATE_LEFT in LIBRARY, which is allowed for kids
        assertEquals(TvAction.NAVIGATE_LEFT, action)
    }

    @Test
    fun `resolve blocks DPAD seek in player for kid profile`() {
        // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: DPAD_LEFT → SEEK_BACKWARD_10S in PLAYER
        // which is blocked for kids
        val ctx = TvScreenContext.player(isKidProfile = true)
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_LEFT, ctx)

        // DPAD_LEFT maps to SEEK_BACKWARD_10S in PLAYER, which is blocked for kids
        assertNull(action)
    }

    @Test
    fun `resolve allows BACK for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx)

        assertEquals(TvAction.BACK, action)
    }

    @Test
    fun `resolve allows PLAY_PAUSE for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.PLAY_PAUSE, ctx)

        assertEquals(TvAction.PLAY_PAUSE, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // COMPREHENSIVE BLOCKED ACTIONS TEST
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all blocked actions are filtered for kids`() {
        val ctx = TvScreenContext.player(isKidProfile = true)

        val blockedActions =
            listOf(
                TvAction.SEEK_FORWARD_10S,
                TvAction.SEEK_FORWARD_30S,
                TvAction.SEEK_BACKWARD_10S,
                TvAction.SEEK_BACKWARD_30S,
                TvAction.OPEN_CC_MENU,
                TvAction.OPEN_ASPECT_MENU,
                TvAction.OPEN_LIVE_LIST,
                // Phase 6 Task 4: Additional blocked actions
                TvAction.PIP_SEEK_FORWARD,
                TvAction.PIP_SEEK_BACKWARD,
                TvAction.OPEN_ADVANCED_SETTINGS,
            )

        for (action in blockedActions) {
            assertNull("$action should be blocked for kids", filterForKidsMode(action, ctx))
        }
    }

    @Test
    fun `all allowed actions pass through for kids`() {
        val ctx = TvScreenContext.player(isKidProfile = true)

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
                TvAction.FOCUS_QUICK_ACTIONS,
                TvAction.FOCUS_TIMELINE,
                TvAction.CHANNEL_UP,
                TvAction.CHANNEL_DOWN,
                // Phase 6 Task 4: Additional allowed actions
                TvAction.OPEN_DETAILS,
                TvAction.ROW_FAST_SCROLL_FORWARD,
                TvAction.ROW_FAST_SCROLL_BACKWARD,
                TvAction.PLAY_FOCUSED_RESUME,
                TvAction.OPEN_FILTER_SORT,
                TvAction.NEXT_EPISODE,
                TvAction.PREVIOUS_EPISODE,
                TvAction.OPEN_DETAIL_MENU,
                TvAction.ACTIVATE_FOCUSED_SETTING,
                TvAction.SELECT_PROFILE,
                TvAction.OPEN_PROFILE_OPTIONS,
                TvAction.OPEN_PLAYER_MENU,
                TvAction.PIP_TOGGLE_PLAY_PAUSE,
                TvAction.PIP_ENTER_RESIZE_MODE,
                TvAction.PIP_CONFIRM_RESIZE,
                TvAction.PIP_MOVE_LEFT,
                TvAction.PIP_MOVE_RIGHT,
                TvAction.PIP_MOVE_UP,
                TvAction.PIP_MOVE_DOWN,
                TvAction.EXIT_TO_HOME,
                TvAction.OPEN_GLOBAL_SEARCH,
                TvAction.SWITCH_SETTINGS_TAB_NEXT,
                TvAction.SWITCH_SETTINGS_TAB_PREVIOUS,
            )

        for (action in allowedActions) {
            assertNotNull("$action should be allowed for kids", filterForKidsMode(action, ctx))
            assertEquals(action, filterForKidsMode(action, ctx))
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PHASE 6 TASK 4: NEW ACTION KIDS MODE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PIP_SEEK_FORWARD is blocked for kid profile`() {
        val ctx = TvScreenContext.miniPlayer(isKidProfile = true)
        val result = filterForKidsMode(TvAction.PIP_SEEK_FORWARD, ctx)
        assertNull(result)
    }

    @Test
    fun `PIP_SEEK_BACKWARD is blocked for kid profile`() {
        val ctx = TvScreenContext.miniPlayer(isKidProfile = true)
        val result = filterForKidsMode(TvAction.PIP_SEEK_BACKWARD, ctx)
        assertNull(result)
    }

    @Test
    fun `OPEN_ADVANCED_SETTINGS is blocked for kid profile`() {
        val ctx = TvScreenContext.settings(isKidProfile = true)
        val result = filterForKidsMode(TvAction.OPEN_ADVANCED_SETTINGS, ctx)
        assertNull(result)
    }

    @Test
    fun `PIP_TOGGLE_PLAY_PAUSE is allowed for kid profile`() {
        val ctx = TvScreenContext.miniPlayer(isKidProfile = true)
        val result = filterForKidsMode(TvAction.PIP_TOGGLE_PLAY_PAUSE, ctx)
        assertEquals(TvAction.PIP_TOGGLE_PLAY_PAUSE, result)
    }

    @Test
    fun `PLAY_FOCUSED_RESUME is allowed for kid profile`() {
        val ctx = TvScreenContext.library(isKidProfile = true)
        val result = filterForKidsMode(TvAction.PLAY_FOCUSED_RESUME, ctx)
        assertEquals(TvAction.PLAY_FOCUSED_RESUME, result)
    }

    @Test
    fun `OPEN_DETAILS is allowed for kid profile`() {
        val ctx = TvScreenContext.library(isKidProfile = true)
        val result = filterForKidsMode(TvAction.OPEN_DETAILS, ctx)
        assertEquals(TvAction.OPEN_DETAILS, result)
    }

    @Test
    fun `EXIT_TO_HOME is allowed for kid profile`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        val result = filterForKidsMode(TvAction.EXIT_TO_HOME, ctx)
        assertEquals(TvAction.EXIT_TO_HOME, result)
    }
}
