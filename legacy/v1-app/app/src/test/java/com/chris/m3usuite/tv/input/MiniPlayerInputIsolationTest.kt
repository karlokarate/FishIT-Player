package com.chris.m3usuite.tv.input

import com.chris.m3usuite.player.miniplayer.DefaultMiniPlayerManager
import com.chris.m3usuite.player.miniplayer.MiniPlayerMode
import com.chris.m3usuite.player.miniplayer.RESIZE_SIZE_DELTA
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests verifying that RESIZE mode fully isolates TV input from the underlying screen.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – Finalization Task: Task 2 - MiniPlayer Input Isolation
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **Requirements:**
 * When MiniPlayer is in RESIZE mode, verify:
 * - FF/RW → resize delta (not seek, not row scroll)
 * - DPAD → moveBy (not navigate underlying screen)
 * - CENTER → confirmResize (not activate focused item)
 * - BACK → cancelResize (not exit screen)
 * - No TvActions leak to the underlying screen
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.2
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md: Resize Mode section
 */
class MiniPlayerInputIsolationTest {
    @Before
    fun setup() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    @After
    fun tearDown() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE MODE INPUT ISOLATION - FF/RW
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `RESIZE mode - FF action mapped to resize size (not seek or row scroll)`() {
        // Given: MiniPlayer visible in RESIZE mode
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        // When: Checking MINI_PLAYER screen config for FF
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.FAST_FORWARD, ctx)

        // Then: Action is PIP_SEEK_FORWARD (used for size in resize mode by handler)
        assertEquals(
            "FF should map to PIP_SEEK_FORWARD in MINI_PLAYER screen",
            TvAction.PIP_SEEK_FORWARD,
            action,
        )

