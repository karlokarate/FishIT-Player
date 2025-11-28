package com.chris.m3usuite.tv.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for TvScreenInputConfig resolution.
 *
 * Tests verify correct action resolution for different screens,
 * including unmapped key handling.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.2
 */
class TvScreenInputConfigResolveTest {
    // ══════════════════════════════════════════════════════════════════
    // BASIC RESOLUTION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `getRawAction returns mapped action for bound role`() {
        val config =
            TvScreenInputConfig(
                screenId = TvScreenId.PLAYER,
                bindings =
                    mapOf(
                        TvKeyRole.FAST_FORWARD to TvAction.SEEK_FORWARD_30S,
                    ),
            )

        assertEquals(TvAction.SEEK_FORWARD_30S, config.getRawAction(TvKeyRole.FAST_FORWARD))
    }

    @Test
    fun `getRawAction returns null for unbound role`() {
        val config =
            TvScreenInputConfig(
                screenId = TvScreenId.PLAYER,
                bindings = emptyMap(),
            )

        assertNull(config.getRawAction(TvKeyRole.FAST_FORWARD))
    }

    @Test
    fun `getRawAction returns null for explicitly null binding`() {
        val config =
            TvScreenInputConfig(
                screenId = TvScreenId.PLAYER,
                bindings = mapOf(TvKeyRole.DPAD_CENTER to null),
            )

        assertNull(config.getRawAction(TvKeyRole.DPAD_CENTER))
    }

    // ══════════════════════════════════════════════════════════════════
    // PLAYER SCREEN RESOLUTION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PLAYER config resolves FAST_FORWARD to SEEK_FORWARD_30S`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.FAST_FORWARD, ctx)

        assertEquals(TvAction.SEEK_FORWARD_30S, action)
    }

    @Test
    fun `PLAYER config resolves REWIND to SEEK_BACKWARD_30S`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.REWIND, ctx)

        assertEquals(TvAction.SEEK_BACKWARD_30S, action)
    }

    @Test
    fun `PLAYER config resolves MENU to OPEN_PLAYER_MENU`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.MENU, ctx)

        // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: MENU → Player options
        assertEquals(TvAction.OPEN_PLAYER_MENU, action)
    }

    @Test
    fun `PLAYER config resolves DPAD_UP to FOCUS_QUICK_ACTIONS`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.DPAD_UP, ctx)

        assertEquals(TvAction.FOCUS_QUICK_ACTIONS, action)
    }

    @Test
    fun `PLAYER config resolves PLAY_PAUSE to PLAY_PAUSE`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.PLAY_PAUSE, ctx)

        assertEquals(TvAction.PLAY_PAUSE, action)
    }

    @Test
    fun `PLAYER config resolves CHANNEL_UP to CHANNEL_UP`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.CHANNEL_UP, ctx)

        assertEquals(TvAction.CHANNEL_UP, action)
    }

    @Test
    fun `PLAYER config resolves BACK to BACK`() {
        val ctx = TvScreenContext.player()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.BACK, ctx)

        assertEquals(TvAction.BACK, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // LIBRARY SCREEN RESOLUTION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `LIBRARY config resolves FAST_FORWARD to ROW_FAST_SCROLL_FORWARD`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.FAST_FORWARD, ctx)

        // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: FF → Enter Row Fast Scroll Mode
        assertEquals(TvAction.ROW_FAST_SCROLL_FORWARD, action)
    }

    @Test
    fun `LIBRARY config resolves REWIND to ROW_FAST_SCROLL_BACKWARD`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.REWIND, ctx)

        // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: RW → Row Fast Scroll backwards
        assertEquals(TvAction.ROW_FAST_SCROLL_BACKWARD, action)
    }

    @Test
    fun `LIBRARY config resolves DPAD_UP to NAVIGATE_UP`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_UP, ctx)

        assertEquals(TvAction.NAVIGATE_UP, action)
    }

    @Test
    fun `LIBRARY config resolves DPAD_CENTER to OPEN_DETAILS`() {
        val ctx = TvScreenContext.library()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.LIBRARY, TvKeyRole.DPAD_CENTER, ctx)

        // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: CENTER → Open details
        assertEquals(TvAction.OPEN_DETAILS, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // SETTINGS SCREEN RESOLUTION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `SETTINGS config resolves DPAD navigation actions`() {
        val ctx = TvScreenContext.settings()

        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.DPAD_UP, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_DOWN,
            DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.DPAD_DOWN, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_LEFT,
            DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.DPAD_LEFT, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_RIGHT,
            DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.DPAD_RIGHT, ctx),
        )
    }

    @Test
    fun `SETTINGS config resolves BACK to BACK`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.BACK, ctx)

        assertEquals(TvAction.BACK, action)
    }

    @Test
    fun `SETTINGS config maps FAST_FORWARD to tab switching`() {
        val ctx = TvScreenContext.settings()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.SETTINGS, TvKeyRole.FAST_FORWARD, ctx)

        // Settings maps FF to SWITCH_SETTINGS_TAB_NEXT per behavior map
        assertEquals(TvAction.SWITCH_SETTINGS_TAB_NEXT, action)
    }

    // ══════════════════════════════════════════════════════════════════
    // PROFILE GATE RESOLUTION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PROFILE_GATE config resolves DPAD navigation actions`() {
        val ctx = TvScreenContext.profileGate()

        assertEquals(
            TvAction.NAVIGATE_UP,
            DefaultTvScreenConfigs.resolve(TvScreenId.PROFILE_GATE, TvKeyRole.DPAD_UP, ctx),
        )
        assertEquals(
            TvAction.NAVIGATE_DOWN,
            DefaultTvScreenConfigs.resolve(TvScreenId.PROFILE_GATE, TvKeyRole.DPAD_DOWN, ctx),
        )
    }

    @Test
    fun `PROFILE_GATE config resolves DPAD_CENTER to null for FocusKit handling`() {
        val ctx = TvScreenContext.profileGate()
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PROFILE_GATE, TvKeyRole.DPAD_CENTER, ctx)

        assertNull(action)
    }

    // ══════════════════════════════════════════════════════════════════
    // UNMAPPED KEY TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `unmapped keys return null`() {
        val ctx = TvScreenContext.player()

        // GUIDE is not mapped in PLAYER config
        val action = DefaultTvScreenConfigs.resolve(TvScreenId.PLAYER, TvKeyRole.GUIDE, ctx)

        assertNull(action)
    }

    @Test
    fun `unknown screen returns null for all keys`() {
        val ctx = TvScreenContext.unknown()

        // Unknown screens have no config, so all keys return null
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.UNKNOWN, TvKeyRole.FAST_FORWARD, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.UNKNOWN, TvKeyRole.DPAD_UP, ctx))
        assertNull(DefaultTvScreenConfigs.resolve(TvScreenId.UNKNOWN, TvKeyRole.BACK, ctx))
    }

    // ══════════════════════════════════════════════════════════════════
    // CONFIG HELPER TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `hasBinding returns true for bound role`() {
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PLAYER)

        assertTrue(config.hasBinding(TvKeyRole.FAST_FORWARD))
    }

    @Test
    fun `hasBinding returns false for unbound role`() {
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PLAYER)

        // GUIDE is not in PLAYER bindings
        assertFalse(config.hasBinding(TvKeyRole.GUIDE))
    }

    @Test
    fun `boundRoles returns all bound keys`() {
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PLAYER)
        val boundRoles = config.boundRoles()

        assertTrue(boundRoles.contains(TvKeyRole.FAST_FORWARD))
        assertTrue(boundRoles.contains(TvKeyRole.REWIND))
        assertTrue(boundRoles.contains(TvKeyRole.MENU))
        assertTrue(boundRoles.contains(TvKeyRole.BACK))
    }

    @Test
    fun `empty config returns empty bindings`() {
        val config = TvScreenInputConfig.empty(TvScreenId.UNKNOWN)

        assertTrue(config.bindings.isEmpty())
        assertEquals(TvScreenId.UNKNOWN, config.screenId)
    }

    @Test
    fun `forScreen returns config for defined screen`() {
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.PLAYER)

        assertNotNull(config)
        assertEquals(TvScreenId.PLAYER, config.screenId)
    }

    @Test
    fun `forScreen returns empty config for undefined screen`() {
        // SEARCH screen is not in DefaultTvScreenConfigs
        val config = DefaultTvScreenConfigs.forScreen(TvScreenId.SEARCH)

        assertEquals(TvScreenId.SEARCH, config.screenId)
        assertTrue(config.bindings.isEmpty())
    }

    // Helper to assert boolean values (JUnit 4 doesn't have assertTrue with message as first param)
    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }

    private fun assertFalse(condition: Boolean) {
        org.junit.Assert.assertFalse(condition)
    }
}
