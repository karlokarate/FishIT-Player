package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for DefaultTvScreenConfigs aligned with GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
 *
 * Tests verify that all screen configurations match the behavior map specification.
 *
 * Phase 6 Task 4: Align TvInput configuration with GLOBAL_TV_REMOTE_BEHAVIOR_MAP
 */
class DefaultTvScreenConfigsTest {
    // ══════════════════════════════════════════════════════════════════
    // PLAYER SCREEN TESTS (Behavior Map: PLAYER SCREEN - Context A)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAYER - DPAD_CENTER maps to PLAY_PAUSE per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals(TvAction.PLAY_PAUSE, action)
    }

    @Test
    fun `PLAYER - PLAY_PAUSE maps to PLAY_PAUSE per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals(TvAction.PLAY_PAUSE, action)
    }

    @Test
    fun `PLAYER - DPAD_LEFT maps to SEEK_BACKWARD_10S per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_LEFT, ctx)
        assertEquals(TvAction.SEEK_BACKWARD_10S, action)
    }

    @Test
    fun `PLAYER - DPAD_RIGHT maps to SEEK_FORWARD_10S per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_RIGHT, ctx)
        assertEquals(TvAction.SEEK_FORWARD_10S, action)
    }

    @Test
    fun `PLAYER - DPAD_UP maps to FOCUS_QUICK_ACTIONS per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_UP, ctx)
        assertEquals(TvAction.FOCUS_QUICK_ACTIONS, action)
    }

    @Test
    fun `PLAYER - DPAD_DOWN maps to FOCUS_TIMELINE per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_DOWN, ctx)
        assertEquals(TvAction.FOCUS_TIMELINE, action)
    }

    @Test
    fun `PLAYER - FAST_FORWARD maps to SEEK_FORWARD_30S per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals(TvAction.SEEK_FORWARD_30S, action)
    }

    @Test
    fun `PLAYER - REWIND maps to SEEK_BACKWARD_30S per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.REWIND, ctx)
        assertEquals(TvAction.SEEK_BACKWARD_30S, action)
    }

    @Test
    fun `PLAYER - MENU maps to OPEN_PLAYER_MENU per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.MENU, ctx)
        assertEquals(TvAction.OPEN_PLAYER_MENU, action)
    }

    @Test
    fun `PLAYER - BACK maps to BACK per behavior map`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx)
        assertEquals(TvAction.BACK, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // LIBRARY SCREEN TESTS (Behavior Map: HOME / BROWSE / LIBRARY SCREENS)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `LIBRARY - DPAD_CENTER maps to OPEN_DETAILS per behavior map`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals(TvAction.OPEN_DETAILS, action)
    }

    @Test
    fun `LIBRARY - FAST_FORWARD maps to ROW_FAST_SCROLL_FORWARD per behavior map`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals(TvAction.ROW_FAST_SCROLL_FORWARD, action)
    }

    @Test
    fun `LIBRARY - REWIND maps to ROW_FAST_SCROLL_BACKWARD per behavior map`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.REWIND, ctx)
        assertEquals(TvAction.ROW_FAST_SCROLL_BACKWARD, action)
    }

    @Test
    fun `LIBRARY - PLAY_PAUSE maps to PLAY_FOCUSED_RESUME per behavior map`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals(TvAction.PLAY_FOCUSED_RESUME, action)
    }

    @Test
    fun `LIBRARY - MENU maps to OPEN_FILTER_SORT per behavior map`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.MENU, ctx)
        assertEquals(TvAction.OPEN_FILTER_SORT, action)
    }

    @Test
    fun `LIBRARY - DPAD navigation maps to NAVIGATE actions`() {
        val ctx = TvScreenContext.library()
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
    }

    // ══════════════════════════════════════════════════════════════════
    // START SCREEN TESTS (Same as LIBRARY per behavior map)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `START - DPAD_CENTER maps to OPEN_DETAILS per behavior map`() {
        val ctx = TvScreenContext.start()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.START, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals(TvAction.OPEN_DETAILS, action)
    }

    @Test
    fun `START - FAST_FORWARD maps to ROW_FAST_SCROLL_FORWARD per behavior map`() {
        val ctx = TvScreenContext.start()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.START, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals(TvAction.ROW_FAST_SCROLL_FORWARD, action)
    }

    @Test
    fun `START - PLAY_PAUSE maps to PLAY_FOCUSED_RESUME per behavior map`() {
        val ctx = TvScreenContext.start()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.START, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals(TvAction.PLAY_FOCUSED_RESUME, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // DETAIL SCREEN TESTS (Behavior Map: DETAIL SCREEN)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `DETAIL - DPAD_CENTER maps to PLAY_FOCUSED_RESUME per behavior map`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals(TvAction.PLAY_FOCUSED_RESUME, action)
    }

    @Test
    fun `DETAIL - PLAY_PAUSE maps to PLAY_FOCUSED_RESUME per behavior map`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals(TvAction.PLAY_FOCUSED_RESUME, action)
    }

    @Test
    fun `DETAIL - FAST_FORWARD maps to NEXT_EPISODE per behavior map`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals(TvAction.NEXT_EPISODE, action)
    }

    @Test
    fun `DETAIL - REWIND maps to PREVIOUS_EPISODE per behavior map`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.REWIND, ctx)
        assertEquals(TvAction.PREVIOUS_EPISODE, action)
    }

    @Test
    fun `DETAIL - MENU maps to OPEN_DETAIL_MENU per behavior map`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.MENU, ctx)
        assertEquals(TvAction.OPEN_DETAIL_MENU, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // SETTINGS SCREEN TESTS (Behavior Map: SETTINGS SCREEN)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `SETTINGS - DPAD_CENTER maps to ACTIVATE_FOCUSED_SETTING per behavior map`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals(TvAction.ACTIVATE_FOCUSED_SETTING, action)
    }

    @Test
    fun `SETTINGS - FAST_FORWARD maps to SWITCH_SETTINGS_TAB_NEXT per behavior map`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals(TvAction.SWITCH_SETTINGS_TAB_NEXT, action)
    }

    @Test
    fun `SETTINGS - REWIND maps to SWITCH_SETTINGS_TAB_PREVIOUS per behavior map`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.REWIND, ctx)
        assertEquals(TvAction.SWITCH_SETTINGS_TAB_PREVIOUS, action)
    }

    @Test
    fun `SETTINGS - MENU maps to OPEN_ADVANCED_SETTINGS per behavior map`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.MENU, ctx)
        assertEquals(TvAction.OPEN_ADVANCED_SETTINGS, action)
    }

    @Test
    fun `SETTINGS - PLAY_PAUSE has no mapping per behavior map`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.PLAY_PAUSE, ctx)
        assertNull(action)
    }

    // ══════════════════════════════════════════════════════════════════
    // PROFILE GATE TESTS (Behavior Map: PROFILE GATE SCREEN)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PROFILE_GATE - DPAD_CENTER maps to SELECT_PROFILE per behavior map`() {
        // Note: ProfileGate has hasBlockingOverlay=true, so we need special handling
        val ctx = TvScreenContext(
            screenId = TvScreenId.PROFILE_GATE,
            hasBlockingOverlay = false, // Test raw config without overlay filtering
        )
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PROFILE_GATE)
        val action = config.getRawAction(TvKeyRole.DPAD_CENTER)
        assertEquals(TvAction.SELECT_PROFILE, action)
    }

    @Test
    fun `PROFILE_GATE - MENU maps to OPEN_PROFILE_OPTIONS per behavior map`() {
        val ctx = TvScreenContext(
            screenId = TvScreenId.PROFILE_GATE,
            hasBlockingOverlay = false,
        )
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PROFILE_GATE)
        val action = config.getRawAction(TvKeyRole.MENU)
        assertEquals(TvAction.OPEN_PROFILE_OPTIONS, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // MINI PLAYER / PIP TESTS (Behavior Map: GLOBAL PIP / MINIPLAYER MODE)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MINI_PLAYER - FAST_FORWARD maps to PIP_SEEK_FORWARD per behavior map`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals(TvAction.PIP_SEEK_FORWARD, action)
    }

    @Test
    fun `MINI_PLAYER - REWIND maps to PIP_SEEK_BACKWARD per behavior map`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.REWIND, ctx)
        assertEquals(TvAction.PIP_SEEK_BACKWARD, action)
    }

    @Test
    fun `MINI_PLAYER - PLAY_PAUSE maps to PIP_TOGGLE_PLAY_PAUSE per behavior map`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals(TvAction.PIP_TOGGLE_PLAY_PAUSE, action)
    }

    @Test
    fun `MINI_PLAYER - MENU maps to PIP_ENTER_RESIZE_MODE per behavior map`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.MENU, ctx)
        assertEquals(TvAction.PIP_ENTER_RESIZE_MODE, action)
    }

    @Test
    fun `MINI_PLAYER - DPAD maps to PIP_MOVE actions per behavior map`() {
        val ctx = TvScreenContext.miniPlayer()
        assertEquals(
            TvAction.PIP_MOVE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_UP, ctx),
        )
        assertEquals(
            TvAction.PIP_MOVE_DOWN,
            DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_DOWN, ctx),
        )
        assertEquals(
            TvAction.PIP_MOVE_LEFT,
            DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_LEFT, ctx),
        )
        assertEquals(
            TvAction.PIP_MOVE_RIGHT,
            DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_RIGHT, ctx),
        )
    }

    @Test
    fun `MINI_PLAYER - DPAD_CENTER maps to PIP_CONFIRM_RESIZE per behavior map`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals(TvAction.PIP_CONFIRM_RESIZE, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // CONFIG COMPLETENESS TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all major screens have configurations`() {
        val requiredScreens = listOf(
            TvScreenId.PLAYER,
            TvScreenId.LIBRARY,
            TvScreenId.START,
            TvScreenId.DETAIL,
            TvScreenId.SETTINGS,
            TvScreenId.PROFILE_GATE,
            TvScreenId.MINI_PLAYER,
        )

        for (screenId in requiredScreens) {
            val config = DefaultTvScreenConfigs.forScreen(screenId)
            assertNotNull("Config for $screenId should exist", config)
            assertEquals("Config screenId should match", screenId, config.screenId)
            org.junit.Assert.assertTrue(
                "Config for $screenId should have bindings",
                config.bindings.isNotEmpty(),
            )
        }
    }

    @Test
    fun `BACK action is mapped on all major screens`() {
        val screensWithBack = listOf(
            TvScreenId.PLAYER,
            TvScreenId.LIBRARY,
            TvScreenId.START,
            TvScreenId.DETAIL,
            TvScreenId.SETTINGS,
            TvScreenId.PROFILE_GATE,
            TvScreenId.MINI_PLAYER,
        )

        for (screenId in screensWithBack) {
            val config = DefaultTvScreenConfigs.forScreen(screenId)
            val action = config.getRawAction(TvKeyRole.BACK)
            assertEquals("$screenId should map BACK to TvAction.BACK", TvAction.BACK, action)
        }
    }
}