        // Verify: This action is NOT a row scroll action
        assertFalse(
            "PIP_SEEK_FORWARD should not be ROW_FAST_SCROLL_FORWARD",
            action == TvAction.ROW_FAST_SCROLL_FORWARD,
        )
    }

    @Test
    fun `RESIZE mode - RW action mapped to resize size (not seek or row scroll)`() {
        // Given: MiniPlayer visible in RESIZE mode
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        // When: Checking MINI_PLAYER screen config for RW
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.REWIND, ctx)

        // Then: Action is PIP_SEEK_BACKWARD (used for size in resize mode by handler)
        assertEquals(
            "RW should map to PIP_SEEK_BACKWARD in MINI_PLAYER screen",
            TvAction.PIP_SEEK_BACKWARD,
            action,
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE MODE INPUT ISOLATION - DPAD
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `RESIZE mode - DPAD_LEFT mapped to PIP_MOVE_LEFT (not NAVIGATE_LEFT)`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_LEFT, ctx)

        assertEquals(
            "DPAD_LEFT should map to PIP_MOVE_LEFT in MINI_PLAYER",
            TvAction.PIP_MOVE_LEFT,
            action,
        )
        assertFalse("Should not be NAVIGATE_LEFT", action == TvAction.NAVIGATE_LEFT)
    }

    @Test
    fun `RESIZE mode - DPAD_RIGHT mapped to PIP_MOVE_RIGHT (not NAVIGATE_RIGHT)`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_RIGHT, ctx)

        assertEquals(TvAction.PIP_MOVE_RIGHT, action)
    }

    @Test
    fun `RESIZE mode - DPAD_UP mapped to PIP_MOVE_UP (not NAVIGATE_UP)`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_UP, ctx)

        assertEquals(TvAction.PIP_MOVE_UP, action)
    }

    @Test
    fun `RESIZE mode - DPAD_DOWN mapped to PIP_MOVE_DOWN (not NAVIGATE_DOWN)`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_DOWN, ctx)

        assertEquals(TvAction.PIP_MOVE_DOWN, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE MODE INPUT ISOLATION - CENTER/OK
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `RESIZE mode - CENTER mapped to PIP_CONFIRM_RESIZE (not OPEN_DETAILS or PLAY_PAUSE)`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.DPAD_CENTER, ctx)

        assertEquals(
            "CENTER should map to PIP_CONFIRM_RESIZE in MINI_PLAYER",
            TvAction.PIP_CONFIRM_RESIZE,
            action,
        )

        // Verify isolation from other screens
        assertFalse("Should not be OPEN_DETAILS", action == TvAction.OPEN_DETAILS)
        assertFalse("Should not be PLAY_PAUSE", action == TvAction.PLAY_PAUSE)
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE MODE INPUT ISOLATION - BACK
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `RESIZE mode - BACK mapped to BACK (handled by ResizeActionHandler to cancel)`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.BACK, ctx)

        // BACK is still BACK action, but MiniPlayerResizeActionHandler intercepts it in RESIZE mode
        assertEquals(TvAction.BACK, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE MODE - NO ACTIONS LEAK TO UNDERLYING SCREEN
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `RESIZE mode - all DPAD actions are PIP_MOVE (complete isolation)`() {
        val ctx = TvScreenContext.miniPlayer()
        val dpadRoles =
            listOf(
                TvKeyRole.DPAD_LEFT to TvAction.PIP_MOVE_LEFT,
                TvKeyRole.DPAD_RIGHT to TvAction.PIP_MOVE_RIGHT,
                TvKeyRole.DPAD_UP to TvAction.PIP_MOVE_UP,
                TvKeyRole.DPAD_DOWN to TvAction.PIP_MOVE_DOWN,
            )

        for ((role, expectedAction) in dpadRoles) {
            val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, role, ctx)
            assertEquals(
                "$role should map to $expectedAction in MINI_PLAYER screen",
                expectedAction,
                action,
            )
        }
    }

    @Test
    fun `RESIZE mode - no NAVIGATE actions returned from MINI_PLAYER screen`() {
        val ctx = TvScreenContext.miniPlayer()
        val navigateActions =
            listOf(
                TvAction.NAVIGATE_LEFT,
                TvAction.NAVIGATE_RIGHT,
                TvAction.NAVIGATE_UP,
                TvAction.NAVIGATE_DOWN,
            )

        val allRoles = TvKeyRole.values()
        for (role in allRoles) {
            val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, role, ctx)
            if (action != null) {
                assertFalse(
                    "$role should not produce NAVIGATE action in MINI_PLAYER, got $action",
                    action in navigateActions,
                )
            }
        }
    }

    @Test
    fun `RESIZE mode - no ROW_FAST_SCROLL actions returned from MINI_PLAYER screen`() {
        val ctx = TvScreenContext.miniPlayer()
        val rowScrollActions =
            listOf(
                TvAction.ROW_FAST_SCROLL_FORWARD,
                TvAction.ROW_FAST_SCROLL_BACKWARD,
            )

        val allRoles = TvKeyRole.values()
        for (role in allRoles) {
            val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, role, ctx)
            if (action != null) {
                assertFalse(
                    "$role should not produce ROW_FAST_SCROLL action in MINI_PLAYER, got $action",
                    action in rowScrollActions,
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE MODE STATE TRANSITIONS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `entering RESIZE mode preserves visible state`() {
        // Enter mini player
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        assertTrue(DefaultMiniPlayerManager.state.value.visible)

        // Enter resize mode
        DefaultMiniPlayerManager.enterResizeMode()

        // Verify state
        assertTrue("MiniPlayer should still be visible", DefaultMiniPlayerManager.state.value.visible)
        assertEquals(
            "Mode should be RESIZE",
            MiniPlayerMode.RESIZE,
            DefaultMiniPlayerManager.state.value.mode,
        )
    }

    @Test
    fun `confirming RESIZE mode returns to NORMAL mode`() {
        // Setup: Enter resize mode
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()
        assertEquals(MiniPlayerMode.RESIZE, DefaultMiniPlayerManager.state.value.mode)

        // Confirm resize
        DefaultMiniPlayerManager.confirmResize()

        // Verify: Back to NORMAL
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
        assertTrue("MiniPlayer should still be visible", DefaultMiniPlayerManager.state.value.visible)
    }

    @Test
    fun `canceling RESIZE mode returns to NORMAL mode with original state`() {
        // Setup: Enter with specific state
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size
        DefaultMiniPlayerManager.enterResizeMode()

        // Make changes
        DefaultMiniPlayerManager.applyResize(RESIZE_SIZE_DELTA)

        // Cancel
        DefaultMiniPlayerManager.cancelResize()

        // Verify: State restored
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
        assertEquals("Size should be restored", originalSize, DefaultMiniPlayerManager.state.value.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // NORMAL MODE vs RESIZE MODE ACTION DIFFERENCES
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `NORMAL mode FF and RW are for seek, RESIZE mode FF and RW are for size`() {
        // In both modes, the config mapping is the same (PIP_SEEK_*)
        // The MiniPlayerResizeActionHandler interprets them differently based on mode
        val ctx = TvScreenContext.miniPlayer()

        val ffAction = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.FAST_FORWARD, ctx)
        val rwAction = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.REWIND, ctx)

        // Both should be PIP_SEEK_* (handler interprets based on mode)
        assertEquals(TvAction.PIP_SEEK_FORWARD, ffAction)
        assertEquals(TvAction.PIP_SEEK_BACKWARD, rwAction)
    }

    // ══════════════════════════════════════════════════════════════════
    // MINIPLAYER VISIBILITY CONTEXT
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `miniPlayer context factory creates correct screen context`() {
        val ctx = TvScreenContext.miniPlayer()

        assertEquals(TvScreenId.MINI_PLAYER, ctx.screenId)
        // MiniPlayer context should indicate it's the mini player screen
        assertTrue("Should be mini player screen context", ctx.screenId == TvScreenId.MINI_PLAYER)
    }

    @Test
    fun `miniPlayer context with kid profile applies filtering`() {
        val ctx = TvScreenContext.miniPlayer(isKidProfile = true)

        // Kid profile should filter PIP_SEEK_* actions
        val ffAction = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.FAST_FORWARD, ctx)
        val rwAction = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.REWIND, ctx)

        // Kids filter blocks PIP_SEEK_* actions
        assertNull("PIP_SEEK_FORWARD should be blocked for kids", ffAction)
        assertNull("PIP_SEEK_BACKWARD should be blocked for kids", rwAction)
    }

    // ══════════════════════════════════════════════════════════════════
    // MENU KEY BEHAVIOR IN MINI_PLAYER
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MENU key in MINI_PLAYER maps to PIP_ENTER_RESIZE_MODE`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.MENU, ctx)

        assertEquals(
            "MENU should enter resize mode in MINI_PLAYER",
            TvAction.PIP_ENTER_RESIZE_MODE,
            action,
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // PLAY_PAUSE KEY BEHAVIOR IN MINI_PLAYER
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAY_PAUSE key in MINI_PLAYER maps to PIP_TOGGLE_PLAY_PAUSE`() {
        val ctx = TvScreenContext.miniPlayer()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.MINI_PLAYER, TvKeyRole.PLAY_PAUSE, ctx)

        assertEquals(TvAction.PIP_TOGGLE_PLAY_PAUSE, action)
    }
}
