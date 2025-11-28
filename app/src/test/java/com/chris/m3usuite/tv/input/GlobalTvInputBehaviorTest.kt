package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Comprehensive tests verifying runtime behavior matches GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
 * and INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 TASK 3: Validation, Navigation Hardening & Cleanup
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **Purpose:**
 * Systematically verify that all screen/context configurations match the behavior
 * contracts for TV remote input handling.
 *
 * **Contract References:**
 * - docs/GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
 * - docs/INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md
 * - docs/INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md
 */
class GlobalTvInputBehaviorTest {
    // ══════════════════════════════════════════════════════════════════
    // PLAYER SCREEN - PLAYBACK MODE (Behavior Map: Context A)
    // ══════════════════════════════════════════════════════════════════
    // - DPAD_CENTER / PLAY_PAUSE → PLAY_PAUSE
    // - LEFT/RIGHT → ±10s seek
    // - UP → QUICK_ACTIONS
    // - DOWN → show controls/timeline
    // - FF/RW → ±30s seek
    // - MENU → OPEN_PLAYER_MENU
    // - BACK → show controls, then exit on second back
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAYER playback mode - CENTER maps to PLAY_PAUSE`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals("CENTER → PLAY_PAUSE in playback mode", TvAction.PLAY_PAUSE, action)
    }

    @Test
    fun `PLAYER playback mode - PLAY_PAUSE key maps to PLAY_PAUSE`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals("PLAY_PAUSE key → PLAY_PAUSE", TvAction.PLAY_PAUSE, action)
    }

    @Test
    fun `PLAYER playback mode - DPAD_LEFT maps to SEEK_BACKWARD_10S`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_LEFT, ctx)
        assertEquals("LEFT → ±10s seek backward", TvAction.SEEK_BACKWARD_10S, action)
    }

    @Test
    fun `PLAYER playback mode - DPAD_RIGHT maps to SEEK_FORWARD_10S`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_RIGHT, ctx)
        assertEquals("RIGHT → ±10s seek forward", TvAction.SEEK_FORWARD_10S, action)
    }

    @Test
    fun `PLAYER playback mode - DPAD_UP maps to FOCUS_QUICK_ACTIONS`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_UP, ctx)
        assertEquals("UP → QUICK_ACTIONS", TvAction.FOCUS_QUICK_ACTIONS, action)
    }

    @Test
    fun `PLAYER playback mode - DPAD_DOWN maps to FOCUS_TIMELINE`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_DOWN, ctx)
        assertEquals("DOWN → show controls/timeline", TvAction.FOCUS_TIMELINE, action)
    }

    @Test
    fun `PLAYER playback mode - FAST_FORWARD maps to SEEK_FORWARD_30S`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals("FF → +30s seek", TvAction.SEEK_FORWARD_30S, action)
    }

    @Test
    fun `PLAYER playback mode - REWIND maps to SEEK_BACKWARD_30S`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.REWIND, ctx)
        assertEquals("RW → -30s seek", TvAction.SEEK_BACKWARD_30S, action)
    }

    @Test
    fun `PLAYER playback mode - MENU maps to OPEN_PLAYER_MENU`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.MENU, ctx)
        assertEquals("MENU → OPEN_PLAYER_MENU", TvAction.OPEN_PLAYER_MENU, action)
    }

    @Test
    fun `PLAYER playback mode - BACK maps to BACK`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx)
        assertEquals("BACK → BACK", TvAction.BACK, action)
    }

    @Test
    fun `PLAYER playback mode - FF and RW disabled for row scroll`() {
        // In PLAYER, FF/RW should be seek actions, not row scroll
        val ctx = TvScreenContext.player()
        val ffAction = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx)
        val rwAction = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.REWIND, ctx)

        // Verify they are seek actions, not row scroll actions
        assertEquals(TvAction.SEEK_FORWARD_30S, ffAction)
        assertEquals(TvAction.SEEK_BACKWARD_30S, rwAction)

        // Verify they are NOT row scroll actions
        assert(ffAction != TvAction.ROW_FAST_SCROLL_FORWARD) { "FF should not be row scroll in player" }
        assert(rwAction != TvAction.ROW_FAST_SCROLL_BACKWARD) { "RW should not be row scroll in player" }
    }

    // ══════════════════════════════════════════════════════════════════
    // LIBRARY / START SCREENS (Behavior Map: HOME/BROWSE/LIBRARY)
    // ══════════════════════════════════════════════════════════════════
    // - CENTER → OPEN_DETAILS
    // - DPAD moves tiles and rows
    // - FF/RW → row fast scroll actions
    // - PLAY/PAUSE → PLAY_FOCUSED_RESUME (no details screen)
    // - MENU short → OPEN_FILTER_SORT
    // - MENU long → OPEN_GLOBAL_SEARCH (TODO: not yet wired)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `LIBRARY - CENTER maps to OPEN_DETAILS`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals("CENTER → OPEN_DETAILS", TvAction.OPEN_DETAILS, action)
    }

    @Test
    fun `LIBRARY - DPAD navigation maps correctly`() {
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

    @Test
    fun `LIBRARY - FF maps to ROW_FAST_SCROLL_FORWARD`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals("FF → ROW_FAST_SCROLL_FORWARD", TvAction.ROW_FAST_SCROLL_FORWARD, action)
    }

    @Test
    fun `LIBRARY - RW maps to ROW_FAST_SCROLL_BACKWARD`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.REWIND, ctx)
        assertEquals("RW → ROW_FAST_SCROLL_BACKWARD", TvAction.ROW_FAST_SCROLL_BACKWARD, action)
    }

    @Test
    fun `LIBRARY - PLAY_PAUSE maps to PLAY_FOCUSED_RESUME`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals("PLAY/PAUSE → PLAY_FOCUSED_RESUME", TvAction.PLAY_FOCUSED_RESUME, action)
    }

    @Test
    fun `LIBRARY - MENU short maps to OPEN_FILTER_SORT`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.MENU, ctx)
        assertEquals("MENU short → OPEN_FILTER_SORT", TvAction.OPEN_FILTER_SORT, action)
    }

    @Test
    fun `START screen has same mappings as LIBRARY`() {
        val libCtx = TvScreenContext.library()
        val startCtx = TvScreenContext.start()

        // Verify key mappings match between LIBRARY and START
        val keysToTest = listOf(
            TvKeyRole.DPAD_CENTER,
            TvKeyRole.DPAD_UP,
            TvKeyRole.DPAD_DOWN,
            TvKeyRole.DPAD_LEFT,
            TvKeyRole.DPAD_RIGHT,
            TvKeyRole.FAST_FORWARD,
            TvKeyRole.REWIND,
            TvKeyRole.PLAY_PAUSE,
            TvKeyRole.MENU,
            TvKeyRole.BACK,
        )

        for (key in keysToTest) {
            val libAction = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, key, libCtx)
            val startAction = DefaultTvScreenConfigs.resolve(TvScreenId.START, key, startCtx)
            assertEquals("$key should have same mapping on START as LIBRARY", libAction, startAction)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // DETAIL SCREEN (Behavior Map: DETAIL SCREEN)
    // ══════════════════════════════════════════════════════════════════
    // - CENTER → Play/resume (PLAY_FOCUSED_RESUME)
    // - PLAY/PAUSE → Play/resume
    // - FF/RW → Next/previous episode
    // - MENU → Detail actions (Trailer, Add to list, etc.)
    // - DPAD → Navigate episode list, buttons, metadata
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `DETAIL - CENTER maps to PLAY_FOCUSED_RESUME`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals("CENTER → Play/resume", TvAction.PLAY_FOCUSED_RESUME, action)
    }

    @Test
    fun `DETAIL - PLAY_PAUSE maps to PLAY_FOCUSED_RESUME`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals("PLAY/PAUSE → Play/resume", TvAction.PLAY_FOCUSED_RESUME, action)
    }

    @Test
    fun `DETAIL - FAST_FORWARD maps to NEXT_EPISODE`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals("FF → Next episode", TvAction.NEXT_EPISODE, action)
    }

    @Test
    fun `DETAIL - REWIND maps to PREVIOUS_EPISODE`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.REWIND, ctx)
        assertEquals("RW → Previous episode", TvAction.PREVIOUS_EPISODE, action)
    }

    @Test
    fun `DETAIL - MENU maps to OPEN_DETAIL_MENU`() {
        val ctx = TvScreenContext.detail()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.MENU, ctx)
        assertEquals("MENU → Detail actions", TvAction.OPEN_DETAIL_MENU, action)
    }

    @Test
    fun `DETAIL - DPAD navigation works`() {
        val ctx = TvScreenContext.detail()
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.DPAD_UP, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_DOWN,
            DefaultTvScreenConfigs.resolve(TvScreenId.DETAIL, TvKeyRole.DPAD_DOWN, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // SETTINGS SCREEN (Behavior Map: SETTINGS SCREEN)
    // ══════════════════════════════════════════════════════════════════
    // - CENTER → Activate option (ACTIVATE_FOCUSED_SETTING)
    // - DPAD → Navigate list
    // - BACK → Exit settings
    // - PLAY/PAUSE → no-op
    // - FF/RW → Switch settings tabs (future)
    // - MENU → Advanced Settings
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `SETTINGS - CENTER maps to ACTIVATE_FOCUSED_SETTING`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals("CENTER → Activate option", TvAction.ACTIVATE_FOCUSED_SETTING, action)
    }

    @Test
    fun `SETTINGS - PLAY_PAUSE is no-op (unmapped)`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.PLAY_PAUSE, ctx)
        assertNull("PLAY/PAUSE → no-op in settings", action)
    }

    @Test
    fun `SETTINGS - FF maps to SWITCH_SETTINGS_TAB_NEXT`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals("FF → Switch settings tabs", TvAction.SWITCH_SETTINGS_TAB_NEXT, action)
    }

    @Test
    fun `SETTINGS - RW maps to SWITCH_SETTINGS_TAB_PREVIOUS`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.REWIND, ctx)
        assertEquals("RW → Switch settings tabs", TvAction.SWITCH_SETTINGS_TAB_PREVIOUS, action)
    }

    @Test
    fun `SETTINGS - MENU maps to OPEN_ADVANCED_SETTINGS`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.MENU, ctx)
        assertEquals("MENU → Advanced Settings", TvAction.OPEN_ADVANCED_SETTINGS, action)
    }

    @Test
    fun `SETTINGS - BACK maps to BACK`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.BACK, ctx)
        assertEquals("BACK → Exit settings", TvAction.BACK, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // PROFILE GATE SCREEN (Behavior Map: PROFILE GATE SCREEN)
    // ══════════════════════════════════════════════════════════════════
    // - CENTER → Select profile
    // - DPAD → Navigate profiles
    // - MENU → Profile options
    // - BACK → Exit app / previous
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PROFILE_GATE - CENTER maps to SELECT_PROFILE`() {
        // Use raw config without overlay filtering for testing
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PROFILE_GATE)
        val action = config.getRawAction(TvKeyRole.DPAD_CENTER)
        assertEquals("CENTER → Select profile", TvAction.SELECT_PROFILE, action)
    }

    @Test
    fun `PROFILE_GATE - MENU maps to OPEN_PROFILE_OPTIONS`() {
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PROFILE_GATE)
        val action = config.getRawAction(TvKeyRole.MENU)
        assertEquals("MENU → Profile options", TvAction.OPEN_PROFILE_OPTIONS, action)
    }

    @Test
    fun `PROFILE_GATE - DPAD navigation maps correctly`() {
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PROFILE_GATE)
        assertEquals(TvAction.NAVIGATE_UP, config.getRawAction(TvKeyRole.DPAD_UP))
        assertEquals(TvAction.NAVIGATE_DOWN, config.getRawAction(TvKeyRole.DPAD_DOWN))
        assertEquals(TvAction.NAVIGATE_LEFT, config.getRawAction(TvKeyRole.DPAD_LEFT))
        assertEquals(TvAction.NAVIGATE_RIGHT, config.getRawAction(TvKeyRole.DPAD_RIGHT))
    }

    @Test
    fun `PROFILE_GATE - BACK maps to BACK`() {
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PROFILE_GATE)
        val action = config.getRawAction(TvKeyRole.BACK)
        assertEquals("BACK → Exit app / previous", TvAction.BACK, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // MINI PLAYER / PIP MODE (Behavior Map: GLOBAL PIP / MINIPLAYER MODE)
    // ══════════════════════════════════════════════════════════════════
    // - FF/RW → Seek in mini-player
    // - PLAY/PAUSE → Toggle playback
    // - DPAD → Navigate app behind PIP (or move in resize mode)
    // - MENU (long press) → Enter PIP Resize Mode
    // - CENTER → Confirm size/position
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MINI_PLAYER - FF maps to PIP_SEEK_FORWARD`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.FAST_FORWARD, ctx)
        assertEquals("FF → Seek in PIP", TvAction.PIP_SEEK_FORWARD, action)
    }

    @Test
    fun `MINI_PLAYER - RW maps to PIP_SEEK_BACKWARD`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.REWIND, ctx)
        assertEquals("RW → Seek in PIP", TvAction.PIP_SEEK_BACKWARD, action)
    }

    @Test
    fun `MINI_PLAYER - PLAY_PAUSE maps to PIP_TOGGLE_PLAY_PAUSE`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.PLAY_PAUSE, ctx)
        assertEquals("PLAY/PAUSE → Toggle PIP playback", TvAction.PIP_TOGGLE_PLAY_PAUSE, action)
    }

    @Test
    fun `MINI_PLAYER - DPAD maps to PIP_MOVE actions`() {
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
    fun `MINI_PLAYER - MENU maps to PIP_ENTER_RESIZE_MODE`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.MENU, ctx)
        assertEquals("MENU → Enter PIP Resize Mode", TvAction.PIP_ENTER_RESIZE_MODE, action)
    }

    @Test
    fun `MINI_PLAYER - CENTER maps to PIP_CONFIRM_RESIZE`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_CENTER, ctx)
        assertEquals("CENTER → Confirm size/position", TvAction.PIP_CONFIRM_RESIZE, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // PHASE 7 SPECIFIC: ROW_FAST_SCROLL blocked when MiniPlayer visible
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `LIBRARY - ROW_FAST_SCROLL blocked when MiniPlayer visible`() {
        val ctx = TvScreenContext.library(isMiniPlayerVisible = true)

        // FF/RW should be blocked
        val ffAction = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx)
        val rwAction = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.REWIND, ctx)

        assertNull("ROW_FAST_SCROLL_FORWARD blocked with MiniPlayer visible", ffAction)
        assertNull("ROW_FAST_SCROLL_BACKWARD blocked with MiniPlayer visible", rwAction)
    }

    @Test
    fun `START - ROW_FAST_SCROLL blocked when MiniPlayer visible`() {
        val ctx = TvScreenContext.start(isMiniPlayerVisible = true)

        val ffAction = DefaultTvScreenConfigs.resolve(TvScreenId.START, TvKeyRole.FAST_FORWARD, ctx)
        val rwAction = DefaultTvScreenConfigs.resolve(TvScreenId.START, TvKeyRole.REWIND, ctx)

        assertNull("ROW_FAST_SCROLL_FORWARD blocked with MiniPlayer visible", ffAction)
        assertNull("ROW_FAST_SCROLL_BACKWARD blocked with MiniPlayer visible", rwAction)
    }

    // ══════════════════════════════════════════════════════════════════
    // PHASE 7 SPECIFIC: TOGGLE_MINI_PLAYER_FOCUS mapping
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAYER - PLAY_PAUSE_LONG maps to TOGGLE_MINI_PLAYER_FOCUS`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.PLAY_PAUSE_LONG, ctx)
        assertEquals("Long-press PLAY → TOGGLE_MINI_PLAYER_FOCUS", TvAction.TOGGLE_MINI_PLAYER_FOCUS, action)
    }

    @Test
    fun `LIBRARY - PLAY_PAUSE_LONG maps to TOGGLE_MINI_PLAYER_FOCUS`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE_LONG, ctx)
        assertEquals("Long-press PLAY → TOGGLE_MINI_PLAYER_FOCUS", TvAction.TOGGLE_MINI_PLAYER_FOCUS, action)
    }

    @Test
    fun `START - PLAY_PAUSE_LONG maps to TOGGLE_MINI_PLAYER_FOCUS`() {
        val ctx = TvScreenContext.start()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.START, TvKeyRole.PLAY_PAUSE_LONG, ctx)
        assertEquals("Long-press PLAY → TOGGLE_MINI_PLAYER_FOCUS", TvAction.TOGGLE_MINI_PLAYER_FOCUS, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // KIDS MODE: Comprehensive blocking tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Kids Mode blocks SEEK actions in PLAYER`() {
        val ctx = TvScreenContext.player(isKidProfile = true)

        // All seek actions should be blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_LEFT, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_RIGHT, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.REWIND, ctx))
    }

    @Test
    fun `Kids Mode blocks PIP_SEEK actions`() {
        val ctx = TvScreenContext.miniPlayer(isKidProfile = true)

        assertNull(
            "PIP_SEEK_FORWARD blocked for kids",
            DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.FAST_FORWARD, ctx),
        )
        assertNull(
            "PIP_SEEK_BACKWARD blocked for kids",
            DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.REWIND, ctx),
        )
    }

    @Test
    fun `Kids Mode blocks OPEN_ADVANCED_SETTINGS`() {
        val ctx = TvScreenContext.settings(isKidProfile = true)
        assertNull(
            "OPEN_ADVANCED_SETTINGS blocked for kids",
            DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.MENU, ctx),
        )
    }

    @Test
    fun `Kids Mode allows PLAY_PAUSE`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        assertEquals(
            TvAction.PLAY_PAUSE,
            DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.PLAY_PAUSE, ctx),
        )
    }

    @Test
    fun `Kids Mode allows BACK`() {
        val ctx = TvScreenContext.player(isKidProfile = true)
        assertEquals(
            TvAction.BACK,
            DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx),
        )
    }

    @Test
    fun `Kids Mode allows NAVIGATE actions in LIBRARY`() {
        val ctx = TvScreenContext.library(isKidProfile = true)
        assertEquals(TvAction.NAVIGATE_UP, DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx))
        assertEquals(TvAction.NAVIGATE_DOWN, DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_DOWN, ctx))
    }

    @Test
    fun `Kids Mode allows ROW_FAST_SCROLL in LIBRARY`() {
        val ctx = TvScreenContext.library(isKidProfile = true)
        assertEquals(
            TvAction.ROW_FAST_SCROLL_FORWARD,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx),
        )
    }

    @Test
    fun `Kids Mode allows PLAY_FOCUSED_RESUME in LIBRARY`() {
        val ctx = TvScreenContext.library(isKidProfile = true)
        assertEquals(
            TvAction.PLAY_FOCUSED_RESUME,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.PLAY_PAUSE, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // OVERLAY BLOCKING: Only NAVIGATE + BACK allowed
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Overlay blocks all non-navigation actions`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)

        // Non-navigation actions should be blocked
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.PLAY_PAUSE, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.REWIND, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.MENU, ctx))
    }

    @Test
    fun `Overlay allows BACK to close`() {
        val ctx = TvScreenContext.player(hasBlockingOverlay = true)
        assertEquals(
            TvAction.BACK,
            DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx),
        )
    }

    @Test
    fun `Overlay allows NAVIGATE for navigation within overlay`() {
        // In LIBRARY with overlay, DPAD should still map to NAVIGATE
        val ctx = TvScreenContext.library(hasBlockingOverlay = true)

        // Note: In PLAYER, DPAD maps to seek; but if overlay filter runs first,
        // it should see the seek action and block it. Let's test LIBRARY where DPAD → NAVIGATE
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // COMBINED: Kids Mode + MiniPlayer + Overlay
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Combined Kids Mode and MiniPlayer visible filter`() {
        val ctx = TvScreenContext.library(isKidProfile = true, isMiniPlayerVisible = true)

        // ROW_FAST_SCROLL should be blocked by MiniPlayer filter
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx))

        // NAVIGATE should still be allowed for kids
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx),
        )
    }

    @Test
    fun `Combined Kids Mode and Overlay filter`() {
        val ctx = TvScreenContext.library(isKidProfile = true, hasBlockingOverlay = true)

        // ROW_FAST_SCROLL is normally allowed for kids, but overlay blocks it
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx))

        // NAVIGATE should still be allowed through overlay
        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx),
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // ALL SCREENS: BACK action mapped
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `All major screens have BACK mapped`() {
        val screens = listOf(
            TvScreenId.PLAYER to TvScreenContext.player(),
            TvScreenId.LIBRARY to TvScreenContext.library(),
            TvScreenId.START to TvScreenContext.start(),
            TvScreenId.DETAIL to TvScreenContext.detail(),
            TvScreenId.SETTINGS to TvScreenContext.settings(),
            TvScreenId.MINI_PLAYER to TvScreenContext.miniPlayer(),
        )

        for ((screenId, ctx) in screens) {
            val action = DefaultTvScreenConfigs.resolve(screenId, TvKeyRole.BACK, ctx)
            assertEquals("$screenId should have BACK mapped", TvAction.BACK, action)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // IACTION VERIFICATION: Confirm action categories are correct
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `TOGGLE_MINI_PLAYER_FOCUS is a focus action`() {
        assert(TvAction.TOGGLE_MINI_PLAYER_FOCUS.isFocusAction()) {
            "TOGGLE_MINI_PLAYER_FOCUS should be categorized as focus action"
        }
    }

    @Test
    fun `All seek actions return correct deltas`() {
        assertEquals(10_000L, TvAction.SEEK_FORWARD_10S.getSeekDeltaMs())
        assertEquals(30_000L, TvAction.SEEK_FORWARD_30S.getSeekDeltaMs())
        assertEquals(-10_000L, TvAction.SEEK_BACKWARD_10S.getSeekDeltaMs())
        assertEquals(-30_000L, TvAction.SEEK_BACKWARD_30S.getSeekDeltaMs())
    }

    // Import extension functions
    private fun TvAction.isFocusAction() = TvAction.Companion.run { this@isFocusAction.isFocusAction() }
    private fun TvAction.getSeekDeltaMs() = TvAction.Companion.run { this@getSeekDeltaMs.getSeekDeltaMs() }
}
